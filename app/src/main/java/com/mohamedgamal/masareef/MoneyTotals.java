package com.mohamedgamal.masareef;

import java.util.LinkedHashMap;
import java.util.Map;

/** Day headers, the daily budget and the widget use MoneyFlow's buckets: own transfers never count as income/expense. */
final class MoneyTotals {
    static final class Totals {
        /** income = money in; expense = everything that left as spending; living = «صرف» only. */
        long income, expense, transfers, living;
        /** commitment = paid bills/installments/subscriptions; openTransfer = sent to people, purpose unset; cash = moved to cash. */
        long commitment, openTransfer, cash;

        /** The amount a list filter's rows add up to: the figure a filtered day header shows. */
        long of(String filter) {
            if (filter == null || filter.isEmpty()) return expense;
            if (MoneyFlow.SPENDING_FILTER.equals(filter)) return living + commitment;
            // The statement's «خرج»: spending, open transfers and cash withdrawals (MoneyFlow.isStatementOut).
            if (MoneyFlow.OUT_FILTER.equals(filter)) return expense + cash;
            if (MoneyFlow.OUTFLOW_FILTER.equals(filter)) return living + commitment + openTransfer;
            switch (MoneyFlow.Bucket.valueOf(filter)) {
                case LIVING: return living;
                case COMMITMENT: return commitment;
                case OPEN_TRANSFER: return openTransfer;
                case INFLOW: return income;
                case CASH_WITHDRAWAL: return cash;
                default: return transfers;
            }
        }
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
        else if(bucket==MoneyFlow.Bucket.CASH_WITHDRAWAL) t.cash=Math.addExact(t.cash,amount);
        else if("expense".equals(kind)) {
            // A cash withdrawal is money moved to cash, not spending; the same rule as MoneyFlow.isOutflow.
            t.expense=Math.addExact(t.expense,amount);
            if(bucket==MoneyFlow.Bucket.LIVING) t.living=Math.addExact(t.living,amount);
            else if(bucket==MoneyFlow.Bucket.COMMITMENT) t.commitment=Math.addExact(t.commitment,amount);
            else if(bucket==MoneyFlow.Bucket.OPEN_TRANSFER) t.openTransfer=Math.addExact(t.openTransfer,amount);
        }
    }
}
