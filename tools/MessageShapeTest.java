package com.mohamedgamal.masareef;

import java.util.HashSet;
import java.util.Set;

/** Redacted/user-authorized diagnostic regressions. No capture or parser acceptance changes. */
public final class MessageShapeTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static void oneShape(String name,String... messages){
        Set<String> shapes=new HashSet<>();
        for(String message:messages){String shape=SmsPreview.shape(message);shapes.add(shape);
            check(!shape.matches("(?s).*[\\p{Nd}].*"),name+": numeric data survived: "+shape);}
        check(shapes.size()==1,name+": expected one shape, got "+shapes);
    }
    public static void main(String[] args){
        oneShape("Banque Misr 3/4/5-digit amounts",
            "تم اضافة مبلغ 500EGP الى حساب رقم xxx1234 فى 21-Oct-2026 عن طريق التحويل اللحظي",
            "تم اضافة مبلغ 1500EGP الى حساب رقم xxx9876 فى 20-Nov-2025 عن طريق التحويل اللحظي",
            "تم اضافة مبلغ 15000EGP الى حساب رقم xxx1122 فى 19-Dec-2024 عن طريق التحويل اللحظي");
        oneShape("Previously redacted variable-length placeholders",
            "تم اضافة مبلغ ###EGP الى حساب رقم xxx#### فى ##-Oct-#### عن طريق التحويل اللحظي",
            "تم اضافة مبلغ ####EGP الى حساب رقم xxx### فى ##-Nov-#### عن طريق التحويل اللحظي",
            "تم اضافة مبلغ #####EGP الى حساب رقم xxx##### فى ##-Dec-#### عن طريق التحويل اللحظي");
        String[] amounts={"1.00","50.00","500.00","1,234.56","12,345.67","123,456.78","1,234,567.89","99999999.99"};
        String[] accounts={"123-456***-789","999-888***-111","********1234","xxx1234","**5678","1-22***-333","1234","9999999999999999"};
        String[] hsbc=new String[8], phoneFrom=new String[8], qnb=new String[8];
        String[] refs={"12fb34cc","1234fc56","1a23d4c5","1234567b","12345d67","deadbeef","1","98765432101234567890"};
        for(int i=0;i<8;i++){
            hsbc[i]="From HSBC: "+(i+10)+"OCT26 Salary to "+accounts[i]+" EGP "+amounts[i]+"+ Your available balance is EGP "+amounts[7-i];
            phoneFrom[i]="From HSBC: "+(i+10)+"NOV25 Phone Banking Transfer from "+accounts[i]+" "+(i%2==0?"USD":"EGP")+" "+amounts[i]+"- Your available balance is "+(i%2==0?"USD":"EGP")+" "+amounts[7-i];
            qnb[i]="IPN transfer sent with amount of EGP "+amounts[i]+" from "+(i%2==0?"**":"")+(1000+i)+" on "+(10+i)+"/10 at 03:45 "+(i%2==0?"PM":"AM")+". Ref# "+refs[i]+". For more details call 19700.";
        }
        oneShape("HSBC eight variable lengths/formats",hsbc);
        oneShape("HSBC Phone Banking Transfer from",phoneFrom);
        oneShape("QNB IPN transfer sent numeric/hex refs",qnb);
        oneShape("URLs and references",
            "Paid EGP 1.00 reference 12fb34cc https://example.invalid/path/123?x=19",
            "Paid 9999.99 EGP reference deadbeef http://example.invalid/other/4567");
        oneShape("Mask is one slot","account xxx1234","account ********1234","account 123-456***-789","account 1234");
        oneShape("Dates are one slot","on 21-Oct-2026","on 21OCT26","on 21/10/2026","on 21 أكتوبر 2026");
        oneShape("Times are one slot","at 03:45 PM","at 14:30","at ٢:٣٠ م");
        check(!SmsPreview.shape("تم اضافة مبلغ 500EGP الى حساب رقم xxx1234").equals(SmsPreview.shape("تم تحويل مبلغ 500EGP من حساب رقم xxx1234")),"Direction-bearing grammar must survive a diagnostic shape");
        check(!SmsPreview.shape("Paid EGP 100 at CAFE").equals(SmsPreview.shape("Paid EGP 100 at DEAD CAFE")),"Ordinary hex-looking merchant words must not be references");
        check(SmsPreview.shape(null).isEmpty(),"Null shape");
        System.out.println("Message shape checks passed: "+checks+"; BM 3 -> 1, HSBC 8 -> 1, Phone Banking from 8 -> 1, QNB sent 8 -> 1 (synthetic variations, not a phone rescan).");
    }
}
