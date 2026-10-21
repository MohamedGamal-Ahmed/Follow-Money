package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixture integrity gate. Passing this does NOT mean any parser understood the 240 messages. */
public final class ParserCorpusTest {
    private static int checks;

    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }

    private static void oneOf(String value, String why, String... allowed) {
        check(value != null && Set.of(allowed).contains(value), why + ": " + value);
    }

    private static void invalid(Runnable action, String why) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid fixture: " + why);
    }

    private static long number(String value, String why) {
        check(value != null && value.matches("-?[0-9]+"), why + " is not an integer");
        return Long.parseLong(value);
    }

    private static void validateRows(List<ParserCorpusFixture.Row> rows) {
        check(rows.size() == 240, "Exactly 240 authored expectations required");
        Map<String, ParserCorpusFixture.Row> ids = new LinkedHashMap<>();
        Map<String, Integer> groups = new HashMap<>();
        Map<String, Integer> types = new HashMap<>();
        Set<String> currencies = new HashSet<>();
        Set<String> sources = new HashSet<>();
        Set<String> inputs = new HashSet<>();
        int summaries = 0, indian = 0, persian = 0, bidi = 0;
        for (int index = 0; index < rows.size(); index++) {
            ParserCorpusFixture.Row r = rows.get(index);
            String id = r.get("id");
            check(id.equals(String.format(java.util.Locale.ROOT, "p%03d", index + 1)), "Stable ordered ID at " + r.line);
            check(ids.put(id, r) == null, "Duplicate fixture ID " + id);
            check(r.get("note") != null && !r.get("note").isBlank(), "Missing rationale " + id);
            groups.merge(r.get("group"), 1, Integer::sum);
            types.merge(r.get("expected_type"), 1, Integer::sum);
            sources.add(r.get("source"));
            oneOf(r.get("language"), id, "ar", "en", "mixed");
            oneOf(r.get("channel"), id, "sms", "notification");
            oneOf(r.get("expected_type"), id, "transaction", "promo", "otp", "balance_inquiry", "reminder", "other");
            oneOf(r.get("expected_decision"), id, "AUTO", "REVIEW", "UNUNDERSTOOD", "IGNORE");
            oneOf(r.get("expected_capture"), id, "NEW", "NONE", "REPLAY", "REVIEW_DUPLICATE");
            oneOf(r.get("expected_time_precision"), id, "unknown", "date", "minute");
            check(number(r.get("received_at_ms"), id) > 0, "Missing capture time " + id);
            check(r.get("required_reasons") != null && r.get("required_reasons").matches("[A-Z_]+(?:\\|[A-Z_]+)*"), "Reason codes " + id);
            check(r.get("source") != null && !r.get("source").isBlank(), "Missing source " + id);
            if ("sms".equals(r.get("channel"))) {
                check(r.get("body") != null, "Missing SMS body " + id);
                check(r.get("notification_key") == null, "SMS unexpectedly has Android key " + id);
                for (String field : new String[]{"android.title", "android.text", "android.bigText", "android.textLines", "android.subText", "android.summaryText"})
                    check(r.get(field) == null, "SMS has notification extras " + id);
            } else {
                check(r.get("body") == null, "Notification must test real extras, not preassembled body " + id);
                check(r.get("notification_key") != null, "Notification missing identity " + id);
                check(r.get("source_message_id") == null, "Notification has SMS identity " + id);
                if ((number(r.get("notification_flags"), id) & 512) != 0) summaries++;
            }
            String input = r.inputText();
            check(input != null, "Missing input " + id);
            inputs.add(input);
            if (input.matches("(?s).*[\\u0660-\\u0669].*")) indian++;
            if (input.matches("(?s).*[\\u06f0-\\u06f9].*")) persian++;
            if (input.matches("(?s).*[\\u200e\\u200f\\u202a-\\u202e].*")) bidi++;
            if (r.get("expected_currency") != null) {
                oneOf(r.get("expected_currency"), id, "EGP", "USD", "EUR", "GBP", "SAR", "AED");
                currencies.add(r.get("expected_currency"));
            }
            if (r.get("expected_amount_minor") != null) number(r.get("expected_amount_minor"), id);
            if (r.get("expected_direction") != null) oneOf(r.get("expected_direction"), id, "in", "out");
            if (r.get("expected_balance_minor") != null) {
                number(r.get("expected_balance_minor"), id);
                check(r.get("expected_balance_currency") != null, "Balance currency missing " + id);
            } else check(r.get("expected_balance_currency") == null, "Currency with no balance " + id);
            if (r.get("expected_card_last4") != null)
                check(r.get("expected_card_last4").matches("[0-9]{4}"), "Four card digits including zeros " + id);
            if (r.get("expected_merchant_key") != null)
                check(!r.get("expected_merchant_key").isBlank() && r.get("expected_merchant_key").matches("[\\p{L}\\p{N}]+(?: [\\p{L}\\p{N}]+)*"), "Literal normalized merchant key " + id);
            if ("AUTO".equals(r.get("expected_decision")) || "REVIEW".equals(r.get("expected_decision"))) {
                check("transaction".equals(r.get("expected_type")), "Nontransaction accepted " + id);
                check(number(r.get("expected_amount_minor"), id) > 0, "Accepted nonpositive amount " + id);
                check(r.get("expected_currency") != null && r.get("expected_direction") != null, "Essential fields missing " + id);
            }
            if (!"transaction".equals(r.get("expected_type"))) {
                check("IGNORE".equals(r.get("expected_decision")), "Nontransaction must be ignored " + id);
                check("NONE".equals(r.get("expected_capture")), "Rejected message must not create ledger row " + id);
                for (String field : new String[]{"expected_amount_minor", "expected_currency", "expected_direction", "expected_merchant_key", "expected_balance_minor", "expected_balance_currency", "expected_card_last4", "expected_datetime"})
                    check(r.get(field) == null, "Transaction extraction after classifier rejection " + id);
            }
            if ("UNUNDERSTOOD".equals(r.get("expected_decision"))) check("NONE".equals(r.get("expected_capture")), "Ununderstood creates no ledger row " + id);
            if ("unknown".equals(r.get("expected_time_precision"))) check(r.get("expected_datetime") == null, "Invented event time " + id);
            else if ("date".equals(r.get("expected_time_precision"))) LocalDate.parse(r.get("expected_datetime"));
            else LocalDateTime.parse(r.get("expected_datetime"));
            if (r.get("duplicate_of") != null) {
                ParserCorpusFixture.Row previous = ids.get(r.get("duplicate_of"));
                check(previous != null && previous != r, "Dedupe target must precede case " + id);
                check(previous.get("source").equals(r.get("source")) && previous.inputText().equals(input), "Dedupe source/text mismatch " + id);
                check(Set.of("REPLAY", "REVIEW_DUPLICATE").contains(r.get("expected_capture")), "Unexpected duplicate marker " + id);
            } else check(!Set.of("REPLAY", "REVIEW_DUPLICATE").contains(r.get("expected_capture")), "Missing duplicate target " + id);
            if ("REPLAY".equals(r.get("expected_capture"))) check("DELIVERY_REPLAY".equals(r.get("expected_capture_reason")), "Replay reason " + id);
            else if ("REVIEW_DUPLICATE".equals(r.get("expected_capture"))) check("IDENTITY_UNAVAILABLE".equals(r.get("expected_capture_reason")), "Ambiguous replay reason " + id);
            else check(r.get("expected_capture_reason") == null, "Unexpected capture reason " + id);
        }
        check(groups.equals(Map.ofEntries(Map.entry("notification_fields",24), Map.entry("source_independence",72),
                Map.entry("normalization",24), Map.entry("amount_roles",24), Map.entry("money_movements",16),
                Map.entry("promotions",24), Map.entry("verification",16), Map.entry("balance_inquiries",12),
                Map.entry("reminders",10), Map.entry("other_and_failed",8), Map.entry("dedupe_identity",10))), "Coverage group counts changed");
        check(types.equals(Map.of("transaction",164,"promo",25,"otp",17,"balance_inquiry",13,"reminder",11,"other",10)), "Type coverage changed");
        check(currencies.size() == 6, "Six currencies required");
        for (String source : new String[]{"فيصل الإسلامي", "ADIB", "بنك مصر", "الأهلي", "CIB", "QNB", "Vodafone Cash", "InstaPay", "Fawry", "Telda", "Paymob", "Kashier", "NEWBANK_2099"})
            check(sources.contains(source), "Missing source " + source);
        check(inputs.size() >= 180, "Corpus degenerated into source-label-only variations");
        check(summaries >= 3 && indian >= 10 && persian >= 1 && bidi >= 2, "Missing summary/numeral/bidi coverage");
        ParserCorpusFixture.Row telda = rows.get(0), starred = rows.get(1);
        check("تم دفع 15 ج.م. في \"Kashier Butlers\"\nرصيدك: 31.5 ج.م.".equals(telda.get("android.title")), "Exact title-only Telda fixture lost");
        check("".equals(telda.get("android.text")) && telda.get("android.bigText") == null, "Empty text must differ from absent bigText");
        check(starred.get("android.title").contains("KASHIER*BUTLERS") && "butlers".equals(starred.get("expected_merchant_key")), "Starred processor regression lost");
        check("butlers".equals(telda.get("expected_merchant_key")), "Bare processor regression lost");
        check(rows.get(231).get("expected_capture").equals("NEW"), "Different notification IDs keep both purchases");
        check(!rows.get(230).get("notification_key").equals(rows.get(231).get("notification_key")), "Distinct notification fixture IDs");
        check(rows.get(233).get("expected_capture").equals("NEW"), "Reused Android key cannot identify a payment alone");
        check(rows.get(235).get("expected_capture").equals("NEW"), "Different inbox IDs keep both purchases");
        check(rows.get(238).get("expected_capture").equals("REVIEW_DUPLICATE"), "No-ID ambiguity must not silently merge");
        check(Long.parseLong(rows.get(239).get("received_at_ms")) - Long.parseLong(rows.get(238).get("received_at_ms")) > 60000,
                "Final no-ID case must be outside the candidate window of the most recent delivery, not just the first");
        System.out.println("Parser corpus fixture contract: 240 rows; " + inputs.size() + " distinct text inputs; " + checks + " integrity checks passed.");
        System.out.println("Types: " + new java.util.TreeMap<>(types));
        System.out.println("Parser semantic evaluation: NOT RUN (pipeline not implemented in this delivery).");
    }

    public static void main(String[] args) throws Exception {
        check(ParserCorpusFixture.decode("\\N") == null, "Null marker");
        check("".equals(ParserCorpusFixture.decode("")), "Explicitly empty input");
        check("\\N".equals(ParserCorpusFixture.decode("\\\\N")), "Literal backslash N");
        check("x\n\t\r\u200f".equals(ParserCorpusFixture.decode("x\\n\\t\\r\\u200f")), "Control character decoding");
        invalid(() -> ParserCorpusFixture.decode("\\"), "trailing escape");
        invalid(() -> ParserCorpusFixture.decode("\\q"), "unknown escape");
        invalid(() -> ParserCorpusFixture.decode("\\u12"), "short Unicode escape");
        invalid(() -> ParserCorpusFixture.decode("\\uGGGG"), "nonhex Unicode escape");
        Path path = args.length == 0 ? Path.of("tools/fixtures/parser-corpus.tsv") : Path.of(args[0]);
        validateRows(ParserCorpusFixture.read(path));
        // Reader must reject truncated rows and a wrong header; this is a real fixture corruption guard.
        Path temp = Files.createTempFile("follow-money-corpus-", ".tsv");
        try {
            Files.writeString(temp, "wrong header\n", StandardCharsets.UTF_8);
            invalid(() -> { try { ParserCorpusFixture.read(temp); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }, "wrong header");
            Files.writeString(temp, String.join("\t", ParserCorpusFixture.COLUMNS) + "\np001\ttruncated\n", StandardCharsets.UTF_8);
            invalid(() -> { try { ParserCorpusFixture.read(temp); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }, "truncated row");
        } finally { Files.deleteIfExists(temp); }
        System.out.println("Corpus reader corruption guards passed; total integrity checks: " + checks);
    }
}
