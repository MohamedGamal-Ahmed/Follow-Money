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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The owner's scenario (20 October) on the home statement: salary at month end in bank A, 90% moved to bank B,
 * purchases on two credit cards repaid from their banks, direct spending, a cash withdrawal and a purchase from cash.
 * Every component line must open a list whose rows add up to its figure; without the transfer linked, the warning shows.
 */
final class StatementUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private boolean owned;
    private int checks;
    private File screenshots;
    private long bankA, bankB, cardA, cardB, cash;
    StatementUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private String plain(CharSequence value){return value==null?"":value.toString().replaceAll("[‎‏‪-‮⁦-⁩]","");}
    private AccessibilityNodeInfo root(){return runner.getUiAutomation().getRootInActiveWindow();}
    private void click(String wanted){
        for(int attempt=0;attempt<3;attempt++){
            AccessibilityNodeInfo tree=root();
            if(tree!=null)for(AccessibilityNodeInfo found:tree.findAccessibilityNodeInfosByText(wanted)){
                if(!plain(found.getText()).equals(wanted))continue;
                found.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                for(AccessibilityNodeInfo node=found;node!=null;node=node.getParent())
                    if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            }
            idle();
        }
        throw new AssertionError("Cannot click "+wanted);
    }
    private Object get(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private void show(int tab){
        runner.runOnMainSync(()->{try{
            Field selected=MainActivity.class.getDeclaredField("selectedTab");selected.setAccessible(true);selected.setInt(activity,tab);
            Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void collect(View view,List<String> text){
        if(view instanceof TextView)text.add(plain(((TextView)view).getText()));
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),text);
    }
    private List<String> pageText()throws Exception{List<String> text=new ArrayList<>();collect((View)get("page"),text);return text;}
    private boolean shows(List<String> text,String wanted){for(String s:text)if(s.contains(wanted))return true;return false;}
    private void screen(String name)throws Exception{
        android.graphics.Bitmap image=runner.getUiAutomation().takeScreenshot();ok(image!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(screenshots,name+".png"))){ok(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{image.recycle();}
    }
    private static long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private void row(long when,long amount,String merchant,String category,Long source,String kind,Long destination){
        ok(db.addManualEntry(when,amount,"EGP",merchant,category,source,kind,destination,null),"Synthetic row was not saved: "+merchant);
    }
    private void sources(){
        bankA=db.addPaymentSource("QA Bank A","حساب","1111");bankB=db.addPaymentSource("QA Bank B","حساب","2222");
        cardA=db.addPaymentSource("QA Card A","بطاقة ائتمان","3333");cardB=db.addPaymentSource("QA Card B","بطاقة ائتمان","4444");
        cash=db.addPaymentSource("محفظة كاش","كاش",null);
        ok(bankA>0&&bankB>0&&cardA>0&&cardB>0&&cash>0,"Synthetic sources were not created");
    }
    /** Salary 30,000 on the pay day; 27,000 to bank B; 3 + 2 card purchases; two repayments; 1,500 direct; 500 cash; 200 from cash. */
    private void scenario(LocalDate payDay,boolean linked){
        row(at(payDay,9),3000000,"مرتب","دخل",bankA,"income",null);
        if(linked)row(at(payDay,10),2700000,"تحويل بين حساباتي","تحويلات",bankA,"transfer",bankB);
        else{row(at(payDay,10),2700000,"تحويل لحظي","تحويلات",bankA,"expense",null);row(at(payDay,10)+60000,2700000,"تحويل لحظي","دخل",bankB,"income",null);}
        LocalDate d=payDay.plusDays(1);
        row(at(d,12),100000,"QA SHOP A1","تسوق",cardA,"expense",null);
        row(at(d,13),200000,"QA FOOD A2","أكل وشرب",cardA,"expense",null);
        row(at(d,14),50000,"QA MART A3","بقالة",cardA,"expense",null);
        row(at(d,15),70000,"QA SHOP B1","تسوق",cardB,"expense",null);
        row(at(d,16),30000,"QA CAFE B2","مقاهي",cardB,"expense",null);
        row(at(d,17),400000,"سداد كارت A","تحويلات",bankA,"expense",cardA);
        row(at(d,18),300000,"سداد كارت B","تحويلات",bankB,"expense",cardB);
        row(at(d,19),150000,"QA DIRECT","أكل وشرب",bankA,"expense",null);
        row(at(d,20),50000,"ATM","سحب نقدي",bankA,"expense",null);
        row(at(d,21),20000,"QA CASH SNACK","أكل وشرب",cash,"expense",null);
    }
    @SuppressWarnings("unchecked")
    private long listedTotal(LocalDate[] period)throws Exception{
        Method matches=MainActivity.class.getDeclaredMethod("matchesTransactionFilters",ExpenseDb.Transaction.class,String.class);matches.setAccessible(true);
        Map<Long,Long> offsets=db.reimbursementOffsets();long total=0;
        for(ExpenseDb.Transaction tx:db.transactionsBetween(period[0],period[1]))
            if((Boolean)matches.invoke(activity,tx,""))total+=ExpenseDb.effectiveAmount(tx,offsets);
        return total;
    }
    private void tagged(String tag) {
        runner.runOnMainSync(() -> {
            View found=activity.findViewById(android.R.id.content).findViewWithTag(tag);
            if(found==null)throw new AssertionError("Missing tag: "+tag);found.performClick();
        });idle();
    }
    private void expandOut() {
        runner.runOnMainSync(() -> {try {
            Field open=MainActivity.class.getDeclaredField("homeStatementOpen");open.setAccessible(true);open.setInt(activity,2);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void openAndCompare(String rowLabel,String expectedFilter,long expected,LocalDate[] period)throws Exception{
        show(0);expandOut();tagged("statement_component:"+rowLabel);
        ok(expectedFilter.equals(get("transactionBucketFilter")),"Row «"+rowLabel+"» did not open its bucket filter");
        LocalDate[] opened=(LocalDate[])get("transactionPeriod");
        ok(opened!=null&&opened[0].equals(period[0])&&opened[1].equals(period[1]),"Row «"+rowLabel+"» did not open the statement period");
        long listed=listedTotal(period);
        ok(listed==expected,"Rows behind «"+rowLabel+"» add up to "+listed+", the card shows "+expected);
        ok(shows(pageText(),MainActivity.periodLabel(period)+"  ⌄"),"Period chip does not name the statement period");
        ok(shows(pageText(),"كشف الفترة"),"Filter summary does not say the list is the statement");
    }
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".statementqa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable statementqa emulator required");
            db=AppResources.database(target);ok(db.transactionCount()==0,"QA target must start empty");owned=true;
            screenshots=new File(target.getCacheDir(),"month-statement");ok(screenshots.isDirectory()||screenshots.mkdirs(),"Screenshot folder unavailable");
            // Salary on the last day of each month («آخر يوم في الشهر» = 32) → the period starts on the previous month end.
            DailySummary.saveSalary(target,"EGP",3000000,DailyAllowance.MONTH_END);
            LocalDate today=LocalDate.now();
            LocalDate[] period=MoneyFlow.statementPeriod(DailyAllowance.MONTH_END,today);
            LocalDate payDay=period[0];
            ok(payDay.getDayOfMonth()==payDay.lengthOfMonth()&&!payDay.isAfter(today),"Period does not start on a month end");
            ok(period[1].equals(today.plusDays(1)),"Period does not run through today");
            sources();scenario(payDay,true);
            ok(db.transactionCount()==12,"Expected twelve synthetic rows");
            MoneyFlow.Statement linked=MoneyFlow.statement(db.flowItems(period[0],period[1],ZoneId.systemDefault()),period,ZoneId.systemDefault(),"EGP");
            ok(linked.income==3000000&&linked.salary==3000000&&linked.transfersIn==0,"Income is the salary once");
            ok(linked.out==650000&&linked.living==600000&&linked.cash==50000&&linked.openTransfer==0&&linked.commitment==0,"خرج = card purchases + direct + cash withdrawal: "+linked.out);
            ok(linked.remaining==2350000&&linked.internal==3400000&&linked.cashSpending==20000&&linked.unlinkedTransfer==0,"باقي, internal and cash detail");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true)
                    .putInt("repair_version",ExpenseDb.REPAIR_VERSION).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();show(0);
            List<String> home=pageText();
            ok(shows(home,MainActivity.money(3000000,"EGP")),"Income total missing");
            ok(shows(home,MainActivity.money(650000,"EGP")),"Outflow total missing");
            ok(shows(home,MainActivity.money(2350000,"EGP")),"Difference total missing");
            ok(shows(home,"الفرق")&&shows(home,"مش رصيد حسابك"),"Difference must not imply an account balance");
            ok(!shows(home,"مصروفات من الكاش")&&!shows(home,"بين حساباتك وسداد كروتك"),"Supporting explanations belong in the info sheet");
            ok(!shows(home,"تحويل غير مربوط"),"Linked transfer must not warn");
            screen("01-statement-linked");
            openAndCompare(MoneyFlow.label(MoneyFlow.Bucket.LIVING),"LIVING",600000,period);
            openAndCompare("كاش اتسحب","CASH_WITHDRAWAL",50000,period);
            show(0);expandOut();
            ok(shows(pageText(),"النسب من إجمالي الخارج"),"Outflow accordion missing");
            runner.runOnMainSync(() -> {try {
                Method open=MainActivity.class.getDeclaredMethod("openStatement",String.class,boolean.class);open.setAccessible(true);open.invoke(activity,MoneyFlow.OUT_FILTER,false);
            }catch(Exception error){throw new RuntimeException(error);}});idle();
            ok(MoneyFlow.OUT_FILTER.equals(get("transactionBucketFilter")),"Outflow drill-down filter");
            ok(listedTotal(period)==650000,"Outflow drill-down sum");screen("02-out-list");
            // Same scenario without the transfer linked: the debit is a transfer to a person, the deposit a second inflow.
            runner.runOnMainSync(activity::finish);activity=null;idle();
            db.clearAllData();ok(db.transactionCount()==0,"Reset failed");
            sources();scenario(payDay,false);
            MoneyFlow.Statement warned=MoneyFlow.statement(db.flowItems(period[0],period[1],ZoneId.systemDefault()),period,ZoneId.systemDefault(),"EGP");
            ok(warned.unlinkedTransfer==2700000&&warned.openTransfer==2700000&&warned.out==650000+2700000&&warned.income==5700000,"Unlinked transfer enters خرج and is flagged");
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();show(0);
            home=pageText();
            ok(shows(home,"تحويل غير مربوط بقيمة "+MainActivity.money(2700000,"EGP")),"Unlinked transfer warning missing");
            ok(shows(home,MainActivity.money(3350000,"EGP")),"Outflow includes unlinked transfer");
            tagged("statement_income");
            home=pageText();ok(shows(home,"راتب")&&shows(home,"تحويلات وصلتك")&&shows(home,MainActivity.money(2700000,"EGP")),"Income accordion split missing");
            screen("03-statement-unlinked");
            openAndCompare("تحويلات لناس","OPEN_TRANSFER",2700000,period);
            show(0);tagged("statement_unlinked");
            AccessibilityNodeInfo tree=root();
            ok(tree!=null&&!tree.findAccessibilityNodeInfosByText("مطابقة تحويل بين حساباتك").isEmpty(),"Warning did not open the matching dialog");
            screen("04-link-dialog");
            result.putString("stream","Month statement UI checks passed: "+checks+"; screenshots in cache/month-statement\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","Month statement UI FAILED: "+trace);}
        finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null)db.clearAllData();
            runner.finish(status,result);
        }
    }
}
