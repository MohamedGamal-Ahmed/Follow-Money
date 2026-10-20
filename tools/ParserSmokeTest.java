import com.mohamedgamal.masareef.ExpenseParser;

public final class ParserSmokeTest {
    private static int checked;

    private static void expect(String sender, String message, long amount, String currency) {
        ExpenseParser.Parsed parsed = ExpenseParser.parse(sender, message);
        if (parsed == null || parsed.amountMinor != amount || !currency.equals(parsed.currency))
            throw new AssertionError("Unexpected parse: " + message);
        if (message.contains("AMAZON") && !"AMAZON".equals(parsed.merchant))
            throw new AssertionError("Unexpected merchant: " + parsed.merchant);
        checked++;
    }

    private static void expectMerchant(String message, String merchant, long amount, String last4) {
        ExpenseParser.Parsed parsed = ExpenseParser.parse("BanqueMisr", message);
        if (parsed == null || !merchant.equals(parsed.merchant) || parsed.amountMinor != amount ||
                (last4 == null ? parsed.cardLast4 != null : !last4.equals(parsed.cardLast4)))
            throw new AssertionError("Merchant/amount/card mismatch for: " + message + " -> " +
                    (parsed == null ? "null" : parsed.merchant + "/" + parsed.amountMinor + "/" + parsed.cardLast4));
        checked++;
    }

    private static void skip(String message) {
        if (ExpenseParser.parse("BANK", message) != null)
            throw new AssertionError("Unexpected transaction: " + message);
        checked++;
    }

    private static void expectCategory(String message, String category) {
        ExpenseParser.Parsed parsed = ExpenseParser.parse("BANK", message);
        if (parsed == null || !category.equals(parsed.category))
            throw new AssertionError("Unexpected category: " + message);
        checked++;
    }

    public static void main(String[] args) {
        expect("BANK", "Card purchase at AMAZON EGP 1,250.50. Available balance EGP 300", 125050, "EGP");
        expect("BANK", "تم خصم ٢٥٠٫٠٠ جنيه لدى كارفور من بطاقة ****1234", 25000, "EGP");
        ExpenseParser.Parsed card = ExpenseParser.parse("BANK", "تم خصم ٢٥٠٫٠٠ جنيه لدى كارفور من بطاقة ****1234");
        if (card == null || !"1234".equals(card.cardLast4)) throw new AssertionError("Card matching failed");
        checked++;
        expect("BANK", "You paid 99.99 EGP to Netflix", 9999, "EGP");
        expect("BANK", "تم خصم 100 جنيه استرليني من حسابك لدى TEST", 10000, "GBP");
        expect("BANK", "تم خصم جنيه استرليني 100 من حسابك لدى TEST", 10000, "GBP");
        expect("BANK", "تم خصم 100 جنيه من حسابك لدى TEST", 10000, "EGP");
        expect("BANK", "تم خصم جنيه 100 من حسابك لدى TEST", 10000, "EGP");
        expect("BANK", "Transfer sent USD 10.00 to JOHN", 1000, "USD");
        expectCategory("You transferred EGP 200.00 to JOHN", "تحويلات");
        expectCategory("Installment payment EGP 300.00 to valU", "أقساط");
        skip("Your OTP is 123456 for a payment of EGP 500");
        skip("You received EGP 500.00 from JOHN");
        skip("Available balance EGP 1000.00");
        ExpenseParser.Parsed income = ExpenseParser.parseIncome("BANK", "You received EGP 500.00 from JOHN. Available balance EGP 800.00");
        if (income == null || income.amountMinor != 50000 || !"EGP".equals(income.currency))
            throw new AssertionError("Income parsing failed");
        checked++;
        ExpenseParser.Balance balance = ExpenseParser.parseBalance("Card purchase EGP 200.00 from card ****1234. Available balance EGP 300.00");
        if (balance == null || balance.amountMinor != 30000 || !"1234".equals(balance.cardLast4))
            throw new AssertionError("Balance parsing failed");
        checked++;
        ExpenseParser.Balance oneDecimal = ExpenseParser.parseBalance(
                "Card ****2700. Current balance 11891.7 EGP");
        if (oneDecimal == null || oneDecimal.amountMinor != 1189170)
            throw new AssertionError("One-digit fractional balance parsed incorrectly");
        checked++;
        ExpenseParser.Balance mixed = ExpenseParser.parseBalance("Card ****2700. Balance EGP 1,234.5");
        ExpenseParser.Balance grouped = ExpenseParser.parseBalance("Card ****2700. Balance EGP 1,234");
        ExpenseParser.Balance european = ExpenseParser.parseBalance("Card ****2700. Balance EGP 1.234,5");
        if (mixed == null || mixed.amountMinor != 123450 ||
                grouped == null || grouped.amountMinor != 123400 ||
                european == null || european.amountMinor != 123450)
            throw new AssertionError("Decimal and thousands separators parsed incorrectly");
        checked++;
        long received = java.time.LocalDateTime.of(2026, 10, 9, 17, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long older = ExpenseParser.eventTime("Balance EGP 300 on 08/10/2026 09:15", received);
        long newer = ExpenseParser.eventTime("Balance EGP 250 on 09/10/2026 14:30", received);
        if (older >= newer || newer >= received) throw new AssertionError("Balance event chronology failed");
        checked++;
        if (ExpenseParser.eventTime("Balance EGP 300", received) != received)
            throw new AssertionError("Missing date should use message time");
        checked++;
        for(String body:new String[]{"on 08/10/2026","on 09/10/2026","08OCT26"}) {
            if(!"date_only".equals(ExpenseParser.eventTimeWithPrecision(body,received).precision))
                throw new AssertionError("Missing clock must retain date-only precision");
            checked++;
        }
        for(String body:new String[]{"on 08/10/2026 12:00","on 09/10/2026 14:42","no stated date","on 31/02/2026"}) {
            if(!"exact".equals(ExpenseParser.eventTimeWithPrecision(body,received).precision))
                throw new AssertionError("Explicit noon and receipt fallback must not become date-only");
            checked++;
        }
        long arabicClock=ExpenseParser.eventTime("تم إضافة تحويل لحظي بمبلغ 1 جم يوم 2026-10-09 الساعه 00:40",received);
        java.time.LocalTime statedClock=java.time.Instant.ofEpochMilli(arabicClock).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        if(statedClock.getHour()!=0 || statedClock.getMinute()!=40) throw new AssertionError("Arabic transaction clock ignored");
        checked++;
        String accountMessage = "08OCT26 MARKET*STORE Purchase from 036-137***-001 EGP 427.00. Your available balance is EGP 97.90";
        ExpenseParser.Parsed accountTransaction = ExpenseParser.parse("BANK", accountMessage);
        ExpenseParser.Balance accountBalance = ExpenseParser.parseBalance(accountMessage);
        if (accountTransaction == null || accountTransaction.amountMinor != 42700 ||
                !"MARKET".equals(accountTransaction.merchant) || !"001".equals(accountTransaction.cardLast4) ||
                accountBalance == null || accountBalance.amountMinor != 9790 || !"001".equals(accountBalance.cardLast4) ||
                ExpenseParser.eventTime(accountMessage, received) >= received)
            throw new AssertionError("Masked bank account parsing failed: " +
                    (accountTransaction == null ? "null" : accountTransaction.merchant + "/" + accountTransaction.amountMinor + "/" + accountTransaction.cardLast4));
        checked++;
        String starbucks = "تم الآن خصم 230.00 EGPعند STARBUCKS CITY STA يوم 09/10/2026 ، الرصيد المتاح EGP 9509.02 من بطاقة بنك مصر ****2700";
        ExpenseParser.Parsed coffee = ExpenseParser.parse("Banque Misr", starbucks);
        if (coffee == null || coffee.amountMinor != 23000 || !"STARBUCKS".equals(coffee.merchant) ||
                !"مقاهي".equals(coffee.category) || !"2700".equals(coffee.cardLast4))
            throw new AssertionError("Starbucks SMS was missed or balance confused with transaction amount");
        checked++;
        String atm = "تم الآن خصم EGP 2000.00 على AAIBN123 AAIB يوم 09/10/2026 ، الرصيد المتاح EGP 7504.02 من بطاقة بنك مصر ****2700";
        ExpenseParser.Parsed withdrawal = ExpenseParser.parse("Banque Misr", atm);
        if (withdrawal == null || withdrawal.amountMinor != 200000 ||
                !"سحب نقدي".equals(withdrawal.category))
            throw new AssertionError("ATM SMS was missed");
        checked++;
        // R1: the completed event decides cash principal vs fee; an ATM merchant alone is not enough.
        String[] withdrawals = {
                "تم سحب 2000 جنيه من حسابك",
                "تم سحب مبلغ 2,000 جنيه من حسابك",
                "Cash withdrawal of EGP 2,000.00 from account ****1234",
                "You have withdrawn EGP 500.00 from your account",
                "Cash withdrawal at ATM for EGP 2,000.00 from account ****1234"
        };
        for (String body : withdrawals) {
            ExpenseParser.Parsed cash = ExpenseParser.parse("BANK", body);
            if (cash == null || !"سحب نقدي".equals(cash.category))
                throw new AssertionError("Cash withdrawal not classified: " + body + " -> " +
                        (cash == null ? "null" : cash.category));
            checked++;
        }
        // The fee word belongs to its own amount, so the stated principal stays cash.
        String[][] principalWithFee = {
                {"تم سحب 2000 جنيه من حسابك برسوم 5 جنيه", "200000"},
                {"Cash withdrawal of EGP 2,000.00 from account ****1234 with a fee of EGP 5.00", "200000"},
                {"تم سحب 2000 جنيه من حسابك برسوم", "200000"},
                {"Cash withdrawal of EGP 2000 from account ****1234 with a fee", "200000"},
                {"Cash withdrawal at ATM for EGP 2000 from account ****1234 with a fee", "200000"},
                {"تم سحب من حسابك مبلغ 2000 جنيه مع رسوم", "200000"},
                {"Your account was debited EGP 2000 at ATM with a fee", "200000"},
                {"Cash withdrawal at ATM for EGP 5 from account ****1234 with a fee of EGP 5", "500"},
                {"Fee for cash withdrawal of EGP 2000 from account ****1234", "200000"}
        };
        for (String[] probe : principalWithFee) {
            ExpenseParser.Parsed cash = ExpenseParser.parse("BANK", probe[0]);
            if (cash == null || cash.amountMinor != Long.parseLong(probe[1]) || !"سحب نقدي".equals(cash.category))
                throw new AssertionError("Principal swallowed by its own fee: " + probe[0] + " -> " +
                        (cash == null ? "null" : cash.category + "/" + cash.amountMinor));
            checked++;
        }
        // A negated fee describes the withdrawal; it must not turn the principal into spending.
        String[][] withoutFee = {
                {"تم سحب 2000 جنيه من حسابك بدون رسوم", "200000"},
                {"تم سحب 2000 جنيه من حسابك دون رسوم", "200000"},
                {"Cash withdrawal of EGP 2000 from account ****1234 without commission", "200000"},
                {"Cash withdrawal of EGP 1,000.00 from account ****1234 with no fees", "100000"},
                {"Cash withdrawal of EGP 2,000.00 from account ****1234 free of charge", "200000"},
                {"Cash withdrawal of EGP 2,000.00 from account ****1234 commission-free", "200000"}
        };
        for (String[] probe : withoutFee) {
            ExpenseParser.Parsed cash = ExpenseParser.parse("BANK", probe[0]);
            if (cash == null || cash.amountMinor != Long.parseLong(probe[1]) || !"سحب نقدي".equals(cash.category))
                throw new AssertionError("Negated fee turned cash into spending: " + probe[0] + " -> " +
                        (cash == null ? "null" : cash.category + "/" + cash.amountMinor));
            checked++;
        }
        // Preserve event ownership across account/location word order and optional fee descriptions.
        for(String lead:new String[]{
                "Cash withdrawal at ATM for EGP 2000 from account ****1234",
                "Cash withdrawal of EGP 2000 from account ****1234",
                "You have withdrawn EGP 2000 from your account"})
            for(String tail:new String[]{" with a fee"," with a fee of EGP 5"," without commission"," commission-free"}) {
                ExpenseParser.Parsed cash=ExpenseParser.parse("BANK",lead+tail);
                if(cash==null || cash.amountMinor!=200000 || !"سحب نقدي".equals(cash.category))
                    throw new AssertionError("Withdrawal role changed with word order: "+lead+tail);
                checked++;
            }
        for(String lead:new String[]{
                "تم سحب مبلغ 2000 جنيه من حسابك",
                "تم سحب من حسابك مبلغ 2000 جنيه",
                "سحب نقدي بمبلغ 2000 جنيه من الصراف"})
            for(String tail:new String[]{" مع رسوم"," برسوم 5 جنيه"," بدون عمولة"," بلا رسوم"}) {
                ExpenseParser.Parsed cash=ExpenseParser.parse("BANK",lead+tail);
                if(cash==null || cash.amountMinor!=200000 || !"سحب نقدي".equals(cash.category))
                    throw new AssertionError("Withdrawal role changed with word order: "+lead+tail);
                checked++;
            }
        ExpenseParser.Parsed unrelatedCashClause=ExpenseParser.parse("BANK",
                "Your account was charged EGP 5.00. Cash withdrawal fees apply to another transaction");
        if(unrelatedCashClause==null || unrelatedCashClause.amountMinor!=500 ||
                "سحب نقدي".equals(unrelatedCashClause.category) || "رسوم بنكية".equals(unrelatedCashClause.category))
            throw new AssertionError("Another clause changed the selected charge's role");
        checked++;
        String[] fees = {
                "Your account has been charged EGP 5.00 as a cash withdrawal fee at ATM",
                "Your account was charged EGP 5.00 as a cash withdrawal fee",
                "تم خصم 5 جنيه رسوم سحب نقدي من حسابك",
                "تم خصم 5 جنيه عمولة سحب من ماكينة الصراف الآلي",
                "رسوم سحب نقدي 5 جنيه من حسابك",
                "تم خصم 5 جنيه مصاريف سحب من حسابك",
                "ATM cash withdrawal fee of EGP 5 from account ****1234",
                "Your account was charged EGP 5 from account ****1234 as a cash withdrawal fee",
                "Your account was charged EGP 5 as an ATM fee. Cash withdrawals have no fees next month"
        };
        for (String body : fees) {
            ExpenseParser.Parsed fee = ExpenseParser.parse("BANK", body);
            if (fee == null || fee.amountMinor != 500 || "سحب نقدي".equals(fee.category))
                throw new AssertionError("Standalone fee hidden as cash withdrawal: " + body + " -> " +
                        (fee == null ? "null" : fee.category + "/" + fee.amountMinor));
            checked++;
        }
        // A purchase that merely mentions a fee stays a purchase, not a bank fee.
        ExpenseParser.Parsed purchaseWithFee = ExpenseParser.parse("BANK",
                "Card purchase at AMAZON EGP 120.00 including delivery fee");
        if (purchaseWithFee == null || "سحب نقدي".equals(purchaseWithFee.category) ||
                "رسوم بنكية".equals(purchaseWithFee.category))
            throw new AssertionError("A purchase was reclassified from a fee word");
        checked++;
        expect("BANK", "تم استخدام بطاقتك ****1234 بمبلغ 75.5 جنيه عند مطعم", 7550, "EGP");
        expect("BANK", "خصم مبلغ 120 جنيه من حسابك لصالح محل", 12000, "EGP");
        expect("BANK", "Card ****1234 was charged EGP 45.00 at STORE", 4500, "EGP");
        expect("BANK", "Cash withdrawal at ATM for EGP 2,000.00 from account ****1234", 200000, "EGP");
        expect("HSBC", "09OCT26 Charges from 036-137***-001 EGP 22.49- Your available balance is EGP 800", 2249, "EGP");
        skip("خصم 20 جنيه على القهوة اليوم فقط، زور فرعنا");
        expect("TRU", "عميل ترو العزيز، تم سداد مبلغ 1885.00 ج.م بتاريخ 01/10/2026 بنجاح.", 188500, "EGP");
        expect("Banque Misr", "تم خصم مبلغ USD22.80 فيANTHROPICبتاريخ01/10/2026 الرصيد المتاح EGP9000", 2280, "USD");
        expect("HSBC", "تم خصم مبلغ EGP1897.71 فى Bill Payment Vodafone يوم 17/09/2026 الرصيد المتاح EGP1497.43", 189771, "EGP");
        expect("BANK", "Purchase GBP10.50 at STORE. Available balance EGP30739.13", 1050, "GBP");
        skip("تم الغاء تحويل مبلغ 1030 EGP من حساب رقم 1234 الى كارت ائتمان");
        skip("Purchase EGP60 at Amazon declined");
        skip("Cable 60W payment declined");
        skip("Balance statement EGP10.14 minimum payment to be paid");
        skip("تمت عملية الشراء بنجاح. إجمالي المبلغ الممول 9953.00 ج.م، قسطك بقيمة 553.00 ج.م شهريًا، لمدة 18 شهر");
        skip("بنفكرك بميعاد القسط الشهري لشهر أكتوبر بقيمة 1885.00 ج.م");
        ExpenseParser.Parsed incoming = ExpenseParser.parseIncome("BankMisr", "تم اضافة مبلغ 500 EGP لحساب رقم 1234 عن طريق التحويل اللحظي");
        if (incoming == null || incoming.amountMinor != 50000 || ExpenseParser.parse("BankMisr", "تم اضافة مبلغ 500 EGP لحساب رقم 1234 عن طريق التحويل اللحظي") != null)
            throw new AssertionError("Incoming credit misclassified");
        checked++;
        String qnbCredit = "IPN transfer received with amount of EGP 3000.00 on 2300 on 02/09 at 06:42 PM. Ref# 97101d40.";
        ExpenseParser.Parsed qnbIncome = ExpenseParser.parseIncome("QNB EGYPT", qnbCredit);
        if (qnbIncome == null || qnbIncome.amountMinor != 300000 || !"2300".equals(qnbIncome.cardLast4) ||
                ExpenseParser.parse("QNB EGYPT", qnbCredit) != null)
            throw new AssertionError("QNB IPN credit or account was missed");
        checked++;
        String valuRequest = "تلقينا طلب سداد القسط الشهري بقيمة 1378 سيتم تحديث التطبيق في 48 ساعة";
        if (ExpenseParser.parse("Valu", valuRequest) != null ||
                !ExpenseParser.isFinancialCandidate(valuRequest))
            throw new AssertionError("Valu request should be reviewed, not posted as payment");
        checked++;
        String vodafoneRecharge = "تم شحن 375.0 ج ورصيدك 378.00 ج";
        if (ExpenseParser.parse("Vodafone", vodafoneRecharge) != null ||
                !ExpenseParser.isFinancialCandidate(vodafoneRecharge))
            throw new AssertionError("Telecom recharge should be reviewed for bank-payment overlap");
        checked++;
        String etisalatRecharge = "You have added balance with 95.00 EGP for 135.71 EGP. Your new balance is 138.04 EGP.";
        if (ExpenseParser.parse("e&", etisalatRecharge) != null ||
                !ExpenseParser.isFinancialCandidate(etisalatRecharge))
            throw new AssertionError("Recharge with different paid and credited amounts needs review");
        checked++;
        String cardTransfer = "تم تنفيذ تحويل لحظي من بطاقة رقم 448899**7013 بمبلغ 1250 رقم مرجعي 1234567890يوم 10-10-2026 الساعه 11:07 للمزيد اتصل علي 19888";
        ExpenseParser.Parsed bankTransfer = ExpenseParser.parse("Banque Misr", cardTransfer);
        if (bankTransfer == null || bankTransfer.amountMinor != 125000 ||
                !"تحويلات".equals(bankTransfer.category) || !"7013".equals(bankTransfer.cardLast4))
            throw new AssertionError("Currencyless card transfer was missed or account number was lost");
        checked++;
        String playPurchase = "Dear customer, you have successfully purchased Truecaller Spa - Premium Monthly (Truecaller: Spam Call Blocker) from Google Play Store and 29.99 Egyptian pound has been deducted from your credit";
        ExpenseParser.Parsed play = ExpenseParser.parse("1234", playPurchase);
        if (play == null || play.amountMinor != 2999 || !"Truecaller".equals(play.merchant) ||
                !"اشتراكات".equals(play.category))
            throw new AssertionError("Mobile-credit subscription was missed");
        checked++;
        expect("1234", "Dear customer, you have successfully purchased Microsoft Apps - Microsoft 365 Basic Subscription - 1 month (Microsoft OneDrive) from Google Play Store and 80 Egyptian pound has been deducted from your credit", 8000, "EGP");
        if (!ExpenseParser.isCompletedFinancialCandidate(vodafoneRecharge) ||
                ExpenseParser.isCompletedFinancialCandidate(valuRequest) ||
                ExpenseParser.isCompletedFinancialCandidate("اقوى خصومات باقات Plus الجديدة 400 ميجا ب20 جنيه بدل 100 العرض سارى") ||
                ExpenseParser.isCompletedFinancialCandidate("نذكرك بدفع 5.00 ج.م. رسوم استخدام الراوتر"))
            throw new AssertionError("Coverage audit did not separate completed events from reminders and offers");
        checked++;
        // Promotions mention amounts but never move money (real Banque Misr campaign, July 2026).
        String carnival = "عميلنا العزيز، استمتع بعروض كرنفال بنك مصر - يوليو 2026 عند استخدام بطاقات بنك مصر الائتمانية في الشراء أونلاين، " +
                "وعزز فرصتك للفوز بكاش باك يصل إلى 50,000 جنيه. العرض ساري حتى 16 يوليو 2026. للمزيد: bnkmsr.com/BMCarnivalCashBack . تُطبق الشروط والأحكام.";
        skip(carnival);
        if (ExpenseParser.parseIncome("Banque Misr", carnival) != null || ExpenseParser.parseTransfer(carnival) != null ||
                ExpenseParser.isCompletedFinancialCandidate(carnival))
            throw new AssertionError("Promotion read as money movement");
        checked++;
        skip("Get up to 20% cash back when you pay with your card at AMAZON. Offer valid till 30 Oct. T&Cs apply. EGP 500 max");
        skip("اربح حتى 10,000 جنيه عند شراء بالبطاقة رقم ****1234 . تطبق الشروط والأحكام");
        // A completed payment keeps working even with a marketing footer.
        expect("BANK", "تم خصم 250.00 جنيه لدى كارفور من بطاقة ****1234. استمتع بعروض الصيف", 25000, "EGP");
        expect("BANK", "Card purchase at AMAZON EGP 120.00. Enjoy cash back offers on bnkmsr.com", 12000, "EGP");
        ExpenseParser.Parsed cashback = ExpenseParser.parseIncome("BANK", "تم إضافة كاش باك بمبلغ 50.00 جنيه إلى بطاقة ****1234");
        if (cashback == null || cashback.amountMinor != 5000) throw new AssertionError("Credited cash back skipped");
        checked++;
        ExpenseParser.Parsed gpt = ExpenseParser.parse("BANK", "Card purchase at OPENAI *CHATGPT SUBSCR EGP 220.00 from card ****2700");
        if (gpt == null || !"ChatGPT".equals(gpt.merchant) || !"اشتراكات".equals(gpt.category))
            throw new AssertionError("ChatGPT subscription not recognized: " + (gpt == null ? null : gpt.merchant));
        checked++;
        // A completed payment survives future fees/installments; the future amount never becomes the payment.
        expect("BANK", "تم خصم ١٠٠٠ جنيه لدى كارفور، وسيتم خصم رسوم ٥ جنيه", 100000, "EGP");
        expect("BANK", "Your card was debited EGP 500 at AMAZON. Installment will be debited monthly", 50000, "EGP");
        expectCategory("Your card was debited EGP 500 at AMAZON. Installment will be debited monthly", "غير مصنف");
        expect("BANK", "Card purchase at UBER EGP 100 pending", 10000, "EGP");
        expect("BANK", "EGP 500 was debited from your account. EGP 5 will be charged tomorrow", 50000, "EGP");
        expect("BANK", "تم سحب ٥٠٠ جنيه من حسابك، وسيتم خصم رسوم ٥ جنيه", 50000, "EGP");
        expect("BANK", "سيتم خصم ٥٠٠ جنيه غدًا، وتم خصم ٥ جنيه رسوم من حسابك", 500, "EGP");
        skip("هيتم خصم ٧٠٠ جنيه بكرة");
        skip("هيتم خصم ٧٠٠ جنيه من حسابك بكرة");
        skip("هيتخصم ٧٠٠ جنيه بكرة");
        skip("هيتخصم ٧٠٠ جنيه من حسابك بكرة");
        skip("سيتم خصم ٧٠٠ جنيه من حسابك بكرة");
        skip("سوف يتم خصم ٧٠٠ جنيه من حسابك غدًا");
        skip("Your payment EGP 700 will be processed tomorrow");
        skip("EGP 500 will be debited from your account");
        skip("Your payment EGP 500 is pending");
        skip("Scheduled transaction EGP 500 from your account");
        skip("تم خصم رسوم الخدمة. سيتم خصم 500 جنيه من حسابك غدًا");
        skip("Successfully scheduled: payment EGP 500 will be processed tomorrow");
        skip("تم تنفيذ التعليمات لمبلغ 500 جنيه من حسابك وسيتم خصم المبلغ غدًا");
        String future="Your payment EGP 700 will be processed tomorrow";
        if (!ExpenseParser.isFutureOnly(future) || ExpenseParser.isUnsuccessful(future) ||
                ExpenseParser.isUnsuccessful("هيتم خصم ٧٠٠ جنيه من حسابك بكرة") ||
                ExpenseParser.isFutureOnly("تم خصم ١٠٠٠ جنيه لدى كارفور، وسيتم خصم رسوم ٥ جنيه"))
            throw new AssertionError("Future wording was confused with failure or completed payment");
        checked++;
        if(ExpenseParser.isCompletedFinancialCandidate("سيتم خصم 700 جنيه من حسابك بكرة"))
            throw new AssertionError("Future alert appeared completed in coverage review");
        checked++;
        String distant="تم خصم رسوم الخدمة " + "تفاصيل ".repeat(25) + "500 جنيه وسيتم خصم رسوم غدًا";
        skip(distant);
        ExpenseParser.Parsed mixedCredit=ExpenseParser.parseIncome("BANK","تم إضافة 500 جنيه لحسابك، وسيتم خصم 5 جنيه رسوم");
        if(mixedCredit==null || mixedCredit.amountMinor!=50000) throw new AssertionError("Completed credit lost to future fee");
        checked++;
        // A generic debit verb must not hide a positively identified commitment/payee/transfer.
        expectCategory("تم سحب مبلغ 1500 جنيه من حسابك قسط القرض", "أقساط");
        expectCategory("EGP 1,500.00 withdrawn from your account for loan installment", "أقساط");
        expectCategory("EGP 500 withdrawn from your account as transfer to Mohamed", "تحويلات");
        expectCategory("تم سحب 250 جنيه من حسابك لصالح Vodafone", "فواتير");
        expectCategory("EGP 220 withdrawn from your account at OPENAI *CHATGPT SUBSCR", "اشتراكات");
        // Unmistakable cash context still wins even when a descriptor names a familiar merchant.
        expectCategory("Cash withdrawal of EGP 2000 at Vodafone ATM", "سحب نقدي");
        expectCategory("تم سحب نقدي 2000 جنيه من ماكينة Vodafone", "سحب نقدي");
        expectCategory("no-fee ATM withdrawal of EGP 2000 from account ****1234", "سحب نقدي");
        expectCategory("zero-fee ATM withdrawal of EGP 2000 from account ****1234", "سحب نقدي");
        expectCategory("low fee ATM withdrawal of EGP 2000 from account ****1234", "سحب نقدي");
        for(String body:new String[]{"EGP 100 withdrawn from your account for purchase at CARREFOUR",
                "تم سحب 100 جنيه من حسابك للشراء لدى كارفور"}) {
            ExpenseParser.Parsed purchase=ExpenseParser.parse("BANK",body);
            if(purchase==null || "سحب نقدي".equals(purchase.category))
                throw new AssertionError("Purchase hidden by generic withdrawal verb: "+body);
            checked++;
        }
        // A digit inside a merchant name stays; the amount, card tail, reference, date and time after it do not.
        expectMerchant("Card purchase at QA_B3_SHOP EGP 200.00 on 19/10/2026 at 22:02. Card ****1111.", "QA_B3_SHOP", 20000, "1111");
        expectMerchant("Card purchase at 7-ELEVEN EGP 45.50 on 19/10/2026 at 09:15. Card ****2222.", "7-ELEVEN", 4550, "2222");
        expectMerchant("Card purchase at QA MART 6 OCT EGP 310.00 on 19/10/2026 at 18:00. Card ****3333.", "QA MART 6 OCT", 31000, "3333");
        // A known brand still gets its catalog name even with a branch number after it.
        expectMerchant("Card purchase at SPINNEYS 6 OCT EGP 310.00 on 19/10/2026 at 18:00. Card ****3333.", "Spinneys", 31000, "3333");
        expectMerchant("Purchase of 75 EGP at UBER 19/10/2026 21:44 Card ****5555", "Uber", 7500, "5555");
        expectMerchant("Card purchase at QA_B3_SHOP 200 EGP ref 987654321 Card ****1111", "QA_B3_SHOP", 20000, "1111");
        expectMerchant("Card purchase at GO BUS 2 *9999 EGP 120.00 on 19/10/2026", "GO BUS 2", 12000, null);
        // HSBC puts the sign after the amount: "+" reached the account, "-" left it. The merchant is the dated descriptor.
        // Tester samples (numbers replaced): only the Phone Banking credit arrived; the other three follow the same format.
        // In this format the masked account is the alerting account itself, after "from" (debit) or "to" (credit).
        hsbcCredit("From HSBC: 24SEP26 TT Payment from 012-345***-679 USD 2,083.85+ Your available balance is USD 2,150.10", 208385, "USD", "TT Payment", "679");
        hsbcCredit("From HSBC: 25SEP26 Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74", 8765000, "EGP", "Phone Banking Transfer", "678");
        hsbcDebit("From HSBC: 25SEP26 Phone Banking Transfer from 012-345***-679 USD 1,700.00- Your available balance is USD 450.10", 170000, "USD", "Phone Banking Transfer", "تحويلات", "679");
        hsbcDebit("From HSBC: 17OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00", 200000, "EGP", "ATM Cash Withdrawal", "سحب نقدي", "678");
        hsbcDebit("09OCT26 Charges from 036-137***-001 EGP 22.49- Your available balance is EGP 800", 2249, "EGP", "Charges", "غير مصنف", "001");
        // An explicit debit verb keeps its meaning next to a trailing plus, so no other bank's wording changes.
        String explicitDebit = "Your account was debited with EGP 100.00+ at STORE";
        ExpenseParser.Parsed debited = ExpenseParser.parse("BANK", explicitDebit);
        if (debited == null || debited.amountMinor != 10000 || ExpenseParser.parseIncome("BANK", explicitDebit) != null)
            throw new AssertionError("Explicit debit wording was flipped by a trailing plus");
        checked++;
        System.out.println("Parser smoke tests passed: " + checked);
    }

    private static String describe(ExpenseParser.Parsed parsed) {
        return parsed == null ? "null" : parsed.amountMinor + " " + parsed.currency + " / " + parsed.merchant + " / " + parsed.category + " / " + parsed.cardLast4;
    }

    private static void hsbcCredit(String message, long amount, String currency, String merchant, String last4) {
        if (ExpenseParser.parse("HSBC", message) != null) throw new AssertionError("Trailing plus booked as spending: " + message);
        ExpenseParser.Parsed parsed = ExpenseParser.parseIncome("HSBC", message);
        if (parsed == null || parsed.amountMinor != amount || !currency.equals(parsed.currency) || !merchant.equals(parsed.merchant) ||
                !"دخل".equals(parsed.category) || (last4 == null ? parsed.cardLast4 != null : !last4.equals(parsed.cardLast4)))
            throw new AssertionError("HSBC credit mismatch: " + message + " -> " + describe(parsed));
        checked++;
    }

    private static void hsbcDebit(String message, long amount, String currency, String merchant, String category, String last4) {
        ExpenseParser.Parsed parsed = ExpenseParser.parse("HSBC", message);
        if (parsed == null || parsed.amountMinor != amount || !currency.equals(parsed.currency) || !merchant.equals(parsed.merchant) ||
                !category.equals(parsed.category) || (last4 == null ? parsed.cardLast4 != null : !last4.equals(parsed.cardLast4)))
            throw new AssertionError("HSBC debit mismatch: " + message + " -> " + describe(parsed));
        if (ExpenseParser.parseIncome("HSBC", message) != null) throw new AssertionError("Trailing minus parsed as income: " + message);
        checked++;
    }
}
