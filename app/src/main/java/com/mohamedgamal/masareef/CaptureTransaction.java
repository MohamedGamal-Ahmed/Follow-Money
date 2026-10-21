package com.mohamedgamal.masareef;

/**
 * Orders live capture, bounded repair, and the resulting snapshot. Borrows the caller's connection:
 * ExpenseDb still owns storage transactions; this use case neither closes it nor adds a transaction.
 * Historical import retains its separate batch/confirmation policy.
 */
final class CaptureTransaction {
    private final ExpenseDb db;

    CaptureTransaction(ExpenseDb db) { this.db = db; }

    CaptureResult execute(CaptureRequest request) {
        boolean pending = db.getWritableDatabase().inTransaction();
        long before = db.changes();
        db.rememberCapture(-1, null);
        CaptureResult.Write write = db.addAutomaticCapture(request);
        long capturedId = write.rowId;
        finish(request, write);
        CaptureResult result = new CaptureResult(write, capturedId,
                write.rowId < 0 ? null : db.transactionById(write.rowId), pending, before, db.changes());
        shadow(request, result);
        return result;
    }

    /** Runs after the legacy decision is final; a shadow failure must never change or fail the capture. */
    private static void shadow(CaptureRequest request, CaptureResult result) {
        try {
            ParserShadow.observe(request.raw, String.valueOf(result.reason),
                    result.transaction == null ? null : result.transaction.merchant, request.captureAt, System.currentTimeMillis());
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostics only.
        }
    }

    /** Boolean compatibility path; acceptance keeps its historical meaning. */
    boolean apply(CaptureRequest request) {
        return execute(request).accepted;
    }

    private void finish(CaptureRequest request, CaptureResult.Write write) {
        // A second own-transfer leg or the bank copy of an app notification may have just arrived.
        if (write.added) {
            ExpenseDb.RepairReport repair = db.repairAround(request.captureAt);
            write.rowId = repair.follow(write.rowId);
            write.repairFailed = repair.failed;
        }
        db.rememberCapture(write.inserted ? write.rowId : -1, write.event);
    }
}
