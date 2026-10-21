package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import java.util.Collections;

/** Real Android listener callbacks; isolated emulator and invented shell notifications only. */
final class NotificationCaptureTest {
    private final Instrumentation runner;
    private int checks;
    NotificationCaptureTest(Instrumentation runner){this.runner=runner;}
    private void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private void pause()throws Exception{Thread.sleep(100);}
    private void shell(String command)throws Exception{
        try(android.os.ParcelFileDescriptor fd=runner.getUiAutomation().executeShellCommand(command);
            java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){
            byte[] bytes=new byte[1024];while(input.read(bytes)!=-1){}
        }
    }
    private PaymentNotificationListener listener()throws Exception{
        java.lang.reflect.Field field=PaymentNotificationListener.class.getDeclaredField("connected");field.setAccessible(true);
        return ((java.lang.ref.WeakReference<PaymentNotificationListener>)field.get(null)).get();
    }
    private void post(String tag,String body)throws Exception{
        // executeShellCommand tokenizes arguments; NBSP keeps one argument and is normalized by the parser.
        shell("cmd notification post -t Telda -S bigtext "+tag+" "+body.replace(' ','\u00a0'));
    }
    private void count(ExpenseDb db,int expected)throws Exception{
        for(int i=0;i<100;i++){if(db.transactionCount()==expected)return;pause();}
        check(false,"Expected "+expected+" rows, got "+db.transactionCount());
    }
    void run(){
        Bundle result=new Bundle();int status=-1;ExpenseDb db=null;boolean owned=false;
        Context target=runner.getTargetContext();
        String component=target.getPackageName()+"/"+PaymentNotificationListener.class.getName();
        try{
            if(!target.getPackageName().endsWith(".notificationqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Isolated emulator required");
            db=new ExpenseDb(target);check(db.transactionCount()==0,"Existing ledger retained; empty fixture required");owned=true;
            android.content.SharedPreferences settings=target.getSharedPreferences("notification_settings",0);
            settings.edit().putStringSet("sources",Collections.emptySet()).commit();
            shell("cmd notification allow_listener "+component);
            for(int i=0;i<100&&listener()==null;i++)pause();
            check(listener()!=null,"Android connected the actual notification listener");
            String payment="تم دفع 15 ج.م. في \"QA Butlers\" رصيدك: 31.5 ج.م.";
            post("qa-telda-before-selection",payment);
            boolean active=false;
            for(int i=0;i<100&&!active;i++){
                for(StatusBarNotification item:listener().getActiveNotifications())if("qa-telda-before-selection".equals(item.getTag()))active=true;
                if(!active)pause();
            }
            check(active,"Synthetic Telda-shaped notification is active in Android");
            Thread.sleep(500);check(db.transactionCount()==0,"Unselected source cannot book a transaction");
            settings.edit().putStringSet("sources",Collections.singleton("com.android.shell")).commit();
            Thread.sleep(500);check(db.transactionCount()==0,"Selection alone does not replay an already-delivered notification");
            PaymentNotificationListener.refreshSelectedSources(target);count(db,1);
            ExpenseDb.Transaction paid=db.latestInserted();
            check(paid.amountMinor==1500&&"expense".equals(paid.kind)&&paid.merchant.contains("Butlers"),"Actual listener stored payment rather than balance");
            check(db.sourceEvidence(paid.id).size()==1,"Captured notification kept its source evidence");
            PaymentNotificationListener.refreshSelectedSources(target);Thread.sleep(500);
            check(db.transactionCount()==1,"Active replay is idempotent");
            post("qa-telda-live","تم دفع 17.50 ج.م. في \"QA Store\" رصيدك: 14 ج.م.");count(db,2);
            check(db.latestInserted().amountMinor==1750,"New callback captures without opening the UI");
            post("qa-telda-price","سوف يتم تحديث سعر اشتراك Plus إلى 49.00 ج.م. مع العلم أنه تم إضافة مزايا جديدة للاشتراك");
            Thread.sleep(1000);check(db.transactionCount()==2,"Tariff announcement creates no money movement");
            settings.edit().putStringSet("sources",Collections.emptySet()).commit();
            post("qa-telda-disabled","تم دفع 25 ج.م. في \"QA Other\" رصيدك: 10 ج.م.");
            Thread.sleep(500);check(db.transactionCount()==2,"Removed source is ignored");
            // Observed on the owner's Samsung: DecoratedCustomViewStyle, full payment in
            // android.title, android.text=null, no android.bigText. Exercise the actual queue.
            android.app.Notification titleOnly=new android.app.Notification();
            titleOnly.extras=new Bundle();
            String titlePayment="تم دفع 15 ج.م. في \"QA Title Butlers\"\nرصيدك: 16.5 ج.م.";
            titleOnly.extras.putCharSequence(android.app.Notification.EXTRA_TITLE,titlePayment);
            titleOnly.extras.putCharSequence(android.app.Notification.EXTRA_TEXT,null);
            titleOnly.extras.putString(android.app.Notification.EXTRA_TEMPLATE,"android.app.Notification$DecoratedCustomViewStyle");
            check(titlePayment.equals(PaymentNotificationListener.notificationBody(titleOnly)),"Title-only custom payment is retained");
            settings.edit().putStringSet("sources",Collections.singleton("com.android.shell")).commit();
            StatusBarNotification custom=new StatusBarNotification("com.android.shell","com.android.shell",885,"qa-telda-title",2000,0,0,titleOnly,android.os.Process.myUserHandle(),System.currentTimeMillis());
            listener().onNotificationPosted(custom);count(db,3);
            ExpenseDb.Transaction titlePaid=db.latestInserted();
            check(titlePaid.amountMinor==1500&&"expense".equals(titlePaid.kind)&&titlePaid.merchant.contains("Butlers"),"Title-only callback stores payment, not remaining balance");
            check(db.sourceEvidence(titlePaid.id).get(0).body.equals(titlePayment),"Title-only evidence is preserved");
            listener().onNotificationPosted(custom);Thread.sleep(500);
            check(db.transactionCount()==3,"Repeated title-only callback is idempotent");
            titleOnly.extras.putCharSequence(android.app.Notification.EXTRA_BIG_TEXT,"  ");
            titleOnly.extras.putCharSequence(android.app.Notification.EXTRA_TEXT,"regular body");
            check(PaymentNotificationListener.notificationBody(titleOnly).endsWith(" regular body"),"Empty big text falls back to regular text");
            titleOnly.flags=android.app.Notification.FLAG_GROUP_SUMMARY;
            check(PaymentNotificationListener.notificationBody(titleOnly)==null,"Group summaries remain ignored");
            check(PaymentNotificationListener.notificationBody(new android.app.Notification())==null,"Empty notifications remain ignored");
            result.putString("stream","Notification capture checks passed: "+checks+"; real listener, selection replay, live callback, dedup, price notice, exclusion and Samsung title-only callback regression\n");
        }catch(Throwable failure){status=0;java.io.StringWriter trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Notification capture FAILED: "+trace);}
        finally{
            if(owned){
                try{PaymentNotificationListener service=listener();if(service!=null)for(StatusBarNotification item:service.getActiveNotifications())if("com.android.shell".equals(item.getPackageName())&&item.getTag()!=null&&item.getTag().startsWith("qa-telda-"))service.cancelNotification(item.getKey());}catch(Exception ignored){}
                try{shell("cmd notification disallow_listener "+component);}catch(Exception ignored){}
                target.getSharedPreferences("notification_settings",0).edit().putStringSet("sources",Collections.emptySet()).commit();
                if(db!=null)db.clearAllData();
            }
            if(db!=null)db.close();runner.finish(status,result);
        }
    }
}
