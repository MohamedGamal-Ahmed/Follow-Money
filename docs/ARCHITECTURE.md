# Architecture

Follow Money is a single Android application module using Java 17, native views and SQLite. It has no application backend or network client.

## Data path

1. `SmsReceiver`, `SmsImportActivity` and `PaymentNotificationListener` receive supported sources; the user can also add or share text manually.
2. `ExpenseParser` and identity helpers extract conservative event, amount, currency and account information. An ambiguous input may remain unrecognized or require review.
3. `ExpenseDb` stores transactions, account identities, commitments, corrections and source associations. `EvidenceVault` protects stored source text using Android Keystore.
4. `RepairRules`, `MoneyTotals`, `MoneyFlow`, `DailyAllowance` and `Insights` derive summaries from the ledger and confirmed relationships.
5. `MainActivity`, widgets and reminders display the derived state. Changes trigger the relevant refreshes.

Consult class definitions for field and migration contracts; database schema numbers in historical notes are not authoritative.

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

## Validation

Standalone JVM tests cover parser, time/budget, money-flow, repair and insight rules. Android instrumentation covers SQLite reconciliation, feature integration and widgets using synthetic data. `tools/run-checks.ps1` is the shared entry point for local checks and CI; Android instrumentation is optional and emulator-only. `tools/check-repo.py` checks documentation links, support form field consistency, pinned CI actions and excluded private artifacts.
