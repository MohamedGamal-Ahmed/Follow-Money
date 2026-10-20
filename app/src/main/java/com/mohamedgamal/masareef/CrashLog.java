package com.mohamedgamal.masareef;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;

/** Last three local failures, without exception messages or any ledger access. */
final class CrashLog {
    private static final int MAX_BYTES=64*1024;
    private CrashLog() {}

    static void install(Context context) {
        Context app=context.getApplicationContext();
        Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try {save(app,error);} catch(Throwable ignored) { /* Always reach Android's crash handler. */ }
            if(original!=null)original.uncaughtException(thread,error);
        });
    }

    static synchronized void save(Context context,Throwable error)throws java.io.IOException {
        File folder=new File(context.getFilesDir(),"crashes");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Crash directory unavailable");
        String report="Time: "+Instant.now()+"\nVersion: "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")"
                +"\nAndroid: "+Build.VERSION.RELEASE+" ("+Build.VERSION.SDK_INT+")\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL
                +"\n\n"+Redaction.crashTrace(error);
        byte[] bytes=report.getBytes(StandardCharsets.UTF_8);
        File file=new File(folder,"crash-"+System.currentTimeMillis()+"-"+java.util.UUID.randomUUID()+".txt");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)) {out.write(bytes,0,Math.min(bytes.length,MAX_BYTES));}
        File[] files=files(folder);
        for(int i=3;i<files.length;i++)if(!files[i].delete())throw new java.io.IOException("Crash retention failed");
    }

    static String latest(Context context) {
        File[] files=files(new File(context.getFilesDir(),"crashes"));
        if(files.length==0||files[0].length()>MAX_BYTES)return null;
        try{return new String(Files.readAllBytes(files[0].toPath()),StandardCharsets.UTF_8);}
        catch(java.io.IOException unreadable){return null;}
    }

    private static File[] files(File folder) {
        File[] files=folder.listFiles(file->file.isFile()&&file.getName().startsWith("crash-")&&file.getName().endsWith(".txt"));
        if(files==null)return new File[0];
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
        return files;
    }
}
