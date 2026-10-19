package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Dated limit history and opt-in only; closed-day scores always derive from the live ledger. */
final class DailyPointsStore {
    static final String SETTINGS="daily_points";
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);}
    static boolean enabled(Context context,String code){return prefs(context).getBoolean("enabled_"+code,false);}
    private static String signature(DailyAllowance.Plan plan){return plan==null?"none":plan.limit+"@"+plan.starts;}
    private static boolean valid(DailyAllowance.Plan plan,LocalDate day){return plan!=null&&!day.isBefore(plan.starts);}
    private static String term(String code,LocalDate day){return "auto_term_"+code+"_"+day;}
    private static LocalDate historyDate(SharedPreferences prefs,String code,LocalDate today){
        try{LocalDate last=LocalDate.parse(prefs.getString("auto_last_"+code,today.toString()));return last.isAfter(today)?last:today;}
        catch(java.time.format.DateTimeParseException invalid){return today;}
    }
    private static TreeMap<LocalDate,Long> dates(SharedPreferences prefs,String prefix){
        TreeMap<LocalDate,Long> result=new TreeMap<>();
        for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith(prefix)&&e.getValue() instanceof Long)
            try{result.put(LocalDate.parse(e.getKey().substring(prefix.length())),(Long)e.getValue());}catch(java.time.format.DateTimeParseException ignored){}
        return result;
    }
    private static void clearFuture(SharedPreferences prefs,SharedPreferences.Editor edit,String code,LocalDate today){
        for(LocalDate day:dates(prefs,"auto_term_"+code+"_").keySet())if(day.isAfter(today))edit.remove(term(code,day));
    }
    static synchronized void enable(Context context,String code,LocalDate today) {
        DailyAllowance.Plan plan=DailySummary.plan(context,code);
        if(plan==null||today.isBefore(plan.starts))throw new IllegalArgumentException("Daily limit required");
        today=historyDate(prefs(context),code,today);
        observe(context,code,today);
        SharedPreferences prefs=prefs(context);SharedPreferences.Editor edit=prefs.edit();clearFuture(prefs,edit,code,today);
        edit.putBoolean("enabled_"+code,true).putString("auto_signature_"+code,signature(plan))
                .putString("auto_last_"+code,today.toString()).putLong(term(code,today),plan.limit).putLong(term(code,today.plusDays(1)),plan.limit);
        if(!prefs.contains("auto_since_"+code))edit.putString("auto_since_"+code,today.toString());
        if(!prefs.contains("limit_"+code+"_"+today))edit.putLong("limit_"+code+"_"+today,plan.limit);
        edit.commit();
    }
    static void disable(Context context,String code){disable(context,code,LocalDate.now());}
    static synchronized void disable(Context context,String code,LocalDate today){
        today=historyDate(prefs(context),code,today);
        observe(context,code,today);SharedPreferences prefs=prefs(context);SharedPreferences.Editor edit=prefs.edit();clearFuture(prefs,edit,code,today);
        edit.putBoolean("enabled_"+code,false).putLong(term(code,today.plusDays(1)),0).commit();
    }
    static void observeToday(Context context) {
        LocalDate today=LocalDate.now();
        for(String code:new String[]{"EGP","USD","EUR","GBP"})observe(context,code,today);
    }
    static synchronized void observe(Context context,String code,LocalDate today) {
        DailyAllowance.Plan plan=DailySummary.plan(context,code);
        SharedPreferences prefs=prefs(context);String saved=prefs.getString("auto_signature_"+code,null);
        boolean enabled=enabled(context,code);SharedPreferences.Editor edit=prefs.edit();
        if(saved==null){
            if(!enabled)return;
            // Old observations prove individual days only. Start continuity now, never infer old gaps.
            edit.putString("auto_since_"+code,today.toString()).putString("auto_last_"+code,today.toString())
                    .putString("auto_signature_"+code,signature(plan)).putLong(term(code,today),valid(plan,today)?plan.limit:0);
            if(valid(plan,today)&&!prefs.contains("limit_"+code+"_"+today))edit.putLong("limit_"+code+"_"+today,plan.limit);
            if(plan!=null&&today.isBefore(plan.starts))edit.putLong(term(code,plan.starts),plan.limit);
            edit.commit();return;
        }
        LocalDate last;try{last=LocalDate.parse(prefs.getString("auto_last_"+code,today.toString()));}catch(java.time.format.DateTimeParseException invalid){last=today;}
        if(today.isBefore(last))return; // A backward clock must not rewrite a previously observed day.
        TreeMap<LocalDate,Long> terms=dates(prefs,"auto_term_"+code+"_");
        boolean changed=!saved.equals(signature(plan));
        for(LocalDate day=last.plusDays(1);!day.isAfter(today);day=day.plusDays(1)){
            String key="limit_"+code+"_"+day;if(prefs.contains(key))continue;
            Map.Entry<LocalDate,Long> current=terms.floorEntry(day);
            if(changed&&!day.equals(today)){if(current!=null&&current.getValue()>0)edit.putBoolean("auto_unknown_"+code+"_"+day,true);continue;}
            long limit=changed?(enabled&&valid(plan,day)?plan.limit:0):current==null?0:current.getValue();
            if(limit>0)edit.putLong(key,limit);
        }
        if(changed){
            // An interrupted/untracked settings write cannot justify using the current limit for missed days.
            clearFuture(prefs,edit,code,today);
            edit.putString("auto_signature_"+code,signature(plan)).putLong(term(code,today),enabled&&valid(plan,today)?plan.limit:0);
            if(enabled&&plan!=null&&today.isBefore(plan.starts))edit.putLong(term(code,plan.starts),plan.limit);
        }
        if(changed||today.isAfter(last))edit.putString("auto_last_"+code,today.toString()).commit();
    }
    /** Called after save/remove, following an observation of the old settings on this same date. */
    static synchronized void planChanged(Context context,String code,LocalDate today){
        SharedPreferences prefs=prefs(context);if(!prefs.contains("auto_signature_"+code))return;
        today=historyDate(prefs,code,today);
        DailyAllowance.Plan plan=DailySummary.plan(context,code);SharedPreferences.Editor edit=prefs.edit();clearFuture(prefs,edit,code,today);
        LocalDate next=today.plusDays(1);boolean enabled=enabled(context,code);
        edit.putString("auto_signature_"+code,signature(plan)).putLong(term(code,next),enabled&&valid(plan,next)?plan.limit:0);
        if(enabled&&plan!=null&&next.isBefore(plan.starts))edit.putLong(term(code,plan.starts),plan.limit);
        edit.commit();
    }
    static List<DailyPoints.Day> load(Context context,ExpenseDb db,String code,LocalDate today) {
        observe(context,code,today);
        SharedPreferences prefs=prefs(context);String prefix="limit_"+code+"_";
        TreeMap<LocalDate,Long> limits=new TreeMap<>();
        for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith(prefix)&&e.getValue() instanceof Long) {
            try{LocalDate day=LocalDate.parse(e.getKey().substring(prefix.length()));long limit=(Long)e.getValue();
                if(!day.isAfter(today)&&limit>0)limits.put(day,limit);
            }catch(java.time.format.DateTimeParseException ignored){}
        }
        List<DailyPoints.Day> result=new ArrayList<>();if(limits.isEmpty())return result;
        android.database.sqlite.SQLiteDatabase sql=db.getReadableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransactionNonExclusive();
        try{
        Map<LocalDate,Long> spending=new TreeMap<>();java.util.Set<LocalDate> review=new java.util.HashSet<>();
        Map<Long,Long> offsets=db.reimbursementOffsets();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(limits.firstKey(),today.plusDays(1))) {
            if(!code.equals(tx.currency))continue;
            LocalDate day=Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate();
            if(tx.needsReview)review.add(day);
            if(MoneyFlow.bucket(tx.kind,tx.category)==MoneyFlow.Bucket.LIVING)
                spending.merge(day,ExpenseDb.effectiveAmount(tx,offsets),Math::addExact);
        }
        for(Map.Entry<LocalDate,Long> e:limits.entrySet())result.add(new DailyPoints.Day(e.getKey(),today,e.getValue(),
                spending.getOrDefault(e.getKey(),0L),true,review.contains(e.getKey())));
        if(own)sql.setTransactionSuccessful();
        return result;
        }finally{if(own)sql.endTransaction();}
    }
    static long unknownDays(Context context,String code,LocalDate today){
        SharedPreferences prefs=prefs(context);TreeMap<LocalDate,Long> limits=dates(prefs,"limit_"+code+"_");long count=0;
        String since=prefs.getString("auto_since_"+code,null);
        if(since!=null&&!limits.isEmpty())try{
            LocalDate end=LocalDate.parse(since);if(end.isAfter(today))end=today;
            if(limits.firstKey().isBefore(end))count=java.time.temporal.ChronoUnit.DAYS.between(limits.firstKey(),end)-limits.subMap(limits.firstKey(),true,end,false).size();
        }catch(java.time.format.DateTimeParseException ignored){}
        String prefix="auto_unknown_"+code+"_";
        for(String key:prefs.getAll().keySet())if(key.startsWith(prefix))try{
            LocalDate day=LocalDate.parse(key.substring(prefix.length()));if(day.isBefore(today)&&!limits.containsKey(day))count++;
        }catch(java.time.format.DateTimeParseException ignored){}
        return count;
    }
    /** Compatibility for older fixture callers; closure is automatic and no confirmation is persisted. */
    static void confirm(Context context,ExpenseDb db,String code,LocalDate day,LocalDate today) {
        if(!day.isBefore(today))throw new IllegalArgumentException("Day still open");
        for(DailyPoints.Day item:load(context,db,code,today))if(item.date.equals(day)) {
            if(item.needsReview)throw new IllegalArgumentException("Review required");
            return;
        }
        throw new IllegalArgumentException("No observed daily limit");
    }
    private DailyPointsStore(){}
}
