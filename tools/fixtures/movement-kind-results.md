# Movement kind (step 1 of the device phase)

`movement_kind` is extracted for every completed movement in the corpus vocabulary (`ParsingPolicy.MOVEMENT_KINDS`, 18 values), decided after direction, most specific first: refund/reversal (expense offset) → expiring cashback → wallet top-up → possible internal transfer → wallet payment/transfer → bill → installment → cash withdrawal → salary → inward remittance → instant transfer → transfer → card purchase. No matching wording leaves the kind empty (`\N`). The extractor's internal self-describing kind (fee, interest, cheque, investment, commitment) is a separate field (`selfKind`) and still only suppresses MISSING_MERCHANT. Shadow only: nothing about recording changed.

## Gate (TransactionExtractorTest)

Compared on every v2 transaction row and on legacy rows where the corpus names a kind: **167 / 167**.

| Expected kind | Rows |
| --- | ---: |
| card_purchase | 31 |
| instant_transfer_out | 26 |
| instant_transfer_in | 24 |
| transfer_in | 12 |
| bill_payment | 11 |
| reversal | 11 |
| transfer_out | 9 |
| installment_payment | 7 |
| expiring_cashback | 4 |
| inward_remittance | 4 |
| possible_internal_transfer | 4 |
| salary | 4 |
| wallet_payment | 4 |
| wallet_topup | 4 |
| cash_withdrawal | 3 |
| refund | 3 |
| wallet_transfer_in | 3 |
| wallet_transfer_out | 3 |

## Not scored: legacy-v1 transaction rows without an expected kind

| Extracted kind | Rows |
| --- | ---: |
| \N | 73 |
| card_purchase | 11 |
| cash_withdrawal | 2 |
| transfer_out | 2 |
| refund | 1 |
| salary | 1 |
| wallet_transfer_in | 1 |

## Not scored: held-out sets 1-5 (for the owner's review)

The held-out files carry no kind expectations; their decisions and fields are unchanged (all five sets full).

| Set | # | Decision | Direction | Extracted kind |
| --- | --- | --- | --- | --- |
| 1 | h01 | AUTO | out | \N |
| 1 | h02 | AUTO | out | cash_withdrawal |
| 1 | h05 | AUTO | in | instant_transfer_in |
| 1 | h08 | REVIEW | in | wallet_topup |
| 1 | h09 | AUTO | out | wallet_payment |
| 1 | h10 | AUTO | out | card_purchase |
| 1 | h11 | AUTO | in | \N |
| 1 | h13 | REVIEW | out | possible_internal_transfer |
| 1 | h14 | AUTO | out | bill_payment |
| 1 | h15 | AUTO | out | card_purchase |
| 1 | h17 | AUTO | in | reversal |
| 1 | h18 | AUTO | in | \N |
| 1 | h19 | AUTO | out | instant_transfer_out |
| 1 | h21 | AUTO | in | refund |
| 1 | h22 | AUTO | out | installment_payment |
| 1 | h23 | AUTO | out | \N |
| 1 | h25 | AUTO | out | instant_transfer_out |
| 2 | b01 | AUTO | out | card_purchase |
| 2 | b02 | AUTO | in | instant_transfer_in |
| 2 | b05 | AUTO | out | installment_payment |
| 2 | b06 | REVIEW | out | possible_internal_transfer |
| 2 | b07 | AUTO | out | bill_payment |
| 2 | b08 | AUTO | out | card_purchase |
| 2 | b10 | AUTO | in | refund |
| 2 | b12 | AUTO | out | cash_withdrawal |
| 2 | b14 | AUTO | out | wallet_payment |
| 2 | b15 | AUTO | out | \N |
| 2 | b16 | AUTO | in | salary |
| 2 | b18 | AUTO | out | bill_payment |
| 2 | b19 | AUTO | out | card_purchase |
| 2 | b20 | UNUNDERSTOOD | \N | \N |
| 2 | b21 | AUTO | out | wallet_transfer_out |
| 2 | b23 | AUTO | out | card_purchase |
| 2 | b24 | AUTO | in | instant_transfer_in |
| 2 | b25 | REVIEW | in | expiring_cashback |
