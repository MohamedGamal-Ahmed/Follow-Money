package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The capture section of the support diagnostics (0.4.5): whether notifications can reach the app at all, which apps are
 * selected and when each was last seen, the SMS setup, the missed-messages list and what the live pipeline observed.
 * Pure: no Android, no storage. A Snapshot holds counts, flags, package names and times only; it has no field for a
 * message text, an amount, a merchant or a card number, so none can reach the copied text.
 */
final class CaptureDiagnostics {
    /** One notification app the user selected. lastSeenMs is 0 when no notification of it ever reached the listener. */
    static final class Source {
        final String packageName, label;
        final boolean installed;
        final long lastSeenMs;
        final int today;
        Source(String packageName, String label, boolean installed, long lastSeenMs, int today) {
            this.packageName = packageName; this.label = label; this.installed = installed; this.lastSeenMs = lastSeenMs; this.today = today;
        }
    }

    static final class Snapshot {
        boolean notificationAccess, listenerConnected, smsPermission;
        /** null when the system would not say. */
        Boolean batteryIgnored;
        final List<Source> sources = new ArrayList<>();
        String defaultSmsApp;
        /** -1 when the list could not be read. */
        int missedRows = -1, missedUnreadable;
        int liveSms = -1, liveNotification, liveFailed;
    }

    /** The lines appended to the diagnostics text; times are shown in the given zone. */
    static String format(Snapshot s, ZoneId zone) {
        StringBuilder out = new StringBuilder();
        line(out, "Notification access: " + yesNo(s.notificationAccess, "on", "off"));
        line(out, "Listener connected: " + yesNo(s.listenerConnected, "yes", "no"));
        if (s.sources.isEmpty()) line(out, "Selected apps: none");
        else {
            StringBuilder apps = new StringBuilder();
            for (Source source : s.sources) {
                if (apps.length() > 0) apps.append(", ");
                apps.append(clean(source.packageName)).append('(').append(clean(source.label)).append(") installed=").append(yesNo(source.installed, "yes", "no"));
            }
            line(out, "Selected apps: " + apps);
            for (Source source : s.sources)
                line(out, "  " + clean(source.packageName) + ": last seen " + time(source.lastSeenMs, zone) + ", today: " + source.today);
        }
        line(out, "Default SMS app: " + (s.defaultSmsApp == null || s.defaultSmsApp.isEmpty() ? "none" : clean(s.defaultSmsApp)));
        line(out, "SMS permission: " + yesNo(s.smsPermission, "on", "off"));
        line(out, "Missed list: " + (s.missedRows < 0 ? "unavailable" : s.missedRows + " rows, " + s.missedUnreadable + " unreadable"));
        line(out, "Live observations (7d): " + (s.liveSms < 0 ? "unavailable" : "sms=" + s.liveSms + ", notification=" + s.liveNotification + ", failed=" + s.liveFailed));
        line(out, "Battery optimization ignored: " + (s.batteryIgnored == null ? "unknown" : yesNo(s.batteryIgnored, "yes", "no")));
        return out.toString().trim();
    }

    private static void line(StringBuilder out, String text) { out.append(text).append('\n'); }
    private static String yesNo(boolean value, String yes, String no) { return value ? yes : no; }

    static String time(long millis, ZoneId zone) {
        if (millis <= 0) return "never";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /** A package name or app label as one short line; line breaks and anything past 60 characters never reach the text. */
    static String clean(String value) {
        if (value == null) return "?";
        String one = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return one.length() > 60 ? one.substring(0, 60) : one;
    }

    // Per-day counter kept in SharedPreferences as "yyyy-MM-dd:count" (a number, never any message content).

    /** The stored daily counter after one more notification today; a counter from another day restarts at 1. */
    static String bumpDaily(String stored, String today) {
        return today + ":" + (countOn(stored, today) + 1);
    }

    /** Notifications counted for today in a stored counter, 0 when it is from another day or unreadable. */
    static int countOn(String stored, String today) {
        if (stored == null || today == null) return 0;
        int colon = stored.lastIndexOf(':');
        if (colon < 0 || !stored.substring(0, colon).equals(today)) return 0;
        try { return Math.max(0, Integer.parseInt(stored.substring(colon + 1))); } catch (NumberFormatException unreadable) { return 0; }
    }

    private CaptureDiagnostics() {}
}
