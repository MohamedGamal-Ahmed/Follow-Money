package com.mohamedgamal.masareef;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process lifetime resources. Borrowers never close the shared helper or stop these workers. */
final class AppResources {
    private AppResources(){ }
    private static ExpenseDb database;
    private static final ExecutorService WRITES=Executors.newSingleThreadExecutor(work->thread(work,"capture-worker"));
    private static final ExecutorService READS=Executors.newFixedThreadPool(2,work->thread(work,"app-io"));
    private static Thread thread(Runnable work,String name){
        Thread thread=new Thread(work,name);thread.setDaemon(true);return thread;
    }
    private static boolean startChecked;
    static synchronized ExpenseDb database(Context context){
        if(database==null){
            // Once per process, before the first open: a restore interrupted after its file swap in an earlier process left
            // the previous database beside the live one; put it back. Later reopens (a restore in progress) must not.
            if(!startChecked){
                startChecked=true;
                if(ExpenseDb.recoverInterruptedRestore(context))android.util.Log.w("FollowMoney","Recovered the previous database after an interrupted restore");
            }
            database=new ExpenseDb(context.getApplicationContext());
        }
        return database;
    }
    /** Restore only: closes the shared helper so the database file can be swapped; the next database() call reopens it. */
    static synchronized void closeDatabase(){
        if(database!=null){database.close();database=null;}
    }
    static void write(Runnable work){WRITES.execute(work);}
    static void read(Runnable work){READS.execute(work);}
}
