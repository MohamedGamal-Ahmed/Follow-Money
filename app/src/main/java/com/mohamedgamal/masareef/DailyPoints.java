package com.mohamedgamal.masareef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Whole daily points: ten currency units per point, rounded once per day with a known dated limit. */
final class DailyPoints {
    static final class Day {
        final LocalDate date;
        final long limit,spending,difference;
        final boolean complete,needsReview,finalized;
        Day(LocalDate date,LocalDate today,long limit,long spending,boolean complete,boolean needsReview) {
            if(date==null||today==null||date.isAfter(today)||limit<=0||spending<0)throw new IllegalArgumentException("Invalid points day");
            this.date=date;this.limit=limit;this.spending=spending;this.complete=complete;this.needsReview=needsReview;
            difference=Math.subtractExact(limit,spending);
            finalized=date.isBefore(today)&&complete&&!needsReview;
        }
        BigDecimal points(){return BigDecimal.valueOf(difference,3).setScale(0,RoundingMode.HALF_UP);}
        String status(LocalDate today){return date.equals(today)?"مؤقتة لحد نهاية اليوم":needsReview?"راجع العمليات المحتاجة مراجعة":
                complete?"معتمدة تلقائيًا · تتحدث لو البيانات اتصححت":"الحد التاريخي غير معروف";}
    }
    static BigDecimal total(Iterable<Day> days){BigDecimal total=BigDecimal.ZERO;for(Day day:days)if(day.finalized)total=total.add(day.points());return total;}
    static String format(BigDecimal points){return (points.signum()>0?"+":"")+points.stripTrailingZeros().toPlainString();}
    private DailyPoints(){}
}
