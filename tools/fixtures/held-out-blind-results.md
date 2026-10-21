# Held-out blind set: first run on unchanged code

Run on 22 October 2026 at commit `fe0dab4` (rules and `ParsingPolicy` exactly as approved for phases 2-3; no fixes). Input: [held-out-blind.tsv](held-out-blind.tsv), 25 owner-written messages never used for rule development. Receipt time fixed at 2026-10-22 12:00 UTC.

Messages fully correct (all 8 fields): **19 / 25**

| Field | Correct |
| --- | ---: |
| type | 24 / 25 |
| decision | 21 / 25 |
| amount | 24 / 25 |
| currency | 24 / 25 |
| direction | 23 / 25 |
| party | 21 / 25 |
| balance | 25 / 25 |
| fee | 24 / 25 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| h01 | transaction | AUTO | 125000 | EGP | out | orange | 321045 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 1234 | account 5678; balance 3210.45 |
| h02 | transaction | AUTO | 200000 | EGP | out | \N | 654000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 9911 | cash_withdrawal; ATM0421 is not a merchant; card 9911 |
| h03 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  | trap: amount and merchant inside an OTP |
| h04 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  |  |
| h05 | transaction | AUTO | 120000 | EGP | in | محمد ا | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | counterparty; Arabic-Indic digits and Arabic decimal separator; account 3344 |
| h06 | reminder | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | FUTURE_ONLY |  | future event |
| h07 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  | UNSUCCESSFUL_EVENT |
| h08 | transaction | **UNUNDERSTOOD ≠ REVIEW** | 20000 | EGP | **\N ≠ in** | \N | 21550 | \N | ✗ | AMOUNT_UNAMBIGUOUS,AMBIGUOUS_DIRECTION |  | wallet_topup / pending_internal; letter چ |
| h09 | transaction | AUTO | 6000 | EGP | out | koshary abou tarek | 14000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| h10 | transaction | AUTO | 1200 | USD | out | netflix | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 4455, limit 1500000 EGP | no EGP equivalent so the amount stays USD; credit limit 15000.00 EGP; a limit in another currency is not ambiguity |
| h11 | transaction | AUTO | 1500000 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 7788 | cash_deposit; the branch is not a party |
| h12 | reminder | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | FUTURE_ONLY |  | two amounts, no completed event |
| h13 | transaction | **AUTO ≠ REVIEW** | 450000 | EGP | out | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 6611 | POSSIBLE_INTERNAL_TRANSFER: own card paid from own account |
| h14 | transaction | AUTO | 19900 | EGP | out | we | \N | 300 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 0223 | Total 202.00 is neither the amount nor a balance |
| h15 | transaction | **REVIEW ≠ AUTO** | 4900 | EGP | out | **\N ≠ plus** | 231000 | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT | card 1234 | actual debit for the 49 promo (p170); must be told apart |
| h16 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| h17 | transaction | AUTO | 32000 | EGP | in | **جوميا واعاده المبلغ لبطاقتكم المنتهيه ب ≠ جوميا** | 512000 | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 4455, expense_offset | reversal / expense_offset |
| h18 | transaction | AUTO | 30000 | EGP | in | sara m | 31650 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | text only in textLines; counterparty |
| h19 | **other ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 500000** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ محمد سعيد** | \N | **\N ≠ 0** | ✗ | NO_COMPLETED_EVENT |  | counterparty; hex reference is not an amount |
| h20 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | NO_COMPLETED_EVENT |  | no event |
| h21 | transaction | AUTO | 7500 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | reversal / expense_offset; #88213 is not an amount |
| h22 | transaction | AUTO | 150000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 7788 | installment_payment |
| h23 | transaction | AUTO | 1500 | EGP | out | cilantro zamalek | 150 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | branch suffix zamalek accepted |
| h24 | balance_inquiry | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | BALANCE_ONLY |  | the last-operation line is not booked again |
| h25 | transaction | AUTO | 25000 | EGP | out | **\N ≠ 01012345678** | \N | 0 | ✗ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | counterparty |
