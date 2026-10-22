package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import java.lang.reflect.Method;

/** Real row badge views; synthetic merchants in the disposable notification QA app only. */
final class MerchantBadgeUiTest {
    private final Instrumentation runner;
    private int checks;
    MerchantBadgeUiTest(Instrumentation runner){this.runner=runner;}
    private void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private View badge(MainActivity activity,ExpenseDb.Transaction item)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod("rowBadge",ExpenseDb.Transaction.class,int.class);method.setAccessible(true);
        View[] result=new View[1];
        runner.runOnMainSync(()->{try{result[0]=(View)method.invoke(activity,item,42);}catch(Exception e){throw new RuntimeException(e);}});
        return result[0];
    }
    private ExpenseDb.Transaction row(String merchant,String source,Long account){
        return new ExpenseDb.Transaction(-1,System.currentTimeMillis(),1500,"EGP",merchant,"غير مصنف",source,account,null,"expense",null,null,false);
    }
    void run(){
        Bundle result=new Bundle();int status=-1;ExpenseDb db=null;MainActivity activity=null;
        Context target=runner.getTargetContext();boolean owned=false;
        try{
            if(!target.getPackageName().endsWith(".notificationqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Disposable emulator required");
            db=new ExpenseDb(target);check(db.transactionCount()==0,"Empty QA ledger required");owned=true;
            long bank=db.addPaymentSource("HSBC","حساب","1234");
            long unknown=db.addPaymentSource("QA Bank","حساب","5678");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                .putString("opening_mode","off").putInt("repair_version",ExpenseDb.REPAIR_VERSION).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runner.waitForIdleSync();
            View known=badge(activity,row("Kashier Butlers","SMS",bank));
            check(known instanceof ImageView&&((ImageView)known).getDrawable()!=null,"Known Butlers logo is drawn");
            check("Kashier Butlers".contentEquals(known.getContentDescription()),"Merchant logo takes precedence over the bank");
            View fallback=badge(activity,row("QA Unknown Merchant","SMS",bank));
            check(fallback instanceof ImageView&&((ImageView)fallback).getDrawable()!=null,"Uncategorized merchant gets its bank logo");
            check("مصدر العملية: HSBC".contentEquals(fallback.getContentDescription()),"Bank fallback is labelled as the source");
            View unknownSource=badge(activity,row("QA Unknown Merchant","SMS",unknown));
            check("مصدر العملية: QA Bank".contentEquals(unknownSource.getContentDescription()),"Unknown bank still gets a source identity");
            String shellLabel=target.getPackageManager().getApplicationLabel(target.getPackageManager().getApplicationInfo("com.android.shell",0)).toString();
            target.getSharedPreferences("notification_settings",0).edit().putStringSet("sources",java.util.Collections.singleton("com.android.shell")).commit();
            View provider=badge(activity,row("QA Unknown Merchant","إشعار: "+shellLabel,null));
            check(provider instanceof ImageView&&("مصدر العملية: "+shellLabel).contentEquals(provider.getContentDescription()),"Unlinked notification uses its selected provider icon");
            check(db.transactionCount()==0,"Badge rendering creates no financial rows");
            result.putString("stream","Merchant badge checks passed: "+checks+"; merchant priority, bank fallback, source identity and selected provider icon\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Merchant badges FAILED: "+trace);}
        finally{
            if(activity!=null){MainActivity opened=activity;runner.runOnMainSync(opened::finish);}
            if(owned){target.getSharedPreferences("notification_settings",0).edit().putStringSet("sources",java.util.Collections.emptySet()).commit();if(db!=null)db.clearAllData();}
            if(db!=null)db.close();runner.finish(status,result);
        }
    }
}
