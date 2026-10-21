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
            ParserShadow.Observation seen = ParserShadow.observe(MessageNormalizerTest.raw(row), "LEGACY_REASON", null,
                    Long.parseLong(row.get("received_at_ms")), now++);
            check(seen != null && (seen.status.equals("EXTRACTED") || seen.status.equals("EMPTY")), "Shadow status " + row.get("id") + " " + seen);
            if (seen.status.equals("EXTRACTED")) same(seen.type.toLowerCase(java.util.Locale.ROOT), row.get("expected_type"), "Shadow classification " + row.get("id"));
            same(seen.decision, ShadowComparison.expectedDecision(row), "Shadow scored decision " + row.get("id"));
            check(!"AUTO".equals(seen.decision) || seen.score >= ParsingPolicy.AUTO_THRESHOLD, "Shadow AUTO carries its score " + row.get("id"));
            same(seen.decision, seen.scoredDecision, "Without user data, learning leaves the scored decision " + row.get("id"));
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
        // The user's merchant rules reach the shadow decision; the legacy result is untouched (observe returns only a record).
        ParserShadow.learning().setMerchantRules(List.of(new LocalLearning.MerchantRule("Nova Market", "بقالة", true)));
        ParserShadow.Observation learnedIgnore = ParserShadow.observe(MessageNormalizer.Raw.sms("BANK", "Paid EGP 45 at \"Nova Market\"."), "INSERTED", null, now);
        same(learnedIgnore.decision + "/" + learnedIgnore.scoredDecision, "IGNORE/AUTO", "Ignored-merchant rule in shadow");
        ParserShadow.learning().setMerchantRules(List.of());

        // A failing stage is an observation, never an exception.
        var constructor = MessageNormalizer.Raw.class.getDeclaredConstructor(MessageNormalizer.Channel.class, String.class, String.class, String[].class, int.class);
        constructor.setAccessible(true);
        MessageNormalizer.Raw broken = constructor.newInstance(MessageNormalizer.Channel.NOTIFICATION, "x", null, new String[]{"a", "b", "c", "d", "e", "f", "g"}, 0);
        ParserShadow.Observation failed = ParserShadow.observe(broken, "INSERTED", null, now);
        check(failed.status.startsWith("STAGE_FAILED:"), "Stage failure isolated: " + failed.status);

        // The legacy path decides: outside the shadow wiring no production class reads new-stage output.
        Path source = Path.of("app/src/main/java/com/mohamedgamal/masareef");
        List<String> allowed = List.of("ParserShadow.java", "MessageNormalizer.java", "MerchantNormalizer.java", "MerchantCatalogIndex.java",
                "ParsingPolicy.java", "AmountScanner.java", "MessageClassifier.java", "TransactionExtractor.java", "ConfidenceScorer.java", "LocalLearning.java", "ShadowStore.java", "ShadowDedupe.java", "ShadowDiff.java", "ParserDebugActivity.java", "CaptureTransaction.java", "CaptureRequest.java", "PaymentNotificationListener.java", "ExpenseDb.java");
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                String text = Files.readString(file, StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
                boolean uses = text.matches("(?s).*\\b(ParserShadow|MessageNormalizer|MerchantNormalizer|MerchantCatalogIndex|ParsingPolicy|AmountScanner|MessageClassifier|TransactionExtractor|ConfidenceScorer|LocalLearning)\\b.*");
                check(!uses || allowed.contains(name), "Production class uses the shadow pipeline: " + name);
            }
        }
        String capture = Files.readString(source.resolve("CaptureTransaction.java"), StandardCharsets.UTF_8);
        // The rules read and the shadow call sit in one guarded block (no nested braces) that ends in the shared catch.
        check(capture.matches("(?s).*try \\{[^}]*db\\.merchantRules\\(\\)[^}]*ParserShadow\\.observe\\([^}]*ShadowStore\\.record\\([^}]*\\} catch \\(RuntimeException \\| LinkageError.*"), "Shadow call and store are isolated");
        // The observation goes only to the shadow store: declared once, read once, inside the guarded block.
        same(capture.split("\\bobservation\\b", -1).length - 1, 2, "Shadow result is never used by capture");
        check(capture.contains("ShadowStore.record(sql, raw, legacyBody, label, receivedAt, result, legacyReason, observation);"), "Observation stored beside the legacy result");
        // A notification legacy skips is observed in shadow only: that branch returns before any capture call.
        String listener = Files.readString(source.resolve("PaymentNotificationListener.java"), StandardCharsets.UTF_8);
        check(listener.matches("(?s).*if \\(body == null\\) \\{[^}]*CaptureTransaction\\.observeWithoutCapture\\([^}]*\\}\\s*return;\\s*\\}.*"), "Body-less notification observed, never captured");
        check(listener.contains("raw.withIdentity(MessageNormalizer.Raw.notificationIdentity(item.getKey(), item.getPostTime()))"), "Notification identity is key plus original post time");
        check(listener.contains("captureNotification(packageName, label, body, item.getPostTime(), shadow)"), "Legacy capture unchanged");
        // Storage: sealed values, outside the ledger revision, created on install and upgrade.
        String store = Files.readString(source.resolve("ShadowStore.java"), StandardCharsets.UTF_8);
        same(store.split("put\\(\"encrypted_body\", EvidenceVault\\.encrypt\\(", -1).length - 1, 2, "Observation values and ununderstood text are sealed");
        check(!store.matches("(?s).*\\.put\\(\"(?!encrypted_body)[a-z_]+\", (?:raw|legacyBody|o\\.values).*"), "No message text or value outside encrypted_body");
        String revision = Files.readString(source.resolve("LedgerRevision.java"), StandardCharsets.UTF_8);
        String vault = Files.readString(source.resolve("EvidenceVault.java"), StandardCharsets.UTF_8);
        for (String table : List.of("shadow_observations", "ununderstood_messages", "learning_rules")) {
            check(!revision.contains("\"" + table + "\""), "Shadow table outside the ledger revision: " + table);
            check(store.contains("CREATE TABLE IF NOT EXISTS " + table + "("), "Shadow table created: " + table);
        }
        for (String table : List.of("shadow_observations", "ununderstood_messages")) check(vault.contains("\"" + table + "\""), "Backup re-seals " + table);
        String schema = Files.readString(source.resolve("ExpenseDb.java"), StandardCharsets.UTF_8);
        check(schema.contains("ShadowStore.create(db);\n    }") && schema.contains("if(oldVersion<26)ShadowStore.create(db);"), "Shadow tables on install and upgrade");
        String db = Files.readString(source.resolve("ExpenseDb.java"), StandardCharsets.UTF_8);
        check(!db.matches("(?s).*\\b(ParserShadow|MessageNormalizer\\.normalize|MerchantCatalogIndex|MessageClassifier|AmountScanner|TransactionExtractor|ConfidenceScorer|LocalLearning)\\b.*"), "Ledger storage never reads shadow output");

        // Agreement used by the debug screen and the re-scan report.
        same(ShadowDiff.compare(false, null, null, "IGNORE", null, null).toString(), "BOTH_SKIP", "Both skip");
        same(ShadowDiff.compare(false, null, null, "UNUNDERSTOOD", null, null).toString(), "BOTH_SKIP", "Ununderstood records nothing");
        same(ShadowDiff.compare(true, 4500L, "out", "AUTO", 4500L, "out").toString(), "BOTH_RECORD_SAME", "Same record");
        same(ShadowDiff.compare(true, 4500L, "out", "REVIEW", 4500L, "out").agrees(), true, "Review still records");
        same(ShadowDiff.compare(true, 4500L, "out", "AUTO", 450L, "in").toString(), "BOTH_RECORD_DIFFERENT:AMOUNT+DIRECTION", "Both fields differ");
        same(ShadowDiff.compare(true, 4500L, "out", "AUTO", 4500L, "in").toString(), "BOTH_RECORD_DIFFERENT:DIRECTION", "Direction differs");
        same(ShadowDiff.compare(true, 4500L, "transfer", "REVIEW", 4500L, "in").toString(), "BOTH_RECORD_SAME", "Own transfer has no in/out");
        same(ShadowDiff.compare(true, 4500L, "out", "IGNORE", null, null).toString(), "LEGACY_ONLY", "Legacy only");
        same(ShadowDiff.compare(false, null, null, "AUTO", 4500L, "out").toString(), "NEW_ONLY", "New only");
        check(ShadowDiff.legacyRecorded("RECORDED_REVIEW") && !ShadowDiff.legacyRecorded("NOT_RECORDED"), "Legacy decision labels");
        // The debug screen is read-only and local: no ledger write, no network, not exported.
        String debug = Files.readString(source.resolve("ParserDebugActivity.java"), StandardCharsets.UTF_8);
        check(!debug.matches("(?s).*(getWritableDatabase|\\.insert\\(|execSQL|\\.delete\\(|\\.update\\(|HttpURLConnection|java\\.net\\.|Intent\\.ACTION_SEND).*"), "Debug screen is read-only and offline");
        String manifest = Files.readString(Path.of("app/src/main/AndroidManifest.xml"), StandardCharsets.UTF_8);
        check(manifest.contains("<activity android:name=\".ParserDebugActivity\" android:exported=\"false\" />"), "Debug screen not exported");
        ParserShadow.clear();
        System.out.println("Parser shadow checks passed: " + checks + " (" + rows.size() + " corpus rows observed; legacy remains the only ledger authority).");
    }
}
