package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;

/** Real add/edit/filter navigation and ledger outcomes, using only a disposable emulator ledger. */
final class TransactionPagesUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private java.io.File captures;
    private int checks;
    TransactionPagesUiTest(Instrumentation runner){this.runner=runner;}
    private interface Action{void run()throws Exception;}
    private void main(Action action){
        Throwable[] error={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});
        runner.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private void idle()throws Exception{runner.waitForIdleSync();runner.getUiAutomation().waitForIdle(500,5000);}
    private void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private Object get(String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    private void set(String key,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private View root(){return activity.findViewById(android.R.id.content);}
    private EditText field(String key){return root().findViewWithTag("transaction_"+key);}
    private void fill(String key,String text){main(()->field(key).setText(text));}
    private void submit(){main(()->root().findViewWithTag("ui_page_save").performClick());}
    private boolean open()throws Exception{return ((UiPages)get("formPages")).isOpen();}
    private View byText(View node,String text){
        if(node instanceof TextView&&text.contentEquals(((TextView)node).getText()))return node;
        if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){View found=byText(((ViewGroup)node).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private void pageClick(String text){main(()->{View view=byText(root(),text);if(view==null)throw new AssertionError("Missing page choice "+text);view.performClick();});}
    private void click(String text)throws Exception{
        idle();AccessibilityNodeInfo tree=runner.getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo node:tree.findAccessibilityNodeInfosByText(text)){
            if(node.getText()==null||!text.contentEquals(node.getText()))continue;
            for(AccessibilityNodeInfo at=node;at!=null;at=at.getParent())if(at.isClickable()){
                at.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());idle();at.refresh();
                if(at.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            }
        }
        throw new AssertionError("Missing window action "+text);
    }
    private ScrollView scroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ScrollView found=scroll(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private void screen(String name)throws Exception{
        idle();android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(captures,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private void recreate()throws Exception{
        Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);
        main(()->activity.recreate());MainActivity next=(MainActivity)runner.waitForMonitorWithTimeout(monitor,15000);runner.removeMonitor(monitor);
        ok(next!=null,"Actual Activity recreation");activity=next;idle();
    }
    private void back(){main(()->activity.onBackPressed());}
    private void discard()throws Exception{back();click("تجاهل التعديلات");}
    private void awaitLayout()throws Exception{
        for(int i=0;i<100;i++){
            boolean[] ready={false};main(()->ready[0]=field("amount")!=null&&field("amount").isAttachedToWindow()&&field("amount").isLaidOut());
            if(ready[0]){idle();return;}Thread.sleep(100);
        }
        throw new AssertionError("Transaction form did not attach and lay out");
    }
    void run(boolean dark,float scale){
        Context target=runner.getTargetContext();Bundle result=new Bundle();int status=-1;
        try{
            if(!target.getPackageName().endsWith(".journeyqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Disposable isolated emulator required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0)throw new IllegalStateException("Empty QA ledger required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark).putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("sms_permission_prompted",true).putBoolean("initial_import_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).putBoolean("source_picker_shown",true).commit();
            captures=new java.io.File(target.getCacheDir(),"transactions-"+(dark?"dark":"light")+(scale>1?"-max":""));captures.mkdirs();
            long from=db.addPaymentSource("HSBC","حساب","1111"),to=db.addPaymentSource("Banque Misr","حساب","2222");
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            main(()->{set("selectedTab",6);set("transactionSearch","");invoke("render",new Class<?>[0]);invoke("manualDialog",new Class<?>[0]);});
            awaitLayout();
            ok(open(),"Manual entry opens as a page");ok(root().findViewWithTag("transaction_destination").getVisibility()==View.GONE,"Expense has no destination");
            main(()->{
                AccessibilityNodeInfo node=byText(root(),"مصروف").createAccessibilityNodeInfo();
                ok(node.isCheckable()&&node.isChecked(),"Segment exposes native checked state");
                ok(field("amount").getHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density),"Amount target is at least 48dp");
            });
            submit();ok(open()&&db.transactionCount()==0,"Invalid form keeps draft, no ledger write");
            main(()->ok(field("amount").getError()!=null,"Amount error identified on its input"));
            fill("amount","1.500");fill("merchant","QA groceries");submit();ok(db.transactionCount()==0,"Ambiguous amount refused");
            fill("amount","250.75");fill("note","QA draft note");
            main(()->{View transfer=byText(root(),"تحويل");transfer.requestFocus();
                transfer.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_CENTER));
                transfer.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_DPAD_CENTER));});
            ok(root().findViewWithTag("transaction_destination").getVisibility()==View.VISIBLE,"Transfer shows destination");
            submit();ok(open()&&db.transactionCount()==0,"Unassigned transfer rejected");
            main(()->root().findViewWithTag("transaction_from").performClick());click(db.sourceLabel(from)+" · يحتاج مراجعة");
            main(()->root().findViewWithTag("transaction_to").performClick());click(db.sourceLabel(from)+" · يحتاج مراجعة");
            submit();ok(open()&&db.transactionCount()==0,"Same account transfer rejected");
            main(()->root().findViewWithTag("transaction_to").performClick());click(db.sourceLabel(to)+" · يحتاج مراجعة");
            pageClick("مصروف");pageClick("بقالة");
            main(()->{scroll(root()).scrollTo(0,0);field("amount").clearFocus();});screen("01-add-top");
            main(()->scroll(root()).fullScroll(View.FOCUS_DOWN));screen("02-add-details");
            recreate();
            main(()->{ok("250.75".contentEquals(field("amount").getText()),"Amount survived recreation");ok("QA draft note".contentEquals(field("note").getText()),"Note survived recreation");});
            back();click("كمّل التعديل");ok(open()&&db.transactionCount()==0,"Cancel dirty back kept all draft values");
            submit();ok(!open()&&db.transactionCount()==1,"Manual expense saved once");
            ExpenseDb.Transaction expense=db.latestInserted();long id=expense.id;
            ok(expense.amountMinor==25075&&"بقالة".equals(expense.category),"Category and exact minor amount saved");
            ok(expense.paymentSourceId==from&&expense.destinationSourceId==null,"Hidden destination never persists on an expense");
            ok("QA draft note".equals(expense.note),"Draft note saved");
            main(()->invoke("manualDialog",new Class<?>[]{String.class},"دفعت 45 جنيه من QA Voice"));
            main(()->{ok("45".contentEquals(field("amount").getText()),"Voice amount remains reviewable");ok(field("note").getText().toString().contains("مراجعة نص الصوت"),"Voice review evidence retained");});
            fill("merchant","discarded");discard();ok(db.transactionCount()==1,"Discard voice form did not save");
            // Date-only source precision must remain date-only even after changing the day and recreating.
            long originalAt=LocalDate.now().minusDays(2).atTime(13,27,41).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            db.getWritableDatabase().execSQL("UPDATE transactions SET occurred_at=?,time_precision='date_only' WHERE id=?",new Object[]{originalAt,id});
            main(()->{set("selectedTab",10);set("selectedTransaction",db.transactionById(id));set("transactionReturnTab",6);invoke("render",new Class<?>[0]);invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(id));});
            main(()->ok(byText(root(),"تاريخ فقط · المصدر لا يحدد وقت العملية")!=null,"No synthetic time displayed for date-only source"));
            pageClick("كل التصنيفات");click("صحة");fill("amount","260.25");pageClick("افتكر التصنيف للتاجر ده في العمليات الجديدة");
            main(()->{
                EditText date=findDate(root());invoke("setDateValue",new Class<?>[]{EditText.class,LocalDate.class},date,LocalDate.now().minusDays(1));
                scroll(root()).fullScroll(View.FOCUS_DOWN);
            });screen("03-edit-details");recreate();submit();
            ExpenseDb.Transaction edited=db.transactionById(id);
            ok(edited.amountMinor==26025&&"صحة".equals(edited.category),"Edit saved both values");
            ok(db.merchantRules().stream().anyMatch(rule->"=qa groceries".equals(rule.matchText)&&"صحة".equals(rule.category)),"Checked merchant learning survives recreation and commits after a successful edit");
            ok("date_only".equals(edited.timePrecision),"Edit preserved timestamp precision");
            ok(java.time.Instant.ofEpochMilli(edited.when).atZone(ZoneId.systemDefault()).toLocalTime().equals(java.time.LocalTime.of(13,27,41)),"Date edit preserved stored clock without inventing a new one");
            ok(java.time.Instant.ofEpochMilli(edited.when).atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now().minusDays(1)),"Date draft survived recreation");
            ok((Integer)get("selectedTab")==10&&((ExpenseDb.Transaction)get("selectedTransaction")).amountMinor==26025,"Saved edit returns to refreshed detail");
            main(()->invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},edited));pageClick("EGP");click("USD");discard();
            ok("EGP".equals(db.transactionById(id).currency),"Discard never changes currency");
            main(()->invoke("manualDialog",new Class<?>[0]));fill("amount","100");fill("merchant","QA transfer");pageClick("تحويل");
            main(()->root().findViewWithTag("transaction_from").performClick());click(db.sourceLabel(from)+" · يحتاج مراجعة");
            main(()->root().findViewWithTag("transaction_to").performClick());click(db.sourceLabel(to)+" · يحتاج مراجعة");
            main(()->scroll(root()).fullScroll(View.FOCUS_DOWN));screen("04-transfer-details");submit();
            ExpenseDb.Transaction transfer=db.latestInserted();ok("transfer".equals(transfer.kind)&&transfer.paymentSourceId==from&&transfer.destinationSourceId==to,"Valid transfer saved with both accounts");
            LocalDate start=LocalDate.now().minusDays(5),end=LocalDate.now().plusDays(1);
            main(()->{set("selectedTab",6);set("transactionPeriod",new LocalDate[]{start,end});set("transactionSearch","QA");invoke("render",new Class<?>[0]);invoke("transactionFilterDialog",new Class<?>[0]);});
            pageClick("مصروف");pageClick("كل التصنيفات");click("صحة");recreate();
            main(()->scroll(root()).scrollTo(0,0));screen("05-filters");submit();
            ok("expense".equals(get("transactionKindFilter"))&&"صحة".equals(get("transactionCategoryFilter")),"Filter draft applied after recreation");
            ok("QA".equals(get("transactionSearch")),"Applying filters kept search");
            main(()->invoke("transactionFilterDialog",new Class<?>[0]));pageClick("دخل");discard();ok("expense".equals(get("transactionKindFilter")),"Filter discard kept applied state");
            main(()->invoke("transactionFilterDialog",new Class<?>[0]));pageClick("مسح الفلاتر");
            ok("".equals(get("transactionKindFilter"))&&"".equals(get("transactionCategoryFilter")),"Explicit reset cleared filters");
            ok("QA".equals(get("transactionSearch"))&&((LocalDate[])get("transactionPeriod"))[0].equals(start)&&((LocalDate[])get("transactionPeriod"))[1].equals(end),"Reset kept period and search exactly");
            main(()->{set("bulkMode",true);@SuppressWarnings("unchecked") java.util.Set<Long> ids=(java.util.Set<Long>)get("bulkSelected");ids.add(id);invoke("bulkCategoryDialog",new Class<?>[0]);});
            pageClick("غير مصنف");click("بقالة");recreate();screen("06-bulk-review");submit();
            ok("بقالة".equals(db.transactionById(id).category)&&db.transactionById(id).amountMinor==26025,"Bulk classification changed only category");
            ok("تحويلات".equals(db.transactionById(transfer.id).category),"Bulk did not reclassify transfer");
            ok(db.transactionCount()==2,"No extra transaction from filters, recreation or bulk");
            db.getWritableDatabase().execSQL("UPDATE payment_sources SET archived=1 WHERE id=?",new Object[]{from});
            main(()->invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(id)));
            main(()->ok(root().findViewWithTag("transaction_from").getContentDescription().toString().contains("مؤرشف"),"Existing archived source remains named and selectable"));
            submit();ok(db.transactionById(id).paymentSourceId==from,"Edit preserves an existing archived source");
            // A long English name with an InstaPay chip and a review note: chips and time wrap whole, never mid-word.
            ok(db.addManualEntry(System.currentTimeMillis()-60000,90333,"EGP","International Trading Company Limited Cairo@instapay","بقالة",to,"expense",null,null),"Long InstaPay row saved");
            db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{db.latestInserted().id});
            // Separate regression: the review chip is wider than the time at 320dp / 200%.
            ok(db.addManualEntry(System.currentTimeMillis()-30000,90333,"EGP","QA Pending Transfer",MoneyFlow.OPEN_TRANSFER_CATEGORY,to,"expense",null,null),"Long review chip fixture saved");
            db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{db.latestInserted().id});
            main(()->{invoke("resetTransactionFilters",new Class<?>[0]);set("transactionSearch","");set("transactionPeriod",null);set("bulkMode",false);set("selectedTab",6);invoke("render",new Class<?>[0]);});
            main(()->ok(TransactionRowChecks.verify(root(),this::ok)>=4,"Transaction row chips and times checked"));screen("07-rows");
            // Exercise the actual row at constrained widths: a chip may fit the full row but not beside the amount.
            final ExpenseDb.Transaction chipProbe=db.latestInserted();
            main(()->{
                for(int widthDp:new int[]{240,280,320}){
                    android.widget.LinearLayout probe=new android.widget.LinearLayout(activity);probe.setOrientation(1);
                    probe.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    invoke("transactionRow",new Class<?>[]{android.widget.LinearLayout.class,ExpenseDb.Transaction.class,boolean.class},probe,chipProbe,false);
                    int width=Math.round(widthDp*activity.getResources().getDisplayMetrics().density);
                    probe.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                    probe.layout(0,0,width,probe.getMeasuredHeight());
                    ok(TransactionRowChecks.verify(probe,this::ok)>=3,"Long review chip measured at "+widthDp+"dp");
                }
            });
            // Code-only cash suggestion: original merchant, one-tap confirmation, and a purchase correction route.
            String atmBody="شكرًا لاستخدامك بطاقة بنك مصر *2222 ، تم الآن خصم EGP 500.00 على BRKN0123 BRKA يوم 24/10/2026";
            CaptureResult capture=db.captureSms("Banque Misr",atmBody,System.currentTimeMillis());
            ok(capture.transaction!=null&&capture.transaction.needsReview,"Code-only terminal is saved for review");
            long atmId=capture.transaction.id;long countBeforeConfirm=db.transactionCount();
            ExpenseDb.Transaction pendingAtm=db.transactionById(atmId);
            ok("BRKN0123 BRKA".equals(pendingAtm.merchant)&&MoneyFlow.CASH_WITHDRAWAL_CATEGORY.equals(pendingAtm.category),"Original name with prepared cash proposal");
            main(()->{set("selectedTransaction",pendingAtm);set("selectedTab",10);invoke("render",new Class<?>[0]);});
            main(()->{View confirm=root().findViewWithTag("confirm_atm_suggestion");ok(confirm!=null,"One-tap cash proposal action exists");confirm.performClick();});
            ExpenseDb.Transaction confirmedAtm=db.transactionById(atmId);
            ok(!confirmedAtm.needsReview&&confirmedAtm.amountMinor==50000&&"EGP".equals(confirmedAtm.currency)&&"BRKN0123 BRKA".equals(confirmedAtm.merchant),"One tap confirms without changing name, amount or currency");
            ok(db.transactionCount()==countBeforeConfirm,"Confirmation never inserts a second transaction");
            CaptureResult nextCapture=db.captureSms("Banque Misr",atmBody.replace("500.00","1000.00"),System.currentTimeMillis()+1000);
            ok(nextCapture.transaction!=null&&nextCapture.transaction.needsReview,"Confirmation does not enable future automatic terminal learning");
            ExpenseDb.Transaction toPurchase=nextCapture.transaction;
            main(()->invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},toPurchase));
            pageClick(MoneyFlow.CASH_WITHDRAWAL_CATEGORY);click("بقالة");submit();
            ExpenseDb.Transaction corrected=db.transactionById(toPurchase.id);
            ok("بقالة".equals(corrected.category)&&"BRKN0123 BRKA".equals(corrected.merchant)&&corrected.amountMinor==100000,"User can change cash proposal to purchase without losing original name or money");
            result.putString("stream","Transaction pages checks passed: "+checks+"; failed: 0; dark="+dark+"; fontScale="+scale+"\nCaptures: "+captures+"\n");
        }catch(Throwable error){status=0;try{screen("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Transaction pages FAILED: "+trace);}
        finally{try{if(activity!=null)main(()->activity.finish());if(db!=null){db.clearAllData();db.close();}}catch(Exception e){status=0;result.putString("stream",result.getString("stream","")+e);}runner.finish(status,result);}
    }
    private EditText findDate(View node){
        if(node instanceof EditText&&"التاريخ".contentEquals(((EditText)node).getHint()))return (EditText)node;
        if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){EditText date=findDate(((ViewGroup)node).getChildAt(i));if(date!=null)return date;}
        return null;
    }
}
