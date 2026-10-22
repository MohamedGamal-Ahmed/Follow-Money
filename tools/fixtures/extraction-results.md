# Phase 3: Extraction (shadow mode) test report, 21 October 2026

Corpus contract `v2:6c8b865e`, policy `2026-10-21.3`. The legacy path still decides everything. Extraction runs only
inside `ParserShadow`, after legacy capture is final. Observations hold field *names*, never values, and live in memory.

## What was built

`TransactionExtractor` reads one classified transaction from folded text. The folding keeps an index map back to the
original spelling, so a merchant's original letters (harakat included) are preserved.

| Field | How it is read |
| --- | --- |
| Principal and currency | Each currency amount is tagged by role from the words before it in the same clause: balance, available limit (a credit-card "available" amount is a limit), fee, or stated equivalent. A fee in a future-tense clause is not a fee. Per your amendment, a stated account-currency equivalent is the principal and the foreign amount goes to `original_*`. Ambiguous format, ambiguous currency, a leading minus, a zero amount or no amount stay unread and get a reason. |
| Direction | In order: trailing `+`/`-`, refund/reversal, then the earliest in/out verb (Arabic needs a standalone تم). A terse card+merchant alert with no verb gives "likely out". |
| Merchant | In order: quoted text; `at` / `@ ` / لدى / عند; bill and recharge forms; then from / to / من / إلى / ل. Rejected: masks, numbers, account/card/wallet nouns, InstaPay addresses (these become the counterparty), and `From X:` headers. The key comes from `MerchantNormalizer`, which now also drops "Branch 12" / "BR 009". |
| Card / account / counterparty | Card and account masks are kept separate. "to account 1234" is the recipient on an outgoing transfer but the user's own account on an incoming one. IPN on/from accounts, phone and InstaPay counterparties are handled. |
| Bank time | ISO, d/m/y, 2-digit years, month names (Arabic and English), yearless d/m resolved with the receipt year (`received_year` or `adjacent_year`). Day/month pairs that could be either order are not guessed. A final `at hh:mm` with no AM/PM is treated as cut off. Expiry dates are kept separate from the event date. |
| Provisional decision | Rules, no scoring (scoring is phase 4): unreadable amount/currency/direction → UNUNDERSTOOD; likely direction, possible internal transfer, expiring credit or a purchase with no merchant → REVIEW; otherwise AUTO. A reversal is tagged `expense_offset`. |

All new vocabulary and limits are in `ParsingPolicy`.

## Shadow comparison (the per-row legacy-vs-new table and its generator, ShadowComparison, were removed in 0.4.0 with the legacy opinion)

A row counts as correct when the decision equals the expected standalone decision and, for transaction rows, amount,
currency, direction and merchant key all equal the expectation (null included).

| Outcome | Rows |
| --- | ---: |
| Both correct | 159 |
| **New correct, legacy wrong** | **209** |
| **Legacy correct, new wrong** | **0** |
| Both wrong | 1 (p136, below) |
| Decision only, equal to expected: new / legacy | 369 / 297 |

Legacy can output only AUTO or IGNORE. It therefore cannot be right on the 22 rows that expect REVIEW or
UNUNDERSTOOD; that is part of the 72-row decision gap.

| Principal field (258 transaction rows) | New correct | Legacy correct |
| --- | ---: | ---: |
| amount | 258 | 199 |
| currency | 257 | 201 |
| direction | 258 | 202 |
| merchant key | 258 | 76 |

Secondary fields are compared new vs expected (legacy does not produce them). All match except `card last4`, which
matches on 256 of 258. Balance, fee, credit limit, original amount/currency, account, counterparty, datetime,
precision, time ambiguity, year origin, expiry and `expense_offset` match on every compared row.

## Disagreements left for you (the corpus was not edited)

| Row | Corpus expects | New extractor gives | Why they differ |
| --- | --- | --- | --- |
| p011 | `card_last4 = 2222` for "Account x2222" | account 2222, card null | v1 rows put account masks in the card column. v2 rows separate them (for example u01: account 1234, card null). |
| p146 | `card_last4 = 7733` for "من حساب ****7733" | account 7733, card null | Same v1/v2 difference. |
| p136 | currency EGP on "Debited EGP 100 and credited EGP 50" | currency null (UNUNDERSTOOD) | p014 and p135, the same multi-event shape and also all EGP, expect a null currency. The corpus is inconsistent with itself. |

These three are listed in `TransactionExtractorTest.KNOWN_DISAGREEMENTS`. The test fails if one silently starts to
agree, or if a new disagreement appears. Please decide each one: change the corpus, or change the extractor.

## Tests (`tools/run-checks.ps1`, exit 0)

| Suite | Result |
| --- | --- |
| `TransactionExtractorTest` | PASS, 4882 checks. Every corpus decision; 3647 corpus field comparisons; required reasons; reversal `expense_offset`; the 3 listed disagreements; 10 held-out messages that are not in the corpus. Two of the held-out messages exposed real gaps, now fixed: a merchant run not stopping at "has been", and `بطاقتك المنتهية 4411` written without `بـ`. |
| `ParserShadowTest` | PASS, 3528 checks. The shadow provisional decision equals the expectation for every row. Observations carry no message text. No legacy class or ledger storage reads the new stages. |
| `ShadowComparison extraction` / `classification` | Regenerated. The classification numbers are unchanged (311 / 58 / 0 / 0). |
| Earlier suites | PASS, unchanged. |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | PASS. Lint: 0 errors, 63 warnings (unchanged). |

## Limits

- The rules were written with this corpus in view, so near-perfect corpus scores are expected. The held-out sets (18
  for classification, 10 for extraction) are the real generality signal, and they are small. Real device traffic is
  the next real test.
- The "cut-off `at hh:mm`" rule and the d/m ambiguity rule are heuristics taken from the corpus cases.
- `expected_movement_kind` (instant transfer, salary, card purchase, and so on) and the remaining accounting effects
  (`pending`, `pending_internal`) are not extracted yet. Possible internal transfers are only flagged as a REVIEW reason.
  Phone Banking pairing (context) is not implemented, so those rows are judged on their standalone decision.
- Not run on an emulator or device. Shadow observations are in memory only. The encrypted store and the hidden debug
  screen are still pending.
