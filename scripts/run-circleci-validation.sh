#!/usr/bin/env bash
# Cloud-only entrypoint. Local checks: bash -n this-file; python3 verifier --self-test.
# No registration, dispatch, phone operations, release signing or deployment.
set -Eeuo pipefail

readonly FEATURE_BRANCH='feat/desk-clock-v2'
readonly BASELINE='89f1a60998a8d0cc0bdeb0c5ed17afdc99758d8b'
readonly SERIAL='emulator-5554'
readonly AVD_NAME='Linzin_CircleCI_API35_GoogleApis'
readonly SYSTEM_IMAGE='system-images;android-35;google_apis;x86_64'
readonly PACKAGE='com.linzin.linzindeskclock'
readonly PYTHON='/usr/bin/python3' # Avoid the machine image's pyenv shim after SDK environment changes.
readonly ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly EVIDENCE="$ROOT/.local/circleci"
readonly TOOLS="$ROOT/.local/circleci-tools"
readonly VERIFY="$ROOT/scripts/verify-circleci-results.py"
cd "$ROOT"
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# Guard BEFORE writes, downloads, Gradle or any adb invocation, on every phase.
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || fail 'CircleCI Linux x86_64 required.'
[[ ${CIRCLECI:-} == true && ${LINZIN_FALLBACK_ENABLED:-} == true ]] || fail 'Manual CircleCI opt-in required.'
[[ ${CIRCLE_BRANCH:-} == "$FEATURE_BRANCH" && -z ${CIRCLE_TAG:-} ]] || fail 'Only the feature branch is allowed.'
[[ ${LINZIN_PIPELINE_EVENT:-} == api ]] || fail 'Only an explicit web/API pipeline is allowed.'
[[ ${LINZIN_EXPECTED_SHA:-} =~ ^[0-9a-f]{40}$ ]] || fail 'Set expected-sha to the full pushed feature HEAD.'
[[ ${CIRCLE_SHA1:-} == "$LINZIN_EXPECTED_SHA" ]] || fail 'Pipeline revision differs from expected-sha.'
[[ "$(git rev-parse HEAD)" == "$LINZIN_EXPECTED_SHA" ]] || fail 'Checkout revision differs from expected-sha.'
git diff --quiet && git diff --cached --quiet || fail 'Tracked checkout is dirty.'
# The baseline is informational, not a hardcoded build revision: the new config
# must first be committed. Bind every output to the requested, checked-out SHA.
[[ -n ${BASH_ENV:-} ]] || fail 'CircleCI BASH_ENV required.'
[[ -x "$PYTHON" ]] || fail 'System Python 3 is required.'

fetch() {
  curl --fail --location --silent --show-error --retry 3 --connect-timeout 30 \
    --max-time 900 --proto '=https' --proto-redir '=https' \
    --user-agent 'Linzin-CI-preparation' "$1" --output "$2"
}
accept_licences() {
  # yes commonly receives SIGPIPE after the SDK client exits. Only that producer
  # status is tolerated; the SDK client must have returned zero.
  if yes | "$@"; then return 0; else
    local statuses=("${PIPESTATUS[@]}")
    [[ ${statuses[0]} == 141 && ${statuses[1]} == 0 ]]
  fi
}

prepare() {
  [[ ! -e "$EVIDENCE" && ! -e "$ROOT/app/build" && ! -e "$TOOLS" ]] || fail 'Use a fresh cloud checkout; stale evidence is forbidden.'
  mkdir -p "$EVIDENCE/logs" "$EVIDENCE/evidence" "$EVIDENCE/test-results" "$TOOLS/downloads"
  export LINZIN_EVIDENCE="$EVIDENCE"
  export GRADLE_USER_HOME="$TOOLS/gradle-home"
  export ANDROID_USER_HOME="$TOOLS/android-home"
  export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
  export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
  export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$ANDROID_HOME" && -d "$ANDROID_HOME" && -w "$ANDROID_HOME" ]] || fail 'Writable Android SDK from machine image required.'
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export ANDROID_ADB_SERVER_PORT=5037
  export ANDROID_SERIAL="$SERIAL"
  mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$GRADLE_USER_HOME"
  # ANDROID_USER_HOME provides a fresh debug keystore. Never upload this folder.
  readonly metadata_url='https://api.adoptium.net/v3/assets/release_name/eclipse/jdk-25.0.5%2B8?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=linux&project=jdk'
  fetch "$metadata_url" "$EVIDENCE/evidence/adoptium-metadata.json"
  "$PYTHON" -I -B "$VERIFY" jdk --root "$ROOT" > "$TOOLS/downloads/jdk-selection.txt"
  mapfile -t jdk < "$TOOLS/downloads/jdk-selection.txt"
  [[ ${#jdk[@]} == 2 ]] || fail 'Invalid JDK metadata selection.'
  fetch "${jdk[0]}" "$TOOLS/downloads/jdk.tar.gz"
  printf '%s  %s\n' "${jdk[1]}" "$TOOLS/downloads/jdk.tar.gz" | sha256sum --check --strict
  mkdir -p "$TOOLS/jdk"
  tar -xzf "$TOOLS/downloads/jdk.tar.gz" --strip-components=1 -C "$TOOLS/jdk"
  export JAVA_HOME="$TOOLS/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  java -XshowSettings:properties -version > "$EVIDENCE/logs/java.txt" 2>&1
  grep -Eq 'java.runtime.version = 25\.0\.5\+8(-LTS)?[[:space:]]*$' "$EVIDENCE/logs/java.txt" || fail 'Wrong JDK runtime.'
  grep -Fq 'java.vendor = Eclipse Adoptium' "$EVIDENCE/logs/java.txt" || fail 'Wrong JDK vendor.'

  # Preserve the machine image's working SDK/AVD managers, even when installing
  # native Command-line Tools 23 to obtain the new android-37.0 package.
  SDKMANAGER="$(command -v sdkmanager || true)"
  AVDMANAGER="$(command -v avdmanager || true)"
  [[ -x "$SDKMANAGER" && -x "$AVDMANAGER" ]] || fail 'Machine image must supply sdkmanager and avdmanager.'
  if [[ ! -f "$ANDROID_HOME/platforms/android-37.0/android.jar" || ! -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]]; then
    if ! accept_licences "$SDKMANAGER" --sdk_root="$ANDROID_HOME" 'platforms;android-37.0' 'build-tools;36.0.0' > "$EVIDENCE/logs/sdkmanager-compile.txt" 2>&1; then
      printf 'Legacy sdkmanager could not install compile SDK; checking native Android CLI.\n'
    fi
    if [[ ! -f "$ANDROID_HOME/platforms/android-37.0/android.jar" || ! -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]]; then
      ANDROID_CLI=''
      for candidate in "$ANDROID_HOME/cmdline-tools/23.0/bin/android" "$ANDROID_HOME/cmdline-tools/latest/bin/android"; do
        if [[ -x "$candidate" ]]; then ANDROID_CLI="$candidate"; break; fi
      done
      if [[ -z "$ANDROID_CLI" ]]; then
        # Google repository2-1.xml, cmdline-tools;23.0, Linux archive; checked 2026-11-09.
        fetch 'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' "$TOOLS/downloads/commandline.zip"
        printf '%s  %s\n' 'e025545c62a8e64c7559119566a569fb1dec5f60' "$TOOLS/downloads/commandline.zip" | sha1sum --check --strict
        unzip -q "$TOOLS/downloads/commandline.zip" -d "$TOOLS/native-cli"
        ANDROID_CLI="$TOOLS/native-cli/cmdline-tools/bin/android"
      fi
      [[ -x "$ANDROID_CLI" ]] || fail 'Native Android CLI missing.'
      accept_licences "$ANDROID_CLI" --no-metrics --sdk="$ANDROID_HOME" sdk install \
        'platforms;android-37.0' 'build-tools;36.0.0' 2>&1 | tee "$EVIDENCE/logs/native-sdk-install.txt"
    fi
  fi
  [[ -f "$ANDROID_HOME/platforms/android-37.0/android.jar" && -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]] || fail 'Exact compile SDK/build tools missing.'
  accept_licences "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses > "$EVIDENCE/logs/sdk-licences.txt" 2>&1
  accept_licences "$SDKMANAGER" --sdk_root="$ANDROID_HOME" 'platform-tools' 'emulator' "$SYSTEM_IMAGE" 2>&1 | tee "$EVIDENCE/logs/sdk-emulator-install.txt"
  [[ -f "$ANDROID_HOME/system-images/android-35/google_apis/x86_64/package.xml" ]] || fail 'API 35 Google APIs x86_64 image missing.'
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
  export AVDMANAGER
  for variable in JAVA_HOME GRADLE_USER_HOME ANDROID_HOME ANDROID_SDK_ROOT ANDROID_ADB_SERVER_PORT ANDROID_SERIAL ANDROID_USER_HOME ANDROID_EMULATOR_HOME ANDROID_AVD_HOME LINZIN_EVIDENCE AVDMANAGER PATH; do
    printf 'export %s=%q\n' "$variable" "${!variable}" >> "$BASH_ENV"
  done
  printf '%s\n' "$LINZIN_EXPECTED_SHA" > "$EVIDENCE/evidence/commit.txt"
  printf '%s\n' "$BASELINE" > "$EVIDENCE/evidence/preparation-baseline.txt"
  free -m > "$EVIDENCE/logs/memory-before-build.txt"
  "$PYTHON" -I -B "$VERIFY" --self-test
}

gradle() {
  timeout 1800 bash ./gradlew "$@" --no-daemon --no-parallel --max-workers=1 --console=plain \
    --no-build-cache --no-configuration-cache \
    '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8' \
    "-Dorg.gradle.java.home=$JAVA_HOME" \
    "-Porg.gradle.java.installations.paths=$JAVA_HOME" \
    -Porg.gradle.java.installations.auto-detect=false \
    -Porg.gradle.java.installations.auto-download=false \
    -Pkotlin.compiler.execution.strategy=in-process
}

run_logged() {
  local log=$1
  shift
  # Follow the supervised command PID rather than EOF on a pipe inherited by JVM children.
  # The actual command's exit status remains the gate; BUILD SUCCESSFUL text is not enough.
  : > "$log"
  "$@" > "$log" 2>&1 &
  local command_pid=$!
  tail --pid="$command_pid" --sleep-interval=1 -n +1 -F "$log" &
  local tail_pid=$!
  local status=0
  wait "$command_pid" || status=$?
  wait "$tail_pid" || true
  printf 'CI_COMMAND_FINISHED: exit=%s log=%s\n' "$status" "$log"
  ps -eo pid,ppid,etimes,comm > "$EVIDENCE/logs/processes-after-command.txt"
  return "$status"
}
build() {
  [[ -f "$EVIDENCE/evidence/commit.txt" && ! -e "$EVIDENCE/evidence/build-result.json" ]] || fail 'Fresh prepare phase required.'
  run_logged "$EVIDENCE/logs/gradle-version.txt" gradle --version
  run_logged "$EVIDENCE/logs/build.txt" gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest \
    --rerun-tasks --continue
  printf 'CI_VALIDATING_BUILD_REPORTS\n'
  timeout 120 "$PYTHON" -I -B "$VERIFY" build --root "$ROOT"
  # No Gradle workload overlaps the emulator. --no-daemon terminates its JVM.
}

adb_target() { timeout 45 "$ADB" -H 127.0.0.1 -P 5037 -s "$SERIAL" "$@"; }
identity() {
  [[ "$SERIAL" == emulator-5554 && -n ${EMU_PID:-} ]] || return 1
  kill -0 "$EMU_PID" || return 1
  [[ "$(adb_target get-state)" == device ]] || return 1
  local avd hardware fingerprint
  avd="$(adb_target emu avd name | tr -d '\r' | head -n 1)"
  hardware="$(adb_target shell getprop ro.hardware | tr -d '\r')"
  fingerprint="$(adb_target shell getprop ro.build.fingerprint | tr -d '\r')"
  [[ "$avd" == "$AVD_NAME" && "$hardware" =~ ^(ranchu|goldfish)$ ]] || return 1
  [[ "$fingerprint" == *sdk_* || "$fingerprint" == *generic* ]] || return 1
  [[ "$(adb_target shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || return 1
  [[ "$(adb_target shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] || return 1
  [[ "$(adb_target shell getprop ro.build.version.sdk | tr -d '\r')" == 35 ]] || return 1
  [[ "$(adb_target shell getprop ro.product.cpu.abi | tr -d '\r')" == x86_64 ]] || return 1
  [[ "$(adb_target shell am get-current-user | tr -d '\r')" == 0 ]] || return 1
  printf 'serial=%s\navd=%s\napi=35\nabi=x86_64\nhardware=%s\nfingerprint=%s\n' \
    "$SERIAL" "$avd" "$hardware" "$fingerprint" > "$EVIDENCE/evidence/emulator-identity.txt"
}

cleanup_emulator() {
  local result=$?
  trap - EXIT
  set +e
  # Device diagnostics and force-stop are allowed only after rechecking identity.
  if identity; then
    adb_target logcat -d > "$EVIDENCE/logs/logcat.txt" 2>&1
    adb_target shell dumpsys activity activities > "$EVIDENCE/logs/activities.txt" 2>&1
    adb_target shell dumpsys window > "$EVIDENCE/logs/windows.txt" 2>&1
    adb_target shell dumpsys power > "$EVIDENCE/logs/power.txt" 2>&1
    adb_target shell dumpsys media_session > "$EVIDENCE/logs/media-session.txt" 2>&1
    adb_target shell am force-stop "$PACKAGE"
  fi
  # Kill ONLY the emulator process started by this shell, never pkill or a server.
  if [[ -n ${EMU_PID:-} ]] && kill -0 "$EMU_PID" 2>/dev/null; then
    kill "$EMU_PID"
    for _ in {1..20}; do kill -0 "$EMU_PID" 2>/dev/null || break; sleep 1; done
    if kill -0 "$EMU_PID" 2>/dev/null; then kill -KILL "$EMU_PID"; fi
    wait "$EMU_PID" 2>/dev/null
  fi
  exit "$result"
}

emulator_tests() {
  printf 'CI_VALIDATING_BUILD_REPORTS\n'
  timeout 120 "$PYTHON" -I -B "$VERIFY" build --root "$ROOT"
  [[ ! -e "$EVIDENCE/evidence/instrumentation-result.json" ]] || fail 'Do not rerun against stale emulator evidence.'
  [[ -r /dev/kvm && -w /dev/kvm ]] || fail 'Machine image must provide writable KVM.'
  readonly ADB="$ANDROID_HOME/platform-tools/adb"
  readonly EMULATOR="$ANDROID_HOME/emulator/emulator"
  "$EMULATOR" -version > "$EVIDENCE/logs/emulator-version.txt" 2>&1
  "$EMULATOR" -accel-check 2>&1 | tee "$EVIDENCE/logs/kvm.txt"
  # Reject any pre-existing transport; the cloud instance must be disposable.
  "$ADB" -P 5037 start-server # -H requires a server that already exists, even on loopback.
  "$ADB" -H 127.0.0.1 -P 5037 devices > "$EVIDENCE/logs/devices-before.txt"
  [[ -z "$(sed '1d; /^[[:space:]]*$/d' "$EVIDENCE/logs/devices-before.txt")" ]] || fail 'Unexpected existing device or emulator.'
  printf 'no\n' | "$AVDMANAGER" create avd --name "$AVD_NAME" --package "$SYSTEM_IMAGE" \
    --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" > "$EVIDENCE/logs/avd-create.txt" 2>&1
  [[ -f "$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini" ]] || fail 'Dedicated AVD creation failed.'
  # A fresh AVD plus -wipe-data and -no-snapshot avoid debug signature conflicts.
  "$EMULATOR" -avd "$AVD_NAME" -port 5554 -no-window -no-snapshot -wipe-data \
    -no-boot-anim -noaudio -camera-back none -camera-front none -gpu swiftshader_indirect \
    -accel on -cores 4 -memory 3072 > "$EVIDENCE/logs/emulator.txt" 2>&1 &
  EMU_PID=$!
  trap cleanup_emulator EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  local deadline=$((SECONDS + 360)) booted=false
  while (( SECONDS < deadline )); do
    kill -0 "$EMU_PID" 2>/dev/null || fail 'Emulator exited during boot.'
    if [[ "$(timeout 5 "$ADB" -H 127.0.0.1 -P 5037 -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
      booted=true; break
    fi
    sleep 3
  done
  [[ "$booted" == true ]] || fail 'Emulator did not boot within 360 seconds.'
  identity || fail 'Dedicated emulator identity guard failed.'
  adb_target shell dumpsys battery reset
  adb_target shell locksettings set-disabled --user 0 true
  [[ "$(adb_target shell locksettings get-disabled --user 0 | tr -d '\r')" == true ]] || fail 'Lockscreen was not disabled.'
  # Prevent Android's first immersive-mode teaching overlay from stealing focus from PixelCopy/Espresso.
  # This touches only the fresh emulator after its identity check, never the phone.
  adb_target shell settings --user 0 put secure immersive_mode_confirmations confirmed
  [[ "$(adb_target shell settings --user 0 get secure immersive_mode_confirmations | tr -d '\r')" == confirmed ]] || fail 'Immersive tutorial setup failed.'
  adb_target shell input keyevent KEYCODE_WAKEUP
  adb_target shell wm dismiss-keyguard
  adb_target shell wm size 720x1280
  adb_target shell wm density 320
  adb_target shell settings put system accelerometer_rotation 0
  adb_target shell settings put system user_rotation 1
  adb_target shell wm user-rotation lock 1
  adb_target shell wm fixed-to-user-rotation enabled
  for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb_target shell settings put global "$scale" 0
  done
  if [[ "$(adb_target shell pm path com.google.android.googlequicksearchbox)" == package:* ]]; then
    adb_target shell pm disable-user --user 0 com.google.android.googlequicksearchbox
    adb_target shell am force-stop com.google.android.googlequicksearchbox
  fi
  for apk in app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk; do
    identity || fail 'Identity changed before install.'
    local install_log="$EVIDENCE/logs/install-$(basename "$apk").txt"
    timeout 120 "$ADB" -H 127.0.0.1 -P 5037 -s "$SERIAL" install -t --user 0 "$apk" \
      2>&1 | tee "$install_log"
    grep -Eq '^Success[[:space:]]*$' "$install_log" || fail 'APK installation did not report Success.'
  done
  identity || fail 'Identity changed before instrumentation.'
  timeout 1200 "$ADB" -H 127.0.0.1 -P 5037 -s "$SERIAL" shell am instrument --user 0 -w -r \
    -e linzinDedicatedMediaEmulator true \
    "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" \
    2>&1 | tee "$EVIDENCE/logs/instrumentation.txt"
  "$PYTHON" -I -B "$VERIFY" instrumentation --root "$ROOT"
  identity || fail 'Identity changed before background verification.'
  # The existing verifier independently guards emulator hardware and serial.
  timeout 300 "$PYTHON" -I -B scripts/verify-background-clock.py --adb "$ADB" --serial "$SERIAL" \
    --port 5037 --output "$EVIDENCE/background-clock" 2>&1 | tee "$EVIDENCE/logs/background-clock.txt"
  printf 'Instrumentation and background-clock commands completed.\n'
}

package_artifacts() {
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
    app/build/outputs/apk/debug/app-debug.apk > "$EVIDENCE/evidence/debug-apk-certificate.txt"
  "$PYTHON" -I -B "$VERIFY" package --root "$ROOT"
  printf 'CIRCLECI_VALIDATION_PASSED: 160 JVM tests, lint, 92 instrumentation tests and background clock.\n'
}

collect_reports() {
  [[ -d "$EVIDENCE" ]] || return 0
  mkdir -p "$EVIDENCE/reports" "$EVIDENCE/test-results/jvm"
  if [[ -d app/build/reports ]]; then cp -R app/build/reports/. "$EVIDENCE/reports/"; fi
  if [[ -d app/build/test-results/testDebugUnitTest ]]; then
    cp -R app/build/test-results/testDebugUnitTest/. "$EVIDENCE/test-results/jvm/"
  fi
  git rev-parse HEAD > "$EVIDENCE/evidence/collected-commit.txt"
}

case "${1:-}" in
  prepare) prepare ;;
  build) build ;;
  emulator) emulator_tests ;;
  package) package_artifacts ;;
  collect) collect_reports ;;
  *) fail 'Expected prepare, build, emulator, package or collect.' ;;
esac
