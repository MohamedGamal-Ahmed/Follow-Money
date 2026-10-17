package com.mohamedgamal.masareef;

/**
 * What one automatic capture did, as an immutable value: whether the database changed, the row it created
 * (followed through any merge the repair made right after it), and any cancellation/review event it recorded.
 */
final class CaptureResult {
    final boolean dbChanged;
    final ExpenseDb.Transaction transaction;
    final CaptureEvents.Event event;

    CaptureResult(boolean dbChanged, ExpenseDb.Transaction transaction, CaptureEvents.Event event) {
        this.dbChanged = dbChanged;
        this.transaction = transaction;
        this.event = event;
    }
}
