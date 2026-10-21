# Parser corpus first-delivery verification — 21 October 2026

## Scope and commit boundary

- Parent baseline: `bd0ca1d7fa7193408982fd5eccb90ac488a1c774` (`Preserve pending Telda capture and merchant badge fixes`).
- That independent commit preserved all 18 pre-existing changed/untracked files before corpus authoring.
- This delivery contains the static 240-row TSV, its field/expectation/dedupe contract, dependency-free JVM
  fixture checks, and build/check wiring. No production parser/listener/catalog/UI/SQLite Java was changed.
- No new library, push, merge, phone access, real SMS import or real-data rescan.

## Commands and results

Run in PowerShell with process-local `JAVA_HOME` set to the installed Android Studio JBR:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\run-checks.ps1
```

| Check | Result |
| --- | --- |
| Existing 21 standalone JVM suites | PASS |
| Existing parser smoke cases | 168 PASS |
| Existing Telda capture checks | 24 PASS |
| Existing merchant classification checks | 123 PASS |
| TSV inventory/field contracts/required regression fixtures | 240 rows; 229 distinct assembled text inputs; 8,333 integrity/corruption assertions PASS |
| Null vs empty, escaping, malformed header/truncated row checks | PASS |
| Gradle `:app:parserCorpusCheck` through `preBuild` | PASS |
| `assembleDebug`, `assembleDebugAndroidTest` | PASS |
| `lintDebug` | PASS; 0 errors, 63 existing warnings |
| `git diff --check` | PASS |

Corpus SHA-256 (UTF-8, LF as committed): `628cc295cbbddc7bda70fe90a0dee6c9125df27563140b8b4e0a61d0a6ed806e`.

The first baseline attempt used the system's Java 8 JRE and stopped because no compiler was present.
After selecting the installed JBR, the sandbox blocked writing Gradle's cache lock. Running the same
offline checks with the necessary local cache permission succeeded. Neither failure was hidden or
treated as a parser failure, and no dependencies were downloaded.

## Acceptance criteria captured, not yet implemented

- Title-only Telda with explicitly empty `android.text` (`p001`), starred `KASHIER*BUTLERS` (`p002`), and
  bare `Kashier Butlers` (`p001`, `p099`) all have literal expected `butlers` keys.
- Notification fields remain separate; fixtures include textLines/subText/summaryText-only messages,
  overlapping text and GROUP_SUMMARY cases.
- Different notification/inbox IDs preserve identical purchases. Replaying a stable identity is idempotent.
  Android key reuse with a different original post time does not prove duplication. Missing-ID ambiguity
  is reviewable instead of silently merged. See the [dedupe contract](README.md#dedupe-contract-identity-first-60-seconds-only-for-candidates).
- The future pipeline has 152 AUTO, 2 REVIEW, 10 UNUNDERSTOOD and 76 IGNORE expectations, with required
  reason codes and separately specified capture outcomes.
- The old parser remains the only ledger authority. No template was deleted or reclassified by this commit.

## Not proved / remaining work

- **The 240 semantic parser expectations have not been evaluated against a replacement parser.** The gate
  explicitly prints `Parser semantic evaluation: NOT RUN`; it validates fixtures, not recognition quality.
- No Android instrumentation was executed on an emulator or device in this delivery. Building the test APK
  does not demonstrate listener callbacks, UI rendering or SQL migration correctness.
- Normalization, normalized catalog indexing/fuzzy matching, runtime shadow execution/persistence and
  policy constants have not yet been implemented. There is no new-parser acceptance path to enable.
- Hidden debug UI, local ununderstood-message list, stored/displayed decision reasons, local learning,
  confidence scoring and identity-aware capture dedupe remain implementation work in their respective phases.
- No on-device processing-time or false-positive/false-negative rate claim is made.

The next implementation checkpoint is normalization plus shadow catalog comparison and its tests, using
this committed corpus as the input contract. Later stages must add semantic assertions without changing
correct expected results to accommodate failures.
