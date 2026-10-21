package com.mohamedgamal.masareef;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pipeline stage 1: turns one raw SMS, or the six separate extras of one notification, into normalized text.
 * It decides nothing about meaning: no classification, amounts, merchants or acceptance. Shadow mode only.
 */
final class MessageNormalizer {
    enum Channel { SMS, NOTIFICATION }

    /** Raw input exactly as delivered. Null means the extra was absent; "" means it was present and empty. */
    static final class Raw {
        final Channel channel;
        final String source, body;
        final String[] extras;
        final int flags;

        private Raw(Channel channel, String source, String body, String[] extras, int flags) {
            this.channel = channel; this.source = source; this.body = body; this.extras = extras; this.flags = flags;
        }

        static Raw sms(String source, String body) {
            return new Raw(Channel.SMS, source, body, null, 0);
        }

        /** Extras in ParsingPolicy.NOTIFICATION_FIELDS order; textLines is already joined with '\n'. */
        static Raw notification(String source, int flags, String title, String text, String bigText,
                                String textLines, String subText, String summaryText) {
            return new Raw(Channel.NOTIFICATION, source, null,
                    new String[]{title, text, bigText, textLines, subText, summaryText}, flags);
        }
    }

    static final class Normalized {
        /** Display text: unified digits/spaces/controls, original letters and line breaks kept. */
        final String text;
        /** Matching text: text with letters folded (case, diacritics, alef/ya/ta marbuta forms), one line. */
        final String matchText;
        final List<String> fragments, fields;
        final Set<String> reasons;
        final boolean groupSummary;

        Normalized(String text, List<String> fragments, List<String> fields, Set<String> reasons, boolean groupSummary) {
            this.text = text; this.matchText = fold(text).replace('\n', ' ');
            this.fragments = Collections.unmodifiableList(fragments); this.fields = Collections.unmodifiableList(fields);
            this.reasons = Collections.unmodifiableSet(reasons); this.groupSummary = groupSummary;
        }

        boolean empty() { return text.isEmpty(); }
    }

    static Normalized normalize(Raw raw) {
        Set<String> reasons = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>(), fields = new ArrayList<>(), keys = new ArrayList<>();
        boolean summary = raw.channel == Channel.NOTIFICATION && (raw.flags & ParsingPolicy.FLAG_GROUP_SUMMARY) != 0;
        if (summary) reasons.add("GROUP_SUMMARY");
        String[] values = raw.channel == Channel.SMS ? new String[]{raw.body} : raw.extras;
        if (raw.channel == Channel.SMS && raw.body == null) reasons.add("MISSING_BODY");
        for (int i = 0; i < values.length; i++) {
            String value = text(values[i], reasons);
            if (value.isEmpty()) continue;
            String key = boundaryKey(value);
            boolean covered = false;
            for (String existing : keys) if (contains(existing, key)) { covered = true; break; }
            if (covered) { reasons.add(keys.contains(key) ? "DUPLICATE_EXTRA" : "OVERLAP_COLLAPSED"); continue; }
            // An expanded extra (bigText) that repeats shorter ones replaces them: one event, not several.
            for (int k = keys.size() - 1; k >= 0; k--) if (contains(key, keys.get(k))) {
                keys.remove(k); kept.remove(k); fields.remove(k); reasons.add("OVERLAP_COLLAPSED");
            }
            keys.add(key); kept.add(value);
            fields.add(raw.channel == Channel.SMS ? "body" : ParsingPolicy.NOTIFICATION_FIELDS[i]);
        }
        String text = String.join("\n", kept);
        if (text.length() > ParsingPolicy.MAX_INPUT_CHARS) {
            text = text.substring(0, ParsingPolicy.MAX_INPUT_CHARS).trim();
            reasons.add("TRUNCATED");
        }
        if (text.isEmpty()) reasons.add("EMPTY_MESSAGE");
        return new Normalized(text, kept, fields, reasons, summary);
    }

    /** Display normalization of one fragment. */
    static String text(String raw) { return text(raw, new LinkedHashSet<>()); }

    private static String text(String raw, Set<String> reasons) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c) && (c < '0' || c > '9')) { out.append((char) ('0' + Character.digit(c, 10))); reasons.add("DIGITS_FOLDED"); }
            else if (c == '\u066b') out.append('.');
            else if (c == '\u066c') out.append(',');
            else if (invisibleControl(c)) reasons.add("CONTROLS_REMOVED");
            else if (c == '\u0640') reasons.add("TATWEEL_REMOVED");
            else out.append(c);
        }
        String value = Normalizer.normalize(out, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n');
        StringBuilder lines = new StringBuilder(value.length());
        for (String line : value.split("\n", -1)) {
            String clean = line.replaceAll("[\\t\\x0B\\f\\p{Zs}]+", " ").trim();
            if (clean.isEmpty()) continue;
            if (lines.length() > 0) lines.append('\n');
            lines.append(clean);
        }
        if (!lines.toString().equals(value)) reasons.add("SPACING_NORMALIZED");
        return lines.toString();
    }

    /** Letter folding for matching: case, combining marks (harakat, hamza carriers), alef/ya/ta marbuta forms. */
    static String fold(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        value = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('ٱ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
                .replace("\u200c", "").replace("\u200d", "");
        return value;
    }

    private static boolean invisibleControl(char c) {
        return c == '\u200b' || c == '\u200e' || c == '\u200f' || (c >= '\u202a' && c <= '\u202e') ||
                (c >= '\u2066' && c <= '\u2069') || c == '\u061c' || c == '\u2060' || c == '\ufeff';
    }

    private static String boundaryKey(String value) {
        return fold(value).replaceAll("\\s+", " ").trim();
    }

    /** True when needle occurs in haystack at letter/digit boundaries ("Paid" is not inside "Unpaid"). */
    private static boolean contains(String haystack, String needle) {
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            boolean before = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            boolean after = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (before && after) return true;
        }
        return false;
    }

    private MessageNormalizer() {}
}
