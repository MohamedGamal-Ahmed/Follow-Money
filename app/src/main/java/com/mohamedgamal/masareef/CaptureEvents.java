package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Non-financial audit events. Active ledger queries never include these rows. */
final class CaptureEvents {
    private final ExpenseDb db;
    CaptureEvents(ExpenseDb db){this.db=db;}
    static void create(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS capture_events(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,message_hash TEXT,status TEXT NOT NULL,amount_minor INTEGER,currency TEXT,title TEXT NOT NULL,received_at INTEGER NOT NULL,archive_id INTEGER,original_transaction_id INTEGER,sender TEXT,source TEXT,encrypted_body TEXT,display_at INTEGER)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS capture_event_time ON capture_events(received_at DESC)");
        try(Cursor c=sql.rawQuery("SELECT id,original_id,archived_at,payload FROM transaction_archive WHERE reason='bank confirmed cancellation'",null)){
            while(c.moveToNext()){
                ContentValues v=new ContentValues();v.put("event_key","legacy:"+c.getLong(0));v.put("status","cancelled");
                v.put("title","إلغاء محفوظ · تفاصيل الأرشيف غير متاحة");v.put("received_at",c.getLong(2));v.put("display_at",c.getLong(2));
                v.put("archive_id",c.getLong(0));v.put("original_transaction_id",c.getLong(1));v.put("source","أرشيف سابق");
                try{
                    JSONObject payload=new JSONObject(c.getString(3));if(payload.has("fee_parent_id")&&!payload.isNull("fee_parent_id"))continue;
                    long amount=payload.getLong("amount_minor");String currency=payload.getString("currency");
                    v.put("amount_minor",amount);v.put("currency",currency);v.put("title",payload.optString("merchant","تحويل"));
                    ExpenseDb.Transaction original=Event.archivedTransaction(c.getString(3));if(original!=null)v.put("display_at",original.when);
                }catch(org.json.JSONException invalid){/* Keep an explicit unavailable event and the exact original archive. */}
                sql.insertWithOnConflict("capture_events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
        ensureTimeline(sql);
    }
    static void ensureTimeline(SQLiteDatabase sql){
        boolean column=false;try(Cursor c=sql.rawQuery("PRAGMA table_info(capture_events)",null)){while(c.moveToNext())if("display_at".equals(c.getString(1)))column=true;}
        if(!column)sql.execSQL("ALTER TABLE capture_events ADD COLUMN display_at INTEGER");
        try(Cursor c=sql.rawQuery("SELECT e.id,e.received_at,a.payload FROM capture_events e LEFT JOIN transaction_archive a ON a.id=e.archive_id WHERE e.display_at IS NULL",null)){
            while(c.moveToNext()){ExpenseDb.Transaction original=Event.archivedTransaction(c.getString(2));ContentValues v=new ContentValues();v.put("display_at",original==null?c.getLong(1):original.when);sql.update("capture_events",v,"id=?",new String[]{Long.toString(c.getLong(0))});}
        }
        sql.execSQL("CREATE INDEX IF NOT EXISTS capture_event_display ON capture_events(display_at DESC,id DESC)");
    }
    static final class Event {
        final long id,when,displayAt;final Long archiveId,originalId,amount;final String status,currency,title,sender,source,body;final ExpenseDb.Transaction original;
        Event(Cursor c){id=c.getLong(0);status=c.getString(1);amount=c.isNull(2)?null:c.getLong(2);currency=c.getString(3);title=c.getString(4);when=c.getLong(5);archiveId=c.isNull(6)?null:c.getLong(6);originalId=c.isNull(7)?null:c.getLong(7);sender=c.getString(8);source=c.getString(9);body=c.getString(10);original=c.getColumnCount()>11?archivedTransaction(c.getString(11)):null;displayAt=c.getColumnCount()>12&&!c.isNull(12)?c.getLong(12):original==null?when:original.when;}
        boolean cancelled(){return "cancelled".equals(status);}
        long displayWhen(){return displayAt;}
        private static ExpenseDb.Transaction archivedTransaction(String payload){
            if(payload==null)return null;
            try{
                JSONObject p=new JSONObject(payload);
                return new ExpenseDb.Transaction(p.getLong("id"),p.getLong("occurred_at"),p.getLong("amount_minor"),p.getString("currency"),p.getString("merchant"),p.getString("category"),p.optString("source"),nullableId(p,"payment_source_id"),p.optString("card_last4"),p.getString("kind"),nullableId(p,"destination_source_id"),p.optString("note"),false,p.optString("time_precision"));
            }catch(org.json.JSONException invalid){return null;}
        }
        private static Long nullableId(JSONObject p,String key)throws org.json.JSONException{return p.isNull(key)||!p.has(key)?null:p.getLong(key);}
    }
    private static final String COLUMNS="id,status,amount_minor,currency,title,received_at,archive_id,original_transaction_id,sender,source,encrypted_body";
    private static final String HISTORY="SELECT e."+COLUMNS.replace(",",",e.")+",a.payload,e.display_at FROM capture_events e LEFT JOIN transaction_archive a ON a.id=e.archive_id";
    Event byId(long id){try(Cursor c=db.getReadableDatabase().rawQuery(HISTORY+" WHERE e.id=?",new String[]{Long.toString(id)})){return c.moveToFirst()?new Event(c):null;}}
    /** Filter on the archived operation date, including cancellations received in a later month. */
    List<Event> timelineBetween(long start,long end){
        return new ArrayList<>(db.cachedRead("events:"+start+":"+end,()->readTimeline(start,end)));
    }
    private List<Event> readTimeline(long start,long end){
        List<Event> rows=new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery(HISTORY+" WHERE e.display_at>=? AND e.display_at<? ORDER BY e.display_at DESC,e.id DESC",new String[]{Long.toString(start),Long.toString(end)})){
            while(c.moveToNext())rows.add(new Event(c));
        }return rows;
    }
    List<Event> pendingHistory(long start,long end){
        List<Event> rows=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery(HISTORY+" WHERE e.status='needs_confirmation' AND e.received_at>=? AND e.received_at<=? ORDER BY e.received_at DESC,e.id DESC",new String[]{Long.toString(start),Long.toString(end)})){while(c.moveToNext())rows.add(new Event(c));}return rows;
    }
    List<Event> between(long start,long end){List<Event> rows=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT "+COLUMNS+" FROM capture_events WHERE received_at>=? AND received_at<? ORDER BY received_at DESC,id DESC LIMIT 100",new String[]{Long.toString(start),Long.toString(end)})){while(c.moveToNext())rows.add(new Event(c));}return rows;}
    private Long source(String sender,String last4){
        String key=SourceIdentity.key(sender,last4);if(key==null)return null;Long found=null;
        for(ExpenseDb.PaymentSource s:db.paymentSources())if(key.equals(SourceIdentity.key(s.name,s.last4))){if(found!=null)return null;found=s.id;}return found;
    }
    private List<Long> candidates(Event event){
        List<Long> ids=new ArrayList<>();String text=EvidenceVault.decrypt(event.body);CaptureNotice notice=CaptureNotice.parse(event.sender,text);
        if(notice==null||notice.payment==null)return ids;
        Long from=source(event.sender,notice.payment.cardLast4);if(from==null)return ids;
        String reference=CaptureIdentity.reference(event.sender,text);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT t.id FROM transactions t WHERE t.fee_parent_id IS NULL AND (t.kind='transfer' OR t.kind='expense' AND t.category='تحويلات') AND t.payment_source_id=? AND t.currency=? AND t.amount_minor+COALESCE((SELECT SUM(f.amount_minor) FROM transactions f WHERE f.fee_parent_id=t.id),0)=CAST(? AS INTEGER) AND t.received_at BETWEEN ? AND ? AND (t.user_edited=0 OR EXISTS(SELECT 1 FROM card_settlements s WHERE s.transaction_id=t.id)) AND NOT EXISTS(SELECT 1 FROM reimbursements r WHERE r.income_id=t.id OR r.expense_id=t.id)",new String[]{Long.toString(from),event.currency,Long.toString(event.amount),Long.toString(event.when-120000),Long.toString(event.when)})){
            while(c.moveToNext()){
                long id=c.getLong(0);boolean conflict=false;
                try(Cursor evidence=db.getReadableDatabase().rawQuery("SELECT reference_hash FROM capture_evidence WHERE transaction_id=? AND reference_hash IS NOT NULL",new String[]{Long.toString(id)})){
                    while(evidence.moveToNext())if(reference!=null&&!reference.equals(evidence.getString(0)))conflict=true;
                }
                boolean readable=false;
                try(Cursor proof=db.getReadableDatabase().rawQuery("SELECT encrypted_body FROM capture_evidence WHERE transaction_id=? AND direction='expense'",new String[]{Long.toString(id)})){
                    while(proof.moveToNext())if(EvidenceVault.decrypt(proof.getString(0))!=null)readable=true;
                }
                if(!conflict&&readable)ids.add(id);
            }
        }
        return ids;
    }
    Event record(String sender,String source,String body,long when,String key,CaptureNotice notice){return record(sender,source,body,when,key,notice,true);}
    Event record(String sender,String source,String body,long when,String key,CaptureNotice notice,boolean automatic){
        String hash=CaptureIdentity.normalized(sender,body);
        SQLiteDatabase sql=db.getWritableDatabase();
        try(Cursor c=sql.rawQuery("SELECT id FROM capture_events WHERE event_key=?",new String[]{key})){if(c.moveToFirst())return null;}
        ContentValues v=new ContentValues();v.put("event_key",key);v.put("message_hash",hash);v.put("status",automatic?"needs_review":"needs_confirmation");v.put("title","إلغاء تحويل");v.put("received_at",when);v.put("display_at",when);v.put("sender",sender);v.put("source",source);v.put("encrypted_body",EvidenceVault.encrypt(body));
        if(v.getAsString("encrypted_body")==null)throw new IllegalStateException("Cancellation evidence could not be encrypted");
        if(notice.payment!=null){v.put("amount_minor",notice.payment.amountMinor);v.put("currency",notice.payment.currency);}
        // A repeated source copy may arrive with another receipt timestamp. Do not suppress a real second debit.
        try(Cursor c=sql.rawQuery("SELECT id FROM capture_events WHERE message_hash=? AND received_at BETWEEN ? AND ? ORDER BY id DESC LIMIT 1",new String[]{hash,Long.toString(when-120000),Long.toString(when+120000)})){
            if(c.moveToFirst()){
                Event prior=byId(c.getLong(0));if(!prior.cancelled()||candidates(newTemporary(v)).isEmpty())return null;
            }
        }
        long id=sql.insertOrThrow("capture_events",null,v);Event event=byId(id);if(automatic)resolve(event);return byId(id);
    }
    private Event newTemporary(ContentValues v){
        android.database.MatrixCursor c=new android.database.MatrixCursor(COLUMNS.split(","));
        c.addRow(new Object[]{-1,"needs_review",v.getAsLong("amount_minor"),v.getAsString("currency"),"",v.getAsLong("received_at"),null,null,v.getAsString("sender"),v.getAsString("source"),v.getAsString("encrypted_body")});c.moveToFirst();Event event=new Event(c);c.close();return event;
    }
    List<ExpenseDb.Transaction> choices(Event event){
        List<ExpenseDb.Transaction> rows=new ArrayList<>();if(event==null||event.cancelled())return rows;
        for(long id:candidates(event)){ExpenseDb.Transaction row=db.transactionById(id);if(row!=null)rows.add(row);}return rows;
    }
    String identity(long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT unique_key FROM transactions WHERE id=?",new String[]{Long.toString(id)})){return c.moveToFirst()?c.getString(0):null;}}
    Event confirm(long eventId,long transactionId,String expectedIdentity){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            Event event=byId(eventId);
            if(event==null||event.cancelled()||expectedIdentity==null||!expectedIdentity.equals(identity(transactionId))||!candidates(event).contains(transactionId))throw new IllegalArgumentException("Cancellation selection changed");
            archive(event,transactionId);sql.setTransactionSuccessful();return byId(eventId);
        }finally{sql.endTransaction();}
    }
    private void resolve(Event event){
        if(event==null||event.cancelled()||event.amount==null)return;
        List<Long> matches=candidates(event);if(matches.size()!=1)return;
        archive(event,matches.get(0));
    }
    private void archive(Event event,long id){
        SQLiteDatabase sql=db.getWritableDatabase();
        ContentValues proof=new ContentValues();proof.put("unique_key","cancellation:"+event.id);proof.put("transaction_id",id);proof.put("source",event.source);proof.put("sender",event.sender);proof.put("received_at",event.when);proof.put("direction","cancellation");proof.put("encrypted_body",event.body);
        sql.insertOrThrow("capture_evidence",null,proof);
        ExpenseDb.Transaction original=db.transactionById(id);
        db.archiveEntry(id,"bank confirmed cancellation",true);
        long archive;
        try(Cursor c=sql.rawQuery("SELECT id FROM transaction_archive WHERE original_id=? AND reason='bank confirmed cancellation' ORDER BY id DESC LIMIT 1",new String[]{Long.toString(id)})){if(!c.moveToFirst())throw new IllegalStateException("Cancellation archive missing");archive=c.getLong(0);}
        ContentValues v=new ContentValues();v.put("status","cancelled");if(original!=null)v.put("title",original.merchant);v.put("archive_id",archive);v.put("original_transaction_id",id);if(original!=null)v.put("display_at",original.when);sql.update("capture_events",v,"id=?",new String[]{Long.toString(event.id)});
    }
    Event reconcilePending(long transactionId){
        Event resolved=null;
        // The bank's receipt timestamps, rather than callback order, determine matching.
        List<Long> pending=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM capture_events WHERE status='needs_review' ORDER BY received_at,id",null)){while(c.moveToNext())pending.add(c.getLong(0));}
        for(long id:pending){Event before=byId(id);if(candidates(before).contains(transactionId)){resolve(before);Event after=byId(id);if(after.cancelled())resolved=after;}}
        return resolved;
    }
    List<ExpenseDb.SourceEvidence> evidence(Event event){
        List<ExpenseDb.SourceEvidence> rows=new ArrayList<>();
        if(event.archiveId!=null)try(Cursor c=db.getReadableDatabase().rawQuery("SELECT payload FROM transaction_archive WHERE id=?",new String[]{Long.toString(event.archiveId)})){
            if(c.moveToFirst())try{JSONArray list=new JSONObject(c.getString(0)).optJSONArray("evidence");if(list!=null)for(int i=0;i<list.length();i++){
                JSONObject p=list.getJSONObject(i);EvidenceVault.Read body=EvidenceVault.read(p.isNull("encrypted_body")?null:p.optString("encrypted_body"));
                rows.add(new ExpenseDb.SourceEvidence(p.optString("source"),p.optString("sender"),p.optLong("received_at"),body.body,body.failed));
            }}catch(org.json.JSONException error){rows.add(new ExpenseDb.SourceEvidence("أرشيف سابق","",event.when,null,true));}
        }
        if(rows.isEmpty()&&event.body!=null){EvidenceVault.Read body=EvidenceVault.read(event.body);rows.add(new ExpenseDb.SourceEvidence(event.source,event.sender,event.when,body.body,body.failed));}
        return rows;
    }
}
