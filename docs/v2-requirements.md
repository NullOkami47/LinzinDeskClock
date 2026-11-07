
# LinzinDeskClock v2.0 — Full Implementation Request

Repository:
https://github.com/linzin86/LinzinDeskClock

You are a senior Android engineer specializing in Kotlin, Jetpack Compose, Android system APIs, foldable devices, sensors, media sessions, lock screens, and Android background execution.

Your task is to IMPLEMENT the features described below in the existing LinzinDeskClock Android application.

Do not merely produce an implementation plan. Inspect the repository, modify the actual source code, add the necessary components and permissions, integrate everything into the existing application, run available tests, and fix compilation errors.

Preserve existing functionality and visual design unless a change is explicitly required.

You may use native Android APIs, ADB, Shizuku, or AccessibilityService where appropriate. However, prefer standard Android APIs and use privileged mechanisms only when genuinely necessary, technically viable, and explicitly authorized by the user.

Do not claim that Android restrictions have been bypassed unless the implementation actually works.

==================================================
0. EXISTING PROJECT — READ FIRST
==================================================

The existing application:

- Is written in Kotlin using Jetpack Compose.
- Supports Android 12 and newer (minSdk 31).
- Currently targets Android SDK 37.
- Has a large MainActivity.kt file.
- Detects foldable hinge angles with Sensor.TYPE_HINGE_ANGLE.
- Automatically activates its clock on foldable devices at hinge angles of approximately 30°–120°.
- Uses manufacturer-specific device-state commands for foldable cover-screen behavior.
- Supports normal phones through a "Not a Foldable Phone" bypass.
- Provides a single-panel clock layout.
- Provides dual-panel layouts with fixed 70/30 proportions.
- Includes secondary widgets for calendar and weather.
- Includes screen brightness, timeout, UI lock, and OLED burn-in protection.

Before modifying anything:

1. Inspect the entire relevant codebase.
2. Identify the current entry points, activity lifecycle, sensor listeners, preferences, UI components, and foldable logic.
3. Check AndroidManifest.xml, build configuration, dependencies, and existing resources.
4. Preserve the existing app's appearance and clock styles.
5. Inspect git status and preserve unrelated user changes.
6. Refactor carefully where necessary to avoid further expanding MainActivity.kt.

IMPORTANT:

DO NOT CHANGE THE EXISTING FOLDABLE HINGE-ANGLE TRIGGER RANGE.

The existing 30°–120° hinge-angle behavior MUST remain intact.

The new 30°–75° angle range is ONLY for normal, non-foldable phones.

These are two completely different measurements and must have separate implementations.

==================================================
1. NON-FOLDABLE PHONE AUTO-ACTIVATION
==================================================

Add an automatic desktop-clock activation feature for normal Android phones.

The goal:

When a user places a normal phone horizontally in a tilted desk-stand position, then locks the phone, LinzinDeskClock should automatically appear if the configured conditions are satisfied.

However, it must NEVER spontaneously interrupt someone who is actively using their phone.

1.1 Required activation conditions

For NON-FOLDABLE devices only:

- The phone must be physically positioned in landscape orientation.
- The phone must be tilted 30°–75° relative to the horizontal desk surface.
- The phone must have entered the lock-screen / screen-off / AOD state.
- The charging condition must match the user's selected charging preference.

All applicable conditions must be satisfied.

The general eligibility logic is:

eligible =
    isNonFoldable
    && isPhysicallyLandscape
    && tiltAngle in 30f..75f
    && chargingRequirementSatisfied

However, eligibility alone must NOT launch the clock.

An actual lock-screen transition is required to start an activation attempt.

1.2 IMPORTANT: Activation must be triggered by locking

The clock must NOT activate simply because the user rotates the phone or connects a charger while using it.

Instead, implement this sequence:

1. The user is using the phone normally.
2. The user locks the phone or the phone transitions into its locked, non-interactive state.
3. The application receives an appropriate screen/lock-state event.
4. Start a bounded detection window lasting a maximum of 5 seconds.
5. During this window, sample the relevant sensor and charging conditions.
6. If all required conditions are continuously valid for approximately 1.5 seconds, attempt to activate the desk clock.
7. Stop the scan immediately after successful activation.
8. If the conditions do not become stable within 5 seconds, stop the scan.
9. Keep the normal Android lock screen or AOD if activation is unsuccessful.
10. Do not automatically retry later during the same lock cycle.

This means:

- Placing the phone in the correct position while it is unlocked does NOT trigger the clock.
- Charging the phone while it is unlocked does NOT trigger the clock.
- Rotating the phone while actively using it does NOT trigger the clock.
- Locking the phone while all conditions are satisfied can trigger the clock.
- If the user positions the phone correctly more than 5 seconds after locking it, there is no new automatic attempt until the next eligible lock cycle.

Do not start an infinite polling loop.

1.3 Detect physical tilt correctly

Use TYPE_GRAVITY or an appropriate accelerometer-based fallback.

Measure the angle between the phone's plane and the horizontal desk surface.

Expected examples:

0° = phone lying flat.
45° = phone tilted at 45°.
70° = phone tilted at 70°.
90° = phone approximately vertical.

Only 30°–75° is valid.

Also determine whether the phone is PHYSICALLY in landscape orientation.

Do not simply rely on Activity.requestedOrientation or Configuration.orientation, because the app may force landscape regardless of how the phone is physically positioned.

Use gravity-vector components or orientation sensors to distinguish a phone resting on its long edge from one resting on its short edge.

Support both landscape-left and landscape-right positions where possible.

Reject unreliable or ambiguous sensor readings.

Add smoothing, debouncing, and reasonable hysteresis to avoid flickering at angle boundaries.

1.4 Charging mode settings

Add three user-selectable options:

A. Wireless Charging Only
- Auto-activation requires wireless charging.

B. Wired or Wireless Charging
- Auto-activation requires any supported charging method.

C. No Charging Requirement
- Charging status is ignored.
- Landscape, tilt, and lock-transition requirements still apply.

Default to "Wireless Charging Only", unless existing preferences or established app behavior suggest a safer default.

Use BatteryManager.EXTRA_PLUGGED and the appropriate charging-source constants.

Do not confuse battery status (charging/full) with the actual power-source type.

Handle full batteries and unsupported charging-source reporting gracefully.

Store the selected mode persistently.

1.5 Background and lock-state handling

Evaluate appropriate Android facilities:

- ACTION_SCREEN_OFF
- ACTION_USER_PRESENT
- KeyguardManager
- Display states, including DOZE where available
- Lifecycle-aware event receivers
- DreamService
- Short-lived wake locks
- Foreground services, only where permitted and necessary

Android 12+ background execution and Android 14+ foreground-service restrictions must be respected.

A short PARTIAL_WAKE_LOCK may be used if necessary to complete the bounded scan.

Always release it using safe cleanup, including timeout, cancellation, errors, and lifecycle destruction.

Do not use permanent wake locks for monitoring.

Do not assume sensors remain accessible when the CPU is sleeping.

Do not rely solely on manifest-registered receivers where Android restricts their delivery.

Implement the most reliable achievable solution, with an explicit fallback when a vendor or Android version prevents automatic launch.

==================================================
2. LOCK-SCREEN / AOD CLOCK DISPLAY
==================================================

Allow the clock to be displayed while the phone remains securely locked.

Preferred implementation strategies:

- setShowWhenLocked(true)
- setTurnScreenOn(true), where appropriate
- Proper Activity window and lifecycle handling
- DreamService for compatible charging screen-saver behavior

The clock should appear as a full-screen landscape desk clock.

When possible, it should visually replace the AOD / lock-screen presentation while the desk-clock mode is active.

Important restrictions:

- Do not disable the system lock screen.
- Do not bypass PIN, password, pattern, fingerprint, or biometric authentication.
- Do not call requestDismissKeyguard as a workaround for showing the clock.
- Do not claim third-party apps can universally replace manufacturer-controlled AOD.
- Handle Android background Activity launch restrictions realistically.
- Do not continuously wake the screen if activation fails.

If fully automatic activation is restricted, implement the best available supported path and a clearly explained fallback, such as a configured DreamService or user-action notification.

Do not silently fall back to an intrusive overlay or accessibility automation.

==================================================
3. CLOCK EXIT SYSTEM
==================================================

This is a REQUIRED feature.

Since the clock may cover the Android lock screen, the user must have reliable ways to exit it and unlock the phone normally.

Implement TWO exit methods.

3.1 Exit button

Add a small, visually unobtrusive exit button in the top-right corner of the automatically activated clock UI.

When pressed:

- Exit desk-clock mode.
- Return control to the Android system lock screen.
- Do not unlock the device automatically.
- Stop unnecessary sensors and background work.
- Clear only the window flags and resources associated with this clock session.

Do not break existing settings, brightness, or clock interfaces.

3.2 Swipe up from the bottom

Allow the user to exit the automatically activated clock by swiping upward from the bottom edge.

Requirements:

- Detect a gesture starting within a small bottom-edge activation zone.
- Require a sufficient upward travel distance.
- Ignore small accidental gestures.
- Avoid conflicts with existing vertical widget paging.
- Avoid conflicts with the draggable split divider.
- Work when UI Lock is enabled.
- Work regardless of whether the music widget is active.

Provide a minimal bottom gesture indicator that matches the app's existing visual design.

3.3 Prevent immediate auto-relaunch

CRITICAL:

When the user manually exits the auto-activated clock, the phone may still be:

- Charging
- Tilted at 45°
- In landscape
- Locked

The app must NOT immediately reactivate.

Implement an explicit auto-launch suppression state tied to the current lock cycle.

After manual exit:

- Suppress all additional auto-activation attempts for that lock cycle.
- Wait until the user unlocks the phone.
- Re-arm the system for the next eligible lock transition.

Avoid repeated lock-screen / clock-screen loops.

Handle cases where DreamService may be restarted automatically by the system.

3.4 Compatibility with existing modes

Only apply the new lock-screen exit behavior where appropriate.

Do not break the existing foldable auto-trigger mode.

Do not break manual clock entry on normal phones.

The existing UI Lock feature must never prevent the user from exiting the lock-screen clock.

==================================================
4. STEPLESS, DRAGGABLE DUAL-PANEL LAYOUT
==================================================

Replace the existing fixed dual-panel ratios with continuously adjustable panel sizing.

Currently the app has:

- SINGLE
- DUAL_LEFT_LARGE
- DUAL_RIGHT_LARGE

The current dual layout uses fixed weights around 0.70 / 0.30.

Implement a reusable ResizableDualLayout composable.

4.1 Direct dragging

Users must be able to drag the actual vertical divider between the panels.

The divider should move smoothly with the finger.

The panel widths must update continuously.

Do not replace the divider with a slider in the settings screen.

The divider itself must be draggable.

4.2 Ratio limits

Use a reasonable range such as:

20%–80%

Avoid allowing either panel to become too small for its content.

Use smooth continuous resizing without arbitrary discrete steps.

4.3 Snap to 50%

When the user RELEASES the divider inside the 45%–55% region:

- Snap the divider to exactly 50%.
- Optionally use a short, subtle animation.

Outside that region, retain the chosen ratio.

Do not prevent free dragging through the 45%–55% range.

Snapping should occur when the drag gesture finishes.

4.4 Automatically assign the main panel

The larger panel must display the main clock.

The smaller panel must display secondary widgets.

If the left panel becomes larger, display the clock on the left.

If the right panel becomes larger, display the clock on the right.

At exactly 50/50, retain the previous clock side rather than repeatedly switching.

Handle crossing the center without losing pager state or producing visible layout glitches.

4.5 Persistence

Persist the chosen split ratio and the clock side.

Restore the last arrangement when the app is reopened.

Keep the existing SINGLE mode.

Ensure all clock styles and widgets adapt properly to the available panel size.

4.6 Gesture and UI compatibility

The divider drag must not interfere with:

- Vertical widget swiping
- Clock interaction
- The bottom swipe-to-exit gesture
- Settings interactions
- Media controls

When UI Lock is enabled, prevent accidental resizing.

==================================================
5. MEDIA CONTROL WIDGET
==================================================

Add a fully functional media-control widget to the secondary panel.

The current secondary panel contains:

- Calendar
- Weather

Add a third page:

- Media Control

Preserve the existing vertical swipe mechanism for switching between secondary widgets.

5.1 Supported functionality

Display:

- Current song title
- Artist name
- Album cover
- Playback state
- Previous track
- Play / Pause
- Next track
- Playback progress, when supported

Support multiple Android media players through the system MediaSession framework.

Examples include:

- Spotify
- YouTube Music
- Other compatible Android media apps

Do not build separate proprietary integrations for each music service.

5.2 Android MediaSession integration

Use appropriate Android APIs, including:

- MediaSessionManager
- MediaController
- MediaMetadata
- PlaybackState
- NotificationListenerService, if needed for authorized active-session access

Implement the proper notification-access permission flow.

Do not request accessibility access just to control media.

Gracefully handle:

- No active media session
- Paused playback
- Media player closed
- Missing metadata
- Missing artwork
- Multiple active sessions
- Unsupported playback actions
- Media sessions changing or disappearing

Use only supported media transport controls.

Disable or hide unavailable actions rather than pretending they work.

5.3 Album cover as background

The album cover should be used as the background of the secondary media panel.

The important visual requirement:

DO NOT simply stretch the album cover.

DO NOT crop away large portions of the original cover to fill the panel.

Instead:

1. Preserve the original album artwork aspect ratio.
2. Fit the complete original artwork into the available region.
3. Fill any unused background area by extending the artwork.
4. Use mirrored reflections of the artwork to generate the extended region.
5. Apply a blur ONLY to the extended area.
6. Keep the original artwork clear and undistorted.
7. Use a subtle dark overlay or gradient as needed for text readability.

For example:

If the original artwork is square but the secondary panel is wide:

- Keep the square artwork centered.
- Mirror and extend the left/right artwork edges.
- Blur those extended edges.
- Preserve the original square artwork without stretching.

The same principle applies to tall or unusually shaped panels.

5.4 Smooth transitions

When the song changes:

- Smoothly transition the artwork.
- Avoid flashes of empty or black backgrounds.
- Update media information efficiently.
- Avoid unnecessarily expensive blur operations.

Cache or downsample artwork as appropriate.

Ensure the feature remains lightweight.

5.5 No active playback

When no media is available:

- Display a clean placeholder state.
- Do not show broken artwork.
- Preserve the existing theme.
- Optionally show a short message such as "No media playing".

5.6 Lock-screen privacy

Because the widget may be shown while the phone is locked, provide an option to hide media metadata and artwork during lock-screen clock mode.

Respect platform privacy restrictions.

==================================================
6. OPTIONAL SHIZUKU / ADB / ACCESSIBILITY SUPPORT
==================================================

The application MAY use these capabilities when genuinely needed:

- Shizuku
- ADB shell permissions
- AccessibilityService

However, these are optional enhancement paths, not mandatory dependencies for the basic clock and media features.

6.1 Shizuku

If using Shizuku provides a real improvement to compatible devices:

- Integrate it as an optional advanced feature.
- Detect whether Shizuku is installed and running.
- Request authorization appropriately.
- Handle service disconnection.
- Work normally when Shizuku is unavailable.
- Avoid privileged operations unrelated to the requested clock functionality.

Do not claim Shizuku bypasses every Android security restriction.

6.2 ADB

ADB may be used for:

- Initial setup, where supported
- Development testing
- Granting eligible developer permissions
- Diagnosing device-state and background-launch behavior

Provide reproducible setup instructions for any required commands.

Do not depend on an always-connected desktop computer.

6.3 AccessibilityService

Use AccessibilityService ONLY if a needed feature cannot be implemented adequately through the appropriate normal Android API and the use is technically and policy compliant.

Requirements:

- Explicit user consent
- Clear explanation of what the service does
- Minimum necessary capabilities
- No reading unrelated screen content
- No collection of private information
- No automatic unlocking
- No hidden accessibility actions

Do not introduce accessibility access unless it has a clearly justified purpose.

6.4 Compatibility-first design

Build a clear fallback strategy.

The application must remain functional without Shizuku, root, or AccessibilityService.

If a device cannot support fully automatic AOD activation, keep manual clock entry and other supported features working.

==================================================
7. SETTINGS AND USER EXPERIENCE
==================================================

Integrate new options into the existing settings interface.

Add an "Automatic Desk Clock" section containing:

- Enable/disable non-foldable auto activation
- Charging mode:
  * Wireless Only
  * Wired or Wireless
  * No Charging Requirement
- Lock-screen auto activation status
- Relevant permission/setup status
- Optional advanced integration status
- Lock-screen media privacy option

Include understandable descriptions.

Use the existing app design language rather than introducing an unrelated settings style.

Update all existing localized resources:

- English
- Traditional Chinese (zh-TW)
- Simplified Chinese (zh-CN)

Persist settings across process restarts.

Do not repeatedly request permissions after denial.

Provide a way to retry permission setup from settings.

Preserve the "Not a Foldable Phone" entry path.

==================================================
8. ARCHITECTURE AND REFACTORING
==================================================

The existing MainActivity.kt is already very large.

Avoid putting every new class and composable into MainActivity.kt.

Prefer splitting new responsibilities into focused files, such as:

- sensors/TiltDetector.kt
- sensors/ChargingMonitor.kt
- autostart/AutoClockTriggerController.kt
- autostart/ClockActivationStateMachine.kt
- autostart/LockStateMonitor.kt
- autostart/ClockExitController.kt
- service/ClockDreamService.kt
- ui/ResizableDualLayout.kt
- ui/MediaWidget.kt
- media/MediaSessionRepository.kt
- media/AlbumArtBackground.kt
- settings/AutoClockPreferences.kt

The exact names and structure may be adjusted to fit the existing codebase.

Keep business logic out of composables where practical.

Use lifecycle-aware coroutines and proper cleanup.

Avoid memory leaks, unnecessary background work, and continuously running high-frequency sensors.

For activation state, distinguish at least:

- Manual normal-phone entry
- Existing foldable hinge-triggered entry
- Automatic non-foldable lock-screen entry
- DreamService entry

Do not let cleanup for one entry mode unintentionally destroy another mode.

Pay particular attention to existing onPause(), onStop(), and onDestroy() behavior.

The current activity contains logic that resets foldable device state when leaving the app.

Preserve this behavior for foldable mode, but do not let it accidentally interfere with legitimate non-foldable lock-screen activation.

==================================================
9. BATTERY, DISPLAY, AND SECURITY
==================================================

Power efficiency is important.

Requirements:

- No continuous sensor polling while the user actively uses the phone.
- No unnecessary wake locks.
- Restrict each lock-triggered detection attempt to at most 5 seconds.
- Stop scans after success, timeout, unlock, or cancellation.
- Register and unregister listeners correctly.
- Avoid repeated Activity launch attempts.
- Do not keep unnecessary services running.
- Prefer event-driven behavior.

Preserve existing:

- OLED burn-in protection
- Brightness controls
- Timeout controls
- Existing clock styles
- Full-screen immersive interface

Also:

- Do not disable Android device security.
- Do not automatically unlock the phone.
- Do not bypass biometric authentication.
- Do not expose sensitive information without the appropriate user setting.
- Ensure the user always has a reliable exit path.

==================================================
10. TESTING REQUIREMENTS
==================================================

Implement meaningful automated tests for pure logic where possible.

At minimum, test:

A. Activation eligibility

- Landscape + 45° + wireless charging in Wireless Only mode = eligible.
- Landscape + 45° + wired charging in Wireless Only mode = not eligible.
- Landscape + 45° + wired charging in Any Charging mode = eligible.
- Landscape + 45° + no charging in No Charging Requirement mode = eligible.
- Portrait + 45° = not eligible.
- Landscape + 20° = not eligible.
- Landscape + 80° = not eligible.

B. Lock-trigger behavior

- Eligible conditions while unlocked never auto-launch.
- Lock transition initiates a bounded scan.
- Continuous valid conditions for the stability threshold allow one activation.
- Invalid conditions within the timeout do not launch.
- Scan ends after 5 seconds.
- A charging change alone while unlocked does not launch.
- An unlock cancels a pending scan.

C. Exit behavior

- Exit button closes the automatic clock.
- Bottom swipe exits the automatic clock.
- Manual exit suppresses reactivation for the same lock cycle.
- Unlocking and locking again re-arms auto activation.
- Existing UI Lock does not disable exit.

D. Resizable layout

- Dragging continuously updates proportions.
- Releasing at 45%–55% snaps to 50%.
- Releasing outside that range preserves the chosen ratio.
- Larger panel displays the clock.
- Exactly 50/50 preserves the previous clock side.
- Stored layout is restored correctly.

E. Media

- No active session does not crash.
- Unsupported commands do not crash.
- Missing artwork uses a fallback.
- Metadata updates correctly.
- Artwork changes do not cause memory leaks.

F. Existing functionality

- Foldable hinge-trigger behavior remains unchanged.
- Foldable device-state reset still works as intended.
- Single-panel mode still works.
- Calendar and weather widgets still work.
- Clock styles remain intact.
- OLED burn-in protection still works.

Build the project and run the available unit tests.

Suggested commands:

./gradlew assembleDebug
./gradlew testDebugUnitTest

If additional checks are available, run those too.

Fix all compilation failures caused by your changes.

Do not report tests as passing unless they were actually executed successfully.

Clearly distinguish automated test coverage from hardware-dependent behavior that still requires real-device testing.

==================================================
11. IMPORTANT ANDROID RESTRICTIONS
==================================================

This project targets modern Android versions.

Do not rely on outdated tricks that no longer work on Android 12+.

In particular, investigate and handle:

- Background Activity launch restrictions
- Foreground-service restrictions
- Notification access requirements
- Lock-screen window visibility
- AOD / doze restrictions
- Sensor availability after the screen turns off
- OEM-specific restrictions on screen activation
- Media session permissions
- Android 15/16/17 behavioral differences where applicable

Do not assume a normal Android application can unconditionally launch a full-screen Activity from the background.

Do not silently introduce unsupported full-screen intent, overlay, or accessibility behavior as a workaround.

Prefer documented Android mechanisms.

If some behaviors are impossible on a particular device without unsupported privileges:

1. Implement the closest safe and supported behavior.
2. Keep the rest of the feature functional.
3. Add a sensible fallback.
4. Clearly explain the specific limitation.
5. Do not misrepresent partial support as universal success.

==================================================
12. IMPLEMENTATION PRIORITY
==================================================

Implement in the following order:

PHASE 1 — Foundation
- Inspect and refactor relevant existing code.
- Separate foldable and non-foldable logic.
- Add tilt and physical landscape detection.
- Add charging-source detection and settings.
- Add lock-triggered 5-second detection state machine.

PHASE 2 — Lock-screen Clock
- Implement the supported automatic activation path.
- Integrate lock-screen window handling.
- Add DreamService support where useful.
- Add the exit button.
- Add swipe-up-to-exit.
- Add lock-cycle suppression after manual exit.

PHASE 3 — Resizable Layout
- Replace hardcoded 70/30 panel sizing.
- Add a directly draggable divider.
- Add 45%–55% center snapping.
- Automatically assign clock to the larger panel.
- Save and restore layout state.

PHASE 4 — Media Widget
- Integrate Android MediaSession.
- Add media transport controls.
- Add the third secondary-widget page.
- Implement mirrored, blurred album-art extensions.
- Add playback and metadata state handling.
- Add lock-screen privacy options.

PHASE 5 — Integration
- Add any genuinely necessary optional advanced integration.
- Update settings and localization.
- Address lifecycle and power-management concerns.
- Run builds and tests.
- Fix regressions.
- Document hardware-dependent limitations.

==================================================
13. FINAL ACCEPTANCE CRITERIA
==================================================

The implementation is complete when:

[ ] Existing foldable 30°–120° hinge detection is unchanged.
[ ] Existing foldable clock and cover-screen behavior is preserved.
[ ] Normal phones retain the manual bypass.
[ ] Non-foldable tilt detection works at 30°–75°.
[ ] Physical landscape orientation is required.
[ ] Charging modes are configurable.
[ ] Auto activation is initiated by locking the phone, not by simply moving or charging it.
[ ] Each activation scan lasts no more than 5 seconds.
[ ] Stable conditions are required before attempting activation.
[ ] The clock never interrupts an actively used phone.
[ ] A supported lock-screen display path is implemented.
[ ] The exit button works.
[ ] Bottom swipe exit works.
[ ] Manual exit prevents immediate reactivation.
[ ] The system lock screen remains secure.
[ ] Dual-panel sizes can be freely adjusted by dragging.
[ ] Releasing in the 45%–55% range snaps to 50%.
[ ] The clock automatically occupies the larger panel.
[ ] Media control works with authorized active media sessions.
[ ] Album artwork uses mirrored and blurred extensions without distorting the original cover.
[ ] Existing calendar, weather, clock, and UI-lock features still work.
[ ] New preferences persist.
[ ] All three existing languages are updated.
[ ] The project compiles successfully.
[ ] Automated tests pass where runnable.
[ ] Hardware-specific limitations and remaining manual tests are documented.

==================================================
14. EXPECTED FINAL DELIVERABLE
==================================================

Make actual code changes in the repository.

At the end, provide:

1. A summary of implemented functionality.
2. The files created and modified.
3. The architecture decisions.
4. Any added dependencies and permissions.
5. Instructions for enabling auto activation, notification access, or screen-saver support.
6. Any optional Shizuku or ADB setup instructions, if used.
7. Build and test results with exact command outcomes.
8. Known Android/OEM limitations.
9. A real-device test checklist.
10. Any features that could not be fully implemented and the specific reason.

Do not stop after writing a plan.

Implement as much as technically possible.

Do not replace hard engineering problems with placeholder functions or fake implementations.

If a feature cannot be universally supported, implement a truthful best-effort solution with a proper fallback.

Keep the existing LinzinDeskClock design identity, preserve backwards compatibility, and prioritize reliable real-world Android behavior over theoretical API support.

Begin by inspecting the repository and implementing the changes.
