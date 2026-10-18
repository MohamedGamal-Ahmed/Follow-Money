package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Real form selections, filtered indices, action menus and mixed form/multi-select dialogs. */
final class SelectionUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File captures;
    private android.content.res.Configuration originalConfiguration;
    private int checked;
    SelectionUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value){if(!value)throw new AssertionError("UI check "+checked);checked++;}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private interface Action{void run()throws Exception;}
    private void main(Action action){runner.runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});idle();}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(500,3000);}catch(java.util.concurrent.TimeoutException e){throw new AssertionError("UI never became idle",e);}}
    private boolean has(String text){return containsVisibleText(runner.getUiAutomation().getRootInActiveWindow(),plain(text));}
    private String plain(CharSequence text){return text==null?"":text.toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","");}
    private boolean containsVisibleText(AccessibilityNodeInfo node,String text){
        if(node==null)return false;
        if(node.getText()!=null&&plain(node.getText()).contains(text))return true;
        for(int i=0;i<node.getChildCount();i++)if(containsVisibleText(node.getChild(i),text))return true;
        return false;
    }
    private android.view.View viewWithText(android.view.View node,String text){
        if(node instanceof android.widget.TextView&&node.isShown()&&plain(((android.widget.TextView)node).getText()).equals(text))return node;
        if(node instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){android.view.View found=viewWithText(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private boolean choiceChecked(String name) {
        for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(name)) {
            if(node.getText()==null||!node.getText().toString().equals(name))continue;
            while(node!=null&&!node.isCheckable())node=node.getParent();
            if(node!=null)return node.isChecked();
        }
        return false;
    }
    private void click(String text){
        List<AccessibilityNodeInfo> nodes=runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text);
        for(AccessibilityNodeInfo node:nodes){
            if(node.isEditable()||node.getText()==null||!node.getText().toString().equals(text))continue;
            while(node!=null&&!node.isClickable())node=node.getParent();
            if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
        }
        throw new AssertionError("Cannot click "+text);
    }
    private void awaitText(String text)throws Exception {
        for(int i=0;i<50;i++){idle();if(has(text))return;Thread.sleep(100);}throw new AssertionError("Screen did not arrive: "+text);
    }
    private void screen(String name)throws Exception {
        // Accessibility can settle before SurfaceFlinger has presented the replacement window.
        idle();Thread.sleep(800);runner.waitForIdleSync();
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(captures,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node,String hint) {
        if(node==null)return null;
        if(node.isEditable()&&(plain(node.getText()).contains(hint)||plain(node.getHintText()).contains(hint)))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=editable(node.getChild(i),hint);if(found!=null)return found;}
        return null;
    }
    private void fill(String hint,String value) {
        AccessibilityNodeInfo node=editable(runner.getUiAutomation().getRootInActiveWindow(),hint);
        if(node!=null){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){idle();return;}}
        throw new AssertionError("Missing editable hint "+hint);
    }
    private void search(String value) {
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("بحث في الخيارات")) {
            if(!node.isEditable())continue;Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){idle();return;}
        }
        throw new AssertionError("Missing choice search");
    }
    void run(boolean dark,float fontScale) {
        Context target=runner.getTargetContext();Bundle result=new Bundle();int status=-1;
        try {
            if(!target.getPackageName().endsWith(".journeyqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Isolated emulator required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0)throw new IllegalStateException("Empty QA ledger required");
            captures=new File(target.getCacheDir(),"menus-"+(dark?"dark":"light")+(fontScale>1?"-large":""));captures.mkdirs();
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark).putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            long account=db.addPaymentSource("QA BANK","حساب","1111");
            db.addManualEntry(System.currentTimeMillis()-10000,28800,"EGP","QA STORE","أكل وشرب",account,"expense",null,null);long id=db.latestInserted().id;
            for(int i=0;i<12;i++)db.addOwnHandle("qa"+i+"@instapay");
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            main(()->{((ExpenseDb)get("db")).close();set("db",db);set("introVisible",false);set("selectedTab",6);
                if(fontScale>1){originalConfiguration=new android.content.res.Configuration(activity.getResources().getConfiguration());android.content.res.Configuration config=new android.content.res.Configuration(originalConfiguration);config.fontScale=fontScale;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());}
                invoke("render",new Class<?>[0]);invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(id));});
            click("أكل وشرب");ok(has("بحث في الخيارات"));ok(choiceChecked("أكل وشرب"));screen("01-category");
            search("no-such-choice");ok(has("مفيش نتائج للبحث"));search("صحة");ok(has("صحة"));ok(!has("أكل وشرب"));click("صحة");awaitText("تعديل المعاملة");ok(has("تعديل المعاملة"));click("حفظ");ok("صحة".equals(db.transactionById(id).category));ok(db.transactionById(id).amountMinor==28800);
            main(()->invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(id)));
            click("EGP");ok(choiceChecked("EGP"));screen("02-currency");click("USD");click("إلغاء");ok("EGP".equals(db.transactionById(id).currency));
            main(()->invoke("sourceOptions",new Class<?>[]{ExpenseDb.PaymentSource.class},db.paymentSources().stream().filter(s->s.id==account).findFirst().get()));
            ok(has("سجل الأرصدة"));ok(has("تسمية وتأكيد الاستخدام"));screen("03-source-actions");click("تسمية وتأكيد الاستخدام");ok(has("تسمية الوسيلة"));click("إلغاء");
            main(()->invoke("ownHandlesDialog",new Class<?>[0]));ok(has("مثال: name@instapay"));ok(has("بحث في الخيارات"));
            fill("name@instapay","newqa@instapay");search("qa11@");click("qa11@instapay");ok(choiceChecked("qa11@instapay"));screen("04-multi-search");
            search("");search("qa11@");ok(choiceChecked("qa11@instapay"));click("حفظ");
            ok(!db.isOwnHandle("qa11@instapay"));ok(db.isOwnHandle("newqa@instapay"));ok(db.ownHandles().size()==12);ok(db.transactionCount()==1);
            main(()->invoke("ownHandlesDialog",new Class<?>[0]));search("qa10@");click("qa10@instapay");click("إلغاء");ok(db.isOwnHandle("qa10@instapay"));
            List<ExpenseDb.SourceEvidence> evidence=java.util.Arrays.asList(
                new ExpenseDb.SourceEvidence("SMS","QA BANK",System.currentTimeMillis(),"تم خصم مبلغ 227 جنيه من حساب رقم 1111 — رسالة اختبار فقط"),
                new ExpenseDb.SourceEvidence("SMS","QA BANK",System.currentTimeMillis()+1000,"تم الغاء التحويل بمبلغ 227 جنيه — رسالة اختبار فقط"));
            main(()->invoke("showOriginalMessages",new Class<?>[]{List.class},evidence));ok(has("الرسالة الأصلية"));ok(has("تم خصم"));ok(has("تم الغاء"));screen("05-bank-message");click("إغلاق");
            main(()->invoke("showOriginalMessages",new Class<?>[]{List.class},java.util.Collections.emptyList()));ok(has("مصدر نصي غير متاح"));click("إغلاق");
            ExpenseDb.Transaction stale=db.transactionById(id);
            main(()->invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},stale));
            db.deleteTransaction(id);
            click("افتكر التصنيف للتاجر ده في العمليات الجديدة");click("حفظ");
            ok(has("تعديل المعاملة"));ok(db.merchantRules().isEmpty());ok(db.transactionCount()==0);click("إلغاء");
            if(android.os.Build.VERSION.SDK_INT>=29){
                int sourcesBefore=db.paymentSources().size();
                db.getWritableDatabase().execSQL("CREATE TRIGGER qa_source_alias_fault BEFORE INSERT ON source_aliases BEGIN SELECT RAISE(ABORT,'qa source alias fault'); END");
                try{
                    main(()->invoke("paymentSourceDialog",new Class<?>[0]));
                    fill("اسم الوسيلة","HSBC");fill("آخر 3 أو 4 أرقام","9988");
                    boolean[] callbackThrew={false};
                    main(()->{
                        android.view.View add=null;
                        for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews())
                            if(viewWithText(root,"وسيلة دفع جديدة")!=null)add=viewWithText(root,"إضافة");
                        if(add==null)throw new AssertionError("Missing source-dialog add action");
                        try{add.performClick();}catch(RuntimeException expected){callbackThrew[0]=true;}
                    });
                    int partial=db.paymentSources().size()-sourcesBefore;
                    if(partial!=0)throw new AssertionError("Failed source dialog left "+partial+" partial row(s); callbackThrew="+callbackThrew[0]);
                    ok(!callbackThrew[0]);ok(has("وسيلة دفع جديدة"));
                }finally{db.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS qa_source_alias_fault");}
                click("إضافة");ok(!has("وسيلة دفع جديدة"));ok(db.paymentSources().size()==sourcesBefore+1);
                ExpenseDb.PaymentSource created=db.paymentSources().stream().filter(s->"HSBC".equals(s.name)&&"9988".equals(s.last4)).findFirst().orElseThrow(()->new AssertionError("Missing reviewed source"));
                ok(created.reviewed);ok(db.sourceAliasCount(created.id)==1);
            }
            long transferAt=System.currentTimeMillis()-60000,targetAccount=db.addPaymentSource("QA TR2 TARGET","حساب","7766");
            ok(db.addManualEntry(transferAt,10050,"EGP","QA TR2 debit","تحويلات",account,"expense",null,null));long debit=db.latestInserted().id;
            ok(db.addManualEntry(transferAt+1000,10000,"EGP","QA TR2 receipt","دخل",targetAccount,"income",null,null));long receipt=db.latestInserted().id;
            db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+debit});
            db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{debit});
            main(()->invoke("openRelatedTransaction",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(debit)));ok(has("تحويل محتاج ربط"));
            main(()->invoke("reconcileTransferDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(debit)));
            fill("أصل المبلغ المحوّل","100.00");fill("رسوم البنك","0.50");
            click("بدون وسيلة محددة");click(db.sourceLabel(targetAccount)+" · يحتاج مراجعة");
            for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("QA TR2 receipt")){
                while(node!=null&&!node.isCheckable())node=node.getParent();
                if(node!=null){node.performAction(AccessibilityNodeInfo.ACTION_CLICK);idle();break;}
            }
            click("اربط المصادر وافصل الرسوم");
            ok(db.transactionById(debit).amountMinor==10050);ok(db.transactionById(receipt)!=null);
            ok(has("الرسوم دي محذوفة أو عليها تجاهل"));ok(has("اربط بدون رسوم"));screen("06-tr2-fee-blocked");
            click("اربط بدون رسوم");ok(has("0.50"));ok(has("مش هتتحسب في مصروفاتك"));screen("07-tr2-omission-confirmation");
            ok(db.transactionById(debit).amountMinor==10050);ok(db.transactionById(receipt)!=null);
            click("إلغاء");ok(db.transactionById(debit).amountMinor==10050);
            click("اربط بدون رسوم");click("تأكيد الربط بدون رسوم");
            ok("transfer".equals(db.transactionById(debit).kind));ok(db.transactionById(debit).amountMinor==10000);
            ok(db.transactionById(receipt)==null);ok(db.transferFee(debit)==0);ok(db.transactionById(debit).note.contains("0.50"));
            result.putString("stream","Selection UI checks passed: "+checked+"; failed: 0; dark="+dark+"; fontScale="+fontScale+"\nCaptures: "+captures+"\n");
        }catch(Throwable error){status=0;try{screen("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Selection UI FAILED: "+trace+"\n");}
        finally{try{if(activity!=null)main(()->{if(originalConfiguration!=null)activity.getResources().updateConfiguration(originalConfiguration,activity.getResources().getDisplayMetrics());activity.finish();});if(db!=null){db.clearAllData();db.close();}}catch(Throwable error){status=0;result.putString("stream",result.getString("stream","")+error);}runner.finish(status,result);}
    }
}
