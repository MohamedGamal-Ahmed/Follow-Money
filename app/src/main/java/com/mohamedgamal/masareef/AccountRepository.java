package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.PaymentSource;
import com.mohamedgamal.masareef.ExpenseDb.BalanceSnapshot;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Existing account/source storage primitives and confirmed-parent grouping on the caller's borrowed connection. */
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

    void addSourceAlias(String identity, long sourceId) {
        ContentValues values = new ContentValues();
        values.put("identity", identity);
        values.put("payment_source_id", sourceId);
        db.getWritableDatabase().insertWithOnConflict("source_aliases", null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    long addPaymentSource(String name, String kind, String last4) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim()); values.put("kind", kind); values.put("last4", last4);
        long id = db.getWritableDatabase().insert("payment_sources", null, values);
        String identity = SourceIdentity.key(name, last4);
        if (id > 0 && identity != null) addSourceAlias(identity, id);
        return id;
    }

    void mergeSources(long keepId, long removeId) {
        if(new CardSettlements(db).sourceRelated(keepId)||new CardSettlements(db).sourceRelated(removeId))throw new IllegalArgumentException("Undo card repayment allocations before merging accounts");
        if (keepId == removeId) return;
        if(!linkedCards(keepId).isEmpty() || !linkedCards(removeId).isEmpty() || accountId(keepId)!=keepId || accountId(removeId)!=removeId)
            throw new IllegalArgumentException("Unlink cards before merging message identities");
        SQLiteDatabase writable = db.getWritableDatabase();
        writable.beginTransaction();
        try {
            try (Cursor cursor = writable.rawQuery("SELECT id FROM payment_sources WHERE id IN (?,?)",
                    new String[]{Long.toString(keepId), Long.toString(removeId)})) {
                if (cursor.getCount() != 2) return;
            }
            writable.execSQL("UPDATE transactions SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE transactions SET destination_source_id=? WHERE destination_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE balance_snapshots SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE source_aliases SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.delete("payment_sources", "id=?", new String[]{Long.toString(removeId)});
            writable.setTransactionSuccessful();
        } finally { writable.endTransaction(); }
    }

    void linkCardToAccount(long cardId, long accountId) {
        PaymentSource card=paymentSource(cardId), account=paymentSource(accountId);
        if (card==null || account==null || cardId==accountId || !"بطاقة".equals(card.kind) ||
                !"حساب".equals(account.kind) || account.parentSourceId!=null || card.archived || account.archived ||
                !java.util.Objects.equals(SourceIdentity.bank(card.name),SourceIdentity.bank(account.name)))
            throw new IllegalArgumentException("Choose a debit card and its account in the same bank");
        SQLiteDatabase writable=db.getWritableDatabase();writable.beginTransaction();
        try {
            ContentValues cardValues=new ContentValues();cardValues.put("parent_source_id",accountId);cardValues.put("reviewed",1);
            writable.update("payment_sources",cardValues,"id=?",new String[]{Long.toString(cardId)});
            ContentValues accountValues=new ContentValues();accountValues.put("reviewed",1);
            writable.update("payment_sources",accountValues,"id=?",new String[]{Long.toString(accountId)});
            writable.setTransactionSuccessful();
        } finally { writable.endTransaction(); }
    }

    void unlinkCard(long cardId) {
        ContentValues values=new ContentValues();values.putNull("parent_source_id");values.put("reviewed",0);
        db.getWritableDatabase().update("payment_sources",values,"id=?",new String[]{Long.toString(cardId)});
    }

    void updateSourceProfile(long id,String displayName,boolean archived,boolean reviewed) {
        updateSourceProfileRow(id,displayName,archived,reviewed);
    }

    int updateSourceProfileRow(long id,String displayName,boolean archived,boolean reviewed) {
        ContentValues values=new ContentValues();values.put("display_name",displayName==null?"":displayName.trim());
        values.put("archived",archived?1:0);values.put("reviewed",reviewed?1:0);
        return db.getWritableDatabase().update("payment_sources",values,"id=?",new String[]{Long.toString(id)});
    }

    boolean hasSourceAlias(String identity) {
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT 1 FROM source_aliases WHERE identity=?",new String[]{identity})) {
            return cursor.moveToFirst();
        }
    }

    Long sourceAliasOwner(String identity) {
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT payment_source_id FROM source_aliases WHERE identity=?",new String[]{identity})) {
            return cursor.moveToFirst()?cursor.getLong(0):null;
        }
    }

    int updateSourceBankRow(long id,String bank,String displayName) {
        ContentValues values=new ContentValues();values.put("name",bank);values.put("display_name",displayName.trim());values.put("reviewed",1);
        return db.getWritableDatabase().update("payment_sources",values,"id=?",new String[]{Long.toString(id)});
    }

    void deletePaymentSource(long id) {
        if(new CardSettlements(db).sourceRelated(id))throw new IllegalArgumentException("Undo card repayment allocations before deleting accounts");
        ContentValues values = new ContentValues(); values.putNull("payment_source_id");
        db.getWritableDatabase().update("transactions", values, "payment_source_id=?", new String[]{Long.toString(id)});
        db.getWritableDatabase().delete("balance_snapshots", "payment_source_id=?", new String[]{Long.toString(id)});
        db.getWritableDatabase().delete("source_aliases", "payment_source_id=?", new String[]{Long.toString(id)});
        db.getWritableDatabase().delete("payment_sources", "id=?", new String[]{Long.toString(id)});
    }

    void putManualBalance(long sourceId, String currency, long balanceMinor, long occurredAt) {
        putBalance(sourceId, currency, balanceMinor, occurredAt, System.currentTimeMillis(),
                "يدوي", "manual-balance:" + java.util.UUID.randomUUID());
    }

    void putBalance(long sourceId, String currency, long balanceMinor, long occurredAt,
                            long receivedAt, String source, String uniqueKey) {
        if (balanceMinor < 0) return;
        ContentValues values = new ContentValues();
        values.put("payment_source_id", sourceId);
        values.put("currency", currency);
        values.put("balance_minor", balanceMinor);
        values.put("occurred_at", occurredAt);
        values.put("received_at", receivedAt);
        values.put("source", source);
        values.put("unique_key", uniqueKey);
        SQLiteDatabase sql = db.getWritableDatabase();
        if (sql.insertWithOnConflict("balance_snapshots", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1) {
            ContentValues corrected = new ContentValues();
            corrected.put("occurred_at", occurredAt);
            corrected.put("received_at", receivedAt);
            corrected.put("balance_minor", balanceMinor);
            corrected.put("payment_source_id", sourceId);
            corrected.put("currency", currency);
            sql.update("balance_snapshots", corrected, "unique_key=?", new String[]{uniqueKey});
        }
    }

    BalanceSnapshot latestBalance(long sourceId, String currency) {
        sourceId = accountId(sourceId);
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT balance_minor,occurred_at,received_at,source,payment_source_id FROM balance_snapshots " +
                "WHERE payment_source_id IN (SELECT id FROM payment_sources WHERE COALESCE(parent_source_id,id)=CAST(? AS INTEGER)) " +
                "AND currency=? ORDER BY occurred_at DESC,received_at DESC,id DESC LIMIT 1",
                new String[]{Long.toString(sourceId), currency})) {
            if (!cursor.moveToFirst()) return null;
            return new BalanceSnapshot(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
                    false, cursor.getLong(4), cursor.getLong(1), 0);
        }
    }

    BalanceSnapshot currentBalance(long sourceId, String currency) {
        sourceId = accountId(sourceId);
        BalanceSnapshot anchor = latestBalance(sourceId, currency);
        if (anchor == null) return null;
        long amount = anchor.amountMinor;
        int movements = 0;
        long updatedAt = anchor.occurredAt;
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT t.occurred_at,t.received_at,t.kind,COALESCE(p.parent_source_id,p.id),COALESCE(d.parent_source_id,d.id),t.amount_minor " +
                        "FROM transactions t LEFT JOIN payment_sources p ON p.id=t.payment_source_id " +
                        "LEFT JOIN payment_sources d ON d.id=t.destination_source_id " +
                        "WHERE t.currency=? AND (COALESCE(p.parent_source_id,p.id)=CAST(? AS INTEGER) OR COALESCE(d.parent_source_id,d.id)=CAST(? AS INTEGER)) " +
                        "AND (t.occurred_at>? OR (t.occurred_at=? AND t.received_at>?)) " +
                        "ORDER BY t.occurred_at,t.received_at,t.id",
                new String[]{currency, Long.toString(sourceId), Long.toString(sourceId),
                        Long.toString(anchor.occurredAt), Long.toString(anchor.occurredAt),
                        Long.toString(anchor.receivedAt)})) {
            while (cursor.moveToNext()) {
                if (!BalanceTimeline.after(cursor.getLong(0), cursor.getLong(1),
                        anchor.occurredAt, anchor.receivedAt)) continue;
                long delta = BalanceTimeline.delta(cursor.getString(2),
                        cursor.isNull(3) ? -1 : cursor.getLong(3),
                        cursor.isNull(4) ? -1 : cursor.getLong(4), sourceId, cursor.getLong(5));
                if (delta == 0) continue;
                amount += delta;
                movements++;
                updatedAt = Math.max(updatedAt, cursor.getLong(0));
            }
        }
        return movements == 0 ? anchor : new BalanceSnapshot(amount, anchor.occurredAt,
                anchor.receivedAt, anchor.source, true, anchor.anchorSourceId, updatedAt, movements);
    }

    List<BalanceSnapshot> balanceHistory(long sourceId, String currency, int limit) {
        sourceId = accountId(sourceId);
        List<BalanceSnapshot> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT balance_minor,occurred_at,received_at,source FROM balance_snapshots " +
                "WHERE payment_source_id IN (SELECT id FROM payment_sources WHERE COALESCE(parent_source_id,id)=CAST(? AS INTEGER)) " +
                "AND currency=? ORDER BY occurred_at DESC,received_at DESC,id DESC LIMIT ?",
                new String[]{Long.toString(sourceId), currency, Integer.toString(limit)})) {
            while (cursor.moveToNext()) result.add(new BalanceSnapshot(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3)));
        }
        return result;
    }
}
