# Held-out blind set 4: first run on unchanged code

Owner-written 25 October 2026 ([held-out-blind-4.tsv](held-out-blind-4.tsv)), separate from the corpus and sets 1-3. Run on the rules of `b73669d` (unchanged). The owner's verdict on this raw run: decision 16/30 (9 safe, 5 dangerous). The 8-field row scoring below gives 14/30. `*` = not specified; `|` = accepted alternatives (`\N` accepts null). Receipt time 2026-10-22 12:00 UTC.

Messages fully correct (all 8 fields): **14 / 30**

| Field | Correct |
| --- | ---: |
| type | 25 / 30 |
| decision | 18 / 30 |
| amount | 21 / 30 |
| currency | 21 / 30 |
| direction | 20 / 30 |
| party | 23 / 30 |
| balance | 27 / 30 |
| fee | 26 / 30 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| c4D01 | **other ≠ transaction** | **IGNORE ≠ REVIEW** | **\N ≠ 16340** | **\N ≠ USD** | **\N ≠ out** | **\N ≠ hotel reservation** | \N | \N | ✗ | NO_COMPLETED_EVENT |  | final amount 163.40; 200 earlier hold; 36.60 released |
| c4D02 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c4D03 | **other ≠ transaction** | **IGNORE ≠ REVIEW** | **\N ≠ 340** | **\N ≠ EGP** | **\N ≠ out** | \N | \N | \N | ✗ | UNSUCCESSFUL_EVENT |  | the non-refunded fee 3.40 is the only movement (bank_fee); 3,400 returned |
| c4D04 | transaction | **UNUNDERSTOOD ≠ AUTO** | **\N ≠ 124450** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ adobe** | \N | **\N ≠ 3734** | ✗ | MULTIPLE_EVENTS |  | accounting EGP 1,244.50 (equivalent); original USD 24.99; rate and total are not amounts |
| c4D05 | transaction | **AUTO ≠ REVIEW** | 500000 | EGP | **out ≠ in** | \N | 871520 | 500 | ✗ | EXPLICIT_FEE,UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | POSSIBLE_INTERNAL_TRANSFER: your account at another bank |
| c4D06 | **balance_inquiry ≠ transaction** | **IGNORE ≠ REVIEW** | **\N ≠ 160000** | **\N ≠ EGP** | **\N ≠ out** | \N | \N | \N | ✗ | BALANCE_ONLY |  | internal: own card settled from current account; 500 earlier credit balance used |
| c4D07 | transaction | UNUNDERSTOOD | \N | \N | \N | \N | \N | \N | ✓ | MULTIPLE_EVENTS |  | only movement today: setup fee 192 (bank_fee); UNUNDERSTOOD accepted as minimum |
| c4D08 | transaction | **AUTO ≠ REVIEW** | 200000 | EGP | out | \N | \N | 1000 | ✗ | EXPLICIT_FEE,UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c4D09 | transaction | AUTO | 72000 | EGP | in | youssef adel | 193000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  |  |
| c4D10 | transaction | **AUTO ≠ UNUNDERSTOOD** | **18500 ≠ \N** | **EGP ≠ \N** | **in ≠ \N** | \N | \N | \N | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | expense_offset | summary of three operations: MULTIPLE_EVENTS |
| c4D11 | transaction | AUTO | 31500 | EGP | out | el ezaby | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 1904 |  |
| c4D12 | transaction | AUTO | 6891633 | EGP | out | hotel am stadtpark | \N | 200824 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 5196 |  |
| c4D13 | transaction | **AUTO ≠ IGNORE\|REVIEW** | 86000 | EGP | out | قيد التنفيذ | \N | 86 | ✗ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | status update, no new debit: IGNORE or REVIEW/DUPLICATE_RISK, never AUTO |
| c4D14 | transaction | **UNUNDERSTOOD ≠ AUTO** | **\N ≠ 90354** | **\N ≠ EGP** | **\N ≠ in** | **\N ≠ booking** | \N | \N | ✗ | REFUND_OR_REVERSAL,MULTIPLE_EVENTS |  | expense_offset; accounting EGP 903.54, original USD 18.50 |
| c4D15 | transaction | AUTO | 46000 | EGP | out | **\N ≠ احمد امين** | **1284000 ≠ 1034000** | \N | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | cheque; available balance; book balance and earlier hold are known roles |
| c4D16 | transaction | AUTO | **4550 ≠ 221025** | **USD ≠ EGP** | out | steamgames | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 | accounting EGP 2,210.25, original USD 45.50; card_purchase, not bank_fee |
| c4D17 | transaction | AUTO | 345000 | EGP | out | \N | 1285050 | 0 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c4D18 | transaction | AUTO | 120000 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 4321, expense_offset |  |
| c4D19 | transaction | AUTO | 25000 | EUR | out | deutsche bank ag | \N | 1500 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c4D20 | **otp ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 189900** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ بي تك** | **\N ≠ 41250** | **\N ≠ 500** | ✗ | VERIFICATION_MESSAGE |  | OTP as a payment method does not make the message an OTP |
| c4D21 | transaction | AUTO | 75000 | EGP | out | **\N ≠ مصر للتامين\|شركه مصر للتامين** | \N | **\N ≠ 2500** | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 9090 | insurance/installment; administrative charges are a fee |
| c4D22 | transaction | **UNUNDERSTOOD ≠ AUTO** | **\N ≠ 500000** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ 01098765432** | **\N ≠ 85000** | **\N ≠ 5000** | ✗ | PROMO_SIGNALS,MULTIPLE_EVENTS |  | fee 50 is explicit; 1% is a rate and "minimum 3" a rule; % is not a promo signal here |
| c4D23 | **promo ≠ other\|reminder** | IGNORE | \N | \N | \N | \N | \N | \N | ✗ | PROMOTIONAL_MESSAGE |  | not a promo |
| c4D24 | transaction | AUTO | 112050 | EGP | out | اوبر مصر | 387950 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3321 |  |
| c4D25 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c4D26 | transaction | AUTO | 465000 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  |  |
| c4D27 | transaction | AUTO | 150000 | EGP | out | \N | \N | \N | ✓ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | REVIEW accepted; ideal AUTO out 1,500 with 950 in the future |
| c4D28 | transaction | AUTO | 125000 | EGP | in | the arab contractors osman ahmed osman co | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  |  |
| c4D29 | transaction | AUTO | 31500 | EGP | out | مصلحه الاحوال المدنيه | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c4D30 | transaction | **REVIEW ≠ AUTO** | 50000 | EGP | **in ≠ out** | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN,MISSING_MERCHANT | card 2210 | penalty/bank_fee; "لبطاقتك" is possession, not destination |
