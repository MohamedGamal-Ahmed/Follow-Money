package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.util.List;

/**
 * Shadow storage on a real Android runtime (isolated emulator app only): upgrade v25 → v26, live capture stores its
 * shadow row beside the ledger row without bumping the ledger revision, Keystore sealing, ununderstood text, dedupe
 * replay, a group summary observed and never captured, learning persistence, backup open/re-seal, the re-scan over
 * real inbox SMS without touching the ledger, and "delete all data".
 */
final class ShadowStoreTest {
    private final Instrumentation runner;
    private int checks;
    ShadowStoreTest(Instrumentation runner) { this.runner = runner; }
    private void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }

    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor fd = runner.getUiAutomation().executeShellCommand(command);
             java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] bytes = new byte[1024]; while (input.read(bytes) != -1) { }
        }
    }

    private static void collect(android.view.View view, StringBuilder out) {
        if (view instanceof android.widget.TextView) out.append(((android.widget.TextView) view).getText()).append('\n');
        if (view instanceof android.view.ViewGroup)
            for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) collect(((android.view.ViewGroup) view).getChildAt(i), out);
    }

    private static long count(SQLiteDatabase sql, String query) {
        try (Cursor c = sql.rawQuery(query, null)) { return c.moveToFirst() ? c.getLong(0) : -1; }
    }

    private static String first(SQLiteDatabase sql, String query) {
        try (Cursor c = sql.rawQuery(query, null)) { return c.moveToFirst() ? c.getString(0) : null; }
    }

    void run() {
        Bundle result = new Bundle(); int status = -1; ExpenseDb db = null; boolean owned = false;
        Context target = runner.getTargetContext();
        try {
            if (!target.getPackageName().endsWith(".shadowqa") || !"ranchu".equals(android.os.Build.HARDWARE)) throw new SecurityException("Isolated emulator required");
            db = new ExpenseDb(target); check(db.transactionCount() == 0, "Existing ledger retained; empty fixture required"); owned = true;
            // Sections 1-8 check shadow mode: the legacy parser decides. Step 7 is checked in section 10.
            DecisionSwitch.set(target, false);
            SQLiteDatabase sql = db.getWritableDatabase();

            // 1. Upgrade from schema 25 creates the shadow tables.
            for (String table : new String[]{"shadow_observations", "ununderstood_messages", "learning_rules"}) sql.execSQL("DROP TABLE IF EXISTS " + table);
            sql.setVersion(25); db.close();
            db = new ExpenseDb(target); sql = db.getWritableDatabase();
            check(sql.getVersion() == ExpenseDb.SCHEMA_VERSION && ExpenseDb.SCHEMA_VERSION == 26, "Schema upgraded to 26");
            check(count(sql, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('shadow_observations','ununderstood_messages','learning_rules')") == 3,
                    "Upgrade from 25 creates the three shadow tables");

            // 2. Live capture: the shadow row sits beside the ledger row; the ledger revision ignores it.
            long now = System.currentTimeMillis();
            CaptureResult paid = db.captureSms("QABANK", "Paid EGP 45 at \"QA Market\". Card ****1234.", now);
            check(paid.transaction != null && paid.transaction.amountMinor == 4500, "Legacy recorded the payment: " + paid.reason);
            check(db.changes() == paid.revisionAfter, "Shadow write does not change the ledger revision");
            List<ShadowStore.Stored> live = ShadowStore.recent(sql, "live", 10);
            check(live.size() == 1, "One live shadow row");
            ShadowStore.Stored row = live.get(0);
            check(row.legacyDecision.startsWith("RECORDED") && "4500".equals(row.legacy.get("amount_minor")) && "out".equals(row.legacy.get("direction")), "Legacy values stored");
            check("AUTO".equals(row.decision) && "4500".equals(row.values.get("amount_minor")) && row.score >= 80, "New decision, amount and score stored: " + row.decision);
            check(row.diff().agrees() && !row.unreadable, "Agreement computed from opened values");
            check(count(sql, "SELECT transaction_id FROM shadow_observations WHERE id=" + row.id) == paid.transactionId, "Linked to the legacy record");
            String sealed = first(sql, "SELECT encrypted_body FROM shadow_observations WHERE id=" + row.id);
            check(sealed != null && !sealed.contains("4500") && !sealed.contains("QA Market"), "Values sealed by the Keystore");

            // 3. Not understood: the text is kept, sealed, once per message.
            db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", now + 1000);
            db.captureSms("QABANK", "Paid EUR 1.250 at Cafe Nine", now + 2000);
            List<ShadowStore.Ununderstood> unknown = ShadowStore.ununderstood(sql, 10);
            check(unknown.size() == 1 && unknown.get(0).count == 2 && unknown.get(0).text.contains("Cafe Nine"), "Ununderstood message kept once, opened on device");
            check(!first(sql, "SELECT encrypted_body FROM ununderstood_messages").contains("Cafe"), "Ununderstood text sealed");

            // 4. A group summary is observed, never captured; the same delivery again is a replay.
            long ledgerRows = db.transactionCount(); long revision = db.changes();
            MessageNormalizer.Raw summary = MessageNormalizer.Raw.notification("com.qa.pay", ParsingPolicy.FLAG_GROUP_SUMMARY, "QA Pay",
                    "Paid EGP 85 at \"Nova Market\".", null, null, null, null).withIdentity(MessageNormalizer.Raw.notificationIdentity("0|com.qa.pay|1|t|1", 5000L));
            CaptureTransaction.observeWithoutCapture(db, summary, "QA Pay", 5000L, "LEGACY_GROUP_SUMMARY");
            CaptureTransaction.observeWithoutCapture(db, summary, "QA Pay", 5000L, "LEGACY_GROUP_SUMMARY");
            check(db.transactionCount() == ledgerRows && db.changes() == revision, "Group summary records nothing");
            live = ShadowStore.recent(sql, "live", 10);
            ShadowStore.Stored replay = null, firstSummary = null;
            for (ShadowStore.Stored s : live) if ("LEGACY_GROUP_SUMMARY".equals(s.legacyReason)) { if (replay == null) replay = s; else firstSummary = s; }
            check(firstSummary != null && "NEW".equals(firstSummary.dedupe) && replay.reasons.contains("GROUP_SUMMARY"), "Summary observed with its reason");
            check("REPLAY".equals(replay.dedupe) && "DELIVERY_REPLAY".equals(replay.dedupeReason), "Same delivery replayed: " + replay.dedupe);

            // 5. Learning rules persist (hashes and counts only).
            ParserShadow.learning().recordDismissal("QABANK", "Paid EGP 45 at \"QA Market\".");
            ShadowStore.saveLearning(sql, ParserShadow.learning(), now);
            check(ShadowStore.counts(sql, "live")[2] >= 1, "Learning rule stored");
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

            // 7. Re-scan over the real inbox (the QA script sends the SMS): shadow rows only, the ledger is untouched.
            shell("pm grant " + target.getPackageName() + " android.permission.READ_SMS");
            ledgerRows = db.transactionCount(); revision = db.changes();
            ShadowRescan.Result scan = ShadowRescan.run(target, 0, new java.util.concurrent.atomic.AtomicBoolean(), (done, total) -> { });
            check(scan.scanned >= 3, "Inbox SMS scanned: " + scan.scanned);
            check(db.transactionCount() == ledgerRows && db.changes() == revision, "Re-scan recorded no operation");
            List<ShadowStore.Stored> rescanned = ShadowStore.recent(sql, "rescan", 100);
            boolean comparedPayment = false;
            for (ShadowStore.Stored s : rescanned) if ("7000".equals(s.values.get("amount_minor")) && "7000".equals(s.legacy.get("amount_minor"))) comparedPayment = true;
            check(comparedPayment && rescanned.size() == scan.stored, "Re-scan compared the inbox payment on both sides");
            check(target.getSharedPreferences(ShadowRescan.PREFS, 0).getInt("scanned", -1) == scan.scanned, "Re-scan summary kept");
            ShadowRescan.run(target, 0, new java.util.concurrent.atomic.AtomicBoolean(), (done, total) -> { });
            check(ShadowStore.recent(sql, "rescan", 100).size() == rescanned.size(), "A new re-scan replaces the previous one");
            ShadowReport.Summary report = ShadowReport.build(new java.util.ArrayList<>(), scan.scanned, scan.skippedUnstored);
            check(report.scanned == scan.scanned, "Report built");

            // 7b. A fee returned minutes after the same charge is an expense offset linked to that charge.
            long feeAt = now + 3_600_000L;
            db.captureSms("QABANK", "تم خصم 5.00 جنيه رسوم خدمة من حسابك", feeAt);
            db.captureSms("QABANK", "تم اضافة 5.00 جنيه الى حسابك", feeAt + 5 * 60_000L);
            List<ShadowStore.Stored> feeRows = ShadowStore.recent(sql, "live", 2);
            check("expense_offset".equals(feeRows.get(0).values.get("accounting_effect")) && feeRows.get(0).reasons.contains("LINKED_EXPENSE_OFFSET") &&
                    String.valueOf(feeRows.get(1).id).equals(feeRows.get(0).values.get("offset_of")), "Fee return linked to its charge: " + feeRows.get(0).reasons);

            // 8. The hidden debug screen renders every view over the stored rows (live, differences, ununderstood, re-scan report).
            android.content.Intent open = new android.content.Intent(target, ParserDebugActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ParserDebugActivity screen = (ParserDebugActivity) runner.startActivitySync(open);
            java.lang.reflect.Field viewField = ParserDebugActivity.class.getDeclaredField("view"), loadedField = ParserDebugActivity.class.getDeclaredField("loaded");
            viewField.setAccessible(true); loadedField.setAccessible(true);
            java.lang.reflect.Method show = ParserDebugActivity.class.getDeclaredMethod("show"), load = ParserDebugActivity.class.getDeclaredMethod("load");
            show.setAccessible(true); load.setAccessible(true);
            String[][] views = {{"all", "QABANK"}, {"diff", "إعادة الفحص"}, {"ununderstood", "Cafe Nine"}, {"rescan", "اتفاق:"}};
            for (String[] v : views) {
                runner.runOnMainSync(() -> { try { viewField.set(screen, v[0]); loadedField.set(screen, null); load.invoke(screen); show.invoke(screen); } catch (Exception e) { throw new RuntimeException(e); } });
                String shown = "";
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(100); runner.waitForIdleSync();
                    StringBuilder all = new StringBuilder(); collect(screen.getWindow().getDecorView(), all); shown = all.toString();
                    if (loadedField.get(screen) != null && shown.contains(v[1])) break;
                }
                check(shown.contains("تشخيص المحلل") && shown.contains(v[1]), "Debug view " + v[0] + " shows " + v[1]);
            }
            screen.finish(); runner.waitForIdleSync();

            // 10. Step 7: the new pipeline decides messages that arrive after it is switched on; the switch rolls back at once.
            long beforeSwitch = System.currentTimeMillis();
            DecisionSwitch.set(target, true);
            check(DecisionSwitch.on(target) && DecisionSwitch.since(target) >= beforeSwitch, "Switch on, activation time stored");
            long t = DecisionSwitch.since(target) + 1000;
            CaptureResult early = db.captureSms("QABANK", "Paid EGP 33.00 at \"OLD SHOP\". Card ****1234.", DecisionSwitch.since(target) - 60_000L);
            check(early.transaction != null && early.transaction.amountMinor == 3300, "A message from before activation is decided by legacy");
            CaptureResult glued = db.captureSms("BankMisr", "شكرًا لاستخدامك بطاقة بنك مصر ***4636، تم الآن خصم EGP 149.98عند KASHIER*BUTLERS & CO C يوم 20/10/2026 ، الرصيد المتاح EGP 2139.44", t);
            check(glued.transaction != null && glued.transaction.amountMinor == 14998 && "expense".equals(glued.transaction.kind) && !glued.transaction.needsReview,
                    "New pipeline records the purchase with its piasters: " + (glued.transaction == null ? glued.reason : glued.transaction.amountMinor));
            check(glued.transaction.merchant.toUpperCase(java.util.Locale.ROOT).contains("BUTLERS"), "Merchant from the new pipeline: " + glued.transaction.merchant);
            String opinion = first(sql, "SELECT legacy_decision || '/' || legacy_reason FROM shadow_observations WHERE transaction_id=" + glued.transaction.id);
            check(opinion != null && opinion.startsWith("WOULD_RECORD/LEGACY_OPINION"), "Legacy opinion kept with the message: " + opinion);
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
            long hsbc = db.addPaymentSource("HSBC", "حساب", "0001"), misr = db.addPaymentSource("Banque Misr", "حساب", "7392");
            long transfers = db.transactionCount();
            CaptureResult leg1 = db.captureSms("HSBC", "Your HSBC Account ********0001 was debited with IPN outward transfer for EGP 4.50 on 22-10-2026 06:46 to 7392 with reference 12fb34cc. For further details, please contact HSBC call centre", t + 10_000L);
            check(leg1.transaction != null && leg1.transaction.paymentSourceId != null && leg1.transaction.paymentSourceId == hsbc,
                    "An account-only bank SMS attaches to its source: " + (leg1.transaction == null ? leg1.reason : leg1.transaction.paymentSourceId));
            check("transfer".equals(leg1.transaction.kind) && leg1.transaction.destinationSourceId != null && leg1.transaction.destinationSourceId == misr,
                    "A debit to the user's own account tail is a transfer to that account: " + leg1.transaction.kind + "/" + leg1.transaction.destinationSourceId);
            CaptureResult leg2 = db.captureSms("BankMisr", "تم اضافة مبلغ 4EGP الى حساب رقم xxx7392 فى 22-Oct-2026 عن طريق التحويل اللحظي من MOHAMED GAMAL ABDELAZIZ", t + 10_000L);
            ExpenseDb.Transaction merged = db.transactionById(leg1.transaction.id);
            check(leg2.transaction != null && leg2.transaction.id == leg1.transaction.id, "The receipt is merged into the transfer: " + (leg2.transaction == null ? leg2.reason : leg2.transaction.id));
            check(merged != null && "transfer".equals(merged.kind) && merged.amountMinor == 400 && merged.paymentSourceId == hsbc && merged.destinationSourceId == misr,
                    "One internal transfer of 4.00 from HSBC to Banque Misr: " + (merged == null ? null : merged.kind + "/" + merged.amountMinor));
            check(db.transactionCount() == transfers + 2, "Transfer and its 0.50 fee row, nothing else: " + (db.transactionCount() - transfers));
            DecisionSwitch.set(target, false);
            CaptureResult after = db.captureSms("QABANK", "Paid EGP 44.00 at \"QA Kiosk\". Card ****1234.", t + 3_600_000L);
            check(after.transaction != null && first(sql, "SELECT legacy_decision FROM shadow_observations WHERE transaction_id=" + after.transaction.id).startsWith("RECORDED"), "Rollback: legacy decides the next message at once");
            check(db.transactionById(kept) != null, "Rollback leaves what the new pipeline recorded");

            // 9. Delete all local data removes shadow data.
            db.clearAllData();
            check(count(sql, "SELECT (SELECT COUNT(*) FROM shadow_observations)+(SELECT COUNT(*) FROM ununderstood_messages)+(SELECT COUNT(*) FROM learning_rules)") == 0,
                    "Shadow data deleted with all data");
            check(ParserShadow.learning().shapeCount() == 0 && ParserShadow.recent().isEmpty(), "In-memory shadow data forgotten");
            result.putString("stream", "Shadow store checks passed: " + checks + "; upgrade, live row beside ledger, sealing, ununderstood, dedupe, group summary, learning, backup, re-scan, delete\n");
        } catch (Throwable failure) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Shadow store FAILED: " + trace);
        } finally {
            if (owned) {
                try { db.clearAllData(); } catch (RuntimeException ignored) { }
                target.getSharedPreferences(ShadowRescan.PREFS, 0).edit().clear().commit();
            }
            if (db != null) db.close();
            runner.finish(status, result);
        }
    }
}
