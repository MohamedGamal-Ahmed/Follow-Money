package com.mohamedgamal.masareef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test data reader only; it neither parses payments nor generates expected outcomes. */
final class ParserCorpusFixture {
    static final String[] COLUMNS = ("id group language channel source source_message_id notification_key " +
            "received_at_ms notification_flags android.title android.text android.bigText android.textLines " +
            "android.subText android.summaryText body expected_type expected_amount_minor expected_currency " +
            "expected_direction expected_merchant_key expected_balance_minor expected_balance_currency " +
            "expected_card_last4 expected_datetime expected_time_precision expected_decision required_reasons " +
            "expected_capture expected_capture_reason duplicate_of note").split(" ");

    static final String[] V2_EXTRA = ("expected_fee_minor expected_fee_currency expected_account_last4 expected_counterparty_token " +
            "expected_movement_kind expected_direction_certainty expected_equivalent_minor expected_equivalent_currency " +
            "expected_credit_limit_minor expected_credit_limit_currency expected_expiry_date expected_time_ambiguity " +
            "expected_year_origin context_group expected_standalone_decision expected_context_decision " +
            "source_evidence_id base_case_id variant feature_tags expected_accounting_effect").split(" ");

    static final class Row {
        private final Map<String, String> values;
        final int line;

        Row(Map<String, String> values, int line) {
            this.values = Collections.unmodifiableMap(values);
            this.line = line;
        }

        String get(String column) {
            if (!values.containsKey(column)) throw new IllegalArgumentException("Unknown corpus column: " + column);
            return values.get(column);
        }

        /** Exactly ordered distinct fragments for fixture inspection, not the production listener. */
        String inputText() {
            if ("sms".equals(get("channel"))) return get("body");
            List<String> fragments = new ArrayList<>();
            for (String column : new String[]{"android.title", "android.text", "android.bigText",
                    "android.textLines", "android.subText", "android.summaryText"}) {
                String value = get(column);
                if (value != null && !value.isBlank() && !fragments.contains(value.trim())) fragments.add(value.trim());
            }
            return String.join("\n", fragments);
        }
    }

    static List<Row> read(Path path) throws IOException {
        return readColumns(path, COLUMNS);
    }

    static List<Row> readV2(Path path) throws IOException {
        String[] columns = java.util.Arrays.copyOf(COLUMNS, COLUMNS.length + V2_EXTRA.length);
        System.arraycopy(V2_EXTRA, 0, columns, COLUMNS.length, V2_EXTRA.length);
        return readColumns(path, columns);
    }

    private static List<Row> readColumns(Path path, String[] columns) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(String.join("\t", columns)))
            throw new IllegalArgumentException("Unexpected parser corpus header");
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split("\t", -1);
            if (cells.length != columns.length)
                throw new IllegalArgumentException("Column count at corpus line " + (i + 1));
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < cells.length; c++) values.put(columns[c], decode(cells[c]));
            rows.add(new Row(values, i + 1));
        }
        return Collections.unmodifiableList(rows);
    }

    static String decode(String value) {
        if ("\\N".equals(value)) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\') { out.append(ch); continue; }
            if (++i == value.length()) throw new IllegalArgumentException("Trailing corpus escape");
            switch (value.charAt(i)) {
                case '\\': out.append('\\'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (i + 4 >= value.length()) throw new IllegalArgumentException("Short Unicode escape");
                    try { out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); }
                    catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid Unicode escape", error); }
                    i += 4;
                    break;
                default: throw new IllegalArgumentException("Unknown corpus escape: " + value.charAt(i));
            }
        }
        return out.toString();
    }

    private ParserCorpusFixture() { }
}
