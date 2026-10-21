# Held-out blind set 5: first run on unchanged code

Owner-written 25 October 2026 ([held-out-blind-5.tsv](held-out-blind-5.tsv)), ten routine messages, separate from the corpus and sets 1-4. Run on the rules of `23fded4` (unchanged). The owner's verdict on this raw run: decision 10/10, one field correction (c5E02: `xxx8812` is the user's account, `account_last4`; the party is `\N`). Receipt time 2026-10-25 22:00 UTC.

Messages fully correct (all 8 fields): **9 / 10**

| Field | Correct |
| --- | ---: |
| type | 10 / 10 |
| decision | 10 / 10 |
| amount | 10 / 10 |
| currency | 10 / 10 |
| direction | 10 / 10 |
| party | 9 / 10 |
| balance | 10 / 10 |
| fee | 10 / 10 |

Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.

| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |
| c5E01 | transaction | AUTO | 23000 | EGP | out | carrefour | 327000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 5521 |  |
| c5E02 | transaction | AUTO | 200000 | EGP | in | **8812 ≠ \N** | \N | \N | ✗ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 8812 | 8812 is the user account (account_last4), not a counterparty |
| c5E03 | transaction | AUTO | 6500 | EGP | out | كافيه بينو | 18500 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c5E04 | otp | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | VERIFICATION_MESSAGE |  |  |
| c5E05 | transaction | AUTO | 179900 | EGP | out | amazon | \N | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 9034, limit 2000000 EGP |  |
| c5E06 | transaction | AUTO | 1850000 | EGP | in | شركه المقاولون العرب | 2134075 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_IN | account 8812 |  |
| c5E07 | transaction | AUTO | 40000 | EGP | out | mohamed a instapay | \N | 0 | ✓ | EXPLICIT_FEE,AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  |  |
| c5E08 | promo | IGNORE | \N | \N | \N | \N | \N | \N | ✓ | PROMOTIONAL_MESSAGE |  |  |
| c5E09 | transaction | AUTO | 12000 | EGP | out | محطه وطنيه | 4450 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT |  | text only in textLines |
| c5E10 | transaction | AUTO | 50000 | EGP | out | \N | 277000 | \N | ✓ | AMOUNT_UNAMBIGUOUS,COMPLETED_EVENT,DIRECTION_OUT | card 5521 |  |
