package com.mohamedgamal.masareef;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 0.4.5: which kept «ما اتسجلتش» messages come back after an update, and how a suggestion is registered. */
public final class MissedReevaluationTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    private static final long FIRST_SEEN = java.time.ZonedDateTime.of(2026, 10, 25, 14, 39, 0, 0, ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli();
    private static final long NOW = FIRST_SEEN + 2 * 24 * 3_600_000L;
    private static final Map<String, String> LABELS = Map.of("com.fawry.myfawry", "myFawry", "com.telda", "Telda");

    /** The text the store keeps for a notification: the normalized assembly of its extras. */
    private static String stored(String title, String text) {
        return MessageNormalizer.normalize(MessageNormalizer.Raw.notification("com.fawry.myfawry", 0, title, text, null, null, null, null)).text;
    }

    private static MissedReevaluation.Row notification(String source, String sourceKey, String text, long recoveredAt) {
        return new MissedReevaluation.Row("h1", "NOTIFICATION", source, sourceKey, text, FIRST_SEEN, recoveredAt);
    }

    public static void main(String[] args) {
        LocalLearning learning = new LocalLearning();
        String fawry = stored("You spent 20.00 EGP with your yellowcard", "Debit - 20.00 was spent at KASHIER*BUTLERS");

        // Version gate: first run, same version, a later one.
        check(MissedReevaluation.due(MissedReevaluation.NEVER, 15), "Never ran: due");
        check(!MissedReevaluation.due(15, 15), "Same version: not due");
        check(MissedReevaluation.due(14, 15), "Updated: due");

        // The myFawry notification kept before the fix comes back as 20.00 EGP out; an old row has no source key.
        MissedReevaluation.Recovered back = MissedReevaluation.reevaluate(notification("myFawry", null, fawry, 0), LABELS, learning, NOW);
        check(back != null, "Fawry spent notification is recovered");
        same(back.sourceKey, "com.fawry.myfawry", "Source key found from the app label");
        same(back.reading.amountMinor, 2000L, "Amount"); same(back.reading.currency, "EGP", "Currency"); same(back.reading.direction, "out", "Direction");
        check(back.reading.merchant != null && back.reading.merchant.contains("KASHIER"), "Merchant: " + back.reading.merchant);
        same(MissedReevaluation.reevaluate(notification("myFawry", "com.fawry.myfawry", fawry, 0), Map.of(), learning, NOW).reading.amountMinor, 2000L,
                "A row with its own source key needs no label match");

        // The card reads a suggestion again at display time, whatever its flag says; nothing but the sealed text is stored.
        MissedReevaluation.Reading shown = MissedReevaluation.read(notification("myFawry", "com.fawry.myfawry", fawry, NOW), "com.fawry.myfawry", NOW);
        check(shown != null && shown.amountMinor == 2000 && "out".equals(shown.direction), "The card reading of a recovered row");
        check(MissedReevaluation.read(notification("myFawry", "com.fawry.myfawry", null, NOW), "com.fawry.myfawry", NOW) == null, "No text, no reading");

        // Not candidates.
        check(MissedReevaluation.reevaluate(notification("myFawry", null, null, 0), LABELS, learning, NOW) == null, "Unreadable text stays");
        check(MissedReevaluation.reevaluate(notification("myFawry", null, fawry, NOW), LABELS, learning, NOW) == null, "Already recovered is not read again");
        check(MissedReevaluation.reevaluate(notification("Some App", null, fawry, 0), LABELS, learning, NOW) == null, "Label that names no selected app stays");
        check(MissedReevaluation.reevaluate(notification("myFawry", null, fawry, 0), Map.of("a.one", "myFawry", "b.two", "myFawry"), learning, NOW) == null,
                "Two selected apps with one label are not decidable");
        check(MissedReevaluation.reevaluate(notification(null, null, fawry, 0), LABELS, learning, NOW) == null, "No source at all");

        // Still not understood: stays where it is.
        for (String text : new String[]{"Paid EUR 1.250 at Cafe Nine", "You've spent EGP 5,000 this month", "Your OTP is 123456", "If you spend 100 EGP get 10% cashback"})
            check(MissedReevaluation.reevaluate(new MissedReevaluation.Row("h2", "SMS", "QABANK", null, text, FIRST_SEEN, 0), LABELS, learning, NOW) == null, "Not recovered: " + text);

        // An SMS row's source is its sender.
        MissedReevaluation.Recovered sms = MissedReevaluation.reevaluate(
                new MissedReevaluation.Row("h3", "SMS", "QABANK", null, "You spent 100 EGP at CARREFOUR", FIRST_SEEN, 0), LABELS, learning, NOW);
        check(sms != null && "QABANK".equals(sms.sourceKey) && sms.reading.amountMinor == 10000, "SMS row recovered under its sender");

        // A shape the user dismissed never comes back, even if the new rules would record it.
        LocalLearning dismissed = new LocalLearning();
        dismissed.recordDismissal("myFawry", fawry);
        check(MissedReevaluation.reevaluate(notification("myFawry", null, fawry, 0), LABELS, dismissed, NOW) == null, "A dismissed shape stays dismissed");

        // Batch: only the message the rules now record.
        List<MissedReevaluation.Row> rows = List.of(notification("myFawry", null, fawry, 0),
                new MissedReevaluation.Row("h4", "SMS", "QABANK", null, "Paid EUR 1.250 at Cafe Nine", FIRST_SEEN, 0));
        List<MissedReevaluation.Recovered> found = MissedReevaluation.reevaluate(rows, LABELS, learning, NOW);
        check(found.size() == 1 && "h1".equals(found.get(0).hash), "Batch recovers one of two");

        // The suggestion registers through the live capture request, at the message's own time.
        CaptureRequest request = MissedReevaluation.request(notification("myFawry", "com.fawry.myfawry", fawry, 0), "com.fawry.myfawry");
        same(request.sourceKey, "com.fawry.myfawry", "Request source key"); same(request.label, "myFawry", "Request label");
        same(request.captureAt, FIRST_SEEN, "Request keeps the message time"); same(request.channel, "إشعار: myFawry", "Notification channel");
        same(request.raw.source, "com.fawry.myfawry", "Shadow input names the package");
        CaptureRequest smsRequest = MissedReevaluation.request(new MissedReevaluation.Row("h3", "SMS", "QABANK", null, "x", FIRST_SEEN, 0), "QABANK");
        same(smsRequest.channel, "SMS", "SMS channel"); same(smsRequest.captureAt, FIRST_SEEN, "SMS time");

        // Possible duplicate hint: same instant window, or a date-only hand entry the same local day.
        ZoneId cairo = ZoneId.of("Africa/Cairo");
        check(MissedReevaluation.possiblyRecorded(FIRST_SEEN, FIRST_SEEN + 30_000, false, cairo), "Within the window");
        check(!MissedReevaluation.possiblyRecorded(FIRST_SEEN, FIRST_SEEN + 3_600_000, false, cairo), "An hour later is another operation");
        long midnight = java.time.LocalDate.of(2026, 10, 25).atStartOfDay(cairo).toInstant().toEpochMilli();
        check(MissedReevaluation.possiblyRecorded(FIRST_SEEN, midnight, true, cairo), "A date-only entry the same day");
        check(!MissedReevaluation.possiblyRecorded(FIRST_SEEN, midnight - 86_400_000L, true, cairo), "A date-only entry another day");

        // Registering: recorded, already in the ledger, or removed before leaves the list; anything else stays.
        same(MissedReevaluation.outcome(true, "INSERTED"), MissedReevaluation.Outcome.RECORDED, "Recorded");
        same(MissedReevaluation.outcome(false, "EXISTING_MATCH"), MissedReevaluation.Outcome.ALREADY_RECORDED, "Already recorded");
        same(MissedReevaluation.outcome(false, "EXCLUDED"), MissedReevaluation.Outcome.EXCLUDED, "Excluded");
        same(MissedReevaluation.outcome(false, "UNSUCCESSFUL"), MissedReevaluation.Outcome.NOT_RECORDED, "Not recorded");
        check(MissedReevaluation.removesSuggestion(MissedReevaluation.Outcome.RECORDED) && MissedReevaluation.removesSuggestion(MissedReevaluation.Outcome.ALREADY_RECORDED)
                && MissedReevaluation.removesSuggestion(MissedReevaluation.Outcome.EXCLUDED) && !MissedReevaluation.removesSuggestion(MissedReevaluation.Outcome.NOT_RECORDED), "Which outcomes remove the row");
        System.out.println("Missed message re-evaluation checks passed: " + checks);
    }
}
