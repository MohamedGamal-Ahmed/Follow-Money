package com.mohamedgamal.masareef;

/** Synthetic support drafts; none of these strings come from a device inbox. */
public final class RedactionTest {
    private static int checks;
    private static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    public static void main(String[] args) {
        String[][] cases={
            {"Card 1234567890123456 purchase EGP 1,250.50 to MOHAMED GAMAL on 20/10 balance EGP 5200.00","MOHAMED","1,250.50","5200"},
            {"تم تحويل مبلغ ١٢٥٠٫٥٠ جنيه من أحمد علي إلى سارة حسن بتاريخ ٢٠/١٠","أحمد","سارة","١٢٥٠"},
            {"USD 9.99 from JOHN DOE, reference 987654321","JOHN","9.99","987654321"},
            {"Transferred EUR 25 to ALI AHMED from SALMA NABIL on 2026-10-20","ALI","SALMA","25"},
            {"Account 4444555566667777 balance: 45","4444555566667777","45"},
            {"Payment LE 25.00 reference 543210","25.00","543210"},
            {"Refund EUR -12.50 from ALICE SMITH; card 7788","ALICE","12.50","7788"},
            {"Paid GBP 2 to ROBERT JONES, ref 333444","ROBERT","GBP 2","333444"},
            {"الرصيد ٩٩٫٥٠ ج.م للحساب ١٢٣٤٥٦٧٨٩","٩٩٫٥٠","١٢٣٤٥٦٧٨٩"},
            {"تحويل 50 جنيه لـمحمد أحمد، رقم 123456","محمد","50","123456"}
        };
        for(String[] sample:cases) {
            String redacted=Redaction.text(sample[0]);
            for(int i=1;i<sample.length;i++)ok(!redacted.contains(sample[i]),"Unmasked value "+sample[i]+" in "+redacted);
            ok(!java.util.regex.Pattern.compile("[\\p{Nd}]{3,}").matcher(redacted).find(),"Long digit sequence remained");
        }
        String unknown="Payment format XYZ ref 12345678 amount EGP 120.00";
        ok(SmsPreview.canReport("HSBC",unknown),"Unsupported known-bank message must be reportable");
        ok(!SmsPreview.canReport("+201234567890",unknown),"Personal number must not be reportable");
        ok(!SmsPreview.canReport("Mohamed",unknown),"Personal name must not be reportable");
        ok(!SmsPreview.canReport("HSBC","OTP verification code 123456 for payment"),"OTP must not be reportable");
        ok(!SmsPreview.canReport("HSBC","Offer cashback up to EGP 5000 on your card purchase"),"Promotion must not be reportable");
        ok(!SmsPreview.canReport("HSBC","Purchase of EGP 120.00 at CARREFOUR"),"Recognized purchase must not be reportable");
        // 20 October samples: notices from known banks without an amount are not formats to fix.
        ok(!SmsPreview.canReport("bank_misr","عميلنا العزيز، تم إضافة الراتب لبطاقتكم اليوم. الان يمكنك الحصول على قرض شخصي خلال ساعة بدون مستندات أو مصاريف، للتقديم اضغط https://bnkmsr.com/12wzUrm"),"Salary teaser without an amount must not be reportable");
        ok(!SmsPreview.canReport("qnb","عزيزي العميل، تم اصدار بطاقة الخصم المباشر وجارى تسليمها لسيادتكم من خلال شركة الشحن في محل الإقامة، ولتفعيل البطاقة ارسل اخر 4 ارقام للبطاقة ثم علامة * ثم اخر 4 ارقام من بطاقة الرقم القومي على 19700"),"Card delivery notice must not be reportable");
        ok(!SmsPreview.canReport("bank_misr","عميلنا العزيز، يرجى العلم بأنه سيتم إجراء تحديثات على نظام بطاقات الخصم المباشر يوم الأحد الموافق 20 يوليو 2026، وذلك من الساعة 12:00 ظهراً حتى 4:00 مساءً، مما قد يؤثر على استخدام بطاقة الخصم المباشر الخاصة بكم خلال فترة التحديث"),"Maintenance notice must not be reportable");
        ok(SmsPreview.hasAmount("Debited EGP 1,250.00 from 1234")&&SmsPreview.hasAmount("تم خصم 250 ج.م من بطاقتك")&&SmsPreview.hasAmount("USD 20.5 received")&&!SmsPreview.hasAmount("ارسل اخر 4 ارقام على 19700"),"amount detector");
        ok("from hsbc: <slot> phone banking transfer from <slot> <slot> your available balance is <slot>".equals(SmsPreview.shape("From HSBC: 05JUL26 Phone Banking Transfer from 123-456***-789 USD 1,700.00- Your available balance is USD 12,345.67")),"one slot per value: "+SmsPreview.shape("From HSBC: 05JUL26 Phone Banking Transfer from 123-456***-789 USD 1,700.00- Your available balance is USD 12,345.67"));
        ok("ipn transfer received with amount of <slot> on <slot> on <slot> at <slot>. ref# <slot>. for more details call <slot>.".equals(SmsPreview.shape("IPN transfer received with amount of EGP 1250.50 on 1234 on 12/08 at 03:12 PM. Ref# 1a2b3c4d. For more details call 19700.")),"reference tokens collapse: "+SmsPreview.shape("IPN transfer received with amount of EGP 1250.50 on 1234 on 12/08 at 03:12 PM. Ref# 1a2b3c4d. For more details call 19700."));
        ok("تم اضافة مبلغ <slot> الي حساب رقم <slot> في <slot> عن طريق التحويل اللحظي".equals(SmsPreview.shape("تم اضافة مبلغ 56000EGP الى حساب رقم xxx1234 فى 26-SEP-2026 عن طريق التحويل اللحظي")),"amount glued to currency is one slot: "+SmsPreview.shape("تم اضافة مبلغ 56000EGP الى حساب رقم xxx1234 فى 26-SEP-2026 عن طريق التحويل اللحظي"));
        Throwable error=new NumberFormatException("EGP 1250.00 from SECRET PERSON card 1234567890");
        error.initCause(new IllegalStateException("SMS SECRET RAW BODY 987654"));
        String trace=Redaction.crashTrace(error);
        ok(!trace.contains("SECRET")&&!trace.contains("1250")&&!trace.contains("987654"),"Crash messages leaked");
        ok(trace.contains("NumberFormatException")&&trace.contains("IllegalStateException")&&trace.contains(" at "),"Useful failure types/frames missing");
        System.out.println("Redaction checks passed: "+checks+" (10 synthetic messages)");
    }
}
