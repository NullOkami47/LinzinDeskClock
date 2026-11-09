# Idle screen-off (2.0.4)

Finite idle timeouts now close the clock and request an actual Android system lock through DevicePolicyManager.lockNow(). The former black overlay and zero-brightness fake sleep have been removed. Brightness changes for low-battery protection remain separate.

## Enable immediate screen-off

In the clock settings, select a finite idle timeout, then tap the optional lock-only device-administrator button. Android shows its own consent screen. The receiver declares only the force-lock policy; it cannot wipe the device or change passwords. No permission is granted by installation or by the timeout callback.

Android requires strong authentication (PIN, pattern or password) after this API locks a secured device. On a device without a configured screen lock, it puts the display to sleep without adding a lock credential. The administrator can be disabled in Android device-administrator settings.

If the permission is absent, unsupported, revoked or the lock request fails, the clock exits, releases its keep-awake flag and explains that the normal system screen timeout takes over. This fallback does not promise immediate screen-off and never paints black or forces minimum brightness.

## Lifecycle and auto-activation

- Every finite duration waits the full configured time, including durations shorter than the system screen timeout.
- Touch interaction restarts the idle wait. Settings and permission visits suspend it; returning to the clock starts a fresh wait.
- Background clock windows cannot expire a timer and lock another foreground app. Never keeps the visible clock awake.
- MainActivity, ClockSessionActivity and ClockDreamService own their screen-off termination. Before locking, they persist cycle suppression and release window wake flags. Dream additionally stops scans and its bright-screen request.
- A passive foreground/screen-on observation does not clear exit suppression. A real observed unlock allows the next lock-triggered activation. An explicit new unlocked app session or monitor-enable action also recovers a missed unlock broadcast; passive background resumes do not.
- Clock side selection, split ratios, media visibility/control, burn-in protection and foldable 30–120 degree trigger are unchanged.

## Verification boundaries

ScreenOffTimeoutTest exercises the real Compose timeout, interaction, settings and lifecycle. ScreenOffSystemTest validates the installed receiver policy, no-consent fallback and actual PowerManager noninteractive state through the entire five-second activation window. These tests enforce the existing disposable-emulator guard before launching activities or issuing shell commands. Any temporary emulator admin grant is removed in finally.

Physical-device delivery installs the APK only. The owner must enable the optional permission themselves; neither permission activation nor lock/unlock testing is performed on the phone.

Primary API references: Android Developers DevicePolicyManager.lockNow and Device administration overview.
