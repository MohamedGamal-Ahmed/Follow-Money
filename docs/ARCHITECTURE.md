# Architecture

Follow Money is a single Android application module using Java 17, native views and SQLite. It has no application backend or network client.

## Data path

1. `SmsReceiver`, `SmsImportActivity` and `PaymentNotificationListener` receive supported sources; the user can also add or share text manually.
2. `ExpenseParser` and identity helpers extract conservative event, amount, currency and account information. An ambiguous input may remain unrecognized or require review.
3. `ExpenseDb` stores transactions, account identities, commitments, corrections and source associations. `EvidenceVault` protects stored source text using Android Keystore.
4. `RepairRules`, `MoneyTotals`, `MoneyFlow`, `DailyAllowance` and `Insights` derive summaries from the ledger and confirmed relationships.
5. `MainActivity`, widgets and reminders display the derived state. Changes trigger the relevant refreshes.

Consult class definitions for field and migration contracts; database schema numbers in historical notes are not authoritative.

Schema v21 adds nullable `transactions.time_precision` (`exact` or `date_only`). The v20 upgrade only adds the column; legacy rows remain null, including on re-import. `ExpenseParser.eventTimeWithPrecision` records the precision of the timestamp chosen at capture: an explicit date without a clock is `date_only`, an explicit clock or receipt-time fallback is `exact`. The latter does not prove a bank-stated event time. New CSV rows have date precision. UI list/detail/import preview labels date-only rows instead of showing their internal placeholder clock. Reimbursement selection, preview and confirmation use the same day-based eligibility when either endpoint is date-only; unknown legacy precision retains the prior timestamp rule.

`ExpenseDb.mergeEntries` moves reimbursement endpoints, fee-parent references, recurring origins and source evidence inside a transaction. Conflicting repayment targets/allocations or fee parents leave automatic copies marked for review. Own-transfer reconciliation rejects repayment-linked rows before changing their kinds. Historical import reports an uncapped net ledger increase, distinct from the number of processed SMS.

Capture notifications use private visibility and a generic public version; hidden amounts also remove merchant/card details from the private notification. CSV text columns prefix formula-like values with an apostrophe and escape quoted fields. Numeric amount columns stay numeric. Spreadsheet save/reopen behavior is not a universal security guarantee, and re-import retains the protective apostrophe as text.

## Financial boundaries

- Amounts use integer minor units. Aggregation stays within a currency.
- An account balance starts from an explicit statement anchor when available. Later adjustments are estimates and must be labeled.
- Transfers between confirmed own accounts do not become income or spending. Unknown transfer recipients need review.
- A scheduled installment is a future obligation. Creating a plan does not create a paid transaction.
- Reimbursement/refund links adjust eligible spending and incoming money, while preserving the linked records. Do not erase the source transaction to fix a summary.
- Daily allowance is a user-defined limit. Salary or incoming transfers do not automatically enlarge it.
- Source deduplication and financial reconciliation are separate: nearby payments are not proof of duplication.

## UI and support

Settings is organized by task: budgets/widgets, capture, appearance/profile, reminders, data/privacy, and help. Financial review tools are kept under Data and Privacy. Assets/debts shortcuts appear only when there is existing data in those modules.

`GitHubSupport` constructs browser URLs using encoded fields. The user reviews device metadata before opening a bug form. No issue is submitted automatically, and no financial data is included in that metadata.

## Transaction journeys and daily points

`TransactionJourney` preserves the parent detail chain. `MainActivity` keeps bounded scroll positions by screen/query/filter context and saves them, transaction parents and drilldown return targets in the activity state bundle. A transaction edit finishes in the same details screen. Budget and points drilldowns return to their initiating dialog.

Reimbursement candidates default to the incoming transaction's local calendar day, sorted by timestamp/id descending, without an eight-item cap. An explicit older-purchase branch includes eligible earlier records. `previewReimbursement` is read-only and projects the purchase day's living total; `confirmReimbursement` rechecks the preview under a transaction. Undo deletes only that exact income/expense/amount link. Gross ledger records remain unchanged.

The native picker is a bottom-aligned dialog with selectable cards, checked-state accessibility, a scrollable candidate area and a separate review button. Returning from the impact preview retains the selection and search. Incoming transaction rows and details derive a personal-share caption from valid effective reimbursement offsets, including all repayments against that purchase; they retain the gross incoming amount.

`DailyPointsStore` stores opt-in, the first observed daily limit per currency/date and explicit coverage confirmations in local preferences; it never writes transactions or cached score totals. No historical budget anchors are inferred. `DailyPoints` derives each day's points from `(limitMinor - effectiveLivingMinor) / 1000` using `BigDecimal`, then rounds to a whole number with `HALF_UP` (ties away from zero). The balance sums those rounded daily awards so it matches the visible history. Only closed, coverage-confirmed dates without review-flagged rows contribute; today remains provisional. Edits/refunds recompute the relevant purchase-date score using its anchored limit. Coverage is a revocable user assertion of complete recording, rather than a guarantee of bank/SMS coverage. Disabling keeps the history; deleting all local data also clears points preferences. The points summary separates the confirmed balance, yesterday and provisional today; the next 100-point milestone is visual encouragement only, not a monetary reward. History and calculation guidance are separate from the initial summary.

`JourneyUiTest` is restricted to an emulator and the separate `.journeyqa` package. It exercises real dialogs and Back using a synthetic private database, including preview/cancel/confirm/undo, related-detail parents, search scroll restoration and points review/coverage. Captures stay local and ignored.

`AuditRegressionTest` runs with `-e feature_audit regressions` on a disposable emulator. Its isolated fixtures cover merge relationships/conflicts, own-transfer rejection, imports above 500 rows and rollback, notification privacy, CSV escaping, time eligibility and the frozen `androidTest/assets/schema-v20.sql` migration fixture. These are functional database/view-construction checks, not full visual or physical-device acceptance tests.

`UpgradeInstallTest` is a separate instrumentation runner for a fresh `.upgradeqa` install. Its `seed` mode runs with the archived v20 APK; after an in-place APK update, `verify` compares all old ledger columns, financial preferences, encrypted and decrypted evidence, repayment effects and points, checks null legacy precision/integrity, and opens the real updated activity. `ReleaseVisualTest` runs with `-e feature_audit visual` only in `.releasecheck.journeyqa`, captures native light/dark views, a compact RemoteViews preview and a posted notification, and generates a synthetic CSV through the actual export callback. SystemUI screenshot review and local Excel opening are separate manual checks. QA binaries, snapshots and captures remain local and ignored.

## Monthly summaries

`MonthlyReport` derives closed-calendar-month totals from the same effective `MoneyFlow.Item` amounts as Analytics, with separate currency summaries and explicit behavior thresholds. Coverage confirmations are keyed by currency/month and hashed effective rows; changed imports, edits or reimbursements invalidate the relevant confirmation. An empty or zero prior denominator never produces a percentage.

`MonthlySummaryReceiver` uses an inexact local first-of-month alarm from 09:00 and catches up on activity resume, date/time changes and reboot. It stores the start, latest notified and latest seen months in local preferences. Notifications contain no amounts or merchants, require system permission/channel availability and recorded activity, and deep-link to the completed month. Reading hides the home entry while retaining the Analytics report. This scheduling does not promise delivery while Android force-stops or restricts the app.

## Widgets

`LockSummaryWidget` is a separate transparent, compact provider for compatible widget hosts. It reads `DailySummary`, the selected widget currency and the existing hide-amounts preference; it has no independent financial calculations or persistent settings. `SummaryWidget.refresh()` includes its installed instances in existing refresh paths. It displays only an exact two-decimal remaining/overage value and caption, clears values and accessibility text on unavailability, and opens the app through a normal immutable PendingIntent. The host owns placement, padding and the notification region. The three existing home widget layouts retain their presentation.

## Validation

Standalone JVM tests cover parser, time/budget, money-flow, repair and insight rules. Android instrumentation covers SQLite reconciliation, feature integration and widgets using synthetic data. `tools/run-checks.ps1` is the shared entry point for local checks and CI; Android instrumentation is optional and emulator-only. `tools/check-repo.py` checks documentation links, support form field consistency, pinned CI actions and excluded private artifacts.
