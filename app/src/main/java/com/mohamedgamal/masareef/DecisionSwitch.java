package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Step 7: which parser decides what is recorded. The default comes from ParsingPolicy (on in the test build, off in
 * the release build); advanced settings switch it at once, with no update. The new pipeline decides only messages that
 * arrive after it was turned on; nothing recorded earlier is re-analysed or changed. For LEGACY_OPINION_DAYS after the
 * first activation the legacy parser's opinion is still computed (never written) and kept with each message.
 */
final class DecisionSwitch {
    static final String PREFS = "parser_decision";
    private static final String KEY = "new_pipeline_decides", SINCE = "new_pipeline_since", FIRST = "new_pipeline_first";

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static boolean defaultOn() {
        return BuildConfig.DEBUG ? ParsingPolicy.NEW_PIPELINE_DECIDES_TEST_BUILD : ParsingPolicy.NEW_PIPELINE_DECIDES_RELEASE_BUILD;
    }

    /** Whether the new pipeline is the decision-maker now; the first time it is on, the activation time is stored. */
    static boolean on(Context context) {
        SharedPreferences p = prefs(context);
        boolean on = p.getBoolean(KEY, defaultOn());
        if (on && !p.contains(SINCE)) stamp(p, System.currentTimeMillis());
        return on;
    }

    /** The new pipeline decides this message: it is on and the message arrived after it was turned on. */
    static boolean decides(Context context, long receivedAt) {
        return context != null && on(context) && receivedAt >= prefs(context).getLong(SINCE, Long.MAX_VALUE);
    }

    /** Advanced settings: takes effect for the next message. Turning it on again starts a new activation time. */
    static void set(Context context, boolean on) {
        SharedPreferences p = prefs(context);
        p.edit().putBoolean(KEY, on).apply();
        if (on) stamp(p, System.currentTimeMillis());
    }

    static long since(Context context) { return prefs(context).getLong(SINCE, 0); }

    /** The legacy opinion is still computed for messages within LEGACY_OPINION_DAYS of the first activation. */
    static boolean legacyOpinion(Context context, long receivedAt) {
        long first = prefs(context).getLong(FIRST, 0);
        return first == 0 || receivedAt < first + ParsingPolicy.LEGACY_OPINION_DAYS * 86_400_000L;
    }

    private static void stamp(SharedPreferences p, long now) {
        SharedPreferences.Editor edit = p.edit().putLong(SINCE, now);
        if (!p.contains(FIRST)) edit.putLong(FIRST, now);
        edit.apply();
    }

    private DecisionSwitch() {}
}
