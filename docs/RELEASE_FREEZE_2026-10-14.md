# Follow Money — feature freeze, 14 October 2026

The owner closed new-feature work for this publication cycle. The remaining scope is reported defect fixes, validation, release packaging and Google Play preparation. No new features, libraries or redesign projects are planned for this cycle.

Limited owner-approved exception on 15 October: add a compact transparent presentation of the existing daily remaining amount for LockStar/compatible hosts. This does not change financial rules or reopen other feature work. The implementation requires device/host acceptance testing; compilation is not proof of actual lock-screen placement.

Owner-provided screenshot on 15 October confirms that the transparent widget is visible in the chosen lower lock-screen position while notifications remain visible above it. This validates that pictured arrangement only; background refresh, additional notification load, AOD, hidden amounts and other device/host versions still require testing. The screenshot and personal wallpaper are not included in the repository. Claude's audit was received and checked on 15 October; the owner subsequently approved five focused defect fixes, including v21 time precision. Their implementation and validation are recorded in the [audit triage](CLAUDE_AUDIT_TRIAGE_2026-10-15.md). Broad refactoring remains deferred.

## Frozen scope

- Local supported SMS/payment-notification capture, manual entries, source review, account/card links, classification and separate currencies.
- Existing daily budgets, commitments, widgets, account movements, analytics and completed-month summaries.
- Reimbursement selection, impact preview, confirmation and undo; related-detail navigation and saved list context.
- Optional points rounded per day, with a confirmed balance, yesterday/today summary and non-monetary 100-point milestones. Today remains provisional; coverage confirmation and original-purchase-date refund effects remain required.
- The muted opening video uses `AUDIOFOCUS_NONE` so it does not request focus from music applications. Playback alongside Spotify on the owner's phone has not been verified.

The application is now version **0.2.0 / versionCode 3**, minimum API 26 and target API 36. Local signed APK/AAB artifacts were built and verified on 15 October; no upload has occurred. Assign distinct version codes to future uploaded builds.

The owner subsequently requested a shareable APK for friends. Packaging-only `beta` uses a separate application ID, launcher/widget labels and signing key; it does not change financial rules or reopen feature work. Beta data does not migrate automatically into production. API 26 runtime and real-device security/lock-screen acceptance are still required before treating tester distribution as release approval.

The signed Beta APK (0.2.0-beta / code 3) built offline, passed signature/actual APK manifest/Dex/privacy-asset checks and lint (0 errors, 66 warnings). Its signer matched the dedicated local Beta certificate and differed from the upload certificate. It installed and remained running beside the original on the API 36.1 emulator with distinct app IDs/data directories; original package identity/version/install timestamps were unchanged. No original package update, phone install or Play upload occurred. This is a packaging/launch smoke check, not an API 26 or comprehensive Beta acceptance pass.

## Validation at this checkpoint

- After the owner-approved audit fixes on 15 October: 412 JVM checks across 14 suites, 10 focused native Android regressions, production debug/instrumentation APK builds, lint (0 errors, 66 warnings) and repository preflight passed. The original freeze evidence below is retained for context; broader UI/device acceptance remains separate.

- Subsequent QA on the owner's running Medium Phone emulator: 69 native Android tests passed (18 base, 14 phase, 27 features, 10 audit); the installed v20→v21 APK update retained synthetic data/evidence/budgets/points; 61 journey UI checks passed. The latest visual/notification run passed 46 checks, including a white-background widget preview and points screenshots without the export toast. White widget text remains weak on white backgrounds; the grey day-confirmation button remains a deferred cosmetic item. Release lint passed with 0 errors and 66 warnings. See the audit triage for signing evidence and remaining host/lock-screen limits.

- 401 standalone JVM checks passed across 14 suites, including 21 points checks for signed rounding boundaries, provisional/review exclusions and balance/history agreement.
- Debug app and instrumentation APK builds passed; Android lint reported **0 errors and 66 warnings**. `tools/run-checks.ps1` completed successfully on 14 October.
- Repository preflight and staged-file review exclude generated binaries, signing material, local financial evidence and unrelated agent configuration.
- The redesigned points dialog's native UI test did **not** complete: emulator graphics/boot failures prevented runtime verification. Updated UI expectations compile, but compilation is not a UI-test pass. Historical emulator results do not cover the latest points design or opening-audio change.
- No phone data was exported, repaired, cleared or rescanned for this freeze. The owner's installed build is not proven to match this source.

## Known issues and remaining acceptance checks

- The five approved audit fixes have focused synthetic regressions, including a successful v20→v21 upgrade test. Legacy time precision remains unknown: no heuristic relabeling of historical noon timestamps or automatic real-device rescan was performed. New date-only captures display a date-only label rather than the placeholder clock.
- Points and reimbursement journeys were reviewed on the Medium Phone emulator, including light/dark summaries, confirmed/provisional/negative states, history/help, confirmation, undo and Back. Physical-device/large-font acceptance remains separate.
- Verify opening playback with background music, new SMS/selected notifications while closed, permission denial/revocation, historical import deduplication, widgets and battery-constrained daily/monthly refresh.
- The broader Android reconciliation suites subsequently passed on the owner's Medium Phone emulator with SystemUI active. The signed release, oldest-supported-Android update path, actual lock-screen notifications and Samsung LockStar/AOD still require acceptance testing; this emulator did not enter a showing keyguard state.
- Keep user-edited records and original financial evidence intact. Any real-device financial export/repair requires an explicit separate scope.

## Google Play gates

- No developer account exists. Registration/payment is planned for **1 November 2026**; this is not a promised public-release date.
- Resolve Personal versus Organization eligibility for this local budgeting app before paying. The existing [closed-testing plan](CLOSED_TESTING_PLAN.md) contains the policy question; no answer or Google approval is claimed.
- A new Personal account requires at least 12 continuously opted-in closed testers for 14 days before requesting production access. Prepare 15–20 testers and record useful feedback. APK distribution outside Play does not start this period.
- The existing signed release AAB is dated 12 October and predates this freeze. Rebuild/sign the final corrected source, verify release Dex/manifest and preserve encrypted upload-key recovery. Signing material and binaries remain outside Git.
- Finish the SMS Permissions Declaration and synthetic-data reviewer video, Data Safety/financial-feature declarations, reviewer instructions, audience/content rating, final store graphics/screenshots and listing review.
- The public privacy URL returned HTTP 200 on 14 October; confirm the final policy and artifact remain consistent before upload.

See [Google Play preparation](GOOGLE_PLAY_RELEASE.md) and [Arabic usage](USAGE_AR.md). Publication and restricted-SMS approval remain subject to Google's review.


## Owner-approved cancellation/repayment repair, 16 October

The owner authorized a separate PR to address visible cancellation history, corrected notifications, explicit credit-card repayment review/undo, optional allocations and confirmed statement fees after a real incident. This is a scoped exception to the feature freeze. Implementation uses additive schema v22 and synthetic emulator fixtures; it does not authorize rewriting the owner's ledger, rescanning the phone, replacing the appealed APK, signing a release, or merging this PR. The owner will review and merge. Current accounting contracts and validation are described in ARCHITECTURE.md and the PR.
