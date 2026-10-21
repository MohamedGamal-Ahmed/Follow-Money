# Corpus v2 draft audit

Fixture integrity/oracle assertions: PASS (11963). Parser semantic evaluation: NOT RUN.
Rows: 369; protected v1 rows unchanged: 66; rejected padding rows removed: 72.
v2 SHA-256 (UTF-8/LF): `cc75ee3c643874316b03aa814510ab9980c766f37ef1aae368298f769a01cac4`.
Provenance: 17 user-supplied base cases + one owner-specified descriptor expansion; 7 public base examples; 25 labelled synthetic base cases. Variants are not additional observed formats.
Decisions: {AUTO=230, IGNORE=111, REVIEW=20, UNUNDERSTOOD=8}
Evidence levels (rows): {generated_fixture_v1=168, public_developer_example=3, public_developer_fixture=15, public_user_report=3, synthetic=89, user_redacted=91}
Transaction currencies: {AED=3, EGP=223, EUR=6, GBP=2, SAR=4, USD=16, null=4}

## Message-type coverage

- ipn_explicit_fee: syn-ipnfee-ar (synthetic, 3 variants); syn-ipnfee-en (synthetic, 2 variants)
- cib_dear_customer_purchase: syn-cib-dear-en (synthetic, 2 variants); syn-cib-dear-ar (synthetic, 3 variants)
- nbe_dear_customer_purchase: syn-nbe-dear-credit (synthetic, 3 variants); syn-nbe-dear-debit (synthetic, 3 variants)
- dual_currency_equivalent: syn-dual-en (synthetic, 2 variants); syn-dual-ar (synthetic, 3 variants)
- reversal: syn-reversal-ar (synthetic, 3 variants); syn-reversal-atm-ar (synthetic, 3 variants); syn-reversal-en (synthetic, 2 variants)
- installment_payment: syn-installment-ar (synthetic, 3 variants); syn-installment-en (synthetic, 2 variants)
- wallet_topup: syn-topup-ar (synthetic, 3 variants)
- fawry_bill_payment: syn-fawry-ar (synthetic, 3 variants); syn-fawry-recharge-ar (synthetic, 3 variants); syn-fawry-en (synthetic, 2 variants)
- card_decline: syn-decline-ar (synthetic, 3 variants); syn-decline-limit-ar (synthetic, 3 variants); syn-decline-en (synthetic, 2 variants)
- vodafone_cash: syn-vfcash-pay (synthetic, 3 variants); syn-vfcash-in (synthetic, 2 variants); syn-vfcash-out (synthetic, 2 variants)
- instapay_direct: syn-instapay-ar (synthetic, 2 variants); syn-instapay-en (synthetic, 2 variants)

| Measured language | Count | Share |
| --- | ---: | ---: |
| ar | 155 | 42.01% |
| en | 187 | 50.68% |
| mixed | 26 | 7.05% |
| neutral | 1 | 0.27% |

EGP transactions: 223/258 (86.43%); unresolved-currency transactions stay in the denominator.

## Remaining approval blockers


Status: READY FOR OWNER REVIEW; NOT APPROVED. Normalization/pipeline implementation is prohibited until explicit approval.

## Base-case diagnostic shape inventory

Only base rows are counted. Generated variations, field-order permutations and paired-context rows are excluded. Distinct financial/nonfinancial base families may have different profiles; these are not current bank-format or phone-rescan counts.

- BanK-AlAhly: 3 base-case profiles.
- BankMisr: 5 base-case profiles.
- CIB: 4 base-case profiles.
- HSBC: 6 base-case profiles.
- PUBLIC_FORMAT_CIB: 2 base-case profiles.
- PUBLIC_FORMAT_NBE: 2 base-case profiles.
- QNB: 2 base-case profiles.
- SYNTHETIC_CARD_DECLINE: 3 base-case profiles.
- SYNTHETIC_DUAL_CURRENCY: 2 base-case profiles.
- SYNTHETIC_FAWRY_BILL: 3 base-case profiles.
- SYNTHETIC_INSTALLMENT: 2 base-case profiles.
- SYNTHETIC_INSTAPAY_DIRECT: 2 base-case profiles.
- SYNTHETIC_IPN_FEE: 2 base-case profiles.
- SYNTHETIC_REVERSAL: 3 base-case profiles.
- SYNTHETIC_VODAFONE_CASH: 3 base-case profiles.
- SYNTHETIC_WALLET_TOPUP: 1 base-case profiles.
- UNSPECIFIED_EGYPTIAN_BANK: 3 base-case profiles.
- UNSPECIFIED_WALLET: 1 base-case profiles.
## Verification and boundary report — 21 October 2026 (synthetic-coverage revision)

- Policy commit `e1c5051`: readiness redefined as message-type coverage; synthetic labelling enforced by builder and audit.
- Synthetic cases commit `91c22f1`: 89 rows (25 synthetic bases + declared variants); the 280 previous rows are byte-identical.
- Historical v1 canonical SHA-256 checked by the audit: `628cc295cbbddc7bda70fe90a0dee6c9125df27563140b8b4e0a61d0a6ed806e`.
- Regenerating with `python tools/build-parser-corpus-v2.py` reproduced the identical v2 hash above.
- `tools/run-checks.ps1`: exit 0; all existing JVM suites PASS (including 168 parser, 24 Telda, 31 InstaPay checks); v1 fixture integrity PASS; shape tests PASS; v2 fixture integrity PASS (11522 assertions).
- Strict v2 readiness command with `--require-ready`: **PASS, exit 0**, zero blockers.
- Readiness means "ready for owner review", not approval. No parser semantic evaluation, Normalization stage, application code, schema or UI change was made.
- Synthetic rows prove message-type/shape coverage only. They are not observed messages, not bank-format claims, and not evidence of coverage for unseen banks (that remains the generic design plus REVIEW/UNUNDERSTOOD).

## Owner amendments — 21 October 2026 (approved contract)

- Reversals (11 rows): `expected_accounting_effect = expense_offset`, tag `expense_offset`; audited so no reversal can be booked as income.
- `syn-vfcash-in` and variants: `expected_counterparty_token = 010XXXXX123`.
- Dual currency (`syn-dual-*`, 7 rows): accounting amount EGP 495.25; `expected_original_amount_minor/currency` = 999 USD for display.
- `--require-ready`: PASS, zero blockers. Approved v2 SHA-256 is the one in this report's header.
