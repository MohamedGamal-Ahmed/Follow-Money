package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Existing reimbursement link storage/projection on the borrowed helper, preserving transaction boundaries. */
final class ReimbursementRepository {
    private final ExpenseDb db;

    ReimbursementRepository(ExpenseDb db) { this.db = db; }

    boolean hasReimbursement(long id) {
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM reimbursements WHERE income_id=? OR expense_id=? LIMIT 1",
                new String[]{Long.toString(id),Long.toString(id)})) {return c.moveToFirst();}
    }

    long[] reimbursementLink(long income) {
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT expense_id,amount_minor FROM reimbursements WHERE income_id=?",
                new String[]{Long.toString(income)})) {return c.moveToFirst()?new long[]{c.getLong(0),c.getLong(1)}:null;}
    }

    java.util.Map<Long,Long> reimbursementOffsets() {
        java.util.Map<Long,Long> offsets=new java.util.HashMap<>();
        java.util.Map<Long,Long> remaining=new java.util.HashMap<>();
        try (Cursor c=db.getReadableDatabase().rawQuery("SELECT r.income_id,r.expense_id,r.amount_minor,i.amount_minor,e.amount_minor," +
                "i.kind,e.kind,i.currency,e.currency,e.category FROM reimbursements r " +
                "LEFT JOIN transactions i ON i.id=r.income_id LEFT JOIN transactions e ON e.id=r.expense_id " +
                "ORDER BY i.occurred_at,r.income_id",null)) {
            while (c.moveToNext()) {
                long incomeId=c.getLong(0),expenseId=c.getLong(1);
                boolean valid="income".equals(c.getString(5)) && "expense".equals(c.getString(6)) &&
                        c.getString(7)!=null && c.getString(7).equals(c.getString(8)) &&
                        !MoneyFlow.isCashWithdrawal(c.getString(6),c.getString(9));
                long left=remaining.getOrDefault(expenseId,c.getLong(4));
                long applied=valid?Math.max(0,Math.min(c.getLong(2),Math.min(c.getLong(3),left))):0;
                if (applied>0) {
                    offsets.merge(incomeId,applied,Long::sum);
                    offsets.merge(expenseId,applied,Long::sum);
                }
                remaining.put(expenseId,Math.max(0,left-applied));
            }
        }
        return offsets;
    }

    long reimbursedOf(long expenseId) {
        return reimbursementOffsets().getOrDefault(expenseId,0L);
    }

    Transaction reimbursedExpense(long incomeId) {
        try (Cursor c=db.getReadableDatabase().rawQuery("SELECT expense_id FROM reimbursements WHERE income_id=?",new String[]{Long.toString(incomeId)})) {
            return c.moveToFirst()?db.transactionById(c.getLong(0)):null;
        }
    }

    /** The linked row when it can no longer be repaid: kept as audit history, never applied to totals. */
    Transaction ignoredLinkedExpense(long incomeId) {
        Transaction expense=reimbursedExpense(incomeId);
        return expense!=null && MoneyFlow.isCashWithdrawal(expense.kind,expense.category)?expense:null;
    }

    List<Transaction> reimbursingIncomes(long expenseId) {
        List<Transaction> result=new ArrayList<>();
        try (Cursor c=db.getReadableDatabase().rawQuery("SELECT income_id FROM reimbursements WHERE expense_id=?",new String[]{Long.toString(expenseId)})) {
            while (c.moveToNext()) { Transaction tx=db.transactionById(c.getLong(0)); if (tx!=null) result.add(tx); }
        }
        return result;
    }

    boolean undoReimbursement(long incomeId,long expenseId,long amount) {
        return db.getWritableDatabase().delete("reimbursements","income_id=? AND expense_id=? AND amount_minor=?",
                new String[]{Long.toString(incomeId),Long.toString(expenseId),Long.toString(amount)})==1;
    }

    long linkReimbursement(long incomeId,long expenseId) {
        Transaction income=db.transactionById(incomeId),expense=db.transactionById(expenseId);
        if (income==null || expense==null || !"income".equals(income.kind) || !"expense".equals(expense.kind) ||
                !income.currency.equals(expense.currency) || MoneyFlow.isCashWithdrawal(expense.kind,expense.category))
            throw new IllegalArgumentException("Not a purchase and its repayment");
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            sql.delete("reimbursements","income_id=?",new String[]{Long.toString(incomeId)});
            long left=expense.amountMinor-reimbursedOf(expenseId);
            long applied=Math.min(income.amountMinor,Math.max(0,left));
            if (applied<=0) throw new IllegalArgumentException("Purchase already repaid");
            ContentValues values=new ContentValues();values.put("income_id",incomeId);values.put("expense_id",expenseId);values.put("amount_minor",applied);
            sql.insertOrThrow("reimbursements",null,values);
            sql.setTransactionSuccessful();
            return applied;
        } finally {sql.endTransaction();}
    }

    void unlinkReimbursement(long transactionId) {
        db.getWritableDatabase().delete("reimbursements","income_id=? OR expense_id=?",new String[]{Long.toString(transactionId),Long.toString(transactionId)});
    }
}
