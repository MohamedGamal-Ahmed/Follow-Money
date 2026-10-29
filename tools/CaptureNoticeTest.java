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
        // Money paid into a credit card is a repayment, never income; refunds and purchases on the card are not (29 Oct 2026).
        ok(CaptureNotice.cardDeposit("عميلنا العزيز، تم إيداع EGP 3218  بالبطاقة الائتمانية المنتهية بـ ****2222، فى QA-Online يوم  29/10/2026 ، ورصيدكم الحالي 10920.95 EGP"));
        ok(CaptureNotice.cardDeposit("تم سداد مبلغ 500 جم لبطاقة البنك الائتمانية المنتهية بـ 2222"));
        ok(CaptureNotice.cardDeposit("Your credit card ****2222 payment of EGP 700.00 has been received. Thank you"));
        ok(!CaptureNotice.cardDeposit("تم رد مبلغ EGP 50 للبطاقة الائتمانية المنتهية بـ ****2222 من QA SHOP"));
        ok(!CaptureNotice.cardDeposit("Refund of EGP 50 credited to your credit card ****2222"));
        ok(!CaptureNotice.cardDeposit("تم خصم 300 جم من بطاقتك الائتمانية عند QA SHOP"));
        ok(!CaptureNotice.cardDeposit("تم إضافة تحويل لحظي لحسابكم رقم xxx1111 بمبلغ 20000 جم من QA OWNER NAME"));
        ok(CaptureNotice.cardRepayment("تم تحويل مبلغ 3218EGP      من حساب رقم xxx1111      الى كارت ائتمان / مسبق الدفع فى 29-OCT-2026"));
        System.out.println("Capture notice classification passed");
    }
}
