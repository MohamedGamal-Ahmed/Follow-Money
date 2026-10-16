import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source-level guard for F-03: the drawing path must stay write-free while early-payment
 * normalization happens off the UI thread. Reading the source is the only way to assert this
 * without starting the real activity (whose onCreate writes the user's primary ledger).
 */
public final class AuditStructureTest {
    private static int checked;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }

    public static void main(String[] args) throws IOException {
        String main = read("app/src/main/java/com/mohamedgamal/masareef/MainActivity.java");
        String daily = read("app/src/main/java/com/mohamedgamal/masareef/DailyRefreshReceiver.java");
        check(!body(main, "private void render()").contains("normalizeEarlyPayments"),
                "render() must not normalize (it writes to the ledger)");
        check(body(main, "@Override protected void onCreate(Bundle state)").contains("refreshLedger(true)"),
                "onCreate must schedule the off-thread ledger pass");
        check(body(main, "@Override protected void onResume()").contains("refreshLedger(true)"),
                "onResume must schedule the off-thread ledger pass");
        String normalize = body(main, "private void refreshLedger(final boolean rerender)");
        check(normalize.contains("CaptureQueue.submit("), "the ledger pass must run on the one capture worker");
        check(normalize.contains("runOnUiThread"), "the ledger pass must return to the UI thread to re-render");
        check(normalize.contains("isFinishing()") && normalize.contains("isDestroyed()"),
                "the re-render must keep the finishing/destroyed guard");
        check(!main.contains("EARLY_PAYMENT_QUEUE") && !daily.contains("NORMALIZE_QUEUE"),
                "no extra writer thread may be created beside the capture worker");
        check(body(main, "private void recurringScreen()").contains("recurringCandidates"),
                "the commitment page must read the cached suggestion set");
        check(!body(main, "private void render()").contains("recurringCandidateTransactions"),
                "render() must not run the twelve-month candidate query");
        for (String page : new String[]{"private void homeScreen()", "private void sourcesScreen()",
                "private void transactionsScreen()", "private void transactionDetailsScreen(ExpenseDb.Transaction item)",
                "private void recurringScreen()", "private void commitmentDetailsScreen()",
                "private void commitmentEditorScreen()", "private void insightsScreen()",
                "private void settingsScreen()", "private void merchantRulesScreen()", "private void assetsScreen()",
                "private void debtsScreen()", "private void monthlyReportScreen()", "private void flowAccountScreen()",
                "private void introScreen()", "private void introProfileScreen()"})
            check(!body(main, page).contains("recurringCandidateTransactions"),
                    page + " must read the cached suggestion set");
        check(body(main, "static LedgerSnapshot readLedgerSnapshot(ExpenseDb db)").contains("recurringCandidateTransactions"),
                "the twelve-month candidate scan must stay on the capture worker");
        check(!body(daily, "static void refresh(Context context)").contains("normalizeEarlyPayments"),
                "refresh() must not normalize (render() calls it, so it would loop)");
        check(body(daily, "@Override public void onReceive(Context context,Intent intent)").contains("normalizeEarlyPayments"),
                "the daily receiver must normalize off the main thread");
        check(body(daily, "@Override public void onReceive(Context context,Intent intent)").contains("CaptureQueue.submit("),
                "the daily receiver must normalize on the capture worker");
        check(body(main, "private long optionalMinor(String raw)").contains("manualAmountMisread"),
                "the shared optional-amount helper must reject an ambiguous dot too");
        int guards = 0, inputs = 0;
        for (String line : main.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("rejectMisreadAmount(") && !trimmed.startsWith("private")) guards++;
            if (trimmed.contains("parseMinor(ExpenseParser.normalizeDigits(")) inputs++;
        }
        check(guards == inputs && inputs >= 13,
                "every manual amount input must re-check an ambiguous dot (guards " + guards + " vs inputs " + inputs + ")");
        System.out.println("Source structure checks passed: " + checked);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    /** Body of the named method, matched by brace depth while skipping comments and literals. */
    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Missing method: " + signature);
        int open = source.indexOf('{', start + signature.length());
        if (open < 0) throw new AssertionError("Missing body: " + signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i++;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < source.length() && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\') i++;
                    i++;
                }
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(open, i + 1);
            }
        }
        throw new AssertionError("Unterminated method: " + signature);
    }
}
