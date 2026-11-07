# Version 2 implementation plan

Specification: v2-requirements.md. Preserve minSdk 31, targetSdk 37 and the existing foldable 30–120 degree hinge range. Work on feat/desk-clock-v2 in the dedicated D-drive checkout.

- [ ] Foundation: pure gravity/charging eligibility and lock-cycle state machine; tests for timing, discontinuous samples, invalid sensors, charging sources, unlock and suppression. Android adapters register sensors only during a bounded attempt.
- [ ] Locked clock: user-enabled special-use foreground monitor started from visible settings, event receivers, time-limited wake lock, one attempt per lock. Reuse a foreground clock window only when still eligible; otherwise a user-action notification. Independent secure session Activity and user-configured DreamService; no keyguard dismissal or privileged workaround.
- [ ] Layout: hoisted SplitLayoutState, direct divider drag, release-only centre snap, stable larger-panel clock side and persistent migration from existing dual modes. Preserve pager state.
- [ ] Media: authorised system sessions, stable selection, callbacks/cleanup, supported controls, bounded progress, fitted artwork with blurred reflected margins and privacy.
- [ ] Integration: settings and all three languages, mode-scoped lifecycle cleanup, keep manual bypass and foldable device-state reset. Suppress tutorials/permission prompts in locked/dream sessions.
- [ ] Verification: GitHub-hosted debug/unit/lint workflow where executable; establish toolchain under D, capture exact outcomes, add real-device checklist and platform limitations. No assertion of hardware acceptance from JVM tests.

Testable boundaries: GravityOrientation, ActivationEligibility, ClockActivationStateMachine, SplitLayoutState and media selection/progress. Runtime cleanup uses Android lifecycle and explicit close/cancel ownership. Persist settings and lock-cycle suppression; process restart while locked never creates a new lock attempt.
