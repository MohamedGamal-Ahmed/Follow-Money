package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Financial broadcasts must refresh safe pages independently of the expense-only suggestion cache. */
final class LedgerRefreshRegressionTest {
    private final Instrumentation runner;
    private final Context context;
    private final ExpenseDb db;
    private MainActivity activity;
    LedgerRefreshRegressionTest(Instrumentation runner,Context context,ExpenseDb db){
        this.runner=runner;this.context=context;this.db=db;
    }
    private Object get(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private void set(String name,Object value)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object...args)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}
    private interface Action{void run()throws Exception;}
    private void main(Action action){
        final Throwable[] failure={null};
        runner.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        runner.waitForIdleSync();
        if(failure[0] instanceof AssertionError)throw (AssertionError)failure[0];
        if(failure[0]!=null)throw new RuntimeException(failure[0]);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void drain()throws Exception{
        for(int i=0;i<4;i++){
            CountDownLatch done=new CountDownLatch(1);CaptureQueue.submit(done::countDown);
            require(done.await(30,TimeUnit.SECONDS),"capture worker did not drain");runner.waitForIdleSync();
        }
    }
    private void awaitSnapshot(Object previous)throws Exception{
        for(int attempt=0;attempt<100;attempt++){
            final boolean[] ready={false};
            main(()->ready[0]=get("recurringCandidateRows")!=previous
                    &&!((java.util.concurrent.atomic.AtomicBoolean)get("refreshingLedger")).get()
                    &&!(Boolean)get("ledgerRefreshPending"));
            if(ready[0])return;
            Thread.sleep(50);
        }
        throw new AssertionError("financial broadcast snapshot was not applied");
    }
    private static EditText firstInput(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            EditText found=firstInput(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private static boolean hasText(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(hasText(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    void check(String change,boolean editor,boolean pending){
        ExpenseDb original=null;CountDownLatch release=new CountDownLatch(1);
        try{
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(runner.getTargetContext(),MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            drain();original=(ExpenseDb)get("db");
            final Object[] initial={null};
            main(()->{
                set("db",db);set("introVisible",false);set("selectedTab",6);set("searchOpen",false);
                initial[0]=get("recurringCandidateRows");invoke("render",new Class<?>[0]);
                invoke("refreshLedger",new Class<?>[]{boolean.class},true);
            });
            awaitSnapshot(initial[0]);
            final Object[] before={null,null};
            main(()->{
                if(editor){set("selectedTab",12);set("editingCommitment",null);set("editingSuggestion",null);invoke("render",new Class<?>[0]);
                    firstInput(activity.getWindow().getDecorView()).setText("QA unsaved draft");}
                else require((Boolean)invoke("canRedrawNow",new Class<?>[0]),"the transactions page must be safe to redraw");
                before[0]=get("pageScroll");before[1]=get("recurringCandidateRows");
            });
            if(pending){
                CountDownLatch entered=new CountDownLatch(1);
                CaptureQueue.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
                require(entered.await(10,TimeUnit.SECONDS),"worker blocker did not start");
                main(()->invoke("refreshLedger",new Class<?>[]{boolean.class},false));
            }
            // A different helper matches SMS/notification writes: the activity's total_changes stays unchanged.
            if("income".equals(change)){
                SmsParts parts=new SmsParts();parts.add("InstaPay",System.currentTimeMillis(),
                        "You have received EGP 50.00 from qa_refresh@instapay");
                SmsReceiver.captureNow(context,parts);
                require(db.transactionCount()==1&&"income".equals(db.latestInserted().kind),"the synthetic income must be captured");
            }else{
                try(ExpenseDb external=new ExpenseDb(context)){
                    if("balance".equals(change)){
                        long account=external.addPaymentSource("QA balance account","حساب",null);
                        external.putManualBalance(account,"EGP",1234500,System.currentTimeMillis());
                        require(external.currentBalance(account,"EGP").amountMinor==1234500,"the balance must change");
                    }else{
                        require(external.putCommitment(null,"QA commitment",5000,"EGP",LocalDate.now().plusMonths(1),
                                "monthly","subscription",null,0,1,"qa commitment",null)>0,"the commitment must be saved");
                    }
                }
                SmsReceiver.notifyDataChanged(context);
            }
            if(pending){
                final boolean[] received={false};
                for(int i=0;i<100&&!received[0];i++){main(()->received[0]=(Boolean)get("ledgerRefreshPending"));if(!received[0])Thread.sleep(50);}
                require(received[0],"DATA_CHANGED must arrive while the earlier snapshot is pending");release.countDown();
            }
            awaitSnapshot(before[1]);
            require(db.recurringCandidateTransactions().isEmpty(),"expense suggestions must remain unchanged");
            main(()->{
                if(editor){
                    require(before[0]==get("pageScroll"),"a financial broadcast must not rebuild an open editor");
                    require("QA unsaved draft".contentEquals(firstInput(activity.getWindow().getDecorView()).getText()),"the editor draft must survive");
                }else{
                    require(before[0]!=get("pageScroll"),change+" DATA_CHANGED must redraw even with unchanged expense suggestions");
                    if("income".equals(change))require(hasText(activity.getWindow().getDecorView(),"qa_refresh"),"captured income must appear without navigation");
                }
            });
        }catch(Exception error){throw new AssertionError(error);}
        finally{
            release.countDown();
            if(activity!=null){
                final ExpenseDb restore=original;
                try{drain();main(()->{if(restore!=null)set("db",restore);activity.finish();});}catch(Exception ignored){}
            }
        }
    }
}
