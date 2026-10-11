package com.mohamedgamal.masareef;

import java.util.LinkedHashMap;
import java.util.Map;

/** Day headers, the daily budget and the widget use MoneyFlow's buckets: own transfers never count as income/expense. */
final class MoneyTotals {
    static final class Totals {
        /** income = money in; expense = everything that left (living + commitments + open transfers); living = «صرف» only. */
        long income, expense, transfers, living;
    }
    final Map<String,Totals> currencies=new LinkedHashMap<>();

    /** Without a category every expense is treated as day-to-day spending (legacy callers). */
    void add(String currency,String kind,long amount) { add(currency,kind,null,amount); }

    void add(String currency,String kind,String category,long amount) {
        if(amount<0) throw new IllegalArgumentException("Negative amount");
        Totals t=currencies.computeIfAbsent(currency,ignored->new Totals());
        MoneyFlow.Bucket bucket=MoneyFlow.bucket(kind,category);
        if(bucket==MoneyFlow.Bucket.INFLOW) t.income=Math.addExact(t.income,amount);
        else if(bucket==MoneyFlow.Bucket.INTERNAL) t.transfers=Math.addExact(t.transfers,amount);
        else if("expense".equals(kind)) {
            t.expense=Math.addExact(t.expense,amount);
            if(bucket==MoneyFlow.Bucket.LIVING) t.living=Math.addExact(t.living,amount);
        }
    }
}
