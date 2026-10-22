# Held-out blind set 2: first run on unchanged code

Owner-written 22 October 2026 ([held-out-blind-2.tsv](held-out-blind-2.tsv)), separate from the corpus and from set 1. Run on the rules of `a0dd42d` (unchanged; only the corpus refund decision `4c5b044` followed, which changes no rule). The owner's verdict on this raw run: decision 20/25, full row 17/25, matching the table below. Receipt time 2026-10-22 12:00 UTC.

Messages fully correct (all 8 fields): **17 / 25**

| Field | Correct |
| --- | ---: |
| type | 24 / 25 |
| decision | 21 / 25 |
| amount | 23 / 25 |
| currency | 23 / 25 |
| direction | 24 / 25 |
| party | 22 / 25 |
| balance | 25 / 25 |
| fee | 25 / 25 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| b01 | transaction | AUTO | 8990 | EGP | out | talabat | 411010 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 |  |
| b02 | transaction | AUTO | 300000 | EGP | in | احمد م | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 5566 | transfer party, accepted as party until movement kind is extracted |
| b03 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  |  |
| b04 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| b05 | transaction | AUTO | 112000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 9090 |  |
| b06 | transaction | **AUTO ≠ REVIEW** | 250000 | EGP | out | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 1122 | POSSIBLE_INTERNAL_TRANSFER: savings account of the user |
| b07 | transaction | AUTO | 1500 | USD | out | apple | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001, limit 900000 EGP |  |
| b08 | transaction | AUTO | **1125 ≠ 54000** | **USD ≠ EGP** | out | carrefour maadi | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 | accounting amount EGP 540.00, original USD 11.25; parenthesized equivalent without the word |
| b09 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| b10 | transaction | AUTO | 25000 | EGP | in | noon | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 4321, expense_offset | expense_offset |
| b11 | balance_inquiry | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | BALANCE_ONLY |  |  |
| b12 | transaction | AUTO | 100000 | EGP | out | **another bank s atm ≠ \N** | 298000 | 2000 | ✗ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 8877 | cash withdrawal; an ATM phrase is not a merchant |
| b13 | **balance_inquiry ≠ reminder** | IGNORE | \N | \N | \N | \N | \N | \N | ✗ | BALANCE_ONLY |  | due tomorrow |
| b14 | transaction | AUTO | 32050 | EGP | out | seoudi market | 17950 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| b15 | transaction | AUTO | 7000 | EGP | out | uber | 24650 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | text only in textLines; text and bigText present but empty |
| b16 | transaction | AUTO | 1250000 | EGP | in | شركه النور للتجاره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 5566 |  |
| b17 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | MISSING_AMOUNT,NO_COMPLETED_EVENT |  |  |
| b18 | transaction | AUTO | 19900 | EGP | out | **انترنت ≠ we** | \N | 200 | ✗ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | brand after the service name |
| b19 | transaction | AUTO | 4500 | EGP | out | مطعم ابو شقره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 |  |
| b20 | transaction | **AUTO ≠ UNUNDERSTOOD** | **6000 ≠ \N** | **EGP ≠ \N** | **out ≠ \N** | **cilantro and egp ≠ \N** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 | MULTIPLE_EVENTS: two events joined by and |
| b21 | transaction | AUTO | 50000 | EGP | out | 01112223334 | 9750 | 250 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| b22 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| b23 | transaction | **REVIEW ≠ AUTO** | 220000 | EGP | out | ikea | 191010 | \N | ✗ | AMOUNT_UNAMBIGUOUS,CARD_MERCHANT_CONTEXT,INFERRED_OUTGOING_DIRECTION,COMPLETED_EVENT,DIRECTION_OUT | card 2210 | "was used for" is a completed purchase |
| b24 | transaction | AUTO | 80000 | EGP | in | mona k | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | title only |
| b25 | transaction | **AUTO ≠ REVIEW** | 35000 | EGP | in | \N | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 5566 | EXPIRING_CREDIT: valid-until credit |

## After general-rule fixes (same 25 messages, owner expectations unchanged)

All fixes are general; none names a message. Set 2 is now a frozen gate (`HeldOutBlind --require --file held-out-blind-2.tsv`).

| Gap (found by) | General rule added |
| --- | --- |
| Two events joined by and/و where the second has no verb (b20) | after one completed event, any further clause with its own amount and a merchant marker is another event, so the message is MULTIPLE_EVENTS (safety rule) |
| A second amount with no role (owner safety rule) | a same-currency amount that is not a balance, fee, total, limit or equivalent gives REVIEW with `UNASSIGNED_SECOND_AMOUNT`; `total`/`الإجمالي` is a known role |
| Transfer to the user's own savings account (b06) | self-account markers (`الخاص بكم`, `حسابكم الآخر`, `التوفير`, `your savings/other account`) mean POSSIBLE_INTERNAL_TRANSFER |
| Parenthesized equivalent without the word (b08) | a different-currency amount in parentheses right after the principal is its equivalent, and the account-currency amount becomes the principal |
| "صالح للاستخدام حتى" (b25) | any `صالح/صالحة (للاستخدام) حتى` / `valid (for use) until` on a deposit is EXPIRING_CREDIT |
| "was used for" (b23) | a completed purchase verb |
| ATM phrase as merchant (b12) | a bare ATM/الصراف/ماكينة phrase is never a merchant; a named terminal such as `NBE ATM546` (corpus) stays |
| Service name instead of brand (b18) | after `فاتورة إنترنت/موبايل/هاتف…` the following brand is the merchant |
| "مستحق غدًا" (b13) | due-date wording (`مستحق غدًا/اليوم/في`, `تاريخ الاستحقاق`) is future |
| Found by fresh probes, not by the sets | an amount in a future/request clause is never a principal or a second event ("a fee of EGP 2 will be charged") |

The corpus, set 1 and set 2 all pass. Decisions are unchanged from phase 3 and still match the expectations; nothing regressed.

Messages fully correct (all 8 fields): **25 / 25**

| Field | Correct |
| --- | ---: |
| type | 25 / 25 |
| decision | 25 / 25 |
| amount | 25 / 25 |
| currency | 25 / 25 |
| direction | 25 / 25 |
| party | 25 / 25 |
| balance | 25 / 25 |
| fee | 25 / 25 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| b01 | transaction | AUTO | 8990 | EGP | out | talabat | 411010 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 |  |
| b02 | transaction | AUTO | 300000 | EGP | in | احمد م | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 5566 | transfer party, accepted as party until movement kind is extracted |
| b03 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  |  |
| b04 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| b05 | transaction | AUTO | 112000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | account 9090 |  |
| b06 | transaction | REVIEW | 250000 | EGP | out | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT,POSSIBLE_INTERNAL_TRANSFER | account 1122 | POSSIBLE_INTERNAL_TRANSFER: savings account of the user |
| b07 | transaction | AUTO | 1500 | USD | out | apple | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001, limit 900000 EGP |  |
| b08 | transaction | AUTO | 54000 | EGP | out | carrefour maadi | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 7001 | accounting amount EGP 540.00, original USD 11.25; parenthesized equivalent without the word |
| b09 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | UNSUCCESSFUL_EVENT |  |  |
| b10 | transaction | AUTO | 25000 | EGP | in | noon | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | card 4321, expense_offset | expense_offset |
| b11 | balance_inquiry | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | BALANCE_ONLY |  |  |
| b12 | transaction | AUTO | 100000 | EGP | out | \N | 298000 | 2000 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 8877 | cash withdrawal; an ATM phrase is not a merchant |
| b13 | reminder | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | FUTURE_ONLY |  | due tomorrow |
| b14 | transaction | AUTO | 32050 | EGP | out | seoudi market | 17950 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| b15 | transaction | AUTO | 7000 | EGP | out | uber | 24650 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | text only in textLines; text and bigText present but empty |
| b16 | transaction | AUTO | 1250000 | EGP | in | شركه النور للتجاره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 5566 |  |
| b17 | other | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | MISSING_AMOUNT,NO_COMPLETED_EVENT |  |  |
| b18 | transaction | AUTO | 19900 | EGP | out | we | \N | 200 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | brand after the service name |
| b19 | transaction | AUTO | 4500 | EGP | out | مطعم ابو شقره | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 |  |
| b20 | transaction | UNUNDERSTOOD | \N | \N | \N | \N | \N | \N | ✓ | MULTIPLE_EVENTS |  | MULTIPLE_EVENTS: two events joined by and |
| b21 | transaction | AUTO | 50000 | EGP | out | 01112223334 | 9750 | 250 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| b22 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| b23 | transaction | AUTO | 220000 | EGP | out | ikea | 191010 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 2210 | "was used for" is a completed purchase |
| b24 | transaction | AUTO | 80000 | EGP | in | mona k | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN |  | title only |
| b25 | transaction | REVIEW | 35000 | EGP | in | \N | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN,EXPIRING_CREDIT,PROMO_SIGNALS | account 5566 | EXPIRING_CREDIT: valid-until credit |

