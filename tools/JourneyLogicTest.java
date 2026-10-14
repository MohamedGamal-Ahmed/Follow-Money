package com.mohamedgamal.masareef;

import java.util.Arrays;
import java.util.Objects;

public final class JourneyLogicTest {
    private static int checked;
    private static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);checked++;}
    public static void main(String[] args) {
        TransactionJourney path=new TransactionJourney();eq(null,path.back());
        path.visit(1,2);path.visit(2,3);path.visit(3,3);
        eq(true,Arrays.equals(new long[]{1,2},path.snapshot()));
        TransactionJourney restored=new TransactionJourney();restored.restore(path.snapshot());
        eq(2L,restored.back());eq(1L,restored.back());eq(null,restored.back());
        path.clear();eq(null,path.back());restored.restore(null);eq(null,restored.back());
        System.out.println("Journey logic checks passed: "+checked);
    }
}
