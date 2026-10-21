package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owner-approved diagnostic (21 Oct 2026): masked shapes of one disagreement group, read on the phone. Returns only
 * distinct masked shapes with counts and the new pipeline's reasons: digits, dates, links and references hidden by
 * SmsPreview.shape, then every Latin word and every merchant run after a merchant marker hidden as <name>. For
 * balance_as_expense it also reports, without values, the masked shape of the same sender's previous message, the gap
 * and whether an amount repeats. Writes nothing. Requires shape_report=owner_phone, shape_sender and shape_group:
 * missing_amount, balance_as_expense, no_event, no_direction, two_amount_income.
 */
final class ShadowShapeAudit {
    private final Instrumentation runner;
    private final String consent, sender, group;
    ShadowShapeAudit(Instrumentation runner, String consent, String sender, String group) {
        this.runner = runner; this.consent = consent; this.sender = sender; this.group = group == null ? "missing_amount" : group;
    }

    /** Common English template words stay so an all-Latin form is readable; every other Latin word is a name. */
    static final String TEMPLATE = "egp|usd|eur|le|pounds?|piasters?|slot|dear|customer|valued|your|you|our|we|has|have|had|been|is|are|was|were|be|will|the|a|an|of|for|to|from|with|by|on|at|in|and|or|not|no|" +
            "successfully|successful|renewed|renewal|subscribed|subscription|package|bundle|plan|recharged?|charged|deducted|debited|credited|paid|payment|balance|" +
            "remaining|available|amount|fees?|price|cost|monthly|daily|weekly|valid|validity|until|till|expires?|expiry|date|days?|month|mb|gb|minutes?|units?|" +
            "thank|thanks|please|call|dial|visit|more|info|details|account|number|card|bill|invoice|due|total|new|current|request|requested|activated|cancel|stop";

    static String masked(String text) {
        String shape = SmsPreview.shape(text);
        shape = shape.replaceAll("(?iu)\\b(?!(?:" + TEMPLATE + ")\\b)[a-z][a-z&'*.\\-]*", "<name>");
        shape = shape.replaceAll("(?u)((?:^|\\s)(?:عند|لدي|لدى|في|فى)\\s*)(?!<slot>)[^،,.\\s<]+(?:\\s+(?!يوم|بتاريخ|الساعه|الساعة|بقيمة|بمبلغ)[^،,.\\s<]+)*", "$1<name>");
        shape = shape.replaceAll("(?:<name>[\\s&*'.\\-]*){2,}", "<name> ").replaceAll("\\s+", " ").trim();
        return shape;
    }

    private static final class Message {
        String body; long date, sent; SmsPreview preview; ConfidenceScorer.Confidence score; TransactionExtractor.Extraction x; List<Long> amounts = new ArrayList<>();
    }

    private boolean selected(Message m) {
        if (m.preview == null) return group.equals("fee_refund") && ShadowDiff.newRecords(m.score.decision) && "in".equals(m.x.direction);
        String kind = m.preview.kind, decision = m.score.decision;
        switch (group) {
            case "missing_amount": return m.preview.isTransaction() && "UNUNDERSTOOD".equals(decision) && m.score.reasons.contains("MISSING_AMOUNT");
            case "balance_as_expense": return "balance".equals(kind) && ShadowDiff.newRecords(decision);
            case "no_event": return m.preview.isTransaction() && "IGNORE".equals(decision) && m.score.reasons.contains("NO_COMPLETED_EVENT");
            case "no_direction": return m.preview.isTransaction() && "UNUNDERSTOOD".equals(decision) && m.score.reasons.contains("AMBIGUOUS_DIRECTION");
            case "two_amount_income": return "income".equals(kind) && m.score.reasons.contains("UNASSIGNED_SECOND_AMOUNT");
            case "commitment_recorded": return "commitment".equals(kind) && ShadowDiff.newRecords(decision);
            case "fee_refund": return !m.preview.isTransaction() && ShadowDiff.newRecords(decision) && "in".equals(m.x.direction);
            default: throw new IllegalArgumentException("Unknown shape_group " + group);
        }
    }

    void run() {
        Bundle result = new Bundle(); int status = -1;
        Context target = runner.getTargetContext();
        try {
            if (!"owner_phone".equals(consent) || sender == null) throw new SecurityException("shape_report=owner_phone and shape_sender are required");
            List<Message> all = new ArrayList<>();
            try (Cursor c = target.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI, new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT},
                    Telephony.Sms.ADDRESS + "=?", new String[]{sender}, Telephony.Sms.DATE + " ASC")) {
                while (c != null && c.moveToNext()) {
                    Message m = new Message(); m.body = c.getString(1); m.date = c.getLong(2); m.sent = c.getLong(3);
                    if (m.body == null) continue;
                    m.preview = SmsPreview.parse(c.getString(0), m.body, m.date, m.sent);
                    MessageNormalizer.Normalized n = MessageNormalizer.normalize(MessageNormalizer.Raw.sms(c.getString(0), m.body));
                    MessageClassifier.Result k = MessageClassifier.classify(n);
                    m.x = TransactionExtractor.extract(n, k, m.date);
                    m.score = ConfidenceScorer.score(k, m.x);
                    for (AmountScanner.Money money : AmountScanner.scan(n.matchText)) m.amounts.add(money.minor);
                    all.add(m);
                }
            }
            Map<String, int[]> shapes = new TreeMap<>();
            Map<String, String> notes = new TreeMap<>();
            int selected = 0;
            for (int i = 0; i < all.size(); i++) {
                Message m = all.get(i);
                if (!selected(m)) continue;
                selected++;
                String key = masked(m.body);
                StringBuilder note = new StringBuilder(String.join(",", m.score.reasons)).append(" | amounts scanned: ").append(m.amounts.size())
                        .append(" | new amount is scanned #").append(m.amounts.indexOf(m.x.amountMinor) + 1);
                if (group.equals("two_amount_income") && m.preview != null)
                    note.append(" | legacy amount is scanned #").append(m.amounts.indexOf(m.preview.amountMinor) + 1);
                if (group.equals("balance_as_expense")) {
                    Message before = i > 0 ? all.get(i - 1) : null;
                    long gap = before == null ? -1 : (m.date - before.date) / 60_000;
                    boolean repeats = false;
                    if (before != null) for (Long a : m.amounts) repeats |= before.amounts.contains(a);
                    key += "\n    previous (" + (gap < 0 ? "none" : gap <= 2 ? "0-2 min" : gap <= 10 ? "3-10 min" : gap <= 60 ? "11-60 min" : "> 60 min") +
                            ", amount repeats: " + repeats + ", previous legacy " + (before == null || before.preview == null ? "none" : before.preview.kind) +
                            ", previous new " + (before == null ? "none" : before.score.decision) + "): " + (before == null ? "-" : masked(before.body));
                }
                shapes.computeIfAbsent(key, k -> new int[1])[0]++;
                notes.put(key, note.toString());
            }
            StringBuilder out = new StringBuilder("Masked shapes, group " + group + ", sender " + sender + ": scanned " + all.size() + ", selected " + selected + ", distinct " + shapes.size() + "\n");
            for (Map.Entry<String, int[]> e : shapes.entrySet())
                out.append("[").append(e.getValue()[0]).append("] ").append(e.getKey()).append("\n    ").append(notes.get(e.getKey())).append("\n");
            result.putString("stream", out.toString());
        } catch (Throwable failure) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Shape audit FAILED: " + trace);
        } finally {
            runner.finish(status, result);
        }
    }
}
