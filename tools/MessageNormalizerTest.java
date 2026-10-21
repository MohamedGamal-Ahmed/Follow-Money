package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stage-1 normalization against every row of the approved corpus v2. No classification is evaluated. */
public final class MessageNormalizerTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    static MessageNormalizer.Raw raw(ParserCorpusFixture.Row row) {
        if ("sms".equals(row.get("channel"))) return MessageNormalizer.Raw.sms(row.get("source"), row.get("body"));
        return MessageNormalizer.Raw.notification(row.get("source"), Integer.parseInt(row.get("notification_flags")),
                row.get("android.title"), row.get("android.text"), row.get("android.bigText"),
                row.get("android.textLines"), row.get("android.subText"), row.get("android.summaryText"));
    }

    private static String spaced(MessageNormalizer.Normalized value) { return value.matchText.replaceAll("\\s+", " ").trim(); }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        Map<String, MessageNormalizer.Normalized> out = new HashMap<>();
        int variantsCompared = 0;
        for (ParserCorpusFixture.Row row : rows) {
            String id = row.get("id");
            MessageNormalizer.Normalized value = MessageNormalizer.normalize(raw(row));
            out.put(id, value);
            String text = value.text;
            check(!text.matches("(?s).*[\\u200b\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069\\u061c\\u2060\\ufeff\\u0640].*"), "Invisible control or tatweel left " + id);
            check(!text.matches("(?s).*[\\u0660-\\u0669\\u06f0-\\u06f9\\u066b\\u066c].*"), "Non-ASCII digit/separator left " + id);
            check(!text.matches("(?s).*[\\t\\u00a0\\u202f\\u2009].*") && !text.contains("  "), "Unnormalized spacing " + id);
            for (String line : text.split("\n", -1)) check(text.isEmpty() || (!line.isEmpty() && line.equals(line.trim())), "Empty/untrimmed line " + id);
            same(MessageNormalizer.normalize(MessageNormalizer.Raw.sms(row.get("source"), text)).text, text, "Idempotent " + id);
            check(value.fragments.size() == value.fields.size(), "Fragment/field lineage " + id);
            check(value.empty() == value.reasons.contains("EMPTY_MESSAGE"), "Empty reason " + id);
        }
        // Mechanical digit/spacing variants must normalize to their base, up to line breaks the generator inserted.
        for (ParserCorpusFixture.Row row : rows) {
            if (!List.of("digits", "spaces").contains(row.get("variant"))) continue;
            same(spaced(out.get(row.get("id"))), spaced(out.get(row.get("base_case_id"))), "Variant normalizes to base " + row.get("id"));
            variantsCompared++;
        }
        check(variantsCompared >= 100, "Variant coverage " + variantsCompared);

        // Notification extras: absent vs empty, duplicates, overlap, summaries.
        same(out.get("p001").fields, List.of("android.title"), "Telda title-only body");
        same(out.get("p001").text, "تم دفع 15 ج.م. في \"Kashier Butlers\"\nرصيدك: 31.5 ج.م.", "Title-only text kept with its line break");
        same(out.get("p012").fragments.size(), 1, "Four identical extras are one message");
        check(out.get("p012").reasons.contains("DUPLICATE_EXTRA"), "Duplicate reason");
        same(out.get("p023").fields, List.of("android.bigText"), "Short title/text collapse into the expanded bigText");
        check(out.get("p023").reasons.contains("OVERLAP_COLLAPSED"), "Overlap reason");
        same(out.get("p010").fragments.size(), 4, "Distinct split extras are all kept");
        same(out.get("p006").fields, List.of("android.title", "android.text"), "Whitespace-only bigText ignored");
        for (String id : new String[]{"p013", "p014", "p015"}) check(out.get(id).groupSummary && out.get(id).reasons.contains("GROUP_SUMMARY"), "Group summary flagged, not dropped " + id);
        check(!out.get("p013").empty() && !out.get("p014").empty(), "Summaries keep their text for later stages");
        check(out.get("p018").empty() && out.get("p018").reasons.contains("EMPTY_MESSAGE"), "Empty notification");
        same(out.get("p016").text, "تم دفع 15 ج.م. في \"Kashier Butlers\"\nرصيدك: 31.5 ج.م.", "Arabic digits and decimal separator");
        same(out.get("syn-instapay-ar").fields, List.of("android.title", "android.bigText"), "InstaPay text inside bigText collapses");

        // Text-level normalization from the corpus normalization group.
        check(out.get("p107").text.startsWith("تم دفع 250.75 جنيه في"), "Bidi marks removed between words");
        check(out.get("p107").text.contains("مَكْتَبَة"), "Display text keeps harakat");
        check(out.get("p107").matchText.contains("\"مكتبه النور\""), "Match text folds harakat and ta marbuta");
        same(out.get("p110").text, "تم دفع\n250.75 جنيه في \"إيكيا\"", "Tabs, repeated spaces, tatweel");
        check(out.get("p110").matchText.contains("ايكيا"), "Hamza alef folded");
        check(out.get("p098").text.contains("250.75"), "Persian digits and Arabic decimal separator");
        check(out.get("p097").matchText.contains("سعودي"), "Alef maqsura folded to ya");

        // Direct unit cases.
        same(MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", null)).reasons.contains("MISSING_BODY"), true, "Null SMS body");
        MessageNormalizer.Normalized bounded = MessageNormalizer.normalize(MessageNormalizer.Raw.notification("x", 0, "Paid", "Unpaid invoice EGP 5", null, null, null, null));
        same(bounded.fragments.size(), 2, "Containment respects word boundaries");
        String longText = "a ".repeat(ParsingPolicy.MAX_INPUT_CHARS);
        check(MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", longText)).reasons.contains("TRUNCATED"), "Bounded input");
        same(MessageNormalizer.text("A  B\r\nC D"), "A B\nC\nD", "Line separators and nonbreaking spaces");

        System.out.println("Message normalizer checks passed: " + checks + " (" + rows.size() + " corpus v2 rows, " + variantsCompared + " mechanical variants matched their base).");
    }
}
