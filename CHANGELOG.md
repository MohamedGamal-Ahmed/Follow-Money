# Change log

## Unreleased

- Settings search: one box on the settings home finds every setting and tool by Arabic or English keywords (hamza and taa-marbuta folded); a hit opens the owning section, scrolls to the row and highlights it without running it. App-lock options and the debug review tools are now first-level rows instead of nested menus. Privacy policy and what's new open as reading pages.
- Account naming, manual balance, merchant rules, assets, debts, debt payments, budgets, backup, restore and support/suggestion reports are pages (backup and restore keep passwords only in their fields, never in saved drafts) with inline validation; manual transaction time is editable. Fix: editing an income into an expense no longer keeps «دخل» as its category, correcting a bank no longer copies a real bank name into the friendly name, the link confirmation cannot open twice, and Back on a spoken entry asks before discarding it.
- Separate the bank from a source's friendly name and last digits. Explain debit versus credit cards during creation; let users explicitly correct a legacy bank label while preserving IDs, aliases and financial rows. Card-to-account linking now shows identifying activity, existing relationships, exclusion reasons and correction actions, with review/confirm and a not-sure exit instead of hiding unmatched sources behind an empty-list toast.
- Use one draft-aware page for manual/voice-review entry and transaction editing, with native checked type/category controls, a centered amount, currency picker, account cards, conditional transfer destination and inline validation. Preserve edit timestamps/precision, merchant-learning checks and transaction mutation results. Move transaction filters and bulk category review into pages with guarded Back and recreation support.
- Unify the runtime and framework-widget palettes for the main app and SMS import; improve income text contrast, persistent field labels, keyboard-accessible dates, focus states and scalable control heights. Present shared choice/confirmation/message dialogs as bottom sheets while retaining their native validation and neutral-button callbacks.
- Add a draft-aware in-activity form stack, first used by the display-name editor: keep edits through redraw and recreation, confirm before discarding, and return to the original settings section. Confirm before deleting a merchant rule, asset, debt or budget.

- Fix the new brand logos not showing: every catalog id now maps to its drawable (`tools/check-repo.py` fails when one does not), and the merchant-category refresh runs once more so older untouched automatic purchases pick up the new merchants' categories. Logos themselves are resolved when a row is drawn, so older transactions show them too.

- Telda notifications («تم دفع 15 ج.م. في "Kashier Butlers"») now keep the shop name instead of saving the purchase as «Telda»; any quoted shop name after في/عند/at is read the same way.

- Add Paymob, El Mahalawy and Pizza King logos (Pizza King also gets «أكل وشرب»). Logos on a solid colour now fill the whole circle, and logos on white (InstaPay, NBE, ChatGPT…) lose their empty margin so they no longer sit small inside a white ring.

- Add logos and names for Sharaf DG, Centrepoint, Max, LC Waikiki, Raya, Aman, NBE, Banque du Caire, CIB, Butlers, Seoudi, ChatGPT, OneDrive, Truecaller, Google One, Costa, Etoile, Zaher, Ted's, El Ezaby, illy and TRU (supplied by the owner); Etoile, Zaher, Ted's, El Ezaby and illy also get categories.

- Keep the figure inside the home ring on large-font phones (more padding, smaller auto-size range) and show the InstaPay logo on «تحويل لحظي» transactions.

- Message samples: collapse digit runs, references and masked accounts into one shape (`SmsPreview.shape`), so 279 HSBC messages no longer give 249 shapes; the «ما اتقرتش» section requires an amount in the text (`SmsPreview.hasAmount`), so card-delivery notices, maintenance notes and «تم إضافة الراتب» teasers without a figure stay out.

## 0.3.1 — 2026-10-21

- Redraw the home statement card: one ring for the share of the income that went out (full and coral when more went out than came in) with «باقي من دخل الفترة» in its centre, and three orbs — «دخل», «خرج», «صرف يومي» — each opening exactly its rows; the salary/transfers-in split and the definitions line stay.
- Start the statement on the day the salary really landed: a credit of at least half the declared monthly salary within seven days before or three days after the nominal pay day (instant transfers included) moves the period start to that day, and a salary that already arrived early for the coming pay day starts the new period at once; a transfer that landed on the pay day counts as the salary when no wage credit did. Without a declared salary nothing is guessed.
- Simplify the transactions header to one row: a period chip (month, or «7 أكتوبر → 13 أكتوبر») whose menu offers a month, a day-to-day range or a return to the month, and a «فلاتر» chip with the count of active filters that opens the filter sheet (now holding the type as well as category and account), plus one muted line naming the active filters; the verbose «عمليات من … · رجوع يرجعك لمكان البداية» line and the row of type chips are gone.
- Add «الرسالة اتقرت غلط؟ بلّغ عنها» to a captured transaction's options: an editable, redacted copy of the original message beside what the app recorded and a field for the correction, opening a support e-mail draft the user sends themselves.
- Group the message-samples tool by sender with up to three masked shapes each (digits and month names masked, so one bank template is one shape), and list first the messages from known banks that the parser did not read.

- Show the «جديد في النسخة» card as a headline (two points and a «كل التفاصيل» link to the full notes) and give the transactions period pickers a compact Arabic spinner instead of the calendar grid.

- Show a one-time «جديد في النسخة» card on the home screen after an update (never on a first install), with a permanent «ما الجديد» row in Settings → Help; notes are read from `assets/whats-new.txt` and `tools/check-repo.py` requires the newest section to match `versionName`.

- Keep the last three crashes locally without exception messages, with an optional reviewed crash text in support drafts; explain battery restrictions in capture settings; offer editable, masked email drafts for unrecognized SMS formats from known bank senders, excluding OTP and promotional messages.

- Add an optional device-authenticated app lock for the main ledger and SMS import, with an elapsed-time timeout, a covered screen before financial content, and an optional secure recent-app preview.

- Add a from/to date picker to the existing transactions period filter, keeping the last day inclusive in the list.
- Add a local backup and restore: one `.fmbackup` file (AES-256-GCM, key from the user's password via PBKDF2 with 200,000 rounds) holding the whole ledger with its source text, the settings and the profile picture, saved and opened through the document picker; restore shows a summary first, keeps the previous database beside the restored one until re-encryption with the device's Keystore key and the settings have all succeeded (a failure at any step, or an app start after an interrupted restore, puts the previous data back exactly as it was), and refuses a wrong password, a damaged file, a file above 200 MB, a header with an out-of-range round count or a backup from a newer app without touching current data. The home screen reminds after 30 days without a backup; the privacy policy describes the file.
- Replace the home «آخر 7 أيام» card with the month statement from the pay day to today: «دخل» (salary vs transfers that arrived), «خرج» = day-to-day spending + commitments + transfers to people + cash withdrawals, «باقي من دخل الفترة», one line per component opening exactly its rows; own-account moves and repayments to the user's own credit cards are internal, a purchase paid from cash is detail only, and an unlinked transfer worth a quarter of the income or more shows a link warning. The widget follows the same function.
- Read HSBC's trailing-sign alerts by their sign: `EGP 100.00+` is income, `EGP 100.00-` spending, the dated descriptor (TT Payment, Phone Banking Transfer, ATM Cash Withdrawal, Charges) is the merchant and the row shows the bank; a one-time repair flips credits older versions booked as spending and fixes their names, leaving user-edited and linked rows alone.

- Limit the home page's recent transactions query to the five displayed rows while preserving ledger ordering.
- Recognize explicit ChatGPT/OpenAI subscription payments as recurring suggestions and cover the first-to-next-cycle SMS journey.

- Add an owner-requested private tester APK variant with a separate Beta package, labels, signature permission and dedicated local signing key; preserve production app data and identity.

- Prepare versionCode 3 signed distribution artifacts; add an explicit lock-widget privacy notice and review clean points screenshots plus white-background widget readability. Keep oldest-Android and actual Samsung lock-screen acceptance open.

- Verify an installed APK update from `53b4682` (schema v20) to v21 with synthetic ledger, repayment, evidence and points snapshots. Add native light/dark visual checks and improve points-dialog action text contrast in dark mode.

- Preserve reimbursement endpoints, fee parents and commitment origins during transaction merges; leave conflicting automatic merges for review and reject own-transfer conversion of linked repayments.
- Count historical-import ledger growth without the 500-row display cap, and recognize Arabic sterling before generic Egyptian-pound tokens.
- Respect amount hiding in capture notifications, provide a generic lock-screen public version, and prefix formula-like CSV text cells with an apostrophe while retaining numeric amount cells.
- Add nullable v21 transaction time precision. Preserve unknown precision on legacy rows; label new date-only captures/imports and compare reimbursement eligibility by day when either endpoint has date-only precision.

- Add the owner's explicitly requested compact transparent lock-screen widget variant, reusing daily-summary calculations and privacy settings; keep home widget layouts unchanged and leave placement to the host.

- Freeze the existing feature scope on 14 October 2026; restrict the publication cycle to defect fixes, validation and Google Play preparation. Record known issues and verification limits in the release checkpoint.

- Prevent the muted opening video from requesting audio focus and interrupting background music.

- Present reimbursement selection in a native bottom sheet with selectable cards and a separate review action; display the linked purchase's current personal share under the incoming transaction and in its details.

- Guide reimbursements through same-day chronological selection, an explicit older-purchase search, read-only impact preview, guarded confirmation, success and undo.
- Preserve related transaction parents and screen/query/filter scroll context, retain details after editing, and return daily-budget/points drilldowns to their initiating dialog.
- Add optional currency-separated daily points with gains/deductions rounded to whole points per day, frozen observed daily limits, provisional today and revocable coverage confirmation for closed dates. Recompute historical scores after edits/refunds without awarding incoming repayments twice. Separate confirmed balance, yesterday and today's estimate, with 100-point milestones and a separate history/help view.

- Add closed-month summaries with currency-separated totals, spending behavior, transaction drilldowns and coverage-confirmed comparisons that invalidate after data changes.
- Add a neutral monthly notification with catch-up/deduplication, an independent opt-out, and a home card that disappears after reading while reports remain available in Analytics.

- Aggregate account analytics through confirmed card/account links, include archived linked cards in historical activity, and keep unrelated same-bank sources separate. Show own-account transfer direction separately from recorded inflows and external outflows.
- Identify unlinked card views and offer the existing explicit linking flow; retain the selected account across activity recreation.
- Add the Egyptian maker signature to Settings, onboarding, the opening and bilingual promotional templates.

- Route SMS and Notification Access permission entry points through disclosures; explain the external system speech provider before voice entry.
- Bundle a bilingual privacy policy, generate its public HTML from the same source, and document Google Play declaration/signing gates.
- Configure upload signing through local environment variables; enable release shrinking and restrict developer financial review tools to debug builds.

- Target Android 16 / API 36 with AGP 8.9.1 and Gradle 8.11.1; apply native system/keyboard insets and retain legacy back handling through the documented predictive-back opt-out.

- Parse completed payments independently of future fees/installments; reject future-only colloquial Arabic alerts.
- Preserve historical rejected captures and flag them for review instead of archiving them; retain user edits.
- Calculate reimbursement offsets on reading without shrinking the saved link, including multiple refunds and edit/reopen round trips.
- Add Arabic email support drafts, reviewed metadata and clipboard fallback; retain GitHub as an optional developer route.

- Organize Settings into six focused groups; keep review and destructive tools under Data and Privacy.
- Add GitHub bug/feature links and a preview of basic support metadata.
- Route users to a separate public support repository while keeping application source private.
- Add Arabic issue forms, a pull request template, ownership rules and monthly dependency update proposals.
- Add build/lint/JVM CI, repository privacy checks and portable local validation commands.
- Restrict automatic build-tool proposals to patch updates; larger AGP/Gradle upgrades require a coordinated compatibility review.
- Replace historical project notes in the main README with current setup, usage and calculation boundaries. Private financial audits remain local.

## 0.2.0 — development

- Arabic native UI for transactions, money flow, accounts and recurring payments.
- Local SMS and selected notification capture, editable categorization and source review.
- Daily allowances, budgets, reminders and three resizable widgets.
- Local financial insights, refund/reimbursement links and installment schedules.
- Corrections to duplicate reconciliation, future-payment detection and daily totals.

This documents the development state, not a published or signed release.
