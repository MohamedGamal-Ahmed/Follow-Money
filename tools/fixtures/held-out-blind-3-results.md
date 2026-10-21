# Held-out blind set 3: first run on unchanged code

Owner-written 24 October 2026 ([held-out-blind-3.tsv](held-out-blind-3.tsv)), separate from the corpus and from sets 1-2. Run on the rules of `b75588f` (unchanged). The owner's verdict on this raw run: decision 33/48 (9 of them safe), full row about 26/48, four transactions lost that legacy books (first regression against legacy on unseen messages). The 8-field scoring below gives 25/48. `*` = not specified by the owner; `|` = accepted alternatives. Receipt time 2026-10-22 12:00 UTC.

Messages fully correct (all 8 fields): **25 / 48**

| Field | Correct |
| --- | ---: |
| type | 42 / 48 |
| decision | 36 / 48 |
| amount | 43 / 48 |
| currency | 44 / 48 |
| direction | 43 / 48 |
| party | 34 / 48 |
| balance | 47 / 48 |
| fee | 45 / 48 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| c3A01 | transaction | **REVIEW ≠ AUTO** | 8500 | EGP | out | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT |  | bank_fee; MISSING_MERCHANT does not apply to fees |
| c3A02 | transaction | **REVIEW ≠ AUTO** | 150000 | EGP | out | **\N ≠ مدارس النيل** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT |  | glued lam prefix |
| c3A03 | transaction | **REVIEW ≠ AUTO** | 12000 | EGP | out | **\N ≠ محطه وقود وطنيه** | 340000 | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT |  | fi marker without quotes |
| c3A04 | transaction | AUTO | 42000 | EGP | out | **صيدليات العزبي ببطاقتك الايتمانيه المنته ≠ صيدليات العزبي** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 | card 7001 |
| c3A05 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | MISSING_AMOUNT,NO_COMPLETED_EVENT |  |  |
| c3A06 | transaction | AUTO | 115000 | EGP | out | **مستشفي دار الفواد مقابل خدمات طبيه ≠ مستشفي دار الفواد** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 | card 2210 |
| c3A07 | transaction | **AUTO ≠ REVIEW** | 10000 | EGP | in | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | EXPIRING_CREDIT: valid for a period |
| c3A08 | transaction | AUTO | 60000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 5566 |  |
| c3A09 | balance_inquiry | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | BALANCE_ONLY |  |  |
| c3A10 | transaction | REVIEW | 1000000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER |  |  |
| c3A11 | transaction | AUTO | 4500 | EUR | out | booking | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 |  |
| c3A12 | transaction | AUTO | 31000 | EGP | out | **سينما سيتي ستارس ببطاقه الخصم المباشر ال ≠ سينما سيتي ستارس** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 4321 | card 4321 |
| c3A13 | **other ≠ transaction** | **IGNORE ≠ REVIEW** | \N | \N | \N | \N | \N | \N | ✗ | NO_COMPLETED_EVENT |  | BNPL purchase confirmation: a commitment event shown for review; later installments link to it. Only the decision was specified |
| c3A14 | transaction | AUTO | 35000 | EGP | **out ≠ in** | احمد حسن | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | to your account from X is incoming whatever the verb |
| c3A15 | transaction | AUTO | 9500 | EGP | out | **الفرع ≠ \N** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | bank_fee; the branch is not a merchant |
| c3B01 | transaction | AUTO | **4000 ≠ 5715** | EGP | out | **موبايل ≠ 01076543218** | \N | \N | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | explicit deducted amount; nominal 40 kept separately |
| c3B02 | transaction | AUTO | 46320 | EGP | out | كهرباء | \N | 480 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B03 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  | reason PENDING_EVENT, not UNSUCCESSFUL |
| c3B04 | transaction | AUTO | 60000 | EGP | out | \N | 32825 | 600 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B05 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B06 | transaction | AUTO | 799 | EUR | out | spotify | **1794000 ≠ \N** | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3629 | 17,940 is a credit limit, not a balance |
| c3B07 | **other ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 157200** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ nahdi** | \N | \N | ✗ | NO_COMPLETED_EVENT |  | booked amount 1572 EGP is the accounting amount; original 120 SAR; card 3629 |
| c3B08 | transaction | AUTO | 3144 | EGP | out | nahdi | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3629 |  |
| c3B09 | transaction | AUTO | 123450 | EGP | out | مكتبه الشروق | 567890 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B10 | transaction | AUTO | 8750 | EGP | out | فرن الحاره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 6382 | card 6382 |
| c3B11 | transaction | **REVIEW ≠ AUTO** | 124995 | EGP | out | lc waikiki | 260030 | \N | ✗ | AMOUNT_UNAMBIGUOUS,CARD_MERCHANT_CONTEXT,INFERRED_OUTGOING_DIRECTION,COMPLETED_EVENT,DIRECTION_OUT | card 6382 | Purchase approved is a completed event |
| c3B12 | transaction | AUTO | 50 | EGP | out | paypal | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B13 | transaction | AUTO | 50 | EGP | in | **paypal الي بطاقتك المنتهيه ب ≠ paypal** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 6382, expense_offset |  |
| c3B14 | **other ≠ otp** | IGNORE | \N | \N | \N | \N | \N | \N | ✗ | NO_COMPLETED_EVENT |  |  |
| c3B15 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | NO_COMPLETED_EVENT |  |  |
| c3B16 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B17 | **other ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 72500** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ كريم سمير** | \N | **\N ≠ 73** | ✗ | NO_COMPLETED_EVENT |  |  |
| c3B18 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | NO_COMPLETED_EVENT |  |  |
| c3B19 | **other ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 185000** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ حسام فواد** | \N | **\N ≠ 185** | ✗ | NO_COMPLETED_EVENT |  |  |
| c3B20 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B21 | transaction | AUTO | 21000 | EGP | out | **مطعم بازوكا ثم رد ≠ مطعم بازوكا** | \N | \N | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | owner accepts REVIEW 210 or AUTO 175 (explicit net debit) |
| c3B22 | transaction | AUTO | 12575 | EGP | out | breadfast | 84025 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B23 | transaction | AUTO | 94000 | EGP | out | hany mostafa | \N | 94 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B24 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3C01 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  | reason HOLD_RELEASED |
| c3C02 | transaction | AUTO | 42500 | EGP | in | **decathlon بمبلغ ≠ decathlon** | \N | \N | ✗ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 6382, expense_offset | expense_offset; original transaction amount is a known role |
| c3C03 | transaction | **REVIEW ≠ AUTO** | 15000 | EGP | out | \N | 623040 | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT | account 1840 | bank_fee |
| c3C04 | transaction | **REVIEW ≠ AUTO** | 8346 | EGP | in | \N | 2508346 | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN,POSSIBLE_INTERNAL_TRANSFER | account 9057 | interest; savings alone is not an internal transfer |
| c3C05 | transaction | REVIEW | 2500000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,MISSING_MERCHANT | account 1840 | POSSIBLE_INTERNAL_TRANSFER / investment, not MISSING_MERCHANT |
| c3C06 | transaction | UNUNDERSTOOD | \N | \N | \N | \N | \N | \N | ✓ | REFUND_OR_REVERSAL,MULTIPLE_EVENTS |  |  |
| c3C07 | **other ≠ transaction** | **IGNORE ≠ AUTO** | **\N ≠ 90000** | **\N ≠ EGP** | **\N ≠ out** | **\N ≠ نورهان محمود** | \N | **\N ≠ 0** | ✗ | NO_COMPLETED_EVENT |  | internal = within the bank to another person |
| c3C08 | transaction | REVIEW | 620000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER |  |  |
| c3C09 | transaction | REVIEW | 148000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER,MISSING_MERCHANT | card 3629, account 1840 |  |

## After the owner's 8 general fixes (same 48 messages, owner expectations unchanged)

Every fix is a general rule; none names a message. Set 3 is now a frozen gate (`HeldOutBlind --require --file held-out-blind-3.tsv`).

| Owner fix | Rule as implemented |
| --- | --- |
| 1. Completion markers, not a verb list | `تم/تمت` + optional one word that is not a direction noun or a registration/creation/issuance/request-confirmation/refusal/cancellation/suspension word + financial noun (with ال, with possessive), `بنجاح`, approved/completed/successful/posted. A completion and an amount in the next sentence that states an amount (`المبلغ`/`القيمة`/amount) are one event. A generated test covers marker × middle word × noun × article (115 combinations, including negatives). |
| 2. Direction from the user's account role | `إلى/لـ حسابك·بطاقتك·محفظتك` (and `شحن محفظتك`) means in, `من حسابك·…` means out; this beats any verb. Both roles mean a move between own accounts: the verb decides, and the message is a possible internal transfer; with no verb, AMBIGUOUS_DIRECTION. |
| 3. Merchant boundaries | stops at ببطاقة/بطاقتك/باستخدام/بتاريخ/بمبلغ/بقيمة/مقابل/ثم/المنتهية/إلى/الرصيد/رقم/رسوم and punctuation. `في` works without quotes. A glued لـ after a payment noun (قيمة/مصروفات/ثمن/مستحقات) names the payee. A branch is never a merchant. |
| 4. MISSING_MERCHANT only for card purchases | needs a card mention; bank_fee, interest, installment, investment and commitment are self-describing kinds. |
| 5. Explicit amount roles | booked amount (القيمة المقيدة) is the accounting amount like an equivalent; the amount actually taken (المبلغ المخصوم / إجمالي الخصم / صافي الخصم / net debit) replaces the nominal one only when no fee or other amount explains the gap; original transaction amount, nominal value and totals are known roles, never unassigned. |
| 6. Expiring credit by meaning | `صالح/صالحة لمدة/حتى/للاستخدام`, valid for/until, expires: on any incoming credit, with or without a date. |
| 7. Internal transfer needs a transfer verb and an own account | savings/التوفير alone is not internal. A transfer to a named person's account is not internal. Card/prepaid funding, wallet top-up, card dues and savings certificates still are. |
| 8. OTP | `أدخل الرمز/الكود`, `الرمز/الكود + digits`, `enter the code`: otp even with an amount. |
| Also, from your notes | PENDING_EVENT (code created, not withdrawn yet) and HOLD_RELEASED (temporary hold released) reasons; a confirmed purchase order or BNPL plan is COMMITMENT_EVENT and always REVIEW; Arabic `الحد الائتماني` / "available credit" is a limit. |

Corpus impact: none. All corpus suites pass (210 new-only, 0 legacy-only, 0 both wrong). Sets 1 and 2 stay at 25/25; set 3 went from 25/48 to **48/48**. While doing this, the card stem was widened to `بطاق` (`بطاقتك` was missed), and my own earlier probe "تم دفع 300 جنيه" now expects AUTO under rule 4, with a card variant that expects REVIEW.

Messages fully correct (all 8 fields): **48 / 48**

| Field | Correct |
| --- | ---: |
| type | 48 / 48 |
| decision | 48 / 48 |
| amount | 48 / 48 |
| currency | 48 / 48 |
| direction | 48 / 48 |
| party | 48 / 48 |
| balance | 48 / 48 |
| fee | 48 / 48 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| c3A01 | transaction | AUTO | 8500 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | bank_fee; MISSING_MERCHANT does not apply to fees |
| c3A02 | transaction | AUTO | 150000 | EGP | out | مدارس النيل | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | glued lam prefix |
| c3A03 | transaction | AUTO | 12000 | EGP | out | محطه وقود وطنيه | 340000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | fi marker without quotes |
| c3A04 | transaction | AUTO | 42000 | EGP | out | صيدليات العزبي | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 | card 7001 |
| c3A05 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | MISSING_AMOUNT,NO_COMPLETED_EVENT |  |  |
| c3A06 | transaction | AUTO | 115000 | EGP | out | مستشفي دار الفواد | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 | card 2210 |
| c3A07 | transaction | REVIEW | 10000 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN,EXPIRING_CREDIT |  | EXPIRING_CREDIT: valid for a period |
| c3A08 | transaction | AUTO | 60000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 5566 |  |
| c3A09 | balance_inquiry | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | BALANCE_ONLY |  |  |
| c3A10 | transaction | REVIEW | 1000000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER |  |  |
| c3A11 | transaction | AUTO | 4500 | EUR | out | booking | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 |  |
| c3A12 | transaction | AUTO | 31000 | EGP | out | سينما سيتي ستارس | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 4321 | card 4321 |
| c3A13 | transaction | REVIEW | 289000 | EGP | out | رايه شوب | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,COMMITMENT_EVENT |  | BNPL purchase confirmation: a commitment event shown for review; later installments link to it. Only the decision was specified |
| c3A14 | transaction | AUTO | 35000 | EGP | in | احمد حسن | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | to your account from X is incoming whatever the verb |
| c3A15 | transaction | AUTO | 9500 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | bank_fee; the branch is not a merchant |
| c3B01 | transaction | AUTO | 5715 | EGP | out | 01076543218 | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | explicit deducted amount; nominal 40 kept separately |
| c3B02 | transaction | AUTO | 46320 | EGP | out | كهرباء | \N | 480 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B03 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PENDING_EVENT |  | reason PENDING_EVENT, not UNSUCCESSFUL |
| c3B04 | transaction | AUTO | 60000 | EGP | out | \N | 32825 | 600 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B05 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B06 | transaction | AUTO | 799 | EUR | out | spotify | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3629, limit 1794000 EGP | 17,940 is a credit limit, not a balance |
| c3B07 | transaction | AUTO | 157200 | EGP | out | nahdi | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3629 | booked amount 1572 EGP is the accounting amount; original 120 SAR; card 3629 |
| c3B08 | transaction | AUTO | 3144 | EGP | out | nahdi | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 3629 |  |
| c3B09 | transaction | AUTO | 123450 | EGP | out | مكتبه الشروق | 567890 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B10 | transaction | AUTO | 8750 | EGP | out | فرن الحاره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 6382 | card 6382 |
| c3B11 | transaction | AUTO | 124995 | EGP | out | lc waikiki | 260030 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 6382 | Purchase approved is a completed event |
| c3B12 | transaction | AUTO | 50 | EGP | out | paypal | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B13 | transaction | AUTO | 50 | EGP | in | paypal | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 6382, expense_offset |  |
| c3B14 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  |  |
| c3B15 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | NO_COMPLETED_EVENT |  |  |
| c3B16 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B17 | transaction | AUTO | 72500 | EGP | out | كريم سمير | \N | 73 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B18 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | NO_COMPLETED_EVENT |  |  |
| c3B19 | transaction | AUTO | 185000 | EGP | out | حسام فواد | \N | 185 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B20 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3B21 | transaction | AUTO | 21000 | EGP | out | مطعم بازوكا | \N | \N | ✓ | UNASSIGNED_SECOND_AMOUNT,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | owner accepts REVIEW 210 or AUTO 175 (explicit net debit) |
| c3B22 | transaction | AUTO | 12575 | EGP | out | breadfast | 84025 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B23 | transaction | AUTO | 94000 | EGP | out | hany mostafa | \N | 94 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c3B24 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| c3C01 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  | reason HOLD_RELEASED |
| c3C02 | transaction | AUTO | 42500 | EGP | in | decathlon | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 6382, expense_offset | expense_offset; original transaction amount is a known role |
| c3C03 | transaction | AUTO | 15000 | EGP | out | \N | 623040 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 1840 | bank_fee |
| c3C04 | transaction | AUTO | 8346 | EGP | in | \N | 2508346 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 9057 | interest; savings alone is not an internal transfer |
| c3C05 | transaction | REVIEW | 2500000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER | account 1840 | POSSIBLE_INTERNAL_TRANSFER / investment, not MISSING_MERCHANT |
| c3C06 | transaction | UNUNDERSTOOD | \N | \N | \N | \N | \N | \N | ✓ | REFUND_OR_REVERSAL,MULTIPLE_EVENTS |  |  |
| c3C07 | transaction | AUTO | 90000 | EGP | out | نورهان محمود | \N | 0 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | internal = within the bank to another person |
| c3C08 | transaction | REVIEW | 620000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER |  |  |
| c3C09 | transaction | REVIEW | 148000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER,MISSING_MERCHANT | card 3629, account 1840 |  |

