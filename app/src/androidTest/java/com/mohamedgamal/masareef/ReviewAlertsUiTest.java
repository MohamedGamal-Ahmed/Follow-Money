package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Real banner navigation, notification controls and calendar-to-details window handoff. */
final class ReviewAlertsUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private int checks;
    private File captures;
    private android.content.res.Configuration originalConfiguration;
    ReviewAlertsUiTest(Instrumentation runner) { this.runner = runner; }
    private interface Action { void run() throws Exception; }
    private void main(Action action) {
        runner.runOnMainSync(() -> { try { action.run(); } catch (Exception e) { throw new RuntimeException(e); } });
        idle();
    }
    private void idle() { runner.waitForIdleSync(); try { runner.getUiAutomation().waitForIdle(300, 3000); } catch (java.util.concurrent.TimeoutException e) { throw new AssertionError(e); } }
    private Object get(String name) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void set(String name, Object value) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
    private void invoke(String name, Class<?>[] types, Object... args) throws Exception { Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(activity, args); }
    private void ok(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private View text(View node, String value) {
        if (node instanceof TextView && value.contentEquals(((TextView) node).getText())) return node;
        if (node instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) node).getChildCount(); i++) { View found = text(((ViewGroup) node).getChildAt(i), value); if (found != null) return found; }
        return null;
    }
    private View described(View node, String value) {
        if (value.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        if (node instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) node).getChildCount(); i++) { View found = described(((ViewGroup) node).getChildAt(i), value); if (found != null) return found; }
        return null;
    }
    private View root() { return activity.getWindow().getDecorView(); }
    private void clickText(String value) { main(() -> { View v = text(root(), value); while (v != null && !v.isClickable()) v = v.getParent() instanceof View ? (View) v.getParent() : null; if (v == null || !v.performClick()) throw new AssertionError("Cannot click " + value); }); }
    private Switch control(String title) { View v = described(root(), title); if (!(v instanceof Switch)) throw new AssertionError("Missing switch: " + title); return (Switch) v; }
    private boolean accessibleText(String value) { AccessibilityNodeInfo r = runner.getUiAutomation().getRootInActiveWindow(); return r != null && !r.findAccessibilityNodeInfosByText(value).isEmpty(); }
    private boolean clickDescription(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        if (value.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) if (clickDescription(node.getChild(i), value)) return true;
        return false;
    }
    private void screen(String name) throws Exception {
        idle(); Thread.sleep(400);
        android.graphics.Bitmap bitmap = runner.getUiAutomation().takeScreenshot(); if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(captures, name + ".png"))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); } finally { bitmap.recycle(); }
    }
    void run(boolean dark, float scale) {
        Bundle result = new Bundle(); int status = -1; Context target = runner.getTargetContext();
        try {
            if (!target.getPackageName().endsWith(".reviewalertsqa") || !"ranchu".equals(android.os.Build.HARDWARE)) throw new SecurityException("Disposable isolated emulator required");
            db = new ExpenseDb(target); if (db.transactionCount() != 0) throw new IllegalStateException("Empty QA ledger required");
            captures = new File(target.getCacheDir(), "review-alerts-" + (dark ? "dark" : "light") + (scale > 1 ? "-large" : "")); captures.mkdirs();
            target.getSharedPreferences(MainActivity.class.getName(), 0).edit().putBoolean("dark_mode", dark).putBoolean("intro_completed", true).putString("opening_mode", "off").putBoolean("initial_import_prompted", true).putBoolean("sms_permission_prompted", true).commit();
            target.getSharedPreferences("notification_settings", 0).edit().putBoolean("access_prompted", true).commit();
            CaptureNotifier.setEnabled(target, false);
            target.getSharedPreferences("reminder_settings", 0).edit().putBoolean("daily_enabled", false).commit();
            MonthlySummaryReceiver.settings(target).edit().putBoolean("enabled", false).commit();
            long account = db.addPaymentSource("QA BANK", "حساب", "1111"), first = -1;
            for (int i = 1; i <= 5; i++) { db.addManualEntry(System.currentTimeMillis() - i * 60000, i * 1000, "EGP", "QA REVIEW " + i, "غير مصنف", account, "expense", null, null); if (i == 1) first = db.latestInserted().id; }
            db.addManualEntry(System.currentTimeMillis() - 360000, 10000, "EGP", "QA CLASSIFIED", "بقالة", account, "expense", null, null);
            db.addManualEntry(System.currentTimeMillis() - 420000, 20000, "EGP", "QA INCOME", "غير مصنف", account, "income", null, null);
            LocalDate date = LocalDate.now().withDayOfMonth(28);
            db.addRecurring("QA CALENDAR A", 5000, "EGP", date); db.addRecurring("QA CALENDAR B", 7000, "EGP", date);
            activity = (MainActivity) runner.startActivitySync(new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); idle();
            main(() -> {
                ((ExpenseDb) get("db")).close(); set("db", db); set("introVisible", false); set("selectedTab", 6);
                originalConfiguration = new android.content.res.Configuration(activity.getResources().getConfiguration());
                android.content.res.Configuration config = new android.content.res.Configuration(originalConfiguration); config.fontScale = scale;
                activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
                set("transactionKindFilter", "income"); set("transactionSearch", "QA INCOME"); invoke("render", new Class<?>[0]);
            });
            ok(accessibleText("5 عمليات محتاجة تصنيف"), "Banner counts active uncategorized expenses");
            clickText("عرض الكل");
            main(() -> {
                ok((int) get("selectedTab") == 6, "Banner must remain in the transaction list");
                ok("غير مصنف".equals(get("transactionCategoryFilter")), "Review filter selected");
                for (int i = 1; i <= 5; i++) ok(text(root(), "QA REVIEW " + i) != null, "Every unclassified transaction is in the list");
                ok(text(root(), "QA CLASSIFIED") == null && text(root(), "QA INCOME") == null, "Only expenses awaiting classification are included");
            });
            screen("01-review-list");
            clickText("QA REVIEW 1"); main(() -> { ok((int) get("selectedTab") == 10, "Individual review starts from selected row"); activity.onBackPressed(); });
            main(() -> ok("غير مصنف".equals(get("transactionCategoryFilter")), "Back retains the review list"));
            db.updateCategories(Collections.singletonList(first), "بقالة");
            main(() -> { invoke("render", new Class<?>[0]); ok(text(root(), "QA REVIEW 1") == null, "A classified expense leaves the review list"); for (int i = 2; i <= 5; i++) ok(text(root(), "QA REVIEW " + i) != null, "Remaining expenses stay in the list"); });
            main(() -> { set("selectedTab", 5); set("settingsSection", "alerts"); invoke("render", new Class<?>[0]); });
            main(() -> {
                ok(!control("إشعار بكل عملية").isChecked(), "Capture setting initially off");
                ok(!control("تذكير يومي بالمصاريف").isChecked(), "Daily setting initially off");
                ok(!control("ملخص أول الشهر").isChecked(), "Monthly setting initially off");
                control("إشعار بكل عملية").performClick();
            });
            main(() -> { ok(CaptureNotifier.enabled(target) && control("إشعار بكل عملية").isChecked(), "Capture switch persists on"); ok(text(root(), "مفعّل بالتطبيق · موقوف من إعدادات الجهاز") == null, "A delivered switch shows no blocked status"); control("تذكير يومي بالمصاريف").performClick(); });
            main(() -> { ok(target.getSharedPreferences("reminder_settings", 0).getBoolean("daily_enabled", false), "Daily switch persists on"); control("تذكير يومي بالمصاريف").performClick(); });
            main(() -> ok(!target.getSharedPreferences("reminder_settings", 0).getBoolean("daily_enabled", true), "Daily switch persists off"));
            NotificationManager manager = target.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(MonthlySummaryReceiver.CHANNEL, "QA MONTHLY", NotificationManager.IMPORTANCE_NONE));
            main(() -> control("ملخص أول الشهر").performClick());
            main(() -> { ok(MonthlySummaryReceiver.settings(target).getBoolean("enabled", false), "Monthly preference persists on"); ok(text(root(), "مفعّل بالتطبيق · موقوف من إعدادات نوع الإشعار") != null, "Blocked channel is not reported as active delivery"); });
            screen("02-alert-switches");
            main(() -> { invoke("render", new Class<?>[0]); ok(control("إشعار بكل عملية").isChecked() && !control("تذكير يومي بالمصاريف").isChecked() && control("ملخص أول الشهر").isChecked(), "Control state survives redraw"); set("selectedTab", 2); set("commitmentCalendarOpen", true); set("commitmentMonth", LocalDate.now().withDayOfMonth(1)); invoke("render", new Class<?>[0]); View day = described(root(), date + " · 2 التزامات"); ok(day != null && day.performClick(), "Actual calendar day opens commitments"); });
            ok(accessibleText("عرض التفاصيل"), "Calendar lists show a clear details action"); screen("03-calendar-day");
            ok(clickDescription(runner.getUiAutomation().getRootInActiveWindow(), "عرض تفاصيل QA CALENDAR A"), "Details action can be clicked"); idle();
            main(() -> { ok((int) get("selectedTab") == 11, "Calendar row opens commitment details"); ok(activity.hasWindowFocus(), "Day dialog is dismissed before details"); });
            ok(!accessibleText("إغلاق"), "Day overlay is absent from details"); screen("04-commitment-details");
            main(() -> { activity.onBackPressed(); ok((int) get("selectedTab") == 2, "Back returns to calendar page"); });
            ok(db.transactionCount() == 7, "Settings and navigation preserve financial rows");
            result.putString("stream", "Review/alerts/calendar UI checks passed: " + checks + "; failed: 0; dark=" + dark + "; scale=" + scale + "\nCaptures: " + captures + "\n");
        } catch (Throwable error) {
            status = 0; try { screen("failure"); } catch (Exception ignored) { }
            java.io.StringWriter trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace)); result.putString("stream", "Review/alerts/calendar UI FAILED: " + trace + "\n");
        } finally {
            try { if (activity != null) main(() -> { if (originalConfiguration != null) activity.getResources().updateConfiguration(originalConfiguration, activity.getResources().getDisplayMetrics()); activity.finish(); }); if (db != null) { db.clearAllData(); db.close(); } } catch (Throwable error) { status = 0; result.putString("stream", result.getString("stream", "") + error); }
            runner.finish(status, result);
        }
    }
}
