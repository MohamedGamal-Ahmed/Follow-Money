package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Real period selection and SMS provider, then a long synthetic optional review. Isolated emulator only. */
final class SmsImportUiTest{
    private final Instrumentation runner;private SmsImportActivity activity;private ExpenseDb db;private int checked;private File captures;
    SmsImportUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value){if(!value)throw new AssertionError("SMS UI check "+checked);checked++;}
    private interface Action{void run()throws Exception;}
    private void main(Action action){runner.runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});runner.waitForIdleSync();}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private void call(String name)throws Exception{Method m=SmsImportActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private View find(View node,String text){
        if(node instanceof TextView&&((TextView)node).getText().toString().equals(text))return node;
        if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private View findPrefix(View node,String prefix){
        if(node instanceof TextView&&((TextView)node).getText().toString().startsWith(prefix))return node;
        if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++){View found=findPrefix(group.getChildAt(i),prefix);if(found!=null)return found;}}return null;
    }
    private void click(String text){main(()->{View button=find(activity.getWindow().getDecorView(),text);if(button==null)throw new AssertionError("Missing "+text);button.performClick();});}
    private void awaitDone()throws Exception{for(int i=0;i<300;i++){runner.waitForIdleSync();Object session=field(activity,"session");String phase=(String)field(session,"phase");if("done".equals(phase))return;
        // Automatic import passes through «ready» while it reads pending cancellations; only a review session stops there.
        if("ready".equals(phase)&&(Boolean)field(session,"reviewBeforeImport")||"error".equals(phase))throw new AssertionError("Automatic import stopped at "+phase);Thread.sleep(100);}throw new AssertionError("Import did not finish");}
    /** Inbox messages the pipeline decides record nothing for (IGNORE, UNUNDERSTOOD, INFO_ONLY), read straight from the provider. */
    private int notTransactions(Context target){
        int count=0;
        try(android.database.Cursor c=target.getContentResolver().query(android.provider.Telephony.Sms.Inbox.CONTENT_URI,new String[]{android.provider.Telephony.Sms.ADDRESS,android.provider.Telephony.Sms.BODY,android.provider.Telephony.Sms.DATE,android.provider.Telephony.Sms.DATE_SENT},null,null,null)){
            PipelineDecider.Context decisions=PipelineDecider.context(db,"import");
            while(c.moveToNext()){if(c.getString(1)==null)continue;PipelineDecider.Result r=PipelineDecider.decide(decisions,CaptureRequest.inboxSms(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3),false));
                if(r instanceof PipelineDecider.Decided&&!((PipelineDecider.Decided)r).decision.records)count++;}
        }
        return count;
    }
    private void counts(int added,int skipped,int before){
        String expected="أُضيفت: "+added+"\nتم تخطيها (ليست معاملات): "+skipped+"\nمستوردة من قبل: "+before;
        if(find(activity.getWindow().getDecorView(),expected)==null){View shown=findPrefix(activity.getWindow().getDecorView(),"أُضيفت: ");
            throw new AssertionError("Import counts not shown: "+expected.replace('\n','|')+" shown: "+(shown==null?"none":((TextView)shown).getText().toString().replace('\n','|')));}checked++;
    }
    private void shot(String name)throws Exception{runner.waitForIdleSync();Thread.sleep(600);android.graphics.Bitmap b=runner.getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(captures,name+".png"))){ok(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
    @SuppressWarnings("unchecked") void run(boolean dark,float scale){
        int status=-1;Bundle result=new Bundle();android.content.res.Configuration original=null;
        try{
            Context target=runner.getTargetContext();if(!target.getPackageName().endsWith(".journeyqa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))throw new SecurityException("Isolated emulator required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0)throw new IllegalStateException("Empty QA ledger required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark).commit();
            captures=new File(target.getCacheDir(),"sms-"+(dark?"dark":"light")+(scale>1?"-large":""));captures.mkdirs();
            activity=(SmsImportActivity)runner.startActivitySync(new android.content.Intent(target,SmsImportActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));runner.waitForIdleSync();
            original=new android.content.res.Configuration(activity.getResources().getConfiguration());
            if(scale>1)main(()->{android.content.res.Configuration c=new android.content.res.Configuration(activity.getResources().getConfiguration());c.fontScale=scale;activity.getResources().updateConfiguration(c,activity.getResources().getDisplayMetrics());call("show");});
            ok((Boolean)field(activity,"dark")==dark);shot("01-period");ok(find(activity.getWindow().getDecorView(),"مراجعة الرسائل قبل الحفظ (اختياري)")!=null);
            click("من تاريخ تختاره");shot("00-calendar");runner.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);runner.waitForIdleSync();ok("idle".equals(field(field(activity,"session"),"phase")));
            click("كل الرسائل");awaitDone();ImportResult first=(ImportResult)field(field(activity,"session"),"imported");ok(first!=null&&first.committed&&first.survivingCreated>0);ok(db.transactionCount()>0);
            // 0.4.1 item 2: added, skipped as not transactions, and imported before are three separate numbers.
            int skipped=notTransactions(target);ok(skipped>0);counts(first.created,skipped,0);shot("02-result");
            int recordingTaken=0;for(Object item:(List<?>)field(field(activity,"session"),"items")){SmsPreview r=(SmsPreview)item;if(r.selected&&(r.decision==null||r.decision.records))recordingTaken++;}
            if(new CaptureEvents(db).pendingHistory(0,Long.MAX_VALUE).isEmpty()){StringBuilder why=new StringBuilder("No pending historical cancellation; now="+System.currentTimeMillis());
                try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT status,received_at,source FROM capture_events",null)){while(c.moveToNext())why.append(" | ").append(c.getString(0)).append(" at ").append(c.getLong(1)).append(" via ").append(c.getString(2));}
                for(Object item:(List<?>)field(field(activity,"session"),"items")){SmsPreview r=(SmsPreview)item;if("correction".equals(r.kind))why.append(" | row status ").append(r.captureStatus).append(r.selected?" selected ":" unselected ").append(r.captureAt);}
                throw new AssertionError(why.toString());}
            ok(findPrefix(activity.getWindow().getDecorView(),"مراجعة الإلغاء · ")!=null);
            main(()->{View pending=findPrefix(activity.getWindow().getDecorView(),"مراجعة الإلغاء · ");pending.getParent().requestChildFocus(pending,pending);});shot("02b-pending-cancellation");
            long before=db.transactionCount();click("استيراد فترة أخرى");click("كل الرسائل");awaitDone();ok(before==db.transactionCount());ImportResult repeated=(ImportResult)field(field(activity,"session"),"imported");ok(repeated==null||repeated.committed&&repeated.created==0);
            // Every transaction message the first batch took is now imported before; the not-transaction count does not change.
            counts(0,skipped,recordingTaken);
            click("تفاصيل الرسائل");ok("details".equals(field(field(activity,"session"),"phase")));ok(field(activity,"save")==null);shot("03-details");
            main(()->{Method m=SmsImportActivity.class.getDeclaredMethod("showOriginal",SmsPreview.class);m.setAccessible(true);m.invoke(activity,SmsPreview.parse("QA BANK","Card purchase at QA IMPORT EGP 15.00",System.currentTimeMillis()));});shot("04-source");
            runner.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);runner.waitForIdleSync();
            Object session=field(activity,"session");List<SmsPreview> items=(List<SmsPreview>)field(session,"items");
            main(()->{set(session,"phase","scan");call("show");View stop=find(activity.getWindow().getDecorView(),"إيقاف الفحص");ok(stop!=null);set(session,"phase","apply");stop.performClick();ok("apply".equals(field(session,"phase")));});
            main(()->{set(session,"phase","scan");call("show");find(activity.getWindow().getDecorView(),"إيقاف الفحص").performClick();ok("idle".equals(field(session,"phase")));});
            main(()->{items.clear();for(int i=0;i<85;i++)items.add(SmsPreview.parse("QA BANK","Card purchase at QA LONG ITEM EGP 15.00 using card **8877. Available balance EGP 985.00",System.currentTimeMillis()+i));set(session,"scanned",85);set(session,"total",85);set(session,"phase","ready");set(activity,"pageNumber",0);call("show");});
            click("تحديد الكل");ok(items.stream().filter(x->x.selected).count()==85);Button save=(Button)field(activity,"save");Rect top=new Rect();main(()->ok(save.getGlobalVisibleRect(top)&&top.height()>=40));shot("05-fixed-action");
            LinearLayout page=(LinearLayout)field(activity,"page");ScrollView scroll=(ScrollView)page.getParent();main(()->scroll.fullScroll(View.FOCUS_DOWN));Thread.sleep(300);Rect bottom=new Rect();main(()->ok(save.getGlobalVisibleRect(bottom)&&top.equals(bottom)));shot("06-scrolled-action");
            click("التالي");ok(items.stream().filter(x->x.selected).count()==85);ok(before==db.transactionCount());
            result.putString("stream","Sms import UI checks passed: "+checked+"; failed: 0; dark="+dark+"; scale="+scale+"\nCaptures: "+captures+"\n");
        }catch(Throwable e){status=0;try{shot("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Sms import UI FAILED: "+trace+"\n");}
        finally{try{if(activity!=null){android.content.res.Configuration restore=original;main(()->{if(restore!=null)activity.getResources().updateConfiguration(restore,activity.getResources().getDisplayMetrics());activity.finish();});}if(db!=null){db.clearAllData();db.close();}}catch(Throwable e){status=0;result.putString("stream",result.getString("stream","")+"cleanup failed "+e);}runner.finish(status,result);}
    }
}
