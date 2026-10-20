package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** The encrypted backup file: what opens it, what it refuses, and that nothing leaks past a refusal. */
public final class BackupVaultTest {
    private static int checked;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }

    private static Map<String, byte[]> sample(SecureRandom random) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] ledger = new byte[70_000]; random.nextBytes(ledger);
        entries.put(BackupVault.LEDGER_ENTRY, ledger);
        entries.put(BackupVault.PREFS_ENTRY, "{\"widget_settings\":{\"currency\":\"EGP\"}}".getBytes(StandardCharsets.UTF_8));
        entries.put(BackupVault.PROFILE_ENTRY, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        return entries;
    }

    private static BackupVault.Rejected.Reason refused(char[] password, byte[] file, int supportedSchema) {
        try { BackupVault.open(password, file, supportedSchema); return null; }
        catch (BackupVault.Rejected rejected) { return rejected.reason; }
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        Map<String, byte[]> entries = sample(random);
        long created = 1_760_000_000_000L;
        byte[] file = BackupVault.seal("correct horse".toCharArray(), 25, 4, created, entries, random);
        check(file.length > 70_000 && new String(file, 0, 4, StandardCharsets.US_ASCII).equals(BackupVault.MAGIC), "file starts with the magic and carries the payload");
        BackupVault.Header header = BackupVault.header(file);
        check(header.formatVersion == BackupVault.FORMAT_VERSION && header.schemaVersion == 25 && header.versionCode == 4 && header.createdAt == created, "header fields round-trip");
        check(header.salt.length == BackupVault.SALT_BYTES && header.iv.length == BackupVault.IV_BYTES && header.iterations == BackupVault.ITERATIONS, "salt, IV and iteration count are stated in the header");

        // 1) Encrypt then decrypt returns exactly the same bytes for every entry.
        Map<String, byte[]> opened = BackupVault.open("correct horse".toCharArray(), file, 25);
        check(opened.keySet().equals(entries.keySet()), "every entry comes back: " + opened.keySet());
        for (Map.Entry<String, byte[]> e : entries.entrySet()) check(Arrays.equals(e.getValue(), opened.get(e.getKey())), "entry bytes identical: " + e.getKey());
        // The same content sealed twice never produces the same file (fresh salt and IV).
        byte[] again = BackupVault.seal("correct horse".toCharArray(), 25, 4, created, entries, random);
        check(!Arrays.equals(file, again) && BackupVault.open("correct horse".toCharArray(), again, 25).size() == 3, "fresh salt and IV per backup");

        // 2) A wrong password is refused with a clear reason and yields nothing.
        check(refused("wrong password".toCharArray(), file, 25) == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "wrong password refused");
        // 3) A damaged file (one flipped byte in the body) is refused the same way; the GCM tag protects every byte.
        byte[] damaged = file.clone(); damaged[damaged.length - 20] ^= 0x01;
        check(refused("correct horse".toCharArray(), damaged, 25) == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "flipped body byte refused");
        // A tampered header (schema version edited to look older) is refused too: the header is authenticated.
        byte[] tamperedHeader = file.clone();
        int schemaOffset = 4 + 4; // magic, formatVersion
        tamperedHeader[schemaOffset + 3] = 20;
        check(BackupVault.header(tamperedHeader).schemaVersion == 20, "header parses the edited schema version");
        check(refused("correct horse".toCharArray(), tamperedHeader, 25) == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "edited header refused");
        // 4) A backup from a newer app (schema 99) is refused with «حدّث التطبيق أولًا» before any password work.
        byte[] newer = BackupVault.seal("correct horse".toCharArray(), 99, 9, created, entries, random);
        check(refused("correct horse".toCharArray(), newer, 25) == BackupVault.Rejected.Reason.NEWER_APP_REQUIRED, "newer schema refused");
        check(BackupVault.Rejected.message(BackupVault.Rejected.Reason.NEWER_APP_REQUIRED).contains("حدّث التطبيق أولًا"), "newer schema message asks to update the app");
        // An older schema is accepted (onUpgrade handles it on the device).
        byte[] older = BackupVault.seal("correct horse".toCharArray(), 20, 2, created, entries, random);
        check(BackupVault.open("correct horse".toCharArray(), older, 25).size() == 3, "older schema accepted");
        // Not a backup at all.
        byte[] noise = new byte[300]; random.nextBytes(noise); noise[0] = 'X';
        check(refused("correct horse".toCharArray(), noise, 25) == BackupVault.Rejected.Reason.NOT_A_BACKUP, "random bytes are not a backup");
        check(refused("correct horse".toCharArray(), new byte[3], 25) == BackupVault.Rejected.Reason.NOT_A_BACKUP, "a truncated file is not a backup");
        byte[] futureFormat = file.clone(); futureFormat[4 + 3] = 2;
        check(refused("correct horse".toCharArray(), futureFormat, 25) == BackupVault.Rejected.Reason.UNSUPPORTED_FORMAT, "a newer file format is refused before decryption");
        // 5) The iteration count is bounded: a forged header claiming two billion rounds is refused before any key
        // derivation (so it returns at once), and so is a count too small to protect the password.
        int iterationsOffset = 4 + 4 + 4 + 4 + 8; // magic, formatVersion, schemaVersion, versionCode, createdAt
        byte[] slow = file.clone(); java.nio.ByteBuffer.wrap(slow).putInt(iterationsOffset, 2_000_000_000);
        long started = System.nanoTime();
        check(refused("correct horse".toCharArray(), slow, 25) == BackupVault.Rejected.Reason.UNSUPPORTED_FORMAT, "two billion rounds refused");
        check(System.nanoTime() - started < 1_000_000_000L, "the refusal came before any key derivation");
        byte[] weak = file.clone(); java.nio.ByteBuffer.wrap(weak).putInt(iterationsOffset, BackupVault.MIN_ITERATIONS - 1);
        check(refused("correct horse".toCharArray(), weak, 25) == BackupVault.Rejected.Reason.UNSUPPORTED_FORMAT, "too few rounds refused");
        // The bounds themselves pass the header check; the edited header then fails the tag like any other tampering.
        byte[] atMin = file.clone(); java.nio.ByteBuffer.wrap(atMin).putInt(iterationsOffset, BackupVault.MIN_ITERATIONS);
        byte[] atMax = file.clone(); java.nio.ByteBuffer.wrap(atMax).putInt(iterationsOffset, BackupVault.MAX_ITERATIONS);
        check(BackupVault.header(atMin).iterations == BackupVault.MIN_ITERATIONS && BackupVault.header(atMax).iterations == BackupVault.MAX_ITERATIONS, "the bounds are inclusive");
        check(refused("correct horse".toCharArray(), atMin, 25) == BackupVault.Rejected.Reason.WRONG_PASSWORD_OR_DAMAGED, "an edited iteration count still fails the tag");
        // 6) A file far larger than any backup is refused before it is decrypted in memory.
        byte[] huge = new byte[BackupVault.MAX_FILE_BYTES + 1]; System.arraycopy(file, 0, huge, 0, 64);
        check(refused("correct horse".toCharArray(), huge, 25) == BackupVault.Rejected.Reason.TOO_LARGE, "an oversized file is refused");
        check(BackupVault.Rejected.message(BackupVault.Rejected.Reason.TOO_LARGE).contains("200"), "the size message names the limit");

        // Passwords: at least eight characters; the caller's array is wiped on request.
        try { BackupVault.seal("short".toCharArray(), 25, 4, created, entries, random); check(false, "short password accepted"); }
        catch (IllegalArgumentException expected) { checked++; }
        char[] secret = "correct horse".toCharArray(); BackupVault.wipe(secret);
        check(new String(secret).trim().isEmpty() && secret.length == 13, "wipe clears the characters in place");
        check(BackupVault.fileName(LocalDate.of(2026, 10, 20)).equals("followmoney-2026-10-20.fmbackup"), "file name carries the date and extension");
        System.out.println("Backup vault checks passed: " + checked);
    }
}
