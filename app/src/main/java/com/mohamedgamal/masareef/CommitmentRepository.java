package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mohamedgamal.masareef.ExpenseDb.Recurring;

/** Existing commitment storage primitives on the caller's borrowed helper and transaction. */
final class CommitmentRepository {
    private final ExpenseDb db;

    CommitmentRepository(ExpenseDb db) { this.db = db; }

    boolean addRecurring(String merchant, long amount, String currency, LocalDate nextDue) {
        return addRecurring(merchant, amount, currency, nextDue, "monthly", "subscription");
    }

    boolean addRecurring(String merchant, long amount, String currency, LocalDate nextDue,
                                String cycle, String kind) {
        return putCommitment(null, merchant, amount, currency, nextDue, cycle, kind, null, 0, 1,
                merchant.toLowerCase(Locale.ROOT), null) != -1;
    }

    /** Inserts or updates an installment, subscription or bill. Returns the row id, or -1 for a duplicate. */
    long putCommitment(Long id, String merchant, long amount, String currency, LocalDate nextDue,
                              String cycle, String kind, Integer totalCount, int paidCount, int remindDays,
                              String matchText, Long paymentSourceId) {
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            long saved=putCommitmentRow(id,merchant,amount,currency,nextDue,cycle,kind,totalCount,paidCount,remindDays,matchText,paymentSourceId);
            if(own)sql.setTransactionSuccessful();return saved;
        }finally{if(own)sql.endTransaction();}
    }
    private long putCommitmentRow(Long id,String merchant,long amount,String currency,LocalDate nextDue,
                                 String cycle,String kind,Integer totalCount,int paidCount,int remindDays,
                                 String matchText,Long paymentSourceId){
        if(merchant==null||merchant.isBlank()||amount<=0)throw new IllegalArgumentException("Invalid commitment");
        ContentValues values = new ContentValues();
        values.put("merchant", merchant.trim());
        values.put("amount_minor", amount);
        values.put("currency", currency);
        values.put("next_due", nextDue.toString());
        values.put("cycle", cycle);
        values.put("kind", kind);
        if (totalCount == null) values.putNull("total_count"); else values.put("total_count", totalCount);
        Recurring before = id == null ? null : recurringById(id);
        Recurring stored = id == null ? null : db.storedRecurringById(id);
        // The editor submits the displayed total; only its explicit delta changes the declared baseline.
        int baselinePaid=stored==null?Math.max(0,paidCount):Math.max(0,stored.paidCount+paidCount-before.paidCount);
        values.put("paid_count",baselinePaid);
        List<CommitmentPayments.Cycle> cycles=stored==null?new ArrayList<>():new CommitmentPayments(db).cycles(id);
        if(totalCount!=null&&baselinePaid+cycles.size()>totalCount)throw new IllegalArgumentException("عدد الأقساط أقل من الدورات المحفوظة");
        if(stored!=null&&nextDue.equals(before.nextDue))values.put("next_due",stored.nextDue.toString());
        else for(CommitmentPayments.Cycle saved:cycles)if(saved.remaining()>0||!nextDue.isAfter(saved.due))
            throw new IllegalArgumentException("راجع تسويات الدورات قبل تغيير تاريخ الجدول");
        values.put("remind_days", remindDays);
        values.put("match_text", matchText == null || matchText.isBlank() ? merchant.trim().toLowerCase(Locale.ROOT) :
                matchText.trim().toLowerCase(Locale.ROOT));
        if (paymentSourceId == null) values.putNull("payment_source_id"); else values.put("payment_source_id", paymentSourceId);
        Recurring previous = stored;
        values.put("status", previous != null && previous.isDone() ||
                totalCount != null && baselinePaid >= totalCount ? "done" : "active");
        values.put("due_day",stored!=null&&nextDue.equals(before.nextDue)?stored.dueDay:nextDue.getDayOfMonth());
        values.put("needs_review", ExpenseParser.genericMerchant(merchant) ? 1 : 0);
        values.putNull("last_alert_date");
        if (id != null) {
            db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
            return id;
        }
        values.put("settlement_key",java.util.UUID.randomUUID().toString());
        return db.getWritableDatabase().insertWithOnConflict("recurring", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    List<Recurring> recurring() {
        SQLiteDatabase sql=db.getReadableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransactionNonExclusive();
        try{
            List<Recurring> rows=new ArrayList<>();CommitmentPayments payments=new CommitmentPayments(db);
            for(Recurring row:storedRecurring())rows.add(payments.project(row));
            rows.sort(java.util.Comparator.comparing(Recurring::isDone).thenComparing(r->r.nextDue));
            if(own)sql.setTransactionSuccessful();return rows;
        }finally{if(own)sql.endTransaction();}
    }

    List<Recurring> storedRecurring() {
        List<Recurring> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().query("recurring",
                new String[]{"id", "merchant", "amount_minor", "currency", "next_due", "last_alert_date", "cycle", "kind",
                        "total_count", "paid_count", "remind_days", "match_text", "payment_source_id", "last_paid_date", "status", "due_day", "needs_review"},
                null, null, null, null, "status='done' ASC, next_due ASC")) {
            while (cursor.moveToNext()) result.add(new Recurring(cursor.getLong(0), cursor.getString(1),
                    cursor.getLong(2), cursor.getString(3), LocalDate.parse(cursor.getString(4)),
                    cursor.getString(5), cursor.getString(6), cursor.getString(7),
                    cursor.isNull(8) ? null : cursor.getInt(8), cursor.getInt(9), cursor.getInt(10),
                    cursor.getString(11), cursor.isNull(12) ? null : cursor.getLong(12),
                    cursor.getString(13), cursor.getString(14), cursor.isNull(15) ? 0 : cursor.getInt(15), cursor.getInt(16)!=0));
        }
        return result;
    }

    Recurring recurringById(long id) {
        for (Recurring item : recurring()) if (item.id == id) return item;
        return null;
    }

    void setDueDay(long id, int day) {
        ContentValues values = new ContentValues();
        values.put("due_day", Math.max(1, Math.min(31, day)));
        db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    void setRemindDays(long id, int days) {
        ContentValues values = new ContentValues();
        values.put("remind_days", days);
        values.putNull("last_alert_date");
        db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    void removeRecurring(long id) {
        db.getWritableDatabase().delete("recurring", "id=?", new String[]{Long.toString(id)});
    }

    void markAlerted(long id, LocalDate today) {
        ContentValues values = new ContentValues();
        values.put("last_alert_date", today.toString());
        db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    // Legacy payment-state writes; rule changes and settlement cycles remain separate work.
    int normalizeEarlyPayments() {
        int moved = 0;
        java.util.Map<Long,String> coveredDue = new java.util.HashMap<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT id,last_paid_due FROM recurring WHERE last_paid_due IS NOT NULL", null)) {
            while (c.moveToNext()) coveredDue.put(c.getLong(0), c.getString(1));
        }
        for (Recurring item : storedRecurring()) {
            if (item.isDone() || item.lastPaidDate == null) continue;
            LocalDate paid;
            try { paid = LocalDate.parse(item.lastPaidDate); } catch (RuntimeException invalid) { continue; }
            LocalDate due = item.nextDue;
            String known = coveredDue.get(item.id);
            boolean covered;
            if (known != null) {
                // We know which due date the last payment settled: the next one must come after it.
                covered = !due.isAfter(LocalDate.parse(known));
            } else {
                // Older rows: a payment shortly before the due date, or earlier in the same month for a monthly bill
                // (e.g. a card statement paid on the 10th for the 26th), settles that due date.
                long window = "weekly".equals(item.cycle) ? 2 : "yearly".equals(item.cycle) ? 15 : 7;
                long early = java.time.temporal.ChronoUnit.DAYS.between(paid, due);
                boolean sameMonth = "monthly".equals(item.cycle) && java.time.YearMonth.from(paid).equals(java.time.YearMonth.from(due));
                covered = early >= 0 && (early <= window || sameMonth);
            }
            if (!covered) continue;
            LocalDate next = item.nextAfter(due);
            ContentValues values = new ContentValues();
            values.put("next_due", next.toString());
            values.put("last_paid_due", due.toString());
            values.putNull("last_alert_date");
            db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(item.id)});
            moved++;
        }
        return moved;
    }

    boolean markRecurringPaid(long id, LocalDate day) {
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            normalizeEarlyPayments();
            boolean changed=new CommitmentPayments(db).confirm(id,day,"manual:"+java.util.UUID.randomUUID(),"manual");
            if(own)sql.setTransactionSuccessful();return changed;
        }finally{if(own)sql.endTransaction();}
    }

    boolean finishRecurring(long id, boolean allInstallmentsPaid) {
        Recurring item = recurringById(id);
        if (item == null || item.isDone()) return false;
        ContentValues values = new ContentValues();
        values.put("status", "done");
        values.putNull("last_alert_date");
        if (allInstallmentsPaid && "installment".equals(item.kind) && item.totalCount != null)
            values.put("paid_count", item.totalCount);
        return db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)}) == 1;
    }

    boolean reopenRecurring(long id) {
        Recurring item = recurringById(id);
        if (item == null || !item.isDone() ||
                item.totalCount != null && item.paidCount >= item.totalCount) return false;
        ContentValues values = new ContentValues();
        values.put("status", "active");
        values.putNull("last_alert_date");
        return db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)}) == 1;
    }
}
