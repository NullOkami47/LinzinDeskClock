# Self-hosted validation handoff

Status: 2026-11-08, Asia/Hong_Kong. The workflow and validation script are prepared; no self-hosted runner is registered or online, and no self-hosted job has executed.

## Trigger and execution boundary

- Repository: `NullOkami47/LinzinDeskClock` only.
- Dispatch-only workflow for exactly `feat/desk-clock-v2`. No automatic push job is queued while runner registration is blocked.
- Manual dispatch requires the explicit matching branch input; job and script also check repository, ref and actor `NullOkami47`.
- No pull-request trigger. Publishing the prepared workflow does not run it. Dispatch only once the runner is available and the dedicated emulator is released.
- Label: `linzin-v2-qa`; Windows X64; one job with `--ephemeral --disableupdate` was intended. No global permissions, services or runners in other repositories were changed.
- All workspace, cache and temporary paths are under `D:\codex-tools\linzin-android\runner-QA`. The validation script requires the isolated `work` checkout and the triggering commit SHA.

## Dedicated emulator

Every adb invocation specifies `-H 127.0.0.1 -P 5038 -s emulator-5580`. The script verifies the recorded private adb process and start time, its `--one-device emulator-5580` server arguments, the QEMU AVD/port, then the actual AVD name `Linzin_API35_GoogleApis_QA`, boot completion, API 35, emulator hardware/fingerprint and user 0. A failed check stops execution. It never starts or replaces an adb server, restarts an emulator, or uses connectedDebugAndroidTest.

On 2026-11-08 at 10:20 +08:00, an authoritative fresh process check confirmed the old QEMU 13876/private adb 25908 had exited, with no active dedicated command. The parent-authorised existing launcher restarted only this AVD after checking its ports. New private adb PID: 4488; emulator launcher PID: 18788; QEMU PID: 22364, start 2026-11-08T10:20:59.8163668+08:00. `current-run.json` records the new QEMU PID and start time. The read-only preflight passed. These PIDs are a historical snapshot; rerun preflight before use.

```powershell
& .\scripts\run-self-hosted-validation.ps1 -PreflightOnly
```

The actual job builds with `scripts/build-android.ps1`, installs app/test APKs only on this verified emulator, prepares its landscape/media fixture and invokes AndroidJUnitRunner with `-e linzinDedicatedMediaEmulator true`. The physical phone was not selected, woken, installed to, or tested. No build/install/instrumentation was performed during this handoff.

## Test result parsing

Custom `media_artwork_capture` events use `INSTRUMENTATION_STATUS_CODE: 0`, so those codes are not counted as passed tests. The script requires exactly one JUnit `OK (28 tests)` summary, no failure/skip codes, exactly one normal final `INSTRUMENTATION_CODE: -1`, and no failure/aborted markers. Seven local parser regression cases passed, including 28 test events plus six capture events, CRLF, skips, failures, missing end and duplicate summary. This is parser validation, not a new device test run. The parent separately reported 120 unit and 28 instrumentation tests passing.

PowerShell syntax validation passed. actionlint 1.7.12 passed with the requested custom runner label declared in the QA-local actionlint configuration. The workflow has not been executed.

## Runner registration blocker

Official Windows runner: 2.338.2, archive `actions-runner-win-x64-2.338.2.zip`. SHA-256 verified against both GitHub's asset digest and the official release checksum:

`a1f17aa987fc8158bc7521d284912267a0d01b7c2c4684b33c86783b5e8221cc`

The archive is downloaded and extracted under the QA directory. The local listener reports 2.338.2; its help confirms `--ephemeral` and `--disableupdate` support. The authenticated repository check confirmed admin access and zero registered runners.

The sole attempted combined registration/background-start command was rejected before execution by automatic approval review with `blocked by policy`, without a more specific reason. No registration token was obtained by that command, printed or persisted. No runner was configured or started. This is an execution-approval blocker, not a GitHub registration API permission error. It was not retried through another tool or delegated to bypass that restriction. Registration must wait until the execution restriction is resolved; do not represent the prepared workflow as an actual self-hosted pass.

The original hosted run 42399769799 was independently confirmed: debug-test-lint succeeded and instrumentation-api-35 failed. Parent reports the second hosted run 42403706282 also passed the build job and failed at KVM setup. Main branch was not changed by this work.
