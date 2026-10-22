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
        // The new pipeline decides; only when it cannot decide does the legacy parser decide this message.
        PipelineDecider.Result decided = PipelineDecider.decide(PipelineDecider.context(db, "live"), request);
        PipelineDecider.Decided pipeline = decided instanceof PipelineDecider.Decided ? (PipelineDecider.Decided) decided : null;
        CaptureResult.Write write = db.addAutomaticCapture(request, pipeline == null ? null : pipeline.decision);
        long capturedId = write.rowId;
        finish(request, write);
        CaptureResult result = new CaptureResult(write, capturedId,
                write.rowId < 0 ? null : db.transactionById(write.rowId), pending, before, db.changes());
        store(db, "live", request, decided, write.reason, result.transaction == null ? null : result.transactionId);
        return result;
    }

    /**
     * Stores the observation beside the ledger row, or, when the pipeline failed, a PIPELINE_FAILED note with its reason
     * (the pipeline is not run again). Diagnostics only: a failure here never changes or fails the capture.
     */
    static void store(ExpenseDb db, String origin, CaptureRequest request, PipelineDecider.Result decided, CaptureResult.Reason written, Long transactionId) {
        try {
            android.database.sqlite.SQLiteDatabase sql = db.getWritableDatabase();
            if (decided instanceof PipelineDecider.Decided) {
                PipelineDecider.Decided d = (PipelineDecider.Decided) decided;
                String guard = d.decision.records ? guard(written) : null;
                ShadowStore.record(sql, origin, request.raw, request.body, request.label, request.captureAt, transactionId,
                        null, "LEGACY_OFF", null, null, null, null, d.observation, guard == null ? null : "LEGACY_GUARD:" + guard);
            }
            else
                ShadowStore.recordFailure(sql, origin, request.body, request.label, request.captureAt, transactionId,
                        ((PipelineDecider.Failed) decided).reason);
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostics only.
        }
    }

    /** A legacy guard that kept a recording decision out of the ledger, or null. */
    static String guard(CaptureResult.Reason written) {
        if (written == null) return null;
        switch (written) {
            case FUTURE_ONLY: return "FUTURE_ONLY";
            case UNSUCCESSFUL: return "UNSUCCESSFUL";
            case NOTICE_RECORDED: case NOTICE_DUPLICATE: return "NOTICE";
            default: return null;
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
