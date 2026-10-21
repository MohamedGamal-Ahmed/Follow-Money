# Shadow storage (step 2 of the device phase)

Schema v26 adds three tables (`ShadowStore`), created on install and on upgrade from any older schema. None of them is tracked by `LedgerRevision`, so a shadow write never counts as a ledger change and recording behaviour is unchanged. Legacy remains the only decision-maker.

| Table | One row per | Plain columns | Sealed by EvidenceVault (`encrypted_body`) |
| --- | --- | --- | --- |
| `shadow_observations` | observed message (live capture now; the rescan of step 5 uses `origin='rescan'`) | time, channel, source label, message hash, legacy `transaction_id`, legacy reason and decision (`RECORDED` / `RECORDED_REVIEW` / `NOT_RECORDED`), status, type, stage/scored/final decision, score, movement kind, reasons, policy | JSON of the new pipeline's field values (amount, currency, direction, merchant, party, balance, fee, card, account, time, accounting effect, learned category) and the legacy values (amount, currency, direction, merchant) |
| `ununderstood_messages` ("رسائل لم أفهمها") | distinct message (hash) the pipeline decided UNUNDERSTOOD | first/last seen, count, channel, source, reasons, policy | the normalized message text |
| `learning_rules` | learned shape (and, after the flip, user corrections) | shape hash, source label, dismissed / confirmed counts, direction, conflicting, merchant, category | nothing: no message text is stored |

- Bounds: `SHADOW_STORE_MAX_ROWS` = 5000 observations (oldest removed first), `UNUNDERSTOOD_MAX_ROWS` = 500 messages (least recently seen removed first), `LEARN_MAX_SHAPES` = 500 shapes.
- Backup and restore: both sealed tables are in `EvidenceVault.ENCRYPTED_TABLES`, so a backup opens them on this device and a restore re-seals them with the new device's key, the same rule as capture evidence.
- The local-learning model loads its stored shapes once per process before the first observation (`ShadowStore.loadLearningOnce`); `saveLearning` writes them back and will be called by the correction UI (step 8). Nothing records dismissals or confirmations before then.
- Failure isolation: the store call sits in the same guarded block as the shadow observation in `CaptureTransaction`; any exception is swallowed and the capture result is built before it runs.
- Checks (`ParserShadowTest`, `LocalLearningTest`): the observation is used only by the store; both sealed columns go through `EvidenceVault.encrypt`; no shadow table is in `LedgerRevision`; the backup re-seals both; the tables exist on install and upgrade; learning export/restore keeps decisions and eviction order with hashes only.
- Not verified on a device yet: the Keystore round trip and the upgrade from v25 run only on Android (step 6).

## Listener and dedupe (step 3)

- **Six fields:** the listener passes the six separate extras (`shadowInput`). A notification legacy never captures (a GROUP_SUMMARY, or no title/text, such as text only in `textLines`) is now observed in shadow with `legacy_reason` `LEGACY_GROUP_SUMMARY` or `LEGACY_NO_BODY`. Nothing is recorded for it, and the legacy capture call is unchanged.
- **Identity first:** each delivery carries its identity (`MessageNormalizer.Raw.identity`): `notification:` plus the Android key and the original post time, or `sms:` plus the inbox ID where one exists (live SMS broadcasts have none). Identity never reaches the text or the parser.
- **Dedupe** (`ShadowDedupe`, the corpus contract in [README.md](README.md#dedupe-contract-identity-first-60-seconds-only-for-candidates)):
  - The same identity with the same normalized content is `REPLAY`.
  - The same identity with other content is `REVIEW_DUPLICATE/IDENTITY_CONFLICT`.
  - Without identity, the same source and normalized content within 60 s of any earlier identity-less delivery is `REVIEW_DUPLICATE/IDENTITY_UNAVAILABLE`, never dropped.
  - Anything else is `NEW`. Financial fields are never identity.
  - The verdict, the earlier row it points to, the identity and the content hash are stored on each `shadow_observations` row.
- **Gate** (`ShadowDedupeTest`): the ten `dedupe_identity` corpus rows (p231-p240), replayed in order, match `expected_capture`, `expected_capture_reason` and `duplicate_of`. Probes cover the window edge (60 s in, 60 s + 1 ms out), source and content isolation, an identified delivery never being treated as a text candidate, a replay proven outside the window, and an identity conflict.

## Debug screen (step 4)

- **Opening it (no adb):** tap the version line `Follow Money · …` in Settings → المساعدة وعن التطبيق seven times. A new **إعدادات متقدمة** section then appears in Settings → البيانات والخصوصية, with the row **تشخيص المحلل (وضع الظل)**. Seven more taps hide it again. Debug builds always show it.
- **Screen** (`ParserDebugActivity`, not exported, behind the app lock): the policy and contract versions, then a summary of the latest 300 live observations (agree / disagree by `ShadowDiff` outcome) and the number of learned rules. Three views:
  - **الكل**: one card per message or notification, with the legacy decision and reason, its amount, direction and merchant; the new decision, score, type, movement kind, fields and reasons; the pre-learning decision when it differs; and the dedupe verdict.
  - **الاختلافات**: disagreements only.
  - **لم أفهمها**: the ununderstood messages, with their text opened on this device.
- **Agreement** (`ShadowDiff`): legacy *recorded* means it wrote a ledger row, and the new pipeline *records* on AUTO or REVIEW. Two records agree only on the same amount and direction; a legacy own-account transfer fits either direction. The outcomes are BOTH_SKIP, BOTH_RECORD_SAME, BOTH_RECORD_DIFFERENT (AMOUNT / DIRECTION), LEGACY_ONLY and NEW_ONLY.
- **Read-only and local:** a check (`ParserShadowTest`) rejects any database write, network class or share intent in the screen, and confirms it is not exported.

## Shadow re-scan (step 5)

- **Where:** debug screen → **إعادة الفحص (بدون تسجيل)**, with a period of 30 days, 90 days, a year, or all messages.
- **Path:** the same inbox query and legacy preview as «اختيار فترة الرسائل القديمة» (`Telephony.Sms.Inbox`, `SmsPreview.parse`).
  - **Legacy side:** what that import would capture (the preview is a transaction), with amount, currency and direction. Its current ledger status goes in the reason, e.g. `PREVIEW_EXPENSE/LEDGER_RECORDED`.
  - **New side:** the full shadow pipeline. Each SMS carries its inbox ID as identity.
- **No operation is recorded.** A check (`ParserShadowTest`) rejects any capture, import or ledger-table write in `ShadowRescan`. Rows go to `shadow_observations` with `origin='rescan'`, and each run replaces the previous one.
  - A message both sides skip is only counted, unless the new pipeline did not understand it.
  - At most `RESCAN_MAX_ROWS` = 10000 rows are stored; later ones are counted and the report says so.
- **Live capture is never delayed:** reading and parsing run on the re-scan thread. Each batch of `RESCAN_BATCH` = 200 rows is written on the single capture worker (`CaptureQueue`) in a short transaction, so a new SMS or notification is captured between batches.
- **Report on screen** (`ShadowReport`): messages scanned, compared, agree and disagree, counts by `ShadowDiff` outcome, agreement by source and by type (movement kind, else message type), and one card per difference. Each card shows the source, date, legacy decision, reason, amount and direction, then the new decision, amount, direction, score, type and reasons.
- **Export (optional):** **حفظ التقرير في ملف** opens the system file picker (`ACTION_CREATE_DOCUMENT`) and writes the same report as Markdown to the file the user picks. It contains no message text, merchant or counterparty; a check confirms the report rows have no such field. Nothing is uploaded or shared.

## On-device verification and data deletion

- **Delete all local data** (البيانات والخصوصية → حذف كل البيانات المحلية) now also removes `shadow_observations`, `ununderstood_messages`, `learning_rules`, the re-scan summary and the in-memory shadow copies. A check enforces it.
- `tools/run-shadow-store.ps1` (disposable emulator, isolated `.shadowqa` app) runs `ShadowStoreTest`: **32 checks passed** on Medium_Phone_API_36.1, 21 October 2026. They cover:
  - the v25 → v26 upgrade;
  - a live SMS capture whose shadow row is linked to the ledger row, with the ledger revision unchanged by the shadow write;
  - values and text sealed by the Keystore and opened again;
  - an ununderstood message kept once with a count;
  - a GROUP_SUMMARY observed and never captured, then replayed (REPLAY/DELIVERY_REPLAY);
  - learning rules stored as hashes;
  - a backup snapshot opening every shadow body and a restore re-sealing it, with the live database untouched;
  - a re-scan of real inbox SMS (sent through the emulator console) comparing the payment on both sides, with no ledger change, and a second re-scan replacing the first;
  - delete-all emptying everything.
