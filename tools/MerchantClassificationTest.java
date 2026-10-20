package com.mohamedgamal.masareef;

public final class MerchantClassificationTest {
    private static int checked;
    private static void eq(Object expected,Object actual) {
        if(!java.util.Objects.equals(expected,actual)) throw new AssertionError("Expected "+expected+", got "+actual);
        checked++;
    }
    private static void purchase(String descriptor,String name,String category) {
        // Synthetic bank wording, not a phone export. The balance must never become the expense.
        ExpenseParser.Parsed parsed=ExpenseParser.parse("BANK","تم الآن خصم 88.25 EGPعند "+descriptor+
                " يوم 12/10/2026 ، الرصيد المتاح EGP 900.00 من بطاقة ****1234");
        if(parsed==null) throw new AssertionError("Missing purchase: "+descriptor);
        eq(8825L,parsed.amountMinor);eq("EGP",parsed.currency);eq(name,parsed.merchant);eq(category,parsed.category);
    }
    public static void main(String[] args) {
        purchase("KASHIER*BUTLERS & CO C","Butlers","أكل وشرب");
        purchase("paymob * BUTLERS & CO CAIRO","Butlers","أكل وشرب");
        purchase("SEOUDI MARKET SODIC E","Seoudi","بقالة");
        purchase("SEOUDI-Sodic East","Seoudi","بقالة");
        purchase("سعودى ماركت","Seoudi","بقالة");
        purchase("ESPRESSO LAB-CENTER ALMA","Espresso Lab","مقاهي");
        purchase("اسبرسو لاب","Espresso Lab","مقاهي");
        purchase("CLEOPATRA HOSPITAL","Cleopatra Hospital","رعاية صحية");
        purchase("KASHIER*QA UNLISTED STORE","QA UNLISTED STORE","غير مصنف");
        eq(true,MerchantClassifier.sameMerchant("KASHIER*QA UNLISTED STORE","QA UNLISTED STORE"));
        eq(false,MerchantClassifier.sameMerchant("KASHIER*QA UNLISTED STORE","KASHIER*QA OTHER STORE"));
        eq("رعاية صحية",ExpenseParser.category("QA PHARMACY"));
        eq("مقاهي",ExpenseParser.category("QA COFFEE HOUSE"));
        eq("أكل وشرب",ExpenseParser.category("QA RESTAURANT"));
        eq("أكل وشرب",ExpenseParser.category("UBER EATS CAIRO"));
        eq("مواصلات",ExpenseParser.category("UBER TRIP"));
        for(String unknown:new String[]{"Superuber Shop","My Cleopatra Hospital Company","Cleopatra Boutique","Espresso Machines","KASHIER*QA UNLISTED STORE","ButlersXYZ"}) eq(null,MerchantClassifier.category(unknown));
        eq(true,MerchantClassifier.sameMerchant("KASHIER*BUTLERS & CO C","Butlers"));
        eq(true,MerchantClassifier.sameMerchant("SEOUDI-Sodic East","SEOUDI MARKET"));
        eq(false,MerchantClassifier.sameMerchant("QA STORE EAST","QA STORE WEST"));
        eq(true,MerchantClassifier.matchesRule("KASHIER*BUTLERS & CO C","=butlers"));
        eq(false,MerchantClassifier.matchesRule("ButlersXYZ","=butlers"));
        eq(false,MerchantClassifier.matchesRule("Vegas","gas"));
        eq(false,MerchantClassifier.matchesRule("Superuber Shop","uber"));
        eq("cleopatra",BrandCatalog.merchant("PAYMOB*CLEOPATRA HOSPITAL CAIRO"));
        eq("espressolab",BrandCatalog.merchant("ESPRESSOLAB MALL"));
        eq("instapay",BrandCatalog.merchant("تحويل لحظي"));
        for(String[] pair:new String[][]{{"butlers","Butlers & Co"},{"seoudi","سعودي"},{"chatgpt","OpenAI ChatGPT"},{"costa","COSTA COFFEE"},{"etoile","Etoile"},
                {"zaher","زاهر"},{"teds","Ted's"},{"ezaby","صيدلية العزبي"},{"illy","ILLY"},{"tru","TRU"},{"googleone","Google One"},{"onedrive","OneDrive"},{"truecaller","Truecaller"}})
            eq(pair[0],BrandCatalog.merchant(pair[1]));
        eq("nbe",BrandCatalog.bank("NBE"));eq("bdc",BrandCatalog.bank("Banque du Caire"));eq("bdc",BrandCatalog.bank("بنك القاهرة"));eq("cib",BrandCatalog.bank("CIB"));
        eq("أكل وشرب",MerchantClassifier.category("Etoile"));eq("رعاية صحية",MerchantClassifier.category("العزبي"));
        eq("تحويلات",ExpenseParser.parse("BANK","تم تحويل 88.25 EGP من حسابك الى Butlers يوم 12/10/2026").category);
        for(int screen:new int[]{0,1,5,7}) eq(0,NavigationState.destination(screen,6));
        for(int screen:new int[]{3,4,8,9,13,14}) eq(4,NavigationState.destination(screen,6));
        for(int screen:new int[]{2,11,12}) eq(2,NavigationState.destination(screen,6));
        eq(6,NavigationState.destination(6,6));eq(6,NavigationState.destination(10,6));
        eq(4,NavigationState.destination(10,4));eq(0,NavigationState.destination(10,0));
        System.out.println("Merchant classification checks passed: "+checked);
    }
}
