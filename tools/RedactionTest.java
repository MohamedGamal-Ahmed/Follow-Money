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
        String unknown="Payment format XYZ ref 12345678";
        ok(SmsPreview.canReport("HSBC",unknown),"Unsupported known-bank message must be reportable");
        ok(!SmsPreview.canReport("+201234567890",unknown),"Personal number must not be reportable");
        ok(!SmsPreview.canReport("Mohamed",unknown),"Personal name must not be reportable");
        ok(!SmsPreview.canReport("HSBC","OTP verification code 123456 for payment"),"OTP must not be reportable");
        ok(!SmsPreview.canReport("HSBC","Offer cashback up to EGP 5000 on your card purchase"),"Promotion must not be reportable");
        ok(!SmsPreview.canReport("HSBC","Purchase of EGP 120.00 at CARREFOUR"),"Recognized purchase must not be reportable");
        Throwable error=new NumberFormatException("EGP 1250.00 from SECRET PERSON card 1234567890");
        error.initCause(new IllegalStateException("SMS SECRET RAW BODY 987654"));
        String trace=Redaction.crashTrace(error);
        ok(!trace.contains("SECRET")&&!trace.contains("1250")&&!trace.contains("987654"),"Crash messages leaked");
        ok(trace.contains("NumberFormatException")&&trace.contains("IllegalStateException")&&trace.contains(" at "),"Useful failure types/frames missing");
        System.out.println("Redaction checks passed: "+checks+" (10 synthetic messages)");
    }
}
