# Design QA — launch and legal onboarding

## Target

MyNaviScooter's selected dark navy mobile visual direction: high-contrast white typography, orange performance accent, green safety confirmation, rounded technical panels, and restrained futuristic spacing.

## Evidence

- Android API 35 emulator, 1080 × 2400 capture.
- `onboarding-terms.png`
- `onboarding-liability.png`
- Existing dashboard instrumentation captures.

## Review

- Hierarchy: passed. Titles, progress labels, warning title and primary actions are clearly separated.
- Contrast: passed. Primary and secondary copy remain legible against the navy surfaces.
- Safety: passed. Consent is unchecked by default and the primary action remains disabled until acknowledgement.
- Responsiveness: passed at the test viewport. Content remains inside system insets and the liability screen is scrollable.
- Brand match: passed. Orange, green, navy, rounded geometry and typography remain consistent with the selected dashboard direction.
- Interaction: passed. Terms → liability → acceptance → dashboard is covered by the Android instrumentation test.
- P2 correction: the first terms panel initially stretched into unused vertical space; changed to content height with a flexible spacer before the CTA.

## Remaining polish

- P3: add localized PT/EN/DE legal copy when the application-wide language system is introduced.
- P3: capture the animated splash as video on physical hardware; static emulator QA cannot represent its timing.

final result: passed
