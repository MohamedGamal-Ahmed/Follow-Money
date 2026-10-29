package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 0.4.5: once per app version, reads the kept «ما اتسجلتش» messages again with the current rules (MissedReevaluation)
 * and marks those it would now record, for the user to review. Runs on the capture worker; nothing reaches the ledger.
 */
final class MissedRecovery {
    static final String STATE = "app_state", VERSION_KEY = "missed_reeval_version", STRIP_HIDDEN_KEY = "recovered_strip_hidden";

    /**
     * No-op unless the stored version differs from the running one. A failure leaves the stored version alone, so the
     * next launch tries again, and never reaches the caller. whenDone runs on the worker after a successful pass.
     */
    static void runIfDue(Context context, Runnable whenDone) {
        final Context app = context.getApplicationContext();
        final SharedPreferences state = app.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        if (!MissedReevaluation.due(state.getInt(VERSION_KEY, MissedReevaluation.NEVER), BuildConfig.VERSION_CODE)) return;
        CaptureQueue.submit(() -> {
            try {
                // Two launches may both have queued a pass; the second finds it already done.
                if (!MissedReevaluation.due(state.getInt(VERSION_KEY, MissedReevaluation.NEVER), BuildConfig.VERSION_CODE)) return;
                ExpenseDb db = AppResources.database(app);
                PipelineDecider.context(db, "live").prepare();
                int recovered = ShadowStore.reevaluateMissed(db.getWritableDatabase(), selectedApps(app), ParserShadow.learning(), System.currentTimeMillis());
                SharedPreferences.Editor edit = state.edit().putInt(VERSION_KEY, BuildConfig.VERSION_CODE);
                if (recovered > 0) edit.putBoolean(STRIP_HIDDEN_KEY, false);
                edit.apply();
                if (whenDone != null) whenDone.run();
            } catch (RuntimeException | LinkageError failure) {
                android.util.Log.w("FollowMoney", "Missed-message re-read skipped: " + failure.getClass().getSimpleName());
            }
        });
    }

    /** The notification apps the user selected that are installed, package to label. */
    static Map<String, String> selectedApps(Context context) {
        Map<String, String> labels = new HashMap<>();
        Set<String> selected = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
                .getStringSet("sources", java.util.Collections.emptySet());
        PackageManager packages = context.getPackageManager();
        for (String packageName : selected) {
            try { labels.put(packageName, packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()); }
            catch (PackageManager.NameNotFoundException removed) { /* an app that is gone cannot match */ }
        }
        return labels;
    }

    /**
     * «سجّلها»: records a suggestion through the same live path a new message takes (exclusions, dedupe, settlement and
     * the legacy guards all apply). Returns what happened; the row leaves the list only for an outcome that removes it.
     * Call on the capture worker.
     */
    static MissedReevaluation.Outcome register(Context context, ShadowStore.Ununderstood message) {
        ExpenseDb db = AppResources.database(context);
        String key = message.sourceKey != null ? message.sourceKey : MissedReevaluation.sourceKey(message.row(), selectedApps(context));
        if (key == null || message.text == null) return MissedReevaluation.Outcome.NOT_RECORDED;
        CaptureResult result = db.capture(MissedReevaluation.request(message.row(), key));
        MissedReevaluation.Outcome outcome = MissedReevaluation.outcome(result.accepted, result.reason == null ? null : result.reason.name());
        if (MissedReevaluation.removesSuggestion(outcome)) ShadowStore.removeUnunderstood(db.getWritableDatabase(), message.hash);
        if (result.dbChanged) SmsReceiver.notifyDataChanged(context);
        return outcome;
    }

    private MissedRecovery() {}
}
