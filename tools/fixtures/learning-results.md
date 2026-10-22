# Phase 5: Local learning (shadow mode) test report, 22 October 2026

Corpus contract `v2:bcf53eb0`, policy `2026-10-22.2`. The legacy path still decides everything. `LocalLearning` runs
last inside `ParserShadow`, on-device only; nothing is uploaded.

## Before phase 5

- **Refund decision** (`4c5b044`): `pub-cib-refund` and its two variants expect `expense_offset`. `KNOWN_DISAGREEMENTS` is empty.
- **Blind set 2** ([held-out-blind-2.tsv](held-out-blind-2.tsv)):
  - The raw run on unchanged code gave **17/25**, matching your verdict, and was committed before any fix (`44c4c22`, `ff04ee4`).
  - General rules, including your two safety rules, brought it to **25/25** (`ac31a90`). The two safety rules:
    - **Unassigned second amount:** a same-currency second amount with no role gives REVIEW with `UNASSIGNED_SECOND_AMOUNT`.
    - **Second event:** after one completed event, another "amount + merchant" clause gives MULTIPLE_EVENTS.
  - Set 2 is now a frozen gate next to set 1. Details: [held-out-blind-2-results.md](held-out-blind-2-results.md).
- **Two more gaps found by fresh probes, not by the sets:** a fee in a future clause ("will be charged next month")
  is neither a principal nor a second event.

Decision distribution after those fixes (no regression; decisions unchanged from phase 3):

| Decision | Corpus expected = phase 4 | Legacy | Set 1 expected = phase 4 | Set 2 expected = phase 4 |
| --- | ---: | ---: | ---: | ---: |
| AUTO | 236 | 214 | 15 | 15 |
| IGNORE | 111 | 155 | 8 | 7 |
| REVIEW | 14 | 0 | 2 | 2 |
| UNUNDERSTOOD | 8 | 0 | 0 | 1 |

Corpus shadow comparison: 159 both correct, **210 new correct where legacy is wrong, 0 the other way**, 0 both wrong.

## What was built

`LocalLearning` adjusts the scored decision using only what this user taught the app on this device. All
thresholds and caps live in `ParsingPolicy`.

| Signal | Source | Effect |
| --- | --- | --- |
| Merchant rule "ignore new messages" | Your existing `merchant_rules` (read-only), matched exactly as legacy matches them | IGNORE, `LEARNED_IGNORED_MERCHANT` |
| Merchant rule with a category | Same | Proposed category, `LEARNED_CATEGORY`; decision unchanged |
| Same source + message shape dismissed 2 times | API (`recordDismissal`) | IGNORE, `LEARNED_DISMISSED_SHAPE` |
| Same source + shape + direction confirmed 3 times | API (`recordConfirmation`) | REVIEW becomes AUTO, `LEARNED_CONFIRMATION`, only if every cap present is learnable (own-account transfer, inferred direction) |

How the shape memory is stored:

- **No text.** A shape is stored as a hash of the source plus `SmsPreview.shape`. The same template with another
  amount or date shares the key.
- **Bounded.** Memory is capped at 500 shapes; the least recently updated shape is dropped first.
- **Conflicts teach nothing.** Dismissing a template you also confirmed, or confirming it with two directions,
  leaves both ways unlearned.

Learning never:

- changes amount, currency or direction;
- creates a transaction from IGNORE;
- overrides a hard failure or UNUNDERSTOOD;
- lifts `UNASSIGNED_SECOND_AMOUNT`, `EXPIRING_CREDIT` or `MISSING_MERCHANT`.

## Decision distribution and changes from phase 4

**With no user data (the state of every device today): 0 changes.** The corpus (369 rows) and both held-out sets (50
messages) keep their phase 4 decisions. `LocalLearningTest` and `ParserShadowTest` assert this row by row, so the
distribution above is also the phase 5 distribution.

With simulated user feedback on real corpus messages (from `LocalLearningTest`):

| Simulated feedback | Message | Phase 4 | Phase 5 | Reason |
| --- | --- | --- | --- | --- |
| Rule: ignore "Nova Market" | p004 | AUTO | IGNORE | LEARNED_IGNORED_MERCHANT |
| Rule: Butlers → مقاهي | p001 | AUTO | AUTO + category | LEARNED_CATEGORY |
| Dismiss u01 twice | u01, u01-amount4 (same template, other amount) | AUTO | IGNORE | LEARNED_DISMISSED_SHAPE |
| Dismiss u01 twice | u02 (different template) | AUTO | AUTO | not the same shape |
| Same dismissals from another source | u01 | AUTO | AUTO | learning is per source |
| Confirm wallet top-up 3× (in) | syn-topup-ar | REVIEW | AUTO | LEARNED_CONFIRMATION (own-account cap) |
| Confirm 2× only | syn-topup-ar | REVIEW | REVIEW | below threshold |
| Confirm 3× with the wrong direction | syn-topup-ar | REVIEW | REVIEW | direction must match |
| Confirm 3×, then dismiss 2× | syn-topup-ar | REVIEW | REVIEW | conflicting feedback |
| Confirm terse alert 3× (out) | p140 | REVIEW | AUTO | LEARNED_CONFIRMATION (inferred-direction cap) |
| Confirm 5× | u08 (expiring credit), p141 (missing merchant) | REVIEW | REVIEW | cap not learnable |
| Confirm 5× | p131 (ambiguous amount), p161 (promo) | UNUNDERSTOOD / IGNORE | unchanged | never learned into a transaction |
| Confirm 5× | two unassigned amounts (probe) | REVIEW | REVIEW | safety cap not learnable |

## Tests (`tools/run-checks.ps1`, exit 0)

| Suite | Result |
| --- | --- |
| `LocalLearningTest` | PASS, 443 checks: an empty model leaves all corpus rows and both sets unchanged; every path and guard in the table above passes; memory bound; keys are hashes, no text. |
| `ParserShadowTest` | PASS: learned decision equals the scored decision without user data. An ignored-merchant rule changes only the shadow decision. The rules read and the shadow call sit in one guarded block. No legacy class or ledger storage reads the new stages. |
| `ConfidenceScorerTest` | PASS, 665 checks, including 8 fresh safety probes (unassigned amount, Arabic و second event, savings/other account, parenthesized equivalent, صالحة للاستخدام حتى, "was used at", future fee). |
| `HeldOutBlind --require` (set 1, set 2) | PASS, 25/25 each. |
| Earlier suites | PASS, unchanged. |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | PASS. Lint: 0 errors, 63 warnings (unchanged). |

## Not done or not verified

- **Dismissal and confirmation are API-only.** No screen records them from the new pipeline yet, and legacy
  exclusions store message hashes, not shapes. On a device today, only your merchant rules feed learning.
- **Learned memory lives in process memory.** It resets on restart until the encrypted store exists.
- **Not run on a device.** Thresholds (2 dismissals, 3 confirmations) are owner-reviewable defaults, not calibrated.
