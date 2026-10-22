package com.mohamedgamal.masareef;

/**
 * Owner decision m6 (0.4.0): which messages the app did not record wait on «رسائل من بنوكك ما اتسجلتش». Pure; no
 * Android, no storage.
 */
final class MissedMessages {
    /**
     * A message the pipeline did not understand, or a possible miss from a known bank; never a shape the user dismissed
     * on the page («تجاهل» teaches it), so a message delivered again does not come back.
     */
    static boolean listed(String source, ParserShadow.Observation o, String normalizedText, LocalLearning learning) {
        if (learning.dismissedShape(source, normalizedText)) return false;
        if ("UNUNDERSTOOD".equals(o.decision)) return true;
        return missedFromBank(source, o, normalizedText);
    }

    /**
     * The pipeline ignored it as OTHER (no completed event it could read; never an offer, a code, a balance or a
     * reminder), it comes from a known bank or a payment app, and its text names an amount. The user's own ignore rule
     * or dismissed shape is not a miss.
     */
    static boolean missedFromBank(String source, ParserShadow.Observation o, String normalizedText) {
        if (!"IGNORE".equals(o.decision) || !"OTHER".equals(o.type)) return false;
        if (o.reasons.contains("LEARNED_IGNORED_MERCHANT") || o.reasons.contains("LEARNED_DISMISSED_SHAPE")) return false;
        if (!knownSender(source)) return false;
        return SmsPreview.hasAmount(normalizedText);
    }

    /** A bank or payment app the catalog knows, by name («Banque Misr», «BM») or folded sender («BankMisr», «Telda»). */
    static boolean knownSender(String source) {
        return BrandCatalog.bank(source) != null || BrandCatalog.sourcePackage(source) != null;
    }

    private MissedMessages() {}
}
