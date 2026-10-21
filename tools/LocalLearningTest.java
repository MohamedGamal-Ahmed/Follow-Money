package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stage-5 local learning: a no-op without user data, and every learning path and guard on real corpus messages. */
public final class LocalLearningTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    static Map<String, ParserCorpusFixture.Row> ids;

    /** Runs stages 1-5 on one corpus row with the given model. */
    static LocalLearning.Learned learn(LocalLearning model, String id) {
        ParserCorpusFixture.Row row = ids.get(id);
        MessageNormalizer.Raw raw = MessageNormalizerTest.raw(row);
        TransactionExtractor.Extraction[] x = new TransactionExtractor.Extraction[1];
        ConfidenceScorer.Confidence c = ShadowComparison.confidence(raw, Long.parseLong(row.get("received_at_ms")), x);
        return model.apply(row.get("source"), MessageNormalizer.normalize(raw).text, c, x[0]);
    }

    static String text(String id) { return MessageNormalizer.normalize(MessageNormalizerTest.raw(ids.get(id))).text; }
    static String source(String id) { return ids.get(id).get("source"); }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        ids = new LinkedHashMap<>();
        for (ParserCorpusFixture.Row row : rows) ids.put(row.get("id"), row);

        // Without any user data, learning changes nothing: corpus and both held-out sets keep the phase-4 decision.
        LocalLearning empty = new LocalLearning();
        for (ParserCorpusFixture.Row row : rows) same(learn(empty, row.get("id")).decision, ShadowComparison.expectedDecision(row), "Empty model no-op " + row.get("id"));
        long received = java.time.LocalDateTime.of(2026, 10, 22, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        for (String file : ShadowComparison.HELD_OUT_SETS) for (Map<String, String> h : HeldOutBlind.read(Path.of(file))) {
            MessageNormalizer.Raw raw = "sms".equals(h.get("channel")) ? MessageNormalizer.Raw.sms("held-out", h.get("body")) :
                    MessageNormalizer.Raw.notification("held-out", 0, h.get("android.title"), h.get("android.text"), h.get("android.bigText"), h.get("android.textLines"), null, null);
            TransactionExtractor.Extraction[] x = new TransactionExtractor.Extraction[1];
            ConfidenceScorer.Confidence c = ShadowComparison.confidence(raw, received, x);
            String learned = empty.apply("held-out", MessageNormalizer.normalize(raw).text, c, x[0]).decision;
            check(learned.equals(c.decision) && HeldOutBlind.equal("decision", learned, h.get("expected_decision")), "Empty model no-op " + h.get("id") + ": " + learned);
        }

        // Merchant rules the user set in the app: ignore new messages, or a category.
        LocalLearning rules = new LocalLearning();
        rules.setMerchantRules(List.of(new LocalLearning.MerchantRule("Nova Market", "بقالة", true), new LocalLearning.MerchantRule("Butlers", "مقاهي", false)));
        LocalLearning.Learned ignoredMerchant = learn(rules, "p004");
        same(ignoredMerchant.decision, "IGNORE", "Ignored merchant rule"); check(ignoredMerchant.reasons.contains("LEARNED_IGNORED_MERCHANT"), "Ignored merchant reason");
        LocalLearning.Learned categorized = learn(rules, "p001");
        same(categorized.decision, "AUTO", "Category rule keeps the decision"); same(categorized.category, "مقاهي", "Learned category");

        // Dismissals: threshold, template generality (same shape, other amount), source isolation.
        LocalLearning dismiss = new LocalLearning();
        dismiss.recordDismissal(source("u01"), text("u01"));
        same(learn(dismiss, "u01").decision, "AUTO", "One dismissal is not enough");
        dismiss.recordDismissal(source("u01"), text("u01"));
        LocalLearning.Learned dismissed = learn(dismiss, "u01");
        same(dismissed.decision, "IGNORE", "Two dismissals ignore the shape"); check(dismissed.reasons.contains("LEARNED_DISMISSED_SHAPE"), "Dismissed reason");
        same(learn(dismiss, "u01-amount4").decision, "IGNORE", "Same template with another amount shares the learned shape");
        same(learn(dismiss, "u02").decision, "AUTO", "A different template is unaffected");
        LocalLearning otherSource = new LocalLearning();
        otherSource.recordDismissal("SOMEONE_ELSE", text("u01")); otherSource.recordDismissal("SOMEONE_ELSE", text("u01"));
        same(learn(otherSource, "u01").decision, "AUTO", "Learning is per source");

        // Confirmations lift only learnable caps.
        LocalLearning confirm = new LocalLearning();
        for (int i = 0; i < ParsingPolicy.LEARN_CONFIRM_MIN - 1; i++) confirm.recordConfirmation(source("syn-topup-ar"), text("syn-topup-ar"), "in");
        same(learn(confirm, "syn-topup-ar").decision, "REVIEW", "Below the confirmation threshold");
        confirm.recordConfirmation(source("syn-topup-ar"), text("syn-topup-ar"), "in");
        LocalLearning.Learned lifted = learn(confirm, "syn-topup-ar");
        same(lifted.decision, "AUTO", "Own-account top-up confirmed three times"); check(lifted.reasons.contains("LEARNED_CONFIRMATION"), "Confirmation reason");
        for (int i = 0; i < 3; i++) confirm.recordConfirmation(source("p140"), text("p140"), "out");
        same(learn(confirm, "p140").decision, "AUTO", "Inferred direction confirmed three times");
        for (String id : new String[]{"u08", "p141"}) {
            for (int i = 0; i < 5; i++) confirm.recordConfirmation(source(id), text(id), ShadowComparison.extract(ids.get(id)).direction);
            same(learn(confirm, id).decision, "REVIEW", "Non-learnable cap stays REVIEW " + id);
        }
        for (String id : new String[]{"p131", "p161"}) {
            for (int i = 0; i < 5; i++) confirm.recordConfirmation(source(id), text(id), "out");
            same(learn(confirm, id).decision, ShadowComparison.expectedDecision(ids.get(id)), "Hard failure / IGNORE never learned into a transaction " + id);
        }
        LocalLearning wrongDirection = new LocalLearning();
        for (int i = 0; i < 3; i++) wrongDirection.recordConfirmation(source("syn-topup-ar"), text("syn-topup-ar"), "out");
        same(learn(wrongDirection, "syn-topup-ar").decision, "REVIEW", "Confirmed direction must match the message");
        LocalLearning conflict = new LocalLearning();
        for (int i = 0; i < 3; i++) conflict.recordConfirmation(source("syn-topup-ar"), text("syn-topup-ar"), "in");
        conflict.recordDismissal(source("syn-topup-ar"), text("syn-topup-ar")); conflict.recordDismissal(source("syn-topup-ar"), text("syn-topup-ar"));
        same(learn(conflict, "syn-topup-ar").decision, "REVIEW", "Conflicting feedback teaches nothing");

        // The unassigned-second-amount safety cap is never learnable.
        String twoAmounts = "Paid EGP 100.00 at NILE SHOP. EGP 40.00 extra charge applied.";
        MessageNormalizer.Normalized n = MessageNormalizer.normalize(MessageNormalizer.Raw.sms("probe", twoAmounts));
        MessageClassifier.Result cls = MessageClassifier.classify(n);
        TransactionExtractor.Extraction ex = TransactionExtractor.extract(n, cls, received);
        ConfidenceScorer.Confidence sc = ConfidenceScorer.score(cls, ex);
        LocalLearning safety = new LocalLearning();
        for (int i = 0; i < 5; i++) safety.recordConfirmation("probe", n.text, "out");
        same(safety.apply("probe", n.text, sc, ex).decision, "REVIEW", "UNASSIGNED_SECOND_AMOUNT is never learned away");

        // Bounded memory and no message text kept.
        LocalLearning bounded = new LocalLearning();
        for (int i = 0; i < ParsingPolicy.LEARN_MAX_SHAPES + 5; i++) bounded.recordDismissal("source-" + i, text("u01"));
        same(bounded.shapeCount(), ParsingPolicy.LEARN_MAX_SHAPES, "Shape memory is bounded");
        check(LocalLearning.key("BankMisr", text("u01")).matches("[0-9a-f]{64}"), "Shape key is a hash");
        check(!bounded.toString().contains("حساب"), "Model holds no message text");

        System.out.println("Local learning checks passed: " + checks + " (" + rows.size() + " corpus rows + 50 held-out unchanged by an empty model).");
    }
}
