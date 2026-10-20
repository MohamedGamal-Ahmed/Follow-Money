package com.mohamedgamal.masareef;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Financial source text is encrypted locally with a device Keystore key. */
final class EvidenceVault {
    private static final String ALIAS = "follow_money_source_evidence_v1";
    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static String encrypt(String text) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Exception error) { return null; }
    }
    /** What a stored body turned out to be: readable text, or evidence this device can no longer open. */
    static final class Read {
        static final Read NONE = new Read(null, false);
        final String body;
        final boolean failed;
        Read(String body, boolean failed) { this.body = body; this.failed = failed; }
    }

    /** Opens one stored body, telling "nothing was stored" apart from "stored but unreadable". */
    static Read read(String text) {
        if (text == null) return Read.NONE;
        try {
            String[] parts = text.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new Read(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8), false);
        } catch (Exception error) {
            return new Read(null, true);
        }
    }

    static String decrypt(String text) {
        return read(text).body;
    }

    /** Every table whose encrypted_body column holds Keystore-encrypted source text; all have a rowid. */
    static final String[] ENCRYPTED_TABLES = {"capture_evidence", "capture_events", "commitment_payments", "commitment_suggestions", "commitment_observations"};

    /**
     * Backup: replaces every stored ciphertext inside a snapshot copy of the database with its plain text, because the
     * Keystore key never leaves this device. Text this device can no longer open becomes NULL and is counted.
     * Only ever called on a temporary snapshot, never on the live database.
     */
    static int decryptAll(android.database.sqlite.SQLiteDatabase snapshot) {
        int unreadable = 0;
        for (String table : ENCRYPTED_TABLES) {
            if (!hasTable(snapshot, table)) continue;
            java.util.List<Object[]> updates = new java.util.ArrayList<>();
            try (android.database.Cursor c = snapshot.rawQuery("SELECT rowid, encrypted_body FROM " + table + " WHERE encrypted_body IS NOT NULL", null)) {
                while (c.moveToNext()) {
                    Read body = read(c.getString(1));
                    if (body.failed) unreadable++;
                    updates.add(new Object[]{body.body, c.getLong(0)});
                }
            }
            for (Object[] update : updates) snapshot.execSQL("UPDATE " + table + " SET encrypted_body=? WHERE rowid=?", update);
        }
        if (hasTable(snapshot, "transaction_archive")) {
            java.util.List<Object[]> updates = new java.util.ArrayList<>();
            try (android.database.Cursor c = snapshot.rawQuery("SELECT id, payload FROM transaction_archive WHERE payload LIKE '%encrypted_body%'", null)) {
                while (c.moveToNext()) {
                    int[] failed = new int[1];
                    String rewritten = rewriteArchive(c.getString(1), true, failed);
                    unreadable += failed[0];
                    if (rewritten != null) updates.add(new Object[]{rewritten, c.getLong(0)});
                }
            }
            for (Object[] update : updates) snapshot.execSQL("UPDATE transaction_archive SET payload=? WHERE id=?", update);
        }
        return unreadable;
    }

    /** Restore: encrypts every plain-text body of a freshly restored database with this device's Keystore key. Runs once. */
    static int encryptAll(android.database.sqlite.SQLiteDatabase live) {
        int encrypted = 0;
        for (String table : ENCRYPTED_TABLES) {
            if (!hasTable(live, table)) continue;
            java.util.List<Object[]> updates = new java.util.ArrayList<>();
            try (android.database.Cursor c = live.rawQuery("SELECT rowid, encrypted_body FROM " + table + " WHERE encrypted_body IS NOT NULL", null)) {
                while (c.moveToNext()) {
                    String sealed = encrypt(c.getString(1));
                    if (sealed == null) throw new IllegalStateException("Source text could not be encrypted on this device");
                    updates.add(new Object[]{sealed, c.getLong(0)});
                }
            }
            for (Object[] update : updates) { live.execSQL("UPDATE " + table + " SET encrypted_body=? WHERE rowid=?", update); encrypted++; }
        }
        if (hasTable(live, "transaction_archive")) {
            java.util.List<Object[]> updates = new java.util.ArrayList<>();
            try (android.database.Cursor c = live.rawQuery("SELECT id, payload FROM transaction_archive WHERE payload LIKE '%encrypted_body%'", null)) {
                while (c.moveToNext()) {
                    String rewritten = rewriteArchive(c.getString(1), false, new int[1]);
                    if (rewritten != null) updates.add(new Object[]{rewritten, c.getLong(0)});
                }
            }
            for (Object[] update : updates) { live.execSQL("UPDATE transaction_archive SET payload=? WHERE id=?", update); encrypted++; }
        }
        return encrypted;
    }

    /** An archived row keeps its evidence inside a JSON payload; its bodies follow the same rule as the tables. */
    private static String rewriteArchive(String payload, boolean toPlain, int[] failed) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            org.json.JSONArray evidence = json.optJSONArray("evidence");
            if (evidence == null) return null;
            boolean changed = false;
            for (int i = 0; i < evidence.length(); i++) {
                org.json.JSONObject row = evidence.getJSONObject(i);
                if (row.isNull("encrypted_body")) continue;
                String stored = row.getString("encrypted_body");
                if (toPlain) {
                    Read body = read(stored);
                    if (body.failed) failed[0]++;
                    row.put("encrypted_body", body.body == null ? org.json.JSONObject.NULL : body.body);
                } else {
                    String sealed = encrypt(stored);
                    if (sealed == null) throw new IllegalStateException("Archived source text could not be encrypted on this device");
                    row.put("encrypted_body", sealed);
                }
                changed = true;
            }
            return changed ? json.toString() : null;
        } catch (org.json.JSONException malformed) {
            return null; // an unreadable payload is left exactly as it is
        }
    }

    private static boolean hasTable(android.database.sqlite.SQLiteDatabase db, String table) {
        try (android.database.Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table})) { return c.moveToFirst(); }
    }
}
