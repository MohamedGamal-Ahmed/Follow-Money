package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Owns an approved historical SMS batch and its bounded repair; borrows, never closes, ExpenseDb. */
final class ImportBatch {
    private final ExpenseDb db;

    ImportBatch(ExpenseDb db) { this.db = db; }

    /** Same approved selection without repair, retained for existing callers. */
    long applySelected(List<SmsPreview> selected) {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            long before=db.transactionCount();
            for(SmsPreview row:selected)db.addHistoricalSms(row);
            long added=db.transactionCount()-before;
            sql.setTransactionSuccessful();return added;
        }finally {sql.endTransaction();}
    }

    /** Activity-owned call returns after commit; failures propagate before the UI writes success flags. */
    long execute(List<SmsPreview> selected) {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            long before=db.transactionCount();applySelected(selected);
            // Do not run a global transfer repair here: unselected history must stay untouched.
            long first=Long.MAX_VALUE,last=Long.MIN_VALUE;
            for(SmsPreview r:selected){first=Math.min(first,r.captureAt);last=Math.max(last,r.captureAt);}
            db.repairBetweenInCallerTransaction(first-86_400_000L,last+86_400_000L);
            long added=db.transactionCount()-before;
            sql.setTransactionSuccessful();return added;
        }finally {sql.endTransaction();}
    }

    /** The same approved selection without repair, reported per selected row instead of as one ledger delta. */
    ImportResult applySelectedDetailed(List<SmsPreview> selected) {
        SQLiteDatabase sql=db.getWritableDatabase();
        boolean owned=!sql.inTransaction();
        sql.beginTransaction();
        try {
            long revisionBefore=db.changes();
            List<ImportResult.Row> rows=applyRows(selected);
            settleSurvivors(rows);
            long revisionAfter=db.changes();
            sql.setTransactionSuccessful();
            return ImportResult.of(rows,owned,revisionBefore,revisionAfter,ImportResult.Repair.NONE);
        }finally {sql.endTransaction();}
    }

    /**
     * The same batch and bounded repair as {@link #execute}, with each selected row's outcome and the repair it
     * performed reported separately. A failure propagates like {@link #execute}: no result is published and the
     * whole batch rolls back.
     */
    ImportResult executeDetailed(List<SmsPreview> selected) {
        SQLiteDatabase sql=db.getWritableDatabase();
        boolean owned=!sql.inTransaction();
        sql.beginTransaction();
        try {
            long revisionBefore=db.changes();
            List<ImportResult.Row> rows=applyRows(selected);
            ExpenseDb.RepairWrites beforeRepair=db.repairWrites();
            long first=Long.MAX_VALUE,last=Long.MIN_VALUE;
            for(SmsPreview r:selected){first=Math.min(first,r.captureAt);last=Math.max(last,r.captureAt);}
            ExpenseDb.RepairReport report=db.repairBetweenInCallerTransaction(first-86_400_000L,last+86_400_000L);
            ImportResult.Repair repair=repairOf(report,beforeRepair,db.repairWrites());
            settleSurvivors(rows);
            long revisionAfter=db.changes();
            sql.setTransactionSuccessful();
            return ImportResult.of(rows,owned,revisionBefore,revisionAfter,repair);
        }finally {sql.endTransaction();}
    }

    private static ImportResult.Repair repairOf(ExpenseDb.RepairReport report,ExpenseDb.RepairWrites before,ExpenseDb.RepairWrites after) {
        return new ImportResult.Repair(report.mergedRowCount(),report.ownTransfers,
                (int)(after.feeRows-before.feeRows),(int)(after.archives-before.archives),report.movedMinor,report.feesMinor);
    }

    /** A row names a ledger row only while that row still exists once the batch and its repair have finished. */
    private void settleSurvivors(List<ImportResult.Row> rows) {
        for(ImportResult.Row row:rows) row.surviving=row.transactionId!=null&&db.transactionById(row.transactionId)!=null;
    }

    private List<ImportResult.Row> applyRows(List<SmsPreview> selected) {
        List<ImportResult.Row> rows=new ArrayList<>();
        for(SmsPreview row:selected) rows.add(applySelectedRow(row));
        return rows;
    }

    /**
     * One selected row, with its writes observed around its own write. The stored status is read first: after
     * the write, the receipt the row just saved would answer "already known" instead of the state it met.
     */
    private ImportResult.Row applySelectedRow(SmsPreview row) {
        int status=db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt,row.receiptKeySafe);
        ExpenseDb.RowWrites before=db.rowWritesFor(row);
        CaptureResult.Write write=db.addHistoricalSmsObserved(row);
        ImportResult.Effects effects=ImportResult.effectsBetween(before,db.rowWritesFor(row));
        if(write==null) {
            boolean excluded=status==2;
            return new ImportResult.Row(row,excluded?ImportResult.Outcome.EXCLUDED:ImportResult.Outcome.SKIPPED_DUPLICATE,
                    excluded?ImportResult.Reason.EXCLUDED:ImportResult.Reason.SKIPPED_DUPLICATE,
                    db.historicalKnown(row),null,effects);
        }
        ImportResult.Reason reason=ImportResult.reasonFrom(write.reason);
        if(reason==ImportResult.Reason.INSERT_NOT_APPLIED&&db.merchantIgnored(row.title))reason=ImportResult.Reason.MERCHANT_IGNORED;
        Long transactionId=write.rowId<0?null:write.rowId;
        if(write.inserted)return new ImportResult.Row(row,ImportResult.Outcome.CREATED,reason,null,transactionId,effects);
        if(write.reason==CaptureResult.Reason.EXISTING_MATCH)
            return new ImportResult.Row(row,ImportResult.Outcome.MATCHED_EXISTING,reason,null,transactionId,effects);
        // No ledger row: a stored effect (balance, hint, event, commitment link) is not a refusal.
        return new ImportResult.Row(row,effects.storedEffect()?ImportResult.Outcome.NO_LEDGER_CHANGE:ImportResult.Outcome.REFUSED,
                reason,null,null,effects);
    }
}
