package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real activity destruction while a worker still owns an open ledger transaction. */
final class ResourceLifetimeTest {
    private final Instrumentation runner;
    ResourceLifetimeTest(Instrumentation runner){this.runner=runner;}
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private void drain()throws Exception{
        CountDownLatch done=new CountDownLatch(1);CaptureQueue.submit(done::countDown);
        require(done.await(30,TimeUnit.SECONDS),"Worker failed to drain");runner.waitForIdleSync();
    }
    void run(){
        if(!runner.getTargetContext().getPackageName().endsWith(".lifetimeqa"))throw new SecurityException("Isolated lifetime QA package required");
        if(!"ranchu".equals(android.os.Build.HARDWARE)&&!"goldfish".equals(android.os.Build.HARDWARE))throw new SecurityException("Emulator only");
        Bundle result=new Bundle();MainActivity activity=null;
        CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1),done=new CountDownLatch(1);
        Throwable[] failure={null};
        try{
            runner.getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("intro_completed",true).putString("opening_mode","off").commit();
            activity=(MainActivity)runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class)
                    .putExtra("open_transactions",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            drain();
            Field field=MainActivity.class.getDeclaredField("db");field.setAccessible(true);
            ExpenseDb borrowed=(ExpenseDb)field.get(activity);SQLiteDatabase connection=borrowed.getWritableDatabase();
            CaptureQueue.submit(()->{
                try{
                    connection.beginTransaction();started.countDown();
                    require(release.await(20,TimeUnit.SECONDS),"Worker was never released");
                    require(connection.isOpen(),"Activity destroyed the worker's open connection");
                    require(borrowed.addManualEntry(System.currentTimeMillis(),123,"EGP","QA lifetime","تسوق",null,"expense",null,null),"Worker write failed");
                    connection.setTransactionSuccessful();
                }catch(Throwable error){failure[0]=error;}
                finally{try{connection.endTransaction();}catch(Throwable error){if(failure[0]==null)failure[0]=error;}done.countDown();}
            });
            require(started.await(30,TimeUnit.SECONDS),"Worker did not start");
            MainActivity old=activity;
            runner.runOnMainSync(old::finish);
            for(int attempt=0;attempt<100&&!old.isDestroyed();attempt++)Thread.sleep(50);
            require(old.isDestroyed(),"Activity did not finish destruction");
            release.countDown();require(done.await(30,TimeUnit.SECONDS),"Worker did not complete");
            if(failure[0]!=null)throw new AssertionError("Active transaction lost across Activity destruction",failure[0]);
            require(connection.isOpen(),"Destroyed Activity closed the application ledger");
            require(borrowed.latestInserted()!=null&&"QA lifetime".equals(borrowed.latestInserted().merchant),"Committed worker result missing");
            // Posting from a worker must recheck lifecycle when the main thread receives the callback.
            boolean[] delivered={false};Method post=MainActivity.class.getDeclaredMethod("postUi",Runnable.class);post.setAccessible(true);
            CountDownLatch posted=new CountDownLatch(1);
            AppResources.read(()->{try{post.invoke(old,(Runnable)()->delivered[0]=true);}catch(Exception error){failure[0]=error;}finally{posted.countDown();}});
            require(posted.await(30,TimeUnit.SECONDS),"I/O callback did not post");runner.waitForIdleSync();
            require(failure[0]==null&&!delivered[0],"Destroyed Activity received a UI result");
            activity=(MainActivity)runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class)
                    .putExtra("open_transactions",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            drain();require(field.get(activity)==borrowed,"Replacement Activity did not borrow the application helper");
            require(borrowed.getWritableDatabase()==connection&&connection.isOpen(),"Replacement Activity reopened a different connection");
            Instrumentation.ActivityMonitor recreation=runner.addMonitor(MainActivity.class.getName(),null,false);
            try{
                MainActivity previous=activity;runner.runOnMainSync(previous::recreate);
                activity=(MainActivity)runner.waitForMonitorWithTimeout(recreation,10000);
                require(activity!=null&&activity!=previous,"Configuration recreation did not replace Activity");
                drain();require(previous.isDestroyed()&&field.get(activity)==borrowed&&connection.isOpen(),"Configuration recreation changed helper ownership");
            }finally{runner.removeMonitor(recreation);}
            result.putString("stream","Passed: 4; failed: 0\n");runner.finish(-1,result);
        }catch(Throwable error){result.putString("stream","Passed: 0; failed: 1\n"+android.util.Log.getStackTraceString(error));runner.finish(0,result);}
        finally{release.countDown();if(activity!=null){MainActivity old=activity;runner.runOnMainSync(old::finish);}}
    }
}
