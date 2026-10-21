package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** v2 fixture/oracle audit, not a new parser. --require-ready fails on unmet owner acceptance gates. */
public final class ParserCorpusV2Test {
    private static int checks;
    private static final Set<String> FROZEN = Set.of("notification_fields","dedupe_identity","promotions","other_and_failed");
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static long number(String value){return Long.parseLong(value);}
    private static void expect(ParserCorpusFixture.Row row,String field,String value){check(Objects.equals(row.get(field),value),row.get("id")+" "+field+": "+row.get(field)+" != "+value);}

    private static String language(String input){
        // Count words, not dates, ISO monetary units, masked values or URLs; merchant/app/bank words still count.
        String text=SmsPreview.shape(input).replace("<slot>","");
        boolean arabic=false,latin=false;
        for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);offset+=Character.charCount(cp);
            if(Character.isLetter(cp)){
                Character.UnicodeScript script=Character.UnicodeScript.of(cp);
                arabic|=script==Character.UnicodeScript.ARABIC;latin|=script==Character.UnicodeScript.LATIN;
            }
        }
        return arabic&&latin?"mixed":arabic?"ar":latin?"en":"neutral";
    }

    private static Map<String,String> sourceLevels(Path path)throws Exception{
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);
        check(lines.get(0).equals("evidence_id\tlevel\turl\tlocator\tlimitations"),"Evidence registry header");
        Map<String,String> result=new HashMap<>();
        for(int i=1;i<lines.size();i++){
            String[] fields=lines.get(i).split("\t",-1);check(fields.length==5,"Source registry width "+i);
            check(result.put(fields[0],fields[1])==null,"Duplicate source evidence ID");
            if(fields[1].startsWith("public_"))check(fields[2].startsWith("https://github.com/"),"Missing inspected public source URL");
        }
        return result;
    }

    private static void oracleRegressions(Map<String,ParserCorpusFixture.Row> ids){
        expect(ids.get("p134"),"expected_decision","AUTO");expect(ids.get("p134"),"expected_amount_minor","50000");
        expect(ids.get("p134"),"expected_fee_minor","500");expect(ids.get("p134"),"expected_fee_currency","EGP");
        expect(ids.get("p137"),"expected_currency","USD");expect(ids.get("p137"),"expected_amount_minor","1250");expect(ids.get("p137"),"expected_decision","AUTO");
        expect(ids.get("p140"),"expected_direction","out");expect(ids.get("p140"),"expected_decision","REVIEW");expect(ids.get("p140"),"expected_direction_certainty","likely");
        for(String id:new String[]{"u01","u02","u07","u13","u14"}){
            expect(ids.get(id),"expected_decision","AUTO");expect(ids.get(id),"expected_merchant_key",null);
        }
        expect(ids.get("u01"),"expected_account_last4","1234");expect(ids.get("u01"),"expected_card_last4",null);
        expect(ids.get("u01-amount4"),"expected_amount_minor","150000");check(ids.get("u01-amount4").inputText().contains("1500EGP"),"Four-digit amount fixture text");
        expect(ids.get("u01-amount5"),"expected_amount_minor","1500000");check(ids.get("u01-amount5").inputText().contains("15000EGP"),"Five-digit amount fixture text");
        expect(ids.get("u03"),"expected_decision","REVIEW");expect(ids.get("u03"),"expected_accounting_effect","pending");
        expect(ids.get("u04"),"expected_type","promo");expect(ids.get("u04"),"expected_amount_minor",null);
        check(ids.get("u04").get("required_reasons").contains("MISSING_AMOUNT"),"Salary teaser without amount");
        expect(ids.get("u06"),"expected_direction","in");expect(ids.get("u06"),"expected_amount_minor","1234567");
        expect(ids.get("u06"),"expected_account_last4",null); // Three visible suffix digits cannot establish last FOUR.
        expect(ids.get("u07"),"expected_account_last4","1234");expect(ids.get("u07"),"expected_counterparty_token","5678");
        expect(ids.get("u07"),"expected_merchant_key",null);expect(ids.get("u07"),"expected_datetime","2026-10-21T14:30");
        expect(ids.get("u08"),"expected_type","transaction");expect(ids.get("u08"),"expected_amount_minor","10000");expect(ids.get("u08"),"expected_decision","REVIEW");
        expect(ids.get("u08"),"expected_expiry_date","2026-12-31");
        for(String reason:new String[]{"COMPLETED_EVENT","PROMO_SIGNALS","EXPIRING_CREDIT"})check(ids.get("u08").get("required_reasons").contains(reason),"Cashback reason "+reason);
        expect(ids.get("u09"),"expected_direction","in");check(ids.get("u09").inputText().contains("Transfer to"),"Contradictory to/sign input retained");
        expect(ids.get("u10"),"expected_direction","out");check(ids.get("u10").inputText().contains("Transfer from"),"Contradictory from/sign input retained");
        expect(ids.get("u12"),"expected_direction","in");expect(ids.get("u12"),"expected_movement_kind","inward_remittance");
        expect(ids.get("u13"),"expected_datetime","2026-10-21T15:45");expect(ids.get("u13-new_year"),"expected_datetime","2026-12-31T15:45");
        expect(ids.get("u14-truncated"),"expected_decision","AUTO");expect(ids.get("u14-truncated"),"expected_time_ambiguity","missing_am_pm");
        expect(ids.get("u14-truncated"),"expected_time_precision","date");check(ids.get("u14-truncated").inputText().endsWith("at 03:45"),"Exact requested truncation");
        expect(ids.get("u15-secure_key"),"expected_type","verification");expect(ids.get("u15-secure_key"),"expected_decision","IGNORE");
        expect(ids.get("pub-cib-charge"),"expected_balance_minor",null);expect(ids.get("pub-cib-charge"),"expected_credit_limit_minor","1000021");
        expect(ids.get("pub-cib-refund"),"expected_amount_minor","93");expect(ids.get("pub-cib-refund"),"expected_movement_kind","refund");
    }

    private static void contextRegressions(Map<String,ParserCorpusFixture.Row> ids){
        for(String group:new String[]{"phone_cross_currency","phone_same_currency","phone_boundary_10m","phone_outside_10m"}){
            ParserCorpusFixture.Row out=ids.get(group+"-out"),in=ids.get(group+"-in");
            long elapsed=number(in.get("received_at_ms"))-number(out.get("received_at_ms"));
            boolean expectedReview=!group.equals("phone_outside_10m");
            check((elapsed<=600000)==expectedReview,"Ten-minute pairing boundary "+group);
            expect(out,"expected_standalone_decision","AUTO");expect(in,"expected_standalone_decision","AUTO");
            expect(out,"expected_direction","out");expect(in,"expected_direction","in");
            expect(out,"source","HSBC");expect(in,"source","HSBC");
            for(ParserCorpusFixture.Row row:List.of(out,in)){
                expect(row,"expected_context_decision",expectedReview?"REVIEW":"AUTO");
                expect(row,"expected_decision",expectedReview?"REVIEW":"AUTO");
                expect(row,"expected_capture",expectedReview?"REVIEW_INTERNAL_TRANSFER":"NEW");
                expect(row,"expected_accounting_effect",expectedReview?"pending_internal":null);
            }
            if(group.equals("phone_cross_currency"))check(!out.get("expected_currency").equals(in.get("expected_currency")),"Cross-currency pair");
            else{check(out.get("expected_currency").equals(in.get("expected_currency")),"Same-currency pair");check(out.get("expected_amount_minor").equals(in.get("expected_amount_minor")),"Equal principal pair");}
        }
    }

    public static void main(String[] args)throws Exception{
        Path folder=Path.of("tools/fixtures");
        List<ParserCorpusFixture.Row> old=ParserCorpusFixture.read(folder.resolve("parser-corpus.tsv"));
        String v1Text=Files.readString(folder.resolve("parser-corpus.tsv"),StandardCharsets.UTF_8).replace("\r\n","\n");
        String v1Hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v1Text.getBytes(StandardCharsets.UTF_8)));
        check(v1Hash.equals("628cc295cbbddc7bda70fe90a0dee6c9125df27563140b8b4e0a61d0a6ed806e"),"Historical v1 comparison file changed");
        List<ParserCorpusFixture.Row> rows=ParserCorpusFixture.readV2(folder.resolve("parser-corpus-v2-draft.tsv"));
        Map<String,String> levels=sourceLevels(folder.resolve("corpus-v2-sources.tsv"));
        Map<String,ParserCorpusFixture.Row> ids=new LinkedHashMap<>();
        Map<String,Integer> languages=new HashMap<>(),decisions=new HashMap<>();
        Map<String,Set<String>> sourceShapes=new java.util.TreeMap<>();
        Set<String> featureTags=new HashSet<>();int tx=0,egp=0,publicBases=0,userBases=0;
        check(rows.size()>=240,"At least the originally requested 240 cases");
        for(ParserCorpusFixture.Row row:rows){
            String id=row.get("id");check(ids.put(id,row)==null,"Duplicate v2 ID "+id);
            check(!row.get("group").equals("source_independence"),"Rejected source-label padding remains");
            check(levels.containsKey(row.get("source_evidence_id")),"Missing source evidence "+id);
            check(row.get("base_case_id")!=null&&row.get("variant")!=null,"Missing variant lineage "+id);
            check(Set.of("transaction","promo","otp","verification","balance_inquiry","reminder","other").contains(row.get("expected_type")),"Type "+id);
            check(Set.of("AUTO","REVIEW","UNUNDERSTOOD","IGNORE").contains(row.get("expected_decision")),"Decision "+id);
            check(row.get("required_reasons")!=null&&!row.get("required_reasons").isBlank(),"Missing reasons "+id);
            decisions.merge(row.get("expected_decision"),1,Integer::sum);
            languages.merge(language(row.inputText()),1,Integer::sum);
            featureTags.addAll(List.of(row.get("feature_tags").split("\\|")));
            if(row.get("variant").equals("base")&&row.get("group").equals("public_documented"))publicBases++;
            if(row.get("variant").equals("base")&&row.get("group").equals("user_masked"))userBases++;
            // Generated field-order permutations and paired scenarios are not additional observed formats.
            if(!row.get("source_evidence_id").equals("legacy-v1")&&row.get("variant").equals("base"))
                sourceShapes.computeIfAbsent(row.get("source"),k->new HashSet<>()).add(SmsPreview.shape(row.inputText()));
            if(row.get("expected_type").equals("transaction")){tx++;if("EGP".equals(row.get("expected_currency")))egp++;}
            else{
                expect(row,"expected_decision","IGNORE");expect(row,"expected_amount_minor",null);expect(row,"expected_direction",null);
                expect(row,"expected_capture","NONE");
            }
            if(Set.of("AUTO","REVIEW").contains(row.get("expected_decision"))){check(number(row.get("expected_amount_minor"))>0,"Nonpositive booked principal "+id);check(Set.of("in","out").contains(row.get("expected_direction")),"Accepted direction missing "+id);check(row.get("expected_currency")!=null,"Accepted currency missing "+id);}
            if(row.get("expected_currency")!=null)check(Set.of("EGP","USD","EUR","GBP","SAR","AED").contains(row.get("expected_currency")),"Invalid transaction currency "+id);
            check((row.get("expected_balance_minor")==null)==(row.get("expected_balance_currency")==null),"Balance currency pair "+id);
            if(row.get("expected_balance_minor")!=null)number(row.get("expected_balance_minor"));
            for(String prefix:new String[]{"expected_fee","expected_equivalent","expected_credit_limit"}){
                String amount=row.get(prefix+"_minor"),currency=row.get(prefix+"_currency");
                check((amount==null)==(currency==null),"Optional amount/currency pair "+id+" "+prefix);
                if(amount!=null)check(number(amount)>=0,"Negative auxiliary amount "+id);
            }
            for(String field:new String[]{"expected_account_last4","expected_card_last4"})if(row.get(field)!=null)check(row.get(field).matches("[0-9]{4}"),"Invented account/card digits "+id);
            if(row.get("expected_datetime")!=null){if(row.get("expected_time_precision").equals("minute"))LocalDateTime.parse(row.get("expected_datetime"));else LocalDate.parse(row.get("expected_datetime"));}
            if(row.get("expected_expiry_date")!=null)LocalDate.parse(row.get("expected_expiry_date"));
        }
        int protectedRows=0,removed=0;
        for(ParserCorpusFixture.Row row:old){
            if(FROZEN.contains(row.get("group"))){protectedRows++;check(ids.containsKey(row.get("id")),"Protected fixture lost");
                for(String col:ParserCorpusFixture.COLUMNS)check(Objects.equals(row.get(col),ids.get(row.get("id")).get(col)),"Protected field changed "+row.get("id")+" "+col);}
            if(row.get("group").equals("source_independence")){removed++;check(!ids.containsKey(row.get("id")),"Rejected padding ID remains");}
        }
        check(protectedRows==66&&removed==72,"Preservation/removal inventory");
        for(ParserCorpusFixture.Row row:rows){
            check(ids.containsKey(row.get("base_case_id")),"Missing variant parent "+row.get("id"));
            if(Set.of("digits","spaces","field_order").contains(row.get("variant"))){
                ParserCorpusFixture.Row parent=ids.get(row.get("base_case_id"));
                for(String field:new String[]{"expected_type","expected_amount_minor","expected_currency","expected_direction","expected_merchant_key","expected_balance_minor","expected_balance_currency","expected_card_last4","expected_datetime","expected_time_precision","expected_decision","expected_account_last4","expected_movement_kind"})
                    check(Objects.equals(row.get(field),parent.get(field)),"Mechanical variant changed expected meaning "+row.get("id")+" "+field);
            }
        }
        oracleRegressions(ids);contextRegressions(ids);
        List<String> blockers=new ArrayList<>();
        if(languages.getOrDefault("ar",0)*100<rows.size()*40)blockers.add("Arabic words-only share below 40%");
        if(languages.getOrDefault("en",0)*100<rows.size()*30)blockers.add("English share below 30%");
        if(languages.getOrDefault("mixed",0)*100>rows.size()*30)blockers.add("Mixed share above 30%");
        if(egp*100<tx*80)blockers.add("EGP transactions below 80%");
        int review=decisions.getOrDefault("REVIEW",0);if(review<15||review>25)blockers.add("REVIEW count outside 15..25");
        for(String feature:new String[]{"ipn_explicit_fee","cib_dear_customer_purchase","nbe_dear_customer_purchase","dual_currency_equivalent","reversal","installment_payment","wallet_topup","fawry_bill_payment","card_decline_observed","vodafone_cash_observed","instapay_direct_observed"})
            if(!featureTags.contains(feature))blockers.add("Missing documented sample: "+feature);
        Path report=Path.of(".tmp/corpus-v2-audit.md");Files.createDirectories(report.getParent());
        List<String> output=new ArrayList<>();
        output.add("# Corpus v2 draft audit");output.add("");output.add("Fixture integrity/oracle assertions: PASS ("+checks+"). Parser semantic evaluation: NOT RUN.");
        output.add("Rows: "+rows.size()+"; protected v1 rows unchanged: "+protectedRows+"; rejected padding rows removed: "+removed+".");
        output.add("Provenance: "+userBases+" user-supplied base cases + one owner-specified descriptor expansion; "+publicBases+" public base examples. Variants are not additional observed formats.");
        output.add("Decisions: "+new java.util.TreeMap<>(decisions));
        output.add("");output.add("| Measured language | Count | Share |");output.add("| --- | ---: | ---: |");
        for(String lang:new String[]{"ar","en","mixed","neutral"})output.add(String.format(Locale.ROOT,"| %s | %d | %.2f%% |",lang,languages.getOrDefault(lang,0),100.0*languages.getOrDefault(lang,0)/rows.size()));
        output.add(String.format(Locale.ROOT,"\nEGP transactions: %d/%d (%.2f%%); unresolved-currency transactions stay in the denominator.",egp,tx,100.0*egp/tx));
        output.add("");output.add("## Remaining approval blockers");output.add("");
        for(String blocker:blockers)output.add("- "+blocker);
        output.add("");output.add("Status: "+(blockers.isEmpty()?"READY FOR OWNER REVIEW; NOT APPROVED":"NOT READY FOR APPROVAL")+". Normalization/pipeline implementation is prohibited until explicit approval.");
        output.add("");output.add("## Base-case diagnostic shape inventory");output.add("");
        output.add("Only base rows are counted. Generated variations, field-order permutations and paired-context rows are excluded. Distinct financial/nonfinancial base families may have different profiles; these are not current bank-format or phone-rescan counts.");output.add("");
        for(var entry:sourceShapes.entrySet())output.add("- "+entry.getKey()+": "+entry.getValue().size()+" base-case profiles.");
        Files.writeString(report,String.join("\n",output)+"\n",StandardCharsets.UTF_8);
        System.out.println("V2 fixture integrity passed: "+checks+" assertions; "+rows.size()+" rows; "+protectedRows+" protected rows unchanged; "+removed+" padding rows removed.");
        System.out.println("Measured languages: "+new java.util.TreeMap<>(languages)+"; EGP "+egp+"/"+tx+"; REVIEW "+review+".");
        System.out.println("Parser semantic evaluation: NOT RUN. Readiness: "+(blockers.isEmpty()?"pending explicit owner approval":blockers.size()+" approval blockers")+". Report: "+report);
        if(List.of(args).contains("--require-ready")&&!blockers.isEmpty())throw new IllegalStateException("Corpus v2 is not ready: "+String.join("; ",blockers));
    }
}
