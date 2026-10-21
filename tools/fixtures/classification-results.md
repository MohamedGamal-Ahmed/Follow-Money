# Phase 2: Classification (shadow mode) test report, 21 October 2026

Corpus contract `v2:6c8b865e`, policy `2026-10-21.2`. The legacy path still decides everything. The classifier runs
only inside `ParserShadow`, after the legacy capture is final, and keeps its result in memory.

## What was built

| Class | Role |
| --- | --- |
| `AmountScanner` | Finds numbers that carry a currency: Arabic and English units, glued (`500EGP`, `المتاح800.00EGP`) or spaced, with a sign (`-120`, HSBC `+`/`-`), and leading decimals. Masked digits (`***-789`) cannot take a unit, and `EGP-1234` is an identifier, not an amount. A single `1.250` and a bare `ريال` stay ambiguous; a bare `$` is USD (owner decision). |
| `MessageClassifier` | Type: transaction, promo, otp, verification, balance_inquiry, reminder or other. Checks run in this order: empty → verification/security request → explicit balance inquiry → promotion without a completed event → future/request wording without a completed event → failure (unless money was returned) → transaction (completed event, refund/reversal, signed amount, or a terse card+merchant alert) → balance → other. Events are counted per clause; two or more give `UNUNDERSTOOD / MULTIPLE_EVENTS`. |
| `ParsingPolicy` | Currency units and all classification vocabulary, matched on normalized folded text. Same place as every other constant. |

The rules are generic wording rules. There are no bank templates, and the sender never decides the type.

## Shadow comparison (full per-row table: [shadow-comparison-classification.md](shadow-comparison-classification.md))

All 369 rows, legacy replayed in `addAutomaticCapture` order on the body the listener builds.

| Outcome | Rows |
| --- | ---: |
| Both correct | 311 |
| **New correct, legacy wrong** | **58** |
| **Legacy correct, new wrong** | **0** |
| Both wrong | 0 |
| New type exactly as expected | 369 / 369 |

Where legacy is wrong:

| Expected; legacy outcome | Rows |
| --- | ---: |
| AUTO transaction; legacy found no ledger candidate | 34 |
| REVIEW transaction; legacy found no ledger candidate | 5 |
| AUTO transaction; legacy treated it as unsuccessful (refunds/reversals) | 4 |
| AUTO transaction; listener body missing (extras outside title/text/bigText, or a group summary) | 4 |
| otp booked as a transaction | 4 |
| promo booked as a transaction | 2 |
| reminder booked as a transaction | 2 |
| UNUNDERSTOOD (ambiguous) booked by legacy | 3 |

Scoring rules: expected AUTO/REVIEW must proceed toward a transaction. Expected IGNORE must stop. Expected UNUNDERSTOOD
must not be booked by legacy and must not be discarded by the new stage. At this phase the new stage sends a
single-event ambiguous row on to extraction, and phase 3 decides it. Legacy cannot produce REVIEW or UNUNDERSTOOD, so
the replay records only "inserted" or "nothing".

**No disagreements with the corpus.** `MessageClassifierTest.KNOWN_DISAGREEMENTS` is empty. A future disagreement
must be listed there and reported. It must never be "fixed" by editing the corpus.

## Tests (`tools/run-checks.ps1`, exit 0)

| Suite | Result |
| --- | --- |
| `MessageClassifierTest` | PASS, 889 checks. Every corpus row matches on type, stage decision and the required reasons for non-transactions. 18 held-out messages that are not in the corpus also pass (Arabic/English purchases, credit, cash, refund, two events, OTP, promos, bill due, scheduled transfer, failures, balance, parcel, a support ID that looks like money). Scanner edge cases pass too. |
| `ParserShadowTest` | PASS, 3158 checks. The shadow type matches the corpus for every row. Observations contain no message text. A source scan confirms no legacy class or ledger storage reads the classifier. |
| `ShadowComparison classification` | Regenerates the comparison table above. |
| Earlier suites | PASS, unchanged (normalizer 2746, merchant 348, v2 gate 11963, legacy parser 168, Telda 24, InstaPay 31, ...). |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | PASS. Lint: 0 errors, 63 warnings (unchanged). |

## Limits

- The 369/369 corpus result comes from rules written with this corpus in view. The held-out set is small (18). Real
  device traffic will surface wording the corpus lacks. Unseen wording falls to `other`/IGNORE in shadow mode only,
  and the legacy path still books as before.
- Java regex `\b` is ASCII on the JVM and Unicode on Android (ICU). The rules use `\b` only around English words, so
  the difference should not matter. It has not been verified on a device.
- Not run on an emulator or device. Observations are in memory only.
