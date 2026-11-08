# CircleCI fallback validation

Scope: `NullOkami47/LinzinDeskClock`, `feat/desk-clock-v2`, exact manually supplied SHA; version 2.0.3 with user-selected clock side. The user authorised CircleCI after this PC's GitHub runner registration/start was repeatedly rejected by execution policy.

The CircleCI project is `LinzinDeskClock`, project ID `df8c1b8a-5a99-49a3-8cc9-8cc25186b3d3`. The pipeline reads `.circleci/config.yml` from the selected feature branch. It runs only when the manual opt-in parameter is true and the checkout matches `expected-sha`. Push/PR/tag defaults do not run it.

## Required evidence

- Exact Temurin 25.0.5+8 and SDK37.0 / Build Tools36.0.0.
- Full build, 156 JVM cases with zero failures/errors/skips, lint zero errors.
- Fresh API35 Google APIs x86_64 emulator, explicit serial emulator-5554, 83 cases with normal completion and no failure/skip.
- Existing background-clock check must report all four lock/exit/re-entry steps.
- Cloud APK SHA-256, source SHA and verification summary are packaged only after all checks pass.
- Phone installation uses the cloud APK re-signed with the existing local debug identity; all non-signature ZIP entries must remain byte-identical. The physical phone is never a test target and is not launched.

## Failed runs retained

- Pipeline1 / build1 / source835d15d: tool setup hit a pyenv shim failure after SDK environment changes. Changed validation to explicit `/usr/bin/python3`.
- Pipeline2: no workflow because manual opt-in remained false; no test execution claimed.
- Pipeline3 / build2 / sourcea38956b: Gradle printed BUILD SUCCESSFUL but its output pipe did not finish. Cancelled; replaced pipe-dependent log streaming with command-PID supervision and bounded report validation.
- Pipeline4 / build3 / source0a1a6d0: full build, 156 JVM cases and lint succeeded; ADB could not start a nonexistent server when `-H` was specified. Local server start now omits `-H`, subsequent calls remain explicit loopback + emulator serial.
- Pipeline5 / build4 / source75fae1c: full build156 pass, lint0 errors/105 warnings/4 hints. Ran83 device cases;21 failures. One parent-layout assertion still encoded the superseded larger-panel rule. Most others required visible/focused windows while Android's first ImmersiveModeConfirmation overlay had focus; logcat also recorded system/GMS ANRs. Keep these failures; do not describe the run as passed.

## Repairs for next run

Update the remaining parent-layout test to explicitly select RIGHT/LEFT, verify the chosen side persists during drag/release, and retain pager/state/disposal assertions. Pre-confirm Android's immersive tutorial on the disposable emulator using the AOSP-defined secure setting, verifying readback. Use the supported large machine and four emulator cores to reduce UI contention; keep test timeouts, counts, focus checks and failure gates unchanged. Preserve window/power diagnostics on cleanup. Actual CI outcome is pending and must be verified before phone installation.
