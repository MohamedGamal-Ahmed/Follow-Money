# Glued amounts, previous-transaction notices and service top-ups (owner request, 21 October 2026)

The first on-phone shadow re-scan found that every Bank Misr purchase (96 messages) glues the word عند to the amount: `EGP 15.00عند KASHIER*BUTLERS & CO C`. The money pattern refused a number followed by a letter, then backtracked to accept a shorter one, so `149.98عند` read as 149 and the piasters were lost.

## General rules (policy 2026-10-27.1, extraction-3, classification-2, normalization-2)

| Rule | Where | Effect |
| --- | --- | --- |
| A number ends at the first character that is neither a digit nor a separator before a digit; a glued word or currency after it is a separate word. | `AmountScanner` | `15.00عند` → 15.00 + عند; `149.98EGP` → 149.98; `EGP15.00` → 15.00. A bare number is still never money. |
| A mask repeats `*`/`#` or touches a digit (`***4636`, `#1234`). One `*` between letters separates a processor from its merchant. | `TransactionExtractor.candidate` | `KASHIER*BUTLERS` reaches merchant normalization, which removes the processor. |
| A fixed-width terminal name cut one letter after a company form (`& CO C`): the fragment and the form go together. A complete form stays. | `MerchantNormalizer` | `BUTLERS & CO C` → butlers; corpus p117 `Butler's & Co.! EG` → butler s co, unchanged. |
| `Bill Payment` before a biller is a descriptor, like a payment processor. | `ParsingPolicy.BARE_PROCESSORS` | `Bill Payment Vodafone` → vodafone (bill_payment). |
| A 1-3 digit store number right before the date word (يوم, on, بتاريخ) is part of the merchant name. | `ParsingPolicy.MERCHANT_STOP` | `ZAHER-AIN SHAMS 1 يوم` → zaher ain shams 1, matching the corpus policy that a branch number alone is never removed. |
| `تم الآن خصم` is purchase wording like `تم خصم`. | `ParsingPolicy.PURCHASE` | The Bank Misr purchase gets the card_purchase kind. |
| A reference to an earlier operation (`الحركة التي تمت`, `آخر حركة`, `the transaction made`) with no new movement verb of its own moves no money. | `MessageClassifier` | The contactless notice is other / IGNORE / NO_NEW_MOVEMENT. |
| `شحن رصيدك` / `recharge balance` names what was topped up, not a stated balance. | `ParsingPolicy.ROLE_BALANCE` | `ب 136.80 ج.م.` is the principal, not MISSING_AMOUNT. |
| `تم شحن` to a line or service is outgoing; to the user's own wallet, account or card it stays incoming. | `ParsingPolicy.DIRECTION_OUT` | `تم شحن الإنترنت الأرضي` is out, not AMBIGUOUS_DIRECTION. |
| A top-up confirmation that names no paying account, card, wallet or channel is `service_topup` with **PAYER_UNKNOWN**, a review cap: the provider confirms receipt, and the same payment may already be recorded from the bank or wallet message. | `TransactionExtractor.movement`, `ParsingPolicy.REVIEW_CAPS` | Both WE samples → transaction / out / REVIEW. A Fawry recharge (channel named) stays bill_payment / AUTO. |

## Corpus (v2:21aa1285, 392 rows, +23)

Evidence level `user_redacted` (owner phone samples) in `corpus-v2-sources.tsv`:

- **bm01:** the Bank Misr purchase above → AUTO, out, 15.00 EGP, merchant butlers, card 4636, balance 2139.44, date 2026-10-20, card_purchase. It has:
  - digit, spacing and field-order variants;
  - amount/merchant variants: 44.95 SEOUDI SODIC EAST, 1897.71 ZAHER-AIN SHAMS 1, 490.00 Bill Payment Vodafone;
  - glued-shape variants: `EGP 15عند`, `15.00EGP عند`, `EGP15.00عند`, `15.00ج.م عند`.
- **bm02:** the contactless "last transaction" notice → other / IGNORE / NO_NEW_MOVEMENT, with three variants.
- **we01, we02:** the WE line and landline-internet top-ups → transaction / out / service_topup / REVIEW / PAYER_UNKNOWN, with three variants each.

Expected values were authored from the owner's instructions. Two of them are my reading where the owner gave no key, for the owner to confirm:
- ZAHER keeps its branch number 1.
- The WE "رصيدك" figure is recorded as the line's balance.

## Gates

- Corpus: all 392 rows pass, with 0 listed disagreements. That's 4330 field comparisons and 186 movement kinds.
- Held-out sets: 25/25, 25/25, 48/48, 30/30 and 10/10.
- New `AmountScannerTest` (the general "no fraction lost" check): 504 generated glued shapes (7 numbers × 3 units × 4 glued words × 6 forms) all read exactly. Across every corpus and held-out text, no scanned amount stops before `.digit` or `,digit` (763 amounts).

## Follow-up: currency glued to the next word (policy 2026-10-27.2, corpus v2:f140a4b0, 393 rows)

The S938B re-scan still showed 79 Banque Misr purchases with no amount. Their masked shapes, read on the phone with the owner's approval, are one form: `تم الان خصم <amount> <currency>عند <name> يوم <date>`. The currency follows the amount and عند is glued to it (`44.95 EGPعند`).

- **General rule:** a currency ends where a word of the other script begins. `EGPعند` is EGP + عند, and `ج.مat` is ج.م + at. A Latin currency is never cut out of a Latin word, nor an Arabic currency out of an Arabic one, so `جنيهات` stays one word.
- **Same rule for merchant markers:** an Arabic marker (عند, لدى) may follow a Latin letter or a digit.
- **Corpus:** new variant `bm01-currency_after_glued_word` (`15.00 EGPعند`). The generated check grows to 588 glued shapes.
- **Gates:** all 393 corpus rows, 0 listed disagreements; held-out sets 25/25, 25/25, 48/48, 30/30 and 10/10; 765 corpus/held-out amounts never cut.
