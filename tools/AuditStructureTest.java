import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source-level guard for F-03: the drawing path must stay write-free while early-payment
 * normalization happens off the UI thread. Reading the source is the only way to assert this
 * without starting the real activity (whose onCreate writes the user's primary ledger).
 * It also guards the follow-up rules: the suggestion cache reloads after a local edit, a delayed
 * snapshot redraw never rebuilds an open editor, and the installed-widget path never normalizes.
 */
public final class AuditStructureTest {
    private static int checked;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }

    public static void main(String[] args) throws IOException {
        String main = read("app/src/main/java/com/mohamedgamal/masareef/MainActivity.java");
        classificationWriters();
        int homeStart = main.indexOf("private void homeStatementCard()");
        int homeEnd = main.indexOf("private void openInsights(", homeStart);
        check(!main.substring(homeStart, homeEnd).contains("setAutoSize"), "Home statement must never shrink system fonts");
        check(main.contains("in_app_update_card"), "Update strip keeps its stable tag");
        // Review of PR #53: a cancelled app-lock prompt must not leave the switch shown on.
        check(main.contains("else{appLock.enable(this::render);render();}"), "the app-lock switch redraws from the real state before the prompt answers");
        check(main.contains("transactionBucketFilter = MoneyFlow.OUTFLOW_FILTER;"), "the analytics total opens only the rows it adds");
        int bucketStart = main.indexOf("private void openBucket(MoneyFlow.Bucket bucket");
        check(bucketStart > 0 && main.substring(bucketStart, main.indexOf("render();", bucketStart)).contains("transactionStatementList = false; transactionStatementIds = null;"),
                "analytics lists clear the home statement's row ids before opening");
        check(!main.contains("attachBaseContext") && !main.contains(".fontScale="), "App must follow system font scale");
        try (java.util.stream.Stream<Path> paths = Files.walk(Path.of("app/src/main"))) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml") || p.toString().endsWith(".txt"))::iterator) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                check(!content.contains("صرف يومي") && !content.contains("باقي من دخل الفترة"), "Old statement names remain: " + path);
            }
        }
        String daily = read("app/src/main/java/com/mohamedgamal/masareef/DailyRefreshReceiver.java");
        check(!body(main, "private void render()").contains("normalizeEarlyPayments"),
                "render() must not normalize (it writes to the ledger)");
        check(body(main, "@Override protected void onCreate(Bundle state)").contains("refreshLedger(true)"),
                "onCreate must schedule the off-thread ledger pass");
        check(body(main, "@Override protected void onResume()").contains("refreshLedger(true)"),
                "onResume must schedule the off-thread ledger pass");
        String normalize = body(main, "private void refreshLedger(final boolean rerender)");
        check(normalize.contains("CaptureQueue.submit("), "the ledger pass must run on the one capture worker");
        check(normalize.contains("postUi"), "the ledger pass must return to the UI thread to re-render");
        check(normalize.contains("canRedrawNow()"),
                "the delayed redraw must re-check the current screen, not the page that requested the refresh");
        check(normalize.contains("ledgerSnapshotIsCurrent("),
                "a snapshot read before a local edit must be discarded instead of applied");
        check(normalize.contains("readLedgerSnapshot(db, version)") && normalize.contains("ledgerVersion"),
                "every pass must carry the generation it was read under");
        check(body(main, "private boolean canRedrawNow()").contains("isEditingSurface")
                        && body(main, "private boolean canRedrawNow()").contains("isFinishing()")
                        && body(main, "private boolean canRedrawNow()").contains("isDestroyed()"),
                "the redraw guard must keep the finishing/destroyed check and the editing-surface check");
        check(body(main, "static boolean isEditingSurface(int tab, boolean introVisible, boolean focusedInput)")
                        .contains("EDITOR_TAB"),
                "the commitment editor page must count as an unsaved editing surface");
        String render = body(main, "private void render()");
        check(render.contains("db.changes()") && render.contains("invalidateLedger()"),
                "render() must reload the cache once the ledger moved, so a manual edit cannot leave it stale");
        check(!main.contains("EARLY_PAYMENT_QUEUE") && !daily.contains("NORMALIZE_QUEUE"),
                "no extra writer thread may be created beside the capture worker");
        // Redesign (31 Oct 2026): detected items moved to the commitments «محتاجة رأيك» page and its counter.
        check(body(main, "private int commitmentAsks()").contains("recurringCandidates")
                        && body(main, "private void commitmentsInboxScreen()").contains("recurringCandidates"),
                "the commitment page must read the cached suggestion set");
        check(!body(main, "private void render()").contains("recurringCandidateTransactions"),
                "render() must not run the twelve-month candidate query");
        for (String page : new String[]{"private void homeScreen()", "private void sourcesScreen()",
                "private void transactionsScreen()", "private void transactionDetailsScreen(ExpenseDb.Transaction item)",
                "private void recurringScreen()", "private void commitmentsInboxScreen()", "private void commitmentDetailsScreen()",
                "private void commitmentEditorScreen()", "private void insightsScreen()",
                "private void settingsScreen()", "private void merchantRulesScreen()", "private void assetsScreen()",
                "private void debtsScreen()", "private void monthlyReportScreen()", "private void flowAccountScreen()",
                "private void introScreen()", "private void introProfileScreen()"})
            check(!body(main, page).contains("recurringCandidateTransactions"),
                    page + " must read the cached suggestion set");
        String snapshot = "static LedgerSnapshot readLedgerSnapshot(ExpenseDb db, long version)";
        check(body(main, snapshot).contains("normalizeEarlyPaymentsOnWorker(db)"),
                "the pass must settle early payments on the worker");
        check(body(main, snapshot).contains("recurringCandidateTransactions"),
                "the twelve-month candidate scan must stay on the capture worker");
        check(body(main, snapshot).contains("db.changes()"),
                "the snapshot must record the change counter it was read at");
        check(!body(daily, "static void refresh(Context context)").contains("normalizeEarlyPayments"),
                "refresh() must not normalize (render() calls it, so it would loop)");
        check(body(daily, "@Override public void onReceive(Context context,Intent intent)").contains("normalizeEarlyPayments"),
                "the daily receiver must normalize off the main thread");
        check(body(daily, "@Override public void onReceive(Context context,Intent intent)").contains("CaptureQueue.submit("),
                "the daily receiver must normalize on the capture worker");
        String dailyReceive = body(daily, "@Override public void onReceive(Context context,Intent intent)");
        check(dailyReceive.indexOf("CaptureQueue.submit(") < dailyReceive.indexOf("ACTION_DATA_CHANGED"),
                "ACTION_DATA_CHANGED must be sent after the worker's normalization, not before it");
        String sms = read("app/src/main/java/com/mohamedgamal/masareef/SmsReceiver.java");
        String receive = body(sms, "public void onReceive(Context context, Intent intent)");
        int failed = receive.indexOf("catch (RuntimeException error)");
        check(failed >= 0 && receive.indexOf("DailyRefreshReceiver.refresh(app)", failed) > failed,
                "a failed capture must still refresh the summaries on the worker");
        check(body(main, "private long optionalMinor(String raw)").contains("manualAmountMisread"),
                "the shared optional-amount helper must reject an ambiguous dot too");
        int guards = 0, inputs = 0;
        for (String line : main.split("\n")) {
            String trimmed = line.trim();
            // Pages use the same predicate directly so the error can stay next to the amount field.
            if ((trimmed.contains("rejectMisreadAmount(") || trimmed.contains("ExpenseParser.manualAmountMisread("))
                    && !trimmed.startsWith("private")) guards++;
            if (trimmed.contains("parseMinor(ExpenseParser.normalizeDigits(")) inputs++;
        }
        check(guards == inputs && inputs >= 13,
                "every manual amount input must re-check an ambiguous dot (guards " + guards + " vs inputs " + inputs + ")");
        // The widget draws from render() -> refreshWidgets() -> DailyRefreshReceiver.refresh() -> onUpdate, so the
        // whole installed-widget path must stay write-free and settle on the one capture worker instead.
        String widget = read("app/src/main/java/com/mohamedgamal/masareef/SummaryWidget.java");
        check(!widget.contains("normalizeEarlyPayments()"),
                "no widget path may normalize on the main thread it is updated from");
        String widgetUpdate = body(widget, "@Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids)");
        check(widgetUpdate.contains("CaptureQueue.submit("),
                "the widget must hand its normalization to the one capture worker");
        check(widgetUpdate.contains("MainActivity.normalizeEarlyPaymentsOnWorker("),
                "the widget must normalize through the shared worker helper");
        check(!body(widget, "static void draw(Context context, AppWidgetManager manager, int[] ids)").contains("normalizeEarlyPayments"),
                "drawing a widget must stay read-only");
        check(body(widget, "static void refresh(Context context)").contains("onUpdate("),
                "refresh() must keep rendering every installed widget");
        try (java.util.stream.Stream<Path> sources = Files.walk(Path.of("app/src/main/java"))) {
            for (Path source : (Iterable<Path>) sources.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(source);
                check(!java.util.regex.Pattern.compile("new\\s+(?:android\\.widget\\.)?Spinner\\s*\\(").matcher(code).find(),
                        "App selectors must use the themed shared component: " + source);
                check(!code.contains("simple_spinner_dropdown_item"),
                        "Native dropdown rows must not bypass the app theme: " + source);
            }
        }
        // 0.4.1 regression (tester, 24 Oct 2026): a labelled field placed in a horizontal row lost its width to its own caption.
        String components = read("app/src/main/java/com/mohamedgamal/masareef/UiComponents.java");
        check(body(components, "EditText field(String title, LinearLayout parent)").contains("LinearLayout.HORIZONTAL"),
                "A labelled field in a horizontal row must get its own column");
        check(!java.util.regex.Pattern.compile("input\\([^;]*\\);\\s*\\(\\(LinearLayout\\.LayoutParams\\)\\s*\\w+\\.getLayoutParams\\(\\)\\)\\.(?:width|weight)").matcher(main).find(),
                "A labelled field's own layout params are inside its column; size the column, not the input");
        // 0.4.5: the listener notes a live delivery of a selected app before it queues any work, so a silent system shows up.
        String listener = read("app/src/main/java/com/mohamedgamal/masareef/PaymentNotificationListener.java");
        String submit = body(listener, "private void submit(StatusBarNotification item, boolean live)");
        check(submit.contains("CaptureDiagnosticsReader.recordSeen(") && submit.contains("CaptureQueue.submit(")
                && submit.indexOf("CaptureDiagnosticsReader.recordSeen(") < submit.indexOf("CaptureQueue.submit("), "recordSeen runs before CaptureQueue.submit");
        check(submit.indexOf("acceptedPackage(item)") < submit.indexOf("CaptureDiagnosticsReader.recordSeen("), "only a selected app is noted");
        check(body(listener, "@Override public void onNotificationPosted(StatusBarNotification item)").contains("submit(item, true)"), "a posted notification is a live delivery");
        check(listener.contains("submit(item, false)"), "a replay is not a delivery");
        String support = read("app/src/main/java/com/mohamedgamal/masareef/GitHubSupport.java");
        check(support.contains("CaptureDiagnosticsReader.text(context)"), "support details include the capture state");
        String reader = read("app/src/main/java/com/mohamedgamal/masareef/CaptureDiagnosticsReader.java");
        check(!reader.contains("encrypted_body") && !reader.contains("decrypt("), "the diagnostics reader never opens a message text");
        // 0.4.5: kept messages are read again once per version on the capture worker; the stored version is written only after a
        // successful pass; a suggestion is registered through the live capture path and the re-read never writes the ledger.
        String recovery = read("app/src/main/java/com/mohamedgamal/masareef/MissedRecovery.java");
        String run = body(recovery, "static void runIfDue(Context context, Runnable whenDone)");
        check(run.contains("CaptureQueue.submit(") && run.indexOf("CaptureQueue.submit(") < run.indexOf("reevaluateMissed("), "the re-read runs on the capture worker");
        check(run.indexOf("reevaluateMissed(") < run.indexOf("putInt(VERSION_KEY"), "the version is stored only after the pass");
        check(run.contains("catch (RuntimeException | LinkageError failure)"), "a failed pass never reaches the app");
        check(body(recovery, "static MissedReevaluation.Outcome register(Context context, ShadowStore.Ununderstood message)").contains("db.capture("), "a suggestion registers through the live capture path");
        String shadowStore = read("app/src/main/java/com/mohamedgamal/masareef/ShadowStore.java");
        String reread = body(shadowStore, "static int reevaluateMissed(");
        check(!reread.contains("transactions") && !reread.contains("insert(\"transactions\""), "the re-read never writes the ledger");
        check(main.contains("MissedRecovery.runIfDue(this"), "the activity starts the re-read");
        check(main.contains("\"recovered_messages_card\"") && main.contains("MissedRecovery.register(MainActivity.this, message)"), "the strip and the register button exist");
        System.out.println("Source structure checks passed: " + checked);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    /** Enumerate SQL writes, not a fixed list of vulnerable methods: a new automatic writer must be guarded too. */
    private static void classificationWriters() throws IOException {
        try(java.util.stream.Stream<Path> files=Files.list(Path.of("app/src/main/java/com/mohamedgamal/masareef"))) {
        for(Path path:(Iterable<Path>)files.filter(p->p.toString().endsWith(".java"))::iterator) {
            String file=path.getFileName().toString().replace(".java","");
            String source=read(path.toString());
            java.util.regex.Matcher methods=java.util.regex.Pattern.compile("(?m)^    (?:public |private |protected |static |final |synchronized )*(?:[\\w<>.,?\\[\\]]+ )+(\\w+)\\([^;{]*\\)\\s*\\{").matcher(source);
            while(methods.find()) {
                String name=methods.group(1),method=body(source,methods.group().substring(0,methods.group().lastIndexOf('{')).trim());
                // Explicit user decisions set user_edited=1, including card/own-transfer confirmations.
                boolean explicit=java.util.Set.of("TransactionRepository.update","ExpenseDb.classifyReviewed","ExpenseDb.confirmReview","ExpenseDb.reconcileOwnTransfer","ExpenseDb.reconcileIncome","ExpenseDb.confirmRepeatingTransfer","ExpenseDb.markAsTransfer","CardSettlements.confirm").contains(file+"."+name);
                java.util.regex.Matcher writes=java.util.regex.Pattern.compile("(?:\\.update\\(\\s*\"transactions\"|execSQL\\(\"UPDATE transactions)[^;]+;",java.util.regex.Pattern.DOTALL).matcher(method);
                while(writes.find()) {
                    String write=writes.group();boolean classification=write.contains("needs_review")||write.contains("category");
                    java.util.regex.Matcher values=java.util.regex.Pattern.compile("\\.update\\(\\s*\"transactions\"\\s*,\\s*(\\w+)").matcher(write);
                    if(values.find()) classification=java.util.regex.Pattern.compile("\\b"+values.group(1)+"\\.put\\(\\s*\"(?:category|needs_review)\"").matcher(method).find();
                    if(!classification)continue;
                    // REPAIR_VERSION 5 gives back a review the user already made: it may only clear needs_review on edited rows.
                    if("ExpenseDb.clearReviewedFlags".equals(file+"."+name)) {
                        check(!java.util.regex.Pattern.compile("\\.put\\(\\s*\"(?:category|kind|amount_minor)\"").matcher(method).find()
                                && method.contains("put(\"needs_review\",0)") && !method.contains("put(\"needs_review\",1)") && write.contains("user_edited=1 AND needs_review=1"),
                                "ExpenseDb.clearReviewedFlags may only clear needs_review on rows the user edited: "+write);
                        continue;
                    }
                    check(explicit?method.contains("\"user_edited\",1")||method.contains("\"user_edited\", 1"):write.contains("user_edited=0"),file+"."+name+" classification/review writer needs an explicit decision or WHERE user_edited=0: "+write);
                }
            }
        }
        }
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
