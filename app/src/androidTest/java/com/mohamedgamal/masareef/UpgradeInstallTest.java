package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Installed-APK update check: seed with v20, retain app data, then verify with the current schema. */
public final class UpgradeInstallTest extends Instrumentation {
    private String mode;
    private Context target;
    private ExpenseDb db;
    private List<String> baselineTables;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode");start();}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();int status=-1;
        try {
            target=getTargetContext();
            ok(target.getPackageName().endsWith(".upgradeqa"),"Separate upgradeqa package required");
            ok("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE),"Emulator required");
            db=new ExpenseDb(target);
            File expected=new File(target.getFilesDir(),"upgrade-snapshot.json");
            if("seed".equals(mode)){
                ok(db.getWritableDatabase().getVersion()==20,"Baseline must be schema v20");
                ok(db.transactions().isEmpty()&&!expected.exists(),"Fresh QA install required; never replace existing data");
                seed();
                Files.write(expected.toPath(),snapshot().toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream","Seeded schema v20 on installed baseline; rows="+db.transactions().size()+"; snapshot saved privately\n");
            }else if("verify".equals(mode)){
                JSONObject before=new JSONObject(new String(Files.readAllBytes(expected.toPath()),StandardCharsets.UTF_8));
                baselineTables=new ArrayList<>();JSONArray originalTables=before.getJSONArray("baseline_tables");
                for(int i=0;i<originalTables.length();i++)baselineTables.add(originalTables.getString(i));
                ok(db.getWritableDatabase().getVersion()==25,"Updated APK must migrate to v25");
                JSONObject after=snapshot();
                ok(before.toString().equals(after.toString()),"Ledger/evidence/preferences/points changed: "+before+" -> "+after);
                try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transactions WHERE time_precision IS NOT NULL",null)){
                    ok(c.moveToFirst()&&c.getLong(0)==0,"Legacy precision must remain unknown");
                }
                try(Cursor c=db.getReadableDatabase().rawQuery("PRAGMA integrity_check",null)){ok(c.moveToFirst()&&"ok".equals(c.getString(0)),"Integrity check failed");}
                try(Cursor tables=db.getReadableDatabase().rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'",null)){
                    while(tables.moveToNext())if(!baselineTables.contains(tables.getString(0))){
                        String table=tables.getString(0);
                        try(Cursor count=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM \""+table.replace("\"","\"\"")+"\"",null)){
                            ok(count.moveToFirst()&&count.getLong(0)==("ledger_revision".equals(table)?1:0),"Unexpected rows in new table "+table);
                        }
                    }
                }
                // Open the real updated activity as part of the installed update path.
                android.app.Activity activity=startActivitySync(new android.content.Intent(target,MainActivity.class)
                        .putExtra("open_transactions",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();
                ok(before.toString().equals(snapshot().toString()),"Opening the updated app changed the fixture");
                runOnMainSync(activity::finish);
                result.putString("stream","Installed update v20 -> v25 passed; all original table rows, encrypted evidence, source plaintext, repayments, budgets and points preserved; legacy precision NULL; integrity_check=ok; app opened\n"+after+"\n");
            }else throw new IllegalArgumentException("Use seed or verify");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Installed upgrade FAILED: "+trace);}
        finally{if(db!=null)db.close();finish(status,result);}
    }
    private long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private void seed(){
        LocalDate today=LocalDate.now(),yesterday=today.minusDays(1);
        db.addManual(at(yesterday,10),41900,"EGP","QA UPGRADE SHOP","بقالة");long purchase=db.latestInserted().id;
        db.addManual(at(yesterday,11),2500,"EGP","QA UPGRADE CAFE","مقاهي");
        db.addManualEntry(at(yesterday,12),29500,"EGP","QA UPGRADE FRIEND","دخل",null,"income",null,"synthetic repayment");
        db.linkReimbursement(db.latestInserted().id,purchase);
        db.addManual(at(today,10),1250,"USD","QA UPGRADE USD","تسوق");
        db.addSms("QA BANK","Card ****1234 purchase EGP 8 at QA UPGRADE BANK SHOP",at(today,11));
        DailySummary.save(target,new DailyAllowance.Plan("EGP",25000,yesterday,0));
        DailyPointsStore.enable(target,"EGP",today);DailyPointsStore.observe(target,"EGP",yesterday);
        DailyPointsStore.confirm(target,db,"EGP",yesterday,today);
        target.getSharedPreferences("widget_settings",0).edit().putString("currency","EGP").putBoolean("hide_amounts",true).commit();
        target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                .putString("opening_mode","off").putBoolean("sms_permission_prompted",true)
                .putBoolean("initial_import_prompted",true).putInt("repair_version",ExpenseDb.REPAIR_VERSION).commit();
        target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
    }
    private JSONObject snapshot()throws Exception {
        JSONObject result=new JSONObject();JSONArray ledger=new JSONArray();List<String> tables=new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata' ORDER BY name",null)){
            while(c.moveToNext())tables.add(c.getString(0));
        }
        if(baselineTables==null)baselineTables=tables;
        tables=baselineTables;result.put("baseline_tables",new JSONArray(tables));
        for(String table:tables){
            JSONArray rows=new JSONArray();
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM \""+table+"\" ORDER BY rowid",null)){
                while(c.moveToNext()){
                    JSONArray row=new JSONArray();
                    for(int i=0;i<c.getColumnCount();i++)if(!"time_precision".equals(c.getColumnName(i))&&!"settlement_key".equals(c.getColumnName(i)))
                        row.put(c.getColumnName(i)).put(c.isNull(i)?JSONObject.NULL:c.getString(i));
                    rows.put(row);
                }
            }
            ledger.put(table).put(rows);
        }
        result.put("ledger_sha256",digest(ledger.toString()));
        JSONArray financialPrefs=new JSONArray();
        for(String name:new String[]{DailySummary.SETTINGS,DailyPointsStore.SETTINGS,"widget_settings"}){
            java.util.TreeMap<String,?> entries=new java.util.TreeMap<>(target.getSharedPreferences(name,0).getAll());
            financialPrefs.put(name).put(entries.toString());
        }
        result.put("financial_preferences_sha256",digest(financialPrefs.toString()));
        JSONArray sources=new JSONArray();for(ExpenseDb.Transaction tx:db.transactions())
            for(ExpenseDb.SourceEvidence evidence:db.sourceEvidence(tx.id))sources.put(evidence.body);
        result.put("source_plaintext_sha256",digest(sources.toString()));
        result.put("confirmed_points",DailyPoints.total(DailyPointsStore.load(target,db,"EGP",LocalDate.now())).toPlainString());
        result.put("rows",db.transactions().size());
        return result;
    }
    private String digest(String text)throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder value=new StringBuilder();for(byte b:hash)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString();
    }
}
