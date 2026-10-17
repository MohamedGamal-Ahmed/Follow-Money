package com.mohamedgamal.masareef;

import android.database.Cursor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.PaymentSource;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Existing account/source reads and confirmed-parent grouping on the caller's borrowed connection. */
final class AccountRepository {
    private final ExpenseDb db;

    AccountRepository(ExpenseDb db) { this.db = db; }

    int sourceAliasCount(long id) {
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM source_aliases WHERE payment_source_id=?",
                new String[]{Long.toString(id)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    List<PaymentSource> paymentSources() {
        List<PaymentSource> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT p.id,p.name,p.kind,p.last4,p.parent_source_id,p.display_name,p.archived,p.reviewed," +
                "MAX(COALESCE((SELECT MAX(occurred_at) FROM transactions t WHERE t.payment_source_id=p.id OR t.destination_source_id=p.id),0), " +
                "COALESCE((SELECT MAX(occurred_at) FROM balance_snapshots b WHERE b.payment_source_id=p.id),0)) AS activity " +
                "FROM payment_sources p ORDER BY activity DESC,p.id DESC",
                null)) {
            while (cursor.moveToNext()) result.add(new PaymentSource(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.isNull(4) ? null : cursor.getLong(4),cursor.getString(5),cursor.getInt(6)!=0,cursor.getInt(7)!=0,cursor.getLong(8)));
        }
        return result;
    }

    PaymentSource paymentSource(long id) {
        for (PaymentSource source : paymentSources()) if (source.id == id) return source;
        return null;
    }

    long accountId(long id) {
        try (Cursor cursor = db.getReadableDatabase().rawQuery("SELECT COALESCE(parent_source_id,id) FROM payment_sources WHERE id=?",
                new String[]{Long.toString(id)})) { return cursor.moveToFirst() ? cursor.getLong(0) : id; }
    }

    java.util.Map<Long,List<Transaction>> accountTransactionGroups(LocalDate from,LocalDate to,String currency) {
        java.util.Map<Long,Long> roots=new java.util.HashMap<>();
        for(PaymentSource source:paymentSources()) roots.put(source.id,source.parentSourceId==null?source.id:source.parentSourceId);
        java.util.Map<Long,List<Transaction>> groups=new java.util.LinkedHashMap<>();
        for(Transaction tx:db.transactionsBetween(from,to)) {
            if(!currency.equals(tx.currency)) continue;
            Long debit=roots.get(tx.paymentSourceId);
            Long credit="transfer".equals(tx.kind)?roots.get(tx.destinationSourceId):null;
            if(debit!=null) groups.computeIfAbsent(debit,k->new ArrayList<>()).add(tx);
            if(credit!=null && !credit.equals(debit)) groups.computeIfAbsent(credit,k->new ArrayList<>()).add(tx);
        }
        return groups;
    }

    List<PaymentSource> accountSources(boolean includeArchived) {
        List<PaymentSource> result=new ArrayList<>();
        for (PaymentSource source:paymentSources())
            if(source.parentSourceId==null && (includeArchived || !source.archived)) result.add(source);
        return result;
    }

    List<PaymentSource> selectableSources() {
        List<PaymentSource> result=new ArrayList<>();
        for(PaymentSource source:paymentSources()) {
            PaymentSource account=source.parentSourceId==null?source:paymentSource(source.parentSourceId);
            if(!source.archived && account!=null && !account.archived) result.add(source);
        }
        return result;
    }

    List<PaymentSource> linkedCards(long accountId) {
        List<PaymentSource> result=new ArrayList<>();
        for(PaymentSource source:paymentSources())
            if(source.parentSourceId!=null && source.parentSourceId==accountId) result.add(source);
        return result;
    }

    String sourceLabel(long id) {
        PaymentSource source=paymentSource(id);
        if(source==null) return "حساب غير محدد";
        String label=source.name+" — "+(source.displayName==null || source.displayName.isBlank()?source.kind:source.displayName)+
                (source.last4==null?"":" •"+source.last4);
        if(source.parentSourceId!=null) {
            PaymentSource account=paymentSource(source.parentSourceId);
            if(account!=null) label+=" ← حساب •"+(account.last4==null?"بدون رقم":account.last4);
        }
        return label+(source.archived?" · مؤرشف":"");
    }

    long sourceLastActivity(long sourceId) {
        long root=accountId(sourceId),last=0;
        for(PaymentSource source:paymentSources())
            if(source.id==root || source.parentSourceId!=null && source.parentSourceId==root) last=Math.max(last,source.lastActivityAt);
        return last;
    }
}
