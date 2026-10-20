package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Four synthetic HSBC trailing-sign alerts captured live, then shown in the transactions list with their signs and the bank logo. */
final class HsbcFormatUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private boolean owned;
    private int checks;
    HsbcFormatUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private String plain(CharSequence value){return value==null?"":value.toString().replaceAll("[‎‏‪-‮⁦-⁩]","");}
    private Object get(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private void showTransactions(LocalDate month)throws Exception{
        runner.runOnMainSync(()->{try{
            Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,6);
            Field selected=MainActivity.class.getDeclaredField("selectedMonth");selected.setAccessible(true);selected.set(activity,month);
            Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void collect(View view,List<String> text,List<String> logos){
        if(view instanceof TextView)text.add(plain(((TextView)view).getText()));
        if(view instanceof ImageView&&view.getContentDescription()!=null)logos.add(plain(view.getContentDescription()));
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),text,logos);
    }
    private void screenshot(Context target)throws Exception{
        File dir=new File(target.getCacheDir(),"hsbc-format");ok(dir.isDirectory()||dir.mkdirs(),"Screenshot folder unavailable");
        android.graphics.Bitmap image=runner.getUiAutomation().takeScreenshot();ok(image!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(dir,"hsbc-rows.png"))){ok(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{image.recycle();}
    }
    private static String code(LocalDate day){return day.format(DateTimeFormatter.ofPattern("ddMMMyy",Locale.ENGLISH)).toUpperCase(Locale.ROOT);}
    private static long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private ExpenseDb.Transaction captured(String body,long when){ok(db.addSms("HSBC",body,when),"HSBC alert was not captured: "+body);return db.lastCaptured();}
    private int count(List<String> text,String wanted){int n=0;for(String s:text)if(s.equals(wanted))n++;return n;}
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".hsbcqa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable hsbcqa emulator required");
            db=AppResources.database(target);ok(db.transactionCount()==0,"QA target must start empty");owned=true;
            LocalDate month=LocalDate.now().minusMonths(1).withDayOfMonth(1);
            LocalDate salaryDay=month.withDayOfMonth(24),moveDay=month.withDayOfMonth(25),cashDay=month.withDayOfMonth(17);
            // Synthetic alerts in the tester's format (account digits, amounts and balances are invented).
            ExpenseDb.Transaction salary=captured("From HSBC: "+code(salaryDay)+" TT Payment from 012-345***-679 USD 2,083.85+ Your available balance is USD 2,150.10",at(salaryDay,10));
            ExpenseDb.Transaction out=captured("From HSBC: "+code(moveDay)+" Phone Banking Transfer from 012-345***-679 USD 1,700.00- Your available balance is USD 450.10",at(moveDay,11));
            ExpenseDb.Transaction in=captured("From HSBC: "+code(moveDay)+" Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74",at(moveDay,11)+60000);
            ExpenseDb.Transaction cash=captured("From HSBC: "+code(cashDay)+" ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00",at(cashDay,12));
            ok("income".equals(salary.kind)&&"TT Payment".equals(salary.merchant)&&salary.amountMinor==208385&&"USD".equals(salary.currency),"TT Payment credit");
            ok("expense".equals(out.kind)&&"Phone Banking Transfer".equals(out.merchant)&&"تحويلات".equals(out.category)&&out.amountMinor==170000,"USD debit");
            ok("income".equals(in.kind)&&"Phone Banking Transfer".equals(in.merchant)&&in.amountMinor==8765000&&"EGP".equals(in.currency),"EGP credit");
            ok("expense".equals(cash.kind)&&"ATM Cash Withdrawal".equals(cash.merchant)&&"سحب نقدي".equals(cash.category)&&cash.amountMinor==200000,"ATM withdrawal");
            ok(db.transactionCount()==4,"Expected exactly four rows");
            for(ExpenseDb.Transaction row:new ExpenseDb.Transaction[]{salary,out,in,cash}){
                ok(row.paymentSourceId!=null,"Row has no detected account: "+row.merchant);
                ExpenseDb.PaymentSource source=db.paymentSource(row.paymentSourceId);
                ok(source!=null&&"HSBC".equals(source.name)&&"hsbc".equals(BrandCatalog.bank(source.name)),"Detected account is not an HSBC source");
            }
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true)
                    .putInt("repair_version",ExpenseDb.REPAIR_VERSION).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();showTransactions(month);
            List<String> text=new ArrayList<>(),logos=new ArrayList<>();collect((View)get("page"),text,logos);
            ok(text.contains("TT Payment"),"TT Payment row is missing");
            ok(count(text,"Phone Banking Transfer")==2,"Both Phone Banking Transfer rows should be visible");
            ok(text.contains("ATM Cash Withdrawal"),"ATM Cash Withdrawal row is missing");
            ok(text.contains("+"+MainActivity.money(208385,"USD")),"Salary should show as a credit");
            ok(text.contains("−"+MainActivity.money(170000,"USD")),"USD transfer should show as a debit");
            ok(text.contains("+"+MainActivity.money(8765000,"EGP")),"EGP transfer should show as a credit");
            ok(text.contains("−"+MainActivity.money(200000,"EGP")),"Cash withdrawal should show as a debit");
            ok(!String.join("\n",text).contains("HSBC:"),"The bank name must not appear as a merchant");
            ok(count(logos,"HSBC")>=4,"Expected the HSBC logo on all four rows, found "+count(logos,"HSBC"));
            screenshot(target);
            result.putString("stream","HSBC format UI checks passed: "+checks+"; screenshot in cache/hsbc-format/hsbc-rows.png\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","HSBC format UI FAILED: "+trace);}
        finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null)db.clearAllData();
            runner.finish(status,result);
        }
    }
}
