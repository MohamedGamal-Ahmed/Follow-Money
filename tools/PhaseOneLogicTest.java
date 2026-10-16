package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class PhaseOneLogicTest {
    private static int checked;
    private static void equal(Object expected,Object actual) {
        if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", actual "+actual);checked++;
    }
    public static void main(String[] args) {
        LocalDate today=LocalDate.of(2026,10,10);
        DailyAllowance.Plan egp=new DailyAllowance.Plan("EGP",50000,today,25);
        DailyAllowance.Result day=DailyAllowance.calculate(egp,today,25000,999999);
        equal(25000L,day.remaining);equal(50,day.progress);equal(15,day.daysToSalary);
        equal(LocalDate.of(2026,9,25),day.cycleStart);equal(LocalDate.of(2026,10,25),day.cycleEnd);
        equal(25000L,DailyAllowance.calculate(egp,today,25000,0).remaining);
        day=DailyAllowance.calculate(egp,today,65000,0);equal(-15000L,day.remaining);equal(100,day.progress);equal("تجاوزت حد اليوم",day.status());
        equal(0L,DailyAllowance.calculate(egp,today,50000,0).remaining);
        equal(50000L,DailyAllowance.calculate(egp,today.plusDays(1),0,0).remaining);
        equal(false,DailyAllowance.calculate(egp,today.minusDays(1),0,0).valid);
        equal("حدد ميزانيتك اليومية",DailyAllowance.calculate(null,today,200,0).status());
        equal(-1,DailyAllowance.calculate(new DailyAllowance.Plan("USD",1000,today,0),today,200,0).daysToSalary);
        equal(800L,DailyAllowance.calculate(new DailyAllowance.Plan("USD",1000,today,0),today,200,0).remaining);
        DailyAllowance.Plan monthEnd=new DailyAllowance.Plan("EGP",100,today.minusYears(1),31);
        day=DailyAllowance.calculate(monthEnd,LocalDate.of(2027,2,28),0,0);
        equal(0,day.daysToSalary);equal(LocalDate.of(2027,2,28),day.nextSalary);
        equal(LocalDate.of(2027,2,28),day.cycleStart);equal(LocalDate.of(2027,3,31),day.cycleEnd);
        day=DailyAllowance.calculate(monthEnd,LocalDate.of(2027,3,1),0,0);equal(30,day.daysToSalary);
        DailyAllowance.Plan lastDay=new DailyAllowance.Plan("EGP",100,today.minusYears(1),DailyAllowance.MONTH_END);
        equal(LocalDate.of(2027,2,28),DailyAllowance.calculate(lastDay,LocalDate.of(2027,2,10),0,0).nextSalary);
        equal(LocalDate.of(2028,2,29),DailyAllowance.calculate(lastDay,LocalDate.of(2028,2,10),0,0).nextSalary);
        equal(LocalDate.of(2027,4,30),DailyAllowance.calculate(lastDay,LocalDate.of(2027,4,10),0,0).nextSalary);
        equal(LocalDate.of(2027,3,31),DailyAllowance.calculate(lastDay,LocalDate.of(2027,3,10),0,0).nextSalary);
        equal(21,DailyAllowance.calculate(null,today,0,0,DailyAllowance.MONTH_END).daysToSalary);
        MoneyTotals totals=new MoneyTotals();totals.add("EGP","expense",25000);totals.add("USD","expense",200);totals.add("EGP","income",100000);totals.add("EGP","transfer",200000);
        equal(25000L,totals.currencies.get("EGP").expense);equal(100000L,totals.currencies.get("EGP").income);equal(200L,totals.currencies.get("USD").expense);
        equal(200000L,totals.currencies.get("EGP").transfers);
        for(int invalidDay:new int[]{-1,33})try{new DailyAllowance.Plan("EGP",100,today,invalidDay);throw new AssertionError("Invalid salary day accepted");}catch(IllegalArgumentException expected){checked++;}
        try{totals.add("EGP","expense",-1);throw new AssertionError("Negative accepted");}catch(IllegalArgumentException expected){checked++;}
        ZoneId cairo=ZoneId.of("Africa/Cairo");LocalDate dst=LocalDate.of(2026,10,29);
        equal(25L,ChronoUnit.HOURS.between(dst.atStartOfDay(cairo),dst.plusDays(1).atStartOfDay(cairo)));
        equal("carrefour",BrandCatalog.merchant("CARREFOUR MAADI"));equal("carrefour",BrandCatalog.merchant("كارفور المعادي"));
        equal("mcdonalds",BrandCatalog.merchant("McDonald's Cairo"));equal(null,BrandCatalog.merchant("My Shell Company"));
        equal(null,BrandCatalog.bank("ABC Cafe"));equal("abc",BrandCatalog.bank("Bank ABC"));equal("bank_misr",BrandCatalog.bank("BM-Online"));
        equal(null,BrandCatalog.merchant("HSBC"));equal(null,BrandCatalog.merchant("Superuber shop"));equal(null,BrandCatalog.merchant("we"));
        equal("espressolab",BrandCatalog.merchant("ESPRESSO LAB-CCENTER ALMA"));
        equal("espressolab",BrandCatalog.merchant("Espressolab Egypt"));equal(null,BrandCatalog.merchant("ES"));
        equal("cleopatra",BrandCatalog.merchant("CLEOPATRA HOSPITAL"));
        // Uber Eats is its own brand; the "Uber" prefix must never win the match.
        equal("ubereats",BrandCatalog.merchant("Uber Eats"));equal("ubereats",BrandCatalog.merchant("UBER EATS CAIRO"));
        equal("uber",BrandCatalog.merchant("Uber"));equal("uber",BrandCatalog.merchant("Uber Cairo"));
        // A card network is a payment method, never the beneficiary merchant of a longer descriptor.
        equal(null,BrandCatalog.merchant("VISA PURCHASE CAIRO"));equal(null,BrandCatalog.merchant("MasterCard fee"));
        equal("visa",BrandCatalog.merchant("Visa"));equal("mastercard",BrandCatalog.merchant("MasterCard"));
        // Claude is billed as the product or as the company; both show the Claude mark.
        equal("claude",BrandCatalog.merchant("CLAUDE.AI SUBSCRIPTION"));equal("claude",BrandCatalog.merchant("ANTHROPIC"));
        equal(null,BrandCatalog.merchant("CLAUDETTE BOUTIQUE"));equal(null,BrandCatalog.bank("Claude"));
        long when=today.atTime(12,0).atZone(cairo).toInstant().toEpochMilli();
        SmsPreview preview=SmsPreview.parse("BANK","Card purchase at CARREFOUR EGP 250.00. Available balance EGP 300",when);
        equal("expense",preview.kind);equal(25000L,preview.amountMinor);equal(true,preview.balanceIncluded);equal(false,preview.selected);
        SmsPreview imported=SmsPreview.parse("BANK",preview.body,when+3600000,when);
        equal(when+3600000,imported.receivedAt);equal(when,imported.captureAt);equal(preview.eventAt,imported.eventAt);
        equal(when,SmsPreview.parse("BANK",preview.body,when,0).captureAt);
        equal(when,SmsPreview.parse("BANK",preview.body,when,-1).captureAt);
        java.util.Set<String> sentKeys=new java.util.HashSet<>();
        equal(true,imported.canUseReceiptKey(sentKeys));
        sentKeys.add(CaptureIdentity.smsKey("BANK",preview.body,imported.receivedAt));
        equal(false,imported.canUseReceiptKey(sentKeys));equal(true,preview.canUseReceiptKey(sentKeys));
        equal(null,SmsPreview.parse("BANK","Your OTP is 123456 for payment EGP 500",when));
        preview=SmsPreview.parse("BANK","Available balance EGP 1000",when);equal("balance",preview.kind);equal(false,preview.isTransaction());
        preview=SmsPreview.parse("BANK","You received USD 10.00 from JOHN",when);equal("income",preview.kind);equal("USD",preview.currency);
        equal(null,SmsPreview.parse("Friend","Hello, how are you?",when));
        SmsPreview fresh=SmsPreview.parse("BANK","Card purchase at IKEA EGP 80",when);
        SmsPreview old=SmsPreview.parse("BANK","Card purchase at CARREFOUR EGP 20",when);old.captureStatus=1;
        SmsPreview excluded=SmsPreview.parse("BANK","Card purchase at UBER EGP 30",when);excluded.captureStatus=2;
        java.util.List<SmsPreview> selection=java.util.Arrays.asList(fresh,old,excluded);
        SmsPreview.selectAll(selection,false);equal(true,fresh.selected);equal(true,old.selected);equal(false,excluded.selected);
        SmsPreview.selectAll(selection,true);equal(true,fresh.selected);equal(false,old.selected);
        SmsPreview cancellation=SmsPreview.parse("BANK","تم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 1111",when);
        SmsPreview decline=SmsPreview.parse("BANK","تم رفض عملية خصم 15 جنيه من بطاقة رقم 1111",when);
        SmsPreview future=SmsPreview.parse("BANK","سيتم خصم مبلغ 70 جنيه غدا",when);
        equal(true,cancellation!=null);equal(true,decline!=null);equal(true,future!=null);
        SmsPreview.selectForImport(java.util.Arrays.asList(fresh,old,excluded,cancellation,decline,future));
        equal(true,fresh.selected);equal(false,old.selected);equal(false,excluded.selected);
        equal(true,cancellation.selected);equal(false,decline.selected);equal(false,future.selected);
        // Same text as the emulator inbox fixture in run-card-journeys.ps1.
        SmsPreview fixture=SmsPreview.parse("QAIMPORT","Transfer of EGP 227.00 from account **3333 has been cancelled",when);equal(true,fixture!=null&&"correction".equals(fixture.kind));
        SmsPreview.selectForImport(java.util.Collections.singletonList(fixture));equal(true,fixture.selected);

        SmsParts parts=new SmsParts();
        parts.add("Banque Misr",when,"شكرًا لاستخدامك بطاقة بنك مصر ***5555، تم الآن خصم 255.01 EGPعند ES");
        parts.add("Banque Misr",when+25000,"PRESSO LAB يوم 09/10/2026 ، الرصيد المتاح EGP 500.00");
        equal(1,parts.messages.size());SmsParts.Message complete=parts.messages.values().iterator().next();
        ExpenseParser.Parsed purchase=ExpenseParser.parse(complete.sender,complete.body.toString());
        equal("Espresso Lab",purchase.merchant);equal(25501L,purchase.amountMinor);equal(when,complete.when);
        equal(LocalDate.of(2026,10,9),java.time.Instant.ofEpochMilli(ExpenseParser.eventTime(complete.body.toString(),when)).atZone(ZoneId.systemDefault()).toLocalDate());
        SmsParts otherBroadcast=new SmsParts();otherBroadcast.add("Banque Misr",when+30000,complete.body.toString());equal(1,otherBroadcast.messages.size());
        System.out.println("Phase one logic checks passed: "+checked);
    }
}
