package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;
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
}
