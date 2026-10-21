package com.mohamedgamal.masareef;
import java.text.Normalizer;
import java.util.Locale;
/** Logos are presentation only; aliases never merge banks, accounts or transactions. */
final class BrandCatalog {
    private static final class Entry {
        final String id; final boolean bank; final boolean wholeName; final String[] aliases;
        Entry(String id,boolean bank,String[] aliases){this(id,bank,false,aliases);}
        /** wholeName: the alias may only match the complete name, never a longer merchant descriptor. */
        Entry(String id,boolean bank,boolean wholeName,String[] aliases){
            this.id=id;this.bank=bank;this.wholeName=wholeName;this.aliases=aliases;}
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
        new Entry("nbe",true,new String[]{"NBE","NBE Egypt","National Bank of Egypt","البنك الأهلي المصري","الأهلي المصري"}),
        new Entry("bdc",true,new String[]{"BDC","Banque du Caire","Banque Du Caire","بنك القاهرة"}),
        new Entry("cib",true,new String[]{"CIB","CIB Egypt","Commercial International Bank","البنك التجاري الدولي","التجاري الدولي"}),
        new Entry("starbucks",false,new String[]{"Starbucks","ستاربكس"}),
        new Entry("carrefour",false,new String[]{"Carrefour","كارفور"}),
        new Entry("mcdonalds",false,new String[]{"McDonald's","ماكدونالدز","MCDONALD","McDonalds","McDonald s","ماكدونالد"}),
        new Entry("kfc",false,new String[]{"KFC","كنتاكي"}),
        new Entry("ubereats",false,new String[]{"Uber Eats","UberEats"}),
        new Entry("uber",false,new String[]{"Uber","أوبر"}),
        new Entry("claude",false,new String[]{"Claude","Anthropic"}),
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
        new Entry("visa",false,true,new String[]{"Visa","فيزا"}),
        new Entry("mastercard",false,true,new String[]{"MasterCard","ماستركارد"}),
        new Entry("talabat",false,new String[]{"TALABAT","طلبات","Talabat"}),
        new Entry("cleopatra",false,new String[]{"CLEOPATRA HOSPITAL","كليوباترا","مستشفيات كليوباترا","Cleopatra Hospitals Group"}),
        new Entry("espressolab",false,new String[]{"Espressolab","Espresso Lab","إسبريسو لاب","اسبرسو لاب"}),
        new Entry("fawry",false,new String[]{"FAWRY","فوري","Fawry"}),
        new Entry("instapay",false,new String[]{"INSTAPAY","IPN","إنستاباي","InstaPay"}),
        new Entry("sharafdg",false,new String[]{"Sharaf DG","SharafDG","شرف دي جي"}),
        new Entry("centrepoint",false,new String[]{"Centrepoint","Centre Point","سنتر بوينت"}),
        new Entry("max",false,true,new String[]{"Max","Max Fashion","ماكس"}),
        new Entry("lcwaikiki",false,new String[]{"LC Waikiki","LCW","إل سي وايكيكي","ال سي وايكيكي"}),
        new Entry("raya",false,new String[]{"Raya","Raya Shop","راية","راية شوب"}),
        new Entry("aman",false,true,new String[]{"Aman","Aman Egypt","أمان"}),
        new Entry("paymob",false,new String[]{"Paymob","PAYMOB-","باي موب"}),
        new Entry("mahalawy",false,new String[]{"El Mahalawy","Elmahalawy","El Mahalawy Stores","Mahalawy","المحلاوي","المحلاوى"}),
        new Entry("pizzaking",false,new String[]{"Pizza King","PIZZA KING","بيتزا كينج","بيتزا كنج"}),
        new Entry("butlers",false,new String[]{"Butlers","Butlers & Co"}),
        new Entry("seoudi",false,new String[]{"Seoudi","Saudi Market","سعودي","سعودى"}),
        new Entry("chatgpt",false,new String[]{"ChatGPT","OpenAI ChatGPT","OpenAI"}),
        new Entry("onedrive",false,new String[]{"OneDrive"}),
        new Entry("truecaller",false,new String[]{"Truecaller"}),
        new Entry("googleone",false,new String[]{"Google One"}),
        new Entry("costa",false,new String[]{"Costa Coffee","Costa","كوستا كوفي"}),
        new Entry("etoile",false,new String[]{"Etoile","Etoile Patisserie","إتوال"}),
        new Entry("zaher",false,new String[]{"Zaher","زاهر"}),
        new Entry("teds",false,new String[]{"Ted's","Teds"}),
        new Entry("ezaby",false,new String[]{"El Ezaby","Ezaby Pharmacy","العزبي","صيدلية العزبي"}),
        new Entry("illy",false,new String[]{"illy","illy caffe"}),
        new Entry("tru",false,true,new String[]{"TRU"}),
        // The label the parser gives an InstaPay transfer that has no handle: the channel itself is the identity.
        new Entry("instapay",false,true,new String[]{"تحويل لحظي"})
    };
    static String merchant(String name){return match(MerchantClassifier.displayName(name),false);}
    static String bank(String name){return match(name,true);}
    private static String match(String name,boolean bank) {
        if(name==null)return null;String value=normalize(name);
        for(Entry e:ENTRIES)if(e.bank==bank)for(String raw:e.aliases) {
            String alias=normalize(raw);
            if(value.equals(alias))return e.id;
            // Only strong aliases can accept branch/service suffixes. Short codes and card networks require the whole name.
            if(!e.wholeName&&alias.length()>=4&&value.startsWith(alias+" "))return e.id;
        }
        return null;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("[\\p{M}]","")
                .toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا')
                .replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");
    }
}
