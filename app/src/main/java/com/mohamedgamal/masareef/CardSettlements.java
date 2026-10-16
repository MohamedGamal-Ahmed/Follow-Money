package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Explicit allocation of a card repayment. Original purchases remain gross consumption records. */
final class CardSettlements {
    private final ExpenseDb db;
    CardSettlements(ExpenseDb db){this.db=db;}
    static void create(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS card_settlements(transaction_id INTEGER PRIMARY KEY,card_id INTEGER NOT NULL,original_payload TEXT NOT NULL,confirmed_at INTEGER NOT NULL)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS card_settlement_allocations(settlement_id INTEGER NOT NULL,purchase_id INTEGER NOT NULL,amount_minor INTEGER NOT NULL CHECK(amount_minor>0),PRIMARY KEY(settlement_id,purchase_id))");
        sql.execSQL("CREATE INDEX IF NOT EXISTS card_purchase_allocation ON card_settlement_allocations(purchase_id)");
    }
    boolean related(long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM card_settlements WHERE transaction_id=? UNION ALL SELECT 1 FROM card_settlement_allocations WHERE purchase_id=? OR purchase_id=(SELECT fee_parent_id FROM transactions WHERE id=?) LIMIT 1",new String[]{Long.toString(id),Long.toString(id),Long.toString(id)})){return c.moveToFirst();}}
    boolean sourceRelated(long id){
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT s.card_id,s.original_payload,t.payment_source_id,t.destination_source_id FROM card_settlements s JOIN transactions t ON t.id=s.transaction_id",null)){
            while(c.moveToNext()){
                if(c.getLong(0)==id||!c.isNull(2)&&c.getLong(2)==id||!c.isNull(3)&&c.getLong(3)==id)return true;
                try{JSONObject original=new JSONObject(c.getString(1));for(String field:new String[]{"payment_source_id","destination_source_id"})if(!original.isNull(field)&&original.optLong(field,-1)==id)return true;}catch(org.json.JSONException e){throw new IllegalStateException(e);}
            }return false;
        }
    }
    boolean isSettlement(long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM card_settlements WHERE transaction_id=?",new String[]{Long.toString(id)})){return c.moveToFirst();}}
    static void removeRelations(SQLiteDatabase sql,long id){
        sql.delete("card_settlement_allocations","settlement_id=? OR purchase_id=?",new String[]{Long.toString(id),Long.toString(id)});
        sql.delete("card_settlements","transaction_id=?",new String[]{Long.toString(id)});
    }
    JSONObject history(long id){
        JSONObject data=new JSONObject();try{
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT card_id,original_payload FROM card_settlements WHERE transaction_id=?",new String[]{Long.toString(id)})){
                if(c.moveToFirst()){data.put("card_id",c.getLong(0));data.put("original_payload",new JSONObject(c.getString(1)));}
            }
            org.json.JSONArray allocations=new org.json.JSONArray();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT purchase_id,amount_minor FROM card_settlement_allocations WHERE settlement_id=? ORDER BY purchase_id",new String[]{Long.toString(id)})){
                while(c.moveToNext()){JSONObject row=new JSONObject();row.put("purchase_id",c.getLong(0));row.put("amount_minor",c.getLong(1));allocations.put(row);}
            }data.put("allocations",allocations);return data;
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    long allocated(long purchase){return scalar("SELECT COALESCE(SUM(amount_minor),0) FROM card_settlement_allocations WHERE purchase_id=?",purchase);}
    long allocatedBy(long payment){return scalar("SELECT COALESCE(SUM(amount_minor),0) FROM card_settlement_allocations WHERE settlement_id=?",payment);}
    private long scalar(String query,long id){try(Cursor c=db.getReadableDatabase().rawQuery(query,new String[]{Long.toString(id)})){return c.moveToFirst()?c.getLong(0):0;}}
    long cost(long purchase){ExpenseDb.Transaction tx=db.transactionById(purchase);return tx==null?0:Math.addExact(tx.amountMinor,db.transferFee(purchase));}
    private ExpenseDb.PaymentSource source(long id){for(ExpenseDb.PaymentSource s:db.paymentSources())if(s.id==id)return s;throw new IllegalArgumentException("Source no longer exists");}
    List<ExpenseDb.Transaction> purchases(long card,String currency,long before){
        List<ExpenseDb.Transaction> result=new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE payment_source_id=? AND currency=? AND kind='expense' AND fee_parent_id IS NULL AND occurred_at<=? ORDER BY occurred_at DESC,id DESC",new String[]{Long.toString(card),currency,Long.toString(before)})){
            while(c.moveToNext()){ExpenseDb.Transaction tx=db.transactionById(c.getLong(0));if(tx!=null&&allocated(tx.id)<cost(tx.id))result.add(tx);}
        }return result;
    }
    static final class Preview {
        final ExpenseDb.Transaction payment;final long from,card,allocated,remainder;final LinkedHashMap<Long,Long> allocations;final String signature;
        Preview(ExpenseDb.Transaction payment,long from,long card,LinkedHashMap<Long,Long> allocations,long allocated,String signature){this.payment=payment;this.from=from;this.card=card;this.allocations=allocations;this.allocated=allocated;this.remainder=payment.amountMinor-allocated;this.signature=signature;}
    }
    Preview preview(long paymentId,long from,long card,Map<Long,Long> selections){
        ExpenseDb.Transaction payment=db.transactionById(paymentId);ExpenseDb.PaymentSource origin=source(from),destination=source(card);
        if(payment==null||isSettlement(paymentId)||!"بطاقة ائتمان".equals(destination.kind)||destination.archived||origin.archived
                ||!("حساب".equals(origin.kind)||"محفظة".equals(origin.kind))||db.accountId(from)==db.accountId(card)
                ||!("transfer".equals(payment.kind)||"expense".equals(payment.kind)&&"تحويلات".equals(payment.category)))throw new IllegalArgumentException("Select a live outgoing payment and your credit card");
        if(payment.destinationSourceId!=null&&payment.destinationSourceId!=card)throw new IllegalArgumentException("Existing destination differs from this card");
        if(payment.paymentSourceId!=null&&db.accountId(payment.paymentSourceId)!=db.accountId(from))throw new IllegalArgumentException("Source differs from the bank debit");
        if(db.transferFee(paymentId)>0)throw new IllegalArgumentException("السداد ده مرتبط برسوم تحويل. الربط غير متاح حاليًا للحفاظ على الرسوم؛ راجع أصل التحويل ورسومه من تفاصيل العملية.");
        if(db.reimbursedExpense(paymentId)!=null||db.reimbursedOf(paymentId)>0)throw new IllegalArgumentException("Review related records before settling the card");
        LinkedHashMap<Long,Long> allocations=new LinkedHashMap<>();long total=0;StringBuilder signature=new StringBuilder(snapshot(paymentId).toString()).append('|').append(sourceFingerprint(origin)).append('|').append(sourceFingerprint(destination));
        for(Map.Entry<Long,Long> entry:new java.util.TreeMap<>(selections).entrySet()){
            ExpenseDb.Transaction purchase=db.transactionById(entry.getKey());long amount=entry.getValue()==null?0:entry.getValue();
            if(purchase==null||!"expense".equals(purchase.kind)||purchase.paymentSourceId==null||purchase.paymentSourceId!=card
                    ||!payment.currency.equals(purchase.currency)||!precedes(purchase,payment)||amount<=0||amount>cost(purchase.id)-allocated(purchase.id))throw new IllegalArgumentException("Purchase allocation changed");
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",new String[]{Long.toString(purchase.id)})){if(!c.moveToFirst()||!c.isNull(0))throw new IllegalArgumentException("Select the original purchase, not its fee");}
            total=Math.addExact(total,amount);allocations.put(purchase.id,amount);signature.append('|').append(snapshot(purchase.id)).append('|').append(cost(purchase.id)).append('|').append(allocated(purchase.id));
        }
        if(total>payment.amountMinor)throw new IllegalArgumentException("Allocations exceed the repayment");
        return new Preview(payment,from,card,allocations,total,signature.append('|').append(from).append('|').append(card).append('|').append(allocations).toString());
    }
    private static String sourceFingerprint(ExpenseDb.PaymentSource source){return source.id+":"+source.name+":"+source.kind+":"+source.last4+":"+source.parentSourceId+":"+source.displayName;}
    private static boolean precedes(ExpenseDb.Transaction purchase,ExpenseDb.Transaction payment){
        if("date_only".equals(purchase.timePrecision)||"date_only".equals(payment.timePrecision)){
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();return !java.time.Instant.ofEpochMilli(purchase.when).atZone(zone).toLocalDate().isAfter(java.time.Instant.ofEpochMilli(payment.when).atZone(zone).toLocalDate());
        }return purchase.when<=payment.when;
    }
    private JSONObject snapshot(long id){
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM transactions WHERE id=?",new String[]{Long.toString(id)})){
            if(!c.moveToFirst())throw new IllegalArgumentException("Transaction no longer exists");JSONObject row=new JSONObject();
            for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));return row;
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    void confirm(Preview accepted){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            Preview current=preview(accepted.payment.id,accepted.from,accepted.card,accepted.allocations);
            if(!current.signature.equals(accepted.signature))throw new IllegalArgumentException("Financial records changed; review again");
            ContentValues record=new ContentValues();record.put("transaction_id",current.payment.id);record.put("card_id",current.card);record.put("original_payload",snapshot(current.payment.id).toString());record.put("confirmed_at",System.currentTimeMillis());sql.insertOrThrow("card_settlements",null,record);
            ContentValues v=new ContentValues();v.put("kind","transfer");v.put("category","تحويلات");v.put("merchant","سداد بطاقة ائتمان");v.put("payment_source_id",current.from);v.put("destination_source_id",current.card);v.put("user_edited",1);v.put("needs_review",0);sql.update("transactions",v,"id=?",new String[]{Long.toString(current.payment.id)});
            for(Map.Entry<Long,Long> item:current.allocations.entrySet()){ContentValues allocation=new ContentValues();allocation.put("settlement_id",current.payment.id);allocation.put("purchase_id",item.getKey());allocation.put("amount_minor",item.getValue());sql.insertOrThrow("card_settlement_allocations",null,allocation);}
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    boolean undo(long paymentId){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            try(Cursor c=sql.rawQuery("SELECT card_id,original_payload FROM card_settlements WHERE transaction_id=?",new String[]{Long.toString(paymentId)})){
                if(!c.moveToFirst())return false;ExpenseDb.Transaction current=db.transactionById(paymentId);JSONObject original=new JSONObject(c.getString(1));
                if(current==null||!"transfer".equals(current.kind)||current.destinationSourceId==null||current.destinationSourceId!=c.getLong(0)||current.amountMinor!=original.getLong("amount_minor"))return false;
                ContentValues v=new ContentValues();for(String field:new String[]{"kind","category","merchant","payment_source_id","destination_source_id","user_edited","needs_review"}){if(original.isNull(field))v.putNull(field);else v.put(field,original.getString(field));}
                sql.update("transactions",v,"id=?",new String[]{Long.toString(paymentId)});removeRelations(sql,paymentId);sql.setTransactionSuccessful();return true;
            }catch(org.json.JSONException e){throw new IllegalStateException(e);}
        }finally{sql.endTransaction();}
    }
    long addStatementFee(long purchaseId,long amount,String description){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            ExpenseDb.Transaction purchase=db.transactionById(purchaseId);
            if(purchase==null||purchase.paymentSourceId==null||!"expense".equals(purchase.kind)||!"بطاقة ائتمان".equals(source(purchase.paymentSourceId).kind)||amount<=0||description==null||description.trim().isEmpty())throw new IllegalArgumentException("Review the card purchase and statement fee");
            try(Cursor parent=sql.rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",new String[]{Long.toString(purchaseId)})){if(!parent.moveToFirst()||!parent.isNull(0))throw new IllegalArgumentException("Select the original card purchase");}
            if(allocated(purchaseId)>0)throw new IllegalArgumentException("Undo repayment allocations before changing the purchase cost");
            String key="card-statement-fee:"+purchaseId;
            try(Cursor c=sql.rawQuery("SELECT id,amount_minor,note FROM transactions WHERE unique_key=?",new String[]{key})){
                if(c.moveToFirst()){if(c.getLong(1)==amount&&description.trim().equals(c.getString(2))){sql.setTransactionSuccessful();return c.getLong(0);}throw new IllegalArgumentException("A statement fee already exists; review it first");}
            }
            ContentValues v=new ContentValues();v.put("unique_key",key);v.put("occurred_at",purchase.when);v.put("received_at",System.currentTimeMillis());v.put("amount_minor",amount);v.put("currency",purchase.currency);v.put("merchant","رسوم بطاقة · "+purchase.merchant);v.put("category",purchase.category);v.put("source","رسوم مؤكدة من كشف البطاقة");v.put("payment_source_id",purchase.paymentSourceId);v.put("kind","expense");v.put("fee_parent_id",purchaseId);v.put("user_edited",1);v.put("time_precision","date_only");v.put("note",description.trim());
            long id=sql.insertOrThrow("transactions",null,v);sql.setTransactionSuccessful();return id;
        }finally{sql.endTransaction();}
    }
}
