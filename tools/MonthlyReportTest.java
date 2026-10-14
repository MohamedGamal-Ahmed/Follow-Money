package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class MonthlyReportTest {
    private static int checked;
    private static final ZoneId ZONE=ZoneId.of("Africa/Cairo");
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checked++;}
    private static MoneyFlow.Item row(long id,String date,long amount,String currency,String kind,String category,String merchant) {
        return new MoneyFlow.Item(id,LocalDate.parse(date).atStartOfDay(ZONE).toInstant().toEpochMilli(),amount,currency,kind,category,merchant);
    }
    public static void main(String[] args) {
        YearMonth oct=YearMonth.of(2026,10);List<MoneyFlow.Item> rows=new ArrayList<>();
        rows.add(row(1,"2026-09-01",40000,"EGP","expense","بقالة","Carrefour"));
        rows.add(row(2,"2026-10-01",9000,"EGP","expense","مقاهي","KASHIER*ESPRESSO LAB"));
        rows.add(row(3,"2026-10-31",11000,"EGP","expense","مقاهي","Espresso Lab"));
        rows.add(row(4,"2026-10-07",50000,"EGP","expense","أقساط","QA Plan"));
        rows.add(row(5,"2026-10-08",15000,"EGP","expense","تحويلات","QA friend"));
        rows.add(row(6,"2026-10-08",120000,"EGP","income","دخل","QA incoming"));
        rows.add(row(7,"2026-10-08",900000,"EGP","transfer","تحويلات","QA internal"));
        rows.add(row(8,"2026-10-09",2500,"USD","expense","اشتراكات","QA USD"));
        rows.add(row(9,"2026-11-01",999999,"EGP","expense","بقالة","Not October"));
        MonthlyReport report=MonthlyReport.build(rows,oct,ZONE);MonthlyReport.Currency egp=report.currencies.get("EGP");
        check(report.currencies.size()==2,"currencies separated");
        check(egp.current.totalOut()==85000,"closed month out excludes own transfer and next month");
        check(egp.current.of(MoneyFlow.Bucket.INFLOW)==120000,"recorded inflow stays separate");
        check(egp.current.of(MoneyFlow.Bucket.INTERNAL)==900000,"own transfer recorded once");
        check(egp.current.of(MoneyFlow.Bucket.LIVING)==20000,"October includes its first and last day");
        check(egp.smallCount==1 && egp.smallTotal==9000,"explicit 100 EGP small payment threshold");
        check(egp.topMerchants().size()==1 && egp.topMerchants().get(0).getValue()==20000,"merchant aliases combined");
        check(egp.livingChangePercent(false)==null,"no misleading comparison without coverage confirmation");
        check(egp.livingChangePercent(true)==-50,"confirmed same-currency living comparison");
        check(report.currencies.get("USD").livingChangePercent(true)==null,"zero denominator suppressed");
        String fingerprint=egp.currentFingerprint;java.util.Collections.reverse(rows);
        check(fingerprint.equals(MonthlyReport.build(rows,oct,ZONE).currencies.get("EGP").currentFingerprint),"row ordering does not invalidate confirmation");
        rows.add(row(10,"2026-10-09",1000,"EGP","expense","بقالة","Added later"));
        check(!fingerprint.equals(MonthlyReport.build(rows,oct,ZONE).currencies.get("EGP").currentFingerprint),"later import invalidates coverage confirmation");
        check(MonthlyReport.build(rows,YearMonth.of(2025,10),ZONE).currencies.isEmpty(),"empty month stays empty");
        List<MoneyFlow.Item> leap=java.util.Arrays.asList(row(11,"2024-02-29",100,"EGP","expense","بقالة","Leap"),row(12,"2024-03-01",900,"EGP","expense","بقالة","March"));
        check(MonthlyReport.build(leap,YearMonth.of(2024,2),ZONE).currencies.get("EGP").current.totalOut()==100,"leap February bounds");
        check(!MonthlyReport.notificationDue(LocalDateTime.of(2026,11,1,8,59),oct,null,null),"no midnight or early alert");
        check(MonthlyReport.notificationDue(LocalDateTime.of(2026,11,1,9,0),oct,null,null),"November 1 targets October");
        check(MonthlyReport.notificationDue(LocalDateTime.of(2026,11,3,8,0),oct,null,null),"catch up after phone was off");
        check(!MonthlyReport.notificationDue(LocalDateTime.of(2026,11,3,9,0),oct,oct,null),"no repeated month notification");
        check(!MonthlyReport.notificationDue(LocalDateTime.of(2026,11,3,9,0),oct,null,oct),"already opened summary stays quiet");
        check(!MonthlyReport.notificationDue(LocalDateTime.of(2026,10,12,12,0),oct,null,null),"first install does not spam old months");
        check(!MonthlyReport.notificationDue(LocalDateTime.of(2026,10,12,12,0),oct,YearMonth.of(2026,11),null),"clock rollback does not replay alerts");
        check(MonthlyReport.nextCheck(LocalDateTime.of(2026,12,1,8,0)).equals(LocalDateTime.of(2026,12,1,9,0)),"today's due time retained");
        check(MonthlyReport.nextCheck(LocalDateTime.of(2026,12,1,9,0)).equals(LocalDateTime.of(2027,1,1,9,0)),"year rollover schedule");
        check(MonthlyReport.parseMonth("bad",oct).equals(oct),"invalid deep link safe fallback");
        check(NavigationState.destination(15,6)==4,"monthly page keeps Analytics selected");
        System.out.println("Monthly report checks passed: "+checked);
    }
}
