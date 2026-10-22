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
        long now = 1_792_600_000_000L;
        for (ParserCorpusFixture.Row row : rows) {
            ParserShadow.Observation seen = ParserShadow.observe(MessageNormalizerTest.raw(row), "LEGACY_REASON", null,
                    Long.parseLong(row.get("received_at_ms")), now++);
            check(seen != null && (seen.status.equals("EXTRACTED") || seen.status.equals("EMPTY")), "Shadow status " + row.get("id") + " " + seen);
            if (seen.status.equals("EXTRACTED")) same(seen.type.toLowerCase(java.util.Locale.ROOT), row.get("expected_type"), "Shadow classification " + row.get("id"));
            same(seen.decision, CorpusExpectations.expectedDecision(row), "Shadow scored decision " + row.get("id"));
            check(!"AUTO".equals(seen.decision) || seen.score >= ParsingPolicy.AUTO_THRESHOLD, "Shadow AUTO carries its score " + row.get("id"));
            same(seen.decision, seen.scoredDecision, "Without user data, learning leaves the scored decision " + row.get("id"));
            check(seen.toString().contains(ParserShadow.NOT_IMPLEMENTED), "Unimplemented stages stated " + row.get("id"));
            // Only structure is retained: no fragment of the message text appears in the observation.
            for (String word : MessageNormalizer.normalize(MessageNormalizerTest.raw(row)).text.split("[^\\p{L}]+"))
                if (word.length() >= 5 && !List.of("EMPTY", "SPACING", "NORMALIZED", "android", "title", "bigText", "textLines", "subText", "summaryText", "LEGACY", "REASON", "notImplemented", "CLASSIFICATION", "EXTRACTION", "CONFIDENCE", "LEARNING", "LOCAL", "policy", "normalization", "chars", "fields", "reasons", "legacy", "DIGITS", "FOLDED", "CONTROLS", "REMOVED", "TATWEEL", "GROUP", "SUMMARY", "DUPLICATE", "EXTRA", "OVERLAP", "COLLAPSED", "MESSAGE", "NOTIFICATION").contains(word))
                    // The observation's own upper-case codes (PROMO, CLASSIFIED, ...) are vocabulary, not message text.
                    check(!seen.toString().replaceAll("\\b[A-Z][A-Z_]+\\b", "").contains(word), "Message text kept in observation " + row.get("id") + ": " + word);
        }
        check(ParserShadow.observe(MessageNormalizer.Raw.sms("BANK", "x"), "R", null, now).policy.contains(ParsingPolicy.CORPUS_CONTRACT),
                "Observation names its policy and corpus contract");

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
                "ParsingPolicy.java", "AmountScanner.java", "MessageClassifier.java", "TransactionExtractor.java", "ConfidenceScorer.java", "LocalLearning.java", "ShadowStore.java", "ShadowDedupe.java", "PipelineDecider.java", "SmsPreview.java", "MissedMessages.java", "ShadowDiff.java", "ShadowOffset.java", "CaptureTransaction.java", "CaptureRequest.java", "PaymentNotificationListener.java", "ExpenseDb.java");
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                String text = Files.readString(file, StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
                boolean uses = text.matches("(?s).*\\b(ParserShadow|MessageNormalizer|MerchantNormalizer|MerchantCatalogIndex|ParsingPolicy|AmountScanner|MessageClassifier|TransactionExtractor|ConfidenceScorer|LocalLearning)\\b.*");
                check(!uses || allowed.contains(name), "Production class uses the shadow pipeline: " + name);
            }
        }
        String capture = Files.readString(source.resolve("CaptureTransaction.java"), StandardCharsets.UTF_8);
        // The new pipeline decides every message; only a Failed result hands the message to the legacy write path.
        check(capture.contains("PipelineDecider.Result decided = PipelineDecider.decide(PipelineDecider.context(db, \"live\"), request);"),
                "The new pipeline decides every live message");
        check(capture.contains("db.addAutomaticCapture(request, pipeline == null ? null : pipeline.decision)"), "Without a decision the legacy write path runs");
        // The stored observation or failure note sits in one guarded block that ends in the shared catch.
        String guarded = capture.substring(capture.indexOf("static void store("));
        check(guarded.indexOf("try {") < guarded.indexOf("ShadowStore.record(") && guarded.indexOf("ShadowStore.record(") < guarded.indexOf("ShadowStore.recordFailure(")
                        && guarded.indexOf("ShadowStore.recordFailure(") < guarded.indexOf("} catch (RuntimeException | LinkageError ignored)"),
                "Observation and failure note are isolated from the capture");
        check(!capture.contains("SmsPreview") && !capture.contains("LEGACY_OPINION"), "No legacy opinion is computed");
        String decider = Files.readString(source.resolve("PipelineDecider.java"), StandardCharsets.UTF_8);
        check(decider.contains("} catch (RuntimeException | LinkageError failed) {\n            return new Failed(\"EXCEPTION:\" + failed.getClass().getSimpleName());"),
                "A pipeline exception hands the message back to legacy with its reason");
        for (String reason : List.of("\"MISSING_AMOUNT\"", "\"MISSING_CURRENCY\"", "\"MISSING_DIRECTION\"", "new Failed(o.status)"))
            check(decider.contains(reason), "Named failure reason: " + reason);
        check(decider.contains("if (BuildConfig.DEBUG) failNext = containing;"), "The failure hook works in test builds only");
        check(!Files.exists(source.resolve("DecisionSwitch.java")), "No decision switch");
        String ledger = Files.readString(source.resolve("ExpenseDb.java"), StandardCharsets.UTF_8);
        check(ledger.contains("} else {\n                // The pipeline failed on this message: the legacy parser decides it.\n                transfer = ExpenseParser.parseTransfer(body);\n                parsed = ExpenseParser.parse(label, body);"),
                "Legacy parsing runs only when the pipeline failed");
        // A notification with no title or text has nothing to record.
        String listener = Files.readString(source.resolve("PaymentNotificationListener.java"), StandardCharsets.UTF_8);
        check(listener.contains("if (body == null) return;"), "Body-less notification records nothing");
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
        check(schema.matches("(?s).*public void clearAllData\\(\\) \\{[^}]*\"commitment_cycles\"\\}\\) db\\.delete\\(table, null, null\\);\\s*ShadowStore\\.clearAll\\(db\\);.*"), "Deleting all data deletes shadow data too");
        String db = Files.readString(source.resolve("ExpenseDb.java"), StandardCharsets.UTF_8);
        check(!db.matches("(?s).*\\b(ParserShadow|MessageNormalizer\\.normalize|MerchantCatalogIndex|MessageClassifier|AmountScanner|TransactionExtractor|ConfidenceScorer|LocalLearning)\\b.*"), "Ledger storage never reads shadow output");

        // 0.4.0: the debug screen, the re-scan, its report and the legacy-vs-new comparison are gone.
        for (String gone : List.of("ParserDebugActivity.java", "ShadowRescan.java", "ShadowReport.java", "DecisionSwitch.java"))
            check(!Files.exists(source.resolve(gone)), "Removed: " + gone);
        String manifest = Files.readString(Path.of("app/src/main/AndroidManifest.xml"), StandardCharsets.UTF_8);
        check(!manifest.contains("ParserDebugActivity"), "No debug screen in the manifest");
        String diff = Files.readString(source.resolve("ShadowDiff.java"), StandardCharsets.UTF_8);
        check(!diff.contains("compare(") && !diff.contains("legacyRecorded"), "ShadowDiff keeps newRecords only");
        check(ShadowDiff.newRecords("AUTO") && ShadowDiff.newRecords("REVIEW") && !ShadowDiff.newRecords("IGNORE") && !ShadowDiff.newRecords("UNUNDERSTOOD"), "AUTO and REVIEW record");
        System.out.println("Parser shadow checks passed: " + checks + " (" + rows.size() + " corpus rows observed; the new pipeline decides; legacy only when it fails).");
    }
}
