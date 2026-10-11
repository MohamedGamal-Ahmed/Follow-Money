package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fingerprints evidence without retaining message text or bank references. */
final class CaptureIdentity {
    static long smsTimestamp(long receivedAt, long sentAt) {
        return sentAt > 0 ? sentAt : receivedAt;
    }

    static String smsKey(String sender, String body, long when) {
        return hash(sender + "\n" + when + "\n" + body);
    }

    static boolean receiptKeySafe(String sender, String body, long receivedAt, long sentAt, java.util.Set<String> sentKeys) {
        return receivedAt==smsTimestamp(receivedAt,sentAt) || !sentKeys.contains(smsKey(sender,body,receivedAt));
    }

    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)(?:with\\s+reference|reference(?:\\s+(?:number|no))?|ref(?:erence)?\\.?|رقم\\s*(?:المرجع|مرجعي|العملية))\\s*[:#-]?\\s*([A-Za-z0-9-]{5,})");

    static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static String normalized(String sender, String body) {
        String bank = SourceIdentity.bank(sender);
        String text = ExpenseParser.normalizeDigits(body).replaceAll("[\\s\\p{Z}]+", " ").trim();
        return hash((bank == null ? sender : bank) + "\n" + text);
    }

    static String reference(String sender, String body) {
        Matcher match = REFERENCE.matcher(ExpenseParser.normalizeDigits(body));
        String bank=SourceIdentity.bank(sender);
        return match.find() ? hash((bank==null?sender:bank) + ":" + match.group(1).toLowerCase(Locale.ROOT)) : null;
    }

    static boolean isTruncatedHash(String sender, String body, String legacyHash) {
        // Legacy diagnostic captures could end mid-footer. Match an exact prefix checksum,
        // including the complete reference, rather than assuming amount/time proves duplication.
        if(legacyHash==null) return false;
        Matcher reference=REFERENCE.matcher(body);
        if(!reference.find()) return false;
        for(int end=reference.end();end<body.length();end++)
            if(hash(sender+"\n"+body.substring(0,end).trim()).equals(legacyHash)) return true;
        return false;
    }
}
