package com.mohamedgamal.masareef;

/**
 * Owner decision m6 (0.4.0): which messages the app did not record wait on «رسائل من بنوكك ما اتسجلتش». Pure; no
 * Android, no storage.
 */
final class MissedMessages {
    /**
     * A message from the user's own bank (or a selected payment app) naming an amount above zero, that the pipeline did
     * not understand or ignored as OTHER; never a shape the user dismissed on the page («تجاهل» teaches it).
     * userBanks: SourceIdentity.bank of the user's live sources (owner acceptance: not the general brand catalog, so
     * we-Landline, VFCashGifts and B.TECH never appear).
     */
    /**
     * The same, for a message a legacy guard kept out of the ledger (LEGACY_GUARD:*, reviewer 23 Oct 2026): the pipeline
     * would have recorded it, so with the same sender and amount conditions it is a possible miss, never lost silently.
     */
    static boolean listed(String source, ParserShadow.Observation o, String normalizedText, LocalLearning learning, java.util.Set<String> userBanks, boolean guarded) {
        if (!guarded) return listed(source, o, normalizedText, learning, userBanks);
        return !learning.dismissedShape(source, normalizedText) && knownSender(source, o, userBanks) && positiveAmount(normalizedText);
    }

    static boolean listed(String source, ParserShadow.Observation o, String normalizedText, LocalLearning learning, java.util.Set<String> userBanks) {
        if (learning.dismissedShape(source, normalizedText)) return false;
        if (!knownSender(source, o, userBanks) || !positiveAmount(normalizedText)) return false;
        if ("UNUNDERSTOOD".equals(o.decision)) return true;
        return missedFromBank(o);
    }

    /**
     * The pipeline ignored it as OTHER (no completed event it could read; never an offer, a code, a balance or a
     * reminder). The user's own ignore rule or dismissed shape is not a miss.
     */
    static boolean missedFromBank(ParserShadow.Observation o) {
        if (!"IGNORE".equals(o.decision) || !"OTHER".equals(o.type)) return false;
        return !o.reasons.contains("LEARNED_IGNORED_MERCHANT") && !o.reasons.contains("LEARNED_DISMISSED_SHAPE");
    }

    /** A bank the user holds a source with, or a notification (only selected payment apps' notifications are captured). */
    static boolean knownSender(String source, ParserShadow.Observation o, java.util.Set<String> userBanks) {
        if ("NOTIFICATION".equals(o.channel)) return true;
        String bank = SourceIdentity.bank(source);
        return bank != null && userBanks.contains(bank);
    }

    /** An amount with its currency above zero («0.00 جنيه» is not one; an unclear «EUR 1.250» still is). */
    static boolean positiveAmount(String text) {
        return SmsPreview.hasPositiveAmount(text);
    }

    private MissedMessages() {}
}
