package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Shadow mode: never throws, keeps no message text, stays bounded, and no legacy code reads its output. */
public final class ParserShadowTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        ParserShadow.clear();
        long now = 1_792_600_000_000L;
        for (ParserCorpusFixture.Row row : rows) {
            ParserShadow.Observation seen = ParserShadow.observe(MessageNormalizerTest.raw(row), "LEGACY_REASON", null, now++);
            check(seen != null && (seen.status.equals("CLASSIFIED") || seen.status.equals("EMPTY")), "Shadow status " + row.get("id") + " " + seen);
            if (seen.status.equals("CLASSIFIED")) same(seen.type.toLowerCase(java.util.Locale.ROOT), row.get("expected_type"), "Shadow classification " + row.get("id"));
            check(seen.toString().contains(ParserShadow.NOT_IMPLEMENTED), "Unimplemented stages stated " + row.get("id"));
            // Only structure is retained: no fragment of the message text appears in the observation.
            for (String word : MessageNormalizer.normalize(MessageNormalizerTest.raw(row)).text.split("[^\\p{L}]+"))
                if (word.length() >= 5 && !List.of("EMPTY", "SPACING", "NORMALIZED", "android", "title", "bigText", "textLines", "subText", "summaryText", "LEGACY", "REASON", "notImplemented", "CLASSIFICATION", "EXTRACTION", "CONFIDENCE", "LEARNING", "LOCAL", "policy", "normalization", "chars", "fields", "reasons", "legacy", "DIGITS", "FOLDED", "CONTROLS", "REMOVED", "TATWEEL", "GROUP", "SUMMARY", "DUPLICATE", "EXTRA", "OVERLAP", "COLLAPSED", "MESSAGE", "NOTIFICATION").contains(word))
                    // The observation's own upper-case codes (PROMO, CLASSIFIED, ...) are vocabulary, not message text.
                    check(!seen.toString().replaceAll("\\b[A-Z][A-Z_]+\\b", "").contains(word), "Message text kept in observation " + row.get("id") + ": " + word);
        }
        List<ParserShadow.Observation> recent = ParserShadow.recent();
        same(recent.size(), ParsingPolicy.SHADOW_BUFFER_SIZE, "Bounded in-memory buffer");
        same(recent.get(recent.size() - 1).observedAt, now - 1, "Newest observation last");
        check(recent.get(0).policy.contains(ParsingPolicy.CORPUS_CONTRACT), "Observation names its policy and corpus contract");

        // Legacy merchant compared with the normalized catalog, never written back.
        ParserShadow.Observation agrees = ParserShadow.observe(MessageNormalizer.Raw.sms("BANK", "x"), "INSERTED", "KASHIER*BUTLERS", now);
        same(agrees.merchantProposal, "EXACT:Butlers", "Catalog proposal for legacy merchant");
        check(agrees.reasons.contains("MERCHANT_AGREES"), "Agreement recorded");
        ParserShadow.Observation differs = ParserShadow.observe(MessageNormalizer.Raw.sms("BANK", "x"), "INSERTED", "Starbuks", now);
        same(differs.merchantProposal, "FUZZY:STARBUCKS~1", "Fuzzy proposal");
        check(differs.reasons.contains("MERCHANT_DIFFERS"), "Disagreement recorded, legacy untouched");
        same(ParserShadow.observe(null, "INSERTED", null, now), null, "No input, no observation");

        // A failing stage is an observation, never an exception.
        var constructor = MessageNormalizer.Raw.class.getDeclaredConstructor(MessageNormalizer.Channel.class, String.class, String.class, String[].class, int.class);
        constructor.setAccessible(true);
        MessageNormalizer.Raw broken = constructor.newInstance(MessageNormalizer.Channel.NOTIFICATION, "x", null, new String[]{"a", "b", "c", "d", "e", "f", "g"}, 0);
        ParserShadow.Observation failed = ParserShadow.observe(broken, "INSERTED", null, now);
        check(failed.status.startsWith("STAGE_FAILED:"), "Stage failure isolated: " + failed.status);

        // The legacy path decides: outside the shadow wiring no production class reads new-stage output.
        Path source = Path.of("app/src/main/java/com/mohamedgamal/masareef");
        List<String> allowed = List.of("ParserShadow.java", "MessageNormalizer.java", "MerchantNormalizer.java", "MerchantCatalogIndex.java",
                "ParsingPolicy.java", "AmountScanner.java", "MessageClassifier.java", "TransactionExtractor.java", "CaptureTransaction.java", "CaptureRequest.java", "PaymentNotificationListener.java", "ExpenseDb.java");
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                String text = Files.readString(file, StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
                boolean uses = text.matches("(?s).*\\b(ParserShadow|MessageNormalizer|MerchantNormalizer|MerchantCatalogIndex|ParsingPolicy|AmountScanner|MessageClassifier|TransactionExtractor)\\b.*");
                check(!uses || allowed.contains(name), "Production class uses the shadow pipeline: " + name);
            }
        }
        String capture = Files.readString(source.resolve("CaptureTransaction.java"), StandardCharsets.UTF_8);
        check(capture.matches("(?s).*try \\{\\s*ParserShadow\\.observe\\(.*catch \\(RuntimeException \\| LinkageError.*"), "Shadow call is isolated");
        check(!capture.matches("(?s).*=\\s*ParserShadow\\.observe.*"), "Shadow result is never used by capture");
        String db = Files.readString(source.resolve("ExpenseDb.java"), StandardCharsets.UTF_8);
        check(!db.matches("(?s).*\\b(ParserShadow|MessageNormalizer\\.normalize|MerchantCatalogIndex|MessageClassifier|AmountScanner|TransactionExtractor)\\b.*"), "Ledger storage never reads shadow output");

        ParserShadow.clear();
        System.out.println("Parser shadow checks passed: " + checks + " (" + rows.size() + " corpus rows observed; legacy remains the only ledger authority).");
    }
}
