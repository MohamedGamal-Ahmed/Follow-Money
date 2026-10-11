package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict CSV import. Unknown bank layouts are rejected instead of guessing financial columns. */
public final class CsvImport {
    private CsvImport() { }

    public static List<Row> parse(String text) {
        List<List<String>> records = records(text.replace("\ufeff", ""));
        if (records.size() < 2) throw new IllegalArgumentException("الملف لا يحتوي عمليات");
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < records.get(0).size(); i++)
            headers.put(records.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
        int date = column(headers, "date", "التاريخ");
        int name = column(headers, "merchant", "description", "التاجر", "الوصف");
        int amount = column(headers, "amount", "المبلغ");
        int currency = optional(headers, "currency", "العملة");
        int type = optional(headers, "type", "النوع");
        int category = optional(headers, "category", "التصنيف");
        int note = optional(headers, "note", "ملاحظة");
        List<Row> result = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> cells = records.get(i);
            if (cells.size() == 1 && cells.get(0).isBlank()) continue;
            try {
                LocalDate day = parseDate(value(cells, date));
                String merchant = value(cells, name).trim();
                String rawAmount = ExpenseParser.normalizeDigits(value(cells, amount).trim());
                boolean negative = rawAmount.startsWith("-") || rawAmount.startsWith("(");
                long minor = ExpenseParser.parseMinor(rawAmount.replaceAll("[()\\-+]", ""));
                String kind = type < 0 ? negative ? "expense" : "income" : value(cells, type).trim().toLowerCase(Locale.ROOT);
                if ("مصروف".equals(kind)) kind = "expense";
                if ("دخل".equals(kind)) kind = "income";
                if (!"expense".equals(kind) && !"income".equals(kind)) throw new IllegalArgumentException();
                String curr = currency < 0 ? "EGP" : value(cells, currency).trim().toUpperCase(Locale.ROOT);
                if (!"EGP".equals(curr) && !"USD".equals(curr) && !"EUR".equals(curr)) throw new IllegalArgumentException();
                if (merchant.isEmpty() || minor <= 0) throw new IllegalArgumentException();
                String cat = category < 0 ? "غير مصنف" : value(cells, category).trim();
                result.add(new Row(i, day, merchant, minor, curr, kind, cat.isEmpty() ? "غير مصنف" : cat,
                        note < 0 ? "" : value(cells, note)));
            } catch (RuntimeException error) { throw new IllegalArgumentException("راجع بيانات السطر " + (i + 1)); }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("الملف لا يحتوي عمليات");
        return result;
    }

    private static LocalDate parseDate(String raw) {
        String value = ExpenseParser.normalizeDigits(raw.trim());
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (RuntimeException ignored) { return LocalDate.parse(value, DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT)); }
    }

    private static int column(Map<String, Integer> headers, String... names) {
        int found = optional(headers, names);
        if (found < 0) throw new IllegalArgumentException("أعمدة CSV المطلوبة: Date, Merchant, Amount");
        return found;
    }

    private static int optional(Map<String, Integer> headers, String... names) {
        for (String name : names) if (headers.containsKey(name)) return headers.get(name);
        return -1;
    }

    private static String value(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) throw new IllegalArgumentException();
        return cells.get(index);
    }

    private static List<List<String>> records(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); rows.add(row); row = new ArrayList<>(); cell.setLength(0);
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("علامات الاقتباس في CSV غير مكتملة");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    public static final class Row {
        public final int line;
        public final LocalDate date;
        public final String merchant, currency, kind, category, note;
        public final long amountMinor;
        Row(int line, LocalDate date, String merchant, long amountMinor, String currency,
            String kind, String category, String note) {
            this.line = line; this.date = date; this.merchant = merchant; this.amountMinor = amountMinor;
            this.currency = currency; this.kind = kind; this.category = category; this.note = note;
        }
    }
}
