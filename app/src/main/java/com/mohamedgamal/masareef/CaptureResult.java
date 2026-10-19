package com.mohamedgamal.masareef;

/** Immutable observations after capture/repair return; a caller-owned transaction is still pending. */
final class CaptureResult {
    enum Reason { MISSING_BODY, VERIFICATION, EXCLUDED, FUTURE_ONLY, UNSUCCESSFUL,
        NOTICE_RECORDED, NOTICE_DUPLICATE, NO_LEDGER_CANDIDATE, SERVICE_MATCH_ACCEPTED, COMMITMENT_REVIEW_REQUIRED,
        INSERTED, EXISTING_MATCH, INSERT_NOT_APPLIED }
    /** Describes the attempted source write, even if repair later moves it into an archive. */
    enum Evidence { NOT_ATTEMPTED, SAVED_WITH_BODY, SAVED_WITHOUT_BODY, NOT_APPLIED, EVENT_BODY, PROVIDER_BODY }
    enum Completion { COMMITTED, COMMITTED_REPAIR_FAILED, PENDING_CALLER }

    /** Request-local storage observations; never stored on the shared helper. */
    static final class Write {
        boolean added, inserted, repairFailed;
        long rowId = -1;
        CaptureEvents.Event event;
        Reason reason = Reason.NO_LEDGER_CANDIDATE;
        Evidence evidence = Evidence.NOT_ATTEMPTED;
    }
    /** Acceptance preserves the old boolean policy; it does not prove a new row or any write. */
    final boolean accepted, dbChanged, needsReview;
    final Completion completion;
    final Reason reason;
    final Evidence evidence;
    final long revisionBefore, revisionAfter;
    final Long capturedTransactionId, transactionId;
    /** A created row followed through repair; null for existing matches, side effects and archives. */
    final ExpenseDb.Transaction transaction;
    final CaptureEvents.Event event;

    CaptureResult(Write write, long capturedId, ExpenseDb.Transaction affected, boolean pending,
                  long revisionBefore, long revisionAfter) {
        accepted = write.added;
        completion = pending ? Completion.PENDING_CALLER : write.repairFailed ?
                Completion.COMMITTED_REPAIR_FAILED : Completion.COMMITTED;
        this.revisionBefore = revisionBefore;
        this.revisionAfter = revisionAfter;
        // Global invalidation observations, not an attributed write count under competing writers.
        dbChanged = !pending && revisionAfter != revisionBefore;
        capturedTransactionId = write.inserted ? capturedId : null;
        transactionId = affected == null ? null : affected.id;
        transaction = write.inserted ? affected : null;
        event = write.event;
        reason = write.reason;
        evidence = write.evidence;
        needsReview = affected != null && affected.needsReview || event != null && !event.cancelled() || reason==Reason.COMMITMENT_REVIEW_REQUIRED;
    }
}
