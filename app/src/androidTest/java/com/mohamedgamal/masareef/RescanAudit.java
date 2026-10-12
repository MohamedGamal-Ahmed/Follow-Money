package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.Telephony;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit device audit: preview changes only a cache copy. Apply requires the reviewed live fingerprint. */
final class RescanAudit {
    private final Instrumentation runner;
    private final Context target;
    private final File directory;
    private static final ZoneId ZONE=ZoneId.of("Africa/Cairo");
    private static final LocalDate TODAY=LocalDate.of(2026,10,12);
    RescanAudit(Instrumentation runner){this.runner=runner;target=runner.getTargetContext();directory=new File(target.getCacheDir(),"rescan-review-2026-10-12");}

    void run(String mode,String expected) {
        Bundle result=new Bundle();
        try {
            if(!directory.isDirectory() && !directory.mkdirs())throw new IllegalStateException("Cannot create private audit cache");
            if("preview".equals(mode))preview();
            else if("apply".equals(mode))apply(expected);
            else throw new IllegalArgumentException("Only preview or apply is supported");
            result.putString("stream","Rescan "+mode+" completed; private report in cache/rescan-review-2026-10-12; no raw messages in tool output\n");runner.finish(-1,result);
        } catch(Exception error) {
            result.putString("stream","Rescan "+mode+" failed: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");runner.finish(0,result);
        }
    }

    private Context isolatedContext(File file) {
        return new ContextWrapper(target) {
            @Override public Context getApplicationContext(){return this;}
            @Override public File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.getPath(),factory,handler);}
        };
    }

    private void preview() throws Exception {
        File live=target.getDatabasePath("masareef.db"),copy=new File(directory,"preview.db");
        SQLiteDatabase.deleteDatabase(copy);
        // Caller force-stops the app before this diagnostic, so its DB/WAL files remain consistent.
        for(String suffix:new String[]{"","-wal","-shm"}) {
            File from=new File(live.getPath()+suffix);
            if(from.isFile())Files.copy(from.toPath(),new File(copy.getPath()+suffix).toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        String original;
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(live.getPath(),null,SQLiteDatabase.OPEN_READONLY)){original=databaseFingerprint(source);}
        try(ExpenseDb db=new ExpenseDb(isolatedContext(copy))) {
            SQLiteDatabase sql=db.getWritableDatabase();
            if(!original.equals(databaseFingerprint(sql)))throw new IllegalStateException("Clone differs from live database before replay");
            JSONObject report=new JSONObject();report.put("before_fingerprint",original);report.put("before",summary(db));
            File baseline=new File(directory,"preinstall.db");
            if(baseline.isFile())try(SQLiteDatabase previous=SQLiteDatabase.openDatabase(baseline.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                report.put("install_semantic_database_unchanged",original.equals(databaseFingerprint(previous)));
            }
            Map<Long,String> edited=editedRows(sql),before=transactionRows(sql);
            report.put("edited_rows_preserved_count",edited.size());
            JSONArray messages=scan(db,report);
            JSONObject plan=new JSONObject();plan.put("before_fingerprint",original);plan.put("messages",messages);
            replay(db,messages);
            assertEdited(sql,edited);
            report.put("after",summary(db));report.put("changes",changes(before,transactionRows(sql)));
            String after=tableFingerprint(sql,"transactions");
            plan.put("after_transactions_fingerprint",after);plan.put("after_summary",report.getJSONObject("after"));
            replay(db,messages);assertEdited(sql,edited);
            if(!after.equals(tableFingerprint(sql,"transactions")))throw new IllegalStateException("Replay is not idempotent");
            report.put("second_replay_ledger_unchanged",true);
            try(SQLiteDatabase source=SQLiteDatabase.openDatabase(live.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                if(!original.equals(databaseFingerprint(source)))throw new IllegalStateException("Live data changed during preview; rerun on a fresh snapshot");
            }
            report.put("live_unchanged",true);
            String sealed=EvidenceVault.encrypt(plan.toString());
            if(sealed==null)throw new IllegalStateException("Cannot encrypt private rescan plan");
            Files.write(new File(directory,"plan.vault").toPath(),sealed.getBytes(StandardCharsets.UTF_8));
            new File(directory,"plan.json").delete();write("preview.json",report);
        }
    }

    private void apply(String expected) throws Exception {
        if(expected==null || !expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Reviewed fingerprint is required");
        String decoded=EvidenceVault.decrypt(new String(Files.readAllBytes(new File(directory,"plan.vault").toPath()),StandardCharsets.UTF_8));
        if(decoded==null)throw new IllegalStateException("Cannot decrypt reviewed plan");
        JSONObject plan=new JSONObject(decoded);
        if(!expected.equals(plan.getString("before_fingerprint")))throw new IllegalStateException("Requested plan does not match reviewed fingerprint");
        JSONObject report=new JSONObject();
        try(ExpenseDb db=new ExpenseDb(target)) {
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try {
                if(!expected.equals(databaseFingerprint(sql)))throw new IllegalStateException("Live data changed since review; application refused");
                Map<Long,String> edited=editedRows(sql);
                report.put("before",summary(db));
                replay(db,plan.getJSONArray("messages"));assertEdited(sql,edited);
                if(!plan.getString("after_transactions_fingerprint").equals(tableFingerprint(sql,"transactions")))
                    throw new IllegalStateException("Applied ledger differs from preview; rolling back");
                report.put("after",summary(db));report.put("edited_rows_unchanged",true);report.put("matches_preview",true);
                if(!report.getJSONObject("after").toString().equals(plan.getJSONObject("after_summary").toString()))
                    throw new IllegalStateException("Applied summaries/balances differ from preview; rolling back");
                sql.setTransactionSuccessful();
            } finally {sql.endTransaction();}
            write("applied.json",report);
        }
    }

    private JSONArray scan(ExpenseDb db,JSONObject report) throws Exception {
        Set<String> sentKeys=ExpenseDb.smsSentKeys(target,0,System.currentTimeMillis());
        JSONArray selected=new JSONArray();int scanned=0,excluded=0,correctionsSkipped=0,future=0,review=0;
        try(Cursor c=target.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.DATE_SENT},
                Telephony.Sms.DATE+"<=?",new String[]{Long.toString(System.currentTimeMillis())},Telephony.Sms.DATE+" ASC")) {
            if(c==null)throw new IllegalStateException("SMS provider unavailable");
            while(c.moveToNext()) {
                scanned++;SmsPreview row=SmsPreview.parse(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3));
                if(row==null)continue;
                row.receiptKeySafe=row.canUseReceiptKey(sentKeys);
                row.captureStatus=db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt,row.receiptKeySafe);
                if(row.captureStatus==2){excluded++;continue;}
                if("correction".equals(row.kind) && !ExpenseParser.isFutureOnly(row.body)){correctionsSkipped++;continue;}
                if("review".equals(row.kind)){review++;continue;}
                if("correction".equals(row.kind))future++;
                JSONObject item=new JSONObject();item.put("sender",row.sender);item.put("body",row.body);
                item.put("received_at",row.receivedAt);item.put("capture_at",row.captureAt);item.put("receipt_key_safe",row.receiptKeySafe);
                selected.put(item);
            }
        }
        report.put("inbox_messages",scanned);report.put("selected_messages",selected.length());report.put("excluded_messages_skipped",excluded);
        report.put("failed_corrections_skipped",correctionsSkipped);report.put("future_review_messages",future);report.put("unconfirmed_messages_skipped",review);
        return selected;
    }

    private void replay(ExpenseDb db,JSONArray rows) throws Exception {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            long first=Long.MAX_VALUE,last=Long.MIN_VALUE;
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);long at=row.getLong("capture_at");
                db.addSms(row.isNull("sender")?null:row.getString("sender"),row.getString("body"),row.getLong("received_at"),at,row.getBoolean("receipt_key_safe"));
                first=Math.min(first,at);last=Math.max(last,at);
            }
            if(rows.length()>0)db.repairBetween(first-86_400_000L,last+86_400_000L);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }

    private JSONObject summary(ExpenseDb db) throws Exception {
        JSONObject out=new JSONObject();SQLiteDatabase sql=db.getReadableDatabase();
        for(String table:new String[]{"transactions","capture_evidence","transaction_archive","reimbursements","balance_snapshots","recurring"})
            try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();out.put(table,c.getLong(0));}
        List<MoneyFlow.Item> items=new ArrayList<>();Map<Long,Long> offsets=db.reimbursementOffsets();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(TODAY.withDayOfYear(1),TODAY.plusDays(1),ZONE))
            items.add(new MoneyFlow.Item(tx.id,tx.when,ExpenseDb.effectiveAmount(tx,offsets),tx.currency,tx.kind,tx.category,tx.merchant));
        Map<String,MoneyFlow.Summary> totals=MoneyFlow.summarize(items,new LocalDate[]{TODAY.withDayOfYear(1),TODAY.plusDays(1)},ZONE);
        JSONObject currencies=new JSONObject();
        for(Map.Entry<String,MoneyFlow.Summary> entry:totals.entrySet()) {
            JSONObject value=new JSONObject();for(MoneyFlow.Bucket bucket:MoneyFlow.Bucket.values())value.put(bucket.name(),entry.getValue().of(bucket));
            currencies.put(entry.getKey(),value);
        }
        out.put("year_2026_minor",currencies);
        JSONObject months=new JSONObject();
        for(MoneyFlow.Item item:items) {
            String month=java.time.Instant.ofEpochMilli(item.when).atZone(ZONE).toLocalDate().withDayOfMonth(1).toString().substring(0,7);
            String key=month+":"+item.currency;JSONObject totalsByMonth=months.optJSONObject(key);
            if(totalsByMonth==null){totalsByMonth=new JSONObject();months.put(key,totalsByMonth);}
            String bucket=MoneyFlow.bucket(item.kind,item.category).name();totalsByMonth.put(bucket,totalsByMonth.optLong(bucket)+item.amountMinor);
        }
        out.put("months_2026_minor",months);
        Set<String> balanceCurrencies=new java.util.TreeSet<>();
        try(Cursor c=sql.rawQuery("SELECT DISTINCT currency FROM balance_snapshots",null)){while(c.moveToNext())balanceCurrencies.add(c.getString(0));}
        JSONObject balances=new JSONObject();
        for(ExpenseDb.PaymentSource source:db.paymentSources())for(String code:balanceCurrencies) {
            ExpenseDb.BalanceSnapshot balance=db.currentBalance(source.id,code);
            if(balance!=null)balances.put(source.id+":"+code,new JSONObject().put("amount_minor",balance.amountMinor).put("estimated",balance.estimated));
        }
        out.put("balances_minor",balances);
        try(Cursor c=sql.rawQuery("PRAGMA integrity_check",null)){c.moveToFirst();out.put("integrity",c.getString(0));}
        return out;
    }

    private Map<Long,String> transactionRows(SQLiteDatabase sql) {
        Map<Long,String> rows=new LinkedHashMap<>();try(Cursor c=sql.rawQuery("SELECT * FROM transactions ORDER BY id",null)) {
            while(c.moveToNext())rows.put(c.getLong(c.getColumnIndexOrThrow("id")),rowText(c));
        }return rows;
    }
    private Map<Long,String> editedRows(SQLiteDatabase sql) {
        Map<Long,String> rows=new LinkedHashMap<>();try(Cursor c=sql.rawQuery("SELECT * FROM transactions WHERE user_edited=1 ORDER BY id",null)) {
            while(c.moveToNext())rows.put(c.getLong(c.getColumnIndexOrThrow("id")),rowText(c));
        }return rows;
    }
    private void assertEdited(SQLiteDatabase sql,Map<Long,String> before) {
        if(!before.equals(editedRows(sql)))throw new IllegalStateException("A user-edited row changed; audit/application stopped");
    }
    private JSONObject changes(Map<Long,String> before,Map<Long,String> after) throws Exception {
        JSONArray added=new JSONArray(),removed=new JSONArray(),changed=new JSONArray();
        for(Long id:after.keySet()) {if(!before.containsKey(id))added.put(id);else if(!before.get(id).equals(after.get(id)))changed.put(id);}
        for(Long id:before.keySet())if(!after.containsKey(id))removed.put(id);
        return new JSONObject().put("added_ids",added).put("removed_ids",removed).put("changed_ids",changed);
    }
    private String rowText(Cursor c) {
        StringBuilder row=new StringBuilder();for(int i=0;i<c.getColumnCount();i++) {
            String value=c.isNull(i)?"":c.getType(i)==Cursor.FIELD_TYPE_BLOB?android.util.Base64.encodeToString(c.getBlob(i),android.util.Base64.NO_WRAP):c.getString(i);
            // Repeated notification capture re-encrypts the same source with a fresh IV. Compare its meaning, not ciphertext randomness.
            if(!c.isNull(i) && "encrypted_body".equals(c.getColumnName(i))) {
                String plaintext=EvidenceVault.decrypt(value);
                if(plaintext!=null)value="decrypted:"+plaintext;
            }
            row.append(c.getType(i)).append(':').append(value.length()).append(':').append(value).append(';');
        }return row.toString();
    }
    private String tableFingerprint(SQLiteDatabase sql,String table) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(Cursor c=sql.rawQuery("SELECT * FROM \""+table.replace("\"","\"\"")+"\" ORDER BY 1",null)) {
            while(c.moveToNext())digest.update((rowText(c)+"\n").getBytes(StandardCharsets.UTF_8));
        }return hex(digest.digest());
    }
    private String databaseFingerprint(SQLiteDatabase sql) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(Cursor tables=sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",null)) {
            while(tables.moveToNext())digest.update((tables.getString(0)+":"+tableFingerprint(sql,tables.getString(0))+"\n").getBytes(StandardCharsets.UTF_8));
        }return hex(digest.digest());
    }
    private String hex(byte[] value){StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private void write(String name,JSONObject value) throws Exception {Files.write(new File(directory,name).toPath(),value.toString(2).getBytes(StandardCharsets.UTF_8));}
}
