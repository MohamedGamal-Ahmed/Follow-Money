# Change log

## Unreleased

- Add a from/to date picker to the existing transactions period filter, keeping the last day inclusive in the list.
- Replace the home «آخر 7 أيام» card with the month statement from the pay day to today: «دخل» (salary vs transfers that arrived), «خرج» = day-to-day spending + commitments + transfers to people + cash withdrawals, «باقي من دخل الفترة», one line per component opening exactly its rows; own-account moves and repayments to the user's own credit cards are internal, a purchase paid from cash is detail only, and an unlinked transfer worth a quarter of the income or more shows a link warning. The widget follows the same function.
- Read HSBC's trailing-sign alerts by their sign: `EGP 100.00+` is income, `EGP 100.00-` spending, the dated descriptor (TT Payment, Phone Banking Transfer, ATM Cash Withdrawal, Charges) is the merchant and the row shows the bank; a one-time repair flips credits older versions booked as spending and fixes their names, leaving user-edited and linked rows alone.

- Limit the home page's recent transactions query to the five displayed rows while preserving ledger ordering.
- Recognize explicit ChatGPT/OpenAI subscription payments as recurring suggestions and cover the first-to-next-cycle SMS journey.

- Add an owner-requested private tester APK variant with a separate Beta package, labels, signature permission and dedicated local signing key; preserve production app data and identity.

- Prepare versionCode 3 signed distribution artifacts; add an explicit lock-widget privacy notice and review clean points screenshots plus white-background widget readability. Keep oldest-Android and actual Samsung lock-screen acceptance open.

- Verify an installed APK update from `53b4682` (schema v20) to v21 with synthetic ledger, repayment, evidence and points snapshots. Add native light/dark visual checks and improve points-dialog action text contrast in dark mode.

- Preserve reimbursement endpoints, fee parents and commitment origins during transaction merges; leave conflicting automatic merges for review and reject own-transfer conversion of linked repayments.
- Count historical-import ledger growth without the 500-row display cap, and recognize Arabic sterling before generic Egyptian-pound tokens.
- Respect amount hiding in capture notifications, provide a generic lock-screen public version, and prefix formula-like CSV text cells with an apostrophe while retaining numeric amount cells.
- Add nullable v21 transaction time precision. Preserve unknown precision on legacy rows; label new date-only captures/imports and compare reimbursement eligibility by day when either endpoint has date-only precision.

- Add the owner's explicitly requested compact transparent lock-screen widget variant, reusing daily-summary calculations and privacy settings; keep home widget layouts unchanged and leave placement to the host.

- Freeze the existing feature scope on 14 October 2026; restrict the publication cycle to defect fixes, validation and Google Play preparation. Record known issues and verification limits in the release checkpoint.

- Prevent the muted opening video from requesting audio focus and interrupting background music.

- Present reimbursement selection in a native bottom sheet with selectable cards and a separate review action; display the linked purchase's current personal share under the incoming transaction and in its details.

- Guide reimbursements through same-day chronological selection, an explicit older-purchase search, read-only impact preview, guarded confirmation, success and undo.
- Preserve related transaction parents and screen/query/filter scroll context, retain details after editing, and return daily-budget/points drilldowns to their initiating dialog.
- Add optional currency-separated daily points with gains/deductions rounded to whole points per day, frozen observed daily limits, provisional today and revocable coverage confirmation for closed dates. Recompute historical scores after edits/refunds without awarding incoming repayments twice. Separate confirmed balance, yesterday and today's estimate, with 100-point milestones and a separate history/help view.

- Add closed-month summaries with currency-separated totals, spending behavior, transaction drilldowns and coverage-confirmed comparisons that invalidate after data changes.
- Add a neutral monthly notification with catch-up/deduplication, an independent opt-out, and a home card that disappears after reading while reports remain available in Analytics.

- Aggregate account analytics through confirmed card/account links, include archived linked cards in historical activity, and keep unrelated same-bank sources separate. Show own-account transfer direction separately from recorded inflows and external outflows.
- Identify unlinked card views and offer the existing explicit linking flow; retain the selected account across activity recreation.
- Add the Egyptian maker signature to Settings, onboarding, the opening and bilingual promotional templates.

- Route SMS and Notification Access permission entry points through disclosures; explain the external system speech provider before voice entry.
- Bundle a bilingual privacy policy, generate its public HTML from the same source, and document Google Play declaration/signing gates.
- Configure upload signing through local environment variables; enable release shrinking and restrict developer financial review tools to debug builds.

- Target Android 16 / API 36 with AGP 8.9.1 and Gradle 8.11.1; apply native system/keyboard insets and retain legacy back handling through the documented predictive-back opt-out.

- Parse completed payments independently of future fees/installments; reject future-only colloquial Arabic alerts.
- Preserve historical rejected captures and flag them for review instead of archiving them; retain user edits.
- Calculate reimbursement offsets on reading without shrinking the saved link, including multiple refunds and edit/reopen round trips.
- Add Arabic email support drafts, reviewed metadata and clipboard fallback; retain GitHub as an optional developer route.

- Organize Settings into six focused groups; keep review and destructive tools under Data and Privacy.
- Add GitHub bug/feature links and a preview of basic support metadata.
- Route users to a separate public support repository while keeping application source private.
- Add Arabic issue forms, a pull request template, ownership rules and monthly dependency update proposals.
- Add build/lint/JVM CI, repository privacy checks and portable local validation commands.
- Restrict automatic build-tool proposals to patch updates; larger AGP/Gradle upgrades require a coordinated compatibility review.
- Replace historical project notes in the main README with current setup, usage and calculation boundaries. Private financial audits remain local.

## 0.2.0 — development

- Arabic native UI for transactions, money flow, accounts and recurring payments.
- Local SMS and selected notification capture, editable categorization and source review.
- Daily allowances, budgets, reminders and three resizable widgets.
- Local financial insights, refund/reimbursement links and installment schedules.
- Corrections to duplicate reconciliation, future-payment detection and daily totals.

This documents the development state, not a published or signed release.
