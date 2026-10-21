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

            // 8. Delete all local data removes shadow data.
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
