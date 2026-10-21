package com.mohamedgamal.masareef;

/**
 * One legacy decision against one shadow decision, for the debug screen and the re-scan report. Pure; no text.
 * Legacy "recorded" means it wrote a ledger row; the new pipeline "records" on AUTO or REVIEW. Two records agree
 * only on the same amount and direction (a legacy own-account transfer has no in/out, so any direction fits it).
 */
final class ShadowDiff {
    enum Outcome {
        BOTH_SKIP(true), BOTH_RECORD_SAME(true), BOTH_RECORD_DIFFERENT(false), LEGACY_ONLY(false), NEW_ONLY(false);
        final boolean agrees;
        Outcome(boolean agrees) { this.agrees = agrees; }
    }

    static final class Result {
        final Outcome outcome;
        /** For BOTH_RECORD_DIFFERENT: AMOUNT, DIRECTION or AMOUNT+DIRECTION; otherwise null. */
        final String difference;
        Result(Outcome outcome, String difference) { this.outcome = outcome; this.difference = difference; }
        boolean agrees() { return outcome.agrees; }
        @Override public String toString() { return outcome + (difference == null ? "" : ":" + difference); }
    }

    static boolean newRecords(String decision) { return "AUTO".equals(decision) || "REVIEW".equals(decision); }

    /** legacyDirection: out, in or transfer (ShadowStore.legacyDirection); null when legacy recorded nothing. */
    static Result compare(boolean legacyRecorded, Long legacyAmount, String legacyDirection, String newDecision, Long newAmount, String newDirection) {
        boolean recorded = newRecords(newDecision);
        if (!legacyRecorded && !recorded) return new Result(Outcome.BOTH_SKIP, null);
        if (legacyRecorded && !recorded) return new Result(Outcome.LEGACY_ONLY, null);
        if (!legacyRecorded) return new Result(Outcome.NEW_ONLY, null);
        boolean amount = java.util.Objects.equals(legacyAmount, newAmount);
        boolean direction = "transfer".equals(legacyDirection) || java.util.Objects.equals(legacyDirection, newDirection);
        if (amount && direction) return new Result(Outcome.BOTH_RECORD_SAME, null);
        return new Result(Outcome.BOTH_RECORD_DIFFERENT, !amount && !direction ? "AMOUNT+DIRECTION" : !amount ? "AMOUNT" : "DIRECTION");
    }

    /** Legacy decisions stored by ShadowStore: RECORDED and RECORDED_REVIEW wrote a ledger row. */
    static boolean legacyRecorded(String legacyDecision) { return legacyDecision != null && legacyDecision.startsWith("RECORDED"); }

    private ShadowDiff() {}
}
