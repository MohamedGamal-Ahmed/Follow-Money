# The 14 device failures known at 4ab28ed — diagnosis (0.4.1 item 5)

Named in commit 9819a2c. Device suites in five modes on emulator-5554 (base, phase, features, audit, card).
Before: 14 failures. After this commit: 0 in all five modes (base 27, phase 14, features 68, audit 149, card 30).

Every test was diagnosed before it was changed. No test was deleted.

## A. Stale: the latest schema version is 26, not 25 (8 tests)

Schema v26 was added deliberately in c170129 ("Persist shadow observations, ununderstood messages and learning
rules"): it creates shadow_observations, ununderstood_messages and learning_rules and touches no older table. These
tests upgrade an old fixture and then asserted the version equals the literal 25 ("expected 25, actual 26"). In every
case the version check was the first assertion that failed; the data checks behind it never ran.

Change: the literal 25 now reads `ExpenseDb.SCHEMA_VERSION`, so the test keeps asserting "migrated to the latest
version" and then runs its real checks. All of them pass:

| Test | Mode | Migration it covers |
|---|---|---|
| testVersion17SourceMigrationPreservesFinancialRows | base | v17 sources |
| testSchema16MigrationPreservesLedger | phase | v16 ledger |
| testV24CommitmentUpgradeKeepsLegacyStateWithoutInventingPayments | features | v24 commitments |
| testV20UpgradePreservesLedgerLinksEvidenceAndUnknownPrecision | audit | v20 links, evidence, precision |
| testV23RevisionUpgradePreservesEveryUserTableAndEvidence | audit | v23 revision |
| testCorruptV21ArchivesDoNotBlockUpgradeOrLoseRawData | card | v21 archives |
| testLegacyCancellationMigrationKeepsArchiveAndDistinguishesMissingProof | card | legacy cancellations |
| testV22TimelineUpgradeBackfillsDateAndUsesIndex | card | v22 timeline |

The same literal was replaced wherever a test asserts the latest version (9 places in 6 files, including
UpgradeInstallTest, which is not run in these five modes).

Migrations were treated as defects until proven otherwise, so beyond the synthetic fixtures the upgrade was also run on
real databases from the owner's phone (copies kept under .tmp, upgraded on the emulator by opening the app, then
compared by counts and sums only; the copies were deleted afterwards):

| Database | From | To | integrity | Pre-existing tables | Transactions and money |
|---|---|---|---|---|---|
| audit-2026-10-10 phone-before | v13 | v26 | ok | all row counts equal | 1072 rows; sums per kind/currency equal |
| private-audit R5CX71R7TLF-before | v16 | v26 | ok | all row counts equal | 1999 rows; sums equal |
| balance-review-2026-10-11 final-phone | v18 | v26 | ok | all row counts equal | 1263 rows; sums equal; archive 20 |
| git-publish-2026-10-12 phone-before | v20 | v26 | ok | all row counts equal | 1197 rows; sums equal; reimbursed 111288 |
| phone-update-20261016 after-db | v23 | v26 | ok | all row counts equal | 1212 rows; sums equal; archive 91 |

Only new tables appeared, plus the backfills the migrations are meant to make (v13: 1072 capture_evidence rows; v18
and v20: 5 capture_events from legacy cancellations). No real database at v25 exists: 0.2.1 and 0.3.1 both shipped
schema 25, and none of the saved phone copies is from those builds, so the 25 → 26 step on real data is covered
only by the v23 → 26 run (which includes it) and the synthetic tests.

## B. Stale: the revision test predates the shadow tables (1 test)

**testRevisionCoversUserTablesAndIgnoresReadsAndUnmatchedWrites** (audit): "Untracked user-table write:
shadow_observations INSERT". The test requires a LedgerRevision trigger on every table. ShadowStore's three tables are
untracked by design: its class comment says "none tracked by LedgerRevision, so a write here never looks like a ledger
change", and c170129 says the same. The test now exempts exactly those three tables; every other table must still be
tracked, and the test passes.

## C. Real defects in the new pipeline, fixed in the code (4 tests)

The new pipeline decides every message since 0.4.0. These four tests still describe the intended behaviour; no
decision changed it.

**testCapturedCashPrincipalAndStandaloneFeeUseEventWording**,
**testMixedPrincipalAndNegatedFeesStayCash**, **testCashFeeOwnershipIsStableAcrossWordOrder** (features)
- «تم سحب 2000 جنيه من حسابك» and «تم سحب من حسابك مبلغ 2000 جنيه مع رسوم» had no movement kind, so a cash withdrawal
  was filed «غير مصنف» and counted as spending. Fix: TransactionExtractor treats a completed «تم … سحب» with no merchant
  and no counterparty as cash_withdrawal.
- "charged EGP 5 as a cash withdrawal fee", "ATM cash withdrawal fee of EGP 5" were cash_withdrawal (MOVEMENT_CASH
  matched "cash withdrawal" before the fee was considered), so a bank fee left spending. Fix: when the principal itself
  is a fee (self kind bank_fee) and the text names a withdrawal fee (ParsingPolicy.WITHDRAWAL_FEE), it is not cash; the
  self kind now reaches DecisionNames (value self_kind), which files it under «رسوم بنكية». A withdrawal that states its
  fee separately ("… with a fee of EGP 5") stays cash.

**testMixedFutureAlertBooksOnlyCompletedPurchase** (features)
- "Your card was debited EGP 500 at AMAZON. Installment will be debited monthly" was installment_payment («أقساط»): any
  installment word anywhere made the purchase an installment. Fix: an installment word counts only in a clause that
  is not future wording.

Proof: DecisionNamesTest gained 9 checks that failed first, then passed. Corpus v2 movement kinds (234), field
comparisons and held-out sets 1–5 are unchanged.

## D. Stale: a documented time decision (1 test)

**testDateOnlyEligibilityUsesDayInPreviewAndConfirmation** (audit): "Purchase or repayment changed". The last part
captures "You received EGP 50 from QA FRIEND on <today>" and expects it to be date-only, so an evening purchase that
day can be repaid by it. Since 1cd57fa (owner test, 22 Oct 2026) a date equal to the delivery day with no clock takes the
delivery time, exact. The income was therefore exact at 16:00 and the 20:00 purchase rightly did not precede it. The
test's intent (a date-only income does not exclude same-day purchases) is kept by dating the message the day before
its delivery, and it now asserts that the income is date_only.
