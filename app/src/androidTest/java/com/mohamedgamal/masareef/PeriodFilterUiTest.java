package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Synthetic from/to selection against the existing transactionPeriod query and list totals. */
final class PeriodFilterUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private boolean owned;
    private int checks;
    PeriodFilterUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private String plain(CharSequence value){return value==null?"":value.toString().replaceAll("[\u200e\u200f\u202a-\u202e\u2066-\u2069]","");}
    private AccessibilityNodeInfo root(){return runner.getUiAutomation().getRootInActiveWindow();}
    private boolean has(String wanted){AccessibilityNodeInfo tree=root();return tree!=null&&!tree.findAccessibilityNodeInfosByText(wanted).isEmpty();}
    private boolean hasPicker(AccessibilityNodeInfo node){
        if(node==null)return false;
        if("android:id/datePicker".equals(node.getViewIdResourceName())||"android.widget.DatePicker".contentEquals(node.getClassName()))return true;
        for(int i=0;i<node.getChildCount();i++)if(hasPicker(node.getChild(i)))return true;
        return false;
    }
    private void click(String wanted){
        AccessibilityNodeInfo tree=root();
        if(tree!=null)for(AccessibilityNodeInfo found:tree.findAccessibilityNodeInfosByText(wanted)){
            if(!plain(found.getText()).contains(wanted))continue;
            for(AccessibilityNodeInfo node=found;node!=null;node=node.getParent())
                if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
        }
        throw new AssertionError("Cannot click "+wanted);
    }
    private void clickDay(LocalDate date){
        String wanted=Integer.toString(date.getDayOfMonth());
        String description=date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy",java.util.Locale.ENGLISH));
        StringBuilder diagnostic=new StringBuilder();
        if(clickDay(root(),wanted,description,diagnostic)){idle();return;}
        throw new AssertionError("Cannot select calendar day "+description+" ("+diagnostic+")");
    }
    private boolean clickDay(AccessibilityNodeInfo node,String wanted,String description,StringBuilder diagnostic){
        if(node==null)return false;
        if(wanted.equals(plain(node.getText()))&&description.equals(plain(node.getContentDescription()))){
            diagnostic.append("found enabled=").append(node.isEnabled()).append(" clickable=").append(node.isClickable())
                    .append(" visible=").append(node.isVisibleToUser()).append("; ");
            if(node.isEnabled()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
        }
        for(int i=0;i<node.getChildCount();i++)if(clickDay(node.getChild(i),wanted,description,diagnostic))return true;
        return false;
    }
    private boolean clickId(AccessibilityNodeInfo node,String id){
        if(node==null)return false;
        if(id.equals(node.getViewIdResourceName())&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
        for(int i=0;i<node.getChildCount();i++)if(clickId(node.getChild(i),id))return true;
        return false;
    }
    private void acceptDate(){ok(clickId(root(),"android:id/button1"),"Date picker OK button unavailable");idle();}
    private void pick(LocalDate date)throws Exception{
        runner.runOnMainSync(()->{try{((android.widget.DatePicker)get("periodPicker")).updateDate(date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());}catch(Exception error){throw new RuntimeException(error);}});
        idle();acceptDate();
    }
    private Object get(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private void showTransactions(LocalDate month)throws Exception{
        runner.runOnMainSync(()->{try{
            Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,6);
            Field selected=MainActivity.class.getDeclaredField("selectedMonth");selected.setAccessible(true);selected.set(activity,month);
            Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void collect(View view,List<String> text){
        if(view instanceof TextView)text.add(plain(((TextView)view).getText()));
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),text);
    }
    private List<String> pageText()throws Exception{List<String> text=new ArrayList<>();collect((View)get("page"),text);return text;}
    private void screenshot(Context target)throws Exception{screenshot(target,"filtered-list");}
    private void screenshot(Context target,String name)throws Exception{
        File dir=new File(target.getCacheDir(),"period-filter");ok(dir.isDirectory()||dir.mkdirs(),"Screenshot folder unavailable");
        android.graphics.Bitmap image=runner.getUiAutomation().takeScreenshot();ok(image!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){ok(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{image.recycle();}
    }
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".periodqa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable periodqa emulator required");
            db=AppResources.database(target);ok(db.transactionCount()==0,"QA target must start empty");owned=true;
            LocalDate month=LocalDate.now().minusMonths(1).withDayOfMonth(1);
            int[] days={1,3,5,7,9,11,13,15,17,19};
            for(int i=0;i<days.length;i++){
                long when=month.withDayOfMonth(days[i]).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                ok(db.addManualEntry(when,(i+1)*10000L,"EGP",String.format(java.util.Locale.ROOT,"PERIOD QA %02d",i+1),
                        "أكل وشرب",null,"expense",null,null),"Synthetic transaction was not saved");
            }
            ok(db.transactionCount()==10,"Expected ten transactions across three weeks");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();showTransactions(month);
            // The period chip (month name) opens a menu; «فترة من يوم لـ يوم» starts the two date pickers.
            String monthChip=month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",new java.util.Locale("ar")));
            ok(has(monthChip),"Period chip is missing");click(monthChip);
            ok(has("فترة من يوم لـ يوم"),"Period menu is missing");click("فترة من يوم لـ يوم");
            ok(hasPicker(root()),"Start DatePicker did not open");ok(has("من يوم"),"Start calendar title is missing");screenshot(activity,"picker");pick(month.withDayOfMonth(7));
            ok(hasPicker(root()),"End DatePicker did not open");ok(has("إلى يوم"),"End calendar title is missing");pick(month.withDayOfMonth(13));
            LocalDate start=month.withDayOfMonth(7),end=month.withDayOfMonth(13);
            LocalDate[] period=(LocalDate[])get("transactionPeriod");
            ok(start.equals(period[0])&&end.plusDays(1).equals(period[1]),"Period did not use the existing exclusive-end state");
            List<ExpenseDb.Transaction> expected=db.transactionsBetween(start,end.plusDays(1));ok(expected.size()==4,"Expected four inclusive-date rows");
            List<String> shown=pageText();Set<String> merchants=new HashSet<>();long expectedTotal=0;
            for(ExpenseDb.Transaction tx:expected){merchants.add(tx.merchant);expectedTotal+=tx.amountMinor;
                ok(shown.contains("دخل "+MainActivity.money(0,"EGP")+" · خرج "+MainActivity.money(tx.amountMinor,"EGP")),"Displayed day total differs from row "+tx.merchant);
            }
            Set<String> visible=new HashSet<>();for(String text:shown)if(text.startsWith("PERIOD QA "))visible.add(text);
            ok(visible.equals(merchants),"Visible rows do not match transactionsBetween: "+visible);
            ok(expectedTotal==220000,"Filtered total changed unexpectedly");
            String periodChip=MainActivity.periodLabel(new LocalDate[]{start,end.plusDays(1)});
            ok(shown.contains(periodChip+"  ⌄"),"Inclusive end chip is missing: "+periodChip);
            ok(!shown.contains("عمليات من "+start+" إلى "+end+" · رجوع يرجعك لمكان البداية"),"Old verbose period line is back");
            screenshot(target);
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);
            runner.runOnMainSync(activity::recreate);MainActivity restored=(MainActivity)runner.waitForMonitorWithTimeout(monitor,5000);runner.removeMonitor(monitor);
            ok(restored!=null,"Activity did not recreate");activity=restored;idle();
            period=(LocalDate[])get("transactionPeriod");ok(start.equals(period[0])&&end.plusDays(1).equals(period[1]),"Period was not restored after recreation");
            click(periodChip);click("فترة من يوم لـ يوم");pick(month.withDayOfMonth(13));pick(month.withDayOfMonth(7));
            period=(LocalDate[])get("transactionPeriod");ok(start.equals(period[0])&&end.plusDays(1).equals(period[1]),"Invalid reversed range replaced the active filter");
            click(periodChip);click("رجوع لعرض "+monthChip);ok(get("transactionPeriod")==null,"Period menu did not clear the filter");
            result.putString("stream","Period filter UI checks passed: "+checks+"; screenshot in cache/period-filter/filtered-list.png\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","Period filter UI FAILED: "+trace);}
        finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null)db.clearAllData();
            runner.finish(status,result);
        }
    }
}
