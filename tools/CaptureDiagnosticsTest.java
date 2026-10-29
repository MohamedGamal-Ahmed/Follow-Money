package com.mohamedgamal.masareef;

import java.time.ZoneId;
import java.time.ZoneOffset;

/** 0.4.5 capture diagnostics text: fixed inputs to the expected lines, and nothing but flags, counts, names and times. */
public final class CaptureDiagnosticsTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(java.util.Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    public static void main(String[] args) {
        ZoneId cairo = ZoneId.of("Africa/Cairo");
        long seen = java.time.ZonedDateTime.of(2026, 10, 27, 14, 39, 0, 0, cairo).toInstant().toEpochMilli();

        CaptureDiagnostics.Snapshot s = new CaptureDiagnostics.Snapshot();
        s.notificationAccess = true; s.listenerConnected = false; s.smsPermission = true; s.defaultSmsApp = "com.google.android.apps.messaging";
        s.batteryIgnored = false;
        s.sources.add(new CaptureDiagnostics.Source("com.fawry.myfawry", "myFawry", true, seen, 3));
        s.sources.add(new CaptureDiagnostics.Source("com.telda", "Telda", false, 0, 0));
        s.missedRows = 7; s.missedUnreadable = 2;
        s.liveSms = 12; s.liveNotification = 4; s.liveFailed = 1;
        String expected = String.join("\n",
                "Notification access: on",
                "Listener connected: no",
                "Selected apps: com.fawry.myfawry(myFawry) installed=yes, com.telda(Telda) installed=no",
                "  com.fawry.myfawry: last seen 2026-10-27 14:39, today: 3",
                "  com.telda: last seen never, today: 0",
                "Default SMS app: com.google.android.apps.messaging",
                "SMS permission: on",
                "Missed list: 7 rows, 2 unreadable",
                "Live observations (7d): sms=12, notification=4, failed=1",
                "Battery optimization ignored: no");
        same(CaptureDiagnostics.format(s, cairo), expected, "Full diagnostics text");

        CaptureDiagnostics.Snapshot empty = new CaptureDiagnostics.Snapshot();
        String bare = CaptureDiagnostics.format(empty, ZoneOffset.UTC);
        check(bare.contains("Notification access: off") && bare.contains("Listener connected: no") && bare.contains("Selected apps: none"), "Empty state: " + bare);
        check(bare.contains("Default SMS app: none") && bare.contains("SMS permission: off") && bare.contains("Missed list: unavailable"), "Unavailable parts: " + bare);
        check(bare.contains("Live observations (7d): unavailable") && bare.contains("Battery optimization ignored: unknown"), "Unknown parts: " + bare);

        // A hostile or odd label stays on one short line; nothing multi-line or long reaches the text.
        CaptureDiagnostics.Snapshot odd = new CaptureDiagnostics.Snapshot();
        odd.sources.add(new CaptureDiagnostics.Source("a.b", "Bad\nName\r\nwith lines " + "x".repeat(100), true, 0, 1));
        String oddText = CaptureDiagnostics.format(odd, ZoneOffset.UTC);
        check(oddText.lines().count() == 9, "One line per fact: " + oddText.lines().count());
        check(!oddText.contains("x".repeat(61)), "Long labels are cut");

        // Daily counter: counts within a day, restarts on a new day, survives garbage.
        same(CaptureDiagnostics.bumpDaily(null, "2026-10-27"), "2026-10-27:1", "First notification today");
        same(CaptureDiagnostics.bumpDaily("2026-10-27:1", "2026-10-27"), "2026-10-27:2", "Second notification today");
        same(CaptureDiagnostics.bumpDaily("2026-10-26:9", "2026-10-27"), "2026-10-27:1", "A new day restarts");
        same(CaptureDiagnostics.bumpDaily("garbage", "2026-10-27"), "2026-10-27:1", "Unreadable counter restarts");
        same(CaptureDiagnostics.countOn("2026-10-27:5", "2026-10-27"), 5, "Count today");
        same(CaptureDiagnostics.countOn("2026-10-26:5", "2026-10-27"), 0, "Yesterday's count is not today's");
        same(CaptureDiagnostics.countOn("2026-10-27:x", "2026-10-27"), 0, "Unreadable count");
        same(CaptureDiagnostics.countOn(null, "2026-10-27"), 0, "No counter");
        same(CaptureDiagnostics.time(0, cairo), "never", "No time");

        // Structure: the snapshot has no field that could hold a message, an amount, a merchant or a card number.
        for (Class<?> type : new Class<?>[]{CaptureDiagnostics.Snapshot.class, CaptureDiagnostics.Source.class})
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                for (String banned : new String[]{"text", "body", "message", "amount", "merchant", "card"})
                    check(!name.contains(banned), "Snapshot field must not carry content: " + field.getName());
            }
        System.out.println("Capture diagnostics checks passed: " + checks);
    }
}
