# Bank-message parser corpus: first delivery

`parser-corpus.tsv` is the authoritative, versioned set of **240 synthetic input/expected-result pairs**.
It was authored before implementing the replacement pipeline. Expectations are not produced by the legacy
parser or by a new parser. No personal inbox exports, real account identifiers or real notification dumps
are included. The Telda title-only layout reproduces the reported input shape using the synthetic payment
fixture already in this repository.

## Encoding and input contract

- UTF-8 without a BOM; a literal tab separates cells. No CSV quoting; quotes inside merchant names are data.
- One physical line per case, plus the header. `\N` means absent/null; an empty cell means an explicitly empty
  string. This distinction is essential for `android.text` in `p001` and `p002`.
- Escape sequences: `\\` (literal backslash), `\n`, `\r`, `\t`, `\uXXXX`. Bidi marks and nonstandard spaces are
  escaped to make their presence reviewable. Arabic and Persian numerals remain literal Unicode.
- `channel=sms`: `body` contains the original SMS. The six Android fields and `notification_key` are null.
- `channel=notification`: `body` is null. Reconstruct a real notification with the **six separate extras**;
  do not substitute an already assembled body. `android.textLines` is a newline-separated array of lines.
  A missing field remains missing; an empty field remains empty. `notification_flags=512` is GROUP_SUMMARY.
- `source_message_id` represents an available inbox/provider message ID, not a hash of financial fields.
  An empty ID deliberately represents unavailable identity. Notification fixtures use an Android key and
  original post time (`received_at_ms`), not a new timestamp each time a notification is replayed.
- `language` is a broad coverage tag; Arabic messages may contain English merchant names. No source name
  is permission to book a message or an instruction to choose a bank-specific grammar.

## Expected results

| Column | Meaning |
| --- | --- |
| `expected_type` | transaction / promo / otp / balance_inquiry / reminder / other |
| `expected_amount_minor`, `expected_currency` | Transaction principal and explicit ISO currency; all six currencies here have two fractional digits |
| `expected_direction` | `in` / `out`; this alone does not turn a withdrawal into consumption or a refund into salary |
| `expected_merchant_key` | Normalized **surface text**, not a logo ID or a transliteration; Arabic/English aliases may have different keys and resolve to one catalog entry |
| `expected_balance_minor`, `expected_balance_currency` | Independently extracted explicit balance; may be negative or use a different currency from the transaction |
| `expected_card_last4` | Four digits as a string, including leading zeros; fixtures include masked cards/accounts |
| `expected_datetime`, `expected_time_precision` | Literal bank-supplied local date/time; precision is unknown / date / minute. Unknown stays null; no receipt-time/noon fabrication |
| `expected_decision` | Proposed new-parser decision, independent of what the legacy parser currently does |
| `required_reasons` | Minimum stable reason-code set that the new decision must include; extra truthful diagnostic reasons are allowed |
| `expected_capture` | NEW / NONE / REPLAY / REVIEW_DUPLICATE; a **separate integration expectation**, not a pure parser outcome |
| `expected_capture_reason`, `duplicate_of` | Reason for identity handling and earlier fixture ID being replayed or potentially repeated |

For nontransactions, the classification stage short-circuits **transaction** extraction: the transaction
fields are null, even if the advertisement/OTP/balance inquiry contains monetary values. A separate legacy
balance-evidence path is not being removed or changed by this fixture delivery. Explicitly ambiguous
transactions can retain independently known fields while their unresolved fields stay null. No ambiguous
principal, currency or direction is invented to make a fixture automatically bookable.

`AUTO` means the **future** complete pipeline should be able to accept it. `REVIEW` retains a valid principal
and direction while requiring user review (for example, no merchant or a separate fee). `UNUNDERSTOOD`
creates no transaction and retains local evidence for manual recovery. `IGNORE` creates no transaction.
These are acceptance criteria, not claims of current support. In shadow mode **none of these expectations
overrides the legacy parser or writes a new-parser transaction**.

The principal/fee case `p134` exposes the principal but requires review; it does not silently discard or
invent a fee. A summary with two separate transactions (`p014`) must not synthesize one ledger entry;
children remain independently eligible. Overlapping short/expanded text (`p023`) describes one event.
`p137`/`p138` deliberately leave bare `$`/`ريال` unresolved. Single-dot `1.250` in `p131` is deliberately
ambiguous without locale evidence; there is no rounding or first-number fallback. Repeated thousands
groups, two-place decimal commas, decimal points and Arabic separators have explicit positive cases.

Merchant normalization removes a **leading processor descriptor with a following merchant** and
recognizable trailing country/branch descriptors. It must preserve standalone `PAYPAL`, `Studio 54` and
the meaningful initial `CAIRO` in `CAIRO BOOKS`. Bare `Kashier Butlers` and `KASHIER*BUTLERS` both map to
`butlers`. This is not permission to delete every number or location token from a merchant name.

## Coverage inventory

| IDs | Group | Count |
| --- | --- | ---: |
| p001-p024 | Separate Android extras, empty/null, overlap, summaries, title-only Telda | 24 |
| p025-p096 | Twelve requested sources, each with six different field orders/currencies | 72 |
| p097-p120 | Digits, diacritics, bidi, whitespace, currency spellings, processor/branch cleanup | 24 |
| p121-p144 | Amount/balance/fee roles, numeric formats, ambiguity, negative balance | 24 |
| p145-p160 | Incoming/outgoing, ATM, refunds, salary, bank-time precision, unknown sources | 16 |
| p161-p184 | Arabic/English/mixed advertisements and misleading financial vocabulary | 24 |
| p185-p200 | OTP and verification, including quoted completed-payment wording | 16 |
| p201-p212 | Balance inquiries, including masked accounts, dates and references | 12 |
| p213-p222 | Reminders, pending requests and future debits | 10 |
| p223-p230 | Failed/negated operations and nonfinancial messages | 8 |
| p231-p240 | Distinct delivery identities, real identical payments, replay and missing IDs | 10 |
| **Total** | **229 distinct assembled text inputs; 6 currencies** | **240** |

Type totals: 164 transaction, 25 promo, 17 otp, 13 balance_inquiry, 11 reminder, 10 other.
Proposed parser decisions: 152 AUTO, 2 REVIEW, 10 UNUNDERSTOOD, 76 IGNORE.

## Dedupe contract: identity first, 60 seconds only for candidates

The short window cannot prove two identical messages describe the same payment.

1. Different available inbox IDs or different Android keys mean distinct deliveries: identical purchases
   with no balance line in `p231`/`p232` and `p235`/`p236` **both survive** inside the window.
2. Replaying the same SMS ID, or the same Android key **plus original post time and exact normalized
   content**, is idempotent (`p233`, `p237`). Preserve original identity on active-notification replay.
3. Android keys may be reused. The same key with a new original post time (`p234`) must not by itself
   suppress another identical purchase. An updated text/ref/balance is not automatically the same event;
   conflicting identity evidence needs review rather than a guessed merge.
4. Without a usable identity or transaction reference, the first message is eligible normally (`p238`).
   A second identical source/text inside 60 seconds (`p239`) retains its own evidence and becomes a
   **possible-duplicate review item**. It is neither silently dropped/merged nor automatically booked
   a second time. `duplicate_of` here records a candidate, not a proven duplicate.
5. Outside that short candidate window of **every** prior candidate, text alone is insufficient to suppress
   another payment (`p240`, 76 seconds after `p238` and 61 seconds after `p239`).
   Stable replay identity can still prove a replay outside the window; the window does not expire identity.

`expected_decision=AUTO` with `expected_capture=REVIEW_DUPLICATE` is intentional: the text parser is confident,
but the capture layer lacks delivery identity. Financial fields (amount/merchant/day) are never event IDs.
Cross-channel reconciliation of bank/app copies requires its own evidence; it must not undo this contract.

## Shadow mode and later phase boundaries

This delivery changes no application Java, SQLite schema, listener behavior or existing parser templates.
The next normalization stage must run in shadow mode; the legacy path remains the only ledger authority.
All configurable thresholds, weights, fuzzy limits, the short window and diagnostics limits must live in
one documented `ParsingPolicy.java` when those stages are implemented; do not scatter constants in stages.

Later capture diagnostics must retain original encrypted evidence, legacy result, proposed result,
decision reasons, phase/parser/policy versions and disagreements. Stage-one-only observations must say
that classification/extraction/scoring are not implemented, rather than invent AUTO or rejection reasons.
Diagnostic failures must not change legacy acceptance, `CaptureResult` semantics or financial totals.
There must be no external logging/upload of these records.

The user explicitly requires local access without adb: a hidden debug screen and reason display in
"رسائل لم أفهمها". That UI/storage integration is **still pending**; it is not represented as implemented
by this fixture commit. No old template may be removed until the replacement passes every existing test
and every corpus semantic expectation. Keep phase commits and verification reports separate.

## Verification commands and limits

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline :app:parserCorpusCheck
.\tools\run-checks.ps1
```

The corpus uses the repository's existing dependency-free JVM test style. `ParserCorpusFixture.java`
decodes TSV without a new library; `ParserCorpusTest.java` validates the complete inventory, mandatory
regressions, null/empty distinctions, field contracts, reason/capture consistency and corrupted input.
The Gradle gate is attached to `preBuild` and `check`, and `run-checks.ps1` also executes it in the JVM suite.

**A passing fixture gate is not a passing parser corpus.** The replacement pipeline is not implemented
in this delivery. Subsequent stage tests must evaluate the authored expectations; changing expected
results merely to match a faulty implementation is not an acceptable fix.
