package com.mohamedgamal.masareef;
public final class CaptureNoticeTest {
    private static void ok(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        for(String body:new String[]{"تم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 1111", "تم إلغاء عملية تحويل بمبلغ 227 جنيه من حساب رقم 1111", "Transfer EGP 227 from account ending 1111 cancelled", "Payment EGP 227 from account ending 1111 reversed"}){
            CaptureNotice notice=CaptureNotice.parse("BANK",body);ok(notice!=null);ok(notice.payment!=null);ok(notice.payment.amountMinor==22700);ok("1111".equals(notice.payment.cardLast4));ok(ExpenseParser.parse("BANK",body)==null);
        }
        ok(CaptureNotice.parse("BANK","تم رفضها: عملية 15 جنيه من حساب رقم 1111")==null);
        ok(ExpenseParser.isUnsuccessful("تم رفضها عملية خصم 15 جنيه من حساب رقم 1111"));
        ok(CaptureNotice.parse("BANK","يمكنك إلغاء التحويل من التطبيق")==null);
        ok(CaptureNotice.parse("BANK","لم يتم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 1111")==null);
        ok(CaptureNotice.parse("BANK","Transfer EGP 227 from account ending 1111 will be reversed")==null);
        ok(CaptureNotice.parse("BANK","Payment EGP 227 from account ending 1111 has not been canceled")==null);
        ok(CaptureNotice.parse("BANK","OTP verification code 123456 to cancel payment EGP 227")==null);
        ok(!ExpenseParser.isUnsuccessful("تم خصم 15 جنيه من بطاقتك، هذه آخر حركة لا تلامسية. استخدم PIN للحركة القادمة"));
        ok(CaptureNotice.cardRepayment("تم تحويل 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية 2222"));
        ok("1111".equals(ExpenseParser.debitAccountLast4("تم تحويل 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222")));
        System.out.println("Capture notice classification passed");
    }
}
