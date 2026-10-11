package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** All daily surfaces obtain one dated snapshot using the phone's local timezone.
 * The daily budget measures day-to-day spending only (MoneyFlow LIVING): installments,
 * bills and transfers to people have their own lines and would otherwise blow the daily limit. */
final class DailySummary {
    static final String SETTINGS="daily_budget";
    final Instant updated;
    final ZoneId zone;
    final LocalDate date;
    final Map<String,DailyAllowance.Result> currencies=new LinkedHashMap<>();
    private DailySummary(Instant updated,ZoneId zone) {this.updated=updated;this.zone=zone;date=updated.atZone(zone).toLocalDate();}
    static DailySummary load(Context context,ExpenseDb db) {
        DailySummary snapshot=new DailySummary(Instant.now(),ZoneId.systemDefault());
        MoneyTotals totals=new MoneyTotals();
        java.util.Map<Long,Long> offsets=db.reimbursementOffsets(); // purchases repaid by someone else do not use today's limit
        for(ExpenseDb.Transaction t:db.transactionsBetween(snapshot.date,snapshot.date.plusDays(1),snapshot.zone))
            totals.add(t.currency,t.kind,t.category,ExpenseDb.effectiveAmount(t,offsets));
        SharedPreferences prefs=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        java.util.Set<String> codes=new java.util.TreeSet<>(totals.currencies.keySet());
        codes.addAll(prefs.getStringSet("currencies",java.util.Collections.emptySet()));
        codes.add(context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getString("currency","EGP"));
        for(String currency:codes) {
            DailyAllowance.Plan plan=plan(context,currency);
            MoneyTotals.Totals t=totals.currencies.get(currency);
            snapshot.currencies.put(currency,DailyAllowance.calculate(plan,snapshot.date,t==null?0:t.living,t==null?0:t.income,prefs.getInt("salary_"+currency,0)));
        }
        return snapshot;
    }
    static DailyAllowance.Plan plan(Context context,String currency) {
        SharedPreferences prefs=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        long amount=prefs.getLong("amount_"+currency,0);
        if(amount<=0)return null;
        try {return new DailyAllowance.Plan(currency,amount,LocalDate.parse(prefs.getString("start_"+currency,"")),prefs.getInt("salary_"+currency,0));}
        catch(RuntimeException invalid) {return null;}
    }
    static void save(Context context,DailyAllowance.Plan plan) {
        SharedPreferences prefs=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        java.util.Set<String> codes=new java.util.HashSet<>(prefs.getStringSet("currencies",java.util.Collections.emptySet()));codes.add(plan.currency);
        prefs.edit().putStringSet("currencies",codes).putLong("amount_"+plan.currency,plan.limit)
                .putString("start_"+plan.currency,plan.starts.toString()).putInt("salary_"+plan.currency,plan.salaryDay).apply();
    }
    static void remove(Context context,String currency) {
        SharedPreferences prefs=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        prefs.edit().remove("amount_"+currency).remove("start_"+currency).apply();
    }
    static void saveSalary(Context context,String currency,long monthlyAmount,int day) {
        if(monthlyAmount<0||day<0||day>DailyAllowance.MONTH_END)throw new IllegalArgumentException("Invalid salary");
        SharedPreferences prefs=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        java.util.Set<String> codes=new java.util.HashSet<>(prefs.getStringSet("currencies",java.util.Collections.emptySet()));codes.add(currency);
        prefs.edit().putStringSet("currencies",codes).putLong("monthly_salary_"+currency,monthlyAmount).putInt("salary_"+currency,day).apply();
    }
    static long monthlySalary(Context context,String currency) {return context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE).getLong("monthly_salary_"+currency,0);}
    String remaining(String currency,boolean hidden) {
        DailyAllowance.Result result=currencies.get(currency);
        if(result==null||!result.valid)return "حدد ميزانيتك اليومية";
        if(hidden)return result.status();
        return result.remaining<0?"تجاوزت اليوم بـ "+MainActivity.money(-result.remaining,currency):"متبقي النهارده: "+MainActivity.money(result.remaining,currency);
    }
}
