#!/usr/bin/env python3
"""Strict CircleCI evidence checks; --self-test optionally reads --reports-root."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

JVM_TESTS = 156
ANDROID_TESTS = 83
JDK_RELEASE = "jdk-25.0.5+8"
JDK_NAME = "OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz"
# Adoptium release_name metadata, Linux x64/HotSpot, verified 2026-11-09.
JDK_SHA256 = "68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb"
APK_PATHS = (
    "app/build/outputs/apk/debug/app-debug.apk",
    "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def parse_xml(text):
    require(not re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", text, re.I), "DTD/entities are forbidden")
    return ET.fromstring(text)


def check_jvm(reports):
    require(bool(reports), "Missing JVM XML reports")
    total, identities = 0, set()
    for text in reports:
        root = parse_xml(text)
        require(root.tag == "testsuite", "Expected a testsuite root")
        counters = {}
        for key in ("tests", "failures", "errors", "skipped"):
            value = root.get(key, "")
            require(bool(re.fullmatch(r"[0-9]+", value)), f"Missing/invalid JUnit counter: {key}")
            counters[key] = int(value)
        cases = root.findall("testcase")
        require(len(cases) == counters["tests"], "JUnit declared/actual count mismatch")
        require(all(counters[key] == 0 for key in ("failures", "errors", "skipped")), "JVM failures/errors/skips")
        require(not any(node.tag in ("failure", "error", "skipped") for node in root.iter()), "Hidden failure/error/skip node")
        require(len(list(root.iter("testcase"))) == len(cases), "Unexpected nested testcases")
        for case in cases:
            name = case.get("name", "")
            identity = (case.get("classname") or root.get("name", ""), name)
            require(bool(name) and bool(identity[0]) and identity not in identities, "Missing/duplicate testcase identity")
            identities.add(identity)
        total += len(cases)
    require(total == JVM_TESTS, f"Expected {JVM_TESTS} JVM tests, got {total}")
    return {"success": True, "tests": total, "failures": 0, "errors": 0, "skipped": 0}


def check_lint(text):
    root = parse_xml(text)
    require(root.tag == "issues", "Expected Android lint issues XML")
    issues = list(root.iter("issue"))
    severities = [item.get("severity", "") for item in issues]
    require(all(s in ("Fatal", "Error", "Warning", "Hint", "Information", "Ignore") for s in severities), "Invalid lint severity")
    require(not any(s in ("Fatal", "Error") for s in severities), "Lint errors/fatals present")
    return {"success": True, "errors": 0, "warnings": severities.count("Warning"), "hints": severities.count("Hint")}


def check_instrumentation(text):
    # sendStatus(0, captureBundle) emits extra zeros: never count status zeros as tests.
    summaries = list(re.finditer(r"^[ \t]*OK \(([0-9]+) tests?\)[ \t]*\r?$", text, re.M))
    require(len(summaries) == 1, "Exactly one JUnit OK summary required")
    passed = int(summaries[0][1])
    require(passed == ANDROID_TESTS, f"Expected {ANDROID_TESTS} instrumentation tests, got {passed}")
    statuses = re.findall(r"^[ \t]*INSTRUMENTATION_STATUS_CODE:([^\r\n]*)", text, re.M)
    require(bool(statuses), "No instrumentation status records")
    require(all(re.fullmatch(r"[ \t]*[012][ \t]*", value) for value in statuses), "Failed/skipped/unknown instrumentation status")
    ends = list(re.finditer(r"^[ \t]*INSTRUMENTATION_CODE:[^\r\n]*", text, re.M))
    final = re.search(r"^[ \t]*INSTRUMENTATION_CODE:[ \t]*-1[ \t\r\n]*\Z", text, re.M)
    require(len(ends) == 1 and final is not None and summaries[0].start() < final.start(), "Missing/abnormal/duplicate/trailing instrumentation completion")
    require(not re.search(r"INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|FAILURES!!!", text), "Runner reported failure")
    require(not re.search(r"^[ \t]*INSTRUMENTATION_(?:STATUS|RESULT):[ \t]*(?:Error|shortMsg|longMsg|stack)[ \t]*=", text, re.M | re.I), "Runner error bundle")
    return {"success": True, "tests": passed, "failures": 0, "errors": 0, "skipped": 0, "normalEnd": True}


def check_background(record):
    require(record.get("success") is True and record.get("emulator") == "emulator-5554", "Background verifier did not pass on the dedicated emulator")
    require(record.get("steps") == [
        "Background lock opens focused awake clock without notification tap",
        "Session survives configuration recreation",
        "Exit suppresses same lock cycle",
        "Next lock opens clock without reopening main activity",
    ], "Missing background-clock verification steps")
    return record


def select_jdk(metadata):
    require(isinstance(metadata, dict) and metadata.get("release_name") == JDK_RELEASE, "Wrong Adoptium release")
    binaries = [b for b in metadata.get("binaries", []) if all(b.get(k) == v for k, v in {
        "architecture": "x64", "heap_size": "normal", "image_type": "jdk",
        "jvm_impl": "hotspot", "os": "linux", "project": "jdk",
    }.items())]
    require(len(binaries) == 1, "Exactly one Linux x64 HotSpot JDK required")
    package = binaries[0]["package"]
    url = "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/" + JDK_NAME
    require(package.get("name") == JDK_NAME and package.get("link") == url, "Unexpected JDK archive/source")
    require(package.get("checksum") == JDK_SHA256, "JDK metadata SHA-256 differs from reviewed release")
    return url, JDK_SHA256


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def apk_records(root):
    records = []
    for relative in APK_PATHS:
        file = root / relative
        require(file.is_file() and file.stat().st_size > 0, f"APK missing/empty: {relative}")
        records.append({"file": file.name, "source": relative, "bytes": file.stat().st_size,
                        "sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
    return records


def build_result(root):
    files = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    jvm = check_jvm([file.read_text(encoding="utf-8") for file in files])
    lint = check_lint((root / "app/build/reports/lint-results-debug.xml").read_text(encoding="utf-8"))
    return {"success": True, "commit": os.environ["CIRCLE_SHA1"], "jvm": jvm, "lint": lint, "apks": apk_records(root)}


def instrumentation_junit(result):
    # A single aggregate gate, not fabricated per-test results. The raw runner
    # transcript is the authoritative evidence for the 83 Android test cases.
    suite = ET.Element("testsuite", name="CircleCI.InstrumentationGate", tests="1", failures="0", errors="0", skipped="0")
    ET.SubElement(suite, "testcase", classname="CircleCI.InstrumentationGate", name=f"all_{result['tests']}_tests_complete_without_failures_or_skips")
    return ET.tostring(suite, encoding="unicode") + "\n"


def run_phase(phase, root):
    evidence = root / ".local/circleci"
    if phase == "jdk":
        print("\n".join(select_jdk(load_json(evidence / "evidence/adoptium-metadata.json"))))
        return
    require(os.environ.get("CIRCLECI") == "true", "Evidence writes require CircleCI")
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    require(sha == os.environ.get("CIRCLE_SHA1") == os.environ.get("LINZIN_EXPECTED_SHA"), "Evidence revision mismatch")
    require((evidence / "evidence/commit.txt").read_text().strip() == sha, "Prepare revision mismatch")
    if phase == "build":
        result = build_result(root)
        path = evidence / "evidence/build-result.json"
        if path.exists():
            require(load_json(path) == result, "Build evidence or APKs changed since validation")
        else:
            save_json(path, result)
    elif phase == "instrumentation":
        result = check_instrumentation((evidence / "logs/instrumentation.txt").read_text(encoding="utf-8"))
        result["commit"] = sha
        save_json(evidence / "evidence/instrumentation-result.json", result)
        (evidence / "test-results/instrumentation-gate.xml").write_text(instrumentation_junit(result), encoding="utf-8")
    elif phase == "package":
        build = build_result(root)
        require(load_json(evidence / "evidence/build-result.json") == build, "Build evidence changed")
        android = check_instrumentation((evidence / "logs/instrumentation.txt").read_text(encoding="utf-8"))
        android["commit"] = sha
        require(load_json(evidence / "evidence/instrumentation-result.json") == android, "Instrumentation evidence changed")
        background = check_background(load_json(evidence / "background-clock/result.json"))
        result = {
            "success": True, "commit": sha, "branch": os.environ["CIRCLE_BRANCH"],
            "completedAt": datetime.now(timezone.utc).isoformat(),
            "pipelineId": os.environ.get("LINZIN_PIPELINE_ID"),
            "workflowId": os.environ.get("CIRCLE_WORKFLOW_ID"), "buildUrl": os.environ.get("CIRCLE_BUILD_URL"),
            "machineImage": "android:2026.10.1", "resourceClass": "large", "jdk": "25.0.5+8",
            "compileSdk": "37.0", "buildTools": "36.0.0", "minSdk": 31, "targetSdk": 37,
            "jvm": build["jvm"], "lint": build["lint"], "instrumentation": android,
            "backgroundClock": background, "apks": build["apks"],
            "handoff": "Cloud debug-signed APK only. Parent must verify this commit and SHA-256, re-sign with the existing phone key, and install without launching after the CircleCI job succeeds.",
        }
        out = evidence / "apks"
        out.mkdir(exist_ok=False)
        for item in build["apks"]:
            shutil.copyfile(root / item["source"], out / item["file"])
        (out / "SHA256SUMS").write_text("".join(f"{r['sha256']}  {r['file']}\n" for r in build["apks"]), encoding="utf-8")
        save_json(out / "commit-and-validation.json", result)
        save_json(evidence / "result.json", result)
    else:
        raise ValueError(f"Unknown phase: {phase}")
    print(json.dumps(result, indent=2))


class ParserTests(unittest.TestCase):
    @staticmethod
    def junit(count=JVM_TESTS, suffix="", **counts):
        attrs = {"name": "fixture.ClockTest", "tests": str(count), "failures": "0", "errors": "0", "skipped": "0"}
        attrs.update(counts)
        root = ET.Element("testsuite", attrs)
        for index in range(count):
            ET.SubElement(root, "testcase", classname="fixture.ClockTest" + suffix, name=f"test{index}")
        return ET.tostring(root, encoding="unicode")

    @staticmethod
    def instrumentation():
        return ("INSTRUMENTATION_STATUS: media_presentation_capture=/fixture.png\n"
                "INSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_STATUS_CODE: 1\n"
                "INSTRUMENTATION_STATUS_CODE: 2\nINSTRUMENTATION_STATUS_CODE: 0\n"
                "INSTRUMENTATION_RESULT: stream=\nTime: 123.4\n\nOK (83 tests)\n\nINSTRUMENTATION_CODE: -1\n")

    def test_jvm_accepts_real_case_counts(self):
        self.assertEqual(check_jvm([self.junit()])["tests"], 156)
        self.assertEqual(check_jvm([self.junit(100), self.junit(56, suffix="Other")])["tests"], 156)

    def test_jvm_rejects_invalid_evidence(self):
        good = self.junit()
        cases = [[], [self.junit(155)], [self.junit(157)], [self.junit(0)], [good, good],
                 [self.junit(failures="1")], [self.junit(errors="1")], [self.junit(skipped="1")],
                 [self.junit(tests="157")], [self.junit(errors="NaN")], [self.junit(errors="-1")],
                 [good.replace(' errors="0"', '')], [good.replace('name="test0"', 'name="test1"')],
                 ['<testsuite'], ['<testsuites/>'], ['<!DOCTYPE testsuite>' + good]]
        for tag in ("failure", "error", "skipped"):
            cases.append([good.replace(" />", f"><{tag}/></testcase>", 1)])
        for number, reports in enumerate(cases):
            with self.subTest(case=number), self.assertRaises((ValueError, ET.ParseError)):
                check_jvm(reports)

    def test_instrumentation_accepts_crlf_and_capture_zeros(self):
        for text in (self.instrumentation(), self.instrumentation().replace("\n", "\r\n")):
            self.assertEqual(check_instrumentation(text)["tests"], 83)

    def test_instrumentation_rejects_partial_or_abnormal_runs(self):
        good = self.instrumentation()
        cases = [good.replace("83 tests", f"{n} tests") for n in (0, 82, 84)]
        cases += [good.replace("OK (83 tests)", ""), good.replace("OK (83 tests)", "OK (83 tests)\nOK (83 tests)"),
                  good.replace("INSTRUMENTATION_CODE: -1", ""), good.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
                  good + "INSTRUMENTATION_CODE: -1", good + "Process crashed", "INSTRUMENTATION_CODE: -1\n" + good,
                  re.sub(r"INSTRUMENTATION_STATUS_CODE:[^\n]*\n", "", good)]
        for status in ("-1", "-2", "-3", "-4", "-5", "3", "invalid", "", "+1"):
            cases.append(f"INSTRUMENTATION_STATUS_CODE: {status}\n" + good)
        for failure in ("INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "FAILURES!!!",
                        "INSTRUMENTATION_STATUS: Error=", "INSTRUMENTATION_RESULT: shortMsg=",
                        "INSTRUMENTATION_RESULT: longMsg=", "INSTRUMENTATION_STATUS: stack="):
            cases.append(failure + "\n" + good)
        for number, text in enumerate(cases):
            with self.subTest(case=number), self.assertRaises(ValueError):
                check_instrumentation(text)

    def test_lint_requires_zero_errors(self):
        self.assertEqual(check_lint('<issues/>')["errors"], 0)
        self.assertEqual(check_lint('<issues><issue severity="Warning"/></issues>')["warnings"], 1)
        self.assertEqual(check_lint('<issues><issue severity="Hint"/></issues>'),
                         {"success": True, "errors": 0, "warnings": 0, "hints": 1})
        for text in ('<issues><issue severity="Error"/></issues>', '<issues><issue severity="Fatal"/></issues>',
                     '<issues><issue severity="Unknown"/></issues>',
                     '<issues><issue severity="Hint"/><issue severity="Error"/></issues>',
                     '<issues><issue/></issues>', '<testsuite/>', '<!DOCTYPE issues><issues/>'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                check_lint(text)

    def test_background_requires_all_steps_and_emulator(self):
        good = {"emulator": "emulator-5554", "success": True, "steps": [
            "Background lock opens focused awake clock without notification tap", "Session survives configuration recreation",
            "Exit suppresses same lock cycle", "Next lock opens clock without reopening main activity"]}
        self.assertTrue(check_background(good)["success"])
        for patch in ({"emulator": "phone"}, {"success": False}, {"success": "true"}, {"steps": good["steps"][:3]}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                check_background({**good, **patch})

    def test_jdk_requires_exact_official_release_and_digest(self):
        binary = {"architecture": "x64", "heap_size": "normal", "image_type": "jdk", "jvm_impl": "hotspot",
                  "os": "linux", "project": "jdk", "package": {"name": JDK_NAME, "checksum": JDK_SHA256,
                  "link": "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/" + JDK_NAME}}
        good = {"release_name": JDK_RELEASE, "binaries": [binary]}
        self.assertEqual(select_jdk(good)[1], JDK_SHA256)
        cases = [{**good, "release_name": "jdk-25"}, {**good, "binaries": []}, {**good, "binaries": [binary, binary]}]
        for key, value in (("checksum", "0" * 64), ("link", "https://example.com/jdk.tar.gz"), ("name", "other.tar.gz")):
            cases.append({**good, "binaries": [{**binary, "package": {**binary["package"], key: value}}]})
        for metadata in cases:
            with self.subTest(metadata=metadata), self.assertRaises(ValueError):
                select_jdk(metadata)


def real_report_tests(root):
    """Read existing reports only: no build, device command or evidence writes."""
    class RealReportTests(unittest.TestCase):
        def test_existing_lint_xml(self):
            text = (root / "app/build/reports/lint-results-debug.xml").read_text(encoding="utf-8")
            result = check_lint(text)
            self.assertTrue(result["success"])
            self.assertEqual(result["errors"], 0)
            issues = list(parse_xml(text).iter("issue"))
            self.assertEqual(result["warnings"], sum(n.get("severity") == "Warning" for n in issues))
            self.assertEqual(result["hints"], sum(n.get("severity") == "Hint" for n in issues))

        def test_existing_156_jvm_tests(self):
            files = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
            result = check_jvm([file.read_text(encoding="utf-8") for file in files])
            self.assertEqual(result, {"success": True, "tests": 156, "failures": 0, "errors": 0, "skipped": 0})

    return unittest.defaultTestLoader.loadTestsFromTestCase(RealReportTests)


def main():
    if sys.argv[1:2] == ["--self-test"]:
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument("--self-test", action="store_true")
        parser.add_argument("--reports-root", type=Path,
                            help="Also validate existing lint/JVM reports, read-only; never builds or invokes adb")
        args = parser.parse_args()
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(ParserTests)
        if args.reports_root is not None:
            suite.addTests(real_report_tests(args.reports_root.resolve()))
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("jdk", "build", "instrumentation", "package"))
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    try:
        run_phase(args.phase, args.root.resolve())
    except (ValueError, KeyError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"VALIDATION_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
