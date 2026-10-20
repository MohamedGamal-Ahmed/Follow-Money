package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One encrypted file (BackupVault) with the whole ledger, the settings and the profile picture, written where the user
 * chooses through the document picker and restored on any device. Source text is Keystore-encrypted on the device and
 * that key never leaves it, so the backup carries the text in plain form inside the password-encrypted file and the
 * restore re-encrypts it with the new device's key. Decrypted text only ever exists in temporary files that are deleted
 * in finally blocks; nothing unencrypted is left in the app folder.
 */
final class LocalBackup {
    static final String PREFS = "backup_settings";
    static final String LAST_BACKUP_AT = "last_backup_at";
    static final String REMINDER_DISMISSED_AT = "reminder_dismissed_at";
    static final long REMINDER_AFTER_MS = 30L * 24 * 60 * 60 * 1000;
    /** The settings that travel with the ledger: capture, notifications, reminders, widget, and the daily budget / pay day. */
    static final String[] SETTINGS = {"capture_settings", "notification_settings", "reminder_settings", "widget_settings", DailySummary.SETTINGS};
    static final String PROFILE_FILE = "profile-avatar.png";

    private LocalBackup() {}

    static void recordBackup(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_BACKUP_AT, System.currentTimeMillis()).apply();
    }
    static long lastBackupAt(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_BACKUP_AT, 0); }
    static void dismissReminder(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(REMINDER_DISMISSED_AT, System.currentTimeMillis()).apply();
    }
    /** A gentle home reminder: thirty days without a backup (or since the last dismissal), only when there is something to back up. */
    static boolean reminderDue(Context context, long transactionCount, long now) {
        if (transactionCount <= 0) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(LAST_BACKUP_AT, 0), dismissed = prefs.getLong(REMINDER_DISMISSED_AT, 0);
        long since = Math.max(last, dismissed);
        if (since == 0) {
            long firstSeen = prefs.getLong("first_seen_at", 0);
            if (firstSeen == 0) { prefs.edit().putLong("first_seen_at", now).apply(); return false; }
            since = firstSeen;
        }
        return now - since >= REMINDER_AFTER_MS;
    }

    /** Builds the encrypted backup bytes; the password is wiped before returning, whatever happens. */
    static byte[] export(Context context, ExpenseDb db, char[] password) throws IOException, GeneralSecurityException {
        File snapshot = File.createTempFile("backup-", ".db", context.getCacheDir());
        try {
            db.exportSnapshot(snapshot);
            SQLiteDatabase copy = SQLiteDatabase.openDatabase(snapshot.getPath(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                copy.beginTransaction();
                try { EvidenceVault.decryptAll(copy); copy.setTransactionSuccessful(); } finally { copy.endTransaction(); }
                // A rollback journal left beside the snapshot would otherwise be missed when the file is read as bytes.
                copy.execSQL("VACUUM");
            } finally { copy.close(); }
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put(BackupVault.LEDGER_ENTRY, Files.readAllBytes(snapshot.toPath()));
            entries.put(BackupVault.PREFS_ENTRY, settingsJson(context).getBytes(StandardCharsets.UTF_8));
            File profile = new File(context.getFilesDir(), PROFILE_FILE);
            if (profile.exists()) entries.put(BackupVault.PROFILE_ENTRY, Files.readAllBytes(profile.toPath()));
            return BackupVault.seal(password, ExpenseDb.SCHEMA_VERSION, BuildConfig.VERSION_CODE, System.currentTimeMillis(), entries, new SecureRandom());
        } finally {
            BackupVault.wipe(password);
            for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) { File leftover = new File(snapshot.getPath() + suffix); if (leftover.exists()) leftover.delete(); }
        }
    }

    /** A restore that did not complete; rolledBack says whether the previous database is back in place. */
    static final class RestoreFailed extends IOException {
        final boolean rolledBack;
        RestoreFailed(boolean rolledBack, Throwable cause) { super(rolledBack ? "Restore failed; previous data restored" : "Restore failed; previous data kept as .restore-bak", cause); this.rolledBack = rolledBack; }
    }

    /**
     * App start: temporary snapshots that an export or a restore left in the cache (a crash mid-way) hold the ledger with
     * plain source text, so they are removed. Anything younger than an hour may belong to a backup still running.
     */
    static int removeLeftovers(Context context) {
        File[] files = context.getCacheDir().listFiles((dir, name) -> (name.startsWith("backup-") || name.startsWith("restore-")) && name.contains(".db"));
        if (files == null) return 0;
        int removed = 0;
        long stale = System.currentTimeMillis() - 60L * 60 * 1000;
        for (File file : files) if (file.isFile() && file.lastModified() < stale && file.delete()) removed++;
        return removed;
    }

    /** What a backup holds, shown before the user confirms that the current data will be replaced. */
    static final class Summary {
        final int schemaVersion, versionCode, transactions, sources, unreadable;
        final long createdAt;
        Summary(int schemaVersion, int versionCode, long createdAt, int transactions, int sources, int unreadable) {
            this.schemaVersion = schemaVersion; this.versionCode = versionCode; this.createdAt = createdAt;
            this.transactions = transactions; this.sources = sources; this.unreadable = unreadable;
        }
    }

    /** A decrypted, verified backup waiting for the user's confirmation; apply() swaps the data, discard() deletes it. */
    static final class Prepared {
        final Summary summary;
        private final File ledger;
        private final String settings;
        private final byte[] profile;
        private boolean done;
        Prepared(Summary summary, File ledger, String settings, byte[] profile) { this.summary = summary; this.ledger = ledger; this.settings = settings; this.profile = profile; }

        /**
         * Replaces everything: closes the shared database, swaps the file (the old one is kept as .restore-bak), reopens
         * it (onUpgrade handles an older schema), re-encrypts the source text with this device's key, restores settings
         * and picture, and only then drops the kept file, reschedules reminders and refreshes the widget. A failure at
         * any step before that point puts the old database back and reopens it, so the user's data is as it was; the
         * RestoreFailed thrown says whether that rollback itself succeeded (if not, the next app start repeats it).
         */
        ExpenseDb apply(Context context) throws RestoreFailed {
            if (done) throw new IllegalStateException("Already applied or discarded");
            done = true;
            try {
                try {
                    AppResources.closeDatabase();
                    ExpenseDb.replaceDatabase(context, ledger);
                } catch (IOException | RuntimeException failed) {
                    reopen(context);
                    throw new RestoreFailed(true, failed);
                }
                try {
                    ExpenseDb fresh = AppResources.database(context);
                    SQLiteDatabase live = fresh.getWritableDatabase();
                    live.beginTransaction();
                    try { EvidenceVault.encryptAll(live); live.setTransactionSuccessful(); } finally { live.endTransaction(); }
                    restoreSettings(context, settings);
                    File picture = new File(context.getFilesDir(), PROFILE_FILE);
                    if (profile != null) {
                        File temp = new File(context.getFilesDir(), PROFILE_FILE + ".tmp");
                        Files.write(temp.toPath(), profile);
                        Files.move(temp.toPath(), picture.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else if (picture.exists()) picture.delete();
                    ExpenseDb.finishRestore(context);
                    ReminderReceiver.schedule(context);
                    DailyRefreshReceiver.schedule(context);
                    MonthlySummaryReceiver.schedule(context);
                    SummaryWidget.refresh(context);
                    return fresh;
                } catch (IOException | RuntimeException failed) {
                    boolean rolledBack = false;
                    try { AppResources.closeDatabase(); ExpenseDb.rollbackRestore(context); rolledBack = true; }
                    catch (IOException | RuntimeException rollbackFailed) { failed.addSuppressed(rollbackFailed); }
                    reopen(context);
                    throw new RestoreFailed(rolledBack, failed);
                }
            } finally { discardFiles(); }
        }

        /** The activity keeps using the shared helper; after a failure it must point at an open one again. */
        private static void reopen(Context context) {
            try { AppResources.database(context); } catch (RuntimeException unopenable) { android.util.Log.e("FollowMoney", "Database did not reopen after a failed restore: " + unopenable.getClass().getSimpleName()); }
        }

        void discard() { done = true; discardFiles(); }
        private void discardFiles() { for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) { File f = new File(ledger.getPath() + suffix); if (f.exists()) f.delete(); } }
    }

    /**
     * Decrypts and verifies a backup (GCM tag first, then the ledger itself) into temporary files and reads its summary.
     * The password is wiped whatever happens. A Rejected result means nothing was written anywhere but the cache, and
     * even that is removed.
     */
    static Prepared prepare(Context context, byte[] file, char[] password) throws BackupVault.Rejected, IOException {
        Map<String, byte[]> entries;
        try { entries = BackupVault.open(password, file, ExpenseDb.SCHEMA_VERSION); }
        finally { BackupVault.wipe(password); }
        BackupVault.Header header = BackupVault.header(file);
        File ledger = File.createTempFile("restore-", ".db", context.getCacheDir());
        boolean ready = false;
        try {
            Files.write(ledger.toPath(), entries.get(BackupVault.LEDGER_ENTRY));
            int transactions, sources, unreadable = 0;
            SQLiteDatabase check = SQLiteDatabase.openDatabase(ledger.getPath(), null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                try (Cursor c = check.rawQuery("PRAGMA user_version", null)) { c.moveToFirst(); if (c.getInt(0) > ExpenseDb.SCHEMA_VERSION) throw new BackupVault.Rejected(BackupVault.Rejected.Reason.NEWER_APP_REQUIRED); }
                try (Cursor c = check.rawQuery("SELECT COUNT(*) FROM transactions", null)) { c.moveToFirst(); transactions = c.getInt(0); }
                try (Cursor c = check.rawQuery("SELECT COUNT(*) FROM payment_sources", null)) { c.moveToFirst(); sources = c.getInt(0); }
            } catch (android.database.SQLException notALedger) {
                throw new BackupVault.Rejected(BackupVault.Rejected.Reason.NOT_A_BACKUP);
            } finally { check.close(); }
            byte[] prefs = entries.get(BackupVault.PREFS_ENTRY);
            String settings = prefs == null ? "{}" : new String(prefs, StandardCharsets.UTF_8);
            try { new JSONObject(settings); } catch (JSONException malformed) { throw new BackupVault.Rejected(BackupVault.Rejected.Reason.NOT_A_BACKUP); }
            Prepared prepared = new Prepared(new Summary(header.schemaVersion, header.versionCode, header.createdAt, transactions, sources, unreadable),
                    ledger, settings, entries.get(BackupVault.PROFILE_ENTRY));
            ready = true;
            return prepared;
        } finally {
            if (!ready) for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) { File f = new File(ledger.getPath() + suffix); if (f.exists()) f.delete(); }
        }
    }

    // ---- settings as JSON: {"widget_settings":{"currency":{"t":"s","v":"EGP"}}} ----

    static String settingsJson(Context context) {
        JSONObject all = new JSONObject();
        try {
            for (String name : SETTINGS) {
                JSONObject group = new JSONObject();
                for (Map.Entry<String, ?> entry : context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                    Object value = entry.getValue();
                    JSONObject typed = new JSONObject();
                    if (value instanceof Boolean) typed.put("t", "b").put("v", value);
                    else if (value instanceof Integer) typed.put("t", "i").put("v", value);
                    else if (value instanceof Long) typed.put("t", "l").put("v", value);
                    else if (value instanceof Float) typed.put("t", "f").put("v", ((Float) value).doubleValue());
                    else if (value instanceof Set) { JSONArray items = new JSONArray(); for (Object item : (Set<?>) value) items.put(String.valueOf(item)); typed.put("t", "set").put("v", items); }
                    else if (value != null) typed.put("t", "s").put("v", String.valueOf(value));
                    else continue;
                    group.put(entry.getKey(), typed);
                }
                all.put(name, group);
            }
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return all.toString();
    }

    static void restoreSettings(Context context, String json) {
        try {
            JSONObject all = new JSONObject(json);
            for (String name : SETTINGS) {
                JSONObject group = all.optJSONObject(name);
                SharedPreferences.Editor editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
                if (group != null) for (Iterator<String> keys = group.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    JSONObject typed = group.getJSONObject(key);
                    switch (typed.getString("t")) {
                        case "b": editor.putBoolean(key, typed.getBoolean("v")); break;
                        case "i": editor.putInt(key, typed.getInt("v")); break;
                        case "l": editor.putLong(key, typed.getLong("v")); break;
                        case "f": editor.putFloat(key, (float) typed.getDouble("v")); break;
                        case "set": { Set<String> items = new LinkedHashSet<>(); JSONArray array = typed.getJSONArray("v"); for (int i = 0; i < array.length(); i++) items.add(array.getString(i)); editor.putStringSet(key, items); break; }
                        default: editor.putString(key, typed.getString("v"));
                    }
                }
                editor.apply(); // in-memory immediately for this process; the disk write follows
            }
        } catch (JSONException malformed) { throw new IllegalStateException("Settings in the backup are unreadable", malformed); }
    }
}
