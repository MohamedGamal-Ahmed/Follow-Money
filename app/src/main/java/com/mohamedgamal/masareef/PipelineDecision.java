package com.mohamedgamal.masareef;

/**
 * Step 7 (owner approval, 21 Oct 2026): the new pipeline's decision for one message, as plain data for the ledger
 * write path. ExpenseDb reads only these fields; it never runs or reads a pipeline stage. records=false means no
 * ledger row (IGNORE, UNUNDERSTOOD, INFO_ONLY); the legacy non-ledger paths (notices, hints, balances, commitments)
 * still run.
 */
final class PipelineDecision {
    final boolean records, review;
    final long amountMinor;
    final String currency, direction, merchant, category, cardLast4, decision;
    /** The ledger row this return offsets (expense_offset), or null. */
    final Long offsetOfTransactionId;
    /** A return (refund, reversal) that should offset an expense but found none: it waits for the user to link it. */
    final boolean unlinkedOffset;

    private PipelineDecision(boolean records, boolean review, long amountMinor, String currency, String direction, String merchant,
                             String category, String cardLast4, String decision, Long offsetOfTransactionId, boolean unlinkedOffset) {
        this.records = records; this.review = review; this.amountMinor = amountMinor; this.currency = currency; this.direction = direction;
        this.merchant = merchant; this.category = category; this.cardLast4 = cardLast4; this.decision = decision;
        this.offsetOfTransactionId = offsetOfTransactionId; this.unlinkedOffset = unlinkedOffset;
    }

    static PipelineDecision skip(String decision) {
        return new PipelineDecision(false, false, 0, null, null, null, null, null, decision, null, false);
    }

    static PipelineDecision record(boolean review, long amountMinor, String currency, String direction, String merchant, String category,
                                   String cardLast4, Long offsetOfTransactionId, boolean unlinkedOffset) {
        return new PipelineDecision(true, review || unlinkedOffset, amountMinor, currency, direction, merchant, category, cardLast4,
                review || unlinkedOffset ? "REVIEW" : "AUTO", offsetOfTransactionId, unlinkedOffset);
    }

    /** The legacy ledger kind for this direction. */
    String kind() { return "in".equals(direction) ? "income" : "expense"; }
}
