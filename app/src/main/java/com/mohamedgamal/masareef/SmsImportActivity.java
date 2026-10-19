package com.mohamedgamal.masareef;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Choosing a period imports supported SMS. An optional preview keeps manual selection available. */
public final class SmsImportActivity extends Activity {
    /** One approved batch; the returned net ledger increase is independent of list pagination. */
    static long applySelected(ExpenseDb db,List<SmsPreview> selected) {
        return new ImportBatch(db).applySelected(selected);
    }
    /** Compatibility facade for the import use case; its return follows the batch commit. */
    static long applyBatchWithRepair(ExpenseDb db,List<SmsPreview> selected) {
        return new ImportBatch(db).execute(selected);
    }
    /** The review list is a read after the commit: failing to read it must never turn a saved import into a reported failure. */
    static List<CaptureEvents.Event> pendingAfterCommit(ExpenseDb db,long start,long end) {
        try {return new CaptureEvents(db).pendingHistory(start,end);}
        catch(RuntimeException unreadable){android.util.Log.e("FollowMoney","Imported data; review list unavailable: "+unreadable.getClass().getSimpleName());return Collections.emptyList();}
    }
    private Session session;
    private LinearLayout page, rows, footer;
    private TextView status, selection;
    private ProgressBar progress;
    private Button save;
    private Typeface font;
    private int pageNumber;
    private static final int PAGE_SIZE=40;
    private static final int BRAND=0xff0b3d2e;
    private static final int LIME=0xffc8f25a;
    private int accent=BRAND,border=0xffe3e6df;
    private boolean dark;
    private int INK=0xff0d1f1a,MUTED=0xff5e6b66,background=0xfff4f5f1,surface=0xffffffff;

    private static android.content.SharedPreferences mainPreferences(Context context){
        String name=MainActivity.class.getName(),prefix=context.getPackageName()+".";
        if(name.startsWith(prefix))name=name.substring(prefix.length());
        return context.getSharedPreferences(name,MODE_PRIVATE);
    }
    @Override public void onCreate(Bundle state) {
        dark=mainPreferences(this).getBoolean("dark_mode",false);
        if(dark)setTheme(R.style.AppTheme_Dark);
        super.onCreate(state);
        if(dark) {
            INK=0xffeef3ef;MUTED=0xff9aaaa2;background=0xff0d1a16;surface=0xff16261f;accent=0xff2bbf85;border=0xff2a3b33;
        }
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(background==0xfff4f5f1?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        try {font=new Typeface.Builder(getAssets(),"fonts/ReadexPro.ttf").build();}
        catch(RuntimeException e) {font=Typeface.DEFAULT;}
        Object retained=getLastNonConfigurationInstance();
        session=retained instanceof Session?(Session)retained:new Session(getApplicationContext());
        pageNumber=state==null?0:state.getInt("page",0);
        show();
    }
    @Override protected void onStart() {super.onStart();session.attach(this);}
    @Override protected void onStop() {session.detach(this);super.onStop();}
    @Override public Object onRetainNonConfigurationInstance() {return session;}
    @Override protected void onSaveInstanceState(Bundle out) {super.onSaveInstanceState(out);out.putInt("page",pageNumber);}
    @Override protected void onDestroy() {
        if(!isChangingConfigurations()) session.cancelScan();
        super.onDestroy();
    }
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setTypeface(font);
        t.setPadding(0,dp(7),0,dp(7));return t;
    }
    private Button button(String s,Runnable action) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTypeface(font);
        b.setStateListAnimator(null);b.setElevation(0);b.setTextSize(14);b.setTextColor(accent);b.setMinHeight(dp(48));b.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),border);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x220b3d2e),bg,null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);lp.bottomMargin=dp(6);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }
    private void primary(Button button) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(dark?accent:BRAND);bg.setCornerRadius(dp(16));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33c8f25a),bg,null));
        button.setTextColor(dark?0xff0d1f1a:0xffffffff);button.setTypeface(font,Typeface.BOLD);
    }
    private void show() {
        status=null;selection=null;progress=null;save=null;rows=null;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(background);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(16),dp(20),dp(16));scroll.addView(page);
        footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(20),dp(8),dp(20),dp(12));footer.setBackgroundColor(surface);root.addView(footer);
        setContentView(root);SystemUi.apply(this,root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);header.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView brand=text("Follow Money",18);brand.setPadding(dp(10),0,dp(10),0);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        TextView title=text("استيراد رسائل البنك",24);title.setTypeface(font,Typeface.BOLD);page.addView(title);
        if("idle".equals(session.phase)){choosePeriod();return;}
        page.addView(text(session.startLabel+" ← حتى وقت بدء الاستيراد",13));
        status=text("",15);page.addView(status);
        if("scan".equals(session.phase)||"apply".equals(session.phase)){
            progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));progress.setIndeterminate("apply".equals(session.phase));page.addView(progress);
            if("scan".equals(session.phase)){
                page.addView(text(session.reviewBeforeImport?"هنعرض الرسائل للمراجعة قبل الحفظ.":"هنستورد الرسائل المالية تلقائيًا بعد الفحص. الرسائل المكررة لن تُسجل مرتين، والمستبعدة يدويًا تظل مستبعدة.",14));
                footer.addView(button("إيقاف الفحص",()->{session.cancelScan();show();}));
            }else page.addView(text("جارٍ الحفظ؛ انتظر اكتمال الاستيراد. العمليات المرفوضة ليست مصروفًا، والإلغاءات القديمة تحتاج تأكيدك قبل تغيير التحويل الأصلي.",14));
        }else if("ready".equals(session.phase)||"details".equals(session.phase)){
            boolean reviewing="ready".equals(session.phase);
            page.addView(text(reviewing?"المراجعة اختيارية. المحدد فقط سيُستورد؛ الإلغاء غير المؤكد يظل بحاجة لمراجعة.":"تفاصيل الرسائل التي فحصناها. الرسائل المرفوضة أو المستقبلية ليست مصروفات، والمستبعدة يدويًا لا تُعاد إضافتها.",13));
            if(reviewing){
                LinearLayout all=new LinearLayout(this);
                all.addView(button("تحديد الكل",()->{SmsPreview.selectForImport(session.items);drawRows();}),new LinearLayout.LayoutParams(0,-2,1));
                all.addView(button("إلغاء التحديد",()->{for(SmsPreview r:session.items)r.selected=false;drawRows();}),new LinearLayout.LayoutParams(0,-2,1));page.addView(all);
            }
            rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);page.addView(rows);
            selection=text("",12);footer.addView(selection);
            LinearLayout nav=new LinearLayout(this);
            nav.addView(button("السابق",()->{if(pageNumber>0){pageNumber--;drawRows();scroll.scrollTo(0,0);}}),new LinearLayout.LayoutParams(0,-2,1));
            nav.addView(button("التالي",()->{if((pageNumber+1)*PAGE_SIZE<session.items.size()){pageNumber++;drawRows();scroll.scrollTo(0,0);}}),new LinearLayout.LayoutParams(0,-2,1));footer.addView(nav);
            if(reviewing){save=button("استيراد المحدد",this::confirmApply);primary(save);footer.addView(save);}
            else footer.addView(button("رجوع للنتيجة",()->{session.phase="done";show();}));
            drawRows();
        }else if("done".equals(session.phase)){
            TextView done=text("اكتمل الاستيراد",22);done.setTypeface(font,Typeface.BOLD);page.addView(done);
            ImportResult imported=session.imported;
            if(imported==null||imported.offered==0){
                page.addView(text("لم نعتمد أي رسالة محددة؛ لم تتغير العمليات.",16));
            }else{
                page.addView(text("اعتمدنا "+imported.offered+" رسالة محددة:",16));
                StringBuilder summary=new StringBuilder("• "+imported.created+" عملية جديدة");
                if(imported.matchedExisting>0)summary.append("\n• ").append(imported.matchedExisting).append(" طابقت عملية مسجلة بالفعل");
                if(imported.skippedDuplicate>0)summary.append("\n• ").append(imported.skippedDuplicate).append(" مكررة لم تُسجَّل مرتين");
                if(imported.noLedgerChange>0)summary.append("\n• ").append(imported.noLedgerChange).append(" حدّثت رصيدًا أو تذكيرًا أو مراجعة بلا عملية جديدة");
                if(imported.refused>0)summary.append("\n• ").append(imported.refused).append(" مرفوضة (ليست مصروفًا)");
                if(imported.excluded>0)summary.append("\n• ").append(imported.excluded).append(" مستبعدة عندك");
                page.addView(text(summary.toString(),14));
                // Repair runs inside the same batch and may move a row the user selected as well as pre-existing
                // rows, so it is always reported as repair and the wording never claims a row was unselected.
                int archivedSelected=imported.archivedSelectedRows();
                if(imported.repair.archivedRows>0||imported.repair.ownTransfers>0||imported.repair.feesCreated>0)
                    page.addView(text("الإصلاح الآلي في نفس دفعة الحفظ: سوّى "+imported.repair.ownTransfers+" تحويل داخلي، وأرشف "+imported.repair.archivedRows+" صفًا (قد تكون من صفوف سابقة أو من الصفوف التي اخترتها)، وأضاف "+imported.repair.feesCreated+" عمولة. الرسوم تظهر في العمليات.",14));
                if(archivedSelected>0)
                    page.addView(text("من الصفوف التي اخترتها، "+archivedSelected+" لم تعد قائمة كصف مستقل بعد الدفعة.",14));
            }
            page.addView(text("المكررات لا تُسجل مرتين. الرصيد والتذكيرات ليست مصروفات جديدة؛ إلغاء التحويل لا يلغي المشتريات الأصلية.",14));
            if(session.skipped>0)page.addView(text(session.skipped+" رسالة لم تُعرض على الدفعة: غير محددة أو مستبعدة سابقًا. تقدر تشوفها في التفاصيل.",14));
            if(!session.pending.isEmpty()){
                page.addView(text(session.pending.size()+" رسالة إلغاء قديمة تحتاج تأكيدك؛ التحويلات الأصلية لم تتغير بسببها.",15));
                for(CaptureEvents.Event event:session.pending)page.addView(button("مراجعة الإلغاء · "+(event.amount==null?"المبلغ غير مؤكد":MainActivity.money(event.amount,event.currency)),()->{
                    startActivity(new Intent(this,MainActivity.class).putExtra(CaptureNotifier.EXTRA_CAPTURE_EVENT,event.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
                }));
            }
            page.addView(button("تفاصيل الرسائل",()->{session.phase="details";pageNumber=0;show();}));
            Button open=button("عرض العمليات",this::openTransactions);primary(open);footer.addView(open);
            footer.addView(button("استيراد فترة أخرى",()->{session.items.clear();session.phase="idle";pageNumber=0;show();}));
        }else{
            page.addView(text(session.error,15));footer.addView(button("اختيار فترة أخرى",()->{session.phase="idle";show();}));
        }
        updateProgress();
    }
    private void openTransactions(){
        startActivity(new Intent(this,MainActivity.class).putExtra("open_transactions",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));setResult(RESULT_OK);finish();
    }
    private void choosePeriod(){
        page.addView(text("اختار المدة وهنستورد رسائل البنك مباشرة. هنحفظ العمليات المدعومة ونحدّث الأرصدة والتذكيرات، من غير تكرار. إلغاء التحويل في الرسائل القديمة هتراجعه وتؤكده في العمليات قبل تغيير أي تحويل.",15));
        page.addView(button("آخر 30 يوم",()->scan(LocalDate.now().minusDays(29))));
        page.addView(button("آخر 90 يوم",()->scan(LocalDate.now().minusDays(89))));
        Button all=button("كل الرسائل",()->scan(null));primary(all);page.addView(all);
        page.addView(button("من تاريخ تختاره",this::pickStartDate));
        CheckBox review=new CheckBox(this);review.setText("مراجعة الرسائل قبل الحفظ (اختياري)");review.setTypeface(font);review.setTextColor(INK);review.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));review.setChecked(session.reviewBeforeImport);review.setOnCheckedChangeListener((v,checked)->session.reviewBeforeImport=checked);page.addView(review);
        footer.addView(button("لاحقًا",this::finish));
    }
    private void pickStartDate(){
        LocalDate initial=LocalDate.now().minusDays(29);
        android.app.DatePickerDialog picker=new android.app.DatePickerDialog(this,(view,year,month,day)->scan(LocalDate.of(year,month+1,day)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        picker.setTitle("بداية فترة الاستيراد");picker.setButton(android.content.DialogInterface.BUTTON_POSITIVE,"استيراد",picker);picker.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,"رجوع",picker);picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
        styleText(picker.getWindow().getDecorView());primary(picker.getButton(android.content.DialogInterface.BUTTON_POSITIVE));
    }
    private void styleText(View view){
        if(view instanceof TextView)((TextView)view).setTypeface(font);
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)styleText(group.getChildAt(i));}
    }
    private void scan(LocalDate date) {
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"إذن SMS غير متاح. ارجع وفعّله من التطبيق.",Toast.LENGTH_LONG).show();return;
        }
        session.start(date);pageNumber=0;show();
    }
    private void drawRows() {
        if(rows==null)return;rows.removeAllViews();
        int end=Math.min(session.items.size(),(pageNumber+1)*PAGE_SIZE);
        for(int i=pageNumber*PAGE_SIZE;i<end;i++) {
            SmsPreview r=session.items.get(i);
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12),dp(8),dp(12),dp(8));
            GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(16));box.setBackground(bg);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.bottomMargin=dp(8);rows.addView(box,bp);
            CheckBox check=new CheckBox(this);check.setText(r.title+(!r.currency.isEmpty()?" · "+MainActivity.money(r.amountMinor,r.currency):""));
            check.setTypeface(font);check.setTextColor(INK);check.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));check.setChecked(r.selected);check.setEnabled(r.captureStatus==0);
            check.setOnCheckedChangeListener((b,checked)->{r.selected=checked;updateSelection();});
            LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
            if("ready".equals(session.phase))heading.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            else{TextView title=text(r.title+(!r.currency.isEmpty()?" · "+MainActivity.money(r.amountMinor,r.currency):""),14);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));}
            // A bank sender identifies the source, never an unknown purchase merchant.
            String brand=r.isTransaction()?BrandCatalog.merchant(r.title):BrandCatalog.bank(r.sender);
            int logo=MainActivity.brandLogo(brand);
            if(logo!=0)heading.addView(UiKit.logoBadge(this,logo,brand,r.isTransaction()?r.title:r.sender,44,dark?0:border),new LinearLayout.LayoutParams(dp(44),dp(44)));
            box.addView(heading);
            boolean dateOnly="date_only".equals(r.timePrecision);
            String detail=r.category+"\n"+r.sender+" · "+Instant.ofEpochMilli(r.eventAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(dateOnly?"d MMM yyyy":"d MMM yyyy · h:mm a",new Locale("ar")))+(dateOnly?" · تاريخ فقط":"");
            if(r.isTransaction()&&r.balanceIncluded)detail+="\nيشمل أيضًا تحديث الرصيد من نفس الرسالة";
            if(r.isTransaction()&&r.commitmentIncluded)detail+="\nيشمل أيضًا تحديث/اقتراح التزام";
            if(r.captureStatus==1)detail+="\nمسجلة سابقًا · لن نعيد تطبيقها؛ التصحيح اليدوي محفوظ";
            if(r.captureStatus==2)detail+="\nمستبعدة سابقًا · لن تُعاد إضافتها";
            if("correction".equals(r.kind))detail+=CaptureNotice.parse(r.sender,r.body)==null?"\nلم تُحسب كمصروف":"\nإلغاء تحويل · المطابقة الآمنة فقط؛ غير المؤكد يحتاج مراجعة";
            if("details".equals(session.phase))detail+=r.selected?"\nتمت المعالجة":"\nلم تُستورد";
            TextView info=text(detail,12);info.setTextColor(MUTED);box.addView(info);
            Button original=button("عرض الرسالة",()->showOriginal(r));
            box.addView(original);
        }
        if(session.items.isEmpty())rows.addView(text("لم نجد رسائل مالية قابلة للمراجعة في هذه الفترة.",16));
        updateSelection();
    }
    private void showOriginal(SmsPreview row){
        TextView body=text(row.body==null?"النص الأصلي غير متاح":row.body,14);body.setTextIsSelectable(true);body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        sheet("المصدر · "+row.sender,body,"إغلاق",null);
    }
    private void sheet(String title,View body,String action,Runnable accept){
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);content.setPadding(dp(20),dp(16),dp(20),dp(16));
        GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(26));content.setBackground(bg);
        TextView heading=text(title,20);heading.setTypeface(font,Typeface.BOLD);content.addView(heading);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button close=button(action,()->{dialog.dismiss();if(accept!=null)accept.run();});primary(close);content.addView(close);
        dialog.setContentView(content);android.view.Window window=dialog.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setNavigationBarColor(surface);dialog.show();window.setLayout(-1,Math.round(getResources().getDisplayMetrics().heightPixels*.75f));SystemUi.apply(this,content);
    }
    private void updateSelection(){
        int n=0;for(SmsPreview r:session.items)if(r.selected)n++;
        if(selection!=null)selection.setText(("ready".equals(session.phase)?"المحدد "+n+" رسالة · ":"")+"صفحة "+(pageNumber+1)+" / "+Math.max(1,(session.items.size()+PAGE_SIZE-1)/PAGE_SIZE));
        if(save!=null){save.setEnabled(n>0);save.setText("استيراد "+n+" رسالة محددة");save.setAlpha(n>0?1f:.5f);}
    }
    private void confirmApply(){
        int n=0;for(SmsPreview r:session.items)if(r.selected)n++;if(n==0)return;
        sheet("استيراد "+n+" رسالة؟",text("هنستورد المحدد فقط، بما فيه الأرصدة والتذكيرات والإلغاءات الموضحة. المكررات والتصحيحات اليدوية محفوظة. تقدر ترجع للمراجعة بزر الرجوع.",15),"استيراد",()->{session.apply();show();});
    }
    private void updateProgress(){
        if(status!=null)status.setText("scan".equals(session.phase)?"جارٍ الفحص: "+session.scanned+" من "+session.total+" رسالة":"apply".equals(session.phase)?"جارٍ استيراد الرسائل":"فحصنا "+session.scanned+" رسالة؛ وجدنا "+session.items.size()+" رسالة مالية");
        if(progress!=null){progress.setMax(Math.max(1,session.total));progress.setProgress(session.scanned);}
    }
    @Override public void onBackPressed() {
        synchronized(session){
            if("apply".equals(session.phase)){Toast.makeText(this,"انتظر اكتمال الحفظ",Toast.LENGTH_SHORT).show();return;}
            if("details".equals(session.phase)){session.phase="done";show();return;}
            session.cancelScan();finish();
        }
    }

    private static final class Session {
        final Context app;
        final List<SmsPreview> items=new ArrayList<>();
        volatile String phase="idle",error="",startLabel="";
        volatile int total,scanned,skipped;
        boolean reviewBeforeImport;
        /** What the approved batch actually did; null until a committed batch reports one. */
        volatile ImportResult imported;
        long rangeStart,rangeEnd;
        volatile List<CaptureEvents.Event> pending=java.util.Collections.emptyList();
        volatile boolean cancelled;
        private volatile SmsImportActivity listener;
        private volatile int generation;
        Session(Context app){this.app=app;}
        void attach(SmsImportActivity a){listener=a;a.show();}
        void detach(SmsImportActivity a){if(listener==a)listener=null;}
        void publish(boolean redraw) {
            SmsImportActivity a=listener;if(a==null)return;
            a.runOnUiThread(()->{if(listener==a&&!a.isFinishing()){if(redraw)a.show();else a.updateProgress();}});
        }
        synchronized void cancelScan(){if("scan".equals(phase)){cancelled=true;generation++;phase="idle";}}
        synchronized void start(LocalDate start) {
            cancelled=false;final int token=++generation;items.clear();total=0;scanned=0;skipped=0;imported=null;phase="scan";
            startLabel=start==null?"كل الرسائل":start.toString();
            long since=start==null?0:start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long until=System.currentTimeMillis();rangeStart=since;rangeEnd=Long.MAX_VALUE;pending=java.util.Collections.emptyList();
            AppResources.read(()->{
                List<SmsPreview> candidates=new ArrayList<>();
                // A receipt timestamp can equal another SMS's sent timestamp. Never treat that key as a legacy alias.
                Set<String> sentKeys;
                try {sentKeys=ExpenseDb.smsSentKeys(app,since,until);}
                catch(RuntimeException e){if(token==generation){error="تعذر الفحص. راجع إذن SMS وأعد المحاولة؛ لم نحفظ بيانات الفحص.";phase="error";publish(true);}return;}
                try(ExpenseDb readDb=new ExpenseDb(app);Cursor c=app.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                        new String[]{Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.DATE_SENT},
                        Telephony.Sms.DATE+">=? AND "+Telephony.Sms.DATE+"<=?",new String[]{Long.toString(since),Long.toString(until)},Telephony.Sms.DATE+" ASC")) {
                    if(token!=generation)return;
                    if(c==null)throw new IllegalStateException("SMS provider unavailable");
                    total=c.getCount();
                    if(c!=null)while(c.moveToNext()) {
                        if(cancelled||token!=generation)return;
                        SmsPreview r=SmsPreview.parse(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3));
                        if(r!=null){r.receiptKeySafe=r.canUseReceiptKey(sentKeys);r.captureStatus=readDb.smsCaptureStatus(r.sender,r.body,r.receivedAt,r.captureAt,r.receiptKeySafe);candidates.add(r);}
                        scanned++;if(scanned%25==0)publish(false);
                    }
                    synchronized(this){if(cancelled||token!=generation)return;items.addAll(candidates);phase="ready";if(!reviewBeforeImport){SmsPreview.selectForImport(items);apply();}}
                    publish(true);
                } catch(RuntimeException e){if(token==generation){error="تعذر الفحص. راجع إذن SMS وأعد المحاولة؛ لم نحفظ بيانات الفحص.";phase="error";publish(true);}}
            });
        }
        synchronized void apply() {
            if(!"ready".equals(phase))return;
            List<SmsPreview> selected=new ArrayList<>();for(SmsPreview r:items)if(r.selected&&r.captureStatus!=2)selected.add(r);
            skipped=items.size()-selected.size();
            if(selected.isEmpty()){imported=null;try(ExpenseDb read=new ExpenseDb(app)){pending=pendingAfterCommit(read,rangeStart,rangeEnd);}mainPreferences(app).edit().putBoolean("initial_sms_import_done",true).apply();phase="done";publish(true);return;}phase="apply";
            CaptureQueue.submit(()->{
                try(ExpenseDb writeDb=new ExpenseDb(app)) {
                    ImportResult result=new ImportBatch(writeDb).executeDetailed(selected);
                    // A caller-owned transaction is not a committed import: never show success before it commits.
                    if(!result.committed){error="لم تُعتمد الدفعة بعد؛ لن نعرض نجاحًا قبل تأكيد الحفظ. أعد المحاولة.";phase="error";publish(true);return;}
                    imported=result;
                    mainPreferences(app).edit().putBoolean("initial_sms_import_done",true).putInt("balance_parser_version",13).apply();
                    pending=pendingAfterCommit(writeDb,rangeStart,rangeEnd);
                    phase="done";
                    try{SmsReceiver.notifyDataChanged(app);}catch(RuntimeException refreshError){android.util.Log.e("FollowMoney","Imported data; refresh unavailable: "+refreshError.getClass().getSimpleName());}
                    publish(true);
                } catch(RuntimeException e){error="تعذر اعتماد المحدد؛ تم التراجع عن دفعة الحفظ. راجع الإذن وأعد الفحص.";phase="error";publish(true);}
            });
        }
    }
}
