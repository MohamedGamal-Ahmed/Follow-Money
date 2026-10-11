package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Fixed daily limits in minor units. Income and salary dates do not increase the limit. */
final class DailyAllowance {
    static final int MONTH_END=32;
    static final class Plan {
        final String currency;
        final long limit;
        final LocalDate starts;
        final int salaryDay; // 0 unset, 1–31 fixed day (clamped), 32 last day of each month.
        Plan(String currency,long limit,LocalDate starts,int salaryDay) {
            if(currency==null||currency.isBlank()||limit<=0||starts==null||salaryDay<0||salaryDay>MONTH_END)
                throw new IllegalArgumentException("Invalid daily plan");
            this.currency=currency;this.limit=limit;this.starts=starts;this.salaryDay=salaryDay;
        }
    }
    static final class Result {
        final Plan plan;
        final LocalDate date, nextSalary, cycleStart, cycleEnd;
        final long expense, income, remaining;
        final boolean valid;
        final int progress, daysToSalary;
        Result(Plan plan,LocalDate today,long expense,long income,int salaryDay) {
            if(expense<0||income<0)throw new IllegalArgumentException("Negative totals");
            this.plan=plan;this.date=today;this.expense=expense;this.income=income;
            valid=plan!=null&&!today.isBefore(plan.starts);
            remaining=valid?Math.subtractExact(plan.limit,expense):0;
            progress=valid?(int)Math.min(100,Math.floor(100.0*((double)expense/plan.limit))):0;
            if(salaryDay>0&&salaryDay<=MONTH_END) {
                LocalDate candidate=day(YearMonth.from(today),salaryDay);
                nextSalary=candidate.isBefore(today)?day(YearMonth.from(today).plusMonths(1),salaryDay):candidate;
                daysToSalary=(int)ChronoUnit.DAYS.between(today,nextSalary);
                LocalDate[] bounds=BudgetPeriod.bounds("monthly",LocalDate.of(2000,1,Math.min(31,salaryDay)),30,today);
                cycleStart=bounds[0];cycleEnd=bounds[1];
            } else {nextSalary=null;cycleStart=null;cycleEnd=null;daysToSalary=-1;}
        }
        String status() {return !valid?"حدد ميزانيتك اليومية":remaining<0?"تجاوزت حد اليوم":remaining==0?"وصلت لحد اليوم":"داخل ميزانية اليوم";}
    }
    private static LocalDate day(YearMonth month,int day) {return day==MONTH_END?month.atEndOfMonth():month.atDay(Math.min(day,month.lengthOfMonth()));}
    static Result calculate(Plan plan,LocalDate today,long expense,long income) {return new Result(plan,today,expense,income,plan==null?0:plan.salaryDay);}
    static Result calculate(Plan plan,LocalDate today,long expense,long income,int salaryDay) {return new Result(plan,today,expense,income,salaryDay);}
}
