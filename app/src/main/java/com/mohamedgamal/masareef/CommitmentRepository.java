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
        ContentValues values = new ContentValues();
        values.put("merchant", merchant.trim());
        values.put("amount_minor", amount);
        values.put("currency", currency);
        values.put("next_due", nextDue.toString());
        values.put("cycle", cycle);
        values.put("kind", kind);
        if (totalCount == null) values.putNull("total_count"); else values.put("total_count", totalCount);
        values.put("paid_count", Math.max(0, paidCount));
        values.put("remind_days", remindDays);
        values.put("match_text", matchText == null || matchText.isBlank() ? merchant.trim().toLowerCase(Locale.ROOT) :
                matchText.trim().toLowerCase(Locale.ROOT));
        if (paymentSourceId == null) values.putNull("payment_source_id"); else values.put("payment_source_id", paymentSourceId);
        Recurring previous = id == null ? null : recurringById(id);
        values.put("status", previous != null && previous.isDone() ||
                totalCount != null && paidCount >= totalCount ? "done" : "active");
        values.put("due_day", nextDue.getDayOfMonth());
        values.put("needs_review", ExpenseParser.genericMerchant(merchant) ? 1 : 0);
        values.putNull("last_alert_date");
        if (id != null) {
            db.getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
            return id;
        }
        return db.getWritableDatabase().insertWithOnConflict("recurring", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    List<Recurring> recurring() {
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

}
