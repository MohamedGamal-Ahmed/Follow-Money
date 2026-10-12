package com.mohamedgamal.masareef;
import java.text.Normalizer;
import java.util.Locale;
/** Logos are presentation only; aliases never merge banks, accounts or transactions. */
final class BrandCatalog {
    private static final class Entry {
        final String id; final boolean bank; final String[] aliases;
        Entry(String id,boolean bank,String[] aliases){this.id=id;this.bank=bank;this.aliases=aliases;}
    }
    private static final Entry[] ENTRIES={
        new Entry("bank_misr",true,new String[]{"BM","BM-Online","Bank Misr","بنك مصر","Banque Misr","BM Online"}),
        new Entry("qnb",true,new String[]{"QNB","QNB ALAHLI","QNB الأهلي","NSGB","QNB مصر","QNB Egypt"}),
        new Entry("fabmisr",true,new String[]{"FABMISR","FAB MISR","FAB","Bank Audi Egypt","بنك أبوظبي الأول مصر"}),
        new Entry("hsbc",true,new String[]{"HSBC","HSBC Egypt","HSBC مصر"}),
        new Entry("alexbank",true,new String[]{"ALEXBANK","Bank of Alexandria","بنك الإسكندرية"}),
        new Entry("adib",true,new String[]{"ADIB","ADIB Egypt","أبوظبي الإسلامي","مصرف أبوظبي الإسلامي مصر"}),
        new Entry("saib",true,new String[]{"SAIB","بنك الشركة المصرفية العربية الدولية","بنك saib","saib"}),
        new Entry("abc",true,new String[]{"Bank ABC","ABC Egypt","BLOM Bank Egypt","بنك ABC مصر","Bank ABC Egypt"}),
        new Entry("banknxt",true,new String[]{"Bank NXT","BANKNXT","aiBANK","Arab Investment Bank","بنك نكست"}),
        new Entry("kfh",true,new String[]{"KFH Egypt","Ahli United Bank Egypt","الأهلي المتحد مصر","بيت التمويل الكويتي مصر"}),
        new Entry("ca",true,new String[]{"Credit Agricole","CA Egypt","كريدي أجريكول","كريدي أجريكول مصر","Crédit Agricole Egypt","كريدي اجريكول"}),
        new Entry("starbucks",false,new String[]{"Starbucks","ستاربكس"}),
        new Entry("carrefour",false,new String[]{"Carrefour","كارفور"}),
        new Entry("mcdonalds",false,new String[]{"McDonald's","ماكدونالدز","MCDONALD","McDonalds","McDonald s","ماكدونالد"}),
        new Entry("kfc",false,new String[]{"KFC","كنتاكي"}),
        new Entry("uber",false,new String[]{"Uber","أوبر"}),
        new Entry("netflix",false,new String[]{"Netflix","نتفليكس"}),
        new Entry("spotify",false,new String[]{"Spotify","سبوتيفاي"}),
        new Entry("ikea",false,new String[]{"IKEA","إيكيا"}),
        new Entry("shell",false,new String[]{"Shell","شل"}),
        new Entry("airbnb",false,new String[]{"Airbnb","AIRBNB"}),
        new Entry("bookingdotcom",false,new String[]{"Booking.com","BOOKING.COM"}),
        new Entry("apple",false,new String[]{"Apple","APPLE.COM/BILL","آبل"}),
        new Entry("samsung",false,new String[]{"Samsung","سامسونج"}),
        new Entry("youtube",false,new String[]{"YouTube","YOUTUBE PREMIUM","يوتيوب"}),
        new Entry("vodafone",false,new String[]{"Vodafone","فودافون"}),
        new Entry("orange",false,new String[]{"Orange","أورنج"}),
        new Entry("paypal",false,new String[]{"PayPal","بايبال"}),
        new Entry("visa",false,new String[]{"Visa","فيزا"}),
        new Entry("mastercard",false,new String[]{"MasterCard","ماستركارد"}),
        new Entry("talabat",false,new String[]{"TALABAT","طلبات","Talabat"}),
        new Entry("cleopatra",false,new String[]{"CLEOPATRA HOSPITAL","كليوباترا","مستشفيات كليوباترا","Cleopatra Hospitals Group"}),
        new Entry("espressolab",false,new String[]{"Espressolab","Espresso Lab","إسبريسو لاب","اسبرسو لاب"}),
        new Entry("fawry",false,new String[]{"FAWRY","فوري","Fawry"}),
        new Entry("instapay",false,new String[]{"INSTAPAY","IPN","إنستاباي","InstaPay"})
    };
    static String merchant(String name){return match(MerchantClassifier.displayName(name),false);}
    static String bank(String name){return match(name,true);}
    private static String match(String name,boolean bank) {
        if(name==null)return null;String value=normalize(name);
        for(Entry e:ENTRIES)if(e.bank==bank)for(String raw:e.aliases) {
            String alias=normalize(raw);
            if(value.equals(alias))return e.id;
            // Only strong aliases can accept branch/service suffixes. Short codes require exact match.
            if(alias.length()>=4&&value.startsWith(alias+" "))return e.id;
        }
        return null;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("[\\p{M}]","")
                .toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا')
                .replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");
    }
}
