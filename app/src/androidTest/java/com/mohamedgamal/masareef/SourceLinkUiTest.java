package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reproduce numeric names from the user's report; correction and linking are separate explicit saves. */
final class SourceLinkUiTest {
    private final Instrumentation runner;
    private MainActivity activity;private ExpenseDb db;private int checks;private java.io.File captures;
    private boolean ownsFixture;
    SourceLinkUiTest(Instrumentation runner){this.runner=runner;}
    private interface Action{void run()throws Exception;}
    private void main(Action action){Throwable[] error={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});runner.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void idle()throws Exception{runner.waitForIdleSync();runner.getUiAutomation().waitForIdle(500,5000);}
    private void ok(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private Object get(String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    private void set(String key,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private View root(){return activity.findViewById(android.R.id.content);}
    private EditText field(String key){return root().findViewWithTag("source_"+key);}
    private void fill(String key,String value){main(()->field(key).setText(value));}
    private View text(View node,String value){if(node instanceof TextView&&value.contentEquals(((TextView)node).getText()))return node;
        if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){View found=text(((ViewGroup)node).getChildAt(i),value);if(found!=null)return found;}return null;}
    private void tap(String tag){main(()->{View view=root().findViewWithTag(tag);if(view==null)throw new AssertionError("Missing "+tag);view.performClick();});}
    private void pageClick(String value){main(()->{View view=text(root(),value);if(view==null)throw new AssertionError("Missing "+value);view.performClick();});}
    private void submit(){tap("ui_page_save");}
    private void click(String value)throws Exception{idle();for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(value)){
        if(node.getText()==null||!value.contentEquals(node.getText()))continue;while(node!=null&&!node.isClickable())node=node.getParent();
        if(node!=null){node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());idle();node.refresh();if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}}
    }throw new AssertionError("Missing window action "+value);}
    private ScrollView scroll(View node){if(node instanceof ScrollView)return (ScrollView)node;
        if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){ScrollView found=scroll(((ViewGroup)node).getChildAt(i));if(found!=null)return found;}return null;}
    private void screen(String name)throws Exception{idle();android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(captures,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private void recreate()throws Exception{Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);main(()->activity.recreate());
        MainActivity next=(MainActivity)runner.waitForMonitorWithTimeout(monitor,15000);runner.removeMonitor(monitor);ok(next!=null,"Activity recreated");activity=next;idle();}
    private boolean open()throws Exception{return ((UiPages)get("formPages")).isOpen();}
    void run(boolean dark,float scale){Context target=runner.getTargetContext();Bundle result=new Bundle();int status=-1;
        try{
            if(!target.getPackageName().endsWith(".journeyqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Disposable isolated emulator required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0||!db.paymentSources().isEmpty())throw new IllegalStateException("Empty QA source ledger required");ownsFixture=true;
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark).putBoolean("intro_completed",true).putString("opening_mode","off")
                    .putBoolean("sms_permission_prompted",true).putBoolean("initial_import_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).putBoolean("source_picker_shown",true).commit();
            captures=new java.io.File(target.getCacheDir(),"source-link-"+(dark?"dark":"light")+(scale>1?"-max":""));captures.mkdirs();
            long card=db.addPaymentSource("10001","بطاقة","4101"),account=db.addPaymentSource("20002","حساب","4102");
            long other=db.addPaymentSource("بنك مصر","بطاقة","7733"),credit=db.addPaymentSource("HSBC","بطاقة ائتمان","9911");
            long at=System.currentTimeMillis()-60000;ok(db.addManualEntry(at,12345,"EGP","QA SHOP","بقالة",card,"expense",null,"evidence kept"),"Recognition fixture saved");long tx=db.latestInserted().id;
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            main(()->{set("selectedTab",1);invoke("render",new Class<?>[0]);invoke("linkSourceDialog",new Class<?>[]{ExpenseDb.PaymentSource.class},db.paymentSource(account));});idle();
            ok(root().findViewWithTag("source_identity_"+card)!=null,"Reported card remains visible when its bank is invalid");
            ok(root().findViewWithTag("source_link_option_"+card)==null,"Unknown bank cannot be selected for a guessed link");
            ok(root().findViewWithTag("source_identity_"+credit)==null,"Credit card is not presented as a debit-card candidate");
            screen("01-unknown-bank");tap("source_edit_"+card);ok("".contentEquals(field("bank").getText()),"Numeric old name does not prefill bank");
            ok("10001".contentEquals(field("name").getText()),"Old user label preserved until explicitly edited");
            fill("bank","HSBC");fill("name","كارت المشتريات");recreate();
            ok("HSBC".contentEquals(field("bank").getText()),"Bank draft survives nested recreation");screen("02-bank-correction");submit();
            ok("HSBC".equals(db.paymentSource(card).name)&&"كارت المشتريات".equals(db.paymentSource(card).displayName),"Bank and friendly label saved separately");
            ok(db.paymentSource(card).parentSourceId==null,"Correcting a bank never auto-links");
            ok(root().findViewWithTag("source_identity_"+account)!=null,"Correction returns to original linking page");
            tap("source_edit_"+account);fill("bank","HSBC");fill("name","حساب المرتب");submit();
            ok(root().findViewWithTag("source_link_option_"+card)!=null,"Corrected same-bank card can now be selected");
            ok(root().findViewWithTag("source_link_option_"+other)==null,"Different bank remains visible but cannot be linked");
            tap("source_link_option_"+card);recreate();
            main(()->ok(((RadioButton)root().findViewWithTag("source_link_option_"+card)).isChecked(),"Selection survives recreation"));
            submit();screen("03-link-review");click("رجوع للاختيار");ok(db.paymentSource(card).parentSourceId==null,"Cancel review made no relationship write");
            main(()->ok(((RadioButton)root().findViewWithTag("source_link_option_"+card)).isChecked(),"Returning from review kept selection"));
            submit();click("أيوه، اربط");ok(!open()&&db.accountId(card)==account,"Explicit confirmation linked and closed the flow");
            ok(db.paymentSource(other).parentSourceId==null&&db.paymentSource(credit).parentSourceId==null,"No unrelated or credit card linked");
            main(()->invoke("linkSourceDialog",new Class<?>[]{ExpenseDb.PaymentSource.class},db.paymentSource(account)));
            main(()->ok(text(root(),"مرتبط حاليًا بـ "+db.sourceLabel(account))!=null,"Existing relationship is visible for review"));screen("04-existing-link");
            pageClick("لسه مش عارف · أرجع بعدين");ok(!open()&&db.accountId(card)==account,"Not-sure exit keeps an existing relationship unchanged");
            int sources=db.paymentSources().size();main(()->invoke("paymentSourceDialog",new Class<?>[0]));pageClick("حساب بنكي");fill("bank","112233");submit();
            main(()->ok(field("bank").getError()!=null,"Digits as bank show a local actionable error"));ok(db.paymentSources().size()==sources,"Invalid bank cannot create a source");
            fill("bank","HSBC");fill("name","حساب الادخار");fill("digits","9900");main(()->scroll(root()).fullScroll(View.FOCUS_DOWN));screen("05-new-source");submit();
            ok(!open()&&db.paymentSources().size()==sources+1,"Guided source form saved exactly once");
            ok(db.paymentSources().stream().anyMatch(s->"HSBC".equals(s.name)&&"حساب الادخار".equals(s.displayName)&&"9900".equals(s.last4)),"New source stores bank separately from its nickname");
            ok(db.transactionCount()==1&&db.transactionById(tx).amountMinor==12345&&db.transactionById(tx).when==at&&db.transactionById(tx).paymentSourceId==card,"Recognition, correction and linking preserve the financial transaction");
            result.putString("stream","Source linking checks passed: "+checks+"; failed: 0; dark="+dark+"; fontScale="+scale+"\nCaptures: "+captures+"\n");
        }catch(Throwable error){status=0;try{screen("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Source linking FAILED: "+trace);}
        finally{try{if(activity!=null)main(()->activity.finish());if(db!=null){if(ownsFixture)db.clearAllData();db.close();}}catch(Exception error){status=0;result.putString("stream",result.getString("stream","")+error);}runner.finish(status,result);}
    }
}
