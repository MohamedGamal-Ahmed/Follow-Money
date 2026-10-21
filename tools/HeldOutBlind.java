package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the owner's held-out blind set (tools/fixtures/held-out-blind.tsv) through normalization, classification and
 * extraction and writes a raw table. The set is never used to develop rules. "--require" fails on any miss
 * (regression gate once gaps are fixed); without it the runner only reports.
 */
public final class HeldOutBlind {
    static final String[] FIELDS = {"type", "decision", "amount", "currency", "direction", "party", "balance", "fee"};

    static String decode(String value) {
        if (value.equals("\\N")) return null;
        return value.replace("\\n", "\n");
    }

    static List<Map<String, String>> read(Path path) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) if (!line.startsWith("#")) lines.add(line);
        String[] header = lines.get(0).split("\t", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split("\t", -1);
            if (cells.length != header.length) throw new IllegalStateException("Held-out row width " + i);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) row.put(header[c], decode(cells[c]));
            rows.add(row);
        }
        return rows;
    }

    /** The new pipeline's eight compared values for one held-out message. */
    static Map<String, String> run(Map<String, String> row, long receivedAt) {
        MessageNormalizer.Raw raw = "sms".equals(row.get("channel")) ? MessageNormalizer.Raw.sms("held-out", row.get("body")) :
                MessageNormalizer.Raw.notification("held-out", 0, row.get("android.title"), row.get("android.text"), row.get("android.bigText"),
                        row.get("android.textLines"), null, null);
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(raw);
        MessageClassifier.Result classified = MessageClassifier.classify(normalized);
        TransactionExtractor.Extraction x = TransactionExtractor.extract(normalized, classified, receivedAt);
        Map<String, String> got = new LinkedHashMap<>();
        got.put("type", classified.corpusType());
        got.put("decision", x.decision);
        got.put("amount", x.amountMinor == null ? null : String.valueOf(x.amountMinor));
        got.put("currency", x.currency);
        got.put("direction", x.direction);
        got.put("party", x.merchantKey != null ? x.merchantKey : x.counterparty == null ? null : MerchantNormalizer.key(x.counterparty));
        got.put("balance", x.balanceMinor == null ? null : String.valueOf(x.balanceMinor));
        got.put("fee", x.feeMinor == null ? null : String.valueOf(x.feeMinor));
        got.put("reasons", String.join(",", x.reasons));
        List<String> extras = new ArrayList<>();
        if (x.cardLast4 != null) extras.add("card " + x.cardLast4);
        if (x.accountLast4 != null) extras.add("account " + x.accountLast4);
        if (x.creditLimitMinor != null) extras.add("limit " + x.creditLimitMinor + " " + x.creditLimitCurrency);
        if (x.accountingEffect != null) extras.add(x.accountingEffect);
        got.put("extras", String.join(", ", extras));
        return got;
    }

    static Map<String, String> expected(Map<String, String> row) {
        Map<String, String> want = new LinkedHashMap<>();
        String[] columns = {"expected_type", "expected_decision", "expected_amount_minor", "expected_currency", "expected_direction",
                "expected_party_key", "expected_balance_minor", "expected_fee_minor"};
        for (int k = 0; k < FIELDS.length; k++) want.put(FIELDS[k], row.get(columns[k]));
        return want;
    }

    static boolean equal(String field, String got, String want) {
        if (field.equals("party") && want != null) return got != null && List.of(want.split("\\|")).contains(got);
        return Objects.equals(got, want);
    }

    public static void main(String[] args) throws Exception {
        int fileArg = List.of(args).indexOf("--file");
        String file = fileArg >= 0 ? args[fileArg + 1] : "tools/fixtures/held-out-blind.tsv";
        List<Map<String, String>> rows = read(Path.of(file));
        long receivedAt = java.time.LocalDateTime.of(2026, 10, 22, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        int hits = 0;
        int[] fieldHits = new int[FIELDS.length];
        List<String> table = new ArrayList<>(), failures = new ArrayList<>();
        for (Map<String, String> row : rows) {
            Map<String, String> got = run(row, receivedAt), want = expected(row);
            List<String> cells = new ArrayList<>(), misses = new ArrayList<>();
            for (int k = 0; k < FIELDS.length; k++) {
                String f = FIELDS[k];
                boolean ok = equal(f, got.get(f), want.get(f));
                if (ok) fieldHits[k]++; else misses.add(f);
                cells.add((ok ? "" : "**") + show(got.get(f)) + (ok ? "" : " ≠ " + show(want.get(f)) + "**"));
            }
            if (misses.isEmpty()) hits++; else failures.add(row.get("id") + ": " + misses);
            table.add("| " + row.get("id") + " | " + String.join(" | ", cells) + " | " + (misses.isEmpty() ? "✓" : "✗") + " | " +
                    got.get("reasons").replace("|", "\\|") + " | " + got.get("extras") + " | " + row.get("note").replace("|", "\\|") + " |");
        }
        List<String> out = new ArrayList<>();
        out.add("Messages fully correct (all 8 fields): **" + hits + " / " + rows.size() + "**");
        out.add("");
        out.add("| Field | Correct |");
        out.add("| --- | ---: |");
        for (int k = 0; k < FIELDS.length; k++) out.add("| " + FIELDS[k] + " | " + fieldHits[k] + " / " + rows.size() + " |");
        out.add("");
        out.add("Cells show the new pipeline's value; a miss is bold as `new ≠ expected`. Party = merchant key, else the normalized counterparty.");
        out.add("");
        out.add("| # | Type | Decision | Amount (minor) | Currency | Direction | Party | Balance | Fee | All | New reasons | New extras (not scored) | Owner note |");
        out.add("| --- | --- | --- | --- | --- | --- | --- | --- | --- | :-: | --- | --- | --- |");
        out.addAll(table);
        String report = String.join("\n", out) + "\n";
        int writeArg = List.of(args).indexOf("--write");
        if (writeArg >= 0) Files.writeString(Path.of(args[writeArg + 1]), report, StandardCharsets.UTF_8);
        System.out.println("Held-out blind set " + Path.of(file).getFileName() + ": " + hits + "/" + rows.size() + " messages fully correct; misses " + failures);
        if (List.of(args).contains("--require") && hits != rows.size()) throw new AssertionError("Held-out blind regressions: " + failures);
    }

    static String show(String value) { return value == null ? "\\N" : value.replace("|", "\\|"); }
}
