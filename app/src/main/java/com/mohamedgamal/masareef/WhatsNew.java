package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;

/** Parses assets/whats-new.txt: «## versionName» headings, each followed by «- » points, newest first. */
final class WhatsNew {
    /** SharedPreferences value that means «never recorded»: a first install, or an update from before this feature. */
    static final int ABSENT = -1;

    static final class Section {
        final String version;
        final List<String> points;
        Section(String version, List<String> points) { this.version = version; this.points = points; }
    }

    private WhatsNew() {}

    /** A missing number on a device that already finished the intro or holds data is an update from before this feature. */
    static int lastSeen(int stored, boolean introCompleted, boolean hasData) {
        return stored == ABSENT && (introCompleted || hasData) ? 0 : stored;
    }

    /** Show the card only for an update; a first install or a downgrade records the number silently. */
    static boolean due(int current, int lastSeen) { return lastSeen != ABSENT && current > lastSeen; }

    /** Newest section, or null when the file has none. */
    static Section parse(String text) {
        List<Section> all = parseAll(text);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The section written for exactly this version name (a «-beta» suffix is ignored), or null. */
    static Section forVersion(String text, String versionName) {
        if (versionName == null) return null;
        String wanted = baseVersion(versionName);
        for (Section section : parseAll(text)) if (section.version.equals(wanted)) return section;
        return null;
    }

    static String baseVersion(String versionName) {
        int dash = versionName.indexOf('-');
        return (dash < 0 ? versionName : versionName.substring(0, dash)).trim();
    }

    /** The whole file as plain text for the settings screen. */
    static String readable(String text) {
        StringBuilder out = new StringBuilder();
        for (Section section : parseAll(text)) {
            if (out.length() > 0) out.append("\n\n");
            out.append("النسخة ").append(section.version);
            for (String point : section.points) out.append("\n• ").append(point);
        }
        return out.toString();
    }

    private static List<Section> parseAll(String text) {
        List<Section> sections = new ArrayList<>();
        if (text == null) return sections;
        String version = null;
        List<String> points = null;
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                if (version != null) sections.add(new Section(version, points));
                version = line.substring(3).trim();
                points = new ArrayList<>();
            } else if (version != null && line.startsWith("- ") && line.length() > 2) {
                points.add(line.substring(2).trim());
            }
        }
        if (version != null) sections.add(new Section(version, points));
        sections.removeIf(section -> section.version.isEmpty() || section.points.isEmpty());
        return sections;
    }
}
