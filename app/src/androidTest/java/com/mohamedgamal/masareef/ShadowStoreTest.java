package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.util.List;

/**
 * Shadow storage on a real Android runtime (isolated emulator app only): upgrade v25 → v26 and v26 → v27 (0.4.5: kept messages get a source key and can come back after an update), live capture stores its
 * shadow row beside the ledger row without bumping the ledger revision, Keystore sealing, ununderstood text, dedupe
 * replay, the new pipeline deciding every message with legacy only on its failure and legacy guards noted, learning persistence, backup open/re-seal, and "delete all data".
 */
final class ShadowStoreTest {
    private final Instrumentation runner;
    private int checks;
    ShadowStoreTest(Instrumentation runner) { this.runner = runner; }
    private void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }



    private static long count(SQLiteDatabase sql, String query) {
        try (Cursor c = sql.rawQuery(query, null)) { return c.moveToFirst() ? c.getLong(0) : -1; }
    }

    private static String first(SQLiteDatabase sql, String query) {
        try (Cursor c = sql.rawQuery(query, null)) { return c.moveToFirst() ? c.getString(0) : null; }
    }

    /** One stored observation with its sealed values opened (the app itself no longer lists them). */
    private static final class Row {
        long id; int score; boolean unreadable;
        String legacyDecision, decision, reasons, dedupe, dedupeReason, source;
        final java.util.Map<String, String> values = new java.util.HashMap<>(), legacy = new java.util.HashMap<>();
    }

    private static List<Row> rows(SQLiteDatabase sql, String origin, int limit) {
        List<Row> out = new java.util.ArrayList<>();
        try (Cursor c = sql.rawQuery("SELECT id, legacy_decision, decision, reasons, dedupe, dedupe_reason, source, score, encrypted_body FROM shadow_observations " +
                "WHERE origin=? ORDER BY received_at DESC, id DESC LIMIT ?", new String[]{origin, Integer.toString(limit)})) {
            while (c.moveToNext()) {
                Row r = new Row();
                r.id = c.getLong(0); r.legacyDecision = c.getString(1); r.decision = c.getString(2); r.reasons = c.getString(3);
                r.dedupe = c.getString(4); r.dedupeReason = c.getString(5); r.source = c.getString(6); r.score = c.getInt(7);
                String body = EvidenceVault.decrypt(c.getString(8));
                r.unreadable = body == null;
                if (body != null) try {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    for (java.util.Iterator<String> k = json.keys(); k.hasNext(); ) { String key = k.next(); if (!key.equals("legacy")) r.values.put(key, json.getString(key)); }
                    org.json.JSONObject legacy = json.optJSONObject("legacy");
                    if (legacy != null) for (java.util.Iterator<String> k = legacy.keys(); k.hasNext(); ) { String key = k.next(); r.legacy.put(key, legacy.getString(key)); }
                } catch (org.json.JSONException malformed) { r.unreadable = true; }
                out.add(r);
            }
        }
        return out;
    }

    void run() {
        Bundle result = new Bundle(); int status = -1; ExpenseDb db = null; boolean owned = false;
        Context target = runner.getTargetContext();
        try {
            if (!target.getPackageName().endsWith(".shadowqa") || !"ranchu".equals(android.os.Build.HARDWARE)) throw new SecurityException("Isolated emulator required");
            db = new ExpenseDb(target); check(db.transactionCount() == 0, "Existing ledger retained; empty fixture required"); owned = true;
            SQLiteDatabase sql = db.getWritableDatabase();

            // 1. Upgrade from schema 25 creates the shadow tables.
            for (String table : new String[]{"shadow_observations", "ununderstood_messages", "learning_rules"}) sql.execSQL("DROP TABLE IF EXISTS " + table);
            sql.setVersion(25); db.close();
            db = new ExpenseDb(target); sql = db.getWritableDatabase();
            check(sql.getVersion() == ExpenseDb.SCHEMA_VERSION && ExpenseDb.SCHEMA_VERSION == 28, "Schema upgraded to 28");
            check(count(sql, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('shadow_observations','ununderstood_messages','learning_rules')") == 3,
                    "Upgrade from 25 creates the three shadow tables");

            // 2. Live capture: the new pipeline decides; its observation sits beside the ledger row; the ledger revision ignores it.
            long now = System.currentTimeMillis();
            CaptureResult paid = db.captureSms("QABANK", "Paid EGP 45 at \"QA Market\". Card ****1234.", now);
            check(paid.transaction != null && paid.transaction.amountMinor == 4500, "The new pipeline recorded the payment: " + paid.reason);
            check(db.changes() == paid.revisionAfter, "Shadow write does not change the ledger revision");
            List<Row> live = rows(sql, "live", 10);
            check(live.size() == 1, "One live shadow row");
            Row row = live.get(0);
            check("LEGACY_OFF".equals(row.legacyDecision) && row.legacy.isEmpty(), "No legacy opinion computed: " + row.legacyDecision);
            check("AUTO".equals(row.decision) && "4500".equals(row.values.get("amount_minor")) && row.score >= 80 && !row.unreadable, "New decision, amount and score stored: " + row.decision);
            check(count(sql, "SELECT transaction_id FROM shadow_observations WHERE id=" + row.id) == paid.transactionId, "Linked to the ledger row");
            String sealed = first(sql, "SELECT encrypted_body FROM shadow_observations WHERE id=" + row.id);
            check(sealed != null && !sealed.contains("4500") && !sealed.contains("QA Market"), "Values sealed by the Keystore");

            // 3. Not understood, from the user's bank (QABANK has a source since section 2): the text is kept, sealed, once
            // per message; the count grows with a new delivery only, never with the same delivery read again (owner
            // acceptance: «وصلت 11 مرات» for a message of one day).
            db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", now + 1000);
            db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", now + 2000);
            check(ShadowStore.ununderstood(sql, 10).get(0).count == 1, "The same message read again within a minute is not another delivery");
            db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", now + 121_000L);
            List<ShadowStore.Ununderstood> unknown = ShadowStore.ununderstood(sql, 10);
            check(unknown.size() == 1 && unknown.get(0).count == 2 && unknown.get(0).text.contains("Cafe Nine"), "Ununderstood message kept once, opened on device");
            db.captureSms("we-Landline", "Paid EUR 1.250 at Cafe Nine", now + 3000);
            check(ShadowStore.ununderstood(sql, 10).size() == 1, "A sender the user has no source with is never kept");
            check(!first(sql, "SELECT encrypted_body FROM ununderstood_messages").contains("Cafe"), "Ununderstood text sealed");

            // 4. The same notification delivered again (same Android key and post time) is one operation and a replay.
            // (A group summary never reaches capture: the listener finds no body in it.)
            long ledgerRows = db.transactionCount();
            MessageNormalizer.Raw delivery = MessageNormalizer.Raw.notification("com.qa.pay", 0, "QA Pay",
                    "Paid EGP 85 at \"Nova Market\".", null, null, null, null).withIdentity(MessageNormalizer.Raw.notificationIdentity("0|com.qa.pay|1|t|1", 5000L));
            db.captureNotification("com.qa.pay", "QA Pay", "QA Pay\nPaid EGP 85 at \"Nova Market\".", 5000L, delivery);
            db.captureNotification("com.qa.pay", "QA Pay", "QA Pay\nPaid EGP 85 at \"Nova Market\".", 5000L, delivery);
            check(db.transactionCount() == ledgerRows + 1, "One operation for two deliveries: " + (db.transactionCount() - ledgerRows));
            live = rows(sql, "live", 10);
            Row replay = null, firstDelivery = null;
            for (Row s : live) if ("QA Pay".equals(s.source)) { if (replay == null) replay = s; else firstDelivery = s; }
            check(firstDelivery != null && "NEW".equals(firstDelivery.dedupe), "First delivery observed");
            check("REPLAY".equals(replay.dedupe) && "DELIVERY_REPLAY".equals(replay.dedupeReason), "Same delivery replayed: " + replay.dedupe);

            // 5. Learning rules persist (hashes and counts only).
            ParserShadow.learning().recordDismissal("QABANK", "Paid EGP 45 at \"QA Market\".");
            ShadowStore.saveLearning(sql, ParserShadow.learning(), now);
            check(count(sql, "SELECT COUNT(*) FROM learning_rules") >= 1, "Learning rule stored");
            check(!first(sql, "SELECT rule_key FROM learning_rules").contains("QA"), "Rule key is a hash");

            // 6. Backup opens shadow ciphertext in the snapshot only; restore re-seals it.
            java.io.File snapshot = new java.io.File(target.getCacheDir(), "shadow-qa-snapshot.db");
            db.exportSnapshot(snapshot);
            try (SQLiteDatabase copy = SQLiteDatabase.openDatabase(snapshot.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                check(EvidenceVault.decryptAll(copy) == 0, "Every shadow body opens on this device");
                check(first(copy, "SELECT encrypted_body FROM shadow_observations WHERE origin='live' ORDER BY id LIMIT 1").startsWith("{"), "Snapshot holds opened values");
                check(first(copy, "SELECT encrypted_body FROM ununderstood_messages").contains("Cafe Nine"), "Snapshot holds opened text");
                check(EvidenceVault.encryptAll(copy) > 0, "Restore re-seals");
                String resealed = first(copy, "SELECT encrypted_body FROM shadow_observations WHERE origin='live' ORDER BY id LIMIT 1");
                check(!resealed.startsWith("{") && EvidenceVault.decrypt(resealed).startsWith("{"), "Re-sealed body opens again");
            } finally { snapshot.delete(); }
            check(first(sql, "SELECT encrypted_body FROM shadow_observations WHERE origin='live' ORDER BY id LIMIT 1").equals(sealed), "Live database untouched by backup");

            // 7b. A fee returned minutes after the same charge is an expense offset linked to that charge.
            long feeAt = now + 3_600_000L;
            db.captureSms("QABANK", "تم خصم 5.00 جنيه رسوم خدمة من حسابك", feeAt);
            db.captureSms("QABANK", "تم اضافة 5.00 جنيه الى حسابك", feeAt + 5 * 60_000L);
            List<Row> feeRows = rows(sql, "live", 2);
            check("expense_offset".equals(feeRows.get(0).values.get("accounting_effect")) && feeRows.get(0).reasons.contains("LINKED_EXPENSE_OFFSET") &&
                    String.valueOf(feeRows.get(1).id).equals(feeRows.get(0).values.get("offset_of")), "Fee return linked to its charge: " + feeRows.get(0).reasons);

            // 10. The new pipeline decides every message, whenever it arrived.
            long t = now + 2 * 3_600_000L;
            CaptureResult early = db.captureSms("QABANK", "Paid EGP 33.00 at \"OLD SHOP\". Card ****1234.", now - 30L * 86_400_000L);
            check(early.transaction != null && early.transaction.amountMinor == 3300 &&
                    "AUTO".equals(first(sql, "SELECT decision FROM shadow_observations WHERE transaction_id=" + early.transaction.id)), "An older message is decided by the new pipeline too");
            CaptureResult glued = db.captureSms("BankMisr", "شكرًا لاستخدامك بطاقة بنك مصر ***4636، تم الآن خصم EGP 149.98عند KASHIER*BUTLERS & CO C يوم 20/10/2026 ، الرصيد المتاح EGP 2139.44", t);
            check(glued.transaction != null && glued.transaction.amountMinor == 14998 && "expense".equals(glued.transaction.kind) && !glued.transaction.needsReview,
                    "New pipeline records the purchase with its piasters: " + (glued.transaction == null ? glued.reason : glued.transaction.amountMinor));
            check(glued.transaction.merchant.toUpperCase(java.util.Locale.ROOT).contains("BUTLERS"), "Merchant from the new pipeline: " + glued.transaction.merchant);
            String opinion = first(sql, "SELECT legacy_decision FROM shadow_observations WHERE transaction_id=" + glued.transaction.id);
            check("LEGACY_OFF".equals(opinion), "No legacy opinion kept with the message: " + opinion);
            CaptureResult inferred = db.captureSms("BankMisr", "تم تنفيذ تحويل لحظي من بطاقة رقم 1234 بمبلغ 2000 رقم مرجعي 123456789يوم 24/07/2026 الساعه 20:53", t + 1000);
            check(inferred.transaction != null && inferred.transaction.needsReview && inferred.transaction.amountMinor == 200000, "REVIEW is recorded and waits for the user");
            String why = ShadowStore.reasonsOf(sql, inferred.transaction.id);
            check(why != null && why.contains("CURRENCY_INFERRED") && ReviewReasons.text(java.util.Arrays.asList(why.split(","))).get(0).contains("العملة"), "Review reason in the user's language");
            long rows = db.transactionCount();
            CaptureResult info = db.captureSms("7030", "Dear Customer, You have successfully renewed 1 GB subscription from Orange and 39 Egyptian pound has been deducted from your line", t + 2000);
            CaptureResult unclear = db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", t + 3000);
            check(db.transactionCount() == rows && info.transaction == null && unclear.transaction == null, "INFO_ONLY and UNUNDERSTOOD record nothing");
            check(info.reason == CaptureResult.Reason.NEW_PIPELINE_SKIPPED || info.reason == CaptureResult.Reason.NO_LEDGER_CANDIDATE, "Skipped by the new pipeline: " + info.reason);
            CaptureResult charge = db.captureSms("BankMisr", "شكرًا لاستخدامك بطاقة بنك مصر ***4636، تم الآن خصم EGP 5.00عند FAWRY يوم 20/10/2026 ، الرصيد المتاح EGP 100.00", t + 4000);
            CaptureResult back = db.captureSms("BankMisr", "تم اعادة قيد معاملتك ل EGP 5.00 التي تمت علي البطاقة 1234*4636يوم 20/10/2026 عندFAWRY ، لمزيد من المعلومات. تفضل بالاتصال ب 19888", t + 4000 + 5 * 60_000L);
            check(charge.transaction != null && back.transaction != null && "income".equals(back.transaction.kind), "Charge and its return recorded");
            check(db.reimbursedExpense(back.transaction.id) != null && db.reimbursedExpense(back.transaction.id).id == charge.transaction.id, "The return is linked to its charge (expense offset)");
            long kept = glued.transaction.id; long keptAmount = glued.transaction.amountMinor;
            db.captureSms("BankMisr", "شكرًا لاستخدامك بطاقة بنك مصر ***4636، تم الآن خصم EGP 149.98عند KASHIER*BUTLERS & CO C يوم 20/10/2026 ، الرصيد المتاح EGP 2139.44", t);
            check(db.transactionById(kept).amountMinor == keptAmount && db.transactionCount() == rows + 2, "A repeated message changes nothing recorded");
            // 10b. Owner test, 22 Oct 2026: HSBC 0001 → Banque Misr 7392 by IPN (4.00 + 0.50 fee). The bank messages name only
            // accounts, so the rows must still attach to their sources; the debit names the user's own 7392, so it is a transfer
            // to that account, and the receipt is merged into it by the repair that follows the capture.
            // The debit carries its own clock (06:46); the receipt takes its delivery time, so deliver both that morning.
            long ipn = java.time.LocalDateTime.of(2026, 10, 22, 6, 47).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long hsbc = db.addPaymentSource("HSBC", "حساب", "0001"), misr = db.addPaymentSource("Banque Misr", "حساب", "7392");
            long transfers = db.transactionCount();
            CaptureResult leg1 = db.captureSms("HSBC", "Your HSBC Account ********0001 was debited with IPN outward transfer for EGP 4.50 on 22-10-2026 06:46 to 7392 with reference 12fb34cc. For further details, please contact HSBC call centre", ipn);
            check(leg1.transaction != null && leg1.transaction.paymentSourceId != null && leg1.transaction.paymentSourceId == hsbc,
                    "An account-only bank SMS attaches to its source: " + (leg1.transaction == null ? leg1.reason : leg1.transaction.paymentSourceId));
            check("transfer".equals(leg1.transaction.kind) && leg1.transaction.destinationSourceId != null && leg1.transaction.destinationSourceId == misr,
                    "A debit to the user's own account tail is a transfer to that account: " + leg1.transaction.kind + "/" + leg1.transaction.destinationSourceId);
            CaptureResult leg2 = db.captureSms("BankMisr", "تم اضافة مبلغ 4EGP الى حساب رقم xxx7392 فى 22-Oct-2026 عن طريق التحويل اللحظي من MOHAMED GAMAL ABDELAZIZ", ipn);
            ExpenseDb.Transaction merged = db.transactionById(leg1.transaction.id);
            check(leg2.transaction != null && leg2.transaction.id == leg1.transaction.id, "The receipt is merged into the transfer: " + (leg2.transaction == null ? leg2.reason : leg2.transaction.id));
            check(merged != null && "transfer".equals(merged.kind) && merged.amountMinor == 400 && merged.paymentSourceId == hsbc && merged.destinationSourceId == misr,
                    "One internal transfer of 4.00 from HSBC to Banque Misr: " + (merged == null ? null : merged.kind + "/" + merged.amountMinor));
            check(db.transactionCount() == transfers + 2, "Transfer and its 0.50 fee row, nothing else: " + (db.transactionCount() - transfers));
            // 10c. Banque Misr → Telda 30 (owner test, 22 Oct 2026): Telda's receipt names only the IPN channel and no digits. It gets
            // the one Telda source (created on first use), reads as an instant transfer in, and is paired with the bank debit
            // even 24 minutes later.
            long before10c = db.transactionCount();
            CaptureResult toTelda = db.captureSms("BankMisr", "تم تحويل 30.00 جنيه بنجاح إلى talda من حساب رقم xxx7392 عن طريق التحويل اللحظي", (ipn + 10 * 60_000L));
            CaptureResult onTelda = db.captureNotification("io.telda.app", "Telda", "تم استلام 30 ج.م. من خلال شبكة المدفوعات اللحظية", (ipn + 10 * 60_000L) + 24 * 60_000L);
            check(toTelda.transaction != null && onTelda.transaction != null && onTelda.transaction.id == toTelda.transaction.id,
                    "Telda receipt merged into the Banque Misr debit: " + (onTelda.transaction == null ? onTelda.reason : onTelda.transaction.id + "/" + toTelda.transaction.id));
            ExpenseDb.Transaction topUp = db.transactionById(toTelda.transaction.id);
            Long telda = null;
            for (ExpenseDb.PaymentSource s : db.paymentSources()) if ("TELDA".equals(SourceIdentity.bank(s.name))) telda = s.id;
            check(telda != null && topUp != null && "transfer".equals(topUp.kind) && topUp.amountMinor == 3000 && topUp.paymentSourceId == misr && topUp.destinationSourceId.equals(telda),
                    "One internal top-up of 30.00 from Banque Misr to the Telda source: " + (topUp == null ? null : topUp.kind + "/" + topUp.destinationSourceId + " telda=" + telda));
            check(db.transactionCount() == before10c + 1, "No fee, no income row: " + (db.transactionCount() - before10c));
            // 10d. The same receipt delivered again by the app, worded slightly differently, after the transfer was settled:
            // it folds into the transfer instead of becoming a second income (owner test, second round).
            CaptureResult again = db.captureNotification("io.telda.app", "Telda", "تم استلام 30 ج.م. من خلال شبكة المدفوعات اللحظية 🎉", (ipn + 10 * 60_000L) + 25 * 60_000L);
            check(again.transaction != null && again.transaction.id == toTelda.transaction.id && db.transactionCount() == before10c + 1,
                    "A late copy of the receipt is the settled transfer: " + (again.transaction == null ? again.reason : again.transaction.id + " count+" + (db.transactionCount() - before10c)));
            // 10e. The pipeline fails on a message: the legacy parser records it, and a PIPELINE_FAILED note keeps the reason.
            long failures = count(sql, "SELECT COUNT(*) FROM shadow_observations WHERE decision='PIPELINE_FAILED'");
            PipelineDecider.failNextFor("QA Kiosk");
            CaptureResult fallback = db.captureSms("QABANK", "Paid EGP 44.00 at \"QA Kiosk\". Card ****1234.", t + 3_600_000L);
            check(fallback.transaction != null && fallback.transaction.amountMinor == 4400 && "expense".equals(fallback.transaction.kind),
                    "Legacy records the message the pipeline failed on: " + (fallback.transaction == null ? fallback.reason : fallback.transaction.amountMinor));
            check(count(sql, "SELECT COUNT(*) FROM shadow_observations WHERE decision='PIPELINE_FAILED'") == failures + 1, "One failure note");
            String note = first(sql, "SELECT reasons || '/' || legacy_decision || '/' || origin || '/' || IFNULL(encrypted_body,'-') FROM shadow_observations WHERE decision='PIPELINE_FAILED' AND transaction_id=" + fallback.transaction.id);
            check("EXCEPTION:IllegalStateException/RECORDED/live/-".equals(note), "Failure note names its reason and what legacy did: " + note);
            CaptureResult next = db.captureSms("QABANK", "Paid EGP 46.00 at \"QA Kiosk\". Card ****1234.", t + 3_700_000L);
            check(next.transaction != null && "AUTO".equals(first(sql, "SELECT decision FROM shadow_observations WHERE transaction_id=" + next.transaction.id)), "The next message is decided by the pipeline again");
            check(db.transactionById(kept) != null, "Earlier rows stay");
            // 10f0. Each guard alone (0.4.1 item 4): a message the pipeline decides AUTO, held by exactly one legacy guard,
            // records no ledger row and its own observation names that guard; the notice also records a capture event.
            // UNSUCCESSFUL and NOTICE are absolute.
            String[][] alone = {
                    {"UNSUCCESSFUL", "Transfer EGP 331 from account ending 1111 will be reversed"},
                    {"NOTICE", "Payment EGP 332 from account ending 1111 reversed"}};
            // FUTURE_ONLY yields to an AUTO decision with a completed event outside future wording (reviewer, 23 Oct 2026):
            // the mixed alert is one purchase, not an installment, and nothing notes a guard on it.
            long mixedAt = t + 3_940_000L, beforeMixed = db.transactionCount();
            CaptureResult mixed = db.captureSms("QAGUARD", "Paid EGP 333 at QA SHOP. Your installment will be debited tomorrow", mixedAt);
            String mixedSeen = first(sql, "SELECT decision || '/' || reasons FROM shadow_observations WHERE origin='live' AND source='QAGUARD' AND received_at=" + mixedAt);
            check(mixed.transaction != null && db.transactionCount() == beforeMixed + 1 && mixed.transaction.amountMinor == 33300
                    && "expense".equals(mixed.transaction.kind) && !"أقساط".equals(mixed.transaction.category),
                    "A completed purchase with a future installment note is one purchase: " + (mixed.transaction == null ? mixed.reason : mixed.transaction.category));
            check(mixedSeen != null && mixedSeen.startsWith("AUTO/") && !mixedSeen.contains("LEGACY_GUARD"), "No guard noted on the recorded purchase: " + mixedSeen);
            // A purely future message records nothing: the pipeline itself ignores it (FUTURE_ONLY), so no legacy guard runs.
            long futureAt = t + 3_945_000L, beforeFuture = db.transactionCount();
            CaptureResult future = db.captureSms("QAGUARD", "Your installment of EGP 333 will be debited tomorrow", futureAt);
            String futureSeen = first(sql, "SELECT decision || '/' || reasons FROM shadow_observations WHERE origin='live' AND source='QAGUARD' AND received_at=" + futureAt);
            check(future.transaction == null && db.transactionCount() == beforeFuture, "A purely future message records nothing: " + future.reason);
            check(futureSeen != null && futureSeen.startsWith("IGNORE/") && futureSeen.contains("FUTURE_ONLY"), "The pipeline holds it as FUTURE_ONLY: " + futureSeen);
            for (int g = 0; g < alone.length; g++) {
                long at = t + 3_950_000L + g * 1000L, rowsBefore = db.transactionCount();
                long events = count(sql, "SELECT COUNT(*) FROM capture_events");
                CaptureResult held = db.captureSms("QAGUARD", alone[g][1], at);
                String seen = first(sql, "SELECT decision || '/' || reasons FROM shadow_observations WHERE origin='live' AND source='QAGUARD' AND received_at=" + at);
                check(held.transaction == null && db.transactionCount() == rowsBefore, alone[g][0] + " alone records no ledger row: " + held.reason);
                check(seen != null && seen.startsWith("AUTO/") && seen.endsWith(",LEGACY_GUARD:" + alone[g][0]), alone[g][0] + " alone is noted on an AUTO decision: " + seen);
                check(count(sql, "SELECT COUNT(*) FROM capture_events") == events + ("NOTICE".equals(alone[g][0]) ? 1 : 0),
                        alone[g][0] + " alone: a capture event only for the notice");
            }
            // 10f. Legacy guards stand when the pipeline records: an unsuccessful or notice message is never a ledger row,
            // and the observation says which guard held it (owner review, 22 Oct 2026).
            long guarded = db.transactionCount();
            CaptureResult willReverse = db.captureSms("QABANK", "Transfer EGP 227 from account ending 1111 will be reversed", t + 3_800_000L);
            CaptureResult reversed = db.captureSms("QABANK", "Payment EGP 227 from account ending 1111 reversed", t + 3_900_000L);
            check(willReverse.transaction == null && reversed.transaction == null && db.transactionCount() == guarded, "Guarded messages record no operation");
            String guards = first(sql, "SELECT GROUP_CONCAT(decision || '/' || reasons, ' | ') FROM (SELECT decision, reasons FROM shadow_observations WHERE reasons LIKE '%LEGACY_GUARD:%' ORDER BY id)");
            check(guards != null && guards.contains("LEGACY_GUARD:UNSUCCESSFUL") && guards.contains("LEGACY_GUARD:NOTICE") && !guards.contains("IGNORE/"),
                    "Each guard noted on a recording decision: " + guards);
            // 10g. Telda is a payment app, not a card waiting for its bank account (task D1): created as «تطبيق دفع»,
            // its notification balance updates its card, and an older «بطاقة» Telda source moves over once in repairAll.
            ExpenseDb.PaymentSource teldaSource = db.paymentSource(telda);
            check("تطبيق دفع".equals(teldaSource.kind), "Telda created as a payment app: " + teldaSource.kind);
            CaptureResult teldaPaid = db.captureNotification("io.telda.app", "Telda", "Telda تم دفع 15 ج.م. في \"Kashier Butlers\"\nرصيدك: 31.5 ج.م.", t + 4_000_000L);
            check(teldaPaid.transaction != null && telda.equals(teldaPaid.transaction.paymentSourceId), "Telda purchase on the Telda source: " + teldaPaid.reason);
            ExpenseDb.BalanceSnapshot teldaBalance = db.latestBalance(telda, "EGP");
            check(teldaBalance != null && teldaBalance.amountMinor == 3150, "Last balance on the Telda card from its notification: " + (teldaBalance == null ? null : teldaBalance.amountMinor));
            sql.execSQL("UPDATE payment_sources SET kind='بطاقة' WHERE id=" + telda);
            db.repairAll();
            check("تطبيق دفع".equals(db.paymentSource(telda).kind), "An older Telda card source becomes a payment app once: " + db.paymentSource(telda).kind);

            // 11. 0.4.5 (schema 27). A message kept by this version names the key it is captured under; a database from 0.4.4
            // (schema 26, no such column) upgrades without losing a row; an update reads the kept messages again and offers
            // those it now records, without writing the ledger; registering one twice records it once.
            check("QABANK".equals(ShadowStore.ununderstood(sql, 500).get(0).sourceKey), "A kept SMS names its sender as its key");
            long nowM = System.currentTimeMillis(), seenM = nowM - 2 * 86_400_000L;
            String fawryText = MessageNormalizer.normalize(MessageNormalizer.Raw.notification("com.fawry.myfawry", 0, "You spent 20.00 EGP with your yellowcard",
                    "Debit - 20.00 was spent at KASHIER*BUTLERS", null, null, null, null)).text;
            sql.execSQL("DROP TABLE ununderstood_messages");
            sql.execSQL("CREATE TABLE ununderstood_messages(message_hash TEXT PRIMARY KEY, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, " +
                    "seen_count INTEGER NOT NULL DEFAULT 1, channel TEXT NOT NULL, source TEXT, reasons TEXT NOT NULL, policy TEXT NOT NULL, encrypted_body TEXT)");
            String[][] keptM = {{"hm-fawry", "NOTIFICATION", "myFawry", fawryText}, {"hm-mystery", "NOTIFICATION", "Mystery App", fawryText},
                    {"hm-sms", "SMS", "QABANK", "Paid EUR 1.250 at Cafe Nine"}};
            for (String[] k : keptM) {
                android.content.ContentValues kept0 = new android.content.ContentValues();
                kept0.put("message_hash", k[0]); kept0.put("first_seen", seenM); kept0.put("last_seen", seenM); kept0.put("channel", k[1]);
                kept0.put("source", k[2]); kept0.put("reasons", "NO_COMPLETED_EVENT"); kept0.put("policy", "2026-10-29.2"); kept0.put("encrypted_body", EvidenceVault.encrypt(k[3]));
                sql.insert("ununderstood_messages", null, kept0);
            }
            sql.setVersion(26); db.close();
            db = new ExpenseDb(target); sql = db.getWritableDatabase();
            check(sql.getVersion() == ExpenseDb.SCHEMA_VERSION, "Schema 26 upgrades to the current schema");
            check(count(sql, "SELECT COUNT(*) FROM pragma_table_info('ununderstood_messages') WHERE name IN ('source_key','recovered_at')") == 2, "The new columns exist");
            check(count(sql, "SELECT COUNT(*) FROM ununderstood_messages") == 3 && count(sql, "SELECT COUNT(*) FROM ununderstood_messages WHERE source_key IS NULL AND recovered_at IS NULL") == 3,
                    "Rows kept by 0.4.4 survive without a key and are not recovered");
            check(ShadowStore.ununderstood(sql, 500).size() == 3 && ShadowStore.recoveredCount(sql) == 0, "They still list on the old page");
            long ledgerM = db.transactionCount();
            java.util.Map<String, String> selectedM = java.util.Collections.singletonMap("com.fawry.myfawry", "myFawry");
            check(ShadowStore.reevaluateMissed(sql, selectedM, ParserShadow.learning(), nowM) == 1, "One of three kept messages is now understood");
            check(db.transactionCount() == ledgerM, "Reading kept messages again never writes the ledger");
            check(ShadowStore.recoveredCount(sql) == 1, "One suggestion waits");
            ShadowStore.Ununderstood fawryRow = null;
            for (ShadowStore.Ununderstood u : ShadowStore.ununderstood(sql, 500)) {
                if ("hm-fawry".equals(u.hash)) fawryRow = u;
                else check(!u.recovered(), "Other rows stay where they were: " + u.hash);
            }
            check(fawryRow != null && fawryRow.recovered() && "com.fawry.myfawry".equals(fawryRow.sourceKey), "The notification row got its package as key");
            check(ShadowStore.reevaluateMissed(sql, selectedM, ParserShadow.learning(), nowM + 1000) == 0, "A second pass finds nothing new");
            MissedReevaluation.Reading cardM = ShadowStore.readingOf(fawryRow, nowM);
            check(cardM != null && cardM.amountMinor == 2000 && "EGP".equals(cardM.currency) && "out".equals(cardM.direction), "The suggestion reads 20.00 EGP out");
            check(!ShadowStore.possiblyRecorded(sql, fawryRow, cardM, java.time.ZoneId.systemDefault()), "No ledger row of that amount near it: no duplicate hint");
            long beforeM = db.transactionCount();
            CaptureResult firstM = db.capture(MissedReevaluation.request(fawryRow.row(), fawryRow.sourceKey));
            CaptureResult secondM = db.capture(MissedReevaluation.request(fawryRow.row(), fawryRow.sourceKey));
            check(firstM.accepted && firstM.transaction != null && firstM.transaction.amountMinor == 2000 && db.transactionCount() == beforeM + 1,
                    "Registering records the purchase through the live path: " + firstM.reason);
            check(!secondM.accepted && db.transactionCount() == beforeM + 1, "Registering twice records once: " + secondM.reason);
            check(MissedReevaluation.outcome(secondM.accepted, secondM.reason.name()) == MissedReevaluation.Outcome.ALREADY_RECORDED, "The second tap is an already-recorded outcome");
            check(ShadowStore.possiblyRecorded(sql, fawryRow, cardM, java.time.ZoneId.systemDefault()), "Once recorded, the same suggestion shows the duplicate hint");
            ShadowStore.removeUnunderstood(sql, fawryRow.hash);
            check(ShadowStore.recoveredCount(sql) == 0, "The registered suggestion left the list");

            // 9. Delete all local data removes shadow data.
            db.clearAllData();
            check(count(sql, "SELECT (SELECT COUNT(*) FROM shadow_observations)+(SELECT COUNT(*) FROM ununderstood_messages)+(SELECT COUNT(*) FROM learning_rules)") == 0,
                    "Shadow data deleted with all data");
            check(ParserShadow.learning().shapeCount() == 0, "In-memory learning forgotten");
            result.putString("stream", "Shadow store checks passed: " + checks + "; upgrade, live row beside ledger, sealing, ununderstood, dedupe, pipeline decides, failure note, guards, learning, backup, delete\n");
        } catch (Throwable failure) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Shadow store FAILED: " + trace);
        } finally {
            if (owned) {
                try { db.clearAllData(); } catch (RuntimeException ignored) { }
            }
            if (db != null) db.close();
            runner.finish(status, result);
        }
    }
}
