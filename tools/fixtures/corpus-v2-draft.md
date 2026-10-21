# Corpus v2 — unapproved draft, 21 October 2026

**Not ready for approval. No production pipeline stage may start from this draft.** The owner accepted
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
  observed Egyptian bank wording. No unsupported bank greeting, wallet top-up or bill SMS was invented.

There are **280 rows**, rather than deleting retained coverage to force the earlier 240-row count.
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
| `expected_accounting_effect` | Pending internal-transfer ownership/reconciliation; not automatic spending/income |
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
| Arabic message words | 90 / 280 = 32.14% | >=40% | **FAIL** |
| English message words | 163 / 280 = 58.21% | >=30% | PASS |
| Mixed message words | 26 / 280 = 9.29% | <=30% | PASS |
| Nonlinguistic empty notification | 1 / 280 | Counted in full denominator | Explicit |
| EGP transactions | 145 / 180 = 80.56% | >=80% | PASS |
| REVIEW | 16 | 15..25 | PASS |

Language counts inspect message **words** after removing variable dates/times, URLs, masked identifiers
and ISO currency codes. Latin merchant/bank/app words still make Arabic text mixed. Source labels do not
count, and inherited v1 language labels are not evidence of the quota. The 66 protected rows retain those
old labels unchanged. Unresolved-currency transactions remain in the EGP-ratio denominator.

Still missing documented examples (placeholders were **not** inserted as pretend test messages):

- An IPN/InstaPay completed transfer explicitly stating its fee in the same message.
- CIB and NBE purchase messages with the requested `Dear Customer` / `عزيزي العميل` greetings.
- A single Google Play/Apple payment message with USD principal and explicit EGP equivalent; specify
  which is the transaction amount independently of balance or fees.
- An explicit reversal distinct from the already documented CIB refund.
- A completed installment payment; wallet top-up; Fawry bill payment; observed card-decline message.
- Vodafone Cash and a direct InstaPay app notification with documented full text/field placement.
- Additional independently sourced Arabic examples to meet the Arabic quota.

The user explicitly noted the two-phone sample contained no bank-card merchant purchases. The public
CIB case adds a separate published user report; the NBE test file is a lower-evidence developer fixture.
Neither proves current coverage across all requested banks/wallets. A service FAQ can explain a product,
but cannot establish verbatim message wording, so such pages were not used to invent those missing cases.

## Reproducibility and checks

```powershell
python tools/build-parser-corpus-v2.py
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\run-checks.ps1
# Strict acceptance gate: intentionally fails while the above requirements remain unmet.
& "$env:JAVA_HOME/bin/java.exe" -cp .tmp/qa-jvm com.mohamedgamal.masareef.ParserCorpusV2Test --require-ready
```

The generator reads v1 and contains explicit base text/expectations; it never asks either parser for gold
values. Output is deterministic UTF-8/LF. `ParserCorpusV2Test` checks preservation/removal, source registry,
variant semantics, optional money fields, account/card separation, the corrected oracles and pair boundaries.
The normal JVM runner executes **draft integrity**, prints readiness blockers and does not claim approval.
`--require-ready` fails on missing distributions/features; even success would still need explicit owner approval.

**No semantic evaluation of a new parser has run.** The only application code changed in this checkpoint
is the separately tested, explicitly requested diagnostic `SmsPreview.shape` prerequisite. Normalization,
classification, extraction, confidence, local learning, shadow recording, UI and database work are untouched.
