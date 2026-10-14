package com.mohamedgamal.masareef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

public final class DailyPointsTest {
    private static int checked;
    private static void eq(Object expected,Object actual){if(!java.util.Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);checked++;}
    public static void main(String[] args) {
        LocalDate today=LocalDate.of(2026,10,13),yesterday=today.minusDays(1);
        DailyPoints.Day provisional=new DailyPoints.Day(today,today,25000,14900,true,false);
        eq("+10",DailyPoints.format(provisional.points()));eq(false,provisional.finalized);
        eq(BigDecimal.ZERO,DailyPoints.total(Arrays.asList(provisional)));
        DailyPoints.Day under=new DailyPoints.Day(yesterday,today,25000,14900,true,false);
        DailyPoints.Day over=new DailyPoints.Day(yesterday.minusDays(1),today,25000,44400,true,false);
        eq("-19",DailyPoints.format(over.points()));
        eq("-9",DailyPoints.format(DailyPoints.total(Arrays.asList(under,over,provisional))));
        eq("0",DailyPoints.format(new DailyPoints.Day(yesterday,today,25000,25000,true,false).points()));
        DailyPoints.Day missing=new DailyPoints.Day(yesterday,today,25000,0,false,false);
        DailyPoints.Day review=new DailyPoints.Day(yesterday,today,25000,0,true,true);
        eq(BigDecimal.ZERO,DailyPoints.total(Arrays.asList(missing,review)));
        eq("+25",DailyPoints.format(new DailyPoints.Day(yesterday,today,25000,0,true,false).points()));
        eq("0",DailyPoints.format(new DailyPoints.Day(yesterday,today,25000,24999,true,false).points()));
        eq("+11",DailyPoints.format(new DailyPoints.Day(today,today,25000,14145,false,false).points())); // 108.55 remaining
        for(long[] boundary:new long[][]{{499,0},{500,1},{501,1},{-499,0},{-500,-1},{-501,-1}}) {
            DailyPoints.Day day=new DailyPoints.Day(yesterday,today,25000,25000-boundary[0],true,false);
            eq(BigDecimal.valueOf(boundary[1]),day.points());
        }
        DailyPoints.Day first=new DailyPoints.Day(yesterday,today,25000,24400,true,false);
        DailyPoints.Day second=new DailyPoints.Day(yesterday.minusDays(1),today,25000,24400,true,false);
        eq("+2",DailyPoints.format(DailyPoints.total(Arrays.asList(first,second)))); // Sum visible daily awards, not raw fractions
        for(long[] invalid:new long[][]{{0,0},{-1,0},{25000,-1}})try{
            new DailyPoints.Day(yesterday,today,invalid[0],invalid[1],true,false);throw new AssertionError("Invalid points accepted");
        }catch(IllegalArgumentException expected){checked++;}
        try{new DailyPoints.Day(today.plusDays(1),today,25000,0,true,false);throw new AssertionError("Future accepted");}catch(IllegalArgumentException expected){checked++;}
        System.out.println("Daily points checks passed: "+checked);
    }
}
