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
    static synchronized ExpenseDb database(Context context){
        if(database==null)database=new ExpenseDb(context.getApplicationContext());
        return database;
    }
    static void write(Runnable work){WRITES.execute(work);}
    static void read(Runnable work){READS.execute(work);}
}
