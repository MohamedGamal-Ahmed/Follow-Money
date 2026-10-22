package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Owns an approved historical SMS batch and its bounded repair; borrows, never closes, ExpenseDb. */
final class ImportBatch {
    private final ExpenseDb db;

    ImportBatch(ExpenseDb db) { this.db = db; }




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
            for(int i=0;i<rows.size();i++)PipelineDecider.linkLateCharge(db,selected.get(i),rows.get(i).initialTransactionId);
            ExpenseDb.RepairWrites beforeRepair=db.repairWrites();
            long first=Long.MAX_VALUE,last=Long.MIN_VALUE;
            for(SmsPreview r:selected){first=Math.min(first,r.captureAt);last=Math.max(last,r.captureAt);}
            ExpenseDb.RepairReport report=db.repairBetweenInCallerTransaction(first-86_400_000L,last+86_400_000L);
            ImportResult.Repair repair=repairOf(report,beforeRepair,db.repairWrites());
            settleSurvivors(rows,report);
            long revisionAfter=db.changes();
            sql.setTransactionSuccessful();
            return ImportResult.of(rows,owned,revisionBefore,revisionAfter,repair);
        }finally {sql.endTransaction();}
    }

    private static ImportResult.Repair repairOf(ExpenseDb.RepairReport report,ExpenseDb.RepairWrites before,ExpenseDb.RepairWrites after) {
        return new ImportResult.Repair(report.mergedRowCount(),report.ownTransfers,
                (int)(after.feeRows-before.feeRows),(int)(after.archives-before.archives),report.movedMinor,report.feesMinor);
    }

    /**
     * Settles what each row names once the batch and its repair have finished. A row is surviving only while
     * the exact row the write produced is still live, matched by its stored key: SQLite reuses the id of a
     * deleted row, so a row that took an archived row's id (a repair fee) is a different row and never counts.
     * When repair merged the row into another, that merge target is named instead, mirroring the capture path.
     */
    private void settleSurvivors(List<ImportResult.Row> rows,ExpenseDb.RepairReport report) {
        for(ImportResult.Row row:rows) {
            Long initial=row.initialTransactionId;
            if(initial==null){row.transactionId=null;row.surviving=false;continue;}
            if(db.rowHasIdentity(initial,row.identityKey)){row.transactionId=initial;row.surviving=true;continue;}
            row.surviving=false;
            Long target=report==null?null:report.follow(initial);
            boolean merged=target!=null&&target.longValue()!=initial.longValue();
            // A merge target was live when repair kept it, so a plain existence check is sound here.
            row.transactionId=merged&&db.transactionById(target)!=null?target:null;
        }
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
                    db.historicalKnown(row),null,null,effects);
        }
        ImportResult.Reason reason=ImportResult.reasonFrom(write.reason);
        if(reason==ImportResult.Reason.INSERT_NOT_APPLIED&&db.merchantIgnored(row.title))reason=ImportResult.Reason.MERCHANT_IGNORED;
        Long transactionId=write.rowId<0?null:write.rowId;
        // Identity is read now, before repair: the row number alone cannot tell the row from a later reuse of it.
        String identityKey=transactionId==null?null:db.rowIdentityKey(transactionId);
        if(write.inserted)return new ImportResult.Row(row,ImportResult.Outcome.CREATED,reason,null,transactionId,identityKey,effects);
        if(write.reason==CaptureResult.Reason.EXISTING_MATCH)
            return new ImportResult.Row(row,ImportResult.Outcome.MATCHED_EXISTING,reason,null,transactionId,identityKey,effects);
        // No ledger row: a stored effect (balance, hint, event, commitment link) is not a refusal.
        return new ImportResult.Row(row,effects.storedEffect()?ImportResult.Outcome.NO_LEDGER_CHANGE:ImportResult.Outcome.REFUSED,
                reason,null,null,null,effects);
    }
}
