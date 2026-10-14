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

/** Preferences store only opt-in, daily budget anchors and coverage; scores derive from the ledger. */
final class DailyPointsStore {
    static final String SETTINGS="daily_points";
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);}
    static boolean enabled(Context context,String code){return prefs(context).getBoolean("enabled_"+code,false);}
    static void enable(Context context,String code,LocalDate today) {
        DailyAllowance.Plan plan=DailySummary.plan(context,code);
        if(plan==null||today.isBefore(plan.starts))throw new IllegalArgumentException("Daily limit required");
        prefs(context).edit().putBoolean("enabled_"+code,true).commit();observe(context,code,today);
    }
    static void disable(Context context,String code){prefs(context).edit().putBoolean("enabled_"+code,false).apply();}
    static void observeToday(Context context) {
        LocalDate today=LocalDate.now();
        for(String code:new String[]{"EGP","USD","EUR","GBP"})observe(context,code,today);
    }
    static synchronized void observe(Context context,String code,LocalDate today) {
        if(!enabled(context,code))return;
        DailyAllowance.Plan plan=DailySummary.plan(context,code);
        if(plan==null||today.isBefore(plan.starts))return;
        SharedPreferences prefs=prefs(context);String key="limit_"+code+"_"+today;
        // First observed limit stays fixed for this day; a settings edit never rewrites yesterday's score.
        if(!prefs.contains(key))prefs.edit().putLong(key,plan.limit).commit();
    }
    static List<DailyPoints.Day> load(Context context,ExpenseDb db,String code,LocalDate today) {
        SharedPreferences prefs=prefs(context);String prefix="limit_"+code+"_";
        TreeMap<LocalDate,Long> limits=new TreeMap<>();
        for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith(prefix)&&e.getValue() instanceof Long) {
            try{LocalDate day=LocalDate.parse(e.getKey().substring(prefix.length()));long limit=(Long)e.getValue();
                if(!day.isAfter(today)&&limit>0)limits.put(day,limit);
            }catch(java.time.format.DateTimeParseException ignored){}
        }
        List<DailyPoints.Day> result=new ArrayList<>();if(limits.isEmpty())return result;
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
                spending.getOrDefault(e.getKey(),0L),prefs.getBoolean("complete_"+code+"_"+e.getKey(),false),review.contains(e.getKey())));
        return result;
    }
    static void confirm(Context context,ExpenseDb db,String code,LocalDate day,LocalDate today) {
        if(!day.isBefore(today))throw new IllegalArgumentException("Day still open");
        for(DailyPoints.Day item:load(context,db,code,today))if(item.date.equals(day)) {
            if(item.needsReview)throw new IllegalArgumentException("Review required");
            prefs(context).edit().putBoolean("complete_"+code+"_"+day,true).commit();return;
        }
        throw new IllegalArgumentException("No observed daily limit");
    }
    static void revoke(Context context,String code,LocalDate day){prefs(context).edit().remove("complete_"+code+"_"+day).apply();}
    private DailyPointsStore(){}
}
