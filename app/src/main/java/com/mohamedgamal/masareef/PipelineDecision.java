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
    /** The other party as the message wrote it (a name, an InstaPay address, an account tail), or null. */
    final String counterparty;
    /** The ledger row this return offsets (expense_offset), or null. */
    final Long offsetOfTransactionId;
    /** A return (refund, reversal) that should offset an expense but found none: it waits for the user to link it. */
    final boolean unlinkedOffset;

    private PipelineDecision(boolean records, boolean review, long amountMinor, String currency, String direction, String merchant,
                             String category, String cardLast4, String counterparty, String decision, Long offsetOfTransactionId, boolean unlinkedOffset) {
        this.records = records; this.review = review; this.amountMinor = amountMinor; this.currency = currency; this.direction = direction;
        this.merchant = merchant; this.category = category; this.cardLast4 = cardLast4; this.counterparty = counterparty; this.decision = decision;
        this.offsetOfTransactionId = offsetOfTransactionId; this.unlinkedOffset = unlinkedOffset;
    }

    /** The balance the message states after the movement (Telda «رصيدك: 31.5 ج.م.»), or null. */
    Long balanceMinor;
    String balanceCurrency;

    PipelineDecision withBalance(Long minor, String currency) {
        balanceMinor = minor; balanceCurrency = currency;
        return this;
    }

    /** The user's own merchant rule ignored this message (LEARNED_IGNORED_MERCHANT): a refusal, as legacy reported it. */
    boolean ignoredByUser;

    static PipelineDecision skip(String decision) {
        return new PipelineDecision(false, false, 0, null, null, null, null, null, null, decision, null, false);
    }

    static PipelineDecision skip(String decision, boolean ignoredByUser) {
        PipelineDecision skipped = skip(decision);
        skipped.ignoredByUser = ignoredByUser;
        return skipped;
    }

    static PipelineDecision record(boolean review, long amountMinor, String currency, String direction, String merchant, String category,
                                   String cardLast4, String counterparty, Long offsetOfTransactionId, boolean unlinkedOffset) {
        return new PipelineDecision(true, review || unlinkedOffset, amountMinor, currency, direction, merchant, category, cardLast4, counterparty,
                review || unlinkedOffset ? "REVIEW" : "AUTO", offsetOfTransactionId, unlinkedOffset);
    }

    /** The same recording decision, now linked to the charge it returns (an expense offset, no longer waiting for the user). */
    PipelineDecision withOffset(long chargeId, boolean pipelineReview) {
        return record(pipelineReview, amountMinor, currency, direction, merchant, category, cardLast4, counterparty, chargeId, false).withBalance(balanceMinor, balanceCurrency);
    }

    /** The legacy ledger kind for this direction. */
    String kind() { return "in".equals(direction) ? "income" : "expense"; }

    /**
     * The digits that attach the row to the user's source: the card when the message names one, else the user's
     * account (an outgoing «to account» tail is never the user's; TransactionExtractor already leaves it out). Same
     * order as the legacy ExpenseParser.last4; without it a bank SMS that names only an account was written with no
     * source, so own-transfer matching never saw it.
     */
    static String counterpartyTail(String counterparty) {
        if (counterparty == null) return null;
        java.util.regex.Matcher m = COUNTERPARTY_TAIL.matcher(counterparty.trim());
        return m.matches() ? m.group(1) : null;
    }
    /** Only a mask and 3–4 digits: an account or card tail, never a name, an address or a phone number. */
    private static final java.util.regex.Pattern COUNTERPARTY_TAIL = java.util.regex.Pattern.compile("(?i)[*x#\\s]*(\\d{3,4})");

    static String sourceLast4(java.util.Map<String, String> values) {
        String card = values.get("card_last4");
        if (card != null) return card;
        // An HSBC statement line keeps only a 3-4 digit tail of the user's account; legacy attached the row by it too.
        return values.get("account_last4") != null ? values.get("account_last4") : values.get("account_partial");
    }
}
