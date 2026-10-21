# Corpus v2 draft audit

Fixture integrity/oracle assertions: PASS (7853). Parser semantic evaluation: NOT RUN.
Rows: 280; protected v1 rows unchanged: 66; rejected padding rows removed: 72.
Provenance: 17 user-supplied base cases + one owner-specified descriptor expansion; 7 public base examples. Variants are not additional observed formats.
Decisions: {AUTO=156, IGNORE=100, REVIEW=16, UNUNDERSTOOD=8}

| Measured language | Count | Share |
| --- | ---: | ---: |
| ar | 90 | 32.14% |
| en | 163 | 58.21% |
| mixed | 26 | 9.29% |
| neutral | 1 | 0.36% |

EGP transactions: 145/180 (80.56%); unresolved-currency transactions stay in the denominator.

## Remaining approval blockers

- Arabic words-only share below 40%
- Missing documented sample: ipn_explicit_fee
- Missing documented sample: cib_dear_customer_purchase
- Missing documented sample: nbe_dear_customer_purchase
- Missing documented sample: dual_currency_equivalent
- Missing documented sample: reversal
- Missing documented sample: installment_payment
- Missing documented sample: wallet_topup
- Missing documented sample: fawry_bill_payment
- Missing documented sample: card_decline_observed
- Missing documented sample: vodafone_cash_observed
- Missing documented sample: instapay_direct_observed

Status: NOT READY FOR APPROVAL. Normalization/pipeline implementation is prohibited until explicit approval.

## Base-case diagnostic shape inventory

Only base rows are counted. Generated variations, field-order permutations and paired-context rows are excluded. Distinct financial/nonfinancial base families may have different profiles; these are not current bank-format or phone-rescan counts.

- BanK-AlAhly: 3 base-case profiles.
- BankMisr: 5 base-case profiles.
- CIB: 4 base-case profiles.
- HSBC: 6 base-case profiles.
- QNB: 2 base-case profiles.
- UNSPECIFIED_EGYPTIAN_BANK: 3 base-case profiles.
- UNSPECIFIED_WALLET: 1 base-case profiles.

## Verification and boundary report — 21 October 2026

- Diagnostic prerequisite commit: `34e9fe5` (55 new shape assertions; existing redaction suite 52 assertions).
- Historical v1 canonical SHA-256 checked by the audit: `628cc295cbbddc7bda70fe90a0dee6c9125df27563140b8b4e0a61d0a6ed806e`.
- This v2 draft SHA-256, UTF-8/LF: `aba8eac4c4d4d2a8b828c51979b18ef66b7e529c93fda361bb79c4b32c624f9a`.
- Regenerating with `python tools/build-parser-corpus-v2.py` reproduced the identical file hash.
- `tools/run-checks.ps1`: existing JVM suites PASS, including 168 parser and 24 Telda checks; v1 fixture integrity PASS; new shape tests PASS; v2 fixture integrity PASS.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: PASS. Lint has 0 errors and 63 existing warnings.
- Strict v2 readiness command with `--require-ready`: **FAIL, exit 1**, with the twelve blockers above. This is not a passing corpus acceptance gate.
- No Android emulator/instrumentation, UI, live notification delivery or phone-message rescan was performed. A test APK build is not evidence those paths worked.
- No network-dependent application code, new library, database migration, capture/dedupe rule, parser stage, shadow recorder or UI was introduced. Public-source research did not upload device messages.
- Source evidence levels and the exact supplied-message/derived-variant distinction are recorded in `corpus-v2-sources.tsv`. Unspecified senders remain unspecified; telephone footer numbers are not bank identities.
- Dates and truncated AM/PM expectations are test oracles only. No new event-time parser was implemented.

The draft intentionally remains unapproved. More independently sourced Arabic examples and the missing exact message types are needed; no messages were fabricated to satisfy their quotas or bank labels. Explicit owner approval of a completed v2 remains required before Normalization begins.