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

    private static final String CREDIT_CARD_WORDS = "(?:\\p{L}*بطاق\\p{L}*\\s+(?:\\p{L}+\\s+){0,3}?ال[اإآ]?ئتماني\\p{L}*|بطاق\\p{L}*\\s+[اإ]ئتمان|كارت\\s+[اإ]ئتمان|credit\\s+card)";
    private static final Pattern CARD_DEPOSIT = Pattern.compile("(?is)(?:"
            + "(?:تم|تمت)\\s+(?:ايداع|إيداع|سداد|تسديد|اضافة|إضافة)(?!\\p{L}).{0,120}?" + CREDIT_CARD_WORDS + "|"
            + CREDIT_CARD_WORDS + ".{0,80}?\\bpayment\\b.{0,60}?\\b(?:received|credited|posted)\\b|"
            + "\\bpayment\\b.{0,60}?\\b(?:received|credited)\\b.{0,60}?" + CREDIT_CARD_WORDS + ")");
    private static final Pattern RETURN_WORDS = Pattern.compile("(?is)(?<!\\p{L})(?:رد|استرداد|إسترداد|عكس|كاش\\s*باك)(?!\\p{L})|\\b(?:refund(?:ed)?|revers(?:al|ed)|cash\\s*back)\\b");

    /**
     * Money paid INTO a credit card ("تم إيداع EGP 3218 بالبطاقة الائتمانية …", "Your credit card payment … received"):
     * the user's own money moving onto the card, never income. A refund or reversal on the card is not a repayment.
     * Shape-based, no bank template (owner rule, 29 Oct 2026).
     */
    static boolean cardDeposit(String body){
        return body!=null&&CARD_DEPOSIT.matcher(body).find()&&!RETURN_WORDS.matcher(body).find();
    }
}
