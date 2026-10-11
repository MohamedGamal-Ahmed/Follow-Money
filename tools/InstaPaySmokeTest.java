import com.mohamedgamal.masareef.ExpenseParser;

/** InstaPay app notification formats seen on a real device (October 2026). */
public class InstaPaySmokeTest {
    private static int passed;

    public static void main(String[] args) {
        transfer("إستلام نقود لقد استلمت 1000.00 جنيه من ahmedhamdyac@instapay", true, 100000, "ahmedhamdyac@instapay");
        transfer("إستلام نقود لقد استلمت 20000.00 جنيه من someone@instapay", true, 2000000, "someone@instapay");
        transfer("إرسال نقود لقد أرسلت 730.00 جنيه إلى friend@instapay", false, 73000, "friend@instapay");
        transfer("You have received EGP 50.00 from x@instapay", true, 5000, "x@instapay");
        check(ExpenseParser.parseTransfer("رمز التحقق 123456 لا تشاركه") == null, "OTP is not a transfer");
        check("TRU".equals(ExpenseParser.paidService("دفع فواتير تم دفع خدمة TRU بنجاح")), "service TRU");
        check("Land Line Bill".equals(ExpenseParser.paidService("تم دفع خدمة Land Line Bill بنجاح")), "service landline");
        check(ExpenseParser.paidService("قسطك القادم 1250 جنيه") == null, "reminder is not a payment");
        ExpenseParser.Parsed bank = ExpenseParser.parse("BM", "تم تحويل 730.00 جنيه إلى friend@instapay بنجاح");
        check(bank != null && bank.merchant.equals("friend@instapay"), "bank transfer merchant without بنجاح");
        ExpenseParser.Parsed bmIn = ExpenseParser.parseIncome("Banque Misr",
                "تم اضافة مبلغ 500EGP الى حساب رقم xxx7392 فى 12-FEB-2026 عن طريق التحويل اللحظي");
        check(bmIn != null && bmIn.amountMinor == 50000 && bmIn.merchant.equals("تحويل لحظي"), "Banque Misr instant credit");
        ExpenseParser.Parsed bmOut = ExpenseParser.parse("Banque Misr",
                "تم تحويل مبلغ 1500EGP من حساب رقم xxx7392 فى 03-MAR-2026 عن طريق التحويل اللحظي");
        check(bmOut != null && bmOut.amountMinor == 150000 && bmOut.category.equals("تحويلات") &&
                bmOut.merchant.equals("تحويل لحظي"), "Banque Misr instant debit");
        ExpenseParser.Parsed hsbc = ExpenseParser.parse("HSBC",
                "Your HSBC Account ********0001 was debited with IPN outward transfer for EGP 1.50 on 10-10-2026 00:26 to 7392");
        check(hsbc != null && hsbc.amountMinor == 150 && hsbc.merchant.equals("تحويل لحظي"), "HSBC IPN debit");
        ExpenseParser.Parsed phone = ExpenseParser.parse("HSBC",
                "From HSBC: 05JUL26 Phone Banking Transfer to 012-345*-678 EGP 25,000.00+ Your available balance is EGP 140,250.75");
        check(phone != null && phone.amountMinor == 2500000 && phone.category.equals("تحويلات"), "HSBC phone banking transfer");
        String accountTransfer = "تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم إلى شخص ا******* م*** رقم مرجعي 123456789012 يوم 2026-10-11 الساعه 15:27 للمزيد اتصل ب 19888";
        bankTransfer("Any Bank", accountTransfer, "شخص ا******* م***");
        bankTransfer("Different Bank", "تم إجراء تحويل لحظي بمبلغ EGP 150 إلى شخص ا******* م*** من حساب رقم xxx7392", "شخص ا******* م***");
        bankTransfer("Other Bank", "بمبلغ ١٥٠ جنيه تم تنفيذ عملية تحويل لحظي من حساب رقم xxx7392", "تحويل لحظي");
        bankTransfer("Unknown Bank", "تم تنفيذ التحويل اللحظي من حساب رقم xxx7392 بقيمة 150.00 جنيه", "تحويل لحظي");
        check(ExpenseParser.parse("Any Bank", "لم يتم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم") == null, "failed transfer is not spending");
        check(ExpenseParser.parse("Any Bank", "سيتم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم غدا") == null, "future transfer is not spending");
        check(ExpenseParser.parse("Any Bank", "تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 رقم مرجعي 150123456") == null, "reference is not an amount");
        check(ExpenseParser.parse("Any Bank", "تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392، الرصيد المتاح 150 جم") == null, "balance is not an amount");
        check(ExpenseParser.parse("Any Bank", "رمز التحقق 123456 لتنفيذ تحويل لحظي بمبلغ 150 جم") == null, "transfer authorization is not spending");
        System.out.println("InstaPay smoke tests passed: " + passed);
    }

    private static void bankTransfer(String sender, String body, String recipient) {
        ExpenseParser.Parsed parsed = ExpenseParser.parse(sender, body);
        check(parsed != null && parsed.amountMinor == 15000 && "EGP".equals(parsed.currency) &&
                "تحويلات".equals(parsed.category) && "7392".equals(parsed.cardLast4) &&
                recipient.equals(parsed.merchant), "completed transfer: " + body);
        check(ExpenseParser.parseIncome(sender, body) == null, "outgoing transfer is not income");
    }

    private static void transfer(String body, boolean incoming, long amount, String who) {
        ExpenseParser.Transfer t = ExpenseParser.parseTransfer(body);
        check(t != null && t.incoming == incoming && t.amountMinor == amount && t.counterpart.equals(who), body);
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("Failed: " + name);
        passed++;
    }
}
