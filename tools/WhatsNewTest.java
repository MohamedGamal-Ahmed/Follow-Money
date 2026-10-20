package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class WhatsNewTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        String text = "## 0.3.1\r\n- أولى\r\n- ثانية\r\n\r\n## 0.3.0\n- قديمة\n";
        WhatsNew.Section latest = WhatsNew.parse(text);
        check(latest != null && "0.3.1".equals(latest.version) && latest.points.size() == 2, "newest section first");
        check("ثانية".equals(latest.points.get(1)), "points trimmed and CRLF-safe");
        check("0.3.0".equals(WhatsNew.forVersion(text, "0.3.0").version), "exact version");
        check("0.3.1".equals(WhatsNew.forVersion(text, "0.3.1-beta").version), "beta suffix ignored");
        check(WhatsNew.forVersion(text, "0.4.0") == null, "missing section is null, not an error");
        check(WhatsNew.forVersion(text, null) == null, "null version");
        check(WhatsNew.parse("") == null && WhatsNew.parse(null) == null, "empty input");
        check(WhatsNew.parse("no headings\n- orphan point") == null, "points before a heading are ignored");
        check(WhatsNew.parse("## 1.0\n## 0.9\n- x") != null && "0.9".equals(WhatsNew.parse("## 1.0\n## 0.9\n- x").version), "a heading without points is dropped");
        check(WhatsNew.readable(text).startsWith("النسخة 0.3.1\n• أولى"), "readable form");
        check(!WhatsNew.due(4, WhatsNew.ABSENT), "first install or pre-feature update: no card");
        check(WhatsNew.due(5, 4) && !WhatsNew.due(5, 5) && !WhatsNew.due(4, 5), "card only when code increased");
        check(WhatsNew.lastSeen(WhatsNew.ABSENT, false, false) == WhatsNew.ABSENT, "true first install stays silent");
        check(WhatsNew.due(4, WhatsNew.lastSeen(WhatsNew.ABSENT, true, false)), "pre-feature update with intro done shows the card");
        check(WhatsNew.due(4, WhatsNew.lastSeen(WhatsNew.ABSENT, false, true)), "pre-feature update with data shows the card");
        check(WhatsNew.lastSeen(3, false, false) == 3, "stored number wins");
        String shipped = new String(Files.readAllBytes(Paths.get("app/src/main/assets/whats-new.txt")), StandardCharsets.UTF_8);
        check(WhatsNew.parse(shipped) != null, "shipped file has a newest section");
        System.out.println("WhatsNewTest passed");
    }
}
