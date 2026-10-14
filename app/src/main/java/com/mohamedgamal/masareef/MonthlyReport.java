package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Closed calendar months, using the same effective amounts and buckets as the analytics pages. */
final class MonthlyReport {
    final YearMonth month;
    final Map<String,Currency> currencies=new TreeMap<>();
    static final class Currency {
        final MoneyFlow.Summary current, previous;
        final Map<String,Long> merchants=new LinkedHashMap<>();
        String currentFingerprint, previousFingerprint;
        long smallTotal; int smallCount;
        Currency(MoneyFlow.Summary current,MoneyFlow.Summary previous){this.current=current;this.previous=previous;}
        Long livingChangePercent(boolean coverageConfirmed) {
            long before=previous.of(MoneyFlow.Bucket.LIVING);
            if(!coverageConfirmed || before<=0 || current.isEmpty())return null;
            return Math.round(100.0*(current.of(MoneyFlow.Bucket.LIVING)-before)/before);
        }
        List<Map.Entry<String,Long>> topMerchants() {
            List<Map.Entry<String,Long>> top=new ArrayList<>(merchants.entrySet());
            top.sort(Comparator.<Map.Entry<String,Long>>comparingLong(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey));
            return top.subList(0,Math.min(3,top.size()));
        }
    }
    private MonthlyReport(YearMonth month){this.month=month;}
    static MonthlyReport build(List<MoneyFlow.Item> history,YearMonth month,ZoneId zone) {
        MonthlyReport report=new MonthlyReport(month);
        LocalDate[] period={month.atDay(1),month.plusMonths(1).atDay(1)};
        Map<String,MoneyFlow.Summary> current=MoneyFlow.summarize(history,period,zone);
        Map<String,MoneyFlow.Summary> previous=MoneyFlow.summarize(history,new LocalDate[]{month.minusMonths(1).atDay(1),period[0]},zone);
        for(Map.Entry<String,MoneyFlow.Summary> entry:current.entrySet()) {
            String code=entry.getKey();Currency data=new Currency(entry.getValue(),previous.getOrDefault(code,new MoneyFlow.Summary(code)));
            report.currencies.put(code,data);
            data.currentFingerprint=fingerprint(history,month,code,zone);
            data.previousFingerprint=fingerprint(history,month.minusMonths(1),code,zone);
        }
        for(MoneyFlow.Item item:history) {
            if(item.bucket!=MoneyFlow.Bucket.LIVING || !month.equals(YearMonth.from(Instant.ofEpochMilli(item.when).atZone(zone))))continue;
            Currency data=report.currencies.get(item.currency);if(data==null)continue;
            data.merchants.merge(MerchantClassifier.displayName(item.merchant),item.amountMinor,Long::sum);
            if("EGP".equals(item.currency) && item.amountMinor<=Insights.SMALL_PURCHASE_MINOR){data.smallCount++;data.smallTotal+=item.amountMinor;}
        }
        return report;
    }
    private static String fingerprint(List<MoneyFlow.Item> history,YearMonth month,String currency,ZoneId zone) {
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            List<MoneyFlow.Item> rows=new ArrayList<>();
            for(MoneyFlow.Item item:history)if(currency.equals(item.currency) && month.equals(YearMonth.from(Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate())))rows.add(item);
            rows.sort(Comparator.comparingLong(item->item.id));
            for(MoneyFlow.Item item:rows)for(String value:new String[]{Long.toString(item.id),Long.toString(item.when),Long.toString(item.amountMinor),item.currency,item.kind,item.category,item.merchant}) {
                byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static boolean notificationDue(LocalDateTime now,YearMonth started,YearMonth notified,YearMonth seen) {
        YearMonth target=YearMonth.from(now).minusMonths(1);
        return !target.isBefore(started) && (notified==null || notified.isBefore(target)) &&
                (seen==null || seen.isBefore(target)) && (now.getDayOfMonth()>1 || now.getHour()>=9);
    }
    static LocalDateTime nextCheck(LocalDateTime now) {
        LocalDateTime first=YearMonth.from(now).atDay(1).atTime(9,0);
        return first.isAfter(now)?first:first.plusMonths(1);
    }
    static YearMonth parseMonth(String value,YearMonth fallback) {
        try{return value==null?fallback:YearMonth.parse(value);}catch(java.time.DateTimeException e){return fallback;}
    }
}
