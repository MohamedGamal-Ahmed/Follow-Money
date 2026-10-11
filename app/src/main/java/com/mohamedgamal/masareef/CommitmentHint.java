package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evidence-backed proposal; a reminder never records an expense or proves previous payments. */
final class CommitmentHint {
    final String key, merchant, kind, explanation, currency, cycle;
    final long amount;
    final Integer count;
    final LocalDate estimatedDate;
    final boolean dateExact;
    private static final String NUMBER="([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final String CURRENCY="(EGP|USD|EUR|GBP|LE|ج\\.?\\s*م\\.?|جم|جنيه|دولار|يورو)";
    private static final Pattern MONEY=Pattern.compile("(?i)(?:"+CURRENCY+"\\s*"+NUMBER+"|"+NUMBER+"\\s*"+CURRENCY+")");
    private static final Pattern REMINDER=Pattern.compile("(?i)(نذكرك|بنفكرك|يرجى\\s*(?:سداد|دفع)|برجاء\\s*(?:سداد|دفع)|طلب\\s+سداد|payment\\s+request|مستحق|استحقاق|جاهزة\\s+للدفع|payment\\s+due|minimum\\s+due|due\\s+(?:before|on)|amount\\s+due|please\\s+pay|reminder)");
    private static final Pattern PROMO=Pattern.compile("(?i)(خصومات|خصم\\s+على|كاش\\s*باك|cash\\s*back|limited\\s+time\\s+offer|العرض\\s+ساري|العرض\\s+سارى|قدم\\s+على|قدّم\\s+على|اشتر[يى]\\s+دلوقتي)");
    private static final Pattern DUE_DATE=Pattern.compile("(?i)(?:due\\s+(?:before|on)|الاستحقاق|استحقاق|للسداد\\s+قبل|سداد[^0-9]{0,15}قبل|بتاريخ|في|فى)\\s*[:：]?\\s*(?:(20\\d{2})[-/]([01]?\\d)[-/]([0-3]?\\d)|([0-3]?\\d)[-/]([01]?\\d)[-/](20\\d{2}))");

    CommitmentHint(String key,String merchant,String kind,long amount,Integer count,LocalDate date,String explanation) {
        this(key,merchant,kind,amount,count,date,explanation,"EGP","monthly",false);
    }
    CommitmentHint(String key,String merchant,String kind,long amount,Integer count,LocalDate date,String explanation,
                   String currency,String cycle,boolean dateExact) {
        this.key=key;this.merchant=merchant;this.kind=kind;this.amount=amount;this.count=count;
        this.estimatedDate=date;this.explanation=explanation;this.currency=currency;this.cycle=cycle;this.dateExact=dateExact;
    }

    static boolean isReminder(String body) {
        return body!=null && REMINDER.matcher(ExpenseParser.normalizeDigits(body)).find() &&
                !body.matches("(?is).*(تم\\s*(?:خصم|سداد|دفع)|تمت\\s+عملية|successfully\\s+(?:paid|purchased)|payment\\s+received).*" );
    }

    static CommitmentHint parse(String sender,String body,long when) {
        if(body==null || ExpenseParser.isUnsuccessful(body) || ExpenseParser.isVerificationMessage(body)) return null;
        String text=ExpenseParser.normalizeDigits(body).replaceAll("[\\s\\p{Z}]+"," ").trim();
        if(PROMO.matcher(text).find()) return null;
        LocalDate day=Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        String identity=sender==null || sender.isBlank()?"جهة غير محددة":sender.trim();

        // A concrete financed purchase or amended plan is distinct from a provider's monthly total.
        Matcher plan=Pattern.compile("(?:قسطك\\s+(?:بقيمة|الآن\\s+بقيمة|الان\\s+بقيمة)|monthly\\s+installment\\s+(?:of|is))\\s*"+NUMBER+"\\s*"+CURRENCY+".*?(?:لمدة|على|for)\\s*(\\d+)\\s*(?:شهور|أشهر|اشهر|شهر|months?)",Pattern.CASE_INSENSITIVE).matcher(text);
        if(plan.find()) {
            Matcher shop=Pattern.compile("من\\s+عند\\s+(.+?)\\s+بنجاح").matcher(text);
            Matcher ref=Pattern.compile("(?:عملية\\s+الشراء|purchase)\\s*(?:رقم|number|no\\.?)\\s*([0-9A-Za-z/.-]+)",Pattern.CASE_INSENSITIVE).matcher(text);
            String reference=ref.find()?ref.group(1):null;
            String title=identity+" · "+(shop.find()?shop.group(1).trim():text.contains("تعديل")?"خطة معدّلة":"قسط شراء")+" · "+day;
            if(plan.group(3).length()>3) return null;
            int count=Integer.parseInt(plan.group(3));
            if(count<=0 || count>600) return null;
            String planKey=reference==null && identity.equalsIgnoreCase("TRU")?
                    "TRU-plan:"+CaptureIdentity.hash(ExpenseParser.normalizeDigits(body)):planKey(identity,reference==null?text:reference);
            return new CommitmentHint(planKey,title,"installment",
                    ExpenseParser.parseMinor(plan.group(1)),count,day.plusMonths(1),
                    text.contains("تعديل")?"خطة معدلة؛ العدد المذكور هو مدة الخطة الجديدة. راجع الأقساط السابقة وموعد أول قسط قبل الحفظ.":"قسط شراء محدد؛ موعد أول قسط يحتاج التأكيد.",
                    currency(plan.group(2)),"monthly",false);
        }

        if(isReminder(text)) {
            if(!text.matches("(?is).*(سداد|دفع|قسط|اقساط|أقساط|فاتورة|رسوم|payment|installment|amount\\s+due|minimum\\s+due|statement|loan|bill).*")) return null;
            boolean statement=text.matches("(?is).*(statement|كشف|minimum\\s+due|الحد\\s+الأدنى|الحد\\s+الادنى).*" );
            String amountText=text;
            if(statement) {
                Matcher minimum=Pattern.compile("(?i)(?:minimum\\s+(?:payment|due)|الحد\\s+(?:الأدنى|الادنى)[^0-9]{0,20})[^0-9A-Z]{0,8}(.+)").matcher(text);
                if(minimum.find()) amountText=minimum.group(1);
            } else {
                Matcher context=Pattern.compile("(?i)(?:بقيمة|بمبلغ|مبلغ\\s*(?:القسط|مستحق)|amount\\s+due|installment\\s+(?:of|is))[^0-9A-Z]{0,8}(.+)").matcher(text);
                if(context.find()) amountText=context.group(1);
            }
            Matcher money=MONEY.matcher(amountText);
            long amount=0;String currency="EGP";
            boolean hasCurrency=money.find();
            if(hasCurrency) {
                amount=ExpenseParser.parseMinor(money.group(2)!=null?money.group(2):money.group(3));
                currency=currency(money.group(1)!=null?money.group(1):money.group(4));
                if(amount<=0) return null;
            } else if(identity.equalsIgnoreCase("Valu") || identity.equalsIgnoreCase("TRU")) {
                Matcher bare=Pattern.compile("(?:بقيمة|بمبلغ)\\s*"+NUMBER).matcher(text);
                if(bare.find()) amount=ExpenseParser.parseMinor(bare.group(1));
            }
            LocalDate due=explicitDate(text);
            if(due==null) due=arabicMonthDate(text,day);
            boolean exact=due!=null;
            if(due==null) due=day.plusMonths(1);
            String kind=statement?"bill":text.matches("(?is).*(قسط|اقساط|أقساط|installment|loan).*" )?"installment":"bill";
            Matcher account=Pattern.compile("(?i)(?:card|بطاق[^\\s]*|account|حساب|loan|قرض|رقم)[^0-9]{0,25}[*xX]*([0-9]{3,20})(?!\\d)").matcher(text);
            String accountId=account.find()?account.group(1):"";
            String last4=accountId.length()>4?accountId.substring(accountId.length()-4):accountId;
            String bank=SourceIdentity.bank(identity);
            String key="due:"+CaptureIdentity.hash((bank==null?identity.toLowerCase(Locale.ROOT):bank)+":"+kind+":"+accountId+":"+currency);
            String title=identity+" · "+(statement?"مستحق بطاقة":kind.equals("installment")?"قسط شهري":"فاتورة")+(last4.isEmpty()?"":" ••••"+last4);
            String explanation=statement?"الحد الأدنى للسداد من كشف البطاقة؛ ليس إجمالي الأقساط ولا إثبات صرف. المبلغ يتغير، وعدد الأقساط غير مذكور.":"تذكير استحقاق؛ لا يثبت السداد. المبلغ قد يتغير، وعدد الأقساط غير مؤكد.";
            explanation+=exact?" الموعد مذكور في الرسالة.":" الموعد تقديري ويحتاج تحديده قبل الحفظ.";
            if(amount==0) explanation+=" المبلغ غير مذكور؛ أدخله وحدد العملة قبل الحفظ.";
            if(text.matches("(?is).*(طلب\\s+سداد|payment\\s+request).*")) explanation+=" طلب السداد لا يثبت اكتمال الدفع.";
            return new CommitmentHint(key,title,kind,amount,null,due,explanation,currency,"monthly",exact);
        }

        ExpenseParser.Parsed paid=ExpenseParser.parse(sender,body);
        if(paid!=null && ("اشتراكات".equals(paid.category) || "Vodafone".equals(paid.merchant))) {
            boolean subscription="اشتراكات".equals(paid.category);
            boolean explicit=text.matches("(?is).*(subscription|monthly|yearly|annual|اشتراك|تجديد|google one|onedrive|truecaller|claude|netflix|spotify).*" );
            if(subscription && !explicit) return null;
            String cycle=text.matches("(?is).*(yearly|annual|سنوي).*" )?"yearly":"monthly";
            String explanation="آخر سداد فعلي؛ اكتشاف الدورية يحتاج تأكيدك. مدة المنتج قد تختلف عن دورية الخصم.";
            String bank=SourceIdentity.bank(identity);
            String source=bank==null?identity.toLowerCase(Locale.ROOT):bank;
            return new CommitmentHint("Vodafone".equals(paid.merchant)?"Vodafone":(subscription?"subscription:":"bill:")+CaptureIdentity.hash(paid.merchant.toLowerCase(Locale.ROOT)+":"+paid.currency+":"+source+":"+paid.cardLast4+":"+day.getDayOfMonth()+":"+paid.amountMinor),
                    paid.merchant,subscription?"subscription":"bill",paid.amountMinor,null,
                    cycle.equals("yearly")?day.plusYears(1):day.plusMonths(1),explanation,paid.currency,cycle,false);
        }
        return null;
    }

    static String completedPlanKey(String sender,String body) {
        if(body==null || ExpenseParser.isUnsuccessful(body) || ExpenseParser.isVerificationMessage(body)) return null;
        String text=ExpenseParser.normalizeDigits(body);
        if(!text.matches("(?is).*(تم\\s+سداد\\s+جميع\\s+(?:الأقساط|الاقساط)|all\\s+installments\\s+(?:have\\s+been\\s+)?paid).*")) return null;
        Matcher ref=Pattern.compile("(?:عملية\\s+الشراء|purchase)\\s*(?:رقم|number|no\\.?)\\s*([0-9A-Za-z/.-]+)",Pattern.CASE_INSENSITIVE).matcher(text);
        return ref.find()?planKey(sender==null?"جهة غير محددة":sender.trim(),ref.group(1)):null;
    }
    private static String planKey(String sender,String reference) {
        String bank=SourceIdentity.bank(sender);
        return "plan:"+CaptureIdentity.hash((bank==null?sender.toLowerCase(Locale.ROOT):bank)+":"+reference.replaceAll("[.,;]+$",""));
    }

    private static LocalDate explicitDate(String text) {
        Matcher date=DUE_DATE.matcher(text);
        if(!date.find()) return null;
        try {
            return date.group(1)!=null?LocalDate.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3))):
                    LocalDate.of(Integer.parseInt(date.group(6)),Integer.parseInt(date.group(5)),Integer.parseInt(date.group(4)));
        } catch(RuntimeException ignored) {return null;}
    }
    private static LocalDate arabicMonthDate(String text,LocalDate received) {
        String[] months={"يناير","فبراير","مارس","أبريل|ابريل","مايو","يونيو","يوليو","أغسطس|اغسطس","سبتمبر","أكتوبر|اكتوبر","نوفمبر","ديسمبر"};
        for(int i=0;i<months.length;i++) {
            Matcher match=Pattern.compile("(?<![0-9])([0-3]?\\d)\\s*(?:"+months[i]+")").matcher(text);
            if(!match.find()) continue;
            try {
                LocalDate date=LocalDate.of(received.getYear(),i+1,Integer.parseInt(match.group(1)));
                if(date.isBefore(received.minusMonths(6))) date=date.plusYears(1);
                return date;
            } catch(RuntimeException ignored) {return null;}
        }
        return null;
    }
    private static String currency(String raw) {
        String value=raw.toUpperCase(Locale.ROOT);
        if(value.equals("USD") || value.equals("دولار")) return "USD";
        if(value.equals("EUR") || value.equals("يورو")) return "EUR";
        return value.equals("GBP")?"GBP":"EGP";
    }
}
