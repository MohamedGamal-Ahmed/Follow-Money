package com.mohamedgamal.masareef;

/**
 * Orders live capture, bounded repair, and the resulting snapshot. Borrows the caller's connection:
 * ExpenseDb still owns storage transactions; this extraction neither closes it nor adds a transaction.
 * Historical import retains its separate batch/confirmation policy.
 */
final class CaptureTransaction {
    private final ExpenseDb db;

    CaptureTransaction(ExpenseDb db) { this.db = db; }

    CaptureResult execute(CaptureRequest request) {
        boolean added = apply(request);
        return new CaptureResult(added, added ? db.lastCaptured() : null, db.lastCaptureEvent());
    }

    /** Boolean compatibility path: preserve the timestamp-aware API's lack of a result-row lookup. */
    boolean apply(CaptureRequest request) {
        boolean added = db.addAutomaticCapture(request);
        // A second own-transfer leg or the bank copy of an app notification may have just arrived.
        if (added) db.repairAround(request.captureAt);
        return added;
    }
}
