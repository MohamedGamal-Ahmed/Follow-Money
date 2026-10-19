package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;
import com.mohamedgamal.masareef.ExpenseDb.ReimbursementPreview;

/** Existing purchase selection, read-only preview and confirmation on the caller's borrowed helper. */
final class ReimbursementUseCase {
    private final ExpenseDb db;

    ReimbursementUseCase(ExpenseDb db) { this.db = db; }

    List<Transaction> reimbursementCandidates(Transaction income,boolean older) {
        List<Transaction> result=new ArrayList<>();
        if (income==null || !"income".equals(income.kind)) return result;
        java.util.Map<Long,Long> offsets=db.reimbursementOffsets();
        LocalDate day=Instant.ofEpochMilli(income.when).atZone(ZoneId.systemDefault()).toLocalDate();
        for (Transaction tx:db.transactionsBetween(older?LocalDate.of(1970,1,1):day,older?day:day.plusDays(1)))
            if ("expense".equals(tx.kind) && tx.currency.equals(income.currency) && reimbursementPrecedes(tx,income) &&
                    ExpenseDb.effectiveAmount(tx,offsets)>0 && isReimbursable(tx)) result.add(tx);
        result.sort((a,b)->{
            int byTime=Long.compare(b.when,a.when);
            return byTime!=0?byTime:Long.compare(b.id,a.id);
        });
        return result;
    }

    /** A repayment offsets a purchase; open transfers and cash withdrawals are not purchases. */
    private static boolean isReimbursable(Transaction expense) {
        MoneyFlow.Bucket bucket=MoneyFlow.bucket(expense.kind,expense.category);
        return bucket!=MoneyFlow.Bucket.OPEN_TRANSFER && bucket!=MoneyFlow.Bucket.CASH_WITHDRAWAL;
    }

    private static boolean reimbursementPrecedes(Transaction expense,Transaction income) {
        if("date_only".equals(expense.timePrecision)||"date_only".equals(income.timePrecision)) {
            ZoneId zone=ZoneId.systemDefault();
            return !Instant.ofEpochMilli(expense.when).atZone(zone).toLocalDate()
                    .isAfter(Instant.ofEpochMilli(income.when).atZone(zone).toLocalDate());
        }
        return expense.when<=income.when+60_000;
    }

    ReimbursementPreview previewReimbursement(long incomeId,long expenseId) {
        Transaction income=db.transactionById(incomeId),expense=db.transactionById(expenseId);
        if(income==null || expense==null || !"income".equals(income.kind) || !"expense".equals(expense.kind) ||
                !income.currency.equals(expense.currency) || !reimbursementPrecedes(expense,income) || db.reimbursedExpense(incomeId)!=null ||
                !isReimbursable(expense))
            throw new IllegalArgumentException("Purchase or repayment changed");
        java.util.Map<Long,Long> offsets=db.reimbursementOffsets();
        long left=ExpenseDb.effectiveAmount(expense,offsets),applied=Math.min(left,income.amountMinor);
        if(applied<=0)throw new IllegalArgumentException("Nothing left to repay");
        LocalDate day=Instant.ofEpochMilli(expense.when).atZone(ZoneId.systemDefault()).toLocalDate();
        MoneyTotals totals=new MoneyTotals();
        for(Transaction tx:db.transactionsBetween(day,day.plusDays(1)))totals.add(tx.currency,tx.kind,tx.category,ExpenseDb.effectiveAmount(tx,offsets));
        MoneyTotals.Totals currencyTotals=totals.currencies.get(expense.currency);
        long spending=currencyTotals==null?0:currencyTotals.living;
        String fingerprint=income.when+":"+income.amountMinor+":"+income.currency+":"+income.merchant+":"+
                expense.when+":"+expense.amountMinor+":"+expense.category+":"+expense.merchant+":"+left+":"+spending+":"+
                income.timePrecision+":"+expense.timePrecision;
        return new ReimbursementPreview(income,expense,applied,left-applied,
                spending-(MoneyFlow.bucket(expense.kind,expense.category)==MoneyFlow.Bucket.LIVING?applied:0),fingerprint);
    }

    long confirmReimbursement(ReimbursementPreview preview) {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            ReimbursementPreview current=previewReimbursement(preview.income.id,preview.expense.id);
            if(!current.fingerprint.equals(preview.fingerprint))throw new IllegalArgumentException("Preview changed");
            long applied=db.linkReimbursement(current.income.id,current.expense.id);
            sql.setTransactionSuccessful();return applied;
        } finally {sql.endTransaction();}
    }
}
