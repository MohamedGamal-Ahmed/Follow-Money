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

## Cancellation history and credit-card repayments

Schema v22 adds `capture_events`, `card_settlements`, and `card_settlement_allocations`. Active transaction and total queries are unchanged. Explicit bank cancellations archive the matching debit and its dependent fees, preserve both encrypted debit/cancellation evidence, and create a visible non-financial event. Old cancellation archives are backfilled without inventing missing source text; an unreadable legacy archive payload no longer blocks the upgrade: its exact payload is kept and an explicit canceled event with unavailable details/unknown amount is shown.

Schema v23 adds nullable `capture_events.display_at` with index `(display_at DESC,id DESC)` and table `sms_import_receipts(unique_key)`. `display_at` is the archived operation date when readable, otherwise the receipt/archive time; v22 rows are backfilled once. Timeline reads filter and order by this indexed column in SQL. Import receipts record every processed historical SMS hash (including balance-only and hint messages) and are cleared by delete-all-data. An existing "recorded" notification is replaced by a privacy-aware cancellation notice; its destination is an immutable event ID, separate from reusable transaction row IDs.

Matching requires a single outgoing transfer (or an expense already classified as a transfer) with the same bank/source identity, currency and gross amount, a receipt timestamp within the preceding two minutes, readable original debit proof, no conflicting bank reference, and no reimbursement links. Fee children are not candidates. Edited rows are protected, except explicitly confirmed card settlements. Ambiguous/unmatched cancellations remain visible and do not change totals. Ambiguous eligible rows can be selected and reviewed manually; confirmation rechecks source identity and all matching conditions. Bank-confirmed repayments with a linked transfer fee are rejected with an explicit explanation; fee-bearing repayments are not supported and the transfer/fee rows stay unchanged. Repeated messages are idempotent; an earlier debit arriving after its cancellation is reconciled using source timestamps. Identical bank aliases use the same cancellation fingerprint.

`CardSettlements` requires explicit account/card selection and review, revalidates the preview inside a database transaction, and changes only the selected outgoing debit into an own transfer. Optional allocations link that transfer to prior purchases on that exact credit card in the same currency, capped by outstanding gross purchase cost. Date-only rows use calendar-day eligibility. The unallocated remainder remains visible on the card repayment; it never becomes an invented fee. Undo restores the debit's original classification/source and removes only its allocations. A canceled confirmed repayment releases allocations without deleting the original card purchases or statement fees; the archived repayment retains its allocation payload.

Statement fees are explicitly entered and confirmed from a statement, in the purchase currency and on its purchase day. They inherit the purchase's card/category, retain the user's description as manual evidence, and have date-only precision. There is one statement-fee entry per purchase; an identical retry is idempotent. This flow does not infer tax, exchange rates, fee amounts or a fee time from a bank balance. Linked purchases/repayments/fee rows cannot be edited/deleted or merged until allocations are undone; related source identities cannot be deleted/merged while a repayment is linked. Re-import cannot rewrite allocated purchases. Purchase and repayment allocations never reduce gross consumption a second time.

The card journey reuses the reimbursement bottom-sheet window and existing typography, theme colors, rounded surfaces, account labels and buttons. Account/card selection uses logo-bearing radio cards with type and last-four labels rather than native dropdowns. It preserves selection when returning from review and provides a result/undo step. Cancellation events are interleaved with active transactions by the archived operation date, with an × icon, canceled badge and struck-through amount. Archived kind/category/account metadata participates in filters; unresolved notices use their receipt date and do not impersonate a ledger row. Canceled rows are excluded from totals and bulk editing. Legacy records without readable operation metadata fall back to an explicitly labeled archive/receipt timestamp. Timeline reads include cross-month cancellations and are not capped at 100 events. The original SMS may be unavailable for a legacy archive or after device key loss; that limitation is displayed rather than guessed.

Synthetic regression: a card purchase of 220.00 plus a confirmed statement fee of 6.60 is allocated 226.60 from a repayment of 227.00; 0.40 remains unallocated. Cancellation archives the repayment, retains both source messages, and reopens 226.60 of purchase cost. A successful last-contactless-use warning is not a decline; a real rejection is never booked as spending. No real phone ledger rescan, repair, installation or release artifact modification is part of this change.


Verification of implementation commit `9149f3e`: `tools/run-checks.ps1` passed all 17 JVM suites, production debug/test APK builds and lint (0 errors, 66 existing warnings). `tools/run-card-journeys.ps1 -Device emulator-5556` passed 112 native Android tests (18 base + 14 phase + 27 features + 31 audit + 22 card/cancellation), 123 card journey UI checks (41 each in light/dark/font-scale 1.3) and 61 existing reimbursement journey UI checks. Actual pending-intent delivery is awaited, and captures wait for compositor presentation. Synthetic `RealDataReplay` retained 226.60 of purchase/statement-fee spending after removing the canceled 227.00 repayment. These are synthetic emulator results, not a real phone replay or installation. [Reviewed screenshots and provenance](screenshots/card-settlement-2026-10-16/README.md).


## Shared selectors and action menus

All 33 form-selector call sites and the two compact commitment selectors use `AppChoiceSpinner`. It keeps the adapter values, original positions and `OnItemSelectedListener` contracts, while opening a themed single-choice dialog with an explicit selected state instead of the platform dropdown. The same `ChoiceDialogBuilder` renders all 20 action/choice menus and four multi-select menus with the existing app typography, rounded surfaces, light/dark tokens and icons. Forms and confirmation dialogs without list items retain their existing behavior. System-owned date/file/permission pickers stay under Android control.

Lists with eight or more options have a search field. Filtering preserves original positions and checkbox state; no financial value is inferred from display text. Multi-select menus retain caller save/cancel semantics and any supplied form, including the InstaPay handle input. Long labels wrap, scroll height is bounded, touch targets are at least 48dp, and cancellation/destructive actions use text as well as color. `SelectionUiTest` exercises the actual transaction editor, account action menu and combined input/multi-select dialog in light, dark and enlarged-font modes.

Original bank messages use the same themed bottom sheet as the financial journeys, with a separate rounded card per evidence item, sender/source and saved time, selectable unmodified message text, scrollable content and a fixed close button. Missing/unreadable evidence remains explicit. Synthetic menu UI checks include this screen in both themes and at font scale 1.3.


Final UI-extension acceptance: `SelectionUiTest` passed 31 checks in each of light, dark and font-scale 1.3 modes (93), including search original-index preservation, empty results, transaction save/cancel, checkbox state across filtering, a combined input/multi-select form, original bank messages and missing evidence. The card journeys passed 41 checks per mode (123), and the existing reimbursement journey passed 61 checks: 277 UI checks in total. The 22 native cancellation/card tests passed again in the same isolated emulator run. The last one-line icon adjustment replaces a filled card-chip decoration with the existing outlined transfer icon; the menu/message modes were rerun for that adjustment. Production/test APK builds and lint pass (0 errors, 66 existing warnings). [Reviewed menu/message screenshots](screenshots/card-settlement-2026-10-16/README.md).

Historical SMS import uses period selection as consent to scan and apply supported messages on a worker. Historical cancellation notices are stored as `needs_confirmation` without archiving any transfer; only live capture auto-matches, and live reconciliation never resolves `needs_confirmation`. The result screen links each pending historical cancellation to its event for explicit review/confirmation. Previously processed messages (capture evidence, capture events or import receipts) are skipped by default and rechecked at apply time, so a repeated import processes nothing. The default excludes declines/future notices and previously excluded messages, and retains duplicate/manual-edit protections. Preview remains opt-in with a fixed action footer; result details are read-only. Apply plus the bounded reconciliation repair share a database transaction. Session state survives activity recreation so rotation cannot submit a second batch.
