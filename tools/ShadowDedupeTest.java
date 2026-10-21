package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shadow dedupe against the corpus dedupe contract (identity first, 60 seconds only for candidates), replayed in order. */
public final class ShadowDedupeTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    /** The delivery identity a real device would attach to this corpus row. */
    static String identity(ParserCorpusFixture.Row row) {
        if ("notification".equals(row.get("channel")))
            return MessageNormalizer.Raw.notificationIdentity(row.get("notification_key"), Long.parseLong(row.get("received_at_ms")));
        return MessageNormalizer.Raw.smsIdentity(row.get("source_message_id"));
    }

    static ShadowDedupe.Delivery delivery(ParserCorpusFixture.Row row, long id) {
        String text = MessageNormalizer.normalize(MessageNormalizerTest.raw(row)).text;
        return new ShadowDedupe.Delivery(id, row.get("source"), identity(row), Long.parseLong(row.get("received_at_ms")),
                ShadowDedupe.contentHash(row.get("source"), text));
    }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        List<ShadowDedupe.Delivery> seen = new ArrayList<>();
        Map<Long, String> idOf = new HashMap<>();
        int contract = 0;
        for (ParserCorpusFixture.Row row : rows) {
            if (!"dedupe_identity".equals(row.get("group"))) continue;
            long id = seen.size();
            ShadowDedupe.Delivery d = delivery(row, id);
            ShadowDedupe.Verdict v = ShadowDedupe.decide(d, seen);
            same(v.capture, row.get("expected_capture"), "Capture " + row.get("id"));
            same(v.reason, row.get("expected_capture_reason"), "Reason " + row.get("id"));
            same(v.duplicateOf < 0 ? null : idOf.get(v.duplicateOf), row.get("duplicate_of"), "Duplicate of " + row.get("id"));
            seen.add(d); idOf.put(id, row.get("id"));
            contract++;
        }
        same(contract, 10, "All ten dedupe contract rows replayed");

        // Probes beyond the corpus rows.
        String h = ShadowDedupe.contentHash("BANK", "Paid EGP 85 at \"Nova Market\".");
        String other = ShadowDedupe.contentHash("BANK", "Paid EGP 86 at \"Nova Market\".");
        long t = 1_000_000L, w = ParsingPolicy.DUPLICATE_CANDIDATE_WINDOW_MS;
        List<ShadowDedupe.Delivery> one = List.of(new ShadowDedupe.Delivery(7, "BANK", null, t, h));
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(8, "BANK", null, t + w, h), one).capture, "REVIEW_DUPLICATE", "Window edge is inside");
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(8, "BANK", null, t + w + 1, h), one).capture, "NEW", "Past the window, text alone never suppresses");
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(8, "OTHER", null, t + 1, h), one).capture, "NEW", "Another source is another delivery");
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(8, "BANK", null, t + 1, other), one).capture, "NEW", "Other content is another payment");
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(8, "BANK", "sms:9", t + 1, h), one).capture, "NEW", "An identified delivery is never a text candidate");
        List<ShadowDedupe.Delivery> identified = List.of(new ShadowDedupe.Delivery(3, "BANK", "sms:9", t, h));
        ShadowDedupe.Verdict replayLate = ShadowDedupe.decide(new ShadowDedupe.Delivery(4, "BANK", "sms:9", t + 10 * w, h), identified);
        same(replayLate.toString(), "REPLAY/DELIVERY_REPLAY@3", "Identity proves a replay outside the window");
        same(ShadowDedupe.decide(new ShadowDedupe.Delivery(4, "BANK", "sms:9", t, other), identified).toString(), "REVIEW_DUPLICATE/IDENTITY_CONFLICT@3",
                "Same identity, other content: review, never a merge");
        same(MessageNormalizer.Raw.smsIdentity(""), null, "An empty inbox ID is no identity");
        same(MessageNormalizer.Raw.notificationIdentity("0|pkg|1|t|10000", 5L), "notification:0|pkg|1|t|10000@5", "Key plus original post time");
        same(MessageNormalizer.normalize(MessageNormalizer.Raw.sms("BANK", "x").withIdentity("sms:1")).text, "x", "Identity never reaches the text");

        // Owner decision: a same-amount return from the same source minutes after a charge offsets it, never income.
        long t0 = 1_000_000L, win = ParsingPolicy.FEE_REFUND_WINDOW_MS;
        java.util.List<ShadowOffset.Movement> charges = java.util.List.of(
                new ShadowOffset.Movement(1, "Banque Misr", t0, "out", 500L, "EGP", true, false),
                new ShadowOffset.Movement(2, "Banque Misr", t0 + 60_000, "out", 500L, "EGP", true, true),
                new ShadowOffset.Movement(3, "HSBC", t0, "out", 500L, "EGP", true, false));
        java.util.Set<Long> none = java.util.Set.of();
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "EGP", true, false), charges, none), 2L, "Latest same-amount charge of the same source");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "EGP", true, false), charges, java.util.Set.of(2L)), 1L, "A claimed charge is never offset twice");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 60_000 + win + 1, "in", 500L, "EGP", true, false), charges, none), -1L, "Outside the window it is not a return");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 600L, "EGP", true, false), charges, none), -1L, "Another amount is another movement");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "EGP", true, true), charges, none), -1L, "A credit from a named party is not a return");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "USD", true, false), charges, none), -1L, "Another currency is another movement");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 - 1, "in", 500L, "EGP", true, false), charges, none), -1L, "A credit before the charge offsets nothing");
        // A refund or reversal may name the merchant; it then pairs only with a charge at the same merchant.
        java.util.List<ShadowOffset.Movement> atShops = java.util.List.of(
                new ShadowOffset.Movement(4, "Banque Misr", t0, "out", 500L, "EGP", true, true, "butlers"),
                new ShadowOffset.Movement(5, "Banque Misr", t0 + 60_000, "out", 500L, "EGP", true, true, "seoudi"));
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "EGP", true, true, "butlers"), true, atShops, none), 4L, "Reversal pairs with the same merchant");
        same(ShadowOffset.match(new ShadowOffset.Movement(9, "Banque Misr", t0 + 5 * 60_000, "in", 500L, "EGP", true, true, "butlers"), false, atShops, none), -1L, "A plain credit naming a party is never a return");

        System.out.println("Shadow dedupe checks passed: " + checks + " (" + contract + " corpus contract rows replayed in order).");
    }
}
