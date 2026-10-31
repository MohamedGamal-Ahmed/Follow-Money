package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** First SMS, confirmed suggestion, and the next paid cycle in a disposable QA install. */
final class CommitmentCycleUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File screenshots;
    private boolean owned;
    private int checks;

    CommitmentCycleUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private void pause(){try{Thread.sleep(100);}catch(InterruptedException error){throw new AssertionError(error);}}
    private String text(CharSequence value){return value==null?"":value.toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","");}
    private AccessibilityNodeInfo node(String wanted){
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();if(root==null)return null;
        for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(wanted))
            if(text(found.getText()).contains(wanted))return found;
        return null;
    }
    private void click(String wanted){
        for(int attempt=0;attempt<40;attempt++){
            AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(wanted)){
                if(!text(found.getText()).contains(wanted))continue;
                found.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                for(AccessibilityNodeInfo item=found;item!=null;item=item.getParent())
                    if(item.isClickable()&&item.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            }
            pause();
        }
        throw new AssertionError("Cannot click "+wanted);
    }
    private void show(int tab){
        runner.runOnMainSync(()->{try{
            Field selected=MainActivity.class.getDeclaredField("selectedTab");selected.setAccessible(true);selected.setInt(activity,tab);
            Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void screen(String name)throws Exception{
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(screenshots,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    private static long at(LocalDate date){return date.atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private static String payment(LocalDate date){
        return "Card purchase at NETFLIX EGP 80.00 on "+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" at 10:00. Card ****1111.";
    }
    @SuppressWarnings("unchecked")
    private MoneyFlow.Week visibleWeek()throws Exception{
        Method flow=MainActivity.class.getDeclaredMethod("flowItems",LocalDate[].class);flow.setAccessible(true);
        LocalDate today=LocalDate.now();
        List<MoneyFlow.Item> rows=(List<MoneyFlow.Item>)flow.invoke(activity,(Object)MoneyFlow.rollingWeek(today));
        return MoneyFlow.week(rows,today,ZoneId.systemDefault(),"EGP");
    }
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".commitmentqa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable commitmentqa emulator required");
            db=AppResources.database(target);
            ok(db.transactionCount()==0&&db.recurring().isEmpty()&&db.commitmentSuggestions().isEmpty(),"QA target must be empty");owned=true;
            screenshots=new File(target.getCacheDir(),"commitment-cycle");ok(screenshots.isDirectory()||screenshots.mkdirs(),"Screenshot folder unavailable");
            LocalDate first=LocalDate.now().minusMonths(1),second=first.plusMonths(1);
            ok(db.addSms("BanqueMisr",payment(first),at(first)),"First Netflix SMS was not captured");
            ok(db.transactionCount()==1,"First SMS did not create exactly one expense");
            List<ExpenseDb.Suggestion> suggestions=db.commitmentSuggestions();
            ok(suggestions.size()==1&&"subscription".equals(suggestions.get(0).kind),"Netflix proposal missing");
            ok(second.equals(suggestions.get(0).date),"Proposed due date is not one month later");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();show(2);
            // Detected items live behind the «محتاجة رأيك» strip.
            click("رأيك");
            ok(node("مكتشفة من رسائلك")!=null,"Detected-from-SMS section not visible");
            screen("01-suggestion");click("راجع المصدر وأضفه للكالندر");
            ok(node("التزام جديد")!=null,"Suggestion did not open the commitment editor");
            click("احفظ الالتزام");
            List<ExpenseDb.Recurring> plans=db.recurring();
            ok(plans.size()==1,"Confirmation did not create exactly one plan");
            ExpenseDb.Recurring plan=plans.get(0);
            ok("subscription".equals(plan.kind)&&second.equals(plan.nextDue),"Confirmed plan due date or kind changed");
            ok(db.transactionCount()==1,"Confirming a suggestion duplicated the first expense");
            ok(db.commitmentSuggestions().isEmpty(),"Confirmed Netflix plan still appears as a suggestion");
            ok(db.addSms("BanqueMisr",payment(second),at(second)),"Second Netflix SMS was not captured");
            ok(db.transactionCount()==2,"Second SMS did not add exactly one expense");
            ok(db.commitmentPayments(plan.id).size()==1,"Second payment was not linked exactly once");
            ok(db.recurringById(plan.id).paidCount==1,"Next cycle was not marked paid once");
            ok(second.plusMonths(1).equals(db.recurringById(plan.id).nextDue),"Next due date did not advance one month");
            ok(db.commitmentSuggestions().isEmpty(),"Second payment created another Netflix suggestion");
            show(11);screen("02-after-second-payment");
            show(0);
            MoneyFlow.Week week=visibleWeek();
            ok(week.spendingCount==1&&week.spending()==8000,"Seven-day spending counted the payment more than once");
            ok(node("1 عملية")!=null,"Home seven-day count did not show one operation");
            screen("03-home-once");
            result.putString("stream","Commitment cycle UI checks passed: "+checks+"; screenshots in cache/commitment-cycle\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","Commitment cycle UI FAILED: "+trace);}
        finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null)db.clearAllData();
            runner.finish(status,result);
        }
    }
}
