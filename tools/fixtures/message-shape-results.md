# Diagnostic shape prerequisite — 21 October 2026

This is the explicitly requested repair to the existing **support/report grouping function**
`SmsPreview.shape`, not stage-one normalization or a change to transaction acceptance.
`MainActivity.maskMessage` delegates to it for the samples report. No parser, listener, dedupe,
database, classification or capture rule was changed.

Every complete amount (including its currency), date, time, masked account/card, numeric/hex reference,
URL and remaining numeric run becomes the same literal `<slot>`. Existing runs of redaction hashes
are independent of their length. Amounts glued to EGP and multipart masks such as `123-456***-789`
are consumed as complete values; ordinary words such as CAFE are not treated as hex references.

Shapes are deliberately lossy. They are **never** transaction IDs, dedupe keys, classifications,
or a reason to accept a payment. The original evidence and financial parsing remain unchanged.

## Verification

`tools/MessageShapeTest.java` initially reproduced the broken redacted `###`/`####`/`#####` grouping.
During repair it also caught date matching that split decimal amounts and masked accounts; these
failures were fixed before the final verification.

| Check | Result |
| --- | --- |
| Banque Misr amounts 500 / 1500 / 15000 | 3 messages, 1 shape |
| Banque Misr redacted 3 / 4 / 5 hashes | 3 messages, 1 shape |
| HSBC Salary varied numbers/masks | 8 messages, 1 shape |
| HSBC Phone Banking Transfer from | 8 messages, 1 shape |
| QNB IPN transfer sent with numeric and hex references | 8 messages, 1 shape |
| Dates, Arabic/English times, URLs, mask variants and negative grouping controls | PASS |
| New diagnostic shape assertions | 55 PASS |
| Existing redaction assertions | 52 PASS |
| Existing parser assertions | 168 PASS |
| Existing standalone JVM suites and v1 fixture integrity | PASS |
| assembleDebug / assembleDebugAndroidTest / lintDebug | PASS |

Command: `.\tools\run-checks.ps1` with process-local `JAVA_HOME` pointing at the installed Android Studio JBR.
The shape suite is included in that runner, which CI already calls. No new library is used.

These counts describe invented variations of the supplied masked examples. **No phone was scanned**;
the earlier 279/249 or 134/131 device-report counts have not been remeasured. No emulator/UI test was run.
The v1 corpus is still not a semantically accepted parser contract; its revision is the next checkpoint.
