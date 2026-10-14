# Follow Money — feature freeze, 14 October 2026

The owner closed new-feature work for this publication cycle. The remaining scope is reported defect fixes, validation, release packaging and Google Play preparation. No new features, libraries or redesign projects are planned for this cycle.

## Frozen scope

- Local supported SMS/payment-notification capture, manual entries, source review, account/card links, classification and separate currencies.
- Existing daily budgets, commitments, widgets, account movements, analytics and completed-month summaries.
- Reimbursement selection, impact preview, confirmation and undo; related-detail navigation and saved list context.
- Optional points rounded per day, with a confirmed balance, yesterday/today summary and non-monetary 100-point milestones. Today remains provisional; coverage confirmation and original-purchase-date refund effects remain required.
- The muted opening video uses `AUDIOFOCUS_NONE` so it does not request focus from music applications. Playback alongside Spotify on the owner's phone has not been verified.

The application remains version **0.2.0 / versionCode 2**, minimum API 26 and target API 36. No upload has occurred; assign distinct version codes to future uploaded builds.

## Validation at this checkpoint

- 401 standalone JVM checks passed across 14 suites, including 21 points checks for signed rounding boundaries, provisional/review exclusions and balance/history agreement.
- Debug app and instrumentation APK builds passed; Android lint reported **0 errors and 66 warnings**. `tools/run-checks.ps1` completed successfully on 14 October.
- Repository preflight and staged-file review exclude generated binaries, signing material, local financial evidence and unrelated agent configuration.
- The redesigned points dialog's native UI test did **not** complete: emulator graphics/boot failures prevented runtime verification. Updated UI expectations compile, but compilation is not a UI-test pass. Historical emulator results do not cover the latest points design or opening-audio change.
- No phone data was exported, repaired, cleared or rescanned for this freeze. The owner's installed build is not proven to match this source.

## Known issues and remaining acceptance checks

- Date-only HSBC text such as `14OCT26` still receives an artificial noon timestamp. Fix/label time provenance before publication; the message timestamp is not a bank-confirmed transaction time.
- Check the points dialog visually in light/dark mode, confirmed/pending/negative states, history/help, confirmation and Back navigation.
- Verify opening playback with background music, new SMS/selected notifications while closed, permission denial/revocation, historical import deduplication, widgets and battery-constrained daily/monthly refresh.
- Re-run synthetic Android database/reconciliation tests on a working disposable emulator and test the signed release and an update path on the oldest supported Android version.
- Keep user-edited records and original financial evidence intact. Any real-device financial export/repair requires an explicit separate scope.

## Google Play gates

- No developer account exists. Registration/payment is planned for **1 November 2026**; this is not a promised public-release date.
- Resolve Personal versus Organization eligibility for this local budgeting app before paying. The existing [closed-testing plan](CLOSED_TESTING_PLAN.md) contains the policy question; no answer or Google approval is claimed.
- A new Personal account requires at least 12 continuously opted-in closed testers for 14 days before requesting production access. Prepare 15–20 testers and record useful feedback. APK distribution outside Play does not start this period.
- The existing signed release AAB is dated 12 October and predates this freeze. Rebuild/sign the final corrected source, verify release Dex/manifest and preserve encrypted upload-key recovery. Signing material and binaries remain outside Git.
- Finish the SMS Permissions Declaration and synthetic-data reviewer video, Data Safety/financial-feature declarations, reviewer instructions, audience/content rating, final store graphics/screenshots and listing review.
- The public privacy URL returned HTTP 200 on 14 October; confirm the final policy and artifact remain consistent before upload.

See [Google Play preparation](GOOGLE_PLAY_RELEASE.md) and [Arabic usage](USAGE_AR.md). Publication and restricted-SMS approval remain subject to Google's review.
