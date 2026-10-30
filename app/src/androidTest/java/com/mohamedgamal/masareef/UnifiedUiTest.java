package com.mohamedgamal.masareef;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native window contracts and non-sensitive form drafts; only the disposable journey QA package. */
final class UnifiedUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private int checks;
    private boolean fixture;
    private java.io.File captures;
    UnifiedUiTest(Instrumentation runner) { this.runner = runner; }
    private interface Action { void run() throws Exception; }
    private void main(Action action) {
        Throwable[] error = {null};
        runner.runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } });
        runner.waitForIdleSync();
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private void ok(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    private Object get(String name) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void set(String name, Object value) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
    private Object invoke(String name, Class<?>[] types, Object... values) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(activity, values);
    }
    private View root() { return activity.findViewById(android.R.id.content); }
    private EditText name() { return root().findViewWithTag("display_name_input"); }
    private Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            Button found=findButton(((ViewGroup)view).getChildAt(i),text); if(found!=null)return found;
        }
        return null;
    }
    private void click(String text) {
        AccessibilityNodeInfo tree=runner.getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo node:tree.findAccessibilityNodeInfosByText(text)) {
            if(!text.contentEquals(node.getText()==null?"":node.getText()))continue;
            for(AccessibilityNodeInfo at=node;at!=null;at=at.getParent())if(at.isClickable()&&at.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                runner.waitForIdleSync();return;
            }
        }
        throw new AssertionError("Missing action: "+text);
    }
    private AlertDialog.Builder builder() throws Exception {
        Class<?> type=Class.forName(MainActivity.class.getName()+"$ChoiceDialogBuilder");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(MainActivity.class);
        constructor.setAccessible(true);return (AlertDialog.Builder)constructor.newInstance(activity);
    }
    private void screen(String name) throws Exception {
        runner.waitForIdleSync();runner.getUiAutomation().waitForIdle(500,5000);
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("No screenshot");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(captures,name+".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        } finally { bitmap.recycle(); }
    }
    private void homeAmounts(View view) {
        if (view instanceof android.widget.TextView && "statement_amount".equals(view.getTag())) {
            android.widget.TextView amount = (android.widget.TextView)view;
            android.text.Layout layout = amount.getLayout();
            ok(layout != null && layout.getLineCount() == 1, "Amount stays together: " + amount.getText());
            for (int i = 0; i < layout.getLineCount(); i++) ok(layout.getEllipsisCount(i) == 0, "No ellipsis");
            float measured = android.text.Layout.getDesiredWidth(amount.getText(), amount.getPaint());
            ok(amount.getWidth() - amount.getPaddingLeft() - amount.getPaddingRight() + 1 >= measured,
                    "Whole amount measured width: " + amount.getText() + " width=" + amount.getWidth() + " text=" + measured);
            int[] xy = new int[2]; amount.getLocationOnScreen(xy);
            ok(xy[0] >= 0 && xy[0] + amount.getWidth() <= root().getWidth(), "Amount horizontally on screen");
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) homeAmounts(((ViewGroup)view).getChildAt(i));
    }
    private void addHomeFixture(ExpenseDb ledger, long when, long amount, String name, String category, String kind) {
        ok(ledger.addManualEntry(when, amount, "EGP", name, category, null, kind, null, null), "Fixture saved: " + name);
    }
    /** Runs at the real system font scale; no Resources.updateConfiguration override. */
    void runHome(boolean dark, float fontScale) {
        Context target = runner.getTargetContext(); Bundle result = new Bundle(); int status = -1;
        ExpenseDb ledger = null;
        String mode = (dark ? "dark" : "light") + (fontScale == 2 ? "-max" : fontScale > 1 ? "-large" : "");
        try {
            if (!target.getPackageName().endsWith(".journeyqa") || !"ranchu".equals(android.os.Build.HARDWARE))
                throw new SecurityException("Disposable isolated emulator required");
            ledger = AppResources.database(target); ledger.clearAllData();
            target.getSharedPreferences(MainActivity.class.getName(), 0).edit().putBoolean("dark_mode", dark)
                    .putBoolean("intro_completed", true).putString("opening_mode", "off").putString("profile_name", "هبة")
                    .putBoolean("initial_import_prompted", true).putBoolean("sms_permission_prompted", true)
                    .putInt("repair_version", ExpenseDb.REPAIR_VERSION).commit();
            target.getSharedPreferences("notification_settings", 0).edit().putBoolean("access_prompted", true).putBoolean("source_picker_shown", true).commit();
            DailySummary.remove(target, "EGP");
            long when = java.time.LocalDate.now().withDayOfMonth(1).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            addHomeFixture(ledger, when, 150000, "راتب", "دخل", "income");
            addHomeFixture(ledger, when + 3600000, 832000, "InstaPay", "دخل", "income");
            addHomeFixture(ledger, when, 4513548, "تحويل لأحمد", "تحويلات", "expense");
            addHomeFixture(ledger, when, 3100000, "ATM", "سحب نقدي", "expense");
            addHomeFixture(ledger, when, 1420934, "HSBC Market", "مشتريات", "expense");
            addHomeFixture(ledger, when, 9499, "قسط", "أقساط", "expense");
            captures = new java.io.File(target.getCacheDir(), "home-" + mode); captures.mkdirs();
            activity = (MainActivity)runner.startActivitySync(new android.content.Intent(target, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runner.waitForIdleSync();
            main(() -> {
                ok(Math.abs(activity.getResources().getConfiguration().fontScale - fontScale) < .01, "Real system font scale");
                float density = activity.getResources().getDisplayMetrics().density;
                ok(Math.abs(activity.getResources().getDisplayMetrics().widthPixels / density - 360) < 2, "360dp wide acceptance viewport");
                ok((Integer)get("homeStatementOpen") == 0, "Closed by default");
                ok(root().findViewWithTag("statement_component:مشتريات ورسوم") == null, "No outflow details when closed");
                ok(root().findViewWithTag("statement_unlinked") != null, "Unlinked transfer warning retained");
                homeAmounts(root());
                int labels = 0;
                for (String name : new String[]{"الرئيسية", "العمليات", "الالتزامات", "التحليلات"}) {
                    android.widget.TextView caption = root().findViewWithTag("nav_label:" + name);
                    ok(caption != null, "Tab label present: " + name); labels++;
                    View item = (View)caption.getParent();
                    ok(item.getContentDescription() != null && item.getContentDescription().toString().startsWith(name), "Tab keeps its full description: " + name);
                    ok(name.contentEquals(item.getTooltipText()), "Tab keeps its tooltip: " + name);
                    ok(caption.getVisibility() != View.VISIBLE || caption.getLayout() != null && caption.getLayout().getLineCount() == 1,
                            "Tab label on one line, never split inside a word: " + name);
                }
                ok(labels == 4, "Four labelled tabs");
                View chip = root().findViewWithTag("statement_period");
                ok(chip.getHeight() >= Math.round(UiTheme.TOUCH * density), "Period chip keeps 48dp touch target");
                android.graphics.drawable.LayerDrawable chipPill = (android.graphics.drawable.LayerDrawable)chip.getBackground();
                chipPill.setBounds(0, 0, chip.getWidth(), chip.getHeight());
                ok(Math.abs(chipPill.getDrawable(0).getBounds().height() - Math.round(34 * density)) <= 1, "Period chip drawn 34dp tall");
                ViewGroup unlinked = root().findViewWithTag("statement_unlinked");
                ok(unlinked.getParent() == root().findViewWithTag("home_statement"), "Unlinked transfer is a row of the statement card");
                ok(unlinked.hasOnClickListeners() && unlinked.getHeight() >= Math.round(UiTheme.TOUCH * density), "Unlinked row stays clickable with 48dp target");
                for (int i = 0; i < unlinked.getChildCount(); i++) if (unlinked.getChildAt(i) instanceof android.widget.TextView) {
                    android.widget.TextView text = (android.widget.TextView)unlinked.getChildAt(i);
                    ok((text.getPaintFlags() & android.graphics.Paint.UNDERLINE_TEXT_FLAG) == 0, "Unlinked row text not underlined");
                    ok(text.getCurrentTextColor() != (Integer)get("CORAL"), "Unlinked row text is not red");
                }
            });
            screen("10-home-closed");
            main(() -> root().findViewWithTag("statement_out").performClick());
            main(() -> {
                ok((Integer)get("homeStatementOpen") == 2, "Outflow opened");
                ok(root().findViewWithTag("statement_component:مشتريات ورسوم") != null, "Outflow components visible");
                ok(root().findViewWithTag("statement_component:راتب") == null, "Income stays closed"); homeAmounts(root());
                componentRows(root(), fontScale);
                android.view.accessibility.AccessibilityNodeInfo info = root().findViewWithTag("statement_out").createAccessibilityNodeInfo();
                ok(info.getActionList().contains(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE), "TalkBack expanded action");
            });
            screen("10-home-out");
            main(() -> root().findViewWithTag("statement_income").performClick());
            main(() -> {
                ok((Integer)get("homeStatementOpen") == 1, "Income opened");
                ok(root().findViewWithTag("statement_component:مشتريات ورسوم") == null, "Opening income closed outflow");
                ok(root().findViewWithTag("statement_component:راتب") != null, "Salary visible"); homeAmounts(root());
                componentRows(root(), fontScale);
                ok(stripText(root().findViewWithTag("statement_component:تحويلات وصلتك")).contains("تحويل واحد"), "Transfer count in Arabic singular");
            });
            screen("10-home-in");
            // Component drill-down matches statement contributions, including an English bank name.
            final ExpenseDb data = ledger;
            for (String component : new String[]{"راتب", "تحويلات وصلتك", "مشتريات ورسوم", "كاش اتسحب", "أقساط والتزامات", "تحويلات لناس"}) {
                main(() -> {
                    set("selectedTab", 0); set("homeStatementOpen", component.equals("راتب") || component.equals("تحويلات وصلتك") ? 1 : 2);
                    invoke("render", new Class<?>[0]); root().findViewWithTag("statement_component:" + component).performClick();
                    java.time.LocalDate[] period = (java.time.LocalDate[])get("transactionPeriod");
                    long listed = 0;
                    for (ExpenseDb.Transaction tx : data.transactionsBetween(period[0], period[1]))
                        if ((Boolean)invoke("matchesTransactionFilters", new Class<?>[]{ExpenseDb.Transaction.class, String.class}, tx, "")) listed += tx.amountMinor;
                    long expected = component.equals("راتب") ? 150000 : component.equals("تحويلات وصلتك") ? 832000
                            : component.equals("مشتريات ورسوم") ? 1420934 : component.equals("كاش اتسحب") ? 3100000 : component.equals("أقساط والتزامات") ? 9499 : 4513548;
                    ok(listed == expected, "Exact rows behind " + component + ": " + listed);
                });
            }
            // flowItems offsets both sides of a reimbursement. Drill-down must show those exact contributions.
            long purchaseWhen = when + 7200000;
            addHomeFixture(data, purchaseWhen, 100000, "QA partial purchase", "مشتريات", "expense");
            long partialPurchase = data.latestInserted().id;
            addHomeFixture(data, purchaseWhen + 1000, 30000, "QA refund", "دخل", "income");
            long refund = data.latestInserted().id;
            ok(data.linkReimbursement(refund, partialPurchase) == 30000, "Partial refund linked");
            addHomeFixture(data, purchaseWhen + 2000, 20000, "QA full purchase", "مشتريات", "expense");
            long fullPurchase = data.latestInserted().id;
            addHomeFixture(data, purchaseWhen + 3000, 50000, "QA mixed refund", "دخل", "income");
            long mixedRefund = data.latestInserted().id;
            ok(data.linkReimbursement(mixedRefund, fullPurchase) == 20000, "Full purchase offset; income retains a contribution");
            for (String filter : new String[]{MoneyFlow.Bucket.LIVING.name(), MoneyFlow.Bucket.INFLOW.name()}) {
                main(() -> invoke("openStatement", new Class<?>[]{String.class, boolean.class}, filter, false));
                main(() -> {
                    java.time.LocalDate[] period = (java.time.LocalDate[])get("transactionPeriod");
                    java.util.Map<Long, Long> offsets = data.reimbursementOffsets(); long listed = 0;
                    MoneyFlow.Statement expected = MoneyFlow.statement(data.flowItems(period[0], period[1], java.time.ZoneId.systemDefault()), period, java.time.ZoneId.systemDefault(), "EGP");
                    for (ExpenseDb.Transaction tx : data.transactionsBetween(period[0], period[1])) {
                        if (!(Boolean)invoke("matchesTransactionFilters", new Class<?>[]{ExpenseDb.Transaction.class, String.class}, tx, "")) continue;
                        long contribution = ExpenseDb.effectiveAmount(tx, offsets); listed += contribution;
                        android.widget.TextView amount = root().findViewWithTag("statement_transaction_amount:" + tx.id);
                        ok(amount != null && amount.getText().toString().equals(("income".equals(tx.kind) ? "+" : "−") + MainActivity.money(contribution, "EGP")), "Displayed row is its statement contribution");
                    }
                    ok(listed == (filter.equals("LIVING") ? expected.living : expected.income), "Refund-adjusted drill-down equals statement");
                    ok(root().findViewWithTag("statement_transaction_amount:" + fullPurchase) == null, "Fully offset expense absent");
                    ok(root().findViewWithTag("statement_transaction_amount:" + refund) == null, "Fully linked refund absent");
                });
            }
            main(() -> { set("selectedTab", 0); set("homeStatementOpen", 0); invoke("render", new Class<?>[0]); });
            main(() -> { android.widget.ScrollView scroll = (android.widget.ScrollView)get("pageScroll"); scroll.scrollTo(0, ((LinearLayout)get("page")).getHeight()); });
            main(() -> {
                LinearLayout page = (LinearLayout)get("page"); android.widget.ScrollView scroll = (android.widget.ScrollView)get("pageScroll");
                android.graphics.Rect bounds = new android.graphics.Rect(); View last = page.getChildAt(page.getChildCount() - 1);
                if (last instanceof ViewGroup && ((ViewGroup)last).getChildCount() > 0) last = ((ViewGroup)last).getChildAt(((ViewGroup)last).getChildCount() - 1);
                ok(last.getGlobalVisibleRect(bounds) && bounds.height() >= last.getHeight() - 1, "Last home row fully scrolls above navigation: visible=" + bounds + " height=" + last.getHeight() + " scrollY=" + scroll.getScrollY());
                ok(page.getPaddingBottom() >= Math.round(76 * activity.getResources().getDisplayMetrics().density), "Navigation and inset clearance");
            });
            // Render all Play states without pretending this is a real Play delivery.
            for (UpdatePrompt.Card state : new UpdatePrompt.Card[]{UpdatePrompt.Card.OFFER, UpdatePrompt.Card.DOWNLOADING, UpdatePrompt.Card.RESTART}) {
                main(() -> {
                    LinearLayout page = (LinearLayout)get("page"); page.removeAllViews();
                    ((android.widget.ScrollView)get("pageScroll")).scrollTo(0, 0);
                    invoke("updateStrip", new Class<?>[]{UpdatePrompt.Card.class, long.class, long.class}, state, 62L, 100L);
                });
                main(() -> {
                    View strip = root().findViewWithTag("in_app_update_card"); android.graphics.Rect visible = new android.graphics.Rect();
                    ok(strip.getGlobalVisibleRect(visible) && visible.width() == strip.getWidth() && visible.height() == strip.getHeight(), "Entire update strip inside screen: " + state);
                    checkStripViews(strip);
                    float density = activity.getResources().getDisplayMetrics().density;
                    View icon = strip.findViewWithTag("update_icon");
                    ok(icon.getWidth() == Math.round(32 * density) && icon.getHeight() == Math.round(32 * density), "Update icon stays a 32dp circle: " + icon.getWidth() + "x" + icon.getHeight());
                    View action = strip.findViewWithTag("update_action");
                    if (state != UpdatePrompt.Card.DOWNLOADING) {
                        ok(action != null && action.getHeight() >= Math.round(UiTheme.TOUCH * density), "Update action keeps 48dp touch target");
                        android.graphics.drawable.LayerDrawable background = (android.graphics.drawable.LayerDrawable)action.getBackground();
                        background.setBounds(0, 0, action.getWidth(), action.getHeight()); android.graphics.Rect pill = background.getDrawable(0).getBounds();
                        ok(Math.abs(pill.height() - Math.round(36 * density)) <= 1 && pill.width() == action.getWidth(), "Update pill drawn 36dp tall: " + pill);
                    } else ok(action == null, "No action while downloading");
                    ok(!stripText(strip).contains("إصدار"), "No version code in the update strip");
                });
                screen("11-update-" + state.name().toLowerCase(java.util.Locale.ROOT));
            }
            main(() -> {
                LinearLayout page = (LinearLayout)get("page"); page.removeAllViews();
                android.widget.TextView amount = (android.widget.TextView)invoke("statementAmount", new Class<?>[]{long.class, int.class, String.class}, 123456789L, android.graphics.Color.BLACK, "");
                page.addView(amount, new LinearLayout.LayoutParams(-2, -2));
            });
            main(() -> homeAmounts(root()));
            result.putString("stream", "Home statement checks passed: " + checks + "; failed: 0; mode=" + mode + "; fontScale=" + fontScale + "\nCaptures: " + captures + "\n");
        } catch (Throwable error) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Home statement FAILED: " + trace);
        } finally {
            try { if (ledger != null) ledger.clearAllData(); } catch (Throwable ignored) {}
            try { if (activity != null) main(() -> activity.finish()); } catch (Throwable ignored) {}
            runner.finish(status, result);
        }
    }
    /** Each sub-row: name, then detail and share bar on one line (until a large font wraps it), and the app chevron. */
    private void componentRows(View view, float fontScale) {
        if (view.getTag() instanceof String && ((String)view.getTag()).startsWith("statement_component:")) {
            ViewGroup row = (ViewGroup)view;
            ok(row.findViewWithTag("statement_chevron") instanceof android.widget.ImageView, "Sub-row uses the app chevron icon");
            ok(!stripText(row).contains("›") && !stripText(row).contains("‹"), "No text chevron glyph");
            ViewGroup copy = (ViewGroup)row.getChildAt(0);
            ok(copy.getChildCount() == 2, "Sub-row has two lines: " + view.getTag());
            ViewGroup second = row.findViewWithTag("statement_component_detail");
            if (fontScale <= 1f) {
                View detail = second.getChildAt(0), bar = second.getChildAt(1);
                if (detail.getVisibility() == View.VISIBLE)
                    ok(detail.getTop() < bar.getBottom() && bar.getTop() < detail.getBottom(), "Detail and share bar on one line: " + view.getTag());
                // At normal size the amount never drops below its label; stacking is for large fonts only.
                View trailing = row.getChildAt(1);
                ok(trailing.getTop() < copy.getBottom() && copy.getTop() < trailing.getBottom(), "Amount on the same line as its label at 1.0: " + view.getTag());
                ok(bar.getWidth() >= Math.round(ShareLine.MIN_BAR_DP * activity.getResources().getDisplayMetrics().density) - 1, "Share bar keeps its 48dp minimum: " + view.getTag());
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) componentRows(((ViewGroup)view).getChildAt(i), fontScale);
    }
    private String stripText(View view) {
        if (view instanceof android.widget.TextView) return ((android.widget.TextView)view).getText().toString();
        StringBuilder text = new StringBuilder();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) text.append(stripText(((ViewGroup)view).getChildAt(i))).append(' ');
        return text.toString();
    }
    private void checkStripViews(View view) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView)view;
            ok(text.getLayout() != null && text.getLayout().getHeight() <= text.getHeight(), "Strip text has full height");
            if (text.isClickable()) ok(text.getHeight() >= Math.round(UiTheme.TOUCH * activity.getResources().getDisplayMetrics().density), "Strip action 48dp touch target");
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) checkStripViews(((ViewGroup)view).getChildAt(i));
    }
    void run(boolean dark, float fontScale) {
        Context target=runner.getTargetContext();Bundle result=new Bundle();int status=-1;
        try {
            if(!target.getPackageName().endsWith(".journeyqa")||!"ranchu".equals(android.os.Build.HARDWARE))
                throw new SecurityException("Disposable isolated emulator required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark)
                    .putBoolean("intro_completed",true).putString("opening_mode","off").putString("profile_name","QA original")
                    .putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true)
                    .putBoolean("source_picker_shown",true).commit();
            captures=new java.io.File(target.getCacheDir(),"unified-"+(dark?"dark":"light")+(fontScale>1?"-max":""));captures.mkdirs();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));runner.waitForIdleSync();
            main(()->{
                if(fontScale>1){android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
                    config.fontScale=fontScale;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());}
                set("selectedTab",5);set("settingsSection","appearance");invoke("render",new Class<?>[0]);
                invoke("displayNamePage",new Class<?>[0]); name().setText("QA draft");
                ok(((UiPages)get("formPages")).isOpen(),"Form must be a page");
                ok(!(Boolean)invoke("canRedrawNow",new Class<?>[0]),"Open page blocks ledger redraw even with no typing focus");
                invoke("render",new Class<?>[0]);ok("QA draft".contentEquals(name().getText()),"Render kept draft");
                ok(name().getId()!=View.NO_ID,"Field has stable-in-view label target");
                // AppLock covers the Activity with a replacement content view before calling onAppUnlocked.
                activity.setContentView(new android.widget.FrameLayout(activity));
                invoke("onAppUnlocked",new Class<?>[0]);
                ok("QA draft".contentEquals(name().getText()),"Unlock rebuilt the form from its draft model");
            });
            screen("01-form-draft");
            main(()->activity.onNewIntent(new android.content.Intent(target,MainActivity.class).putExtra("open_transactions",true)));
            click("كمّل التعديل");
            main(()->{
                ok("QA draft".contentEquals(name().getText()),"Declined incoming navigation kept draft");
                ok(!activity.getIntent().getBooleanExtra("open_transactions",false),"Declined intent cannot replay after recreation");
            });
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());
            MainActivity recreated=(MainActivity)runner.waitForMonitorWithTimeout(monitor,15000);runner.removeMonitor(monitor);
            ok(recreated!=null,"Activity recreated");activity=recreated;runner.waitForIdleSync();
            main(()->{
                ok(name()!=null&&"QA draft".contentEquals(name().getText()),"Draft survived recreation");
                activity.onBackPressed();
            });
            click("كمّل التعديل");
            main(()->ok("QA draft".contentEquals(name().getText()),"Cancel discard kept draft"));
            main(()->activity.onBackPressed());click("تجاهل التعديلات");
            main(()->{
                ok(!((UiPages)get("formPages")).isOpen(),"Discard returned to parent");
                ok((int)get("selectedTab")==5&&"".equals(get("settingsSection")),"Correct parent page");
                ok("QA original".equals(target.getSharedPreferences(MainActivity.class.getName(),0).getString("profile_name","")),"Discard never saved");
                invoke("displayNamePage",new Class<?>[0]);name().setText("QA saved");findButton(root(),"حفظ").performClick();
                ok("QA saved".equals(target.getSharedPreferences(MainActivity.class.getName(),0).getString("profile_name","")),"Save committed draft");
                invoke("displayNamePage",new Class<?>[0]);name().setText("temporary");name().setText("QA saved");activity.onBackPressed();
                ok(!((UiPages)get("formPages")).isOpen(),"Reverted edits are clean");
                UiPages pages=(UiPages)get("formPages");
                UiPages.Page completed=new UiPages.Page("async_old","QA old",new Bundle(),new Bundle()){
                    void build(LinearLayout body,UiComponents ui){} boolean save(){return false;}
                };
                UiPages.Page next=new UiPages.Page("async_next","QA next",new Bundle(),new Bundle()){
                    void build(LinearLayout body,UiComponents ui){} boolean save(){return false;}
                };
                pages.push(completed);pages.back();pages.push(next);pages.completeSave(completed);
                ok(pages.isOpen()&&root().findViewWithTag("ui_form_page:async_next")!=null,"Late save completion cannot close another page");
                pages.completeSave(next);ok(!pages.isOpen(),"Successful async save closes its own page");
            });
            final AlertDialog[] dialog={null};final int[] saved={0};final EditText[] field={null};
            main(()->{
                LinearLayout form=(LinearLayout)invoke("form",new Class<?>[0]);
                field[0]=(EditText)invoke("input",new Class<?>[]{String.class,LinearLayout.class},"QA required",form);
                dialog[0]=builder().setTitle("QA validation").setView(form).setPositiveButton("حفظ",null)
                        .setNegativeButton("إلغاء",null).setNeutralButton("QA neutral",null).create();
                dialog[0].setOnShowListener(d->dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(field[0].getText().length()==0){field[0].setError("QA required");return;}
                    saved[0]++;dialog[0].dismiss();
                }));dialog[0].show();
            });
            main(()->{
                ok((dialog[0].getWindow().getAttributes().gravity&android.view.Gravity.BOTTOM)==android.view.Gravity.BOTTOM,"Sheet at bottom");
                dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                ok(dialog[0].isShowing()&&saved[0]==0,"Invalid submit remained open");
                ok(dialog[0].getButton(AlertDialog.BUTTON_NEUTRAL)!=null,"Neutral button preserved");
            });
            // Accessibility nodes alone can remain actionable even when the framework button bar clips them.
            runner.getUiAutomation().waitForIdle(300,3000);
            main(()->{
                android.graphics.Rect visible=new android.graphics.Rect();
                for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE}){
                    Button button=dialog[0].getButton(which);
                    ok(button.getGlobalVisibleRect(visible)&&visible.height()>=button.getHeight()-1,"Every action fully visible: "+button.getText());
                }
            });
            screen("02-validation-sheet");
            main(()->{field[0].setText("valid");dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                ok(saved[0]==1&&!dialog[0].isShowing(),"Valid submit ran once");});
            final int[] deletions={0};
            main(()->invoke("confirmDelete",new Class<?>[]{String.class,Runnable.class},"QA item",(Runnable)()->deletions[0]++));
            screen("03-delete-confirm");click("إلغاء");ok(deletions[0]==0,"Cancel delete made no change");
            main(()->invoke("confirmDelete",new Class<?>[]{String.class,Runnable.class},"QA item",(Runnable)()->deletions[0]++));
            click("حذف");ok(deletions[0]==1,"Explicit confirm deleted exactly once");
            main(()->{
                LinearLayout form=(LinearLayout)invoke("form",new Class<?>[0]);
                EditText date=(EditText)invoke("dateInput",new Class<?>[]{String.class,LinearLayout.class,java.time.LocalDate.class},"QA date",form,java.time.LocalDate.now());
                ok(date.isFocusable(),"Date remains keyboard focusable");
                dialog[0]=builder().setTitle("QA date form").setView(form).setNegativeButton("إلغاء",null).show();
                date.requestFocus();date.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_SPACE));
                date.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_SPACE));
            });
            screen("04-date-sheet");runner.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            runner.waitForIdleSync();main(()->{ok(dialog[0].isShowing(),"Closing date picker kept parent form");dialog[0].dismiss();});
            main(()->{
                set("selectedTab",5);set("settingsSection","");invoke("render",new Class<?>[0]);
                EditText search=root().findViewWithTag("settings_search");ok(search!=null,"Settings home has search");
                search.setText("نسخه");View hit=root().findViewWithTag("settings_result:نسخة احتياطية");
                ok(hit!=null,"Search folds taa marbuta and finds backup");
                search.setText("app lock");ok(root().findViewWithTag("settings_result:قفل التطبيق")!=null,"English keyword finds app lock");
                search.setText("zzzz");ok(root().findViewWithTag("settings_result:قفل التطبيق")==null,"Non-matching query hides results");
                search.setText("قفل");root().findViewWithTag("settings_result:قفل التطبيق").performClick();
            });
            runner.getUiAutomation().waitForIdle(300,3000);
            main(()->{
                ok("".equals(get("settingsSection")),"Search hit stays on the settings page");
                ok(root().findViewWithTag("setting:قفل التطبيق")!=null,"App lock is a first-level row");
                ((EditText)root().findViewWithTag("settings_search")).setText("csv");root().findViewWithTag("settings_result:استيراد معاملات CSV").performClick();
            });
            runner.getUiAutomation().waitForIdle(300,3000);
            main(()->{
                ok((Boolean)get("settingsAdvanced"),"A hit in the advanced tools opens them");
                ok(root().findViewWithTag("setting:استيراد معاملات CSV")!=null,"Advanced row is shown");
                ok(!((UiPages)get("formPages")).isOpen(),"Search hit never runs an action");
            });
            screen("05-settings-search-hit");
            ExpenseDb ledger=(ExpenseDb)get("db");fixture=true;
            long account=ledger.addPaymentSource("QA BALANCE","حساب","3333");
            main(()->{
                invoke("manualBalanceDialog",new Class<?>[]{ExpenseDb.PaymentSource.class},ledger.paymentSource(account));
                ok(((UiPages)get("formPages")).isOpen(),"Manual balance is a page");
                findButton(root(),"حفظ").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&root().findViewWithTag("manual_balance_error").getVisibility()==View.VISIBLE,"Empty balance stays open with an inline error");
                ((EditText)root().findViewWithTag("manual_balance_amount")).setText("1500");
            });
            screen("06-manual-balance");
            main(()->{
                findButton(root(),"حفظ").performClick();
                ok(!((UiPages)get("formPages")).isOpen(),"Valid balance returns to parent");
                ExpenseDb.BalanceSnapshot balance=ledger.currentBalance(account,"EGP");
                ok(balance!=null&&balance.amountMinor==150000,"Balance saved in minor units");
                invoke("debtDialog",new Class<?>[0]);findButton(root(),"حفظ").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&ledger.debts().isEmpty(),"Incomplete debt not saved");
                ((EditText)root().findViewWithTag("debt_amount")).setText("200");((EditText)root().findViewWithTag("debt_name")).setText("QA debt");
            });
            screen("07-debt-page");
            main(()->{
                findButton(root(),"حفظ").performClick();
                ok(ledger.debts().size()==1&&ledger.debts().get(0).remainingMinor==20000,"Debt saved once with its amount");
            });
            main(()->{
                invoke("budgetPlanDialog",new Class<?>[]{ExpenseDb.BudgetPlan.class},(Object)null);
                ok(((UiPages)get("formPages")).isOpen(),"Budget is a page");
                ok(root().findViewWithTag("budget_days").getVisibility()==View.GONE,"Monthly budget hides the day count");
                ((EditText)root().findViewWithTag("budget_amount")).setText("3000");((EditText)root().findViewWithTag("budget_name")).setText("QA budget");
                findButton(root(),"حفظ").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&ledger.budgetPlans().isEmpty()&&root().findViewWithTag("budget_error").getVisibility()==View.VISIBLE,"Budget without categories stays open with an inline error");
                root().findViewWithTag("budget_categories").performClick();
            });
            click("بقالة");click("تم");
            screen("08-budget-page");
            main(()->{
                findButton(root(),"حفظ").performClick();
                ok(!((UiPages)get("formPages")).isOpen(),"Valid budget returns to parent");
                ok(ledger.budgetPlans().size()==1&&ledger.budgetPlans().get(0).amountMinor==300000
                        &&ledger.budgetPlans().get(0).categories.contains("بقالة"),"Budget saved with its amount and category");
                invoke("backupDialog",new Class<?>[0]);
                ((EditText)root().findViewWithTag("backup_password")).setText("short");((EditText)root().findViewWithTag("backup_confirm")).setText("short");
                findButton(root(),"اختار مكان الحفظ").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&root().findViewWithTag("backup_error").getVisibility()==View.VISIBLE,"Short backup password stays on the page");
                ok(((UiPages)get("formPages")).snapshot().getParcelableArrayList("pages").isEmpty(),"Password page is never saved for recreation");
            });
            screen("09-backup-page");
            main(()->activity.onBackPressed());click("تجاهل التعديلات");
            main(()->ok(!((UiPages)get("formPages")).isOpen(),"Typed password asks before leaving, then discards"));
            main(()->{
                invoke("splitBillDialog",new Class<?>[0]);
                ((EditText)root().findViewWithTag("split_bill_total")).setText("300");((EditText)root().findViewWithTag("split_bill_merchant")).setText("QA dinner");
                ((EditText)root().findViewWithTag("split_bill_people")).setText("1");findButton(root(),"سجل حصتي").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&ledger.transactionCount()==0,"Split bill for one person is refused on the page");
                ((EditText)root().findViewWithTag("split_bill_people")).setText("3");findButton(root(),"سجل حصتي").performClick();
                ok(!((UiPages)get("formPages")).isOpen()&&ledger.transactionCount()==1,"Valid split records one share");
                ok(ledger.latestInserted().amountMinor==10000,"Share is the total divided by people");
                ExpenseDb.Transaction share=ledger.latestInserted();
                invoke("reportTransactionReading",new Class<?>[]{ExpenseDb.Transaction.class},share);
                ok(((UiPages)get("formPages")).isOpen()&&findButton(root(),"فتح مسودة إيميل")!=null,"Misread report is a page with its mail action");
                ok(((UiPages)get("formPages")).snapshot().getParcelableArrayList("pages").isEmpty(),"Message text page is never saved for recreation");
                activity.onBackPressed();ok(!((UiPages)get("formPages")).isOpen(),"Untouched report page closes on Back");
                invoke("commitmentFromTransaction",new Class<?>[]{ExpenseDb.Transaction.class},share);
                ok(((UiPages)get("formPages")).isOpen(),"Commitment from a transaction is a page");
                ((EditText)root().findViewWithTag("commitment_from_transaction_name")).setText("");findButton(root(),"حفظ الالتزام").performClick();
                ok(((UiPages)get("formPages")).isOpen()&&root().findViewWithTag("commitment_from_transaction_error").getVisibility()==View.VISIBLE,"Empty commitment name stays on the page");
                ok(ledger.transactionCount()==1,"Creating a commitment never adds a transaction");
                activity.onBackPressed();
            });
            click("تجاهل التعديلات");
            main(()->invoke("openCommitmentEditor",new Class<?>[]{ExpenseDb.Recurring.class},(Object)null));
            main(()->{
                // 0.4.1 regression: field captions filled their horizontal row, so these inputs and the currency
                // picker had zero width and an installment could not be saved. Typing by tag never noticed.
                int width=root().getWidth();
                for(String tag:new String[]{"commitment_amount","commitment_total","commitment_paid"}){
                    View input=root().findViewWithTag(tag);int[] at=new int[2];
                    ok(input!=null&&input.isShown(),"Commitment field is shown: "+tag);
                    input.getLocationInWindow(at);
                    ok(input.getWidth()>=width/4&&at[0]>=0&&at[0]+input.getWidth()<=width,"Commitment field is on screen and wide enough to type in: "+tag);
                }
                View currency=((android.view.ViewGroup)((View)root().findViewWithTag("commitment_amount").getParent()).getParent()).getChildAt(1);
                int[] at=new int[2];currency.getLocationInWindow(at);
                ok(currency instanceof android.widget.Spinner&&currency.getWidth()>0&&at[0]>=0&&at[0]+currency.getWidth()<=width,"Currency picker sits beside the amount");
            });
            screen("09b-commitment-editor");
            main(()->{
                set("selectedTab",0);invoke("render",new Class<?>[0]);
                invoke("dailyBudgetDialog",new Class<?>[0]);
                ((EditText)root().findViewWithTag("daily_budget_amount")).setText("250");findButton(root(),"حفظ").performClick();
                ok(!((UiPages)get("formPages")).isOpen(),"Daily budget saves and returns");
                DailyAllowance.Plan plan=DailySummary.plan(activity,"EGP");ok(plan!=null&&plan.limit==25000,"Daily limit saved in minor units");
                invoke("dailyBudgetDialog",new Class<?>[0]);
                ok(root().findViewWithTag("daily_budget_remove")!=null,"Existing plan offers removal");
            });
            screen("10-daily-budget");
            click("حذف الحد اليومي");click("حذف");
            main(()->{ok(DailySummary.plan(activity,"EGP")==null&&!((UiPages)get("formPages")).isOpen(),"Confirmed removal clears only the limit and leaves the page");});
            result.putString("stream","Unified UI checks passed: "+checks+"; failed: 0; dark="+dark+"; fontScale="+fontScale+"\nCaptures: "+captures+"\n");
        } catch(Throwable error) {
            status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","Unified UI FAILED: "+trace);
        } finally {
            try{if(fixture)((ExpenseDb)get("db")).clearAllData();}catch(Throwable ignored){}
            try{DailySummary.remove(runner.getTargetContext(),"EGP");}catch(Throwable ignored){}
            try{if(activity!=null)main(()->activity.finish());}catch(Throwable ignored){}
            runner.finish(status,result);
        }
    }
}
