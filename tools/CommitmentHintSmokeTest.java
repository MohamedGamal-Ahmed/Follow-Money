package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.ZoneId;

public final class CommitmentHintSmokeTest {
    private static int checks;
    private static final long AT=LocalDate.of(2026,9,15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    private static CommitmentHint expect(String sender,String body,long amount,String kind,String currency) {
        CommitmentHint h=CommitmentHint.parse(sender,body,AT);
        if(h==null || h.amount!=amount || !h.kind.equals(kind) || !h.currency.equals(currency))
            throw new AssertionError("Unexpected hint: "+body);
        checks++;return h;
    }
    private static void skip(String body) {
        if(CommitmentHint.parse("AnyProvider",body,AT)!=null) throw new AssertionError("Unexpected proposal: "+body);
        checks++;
    }
    public static void main(String[] args) {
        CommitmentHint card=expect("AnyBank","Your card ****4321 statement is issued with total EGP 6642.76, minimum due is EGP 5478.72, due before 26-09-2026",547872,"bill","EGP");
        if(!card.dateExact || !card.estimatedDate.equals(LocalDate.of(2026,9,26)) || card.count!=null) throw new AssertionError("Statement date/count");
        expect("OtherBank","Your card ****4321 statement is issued with total 7000 EGP, minimum due is 4900.50 EGP, due before 26/10/2026",490050,"bill","EGP");
        expect("UnknownFinance","نذكركم باستحقاق القسط الشهري بقيمة ٤٩٠٠٫٥٠ جنيه، يرجى تجاهل الرسالة في حالة السداد",490050,"installment","EGP");
        expect("ForeignProvider","Reminder: monthly installment amount due USD 45.70, due on 2026-10-03",4570,"installment","USD");
        expect("we-Landline","نذكرك بدفع 5.00 ج.م. رسوم استخدام الراوتر",500,"bill","EGP");
        expect("MyFawry","فاتورة Orange DSL جاهزة للدفع بمبلغ 300.5 ج.م.",30050,"bill","EGP");
        expect("Valu","تلقينا طلب سداد القسط الشهري بقيمة 1378 سيتم تحديث التطبيق في 48 ساعة",137800,"installment","EGP");
        CommitmentHint noAmount=expect("Valu","عميلنا العزيز يرجى سداد قسط 1 أكتوبر برجاء تجاهل الرسالة عند السداد",0,"installment","EGP");
        if(!noAmount.dateExact || !noAmount.estimatedDate.equals(LocalDate.of(2026,10,1))) throw new AssertionError("Valu due date");
        expect("AnotherFinance","نذكرك بسداد القسط الشهري، يرجى مراجعة التطبيق للمبلغ",0,"installment","EGP");
        CommitmentHint amended=expect("TRU","تم تعديل خطة أقساطك بنجاح لعملية الشراء رقم 123456/987654. نؤكد استلام دفعتك بقيمة 14000.00 ج.م. وأصبح قسطك الآن بقيمة 1593 ج.م شهريًا لمدة 17 شهور.",159300,"installment","EGP");
        if(amended.count!=17 || amended.merchant.contains("إجمالي")) throw new AssertionError("Amended plan treated as a total");
        expect("7030","Dear customer, you have successfully purchased Microsoft Apps - Basic Subscription - 1 month (Microsoft OneDrive) from Google Play Store and 39 Egyptian pound has been deducted from your credit",3900,"subscription","EGP");
        CommitmentHint arabic=expect("ET-Google","تم شراء G1SK100M - 100 GB (Google One) بنجاح من خلال Google Play Store بقيمة 94.99 جنيه من رصيدكم. قد تُطبق ضرائب إضافية",9499,"subscription","EGP");
        if(!arabic.merchant.equals("Google One")) throw new AssertionError("Arabic subscription merchant");
        expect("AnyBank","تم خصم مبلغ EGP 89.99 فيGoogle One بتاريخ 15/09/2026",8999,"subscription","EGP");
        expect("Billing","You paid USD 20.00 to Netflix for annual subscription",2000,"subscription","USD");
        skip("قسط عضويتك بقيمة 4900 جنيه، العرض ساري حتى نهاية الشهر");
        skip("Your OTP is 123456 for installment payment EGP 4900");
        skip("Your installment payment of EGP 4900 failed");
        skip("Your card available limit EGP 10000");
        skip("نذكركم بموعدكم مع دكتور يوم 5 سبتمبر، موعد العيادة 8 مساءً");
        skip("بنفكرك انك تقدر تكشف اونلاين، من خلال تطبيق الخدمة");
        skip("تم سداد جميع الأقساط لعملية الشراء رقم 123456/789012");
        if(ExpenseParser.parse("AnyBank","يرجى سداد مبلغ 4900 جنيه قبل 26/10/2026")!=null) throw new AssertionError("Reminder became an expense");
        System.out.println("Commitment hint checks passed: "+checks);
    }
}
