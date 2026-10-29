package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.mohamedgamal.masareef.ExpenseDb.Recurring;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Cycle allocations, never expenses. Writers borrow the financial owner's transaction.
 * recurring.paid_count is the declared legacy baseline; next_due is the unmaterialized schedule cursor.
 * Current payment state is a read projection of that baseline and identity-bound live allocations. */
final class CommitmentPayments {
    private final ExpenseDb db;
    CommitmentPayments(ExpenseDb db){this.db=db;}

    static void create(SQLiteDatabase sql){
        boolean present=false;try(Cursor c=sql.rawQuery("PRAGMA table_info(recurring)",null)){while(c.moveToNext())if("settlement_key".equals(c.getString(1)))present=true;}
        if(!present)sql.execSQL("ALTER TABLE recurring ADD COLUMN settlement_key TEXT");
        sql.execSQL("UPDATE recurring SET settlement_key='legacy:'||id WHERE settlement_key IS NULL");
        sql.execSQL("CREATE TABLE IF NOT EXISTS commitment_cycles(id INTEGER PRIMARY KEY AUTOINCREMENT,commitment_key TEXT NOT NULL,due_date TEXT NOT NULL,amount_minor INTEGER NOT NULL CHECK(amount_minor>0),currency TEXT NOT NULL,UNIQUE(commitment_key,due_date))");
        sql.execSQL("CREATE TABLE IF NOT EXISTS commitment_payments(id INTEGER PRIMARY KEY AUTOINCREMENT,cycle_id INTEGER,transaction_key TEXT,confirmation_key TEXT UNIQUE,amount_minor INTEGER NOT NULL CHECK(amount_minor>=0),paid_date TEXT NOT NULL,merchant TEXT,transaction_date TEXT,origin TEXT NOT NULL CHECK(origin IN ('automatic','reviewed','provider','manual')),active INTEGER NOT NULL DEFAULT 1,undone_at INTEGER,encrypted_body TEXT,notice_hash TEXT,received_at INTEGER,review_status TEXT NOT NULL DEFAULT 'resolved' CHECK(review_status IN ('pending','dismissed','resolved')))");
        allowSplitPayments(sql);
        sql.execSQL("CREATE INDEX IF NOT EXISTS commitment_cycle_payments ON commitment_payments(cycle_id)");
    }

    /**
     * Schema 28: one payment may cover several cycles ("دفعت قسطين مرة واحدة"), never the same cycle twice. The old index
     * allowed one active allocation per transaction; the new one allows one per (transaction, cycle). The transaction's
     * own amount still caps the sum of its allocations (link checks it).
     */
    static void allowSplitPayments(SQLiteDatabase sql){
        sql.execSQL("DROP INDEX IF EXISTS commitment_active_transaction");
        sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS commitment_active_transaction_cycle ON commitment_payments(transaction_key,cycle_id) WHERE active=1 AND transaction_key IS NOT NULL");
    }

    /** What this transaction's active allocations already use, across every commitment and cycle. */
    long allocatedByTransaction(String identity){
        if(identity==null)return 0;
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM commitment_payments WHERE transaction_key=? AND active=1",new String[]{identity})){
            return c.moveToFirst()?c.getLong(0):0;
        }
    }
    private long unallocated(Transaction tx,String identity){
        return Math.max(0,ExpenseDb.effectiveAmount(tx,db.reimbursementOffsets())-allocatedByTransaction(identity));
    }

    String key(long id){
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT settlement_key FROM recurring WHERE id=?",new String[]{Long.toString(id)})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    static final class Cycle {
        final long id,expected;final LocalDate due;final String currency;
        long paid;int invalid;LocalDate lastPaid;final Map<Long,Long> applied=new java.util.HashMap<>();
        Cycle(long id,LocalDate due,long expected,String currency){this.id=id;this.due=due;this.expected=expected;this.currency=currency;}
        long remaining(){return Math.max(0,expected-paid);}
    }
    static final class Payment {
        final long id,amount,applied;final LocalDate due;final String origin,currency;final Long transactionId;final boolean active,valid;
        Payment(long id,long amount,long applied,LocalDate due,String origin,String currency,Long transactionId,boolean active,boolean valid){
            this.id=id;this.amount=amount;this.applied=applied;this.due=due;this.origin=origin;this.currency=currency;this.transactionId=transactionId;this.active=active;this.valid=valid;
        }
    }
    private static LocalDate day(long when){return Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();}
    private boolean eligible(Transaction tx){
        if(tx==null||tx.needsReview||!"expense".equals(tx.kind)||MoneyFlow.isCashWithdrawal(tx.kind,tx.category)||
                MoneyFlow.BANK_FEE_CATEGORY.equals(tx.category)||"رسوم تحويل".equals(tx.category))return false;
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM transactions t WHERE t.id=? AND t.fee_parent_id IS NULL AND NOT EXISTS(SELECT 1 FROM card_settlements s WHERE s.transaction_id=t.id)",new String[]{Long.toString(tx.id)})){
            return c.moveToFirst();
        }
    }
    private Transaction paymentTransaction(String identity){
        if(identity==null)return null;
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE unique_key=?",new String[]{identity})){
            return c.moveToFirst()?db.transactionById(c.getLong(0)):null;
        }
    }
    List<Cycle> cycles(long commitmentId){
        List<Cycle> result=new ArrayList<>();String key=key(commitmentId);if(key==null)return result;
        Map<Long,Long> offsets=db.reimbursementOffsets();
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,due_date,amount_minor,currency FROM commitment_cycles WHERE commitment_key=? ORDER BY due_date,id",new String[]{key})){
            while(c.moveToNext()){
                Cycle cycle=new Cycle(c.getLong(0),LocalDate.parse(c.getString(1)),c.getLong(2),c.getString(3));
                try(Cursor p=db.getReadableDatabase().rawQuery("SELECT transaction_key,amount_minor,paid_date,merchant,transaction_date,id FROM commitment_payments WHERE cycle_id=? AND active=1 ORDER BY id",new String[]{Long.toString(cycle.id)})){
                    while(p.moveToNext()){
                        Transaction tx=paymentTransaction(p.getString(0));
                        boolean confirmation=p.isNull(0);
                        boolean valid=confirmation || eligible(tx)&&cycle.currency.equals(tx.currency)&&
                                tx.merchant.equals(p.getString(3))&&day(tx.when).toString().equals(p.getString(4));
                        long amount=valid?confirmation?p.getLong(1):Math.min(p.getLong(1),ExpenseDb.effectiveAmount(tx,offsets)):0;
                        long applied=Math.min(cycle.remaining(),amount);
                        if(!valid||applied<p.getLong(1))cycle.invalid++;
                        cycle.paid=Math.addExact(cycle.paid,applied);cycle.applied.put(p.getLong(5),applied);
                        if(applied>0){LocalDate paid=LocalDate.parse(p.getString(2));if(cycle.lastPaid==null||paid.isAfter(cycle.lastPaid))cycle.lastPaid=paid;}
                    }
                }
                result.add(cycle);
            }
        }
        return result;
    }
    Recurring project(Recurring baseline){
        int paid=baseline.paidCount;LocalDate due=baseline.nextDue;String last=baseline.lastPaidDate;
        long amount=baseline.amountMinor;String currency=baseline.currency;
        for(Cycle cycle:cycles(baseline.id)){
            if(cycle.remaining()==0)paid=Math.addExact(paid,1);
            else if(!cycle.due.isAfter(due)){due=cycle.due;amount=cycle.expected;currency=cycle.currency;}
            if(cycle.remaining()==0&&cycle.lastPaid!=null&&(last==null||cycle.lastPaid.isAfter(LocalDate.parse(last))))last=cycle.lastPaid.toString();
        }
        if(baseline.totalCount!=null)paid=Math.min(paid,baseline.totalCount);
        String status=baseline.isDone()||baseline.totalCount!=null&&paid>=baseline.totalCount?"done":"active";
        return new Recurring(baseline.id,baseline.merchant,amount,currency,due,baseline.lastAlertDate,baseline.cycle,baseline.kind,
                baseline.totalCount,paid,baseline.remindDays,baseline.matchText,baseline.paymentSourceId,last,status,baseline.dueDay,baseline.needsReview);
    }
    private boolean named(Recurring plan,Transaction tx){
        if(ExpenseParser.genericMerchant(tx.merchant))return false;
        boolean name=sameCounterparty(plan,tx.merchant);
        return name && (plan.paymentSourceId==null||tx.paymentSourceId!=null&&db.accountId(plan.paymentSourceId)==db.accountId(tx.paymentSourceId));
    }
    static boolean sameCounterparty(Recurring plan,String merchant){
        if(merchant==null)return false;
        if(MerchantClassifier.sameMerchant(plan.merchant,merchant))return true;
        for(String alias:plan.matchText.split("\\|")){
            String clean=alias.trim();if(clean.length()<3)continue;
            if(java.util.regex.Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(clean)+"(?![\\p{L}\\p{N}])").matcher(merchant).find())return true;
        }return false;
    }
    private int window(Recurring plan){return "weekly".equals(plan.cycle)?3:12;}
    private List<Cycle> possibleCycles(Recurring plan,LocalDate at){
        List<Cycle> result=cycles(plan.id);LocalDate due=plan.nextDue;
        int capacity=plan.totalCount==null?1200:Math.max(0,plan.totalCount-plan.paidCount-result.size());
        for(int i=0;i<Math.min(1200,capacity)&&!due.isAfter(at.plusDays(window(plan)));i++){
            result.add(new Cycle(-1,due,plan.amountMinor,plan.currency));due=plan.nextAfter(due);
        }
        return result;
    }
    private static final class Choice {
        final Recurring plan;final Cycle cycle;
        Choice(Recurring plan,Cycle cycle){this.plan=plan;this.cycle=cycle;}
    }
    private Cycle materialize(Recurring plan,LocalDate due){
        for(Cycle cycle:cycles(plan.id))if(cycle.due.equals(due))return cycle;
        String key=key(plan.id);if(key==null)throw new IllegalArgumentException("Commitment identity missing");
        LocalDate cursor=plan.nextDue;int steps=0;Cycle selected=null;
        int existing=cycles(plan.id).size();
        while(!cursor.isAfter(due)){
            if(++steps>1200||plan.totalCount!=null&&plan.paidCount+existing>=plan.totalCount)
                throw new IllegalArgumentException("Cycle outside commitment schedule");
            ContentValues v=new ContentValues();v.put("commitment_key",key);v.put("due_date",cursor.toString());v.put("amount_minor",plan.amountMinor);v.put("currency",plan.currency);
            long id=db.getWritableDatabase().insertOrThrow("commitment_cycles",null,v);existing++;
            if(cursor.equals(due))selected=new Cycle(id,cursor,plan.amountMinor,plan.currency);
            cursor=plan.nextAfter(cursor);
        }
        if(selected==null)throw new IllegalArgumentException("Cycle is not on the schedule");
        ContentValues v=new ContentValues();v.put("next_due",cursor.toString());v.putNull("last_alert_date");
        db.getWritableDatabase().update("recurring",v,"id=?",new String[]{Long.toString(plan.id)});
        return selected;
    }
    boolean automatic(long transactionId){
        if(!db.getWritableDatabase().inTransaction())throw new IllegalStateException("Commitment match must share the financial transaction");
        Transaction tx=db.transactionById(transactionId);if(!eligible(tx))return false;
        String identity=db.rowIdentityKey(transactionId);if(identity==null)return false;
        // Even an undone link is a user decision; a repeated source must never relink it silently.
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM commitment_payments WHERE transaction_key=? LIMIT 1",new String[]{identity})){if(c.moveToFirst())return false;}
        for(Recurring plan:db.storedRecurring())if(!plan.isDone()&&!plan.needsReview&&named(plan,tx)&&plan.currency.equals(tx.currency)){
            db.normalizeEarlyPayments();break;
        }
        List<Choice> choices=new ArrayList<>();LocalDate at=day(tx.when);
        for(Recurring plan:db.storedRecurring()){
            if(plan.isDone()||plan.needsReview||!named(plan,tx))continue;
            for(Cycle cycle:possibleCycles(plan,at))if(cycle.currency.equals(tx.currency)&&cycle.expected==tx.amountMinor&&
                    Math.abs(ChronoUnit.DAYS.between(cycle.due,at))<=window(plan))choices.add(new Choice(plan,cycle));
        }
        if(choices.size()!=1)return false;
        Choice choice=choices.get(0);Cycle cycle=materialize(choice.plan,choice.cycle.due);
        if(cycle.remaining()<tx.amountMinor){
            // Replace an amountless provider confirmation with the actual payment proof in this cycle.
            // A later cancellation/refund can then reopen it; a repeated provider message cannot re-pay it.
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN origin='provider' AND transaction_key IS NULL THEN 1 ELSE 0 END) FROM commitment_payments WHERE cycle_id=? AND active=1",new String[]{Long.toString(cycle.id)})){
                c.moveToFirst();if(c.getLong(0)==0||c.getLong(0)!=c.getLong(1))return false;
            }
            ContentValues retired=new ContentValues();retired.put("active",0);retired.put("undone_at",System.currentTimeMillis());
            db.getWritableDatabase().update("commitment_payments",retired,"cycle_id=? AND active=1 AND origin='provider'",new String[]{Long.toString(cycle.id)});
            cycle=new Cycle(cycle.id,cycle.due,cycle.expected,cycle.currency);
        }
        insertAllocation(cycle,tx,identity,tx.amountMinor,"automatic");classifyPayment(choice.plan,tx);return true;
    }
    private void insertAllocation(Cycle cycle,Transaction tx,String identity,long amount,String origin){
        if(amount<=0||amount>cycle.remaining())throw new IllegalArgumentException("Cycle allocation changed");
        ContentValues v=new ContentValues();v.put("cycle_id",cycle.id);v.put("transaction_key",identity);v.put("amount_minor",amount);
        v.put("paid_date",day(tx.when).toString());v.put("merchant",tx.merchant);v.put("transaction_date",day(tx.when).toString());v.put("origin",origin);
        db.getWritableDatabase().insertOrThrow("commitment_payments",null,v);
    }
    /** Mohamed's decision: only unedited automatic unknown/transfer categories follow the commitment. */
    private void classifyPayment(Recurring plan,Transaction tx){
        String category="subscription".equals(plan.kind)?"اشتراكات":"bill".equals(plan.kind)?"فواتير":"أقساط";
        ContentValues v=new ContentValues();v.put("category",category);
        db.getWritableDatabase().update("transactions",v,"id=? AND unique_key=? AND user_edited=0 AND source NOT IN ('يدوي','CSV') AND category IN (?,?)",
                new String[]{Long.toString(tx.id),db.rowIdentityKey(tx.id),MoneyFlow.UNCATEGORIZED,MoneyFlow.OPEN_TRANSFER_CATEGORY});
    }
    long link(long commitmentId,LocalDate due,long transactionId,long amount){
        return link(commitmentId,due,transactionId,amount,db.rowIdentityKey(transactionId),db.transactionById(transactionId));
    }
    private long link(long commitmentId,LocalDate due,long transactionId,long amount,String expectedIdentity,Transaction expected){
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            Recurring plan=db.storedRecurringById(commitmentId);Transaction tx=db.transactionById(transactionId);
            if(plan==null||project(plan).isDone()||!eligible(tx))throw new IllegalArgumentException("Payment is no longer eligible");
            if(expectedIdentity==null||!expectedIdentity.equals(db.rowIdentityKey(transactionId))||expected==null||
                    tx.amountMinor!=expected.amountMinor||tx.when!=expected.when||!tx.currency.equals(expected.currency)||
                    !tx.merchant.equals(expected.merchant)||!tx.category.equals(expected.category)||
                    !java.util.Objects.equals(tx.paymentSourceId,expected.paymentSourceId))throw new IllegalArgumentException("Payment preview changed");
            Cycle cycle=materialize(plan,due);String identity=db.rowIdentityKey(transactionId);
            if(!cycle.currency.equals(tx.currency)||amount>unallocated(tx,identity))throw new IllegalArgumentException("Payment currency or amount changed");
            insertAllocation(cycle,tx,identity,amount,"reviewed");
            long id;try(Cursor c=sql.rawQuery("SELECT last_insert_rowid()",null)){c.moveToFirst();id=c.getLong(0);}
            classifyPayment(plan,tx);
            if(own)sql.setTransactionSuccessful();return id;
        }finally{if(own)sql.endTransaction();}
    }
    boolean confirm(long commitmentId,LocalDate at,String confirmationKey,String origin){
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            Recurring plan=db.storedRecurringById(commitmentId);if(plan==null||plan.isDone())return false;
            Recurring current=project(plan);if(current.isDone())return false;
            boolean partial=false;for(Cycle c:cycles(commitmentId))if(c.due.equals(current.nextDue)&&c.paid>0&&c.remaining()>0)partial=true;
            if(!partial&&current.lastPaidDate!=null&&ChronoUnit.DAYS.between(LocalDate.parse(current.lastPaidDate),at)<("weekly".equals(plan.cycle)?4:15))return false;
            try(Cursor c=sql.rawQuery("SELECT 1 FROM commitment_payments WHERE confirmation_key=?",new String[]{confirmationKey})){if(c.moveToFirst())return false;}
            Cycle cycle=materialize(plan,current.nextDue);if(cycle.remaining()==0)return false;
            ContentValues v=new ContentValues();v.put("cycle_id",cycle.id);v.put("confirmation_key",confirmationKey);v.put("amount_minor",cycle.remaining());v.put("paid_date",at.toString());v.put("origin",origin);
            sql.insertOrThrow("commitment_payments",null,v);if(own)sql.setTransactionSuccessful();return true;
        }finally{if(own)sql.endTransaction();}
    }
    boolean confirmCycle(long id,LocalDate due,String expectedKey,LocalDate at){
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            db.normalizeEarlyPayments();
            Recurring stored=db.storedRecurringById(id);
            if(stored==null||expectedKey==null||!expectedKey.equals(key(id))||!project(stored).nextDue.equals(due))return false;
            boolean changed=confirm(id,at,"manual:"+java.util.UUID.randomUUID(),"manual");
            if(own)sql.setTransactionSuccessful();return changed;
        }finally{if(own)sql.endTransaction();}
    }
    boolean undo(long paymentId){
        ContentValues v=new ContentValues();v.put("active",0);v.put("undone_at",System.currentTimeMillis());
        return db.getWritableDatabase().update("commitment_payments",v,"id=? AND active=1",new String[]{Long.toString(paymentId)})==1;
    }
    List<Payment> history(long commitmentId){
        List<Payment> rows=new ArrayList<>();String key=key(commitmentId);if(key==null)return rows;
        Map<Long,Long> applied=new java.util.HashMap<>();for(Cycle cycle:cycles(commitmentId))applied.putAll(cycle.applied);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT p.id,p.amount_minor,c.due_date,p.origin,p.transaction_key,p.active,p.merchant,p.transaction_date,c.currency FROM commitment_payments p JOIN commitment_cycles c ON c.id=p.cycle_id WHERE c.commitment_key=? ORDER BY c.due_date DESC,p.id DESC",new String[]{key(commitmentId)})){
            while(c.moveToNext()){
                Transaction tx=paymentTransaction(c.getString(4));boolean confirmation=c.isNull(4);
                boolean valid=confirmation||eligible(tx)&&tx.currency.equals(c.getString(8))&&tx.merchant.equals(c.getString(6))&&day(tx.when).toString().equals(c.getString(7));
                rows.add(new Payment(c.getLong(0),c.getLong(1),applied.getOrDefault(c.getLong(0),0L),LocalDate.parse(c.getString(2)),c.getString(3),c.getString(8),tx==null?null:tx.id,c.getInt(5)!=0,valid));
            }
        }return rows;
    }
    long recordProvider(String service,long when,long receivedAt,String sourceKey,String body){
        String key="provider:"+sourceKey;
        String hash=CaptureIdentity.normalized(service,body);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM commitment_payments WHERE confirmation_key=? OR notice_hash=? AND received_at BETWEEN ? AND ?",new String[]{key,hash,Long.toString(receivedAt-120000),Long.toString(receivedAt+120000)})){if(c.moveToFirst())return -1;}
        String encrypted=EvidenceVault.encrypt(body);if(encrypted==null)throw new IllegalStateException("Provider evidence could not be encrypted");
        ContentValues v=new ContentValues();v.put("confirmation_key",key);v.put("amount_minor",0);v.put("paid_date",day(when).toString());
        v.put("merchant",service);v.put("origin","provider");v.put("active",0);v.put("encrypted_body",encrypted);v.put("review_status","pending");v.put("notice_hash",hash);v.put("received_at",receivedAt);
        return db.getWritableDatabase().insertOrThrow("commitment_payments",null,v);
    }
    boolean resolveProvider(long noticeId,long commitmentId,LocalDate due){
        return resolveProvider(noticeId,commitmentId,due,key(commitmentId));
    }
    boolean resolveProvider(long noticeId,long commitmentId,LocalDate due,String expectedKey){
        return resolveProvider(noticeId,commitmentId,due,expectedKey,false);
    }
    boolean resolveProvider(long noticeId,long commitmentId,LocalDate due,String expectedKey,boolean automatic){
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            try(Cursor c=sql.rawQuery("SELECT 1 FROM commitment_payments WHERE id=? AND review_status='pending' AND cycle_id IS NULL",new String[]{Long.toString(noticeId)})){if(!c.moveToFirst())return false;}
            Recurring plan=db.storedRecurringById(commitmentId);if(plan==null||plan.isDone())return false;
            if(expectedKey==null||!expectedKey.equals(key(commitmentId)))throw new IllegalArgumentException("Commitment preview changed");
            Cycle cycle=materialize(plan,due);if(automatic&&cycle.invalid>0)return false;
            ContentValues v=new ContentValues();v.put("cycle_id",cycle.id);v.put("amount_minor",cycle.remaining());v.put("active",cycle.remaining()>0?1:0);v.put("review_status","resolved");
            boolean changed=sql.update("commitment_payments",v,"id=? AND review_status='pending'",new String[]{Long.toString(noticeId)})==1;
            if(own)sql.setTransactionSuccessful();return changed;
        }finally{if(own)sql.endTransaction();}
    }
    static final class ProviderNotice {
        final long id;final String service,date,body;final boolean failed;
        ProviderNotice(Cursor c){id=c.getLong(0);service=c.getString(1);date=day(c.getLong(2)).toString();EvidenceVault.Read read=EvidenceVault.read(c.getString(3));body=read.body;failed=read.failed;}
    }
    List<ProviderNotice> pendingProviders(){
        List<ProviderNotice> rows=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,merchant,received_at,encrypted_body FROM commitment_payments WHERE review_status='pending' AND cycle_id IS NULL ORDER BY id DESC",null)){while(c.moveToNext())rows.add(new ProviderNotice(c));}return rows;
    }
    boolean dismissProvider(long noticeId){
        ContentValues v=new ContentValues();v.put("review_status","dismissed");
        return db.getWritableDatabase().update("commitment_payments",v,"id=? AND review_status='pending' AND cycle_id IS NULL",new String[]{Long.toString(noticeId)})==1;
    }
    List<LocalDate> providerCycles(Recurring plan,LocalDate at){
        List<LocalDate> dates=new ArrayList<>();
        for(Cycle cycle:possibleCycles(plan,at))if(Math.abs(ChronoUnit.DAYS.between(cycle.due,at))<=window(plan))dates.add(cycle.due);
        return dates;
    }
    List<Transaction> candidates(LocalDate due){
        List<Transaction> rows=new ArrayList<>();
        for(Transaction tx:db.transactionsBetween(due.minusMonths(1),due.plusMonths(1).plusDays(1))){
            if(!eligible(tx))continue;String identity=db.rowIdentityKey(tx.id);
            // A payment that still has an unallocated part (one transfer for two installments) stays a candidate.
            if(unallocated(tx,identity)<=0)continue;
            rows.add(tx);
        }return rows;
    }
    static final class AllocationPreview {
        final long commitmentId,expected,max,available;final LocalDate due;final String commitmentKey,transactionKey,currency;final Transaction transaction;
        AllocationPreview(long commitmentId,Cycle cycle,String commitmentKey,String transactionKey,Transaction tx,long max){this(commitmentId,cycle,commitmentKey,transactionKey,tx,max,max);}
        /** available: what the payment still has for any cycle; max: what the chosen cycle can take of it. */
        AllocationPreview(long commitmentId,Cycle cycle,String commitmentKey,String transactionKey,Transaction tx,long max,long available){
            this.commitmentId=commitmentId;expected=cycle.expected;this.max=max;this.available=available;due=cycle.due;currency=cycle.currency;
            this.commitmentKey=commitmentKey;this.transactionKey=transactionKey;transaction=tx;
        }
    }
    /**
     * The open cycle of the commitment's own schedule nearest to the payment day (stored cycles, then the schedule from
     * next_due on, as possibleCycles builds it), in the payment's currency; null when none is open. An overdue plan
     * (next_due 26 Aug, paid 26 Oct) gets 26 Oct, a month-end plan keeps its anchor (31 Oct → 30 Nov → 31 Dec).
     */
    LocalDate suggestedCycle(long commitmentId,long transactionId){
        Recurring plan=db.storedRecurringById(commitmentId);Transaction tx=db.transactionById(transactionId);
        if(plan==null||tx==null||project(plan).isDone())return null;
        LocalDate day=Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate best=null;long distance=Long.MAX_VALUE;
        for(Cycle cycle:possibleCycles(plan,day)){
            if(!cycle.currency.equals(tx.currency)||cycle.remaining()<=0)continue;
            long d=Math.abs(ChronoUnit.DAYS.between(cycle.due,day));
            if(d<distance){distance=d;best=cycle.due;}
        }
        return best;
    }
    AllocationPreview preview(long commitmentId,LocalDate due,long transactionId){
        Recurring plan=db.storedRecurringById(commitmentId);Transaction tx=db.transactionById(transactionId);
        if(plan==null||project(plan).isDone()||!eligible(tx))throw new IllegalArgumentException("Payment changed");
        for(Cycle cycle:possibleCycles(plan,due))if(cycle.due.equals(due)&&cycle.currency.equals(tx.currency)){
            long available=unallocated(tx,db.rowIdentityKey(tx.id)),max=Math.min(cycle.remaining(),available);
            if(max<=0)throw new IllegalArgumentException("Cycle already paid");
            return new AllocationPreview(commitmentId,cycle,key(commitmentId),db.rowIdentityKey(tx.id),tx,max,available);
        }
        throw new IllegalArgumentException("Choose a cycle on this commitment's schedule and in the payment currency");
    }
    long link(AllocationPreview expected,long amount){
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            if(!expected.commitmentKey.equals(key(expected.commitmentId)))throw new IllegalArgumentException("Commitment preview changed");
            AllocationPreview current=preview(expected.commitmentId,expected.due,expected.transaction.id);
            if(current.expected!=expected.expected||!current.currency.equals(expected.currency)||amount>expected.max)
                throw new IllegalArgumentException("Cycle preview changed");
            long id=link(expected.commitmentId,expected.due,expected.transaction.id,amount,expected.transactionKey,expected.transaction);
            if(own)sql.setTransactionSuccessful();return id;
        }finally{if(own)sql.endTransaction();}
    }
    /**
     * Open cycles of this commitment from a due date on, oldest first: stored cycles with something left, then the
     * schedule after them (respecting the plan's total count). For the "this payment covers which installments" list.
     */
    List<Cycle> openCyclesFrom(long commitmentId,LocalDate from,int limit){
        List<Cycle> result=new ArrayList<>();Recurring plan=db.storedRecurringById(commitmentId);
        if(plan==null||project(plan).isDone())return result;
        List<Cycle> stored=cycles(commitmentId);
        for(Cycle cycle:stored)if(!cycle.due.isBefore(from)&&cycle.remaining()>0&&result.size()<limit)result.add(cycle);
        LocalDate due=plan.nextDue;
        int capacity=plan.totalCount==null?limit:Math.max(0,plan.totalCount-plan.paidCount-stored.size());
        for(int i=0;i<1200&&capacity>0&&result.size()<limit;i++){
            if(!due.isBefore(from)){result.add(new Cycle(-1,due,plan.amountMinor,plan.currency));capacity--;}
            due=plan.nextAfter(due);
        }
        return result;
    }

    /**
     * The amounts one payment gives to the chosen cycles, oldest first: each cycle takes what it still needs until the
     * payment runs out. The last cycle may be partly paid; an amount left over stays unallocated. Pure arithmetic.
     */
    static long[] split(long available,long[] remaining){
        long[] out=new long[remaining.length];long left=Math.max(0,available);
        for(int i=0;i<remaining.length;i++){long take=Math.min(left,Math.max(0,remaining[i]));out[i]=take;left-=take;}
        return out;
    }

    /**
     * Links one recorded payment to several cycles of one commitment at once, in one database transaction. The
     * transaction must be unchanged since the preview (identity, amount, date, merchant, category, source).
     * Returns the number of cycles that received an amount.
     */
    int linkMany(AllocationPreview expected,List<LocalDate> dues){
        if(dues==null||dues.isEmpty())throw new IllegalArgumentException("Choose at least one installment");
        SQLiteDatabase sql=db.getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try{
            if(!expected.commitmentKey.equals(key(expected.commitmentId)))throw new IllegalArgumentException("Commitment preview changed");
            Recurring plan=db.storedRecurringById(expected.commitmentId);Transaction tx=db.transactionById(expected.transaction.id);
            Transaction was=expected.transaction;
            if(plan==null||project(plan).isDone()||!eligible(tx)||!expected.transactionKey.equals(db.rowIdentityKey(tx.id))||
                    tx.amountMinor!=was.amountMinor||tx.when!=was.when||!tx.currency.equals(was.currency)||!tx.merchant.equals(was.merchant)||
                    !tx.category.equals(was.category)||!java.util.Objects.equals(tx.paymentSourceId,was.paymentSourceId))
                throw new IllegalArgumentException("Payment preview changed");
            List<LocalDate> ordered=new ArrayList<>(new java.util.TreeSet<>(dues));
            long left=unallocated(tx,expected.transactionKey);int linked=0;
            for(LocalDate due:ordered){
                if(left<=0)break;
                Cycle cycle=materialize(plan,due);plan=db.storedRecurringById(expected.commitmentId);
                if(!cycle.currency.equals(tx.currency))throw new IllegalArgumentException("Payment currency changed");
                long take=Math.min(left,cycle.remaining());if(take<=0)continue;
                insertAllocation(cycle,tx,expected.transactionKey,take,"reviewed");left-=take;linked++;
            }
            if(linked==0)throw new IllegalArgumentException("Installments already paid");
            classifyPayment(plan,tx);
            if(own)sql.setTransactionSuccessful();return linked;
        }finally{if(own)sql.endTransaction();}
    }

    EvidenceVault.Read evidence(long paymentId){
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT encrypted_body FROM commitment_payments WHERE id=? AND origin='provider'",new String[]{Long.toString(paymentId)})){
            return c.moveToFirst()?EvidenceVault.read(c.getString(0)):EvidenceVault.Read.NONE;
        }
    }
}
