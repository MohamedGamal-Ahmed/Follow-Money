package com.mohamedgamal.masareef;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Best-effort masking for a draft the user must review, never for changing stored evidence. */
final class Redaction {
    private static final String NUMBER = "[+-]?[0-9]+(?:[.,٬٫][0-9]+)*";
    private static final String CURRENCY = "(?:EGP|USD|EUR|GBP|L\\.?E\\.?|ج\\.?م\\.?|جنيه|دولار|يورو)";
    private static final Pattern BEFORE = Pattern.compile("(?iu)("+CURRENCY+")\\s*("+NUMBER+")");
    private static final Pattern AFTER = Pattern.compile("(?iu)("+NUMBER+")\\s*("+CURRENCY+")");
    private static final Pattern AMOUNT = Pattern.compile("(?iu)((?:balance|amount|رصيد|مبلغ)\\s*[:=]?\\s*)"+NUMBER);
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:[.,٬٫][0-9]+)+");
    private static final Pattern NAME = Pattern.compile(
            "(?iu)((?<![\\p{L}])(?:to|from)\\s+|(?<![\\p{L}])(?:من|إلى|الى|لـ)\\s*)"
            +"[\\p{L}][\\p{L}\\p{M} .’'\\-]*?(?=\\s+(?:on|at|ref|reference|with|amount|balance|to|from|بتاريخ|يوم|بمبلغ|رصيد|من|إلى|الى)\\b|[,;:،\\n0-9]|$)");
    private Redaction() {}

    static String text(String raw) {
        if (raw == null) return "";
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int i=0;i<raw.length();i++) {
            int digit=Character.digit(raw.charAt(i),10);
            normalized.append(digit<0?raw.charAt(i):(char)('0'+digit));
        }
        String value=BEFORE.matcher(normalized).replaceAll("$1 0.00");
        value=AFTER.matcher(value).replaceAll("0.00 $2");
        value=AMOUNT.matcher(value).replaceAll("$1 0.00");
        value=NAME.matcher(value).replaceAll("$1NAME ");
        value=DECIMAL.matcher(value).replaceAll("0.00");
        return value.replaceAll("[0-9]{3,}","###").trim();
    }

    /** Exception messages may contain an entire SMS or SQL value, so omit them completely. */
    static String crashTrace(Throwable error) {
        StringBuilder out=new StringBuilder();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int causes=0;error!=null&&causes<8&&seen.add(error);causes++,error=error.getCause()) {
            out.append(causes==0?"":"Caused by: ").append(error.getClass().getName()).append(" [message omitted]\n");
            StackTraceElement[] frames=error.getStackTrace();
            for(int i=0;i<Math.min(40,frames.length);i++)out.append("  at ").append(frames[i]).append('\n');
        }
        return out.toString();
    }
}
