package com.mohamedgamal.masareef;

import java.util.regex.Pattern;

/** Explicit cancellation is different from a decline, a future instruction, or a successful contactless warning. */
final class CaptureNotice {
    private static final Pattern CANCEL = Pattern.compile("(?is)(تم\\s*(?:الغاء|إلغاء)\\s*(?:ال)?(?:عملية\\s*)?(?:ال)?(?:تحويل|سداد|دفع)|(?:transfer|payment|transaction).{0,90}?(?:cancelled|canceled|reversed)|(?:cancelled|canceled|reversed).{0,90}?(?:transfer|payment|transaction))");
    private static final Pattern NOT_A_CANCELLATION = Pattern.compile("(?is)(?:\\b(?:will|may|can|could|might|not|cannot)\\b.{0,60}\\b(?:cancelled|canceled|reversed)\\b|(?:لم|لن|لا|سيتم|سوف|يمكن).{0,35}(?:الغاء|إلغاء))");
    final ExpenseParser.Parsed payment;
    private CaptureNotice(ExpenseParser.Parsed payment){this.payment=payment;}
    static CaptureNotice parse(String sender,String body){
        if(body==null||ExpenseParser.isVerificationMessage(body)||!CANCEL.matcher(body).find()||NOT_A_CANCELLATION.matcher(body).find())return null;
        String clean=body.replaceAll("(?i)(?:الغاء|إلغاء|cancelled|canceled|reversed)","");
        clean=clean.replaceAll("تم\\s+(?:ال)?(?:تحويل|سداد|دفع)","تم تحويل");
        return new CaptureNotice(ExpenseParser.parse(sender,clean));
    }
    static boolean cardRepayment(String body){
        return body!=null&&Pattern.compile("(?is)(?:من\\s*حساب|from.{0,30}account).{0,100}(?:الى|إلى|to).{0,25}(?:كارت|بطاق|card)").matcher(body).find();
    }
}
