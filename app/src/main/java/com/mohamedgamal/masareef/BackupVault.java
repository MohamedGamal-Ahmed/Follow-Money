package com.mohamedgamal.masareef;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The local backup file (.fmbackup): a small authenticated header followed by a ZIP of the ledger, the settings and
 * the optional profile picture, encrypted with AES-256-GCM under a key derived from the user's password
 * (PBKDF2-HMAC-SHA256, 200,000 rounds, random salt). The header is the GCM additional data, so an edited schema
 * version or version code is detected like any flipped byte. Pure Java: no android.* import, so it runs on the JVM.
 */
final class BackupVault {
    static final String MAGIC = "FMBK";
    static final int FORMAT_VERSION = 1;
    static final String EXTENSION = ".fmbackup";
    static final int ITERATIONS = 200_000;
    /** A file may state its own round count, within these bounds: fewer rounds weaken the password, more would let a forged header hang the app for minutes. */
    static final int MIN_ITERATIONS = 100_000;
    static final int MAX_ITERATIONS = 1_000_000;
    /** A ledger with its messages is a few megabytes; anything past this is refused before it is decrypted in memory. */
    static final int MAX_FILE_BYTES = 200 * 1024 * 1024;
    static final int SALT_BYTES = 16;
    static final int IV_BYTES = 12;
    static final int KEY_BITS = 256;
    static final int TAG_BITS = 128;
    static final int MIN_PASSWORD = 8;
    static final String LEDGER_ENTRY = "ledger.db";
    static final String PREFS_ENTRY = "prefs.json";
    static final String PROFILE_ENTRY = "profile.png";
    /** Header fields never exceed this; anything claiming more is not a backup. */
    private static final int MAX_HEADER = 4 + 4 + 4 + 4 + 8 + 4 + 1 + 64 + 1 + 64;

    private BackupVault() {}

    static final class Header {
        final int formatVersion, schemaVersion, versionCode, iterations;
        final long createdAt;
        final byte[] salt, iv;
        /** Where the ciphertext starts in the file. */
        final int bodyOffset;
        Header(int formatVersion, int schemaVersion, int versionCode, long createdAt, int iterations, byte[] salt, byte[] iv, int bodyOffset) {
            this.formatVersion = formatVersion; this.schemaVersion = schemaVersion; this.versionCode = versionCode;
            this.createdAt = createdAt; this.iterations = iterations; this.salt = salt; this.iv = iv; this.bodyOffset = bodyOffset;
        }
    }

    /** Why a file was not opened. The reason is the whole message to the user; no partial data is ever returned. */
    static final class Rejected extends Exception {
        enum Reason { NOT_A_BACKUP, TOO_LARGE, UNSUPPORTED_FORMAT, NEWER_APP_REQUIRED, WRONG_PASSWORD_OR_DAMAGED }
        final Reason reason;
        Rejected(Reason reason) { super(message(reason)); this.reason = reason; }
        static String message(Reason reason) {
            switch (reason) {
                case NOT_A_BACKUP: return "الملف ده مش نسخة احتياطية من Follow Money.";
                case TOO_LARGE: return "الملف أكبر من 200 ميجابايت، وده أكبر بكتير من أي نسخة احتياطية. ما اتغيرش أي حاجة في بياناتك.";
                case UNSUPPORTED_FORMAT: return "صيغة النسخة مش مدعومة في التطبيق ده. حدّث التطبيق أولًا.";
                case NEWER_APP_REQUIRED: return "النسخة دي من إصدار أحدث من التطبيق. حدّث التطبيق أولًا ثم أعد الاستعادة.";
                default: return "كلمة السر غلط أو الملف تالف. ما اتغيرش أي حاجة في بياناتك.";
            }
        }
    }

    static String fileName(LocalDate date) { return "followmoney-" + date + EXTENSION; }

    /** Clears a password the caller no longer needs. */
    static void wipe(char[] password) { if (password != null) Arrays.fill(password, '\0'); }

    /**
     * Encrypts the entries into one backup file. The password must have at least eight characters; the key material
     * derived from it is cleared before returning, the caller clears the password itself (wipe).
     */
    static byte[] seal(char[] password, int schemaVersion, int versionCode, long createdAt, Map<String, byte[]> entries, SecureRandom random)
            throws GeneralSecurityException, IOException {
        if (password == null || password.length < MIN_PASSWORD) throw new IllegalArgumentException("Password must have at least " + MIN_PASSWORD + " characters");
        if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("Nothing to back up");
        byte[] salt = new byte[SALT_BYTES], iv = new byte[IV_BYTES];
        random.nextBytes(salt); random.nextBytes(iv);
        byte[] header = header(FORMAT_VERSION, schemaVersion, versionCode, createdAt, ITERATIONS, salt, iv);
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        SecretKey key = derive(password, salt, ITERATIONS);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(header);
            byte[] body = cipher.doFinal(zipped.toByteArray());
            ByteArrayOutputStream out = new ByteArrayOutputStream(header.length + body.length);
            out.write(header); out.write(body);
            return out.toByteArray();
        } finally { destroy(key); }
    }

    /** Reads the header only; never touches the password. */
    static Header header(byte[] file) throws Rejected {
        if (file == null || file.length < 4 + 4) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
        if (!MAGIC.equals(new String(file, 0, 4, StandardCharsets.US_ASCII))) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
        if (file.length > MAX_FILE_BYTES) throw new Rejected(Rejected.Reason.TOO_LARGE);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file))) {
            in.skipBytes(4);
            int formatVersion = in.readInt();
            if (formatVersion != FORMAT_VERSION) throw new Rejected(Rejected.Reason.UNSUPPORTED_FORMAT);
            int schemaVersion = in.readInt();
            int versionCode = in.readInt();
            long createdAt = in.readLong();
            int iterations = in.readInt();
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) throw new Rejected(Rejected.Reason.UNSUPPORTED_FORMAT);
            int saltLength = in.readUnsignedByte();
            if (saltLength == 0 || saltLength > 64) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
            byte[] salt = new byte[saltLength]; in.readFully(salt);
            int ivLength = in.readUnsignedByte();
            if (ivLength == 0 || ivLength > 64) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
            byte[] iv = new byte[ivLength]; in.readFully(iv);
            int bodyOffset = 4 + 4 + 4 + 4 + 8 + 4 + 1 + saltLength + 1 + ivLength;
            if (bodyOffset > MAX_HEADER || file.length <= bodyOffset + TAG_BITS / 8) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
            return new Header(formatVersion, schemaVersion, versionCode, createdAt, iterations, salt, iv, bodyOffset);
        } catch (EOFException truncated) {
            throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
        } catch (IOException unexpected) {
            throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
        }
    }

    /**
     * Decrypts and unpacks a backup. The schema check comes first, so a file from a newer app is refused before any
     * password work; a wrong password or a damaged byte fails the GCM tag and nothing is returned.
     */
    static Map<String, byte[]> open(char[] password, byte[] file, int supportedSchema) throws Rejected {
        Header header = header(file);
        if (header.schemaVersion > supportedSchema) throw new Rejected(Rejected.Reason.NEWER_APP_REQUIRED);
        if (password == null || password.length == 0) throw new Rejected(Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED);
        SecretKey key = null;
        try {
            key = derive(password, header.salt, header.iterations);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, header.iv));
            cipher.updateAAD(Arrays.copyOfRange(file, 0, header.bodyOffset));
            byte[] zipped = cipher.doFinal(file, header.bodyOffset, file.length - header.bodyOffset);
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipped))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16 * 1024];
                    for (int read; (read = zip.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
                    entries.put(entry.getName(), bytes.toByteArray());
                    zip.closeEntry();
                }
            }
            if (!entries.containsKey(LEDGER_ENTRY)) throw new Rejected(Rejected.Reason.NOT_A_BACKUP);
            return entries;
        } catch (AEADBadTagException badTag) {
            throw new Rejected(Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED);
        } catch (GeneralSecurityException | IOException | RuntimeException failed) {
            throw new Rejected(Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED);
        } finally { destroy(key); }
    }

    private static byte[] header(int formatVersion, int schemaVersion, int versionCode, long createdAt, int iterations, byte[] salt, byte[] iv) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
            out.writeInt(formatVersion); out.writeInt(schemaVersion); out.writeInt(versionCode);
            out.writeLong(createdAt); out.writeInt(iterations);
            out.writeByte(salt.length); out.write(salt);
            out.writeByte(iv.length); out.write(iv);
        }
        return bytes.toByteArray();
    }

    private static SecretKey derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            byte[] material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            SecretKey key = new SecretKeySpec(material, "AES");
            Arrays.fill(material, (byte) 0);
            return key;
        } finally { spec.clearPassword(); }
    }

    private static void destroy(SecretKey key) {
        if (key == null) return;
        try { key.destroy(); } catch (Exception ignored) { /* SecretKeySpec may not support destroy(); the array copy above is already cleared */ }
    }
}
