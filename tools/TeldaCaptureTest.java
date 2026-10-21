package com.mohamedgamal.masareef;

/** Invented amounts/identities: price announcements must not be confused with completed payments. */
public final class TeldaCaptureTest {
    private static int checks;
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        String notice="عميلنا العزيز، يرجي العلم بأنه اعتباراً من 27 نوفمبر 2026، سوف يتم تحديث سعر اشتراك Plus الي 49.00 ج.م. مع العلم انه تم اضافة مزايا جديدة للاشتراك. يمكنك الاطلاع علي المزايا الجديدة";
        for(String body:new String[]{notice,notice.replace("49.00","0.00"),notice.replace("اضافة مزايا","إضافة مميزات"),
                "اعتبارا من الشهر القادم سيتم تعديل رسوم الخدمة إلى 75 جنيه، تم إضافة خصائص جديدة",
                "Your Plus subscription price will change to EGP 49 next month. New benefits have been added."}){
            check(ExpenseParser.parse("Telda",body)==null,"Price notice became an expense");
            check(ExpenseParser.parseIncome("Telda",body)==null,"Price notice became income");
            check(ExpenseParser.parseTransfer(body)==null,"Price notice became a transfer");
            check(ExpenseParser.isPromotion(body),"Price notice is not identifiable for evidence-based review");
        }
        String purchase="Telda تم دفع 15 ج.م. في \"Kashier Butlers\"\nرصيدك: 31.5 ج.م.";
        ExpenseParser.Parsed paid=ExpenseParser.parse("Telda",purchase);
        check(paid!=null&&paid.amountMinor==1500&&paid.merchant.contains("Butlers"),"Completed Telda notification lost");
        check(ExpenseParser.parseIncome("Telda",purchase)==null,"Telda purchase became income");
        ExpenseParser.Parsed credit=ExpenseParser.parseIncome("Telda","تم إضافة 90 جنيه لحسابك، يمكنك الاطلاع على تحديث سعر اشتراك Plus إلى 49 جنيه");
        check(credit!=null&&credit.amountMinor==9000,"Real credit lost because of a price-announcement footer");
        ExpenseParser.Parsed debit=ExpenseParser.parse("Telda","تم خصم 90 جنيه من حسابك، سيتم تعديل رسوم الخدمة إلى 49 جنيه الشهر القادم");
        check(debit!=null&&debit.amountMinor==9000,"Real debit lost because of a price-announcement footer");
        System.out.println("Telda capture logic checks passed: "+checks);
    }
}
