# Phase 1 — Normalization (shadow mode): test report, 21 October 2026

Corpus contract: approved v2 with the owner's three amendments plus one key-folding fix,
SHA-256 `6c8b865e47c8cb3535aa20974a2817ed12c7ab720c692e8a13e417a5d853778c` (`ParsingPolicy.CORPUS_CONTRACT = v2:6c8b865e`).

## What was built

| Class | Role |
| --- | --- |
| `ParsingPolicy` | The single place for every pipeline threshold, limit and word list (versions, shadow buffer, input bound, processor/location tokens, catalog limits, and the later-stage windows already fixed by the corpus: 60 s duplicate candidates, 10 min internal pairing, 14-day expense offset). |
| `MessageNormalizer` | One SMS body, or the six separate notification extras, to display text plus folded match text. Digits and Arabic separators to ASCII, NFKC, bidi/zero-width controls and tatweel removed, spacing/line breaks unified, duplicate and overlapping extras collapsed at word boundaries, group summaries flagged (not dropped), input bounded. Decides nothing about meaning. |
| `MerchantNormalizer` | Corpus merchant key from surface text: folded letters, punctuation removed, leading processor descriptor removed only when a merchant follows, trailing Latin country/city tokens removed only from the end. |
| `MerchantCatalogIndex` | `MerchantClassifier`'s catalog indexed by those keys: exact, then the longest branch-capable alias plus a suffix, then conservative fuzzy (one OSA edit, key ≥ 6 chars, same first letter, no digits, exactly one candidate merchant). |
| `ParserShadow` | Runs after every legacy capture in `CaptureTransaction`; bounded in-memory observations with no message text; failures become `STAGE_FAILED` observations. |

**The legacy path still decides everything.** No ledger write, schema change, `CaptureResult` change, persistence,
logging of message text, upload or UI was added. Each observation says classification, extraction, confidence and
local learning are not implemented. The notification listener additionally passes the separate extras for the
shadow stage; legacy capture still receives the identical assembled body.

## Test results (`tools/run-checks.ps1`, exit 0)

| Suite | Result |
| --- | --- |
| `MessageNormalizerTest` | PASS, 2746 checks. All 369 corpus rows normalize with no controls, non-ASCII digits or irregular spacing left; idempotent; 100 digit/spacing variants equal their base; notification cases p001/p006/p010/p012/p013–p016/p018/p023, InstaPay extras. |
| `MerchantNormalizerTest` | PASS, 348 checks. 46 corpus merchant surfaces give their oracle keys; processor/location edges; catalog exact, branch and fuzzy lookups and rejections; ambiguous fuzzy refused; exact-path parity with the legacy classifier for all 89 aliases; only intended fuzzy corpus hit. |
| `ParserShadowTest` | PASS, 2788 checks. All corpus rows observed; text-free observations; buffer bound 50; failure isolation; legacy-merchant comparison; source scan that no legacy class or ledger storage reads shadow output. |
| `ParserCorpusV2Test --require-ready` | PASS, 11963 assertions, zero blockers. |
| Existing JVM suites | PASS, unchanged (168 parser, 24 Telda, 31 InstaPay, 123 merchant classification, 211 source structure, v1 corpus 8331, and the rest). |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | PASS. Lint 0 errors, 63 warnings (same as before), none in new or changed files. |

## Findings during the phase

- **Corpus defect fixed (commit `8273914`).** Four synthetic merchant keys (16 rows) were not folded per the corpus's
  own v1 rules (`p107`/`p112`/`p113`). Only those keys changed; the gate still passes. No expectation was changed
  to suit the implementation.
- **One intended fuzzy hit.** `Butler's & Co.` (p117, key `butler s co`) is one edit from the alias `Butlers & Co` and
  resolves to Butlers. Its corpus key remains surface text; identity is a separate proposal.
- **Arabic place names are not trailing descriptors.** `مصر` / `القاهرة` are usually part of a name (`بنك مصر`),
  so only Latin terminal tokens (EG, EGYPT, CAIRO, ...) are removed.
- **Invisible characters in source.** Escapes had been written as literal BOM/bidi characters; lint caught it and
  they are now `\u` escapes (commit `8332ab7`), behavior unchanged.

## Not done or not verified

- No emulator/instrumentation run and no live SMS/notification delivery; a successful APK build is not evidence that
  the listener path ran on a device.
- Observations live in memory for the current process only. The encrypted local store and the hidden debug /
  "رسائل لم أفهمها" screen remain the next separate phase.
- Classification, extraction, confidence scoring and local learning are not started. The reversal
  `expense_offset` matching rule is a policy constant and corpus oracle only; no ledger logic implements it yet.
