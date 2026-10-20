package com.mohamedgamal.masareef;

/** Installs local crash capture before any activity or background receiver runs. */
public final class FollowMoneyApp extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
    }
}
