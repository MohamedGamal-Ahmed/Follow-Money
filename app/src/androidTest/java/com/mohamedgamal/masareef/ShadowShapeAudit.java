package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owner-approved diagnostic (21 Oct 2026): masked shapes of one disagreement group, read on the phone. Selects inbox
 * SMS from one sender that legacy records as a transaction while the new pipeline is UNUNDERSTOOD with MISSING_AMOUNT,
 * and returns only distinct masked shapes with counts and reasons: digits, dates, links and references hidden by
 * SmsPreview.shape, then every Latin word and every merchant run after a merchant marker hidden as <name>. Writes nothing.
 * Requires shape_report=owner_phone and shape_sender=<address>.
 */
final class ShadowShapeAudit {
    private final Instrumentation runner;
    private final String consent, sender;
    ShadowShapeAudit(Instrumentation runner, String consent, String sender) { this.runner = runner; this.consent = consent; this.sender = sender; }

    static String masked(String text) {
        String shape = SmsPreview.shape(text);
        shape = shape.replaceAll("(?iu)\\b(?!(?:egp|usd|eur|slot)\\b)[a-z][a-z&'*.\\-]*", "<name>");
        shape = shape.replaceAll("(?u)((?:^|\\s)(?:عند|لدي|لدى|في|فى)\\s*)(?!<slot>)[^،,.\\s<]+(?:\\s+(?!يوم|بتاريخ|الساعه|الساعة|بقيمة|بمبلغ)[^،,.\\s<]+)*", "$1<name>");
        shape = shape.replaceAll("(?:<name>[\\s&*'.\\-]*){2,}", "<name> ").replaceAll("\\s+", " ").trim();
        return shape;
    }

    void run() {
        Bundle result = new Bundle(); int status = -1;
        Context target = runner.getTargetContext();
        try {
            if (!"owner_phone".equals(consent) || sender == null) throw new SecurityException("shape_report=owner_phone and shape_sender are required");
            Map<String, int[]> shapes = new TreeMap<>();
            Map<String, String> reasonsOf = new TreeMap<>();
            int scanned = 0, selected = 0;
            long received = System.currentTimeMillis();
            try (Cursor c = target.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI, new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT},
                    Telephony.Sms.ADDRESS + "=?", new String[]{sender}, Telephony.Sms.DATE + " ASC")) {
                while (c != null && c.moveToNext()) {
                    scanned++;
                    String body = c.getString(1);
                    SmsPreview preview = SmsPreview.parse(c.getString(0), body, c.getLong(2), c.getLong(3));
                    if (preview == null || !preview.isTransaction()) continue;
                    MessageNormalizer.Normalized n = MessageNormalizer.normalize(MessageNormalizer.Raw.sms(c.getString(0), body));
                    MessageClassifier.Result k = MessageClassifier.classify(n);
                    TransactionExtractor.Extraction x = TransactionExtractor.extract(n, k, c.getLong(2));
                    ConfidenceScorer.Confidence s = ConfidenceScorer.score(k, x);
                    if (!"UNUNDERSTOOD".equals(s.decision) || !s.reasons.contains("MISSING_AMOUNT")) continue;
                    selected++;
                    String shape = masked(body);
                    shapes.computeIfAbsent(shape, key -> new int[1])[0]++;
                    reasonsOf.put(shape, String.join(",", s.reasons) + " | amounts scanned: " + AmountScanner.scan(n.matchText).size());
                }
            }
            StringBuilder out = new StringBuilder("Masked shapes for sender " + sender + ": scanned " + scanned + ", selected " + selected + ", distinct " + shapes.size() + "\n");
            for (Map.Entry<String, int[]> e : shapes.entrySet())
                out.append("[").append(e.getValue()[0]).append("] ").append(e.getKey()).append("\n    ").append(reasonsOf.get(e.getKey())).append("\n");
            result.putString("stream", out.toString());
        } catch (Throwable failure) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Shape audit FAILED: " + trace);
        } finally {
            runner.finish(status, result);
        }
    }
}
