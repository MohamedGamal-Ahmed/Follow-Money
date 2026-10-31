# Change log

## Unreleased — 31 October 2026 · commitments, accounts, intro, monthly summary (redesign step 3 of 3)

- Commitments: one card with what is left this month and a progress bar; everything detected (plans from messages, repeating payments, confirmations) behind one «محتاجة رأيك» strip that opens its own page; the kind filter is a segmented control; the calendar opens from «التقويم»; rows show the progress count and a due chip (lime when it is today or tomorrow).
- Commitment details: the merchant is the title; one hero with the next payment, its date, the progress and «علّمه مدفوع»; «الدفعات» and «الإعدادات» sections (reminder, auto-match, paid from, last installment); end and delete actions at the bottom. The long explanation line is gone.
- Commitment editor: segmented kind control, soft provider tiles with a brand outline on the chosen one, a shorter auto-match hint.
- Accounts: one total («كل اللي معاك») and one row per account with its balance; a row opens the account page; a card with no bank opens the bank picker. The two long notes are gone. The account page keeps its numbers with shorter notes and goes back where it came from.
- First run: three full-color slides (one title, one line, one example, «التالي»), then the optional profile step.
- Monthly summary: a page of التحليلات with a month switcher, a hero total and the same report; two long notes are gone.
- Tests: instrumentation tests open «محتاجة رأيك» and the calendar first; `AuditStructureTest` follows the detected items to their page.
- Review of PR #57: the commitment hero's «فاضل» adds each remaining installment from its stored cycle (partial payments deducted) or the plan price, per currency (`CommitmentEdit.left`, 6 new checks): it walks the schedule, skips cycles already settled early or out of order, and never multiplies the due amount by the count. Accounts show every currency's balance (row, total and a «الأرصدة» list on the account page); «سجل الأرصدة» asks which currency when there is more than one.

## Unreleased — 31 October 2026 · home, transactions, details, add (redesign step 2 of 3)

- Home: one dark hero (what left since pay day, with «دخل» and «الفرق» tiles; tapping «خرج»/«دخل» still opens what each is made of, under the hero); several small notices (what's new, recovered messages, month summary, backup) become one «N حاجات محتاجة منك نظرة» line that lists them, a single one keeps its own strip; the next commitment is one clean card; accounts are small tiles (bank color, name, last balance) instead of full card faces.
- Transactions: the search box is always shown with the filters button beside it; «الكل · خرج · دخل» segmented control beside the period; the two banners become compact chips; day headers are stronger and show only the totals that exist; the statement list drops its second explanation line.
- Transaction details: a hero card (badge, name, amount, date) and three actions — تعديل، الرسالة، خيارات أكتر — instead of the ⋯ icon; the «رجوع إلى …» line and the redundant «النوع» row are gone.
- Add / edit transaction: no «نوع العملية» caption over the segmented control; categories are a 4-column grid of icon tiles (the most used seven and «كل التصنيفات»).

## Unreleased — 31 October 2026 · shared components (redesign step 1 of 3)

Owner-approved redesign, applied through the parts every screen is built from, so no screen keeps the old look.

- Sheets (all ~89 choice and confirm dialogs): grab handle on top; confirm and cancel side by side (a third action gets its own row); cancel is a soft filled button; choice lists are plain rows with dividers instead of outlined boxes.
- Fields: bold caption above, 52dp tall, 16dp corners. Buttons: bold, 52dp, 16dp corners; secondary buttons are a soft fill without an outline. Icon buttons lose their outline.
- Option pickers (`UiComponents.choices`) are a segmented control: one rounded track, the chosen option a raised pill.
- Form pages: the header only goes back (the duplicate ✓ save icon is gone); the save button stays at the bottom while the form scrolls.
- Cards: 22dp corners and a soft lift in light mode; section titles 17sp. Inline links lose the underline (bold brand color marks them).
## Unreleased — 31 October 2026 · analytics and settings redesign

From user feedback (too much text, analytics hard to read, settings look dated, switches unclear), owner-approved designs.

- Analytics opens on one summary page («مصاريفك»): total spending with the change from the same days of the previous month (or year), a daily (or monthly) spending line, the biggest category, the daily average, and «فلوسك راحت فين؟» rows (category icon, amount, bar, share) that open their transactions. Commitments and transfers waiting for a purpose are rows too. Other currencies get one quiet line, never added to the pound. The older reports moved behind «تقارير أكتر»; the duplicate «تكلفة المعيشة» report and the definition notes are gone. `AnalyticsMath` holds the comparison period, change and per-day numbers (JVM checks).
- Settings are one page in groups (التسجيل، الميزانية والويدجت، الإشعارات، المظهر والأمان، بياناتك، المساعدة) with a fixed icon per row, real switches for every on/off setting (widget amounts, notifications, app lock), a three-way appearance control, and «أدوات متقدمة» folded away (merchant rules, assets, debts, data check, old SMS, missed messages, CSV, midnight refresh, opening, device notification settings, diagnostics). Search stays and opens the fold for an advanced hit. Old sub-page links land on the page scrolled to their group.
- «منع لقطات الشاشة» is removed: it only worked with the app lock on. The window flag is still cleared for windows that had it.
- The GitHub developer link and the long support/e-mail notes left settings.

## Unreleased — 29 October 2026

From an owner report (salary month, HSBC → Banque Misr transfer, credit-card repayment).

- An instant transfer from the user's own name («تحويل لحظي … من <الاسم>») is a move between own accounts once that name is confirmed: «المحوِّل ده أنا» on an income from a person's name, or the renamed settings page «حساباتك واسمك في التحويلات», which now accepts a name as the bank prints it besides an InstaPay handle.
- Money paid into a credit card («تم إيداع … بالبطاقة الائتمانية», «credit card payment … received») is never income: it is a transfer into the card. A refund or reversal on the card is not a repayment. Shape-based (`CaptureNotice.cardDeposit`), no bank template.
- A debit «من حساب … الى كارت ائتمان» that names no card digits repays the user's only live credit card (two cards and no digits: no guess) and no longer waits for review. The bank's debit and the card's receipt of the same repayment (same amount and currency, within the own-transfer window) are one row, whichever arrives first.
- Installment providers valU, Souhoola, Premium Card, Halan, Contact, Sympl, MoGo, MoneyFellows, Seven, Forsa and Takka are in the commitment provider tiles, named merchants file under «أقساط», and they show owner-supplied logos (Breadfast and Waffarha too).
- One payment can cover several installments (schema 28): when a linked payment is bigger than the chosen cycle needs, the link page lists the open installments from that cycle on, ticks as many as the payment covers, and links them in one step; the last one may stay partly paid and any extra stays unallocated (still linkable later). The payment's own amount caps the sum of its links across all commitments. The unique index is now one active link per (transaction, cycle) instead of per transaction.
- Tests: `FeatureAuditTest.testOnePaymentCoversSeveralInstallments`; `ShadowStoreTest` expects schema 28.
- Commitments, from an owner report of 30 Oct 2026 (Banque Misr card installments, 5,xxx shown while 4,439 was due):
  - Editing the amount of a commitment that is not closed asks «للقسط الجاي بس» or «للقسط الجاي وكل اللي بعده» and sets the due installment's own amount (stored cycles used to keep their first price forever, so the edit never showed). Never below what that installment already received.
  - «علّمه مدفوع» → «تأكيد يدوي» lists the open installments with their date and remaining amount and settles the one picked, instead of silently closing the oldest open cycle.
  - A repayment into one of the user's own credit cards (bank debit and card receipt, one row) is a payment candidate for any commitment, so card installments can be settled by the card repayment. It stays a move into the card: linking never turns it into an «أقساط» expense.
  - A deleted commitment can be brought back: the commitments page shows «اتحذف «…» · تراجع» until dismissed or another commitment is saved, and restores the row with its cycles and settlements.
  - The editor shows the due installment's amount and currency together; a save that leaves that price untouched keeps the plan's own amount and currency (e.g. an open 4,439 EGP installment under a 100 USD plan), and the due installment never takes an amount in another currency.
  - Tests: `CommitmentEditTest` (JVM); `FeatureAuditTest` — edited amount (only / forward / below paid), second save, other-currency plan, chosen-cycle confirmation, card repayment link, delete-and-restore (emulator).
- Tests: `CaptureNoticeTest` card-deposit cases, `MerchantClassificationTest` provider logos/category, `FeatureAuditTest.testOwnNameTransferAndCardRepaymentLegsAreNeitherIncomeNorSpending` (emulator).

## 0.4.5 — 27 October 2026 (versionCode 15, schema 27)

From an owner-phone report (Honor, myFawry yellowcard purchases not recorded).

- myFawry «spent» notifications are card purchases: «You spent 20.00 EGP with your yellowcard» / «Debit - 20.00 was spent at KASHIER*BUTLERS» is recorded automatically (20.00 EGP, outgoing). The rule is wording, not the bare word: «was/has been spent at|in|on|with» and «you spent <amount> at|with|on|in|using». A spending summary («You've spent EGP 5,000 this month», «You have spent … so far this week», «Total spent in October», «you spent … in October») is `OTHER`/`SPENDING_SUMMARY` and never recorded; a summary clause no longer hides a real purchase in the same message. Classifier version classification-4, policy 2026-10-29.3. The corpus v2 (455 rows) and held-out sets 1–5 are unchanged row for row.
- Support diagnostics (the text copied from «نسخ بيانات التشخيص», the support email and the GitHub report) now describe capture: notification access, listener connected, selected apps with installed / last seen / count today, default SMS app and SMS permission, missed-list rows and unreadable rows, live observations in the last 7 days (sms, notification, failed) and battery-optimization exemption. Counts, flags, times and package names only; the snapshot has no field for message text, amount, merchant or card. The listener notes a live delivery of a selected app (time and a per-day counter in SharedPreferences) before queueing any work; a replay of an already-visible notification is not a delivery.
- Kept «ما اتسجلتش» messages are read again once per app version (schema 27 adds `source_key` and `recovered_at` to `ununderstood_messages`; the key is a notification's package or an SMS sender, taken from the shadow input). A message the current rules would record is marked recovered and waits as a suggestion: a home strip («التحديث الجديد فهم N رسالة كانت فاتتك ›») opens the missed-messages page, whose first section lists each suggestion with source, date, the new reading (amount, direction, merchant), the redacted text and «ممكن تكون متسجلة بالفعل» when the ledger holds the same amount and direction near the message time. «سجّلها» records it through the live capture path (exclusions, dedupe, settlement and legacy guards apply; twice records once); «تجاهل» teaches the dismissed shape. Nothing is written to the ledger without the user. The re-read runs on the capture worker; a failure leaves the stored version unset so the next launch retries. The reading shown on a card is derived from the sealed text when displayed, so no amount is stored outside `encrypted_body`.
- Tests: `MissedReevaluationTest` and `CaptureDiagnosticsTest` in run-checks; myFawry and summary cases in `TransactionExtractorTest`; source-structure checks for the listener, the re-read and the register path in `AuditStructureTest`; `ShadowStoreTest` section 11 (schema 26 → 27 with a kept row, re-read, double register) for the emulator.

## 0.4.4 — 26 October 2026 (versionCode 14)

Classification fixes from the owner report of 26 Oct 2026 (PR #45).

- Banque Misr credit-card messages that glue «في» to a Latin merchant and the merchant to «بتاريخ» («فيOPENAI *CHATGPTبتاريخ») name the merchant again: ChatGPT, Google One, Google Truecall and Google Play are recorded automatically as «اشتراكات» instead of «Banque Misr · غير مصنف» waiting for review. A Latin merchant ends where the Arabic text starts. Extractor version extraction-5. Rows already recorded under the bank's name are not rewritten.
- Another bank's ATM charged on a card and named only by codes («على BRKN0123 BRKA») is a cash withdrawal recorded without review, not a purchase from «BRKN… BRKA». The rule is a shape (terminal id of letters then digits, plus one or two short codes) with a round EGP amount (multiple of 50), not a list of banks; a coded merchant with an odd or foreign amount stays a purchase.
- Transfers whose purpose is open: with only such transfers selected the bulk button reads «حدد غرض المحدد», the page is «غرض التحويلات» and lists «تحويل بين حساباتي», the purposes, then the remaining categories; nothing is preselected and saving without a purpose explains what is missing. The selection note no longer says transfers keep their type.
- Filing an expense as «اشتراكات» or «أقساط» (edit page, bulk page, purpose list) asks which commitment it pays: link it to an existing one of that kind (the same-named one first; the link page opens on the open cycle of the commitment's own schedule nearest the payment (an overdue plan opens on the payment's cycle, a month-end plan keeps its anchor) and always lists that payment, and the commitment learns the merchant's spelling for its next payments, never a bank's name), start a new one from the expense, or «مش دلوقتي». Several rows ask only when they are one merchant. A row already in a commitment is not asked about.
- «حوّلها لالتزام شهري» offers «قسط» too, with optional «اتدفع كام» and «فاضل كام»; left empty, the installment stays open-ended.
- Tests: tools/GluedMerchantAtmTest.java (synthetic Banque Misr, NBE and Al Baraka shapes) in run-checks; PhaseOneTest.testInstallmentFromExpenseCountsAndLearnedMerchant and testLinkFromTransactionSuggestsNearestOpenCycle.

## 0.4.1 — 23 October 2026

- «pending» is a status only in status wording («is pending», «pending payment/transaction/authorization», «still pending», «Status: pending», a message that opens with it, «قيد التنفيذ/الانتظار/المراجعة»); the word inside a merchant name («PENDING COFFEE») no longer stops the purchase being recorded.
- The import result shows three separate numbers: «أُضيفت», «تم تخطيها (ليست معاملات)» (messages the new pipeline records nothing for, the user's ignore rules among them, plus messages the user excluded before) and «مستوردة من قبل».
- A return imported in the same batch as its charge is linked to the charge (expense offset) even when the batch lists the return first; it looks again once every row is written, before the batch's repair.
- Cash and fees in the new pipeline: «تم سحب … من حسابك» with no merchant is a cash withdrawal (not spending); a fee charged for a withdrawal is «رسوم بنكية», not cash; an installment named only in a future clause («Installment will be debited monthly») does not make the purchase an installment.
- Legacy guards (reviewer, 23 Oct 2026): the future-only guard yields to a new-pipeline AUTO decision with a completed event, so «Paid … Your installment will be debited tomorrow» is one purchase; the unsuccessful and notice guards stay absolute. A message any guard keeps out of the ledger is listed on «رسائل من بنوكك ما اتسجلتش» under the same sender and amount conditions instead of being lost silently.
- Tests: each legacy guard (UNSUCCESSFUL, NOTICE, FUTURE_ONLY) checked on its own in ShadowStoreTest; the 14 device failures known since 4ab28ed are diagnosed in docs/superpowers/notes/2026-10-23-known-device-failures.md (4 pipeline defects fixed, 10 stale tests updated); the five device modes have no failures. Upgrades from five real phone databases (v13–v23) to v26 keep every row and total.

## 0.4.0 — 22 October 2026

- The new pipeline decides every message in every build. The legacy parser runs only when the pipeline cannot decide a message (a failed stage, an exception, or a recording decision with no amount, currency or direction); each such message leaves a `PIPELINE_FAILED` note with its reason. The decision switch, its activation time and the 30-day legacy opinion are gone. When the pipeline decides, a message matching a row already recorded only adds its evidence (a delivery of the same message at another time no longer becomes a second row). The version name has no «- Test» suffix.
- Legacy guards stand when the new pipeline records: a future-only, unsuccessful or notice (cancellation, reversal) message is never a ledger row, and its observation is noted `LEGACY_GUARD:FUTURE_ONLY|UNSUCCESSFUL|NOTICE`. A repayment of the user's own credit card takes the debit account as source and the card as destination whoever decided it.
- New-pipeline names and categories match the screens: the catalog's spelling (Espresso Lab, Vodafone, Butlers), the product bought (Google One, not «رصيدكم»), a masked recipient as the name, an HSBC statement line's own description («Phone Banking Transfer», «ATM Cash Withdrawal») with its 3-digit account tail for the source (not a last-4, corpus u06), no currency code glued to a merchant, and the legacy category source and order (TRU → أقساط); a transfer keeps «تحويلات» whatever the user taught for a merchant of the same name. «Card purchase …», «Purchase EGP100 at …» and «Transfer of EGP … from your account» are completed events; «You sent … to» is a transfer. A message the user's own ignore rule refuses reports «not applied», as before.
- Importing old messages uses the new pipeline: each inbox message is decided once, with one context for the scan, and written exactly as listed (origin `import`, bounded by `IMPORT_STORE_MAX_ROWS`; a return finds its charge among the imported rows). A message the pipeline records nothing for is listed only for its non-ledger work (balance, commitment update, cancellation to review) and never becomes a ledger row. Historical cancellations still wait for review; known messages, receipts, exclusions, the bounded repair after the batch and «no success before commit» are unchanged. Sources are discovered silently. The «صيغة غير متعرّفة… أرسل صيغتها» section is gone from the import.
- «رسائل من بنوكك ما اتسجلتش» (Settings → التقاط العمليات): messages from a known bank or payment app that name an amount and were not recorded (ignored by the pipeline or not understood), text redacted, each with «بلّغ عنها» (the same e-mail draft as «الرسالة اتقرت غلط؟») and «تجاهل». The user's own ignore rules never land there.
- Settings: «أدوات المراجعة» (SMS coverage, number diagnostics, transfer review, bank message samples, test crash) and «إعدادات متقدمة» (parser switch, not-understood and not-recorded lists, parser diagnostics) are gone, with the seven-tap gesture and the debug-only rows. The data repair moves to «الخصوصية والملفات» as «فحص البيانات وإصلاحها» in every build. Removed code: ParserDebugActivity, ShadowRescan, ShadowReport, the legacy-vs-new comparison (ShadowDiff keeps newRecords), the stored-observation lister, the in-memory buffer, the import entry points other than executeDetailed, the coverage helper isCompletedFinancialCandidate, canReport, ExpenseDb.diagnostics and repairOwnTransfers; the comparison tables and their generator (helpers kept as tools/CorpusExpectations.java). The trailing-sign repair stays for Beta 0.2.x data (m7).
- «رسائل من بنوكك ما اتسجلتش» lists only what the pipeline could not read (OTHER): offers (Banque Misr's loan offers name amounts), codes, balances and reminders stay out. «تجاهل» also remembers the message's shape, so the same message delivered again does not come back. Banque Misr written «BankMisr» counts as a known bank there.
- Telda is a payment-app source («تطبيق دفع»): no «اربط الكارت بحسابه», no «البنك لسه مش محدد», listed with the accounts; an older Telda card source moves over once (in the data check, and at start-up on phones that already ran it). A Telda notification's balance («رصيدك: 31.5 ج.م.») becomes the card's last balance.
- «لقينا خصم متكرر»: «مش التزام · تجاهل» dismisses the card for good (recurring:<merchant>|<amount>|<currency>); «أضفه» and the row hint «بيتكرر كل شهر؟» open the monthly-commitment page filled in, editable before saving, instead of a two-button dialog.
- Owner acceptance fixes: «رسائل من بنوكك ما اتسجلتش» lists only messages from a bank the user has a source with (or a selected payment app's notification), naming an amount above zero, whether not understood or ignored; we-Landline, VFCashGifts and B.TECH no longer appear. Its count grows with a new delivery only, not with the same message read again. «تحويل بين حساباتي» comes first in the purpose list, the bulk category page and the edit page of an open transfer, and opens the reconcile dialog (Telda can be the destination); choosing «تحويلات» there explains that it keeps the row open. «فحص البيانات وإصلاحها» gives older automatic rows without a source the one their saved message names, then pairs their transfers (the morning of 22 Oct: HSBC «to 7392» 4.50 and the Banque Misr receipt). «مش التزام · ما تقترحوش تاني» dismisses a merchant's recurring card whatever the amount (earlier dismissals still count). Dark mode: logo badges and app icons sit on their own colour, without a white plate or ring.
- Tests: subscriptions paid from prepaid line credit (Google Play via 7030) are information only, per the owner's 21 Oct decision; their commitment suggestions remain.

## 0.3.4 — local phone testing candidate, 21 October 2026

- Transaction rows with no merchant logo fall back to their account's bank/card icon, then the selected notification provider's icon and finally the source's identity badge. Descriptions identify the source without relabelling the merchant. Google Play uses the installed store's icon, separately from Google One.
- Recognize known merchants after a bare Kashier/Paymob prefix (including `Kashier Butlers`); unknown bare descriptors stay intact. Refresh untouched automatic purchase categories using the existing guarded catalog refresh.
- Explain in capture settings that recorded transactions survive notification dismissal, while missed notifications can be recovered only while still active.
- Step 7 rows attach to their source again: a bank SMS that names only an account (HSBC «Account ********0001», Banque Misr «حساب رقم xxx7392») was written with no source, so own-transfer matching never saw it and both legs counted as income and spending. A debit whose other party is only the tail of exactly one of the user's own sources («to 7392») is recorded as a transfer to that account at once, and the receipt is merged into it by the repair after capture; an own InstaPay address decides the same way as in the legacy path.
- A message dated the day it arrived, with no clock in it, takes the delivery time as the transaction time instead of «تاريخ فقط» at noon; earlier dates without a clock keep date-only precision.
- Own-transfer matching waits up to an hour for the receipt instead of ten minutes (owner decision): a wallet or prepaid card such as Telda posts its credit well after the bank debit (24 minutes on 22 Oct). The one-candidate-each-way rule, same currency and the fee ceiling are unchanged.
- Payment-app wording: «لقد استلمت» / «لقد أرسلت» (InstaPay app) and «استلمت» (Telda) are completed events with their direction; the new pipeline had ignored them (NO_COMPLETED_EVENT) and lost the InstaPay own-address evidence. «شبكة المدفوعات اللحظية» is the IPN channel, so a Telda receipt «من خلال شبكة المدفوعات اللحظية» reads as an instant transfer in named «تحويل لحظي» instead of a merchant called «خلال شبكة المدفوعات اللحظية»; «خلال»/«عبر» never start a merchant.
- One source per wallet app: Telda notifications carry no card digits, so their rows had no account and the Banque Misr leg of a top-up could never be paired with the Telda receipt. The first Telda notification now attaches to the single existing source named Telda (any digits), or creates a «Telda» card; later ones reuse it through the alias. InstaPay stays a channel, never a source.
- A receipt delivered again by a payment app after its own transfer was settled (InstaPay +1.00, Telda +10.00 on the owner's phone) folds into that transfer instead of becoming a second income: a notification income of the same amount on the transfer's destination (or with no source), inside the own-transfer window, is the receipt seen again. Bank SMS credits never fold this way. The on-demand data check applies it to rows already recorded.

## 0.3.3 — local phone testing candidate, 21 October 2026

- Read title-only payment notifications. The owner's live Telda notification uses DecoratedCustomViewStyle with the full purchase and balance in `android.title`, null `android.text` and no `android.bigText`; previously the listener returned before parsing it. Empty big text now falls back to regular text. Source selection, summary exclusion and deduplication stay enforced.

## 0.3.2 — local phone testing candidate, 21 October 2026

- Price-change announcements mentioning newly added product features are excluded from money movements. Selected payment apps replay still-active notifications after saving sources or resuming the app; a disconnected listener is asked to reconnect, without booking duplicate transactions.

## 0.3.1 — phone testing candidate, 21 October 2026

- Commitment-link and provider-confirmation pages stay open until the queued database write succeeds, retain the draft on rejection and ignore repeated submission while saving.
- InstaPay own-accounts, «misread message» reports, linking an existing payment to a commitment cycle and status-only provider confirmations are pages. Linking keeps the same cycle→payment→allocation review in one place and still never adds an expense; a new InstaPay account without «@» now shows an error instead of being dropped.
- Daily budget and salary, split bill, «turn into a monthly commitment» and «record as installment» are pages with inline validation; balance history is a page of rows. Transaction details, the bulk-select tools and the account page use the shared buttons; account cards get a visible manage button and the account page offers update-balance and manage actions directly.
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
