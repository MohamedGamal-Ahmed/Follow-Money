package com.mohamedgamal.masareef;

import android.app.Activity;
import android.content.SharedPreferences;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Google Play's flexible in-app update (owner, 24 Oct 2026). The app has no Internet permission: the library asks the
 * Play Store app on the phone, and Play answers whether a newer version is published. Outside a Play install (debug,
 * sideloaded APK, emulator without Play) the request fails and the card simply never shows.
 */
final class InAppUpdate {
    private static final String PREFS = "in_app_update", SNOOZED_VERSION = "snoozed_version", SNOOZED_AT = "snoozed_at";

    private final Activity activity;
    private final Runnable changed;
    private final AppUpdateManager manager;
    private final InstallStateUpdatedListener listener;
    private AppUpdateInfo info;
    private UpdatePrompt.Status status = UpdatePrompt.Status.NONE;
    private UpdatePrompt.Card card = UpdatePrompt.Card.NONE;

    InAppUpdate(Activity activity, Runnable changed) {
        this.activity = activity;
        this.changed = changed;
        AppUpdateManager created = null;
        try { created = AppUpdateManagerFactory.create(activity); } catch (RuntimeException unavailable) { /* no Play: no card */ }
        manager = created;
        listener = state -> {
            int install = state.installStatus();
            set(install == InstallStatus.DOWNLOADED ? UpdatePrompt.Status.DOWNLOADED
                    : install == InstallStatus.DOWNLOADING || install == InstallStatus.PENDING ? UpdatePrompt.Status.DOWNLOADING
                    : UpdatePrompt.Status.NONE);
        };
        if (manager != null) manager.registerListener(listener);
    }

    UpdatePrompt.Card card() { return card; }

    /** On every resume: an update downloaded while the app was away still needs its restart. */
    void refresh() {
        if (manager == null) return;
        try {
            manager.getAppUpdateInfo().addOnSuccessListener(found -> {
                info = found;
                int install = found.installStatus();
                status = install == InstallStatus.DOWNLOADED ? UpdatePrompt.Status.DOWNLOADED
                        : install == InstallStatus.DOWNLOADING || install == InstallStatus.PENDING ? UpdatePrompt.Status.DOWNLOADING
                        : UpdatePrompt.Status.NONE;
                decide();
            }).addOnFailureListener(error -> { info = null; status = UpdatePrompt.Status.NONE; decide(); });
        } catch (RuntimeException unavailable) { info = null; }
    }

    /** «تحديث»: Play shows its own confirmation, then downloads in the background while the app stays usable. */
    void start() {
        AppUpdateInfo current = info;
        if (manager == null || current == null) return;
        info = null; // one AppUpdateInfo starts one flow; the next resume asks Play again
        try {
            manager.startUpdateFlow(current, activity, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                    .addOnSuccessListener(result -> { if (result != Activity.RESULT_OK) snooze(current.availableVersionCode()); })
                    .addOnFailureListener(error -> refresh());
        } catch (RuntimeException failed) { refresh(); }
    }

    /** «بعدين». */
    void later() {
        AppUpdateInfo current = info;
        if (current != null) snooze(current.availableVersionCode());
    }

    /** «إعادة التشغيل»: Play installs and reopens the app. */
    void complete() { if (manager != null) manager.completeUpdate(); }

    void dispose() { if (manager != null) manager.unregisterListener(listener); }

    private void snooze(int version) {
        prefs().edit().putInt(SNOOZED_VERSION, version).putLong(SNOOZED_AT, System.currentTimeMillis()).apply();
        decide();
    }

    private void set(UpdatePrompt.Status next) { status = next; decide(); }

    private void decide() {
        AppUpdateInfo current = info;
        SharedPreferences prefs = prefs();
        UpdatePrompt.Card next = UpdatePrompt.card(
                current != null && current.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                current != null && current.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                current == null ? null : current.clientVersionStalenessDays(), status,
                current == null ? 0 : current.availableVersionCode(),
                prefs.getInt(SNOOZED_VERSION, 0), prefs.getLong(SNOOZED_AT, 0), System.currentTimeMillis());
        if (next == card) return;
        card = next;
        changed.run();
    }

    private SharedPreferences prefs() { return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE); }
}
