package com.mohamedgamal.masareef;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Optional device-authentication gate shared by the two activities that display financial data. */
final class AppLock {
    static final String SETTINGS = "security_settings";
    private static final String ENABLED = "enabled", TIMEOUT = "timeout_seconds";
    private static final String SECURE_PREVIEW = "secure_preview", STOPPED_AT = "stopped_at_elapsed";
    private static final int CREDENTIAL_REQUEST = 8103;
    private static volatile boolean sessionAuthorized;
    private static int startedActivities;

    private final Activity activity;
    private final SharedPreferences settings;
    private final Runnable onUnlocked;
    private boolean locked;
    private boolean authenticating;
    private boolean disposed;
    private boolean started;
    private boolean coverVisible;
    private boolean autoPrompted;
    private boolean awaitingOwnResult;
    private CancellationSignal cancellation;
    private Runnable credentialSuccess;
    private Button retryButton;

    AppLock(Activity activity, Runnable onUnlocked) {
        this.activity = activity;
        this.onUnlocked = onUnlocked;
        settings = activity.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        if (enabled() && !deviceSecure()) {
            disableUnavailable();
        } else if (enabled()) {
            long stoppedAt = settings.getLong(STOPPED_AT, -1);
            locked = !sessionAuthorized && stoppedAt < 0 || expired(stoppedAt);
        }
        applySecurePreview();
        if (locked) showCover();
    }

    boolean enabled() { return settings.getBoolean(ENABLED, false); }
    boolean locked() { return locked; }
    boolean deviceSecure() {
        KeyguardManager manager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isDeviceSecure();
    }
    int timeoutSeconds() { return settings.getInt(TIMEOUT, 0); }
    boolean securePreview() { return settings.getBoolean(SECURE_PREVIEW, true); }

    void setTimeoutSeconds(int seconds) {
        if (seconds != 0 && seconds != 60 && seconds != 300) throw new IllegalArgumentException("timeout");
        settings.edit().putInt(TIMEOUT, seconds).apply();
    }
    void setSecurePreview(boolean secure) {
        settings.edit().putBoolean(SECURE_PREVIEW, secure).apply();
        applySecurePreview();
    }

    void enable(Runnable onEnabled) {
        if (!deviceSecure()) {
            Toast.makeText(activity, "فعّل قفل الشاشة أولًا", Toast.LENGTH_LONG).show();
            return;
        }
        authenticate(() -> {
            settings.edit().putBoolean(ENABLED, true).remove(STOPPED_AT).apply();
            sessionAuthorized = true;
            applySecurePreview();
            onEnabled.run();
        });
    }

    void disable() {
        settings.edit().putBoolean(ENABLED, false).remove(STOPPED_AT).apply();
        locked = false;
        coverVisible = false;
        applySecurePreview();
    }

    /** Called before any resume render or incoming intent is handled. */
    boolean guard() {
        if (!enabled()) return false;
        if (!deviceSecure()) {
            disableUnavailable();
            if (locked) unlock();
            return false;
        }
        if (!locked && expired(settings.getLong(STOPPED_AT, -1))) {
            locked = true;
            autoPrompted = false;
        }
        if (locked) showCover();
        return locked;
    }

    void onStart() {
        if (!started) {
            started = true;
            startedActivities++;
        }
        // The separately named debug QA package inspects the cover before opening the OS prompt.
        if (qaMode()
                && activity.getIntent().getBooleanExtra("app_lock_qa_manual_prompt", false)) {
            guard();
            return;
        }
        if (guard() && !authenticating && !autoPrompted) {
            autoPrompted = true;
            activity.getWindow().getDecorView().post(this::unlockWithDevice);
        }
    }

    void onStop() {
        if (started) {
            started = false;
            startedActivities = Math.max(0, startedActivities - 1);
        }
        if (startedActivities == 0 && enabled() && !locked && !authenticating && !awaitingOwnResult
                && !activity.isChangingConfigurations()) {
            long priorStop = settings.getLong(STOPPED_AT, -1);
            if (!expired(priorStop))
                settings.edit().putLong(STOPPED_AT, SystemClock.elapsedRealtime()).apply();
        }
    }

    void unlockWithDevice() {
        if (!locked || authenticating) return;
        autoPrompted = true;
        authenticate(this::unlock);
    }

    void cancelAuthentication() {
        if (cancellation != null) cancellation.cancel();
    }

    void dispose() {
        disposed = true;
        cancelAuthentication();
        credentialSuccess = null;
        retryButton = null;
    }

    void beginOwnResult(int requestCode) {
        if (requestCode != CREDENTIAL_REQUEST) awaitingOwnResult = true;
    }

    void endOwnResult(int requestCode) {
        if (requestCode != CREDENTIAL_REQUEST) awaitingOwnResult = false;
    }

    boolean onActivityResult(int requestCode, int resultCode) {
        if (requestCode != CREDENTIAL_REQUEST) {
            endOwnResult(requestCode);
            return false;
        }
        authenticating = false;
        if (disposed) return true;
        if (resultCode == Activity.RESULT_OK) {
            Runnable success = credentialSuccess;
            credentialSuccess = null;
            if (success != null) success.run();
        } else {
            credentialSuccess = null;
            showRetry();
        }
        return true;
    }

    private void authenticate(Runnable success) {
        authenticating = true;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                        .setTitle("فتح Follow Money")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
                cancellation = new CancellationSignal();
                prompt.authenticate(cancellation, activity.getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                cancellation = null;
                                authenticating = false;
                                if (!disposed) success.run();
                            }
                            @Override public void onAuthenticationError(int code, CharSequence message) {
                                cancellation = null;
                                authenticating = false;
                                if (disposed) return;
                                if (code == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                                    disableUnavailable();
                                    if (locked) unlock();
                                } else showRetry();
                            }
                        });
                return;
            } catch (SecurityException | IllegalArgumentException unavailable) {
                cancellation = null;
                // A device credential still works if a platform biometric prompt is unavailable.
            }
        }
        KeyguardManager manager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        Intent credential = manager == null ? null : manager.createConfirmDeviceCredentialIntent(
                "فتح Follow Money", "أكد هويتك بقفل الجهاز");
        if (credential == null) {
            authenticating = false;
            disableUnavailable();
            if (locked) unlock();
        } else {
            credentialSuccess = success;
            try {
                activity.startActivityForResult(credential, CREDENTIAL_REQUEST);
            } catch (RuntimeException unavailable) {
                credentialSuccess = null;
                authenticating = false;
                disableUnavailable();
                if (locked) unlock();
            }
        }
    }

    private boolean expired(long stoppedAt) {
        if (stoppedAt < 0) return false;
        long elapsed = SystemClock.elapsedRealtime() - stoppedAt;
        return elapsed < 0 || elapsed >= timeoutSeconds() * 1000L;
    }

    private void unlock() {
        locked = false;
        coverVisible = false;
        autoPrompted = false;
        sessionAuthorized = true;
        settings.edit().remove(STOPPED_AT).apply();
        onUnlocked.run();
    }

    private void disableUnavailable() {
        settings.edit().putBoolean(ENABLED, false).remove(STOPPED_AT).apply();
        applySecurePreview();
        Toast.makeText(activity, "قفل الجهاز غير متاح؛ اتعطّل قفل التطبيق عشان تقدر تفتح بياناتك", Toast.LENGTH_LONG).show();
    }

    private void applySecurePreview() {
        if (enabled() && securePreview() && !qaMode())
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private boolean qaMode() {
        return BuildConfig.DEBUG && activity.getPackageName().endsWith(".appLockQa");
    }

    private void showRetry() {
        if (retryButton != null && locked) retryButton.setText("حاول مرة أخرى");
    }

    private void showCover() {
        if (coverVisible) return;
        coverVisible = true;
        LinearLayout cover = new LinearLayout(activity);
        cover.setOrientation(LinearLayout.VERTICAL);
        cover.setGravity(Gravity.CENTER);
        cover.setBackgroundColor(0xff0b3d2e);
        cover.setPadding(32, 32, 32, 32);
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_launcher);
        cover.addView(icon, new LinearLayout.LayoutParams(96, 96));
        TextView title = new TextView(activity);
        title.setText("التطبيق مقفول");
        title.setTextColor(0xffffffff);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        cover.addView(title);
        retryButton = new Button(activity);
        retryButton.setText("فتح");
        retryButton.setOnClickListener(v -> unlockWithDevice());
        cover.addView(retryButton);
        activity.setContentView(cover);
    }
}
