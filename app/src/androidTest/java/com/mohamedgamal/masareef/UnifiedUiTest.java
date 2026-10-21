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
                ok("appearance".equals(get("settingsSection")),"Correct parent section");
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
                search.setText("قفل");root().findViewWithTag("settings_result:القفل بعد").performClick();
            });
            runner.getUiAutomation().waitForIdle(300,3000);
            main(()->{
                ok("data".equals(get("settingsSection")),"Search hit opens owning section");
                ok(root().findViewWithTag("setting:القفل بعد: فورًا")!=null||root().findViewWithTag("setting:القفل بعد: دقيقة")!=null
                        ||root().findViewWithTag("setting:القفل بعد: 5 دقائق")!=null,"Lock timeout is a first-level row");
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
