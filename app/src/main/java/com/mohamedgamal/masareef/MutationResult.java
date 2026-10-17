package com.mohamedgamal.masareef;

/** Result of the primary-row edit, exposed only after the mutation owner's transaction ends successfully. */
final class MutationResult {
    enum Outcome { UPDATED, NOT_FOUND, NOT_APPLIED }
    final long transactionId;
    final Outcome outcome;

    MutationResult(long transactionId, Outcome outcome) {
        this.transactionId = transactionId;
        this.outcome = outcome;
    }

    boolean changed() { return outcome == Outcome.UPDATED; }
}
