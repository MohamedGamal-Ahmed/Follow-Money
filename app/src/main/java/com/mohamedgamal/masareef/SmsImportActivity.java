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

/** Historical SMS are scanned without database writes. Only checked messages are applied atomically. */
public final class SmsImportActivity extends Activity {
    private Session session;
    private LinearLayout page, rows;
    private TextView status, selection;
    private ProgressBar progress;
    private Button save;
    private Typeface font;
    private int pageNumber;
    private static final int PAGE_SIZE=40;
    private static final int BRAND=0xff0b3d2e;
    private static final int LIME=0xffc8f25a;
    private int accent=BRAND,border=0xffe3e6df;
    private int INK=0xff0d1f1a,MUTED=0xff5e6b66,background=0xfff4f5f1,surface=0xffffffff;

    @Override public void onCreate(Bundle state) {
        boolean dark=getSharedPreferences("MainActivity",MODE_PRIVATE).getBoolean("dark_mode",false);
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
        b.setTextSize(14);b.setTextColor(accent);b.setMinHeight(dp(48));b.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),border);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x220b3d2e),bg,null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);lp.bottomMargin=dp(6);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }
    private void primary(Button button) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(BRAND);bg.setCornerRadius(dp(16));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33c8f25a),bg,null));
        button.setTextColor(LIME);button.setTypeface(font,Typeface.BOLD);
    }
    private void show() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(20),dp(20),dp(28));page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setBackgroundColor(background);scroll.addView(page);setContentView(scroll);
        scroll.setBackgroundColor(background);SystemUi.apply(this,scroll);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);header.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView brand=text("Follow Money",18);brand.setPadding(dp(10),0,dp(10),0);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        page.addView(text("مراجعة الرسائل القديمة",24));
        if("idle".equals(session.phase)) {choosePeriod();return;}
        page.addView(text(session.startLabel+" ← حتى وقت بدء الفحص",13));
        status=text("",15);page.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setMax(Math.max(1,session.total));page.addView(progress);
        if("scan".equals(session.phase)) {
            page.addView(text("الفحص لا يحفظ أي عملية أو رصيد أو تذكير. تقدر توقفه وترجع بدون تغيير البيانات.",14));
            page.addView(button("إيقاف الفحص",()->{session.cancelScan();session.phase="idle";show();}));
        } else if("ready".equals(session.phase)) {
            page.addView(text("حدد الكل أو اختر رسائل بعينها. المسجلة سابقًا لا تتكرر عند الحفظ. رسائل الاستبعاد والإلغاء لا يحددها زر الكل؛ التصحيحات تحتاج مراجعة منفردة. قد تشمل الرسالة رصيدًا أو تحديث التزام كما هو موضح تحتها.",13));
            selection=text("",15);page.addView(selection);
            LinearLayout all=new LinearLayout(this);
            Button selectAll=button("تحديد الكل",()->{SmsPreview.selectAll(session.items,false);drawRows();});primary(selectAll);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMarginEnd(dp(8));all.addView(selectAll,ap);
            all.addView(button("إلغاء التحديد",()->{for(SmsPreview r:session.items)r.selected=false;drawRows();}),new LinearLayout.LayoutParams(0,-2,1));page.addView(all);
            page.addView(button("الجديد فقط",()->{SmsPreview.selectAll(session.items,true);drawRows();}));
            rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);page.addView(rows);
            LinearLayout nav=new LinearLayout(this);
            nav.addView(button("السابق",()->{if(pageNumber>0){pageNumber--;drawRows();}}),new LinearLayout.LayoutParams(0,-2,1));
            nav.addView(button("التالي",()->{if((pageNumber+1)*PAGE_SIZE<session.items.size()){pageNumber++;drawRows();}}),new LinearLayout.LayoutParams(0,-2,1));page.addView(nav);
            save=button("اعتماد المحدد",this::confirmApply);primary(save);page.addView(save);
            page.addView(button("تغيير الفترة",()->{session.items.clear();session.phase="idle";pageNumber=0;show();}));
            drawRows();
        } else if("apply".equals(session.phase)) {
            page.addView(text("جارٍ اعتماد اختيارك. الانتظار حتى النهاية يمنع الحفظ الجزئي.",14));
        } else if("done".equals(session.phase)) {
            page.addView(text("تمت معالجة "+session.processed+" رسالة محددة؛ صافي زيادة عدد العمليات: "+session.added+". الرصيد والتذكيرات والإلغاءات ليست عمليات جديدة، والتكرارات قد تُربط بالأصل.",16));
            page.addView(button("عرض العمليات",()->{
                startActivity(new Intent(this,MainActivity.class).putExtra("open_transactions",true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                setResult(RESULT_OK);finish();
            }));
        } else {
            page.addView(text(session.error,15));
            page.addView(button("اختيار فترة أخرى",()->{session.phase="idle";show();}));
        }
        updateProgress();
    }
    private void choosePeriod() {
        page.addView(text("اختر فترة الفحص، ثم راجع النتائج قبل حفظها. الرسائل الجديدة تظل تلقائية بعد منح الإذن.",15));
        page.addView(button("آخر 30 يوم",()->scan(LocalDate.now().minusDays(29))));
        page.addView(button("آخر 90 يوم",()->scan(LocalDate.now().minusDays(89))));
        page.addView(button("كل الرسائل",()->scan(null)));
        EditText custom=new EditText(this);custom.setHint("تاريخ البداية YYYY-MM-DD");custom.setSingleLine(true);custom.setTypeface(font);page.addView(custom);
        page.addView(button("فحص من تاريخ محدد",()->{
            try {LocalDate d=LocalDate.parse(ExpenseParser.normalizeDigits(custom.getText().toString().trim()));
                if(d.isAfter(LocalDate.now()))throw new IllegalArgumentException();scan(d);
            } catch(RuntimeException e){custom.setError("أدخل تاريخًا صحيحًا لا يتجاوز اليوم");}
        }));
        page.addView(button("لاحقًا",this::finish));
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
            check.setTypeface(font);check.setTextColor(INK);check.setChecked(r.selected);check.setEnabled(r.captureStatus!=2);
            check.setOnCheckedChangeListener((b,checked)->{r.selected=checked;updateSelection();});
            LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            // A bank sender identifies the source, never an unknown purchase merchant.
            String brand=r.isTransaction()?BrandCatalog.merchant(r.title):BrandCatalog.bank(r.sender);
            int logo=MainActivity.brandLogo(brand);
            if(logo!=0) {
                ImageView badge=new ImageView(this);badge.setImageResource(logo);
                badge.setScaleType(ImageView.ScaleType.FIT_CENTER);badge.setContentDescription(r.title);
                GradientDrawable white=new GradientDrawable();white.setColor(0xffffffff);white.setCornerRadius(dp(22));badge.setBackground(white);
                badge.setPadding(dp(5),dp(5),dp(5),dp(5));heading.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));
            }
            box.addView(heading);
            String detail=r.category+"\n"+r.sender+" · "+Instant.ofEpochMilli(r.eventAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a",new Locale("ar")));
            if(r.isTransaction()&&r.balanceIncluded)detail+="\nيشمل أيضًا تحديث الرصيد من نفس الرسالة";
            if(r.isTransaction()&&r.commitmentIncluded)detail+="\nيشمل أيضًا تحديث/اقتراح التزام";
            if(r.captureStatus==1)detail+="\nمسجلة سابقًا · اختيارها قد يحدث مصدرها؛ التصحيح اليدوي محفوظ";
            if(r.captureStatus==2)detail+="\nمستبعدة سابقًا · لن تُعاد إضافتها";
            TextView info=text(detail,12);info.setTextColor(MUTED);box.addView(info);
            Button original=button("عرض الرسالة",()->new AlertDialog.Builder(this).setTitle("المصدر · "+r.sender).setMessage(r.body).setPositiveButton("إغلاق",null).show());
            box.addView(original);
        }
        if(session.items.isEmpty())rows.addView(text("لم نجد رسائل مالية قابلة للمراجعة في هذه الفترة.",16));
        updateSelection();
    }
    private void updateSelection() {
        int n=0,tx=0;for(SmsPreview r:session.items)if(r.selected){n++;if(r.isTransaction())tx++;}
        if(selection!=null)selection.setText("المحدد "+n+" رسالة · منها "+tx+" مرشح عملية · صفحة "+(pageNumber+1)+" / "+Math.max(1,(session.items.size()+PAGE_SIZE-1)/PAGE_SIZE));
        if(save!=null){save.setEnabled(n>0);save.setText("اعتماد "+n+" رسالة محددة");}
    }
    private void confirmApply() {
        int n=0;for(SmsPreview r:session.items)if(r.selected)n++;
        if(n==0)return;
        new AlertDialog.Builder(this).setTitle("اعتماد "+n+" رسالة؟")
                .setMessage("هنطبق المحدد فقط، بما فيه الأرصدة والتذكيرات أو الإلغاءات الموضحة. منع التكرار والتصحيحات اليدوية يظلان فعالين.")
                .setPositiveButton("اعتماد",(d,w)->{session.apply();show();}).setNegativeButton("رجوع للمراجعة",null).show();
    }
    private void updateProgress() {
        if(status!=null)status.setText("scan".equals(session.phase)?"جارٍ الفحص: "+session.scanned+" من "+session.total+" رسالة":
                "apply".equals(session.phase)?"جارٍ اعتماد اختيارك":"فحصنا "+session.scanned+" رسالة؛ وجدنا "+session.items.size()+" رسالة مالية للمراجعة");
        if(progress!=null){progress.setMax(Math.max(1,session.total));progress.setProgress(session.scanned);}
    }
    @Override public void onBackPressed() {
        if("apply".equals(session.phase)){Toast.makeText(this,"انتظر اكتمال الحفظ",Toast.LENGTH_SHORT).show();return;}
        session.cancelScan();finish();
    }

    private static final class Session {
        final Context app;
        final List<SmsPreview> items=new ArrayList<>();
        volatile String phase="idle",error="",startLabel="";
        volatile int total,scanned,processed,added;
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
        synchronized void cancelScan(){if("scan".equals(phase)){cancelled=true;generation++;}}
        synchronized void start(LocalDate start) {
            cancelled=false;final int token=++generation;items.clear();total=0;scanned=0;phase="scan";
            startLabel=start==null?"كل الرسائل":start.toString();
            long since=start==null?0:start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long until=System.currentTimeMillis();
            new Thread(()->{
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
                    synchronized(this){if(cancelled||token!=generation)return;items.addAll(candidates);phase="ready";}
                    publish(true);
                } catch(RuntimeException e){if(token==generation){error="تعذر الفحص. راجع إذن SMS وأعد المحاولة؛ لم نحفظ بيانات الفحص.";phase="error";publish(true);}}
            },"sms-preview").start();
        }
        synchronized void apply() {
            if(!"ready".equals(phase))return;
            List<SmsPreview> selected=new ArrayList<>();for(SmsPreview r:items)if(r.selected&&r.captureStatus!=2)selected.add(r);
            if(selected.isEmpty())return;phase="apply";
            new Thread(()->{
                try(ExpenseDb writeDb=new ExpenseDb(app)) {
                    android.database.sqlite.SQLiteDatabase sql=writeDb.getWritableDatabase();
                    sql.beginTransaction();
                    try {
                        int before=writeDb.transactions().size();
                        for(SmsPreview r:selected)writeDb.addSms(r.sender,r.body,r.receivedAt,r.captureAt,r.receiptKeySafe);
                        // Do not run a global transfer repair here: unselected history must stay untouched.
                        added=writeDb.transactions().size()-before;processed=selected.size();
                        sql.setTransactionSuccessful();
                    } finally {sql.endTransaction();}
                    // Only around the imported messages: their bank/app copies and own-transfer legs.
                    long first=Long.MAX_VALUE,last=Long.MIN_VALUE;
                    for(SmsPreview r:selected){first=Math.min(first,r.captureAt);last=Math.max(last,r.captureAt);}
                    writeDb.repairBetween(first-86_400_000L,last+86_400_000L);
                    app.getSharedPreferences("MainActivity",MODE_PRIVATE).edit().putBoolean("initial_sms_import_done",true).putInt("balance_parser_version",13).apply();
                    items.clear();phase="done";
                    try{SmsReceiver.notifyDataChanged(app);}catch(RuntimeException refreshError){android.util.Log.e("FollowMoney","Imported data; refresh unavailable: "+refreshError.getClass().getSimpleName());}
                    publish(true);
                } catch(RuntimeException e){error="تعذر اعتماد المحدد؛ تم التراجع عن دفعة الحفظ. راجع الإذن وأعد الفحص.";phase="error";publish(true);}
            },"sms-apply").start();
        }
    }
}
