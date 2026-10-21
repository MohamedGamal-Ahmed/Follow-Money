# Corpus v2 — unapproved draft, 21 October 2026

**Approved by the owner on 21 October 2026, conditional on the three amendments now applied (reversal
`expense_offset`, Vodafone Cash counterparty token, dual-currency accounting amount).** The acceptance policy was revised on 21 October 2026 (see
[README](README.md#corpus-v2-acceptance-policy-owner-decision-21-october-2026)): the corpus proves message-type
coverage, not bank coverage, and labelled synthetic cases close missing types. The owner accepted
the v1 file shape but rejected its contents. v1 remains an immutable historical comparison file;
the proposed replacement is [`parser-corpus-v2-draft.tsv`](parser-corpus-v2-draft.tsv).

## What changed

- Removed all 72 `source_independence` rows; none of their IDs is reused for a different message.
- Preserved all **66** `notification_fields`, `dedupe_identity`, `promotions` and `other_and_failed`
  rows, comparing every original column byte-for-value after TSV decoding, not just the body.
- Included the supplied masked Bank Misr, HSBC and QNB examples, security/card-maintenance notices,
  expiring cashback, and their explicitly labelled digit/space/field-order variants. The full supplied
  originals are retained as base cases; variants are not presented as additional observed formats.
- Added seven public example bases with two variants each. Their evidence levels differ:
  one CIB user report, public developer test fixtures for CIB/NBE, and a public CIB IPN example.
  **Public developer examples are not represented as bank-issued specifications or phone validation.**
  Every row joins to the [source registry](corpus-v2-sources.tsv).
- Applied the owner corrections to `p134` (AUTO, principal 500 EGP, separate optional 5 EGP fee),
  `p137` (bare `$` means USD/AUTO), and `p140` (likely outgoing direction/REVIEW).
- Kept the old non-protected synthetic coverage explicitly labelled `legacy-v1`; it is not counted as
  observed Egyptian bank wording.
- Added **89 `synthetic_coverage` rows** (25 bases plus declared variants) for the eleven message types that
  had no observed sample. CIB/NBE greeting cases are attributed to `PUBLIC_FORMAT_CIB`/`PUBLIC_FORMAT_NBE`
  (published structures, URL in the registry); all others to `SYNTHETIC_<type>`. None is attributed to an
  observed bank, and none is an alternative format for Banque Misr, HSBC, QNB, Telda or TRU.

There are **369 rows** (280 earlier rows, byte-identical, plus 89 synthetic rows).
The raw `source_independence` padding is gone. A source/format lineage is meaningful; a larger row count
or a different monetary value is not evidence of another real format or bank.

## Representation and added fields

All original TSV columns and escape rules remain. Additional optional columns describe:

| Fields | Purpose |
| --- | --- |
| `expected_fee_minor`, `expected_fee_currency` | A separately stated fee, never added to principal or inferred from a tariff |
| `expected_account_last4`, `expected_counterparty_token` | Distinguish account/card/recipient; a three-digit visible suffix cannot invent four digits |
| `expected_movement_kind` | instant_transfer_in/out, salary, transfer_in/out, inward_remittance, refund, cash_withdrawal, possible_internal_transfer, etc. |
| `expected_direction_certainty` | explicit / likely; no forced confidence from a merchant alone |
| `expected_equivalent_minor`, `expected_equivalent_currency` | Reserved for an explicitly stated exchange equivalent; neither balances nor another message are exchange equivalents |
| `expected_credit_limit_minor`, `expected_credit_limit_currency` | Credit-card available limit, separate from a deposit-account balance |
| `expected_expiry_date` | Restricted/expiring cashback evidence |
| `expected_time_ambiguity`, `expected_year_origin` | Missing AM/PM, ambiguous partial date or inferred year remain explicit |
| `context_group`, `expected_standalone_decision`, `expected_context_decision` | Isolated parsing versus a supplied pair/context, without contradictory oracles |
| `expected_accounting_effect` | Pending internal-transfer ownership/reconciliation (`pending`, `pending_internal`), or `expense_offset` for reversals; not automatic spending/income |
| `expected_original_amount_minor`, `expected_original_currency` | Foreign amount shown on a dual-currency charge for information; the booked amount is the account-currency equivalent |
| `source_evidence_id`, `base_case_id`, `variant`, `feature_tags` | Source and variant lineage; the source registry states what was changed and what is unverified |

The draft introduces `verification` for the supplied secure-key request alongside the existing `otp` type;
both have IGNORE expectations. This is an **oracle proposal**, not a change to a production enum or parser.

## Decisions explicitly captured

- Missing merchant in an identified completed instant transfer is **not** a REVIEW reason. Account and
  payment kind remain available even with null merchant. Numeric counterparty `5678` is not a merchant.
- For the supplied signed HSBC Salary/Phone Banking/TT form, **only the trailing +/- sign** determines
  direction; `to` and `from` are not financial direction. A partial mask ending `789` has no known last4.
- Salary/loan teasers without an amount remain promo/IGNORE. Completed **nonfinancial** verbs such as card
  issuance never produce a transaction. References, URLs, phone numbers and percentages are not amounts.
- Expiring cashback is transaction/REVIEW with `COMPLETED_EVENT|PROMO_SIGNALS|EXPIRING_CREDIT`; it is not
  ordinary income and is not thrown away as a pure advertisement.
- `u13` has a 21/10 date without a year, using the receipt-year context. The new-year case receives 31/12
  evidence on 1 January 2027 and expects 31 December **2026**, with `adjacent_year` explicitly recorded.
- The truncated outgoing IPN message ending at `03:45` remains AUTO for principal/direction. Its event
  date remains known, while AM/PM and the hour are unresolved; it must not silently select 03:45 or 15:45.
- Bank-supplied dates are stored as dates when the message has no time; no noon or receipt time is made
  into a claimed bank event time. Corpus expected local datetimes do not fabricate a timezone in the SMS.

## Pair/context rules

The **four** pair scenarios are a different capture contract from pure isolated parsing:

1. Same-source HSBC Phone Banking legs, out/in, different currencies, five minutes apart: both REVIEW.
2. Same family, same currency and principal, five minutes apart: both REVIEW.
3. Same family/currency/principal, exactly ten minutes apart: both REVIEW.
4. The same pair at ten minutes plus one millisecond: this specific temporal rule does not trigger.

Each leg's `expected_standalone_decision` is AUTO. The main decision in these context rows is the decision
**after supplying the pair context**. A REVIEW pair has `REVIEW_INTERNAL_TRANSFER` capture disposition,
`POSSIBLE_INTERNAL_TRANSFER` reason, and `pending_internal` accounting effect. No exchange rate, account
ownership or automatic linking is asserted. Same-currency EGP pair data are declared generated scenarios.

Funding a possibly owned credit/prepaid card (`u03`) remains REVIEW/pending. Matching a confirmed owned
card's opposite deposit leg and excluding confirmed internal movement from spending remains a future
integration rule; this draft does not implement it, assume ownership or alter existing bank balances.

## Current distribution and blockers

The [generated audit report](corpus-v2-results.md) contains the final measured values. Current inventory:

| Gate | Current | Required | Status |
| --- | ---: | ---: | --- |
| Arabic message words | 155 / 369 = 42.01% | >=40% | PASS |
| English message words | 187 / 369 = 50.68% | >=30% | PASS |
| Mixed message words | 26 / 369 = 7.05% | <=30% | PASS |
| Nonlinguistic empty notification | 1 / 369 | Counted in full denominator | Explicit |
| EGP transactions | 223 / 258 = 86.43% | >=80% | PASS |
| REVIEW | 20 | 15..25 | PASS |
| Message-type coverage | 11 / 11 types | each: base + >=2 variants | PASS |

Language counts inspect message **words** after removing variable dates/times, URLs, masked identifiers
and ISO currency codes. Latin merchant/bank/app words still make Arabic text mixed. Source labels do not
count, and inherited v1 language labels are not evidence of the quota. The 66 protected rows retain those
old labels unchanged. Unresolved-currency transactions remain in the EGP-ratio denominator.

The eleven former "missing documented sample" blockers are now **message-type coverage** gates, each closed
by labelled synthetic cases (per-type list in the [audit report](corpus-v2-results.md#message-type-coverage)).
Decisions encoded in them: an explicit fee stays separate from the principal (AUTO, like `p134`); in a
dual-currency charge the **accounting amount is the account-currency equivalent** (EGP 495.25, what left the
account and budget) and the foreign USD 9.99 is kept as `original_amount/original_currency` for display;
reversals are `expense_offset` (see below); installment counters are not amounts or dates; a wallet top-up from the user's own
card is REVIEW/`pending_internal`; Fawry recharge wording (`شحن`) is an outgoing payment, not a top-up;
card declines are IGNORE with no transaction; a direct InstaPay notification uses separate Android extras.

**Reversal rule (owner, 21 October 2026).** All eleven reversal rows carry `expected_accounting_effect =
expense_offset` and the `expense_offset` tag. A reversal is linked to its original purchase when one exists
(same amount and same merchant within 14 days) and reduces it; otherwise it is recorded as a negative
expense. It never appears in total income. The incoming direction describes the money movement only.

Synthetic rows prove that the generic logic meets each type and writing shape. They are **not** observed
messages and do not prove coverage of any bank; unseen banks rely on the generic design and the
REVIEW/UNUNDERSTOOD safety net.

## Reproducibility and checks

```powershell
python tools/build-parser-corpus-v2.py
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\run-checks.ps1
# Strict acceptance gate: passes now; success means ready for owner review, not approval.
& "$env:JAVA_HOME/bin/java.exe" -cp .tmp/qa-jvm com.mohamedgamal.masareef.ParserCorpusV2Test --require-ready
```

The generator reads v1 and contains explicit base text/expectations; it never asks either parser for gold
values. Output is deterministic UTF-8/LF. `ParserCorpusV2Test` checks preservation/removal, source registry,
variant semantics, optional money fields, account/card separation, the corrected oracles and pair boundaries.
The normal JVM runner executes **draft integrity**, prints readiness blockers and does not claim approval.
`--require-ready` fails on missing distributions or message-type coverage and on mislabelled synthetic rows;
its success still needs explicit owner approval.

**No semantic evaluation of a new parser has run.** The only application code changed in this checkpoint
is the separately tested, explicitly requested diagnostic `SmsPreview.shape` prerequisite. Normalization,
classification, extraction, confidence, local learning, shadow recording, UI and database work are untouched.
