# Phase 4: Confidence (shadow mode) test report, 22 October 2026

Corpus contract `v2:24b5d663`, policy `2026-10-22.1`. The legacy path still decides everything. `ConfidenceScorer` runs
only inside `ParserShadow`. Observations keep the decision and the score, never message text, and live in memory.

## What was built

`ConfidenceScorer` turns classification and extraction evidence into a 0..100 score and the final proposed decision.
All numbers are in `ParsingPolicy`:

| Evidence | Points |
| --- | ---: |
| Completed event (verb, refund/reversal, or signed amount) | 30 |
| Event inferred only from a terse card + merchant alert | 15 |
| One unambiguous positive principal | 30 |
| Explicit currency | 10 |
| Explicit direction / inferred ("likely") direction | 20 / 5 |
| Identity known (merchant, counterparty, account or card) | 10 |
| Promotional wording inside a completed event | −10 |

- **Score to decision:** 80 or more is AUTO, 50–79 is REVIEW, below 50 is UNUNDERSTOOD. Both scored-down outcomes
  carry the `LOW_CONFIDENCE` reason.
- **Hard failures:** missing, ambiguous or non-positive amount, ambiguous currency, ambiguous direction. These are
  UNUNDERSTOOD whatever the score, so nothing ambiguous is ever booked.
- **Review caps:** possible internal transfer, expiring credit, purchase without a merchant, inferred direction. These
  are never AUTO.
- **Classification IGNORE and multiple-event UNUNDERSTOOD** pass through unscored.

## Decision distribution

Corpus, 369 rows (paired-context rows judged on their standalone decision):

| Decision | Expected | Legacy | Phase 3 (rules) | Phase 4 (scored) |
| --- | ---: | ---: | ---: | ---: |
| AUTO | 236 | 214 | 236 | 236 |
| IGNORE | 111 | 155 | 111 | 111 |
| REVIEW | 14 | 0 | 14 | 14 |
| UNUNDERSTOOD | 8 | 0 | 8 | 8 |

Phase 4 corpus scores: 217 rows at 100, 32 at 80–99, 6 at 50–79, and 114 not scored (111 IGNORE, 3 multiple-event
UNUNDERSTOOD).

- **80–99:**
  - The HSBC signed transfers (u06, u06-transfer, u09, u10, u12, the phone pairs) score 90. They are AUTO, and the
    10 points are missing because a partial mask `123-456***-789` gives no identity.
  - u08 scores 80 (promotional wording, no identity) and is capped at REVIEW for expiring credit.
- **50–79:**
  - p140 scores 70 (terse alert, inferred direction) and is REVIEW.
  - p131, p138, p139, p142 and p143 score 60–70 but are UNUNDERSTOOD by hard failure.

Held-out blind set, 25 messages:

| Decision | Expected | Phase 3 (rules) | Phase 4 (scored) |
| --- | ---: | ---: | ---: |
| AUTO | 15 | 15 | 15 |
| IGNORE | 8 | 8 | 8 |
| REVIEW | 2 | 2 | 2 |

Held-out scores: 14 at 100, 3 at 80–99 (two of them capped at REVIEW), 8 not scored. Decisions equal to expected:
25 / 25.

## Decisions that changed from phase 3

**None: 0 on the corpus, 0 on the held-out set.** The phase 3 rule decision already used the same hard failures and
review reasons, and no message's score crosses a threshold the rules would have decided differently. What phase 4
adds now:

- A graded score per message (AUTO at 100 vs AUTO at 90), to be used later when ranking items for review.
- One place to tune weights and thresholds.
- A `LOW_CONFIDENCE` path for messages with partial evidence that the rules would have passed as AUTO.

That path never triggers on the corpus or the held-out set. `ConfidenceScorerTest` exercises it with unit cases: a
score of 60 gives REVIEW, 40 gives UNUNDERSTOOD, exactly 80 gives AUTO; a hard failure beats a score of 100; each
review cap blocks AUTO.

## Shadow comparison (the per-row legacy-vs-new table and its generator, ShadowComparison, were removed in 0.4.0 with the legacy opinion)

Same scoring as phase 3: decision plus principal fields. Both correct 159, **new correct and legacy wrong 210,
legacy correct and new wrong 0**, both wrong 0.

## Before phase 4: owner decisions and the blind test

- **The three corpus disagreements are resolved** as you decided (commit `fe0dab4`): p011 and p146 move to account,
  p136 has no currency. The only protected v1 cell changed is p011's card cell, recorded as an explicit owner exception.
- **Blind test** (`held-out-blind-results.md`):
  - The raw run on unchanged code gave **19/25** and was committed before any fix (`3298113`).
  - General rules then brought it to **25/25** with no corpus regression (`ce61b85`).
  - The set is now a frozen regression gate (`HeldOutBlind --require` in `run-checks.ps1`).
- **One open disagreement:** your rule for الاسترداد (and h21) makes every incoming refund an `expense_offset`, but
  `pub-cib-refund` (+2 variants) expects no effect. It is listed in `TransactionExtractorTest.KNOWN_DISAGREEMENTS`.

## Tests (`tools/run-checks.ps1`, exit 0)

| Suite | Result |
| --- | --- |
| `ConfidenceScorerTest` | PASS, 647 checks: corpus 369/369 and held-out 25/25 decisions, weights sum to 100, plus the threshold/low-confidence/hard-failure/cap/pass-through unit paths. |
| `ParserShadowTest` | PASS, 3898 checks: the shadow scored decision matches the corpus for every row; every AUTO carries a score ≥ 80; no legacy class reads the new stages. |
| `HeldOutBlind --require` | PASS, 25/25. |
| `TransactionExtractorTest` | PASS, 5034 checks (3 listed: the refund effect). |
| Earlier suites | PASS, unchanged (legacy parser 168, Telda 24, v2 gate 11970, classifier 889, ...). |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | PASS. Lint: 0 errors, 63 warnings (unchanged). |

## Limits

- **Weights are hand-set.** With no device traffic yet, they cannot be calibrated against real error rates. The
  corpus does not separate the scorer from the phase 3 rules, because no corpus or held-out message lands between them.
- **The held-out set has been used once to find gaps.** It is now a regression gate, not a fresh blind measure. A new
  unseen set (or real device messages) is needed to measure generality again.
- **Not run on a device.** Observations are in memory only. Local learning (phase 5), the encrypted shadow store and
  the hidden debug screen are not started.
