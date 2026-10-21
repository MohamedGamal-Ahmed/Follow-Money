package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backup → wipe (data, settings and the Keystore key) → restore on a disposable QA package: every table keeps its row
 * count, the statement keeps its numbers, and the original message text opens again. A wrong password or a damaged
 * file leaves the live data untouched. Modes: backup_restore (same package), backup_export / backup_import (two packages).
 */
final class BackupRestoreTest {
    private static final String ALIAS = "follow_money_source_evidence_v1";
    private static final String SMS_ONE = "Card purchase at QA BACKUP SHOP EGP 120.00 on 05/10/2026 at 10:00. Card ****1111.";
    private static final String SMS_TWO = "From HSBC: 06OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 500.00- Your available balance is EGP 1,000.00";
    private final Instrumentation runner;
    private final String mode;
    private int checks;
    BackupRestoreTest(Instrumentation runner, String mode) { this.runner = runner; this.mode = mode; }
    private void ok(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    private static long at(LocalDate day, int hour) { return day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    private Map<String, Long> rowCounts(ExpenseDb db) {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name", null)) {
            while (c.moveToNext()) tables.add(c.getString(0));
        }
        for (String table : tables) try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) { c.moveToFirst(); counts.put(table, c.getLong(0)); }
        return counts;
    }

    /** A ledger with encrypted source text, a linked refund, a commitment, a daily budget, widget settings and a profile picture. */
    private void seed(Context target, ExpenseDb db) throws Exception {
        LocalDate day = LocalDate.now().minusDays(2);
        ok(db.addSms("QA Bank", SMS_ONE, at(day, 10)), "first SMS captured");
        ok(db.addSms("HSBC", SMS_TWO, at(day, 11)), "second SMS captured");
        ok(db.addManual(at(day, 12), 8000, "EGP", "QA MANUAL", "أكل وشرب"), "manual row saved");
        long purchase = db.latestInserted().id;
        ok(db.addManualEntry(at(day, 13), 3000, "EGP", "QA refund", "دخل", null, "income", null, null), "refund row saved");
        long refund = db.latestInserted().id;
        db.getWritableDatabase().execSQL("INSERT INTO reimbursements(income_id,expense_id,amount_minor) VALUES(?,?,?)", new Object[]{refund, purchase, 3000});
        ok(db.putCommitment(null, "QA provider", 10000, "EGP", LocalDate.now().plusDays(5), "monthly", "installment", 12, 2, 1, "qa provider", null) > 0, "commitment saved");
        DailySummary.save(target, new DailyAllowance.Plan("EGP", 20000, LocalDate.now().minusDays(10), 25));
        target.getSharedPreferences("widget_settings", 0).edit().putString("currency", "USD").putBoolean("hide_amounts", true).commit();
        target.getSharedPreferences("reminder_settings", 0).edit().putInt("hour", 21).commit();
        Files.write(new File(target.getFilesDir(), "profile-avatar.png").toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
    }

    private void wipe(Context target, ExpenseDb db) throws Exception {
        db.clearAllData();
        for (String name : new String[]{"capture_settings", "notification_settings", "reminder_settings", "widget_settings", DailySummary.SETTINGS})
            target.getSharedPreferences(name, 0).edit().clear().commit();
        new File(target.getFilesDir(), "profile-avatar.png").delete();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        ok(!store.containsAlias(ALIAS), "Keystore alias removed: the old ciphertext can no longer be opened on this device");
    }

    /**
     * The same backup, with a trigger in its ledger that aborts any UPDATE of encrypted_body: re-encryption on apply()
     * fails after the file swap, exactly where an unavailable Keystore would fail. The decrypted ledger lives in the cache
     * only for the rewrite and is deleted.
     */
    private byte[] poison(Context target, byte[] file, char[] password) throws Exception {
        Map<String, byte[]> entries = BackupVault.open(password.clone(), file, ExpenseDb.SCHEMA_VERSION);
        File ledger = File.createTempFile("poison-", ".db", target.getCacheDir());
        try {
            Files.write(ledger.toPath(), entries.get(BackupVault.LEDGER_ENTRY));
            android.database.sqlite.SQLiteDatabase sql = android.database.sqlite.SQLiteDatabase.openDatabase(ledger.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            long before, after;
            try {
                // Android opens in WAL mode by default; the trigger must land in the main file, which is all the backup carries.
                try (Cursor c = sql.rawQuery("PRAGMA journal_mode=DELETE", null)) { c.moveToFirst(); }
                try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'", null)) { c.moveToFirst(); before = c.getLong(0); }
                sql.execSQL("CREATE TRIGGER qa_refuse_encrypt BEFORE UPDATE OF encrypted_body ON capture_evidence BEGIN SELECT RAISE(ABORT, 'qa: re-encryption refused'); END");
                try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'", null)) { c.moveToFirst(); after = c.getLong(0); }
            } finally { sql.close(); }
            byte[] bytes = Files.readAllBytes(ledger.toPath());
            ok(after == before + 1 && triggers(target, bytes) == after, "the poisoned ledger carries its trigger in the file itself");
            entries.put(BackupVault.LEDGER_ENTRY, bytes);
            return BackupVault.seal(password.clone(), ExpenseDb.SCHEMA_VERSION, BuildConfig.VERSION_CODE, System.currentTimeMillis(), entries, new java.security.SecureRandom());
        } finally { for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) new File(ledger.getPath() + suffix).delete(); }
    }

    /** How many triggers a ledger (given as file bytes) defines. */
    private long triggers(Context target, byte[] ledgerBytes) throws Exception {
        File ledger = File.createTempFile("probe-", ".db", target.getCacheDir());
        try {
            Files.write(ledger.toPath(), ledgerBytes);
            android.database.sqlite.SQLiteDatabase sql = android.database.sqlite.SQLiteDatabase.openDatabase(ledger.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'", null)) { c.moveToFirst(); return c.getLong(0); }
            finally { sql.close(); }
        } finally { ledger.delete(); }
    }

    private long readableEvidence(ExpenseDb db) {
        long readable = 0;
        for (ExpenseDb.Transaction tx : db.transactions()) for (ExpenseDb.SourceEvidence e : db.sourceEvidence(tx.id)) if (!e.failed && e.body != null) readable++;
        return readable;
    }

    private void verifyRestored(Context target, ExpenseDb db, Map<String, Long> before, long statementOut, String firstBody) {
        Map<String, Long> after = rowCounts(db);
        ok(after.equals(before), "every table keeps its row count: " + after + " vs " + before);
        LocalDate[] period = MoneyFlow.statementPeriod(25, LocalDate.now());
        long out = MoneyFlow.statement(db.flowItems(period[0], period[1], ZoneId.systemDefault()), period, ZoneId.systemDefault(), "EGP").out;
        ok(out == statementOut, "the statement's «خرج» is unchanged: " + out + " vs " + statementOut);
        ok(readableEvidence(db) == 2, "both source messages open again after re-encryption with the device key");
        boolean found = false;
        for (ExpenseDb.Transaction tx : db.transactions()) for (ExpenseDb.SourceEvidence e : db.sourceEvidence(tx.id)) if (firstBody.equals(e.body)) found = true;
        ok(found, "the original message text reads in the details, not «دليل لا يُفتح»");
        SharedPreferences widget = target.getSharedPreferences("widget_settings", 0);
        ok("USD".equals(widget.getString("currency", "")) && widget.getBoolean("hide_amounts", false), "widget settings restored");
        ok(target.getSharedPreferences("reminder_settings", 0).getInt("hour", -1) == 21, "reminder settings restored");
        ok(DailySummary.plan(target, "EGP") != null && DailySummary.plan(target, "EGP").salaryDay == 25, "daily budget and pay day restored");
        ok(new File(target.getFilesDir(), "profile-avatar.png").length() == 7, "profile picture restored");
        ok(db.recurring().size() == 1 && "QA provider".equals(db.recurring().get(0).merchant), "commitment restored");
    }

    private void idle() { runner.waitForIdleSync(); try { runner.getUiAutomation().waitForIdle(250, 3000); } catch (java.util.concurrent.TimeoutException ignored) { } }
    private void screen(Context target, String name) throws Exception {
        File dir = new File(target.getCacheDir(), "backup-ui"); ok(dir.isDirectory() || dir.mkdirs(), "Screenshot folder unavailable");
        android.graphics.Bitmap image = runner.getUiAutomation().takeScreenshot(); ok(image != null, "Screenshot unavailable");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, name + ".png"))) { ok(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out), "Screenshot save failed"); }
        finally { image.recycle(); }
    }
    private boolean has(String wanted) {
        android.view.accessibility.AccessibilityNodeInfo root = runner.getUiAutomation().getRootInActiveWindow();
        return root != null && !root.findAccessibilityNodeInfosByText(wanted).isEmpty();
    }
    private void click(String wanted) {
        android.view.accessibility.AccessibilityNodeInfo root = runner.getUiAutomation().getRootInActiveWindow();
        if (root != null) for (android.view.accessibility.AccessibilityNodeInfo found : root.findAccessibilityNodeInfosByText(wanted)) {
            found.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            for (android.view.accessibility.AccessibilityNodeInfo node = found; node != null; node = node.getParent())
                if (node.isClickable() && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { idle(); return; }
        }
        throw new AssertionError("Cannot click " + wanted);
    }
    private Object invoke(MainActivity activity, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true);
        final Object[] out = new Object[1]; final Exception[] failed = new Exception[1];
        runner.runOnMainSync(() -> { try { out[0] = method.invoke(activity, args); } catch (Exception error) { failed[0] = error; } });
        if (failed[0] != null) throw failed[0];
        idle(); return out[0];
    }

    /** Screenshots of the settings entry, the backup password dialog and the restore summary on synthetic data. */
    private void ui(Context target) throws Exception {
        ExpenseDb db = AppResources.database(target);
        ok(db.transactionCount() == 0, "QA target must start empty");
        seed(target, db);
        byte[] file = LocalBackup.export(target, db, "backup-qa-2026".toCharArray());
        target.getSharedPreferences(MainActivity.class.getName(), 0).edit().putBoolean("intro_completed", true)
                .putString("opening_mode", "off").putBoolean("initial_import_prompted", true).putBoolean("sms_permission_prompted", true)
                .putInt("repair_version", ExpenseDb.REPAIR_VERSION).commit();
        target.getSharedPreferences("notification_settings", 0).edit().putBoolean("access_prompted", true).commit();
        MainActivity activity = (MainActivity) runner.startActivitySync(new android.content.Intent(target, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        idle();
        try {
            runner.runOnMainSync(() -> { try {
                java.lang.reflect.Field tab = MainActivity.class.getDeclaredField("selectedTab"); tab.setAccessible(true); tab.setInt(activity, 5);
                java.lang.reflect.Field section = MainActivity.class.getDeclaredField("settingsSection"); section.setAccessible(true); section.set(activity, "data");
                java.lang.reflect.Field intro = MainActivity.class.getDeclaredField("introVisible"); intro.setAccessible(true); intro.setBoolean(activity, false);
                java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(activity);
            } catch (Exception error) { throw new RuntimeException(error); } });
            idle();
            ok(has("نسخة احتياطية") && has("استعادة نسخة") && has("لسه ما عملتش نسخة"), "Settings show both actions and the last-backup line");
            screen(target, "01-settings-data");
            click("نسخة احتياطية");
            // Hints are not exposed as node text; the explanation and the action button are.
            ok(has("8 أحرف على الأقل") && has("اختار مكان الحفظ"), "Backup dialog explains the password rule and offers the save action");
            screen(target, "02-backup-password");
            // The password page holds nothing to discard while empty, so Back returns to the data section directly.
            runner.runOnMainSync(activity::onBackPressed); idle();
            ok(has("استعادة نسخة") && !has("اختار مكان الحفظ"), "Back from the empty backup page returned to settings");
            LocalBackup.Prepared prepared = LocalBackup.prepare(target, file, "backup-qa-2026".toCharArray());
            invoke(activity, "restoreSummaryDialog", new Class<?>[]{LocalBackup.Prepared.class}, prepared);
            ok(has("استعادة النسخة؟") && has("4 عملية") && has("استبدل بياناتي بالنسخة"), "Restore summary names the rows and warns before replacing");
            screen(target, "03-restore-summary");
            click("إلغاء");
            ok(db.transactionCount() == 4, "Cancelling the summary kept the data");
            File[] leftovers = target.getCacheDir().listFiles((dir, name) -> name.startsWith("restore-"));
            ok(leftovers == null || leftovers.length == 0, "Cancelled restore left no temporary file");
            LocalBackup.recordBackup(target);
            runner.runOnMainSync(() -> { try { java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(activity); } catch (Exception error) { throw new RuntimeException(error); } });
            idle();
            ok(has("آخر نسخة"), "Settings show the last backup date after a backup");
            screen(target, "04-settings-after-backup");
        } finally { runner.runOnMainSync(activity::finish); }
    }

    void run() {
        Bundle result = new Bundle(); int status = -1;
        try {
            Context target = runner.getTargetContext();
            if (!target.getPackageName().contains(".backupqa") || !android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable backupqa emulator required");
            if ("backup_ui".equals(mode)) { ui(target); result.putString("stream", "Backup UI checks passed: " + checks + "; screenshots in cache/backup-ui\n"); return; }
            File exchange = new File(target.getCacheDir(), "exchange.fmbackup");
            if ("backup_import".equals(mode)) {
                // The file was produced by another package (Beta → production stand-in); only the counts travel with it.
                ExpenseDb db = AppResources.database(target);
                ok(db.transactionCount() == 0, "importing package starts empty");
                byte[] file = Files.readAllBytes(exchange.toPath());
                char[] password = "backup-qa-2026".toCharArray();
                LocalBackup.Prepared prepared = LocalBackup.prepare(target, file, password);
                ok(prepared.summary.transactions == 4 && prepared.summary.sources >= 2, "summary names the backup's rows: " + prepared.summary.transactions);
                prepared.apply(target);
                db = AppResources.database(target);
                ok(db.transactionCount() == 4 && readableEvidence(db) == 2, "restored into a different package with readable evidence");
                ok(target.getSharedPreferences("widget_settings", 0).getBoolean("hide_amounts", false), "settings travelled with the file");
                result.putString("stream", "Backup import checks passed: " + checks + "\n");
                return;
            }
            ExpenseDb db = AppResources.database(target);
            ok(db.transactionCount() == 0, "QA target must start empty");
            seed(target, db);
            Map<String, Long> before = rowCounts(db);
            ok(before.get("transactions") == 4 && before.get("capture_evidence") == 2 && before.get("reimbursements") == 1 && before.get("recurring") == 1, "seed is complete: " + before);
            ok(readableEvidence(db) == 2, "evidence is readable before the backup");
            LocalDate[] period = MoneyFlow.statementPeriod(25, LocalDate.now());
            long statementOut = MoneyFlow.statement(db.flowItems(period[0], period[1], ZoneId.systemDefault()), period, ZoneId.systemDefault(), "EGP").out;
            ok(statementOut > 0, "the statement has a figure to preserve");
            char[] password = "backup-qa-2026".toCharArray();
            byte[] file = LocalBackup.export(target, db, password.clone());
            BackupVault.Header header = BackupVault.header(file);
            ok(header.schemaVersion == 25 && header.versionCode > 0, "backup header carries the schema and app version");
            ok(!new String(file, java.nio.charset.StandardCharsets.ISO_8859_1).contains("QA BACKUP SHOP"), "message text is not visible in the file");
            File[] leftovers = target.getCacheDir().listFiles((dir, name) -> new File(dir, name).isFile() && (name.startsWith("backup-") || name.startsWith("restore-")));
            ok(leftovers == null || leftovers.length == 0, "no temporary snapshot is left in the app folder after export");
            if ("backup_export".equals(mode)) {
                Files.write(exchange.toPath(), file);
                result.putString("stream", "Backup export checks passed: " + checks + "; file in cache/exchange.fmbackup\n");
                return;
            }
            // A wrong password and a damaged file are refused while the live data stays exactly as it is.
            try { LocalBackup.prepare(target, file, "not-the-password".toCharArray()); ok(false, "wrong password accepted"); }
            catch (BackupVault.Rejected rejected) { ok(rejected.reason == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "wrong password refused"); }
            byte[] damaged = file.clone(); damaged[damaged.length - 40] ^= 0x10;
            try { LocalBackup.prepare(target, damaged, password.clone()); ok(false, "damaged file accepted"); }
            catch (BackupVault.Rejected rejected) { ok(rejected.reason == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "damaged file refused"); }
            ok(rowCounts(db).equals(before) && readableEvidence(db) == 2, "refusals changed nothing");
            // A prepared restore that is discarded (user cancelled at the summary) changes nothing either.
            LocalBackup.Prepared cancelled = LocalBackup.prepare(target, file, password.clone());
            ok(cancelled.summary.transactions == 4, "summary counts the backup's transactions");
            cancelled.discard();
            ok(rowCounts(db).equals(before) && readableEvidence(db) == 2, "cancelling at the summary changed nothing");
            leftovers = target.getCacheDir().listFiles((dir, name) -> name.startsWith("restore-"));
            ok(leftovers == null || leftovers.length == 0, "discard removed its temporary files");
            // A restore that fails AFTER the file swap (here: a ledger whose rows refuse re-encryption, standing in for a
            // Keystore that is unavailable) rolls back to the previous database: rows and evidence exactly as before.
            File dbFile = target.getDatabasePath("masareef.db");
            File kept = new File(dbFile.getPath() + ExpenseDb.KEPT_SUFFIX), incoming = new File(dbFile.getPath() + ExpenseDb.INCOMING_SUFFIX);
            LocalBackup.Prepared poisoned = LocalBackup.prepare(target, poison(target, file, password), password.clone());
            ok(poisoned.summary.transactions == 4, "the poisoned backup reads like a normal one until it is applied");
            try {
                poisoned.apply(target);
                long triggersLive; try (Cursor c = AppResources.database(target).getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'", null)) { c.moveToFirst(); triggersLive = c.getLong(0); }
                ok(false, "a restore whose re-encryption failed was reported as success; triggers in the live database now: " + triggersLive + ", readable evidence: " + readableEvidence(AppResources.database(target)));
            } catch (LocalBackup.RestoreFailed failed) { ok(failed.rolledBack, "the failure after the swap rolled back: " + failed.getMessage()); }
            db = AppResources.database(target);
            ok(rowCounts(db).equals(before) && readableEvidence(db) == 2, "rows and evidence are exactly as before the failed restore");
            ok(DailySummary.plan(target, "EGP") != null && "USD".equals(target.getSharedPreferences("widget_settings", 0).getString("currency", "")), "settings are as before the failed restore");
            ok(!kept.exists() && !incoming.exists(), "no .restore-bak or .new is left after the rollback");
            leftovers = target.getCacheDir().listFiles((dir, name) -> name.startsWith("restore-"));
            ok(leftovers == null || leftovers.length == 0, "the failed restore removed its temporary files");
            // A restore interrupted after the swap (the process died): the kept database comes back at the next app start,
            // which runs this recovery before the first open.
            AppResources.closeDatabase();
            Files.move(dbFile.toPath(), kept.toPath());
            Files.write(dbFile.toPath(), new byte[]{1, 2, 3});
            Files.write(incoming.toPath(), new byte[]{4});
            ok(ExpenseDb.recoverInterruptedRestore(target), "the start-up recovery found and repaired the interrupted restore");
            db = AppResources.database(target);
            ok(rowCounts(db).equals(before) && readableEvidence(db) == 2, "an interrupted restore is undone at the next open");
            ok(!kept.exists() && !incoming.exists(), "the recovery removed the kept and incoming files");
            // Wipe everything, including the Keystore key, then restore.
            wipe(target, db);
            ok(db.transactionCount() == 0 && DailySummary.plan(target, "EGP") == null, "wiped");
            LocalBackup.Prepared prepared = LocalBackup.prepare(target, file, password.clone());
            ok(prepared.summary.transactions == 4 && prepared.summary.createdAt == header.createdAt, "summary before applying");
            prepared.apply(target);
            db = AppResources.database(target);
            verifyRestored(target, db, before, statementOut, SMS_ONE);
            ok(dbFile.exists() && !kept.exists() && !incoming.exists(), "the live database file was swapped cleanly and the kept copy dropped");
            result.putString("stream", "Backup restore checks passed: " + checks + "\n");
        } catch (Throwable error) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Backup restore FAILED: " + trace);
        } finally {
            if (!"backup_export".equals(mode)) try { AppResources.database(runner.getTargetContext()).clearAllData(); } catch (RuntimeException ignored) { }
            runner.finish(status, result);
        }
    }
}
