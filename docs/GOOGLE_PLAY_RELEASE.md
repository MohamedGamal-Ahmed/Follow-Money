# Google Play release preparation

Prepared 12 October 2026. This is a preparation record, not approval or a published release. Correction: the owner clarified that the Google/Gmail account dates to about 2017, but no Play Console developer account exists yet. Gmail age does not exempt a newly created personal developer account from the mandatory closed test: at least 12 opted-in testers continuously for 14 days before applying for production access. Verify the appropriate developer account type for the app before paying registration fees.

## Submission gates

Registration/payment is deferred to the start of November 2026. [Closed test plan and account-type clarification](CLOSED_TESTING_PLAN.md) records the updated account facts and the official-policy uncertainty to resolve before paying.

| Item | Evidence / remaining action |
| --- | --- |
| Target API | `app/build.gradle` targets API 36; current requirement starts 31 August 2026. |
| SMS permissions | Keep `READ_SMS` for historical preview/import and `RECEIVE_SMS` for new background capture. No `SEND_SMS`. Apply for the SMS-based money management exception; approval is case-specific. |
| Disclosure | Shared SMS disclosure precedes permission requests; Notification Access paths use disclosure before Android settings; system speech has its own provider disclosure. Verify all paths on a clean emulator. |
| Privacy | Exact bundled policy: `app/src/main/assets/privacy-policy.txt`. Native app dialog under Data/Privacy and Help. Public page: `support/privacy.html`; render with `python tools/render-privacy.py`. URL: https://mohamedgamal-ahmed.github.io/Follow-Money-Support/privacy.html (verify availability before submission). |
| Data Safety | Local-only ledger/SMS processing is outside collection, but support metadata, optional email/browser handoff, document-provider export and system speech must be assessed. Do not copy a blanket “no data leaves the phone” answer. See draft below. |
| Release diagnostics | Developer review entry is debug-only; release uses R8. Verify release APK/Dex for audit classes, debug entry points and sensitive strings. Local financial review is not the same as basic support app/device metadata. |
| Signing | First-publication upload key generated locally outside Git; password protected with Windows DPAPI. AAB signature verification passed. Key backup/recovery and Play App Signing setup remain owner actions. |
| Financial features | Declare actual personal-finance/budgeting functionality. Recording debts/installments is bookkeeping; the app does not issue loans, execute payments or fetch bank balances. Verify available Console choices. |
| Store listing | Draft Arabic/English text below. Synthetic screenshots, final icon/feature graphic and reviewer demo video are pending. |
| Reviewer access | No app login. Provide synthetic SMS instructions/video; do not expect a reviewer to have Egyptian bank SMS. |
| Financial QA | Re-run synthetic database/reconciliation checks. Historical financial-audit claims, including the previously mentioned inter-account difference and repair dry run, need a separate local evidence review after timestamp correction. No phone export or repair is authorized by this publishing task. |
| Account setup | New developer registration is required. Confirm account type, then verify identity/contact details, account/device verification, countries, pricing, target audience and content rating. A new personal account requires the 12-tester/14-day closed test; the old Gmail date is irrelevant. |

## Build and upload signing

The owner confirmed first publication and the developer name **Mohamed Gamal**. An upload key was created in the local user's `AppData/Local/FollowMoney/signing` directory, outside the repository. `tools/sign-local-release.ps1` loads its Windows DPAPI password into process environment variables only and restores earlier variables afterward. The password file is bound to this Windows user/machine; copying it alone is not portable password recovery. Arrange an independent encrypted key backup and portable secret recovery before publishing. No passwords, private keys or financial data are in Git.

Run from the project root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\run-checks.ps1
.\tools\build-release.ps1 -Unsigned
python tools/check-release.py
```

Unsigned validation outputs are **not uploadable**. For signed artifacts, set these four process environment variables locally using the owner's secret-management method:

- `FOLLOW_MONEY_UPLOAD_KEYSTORE`: absolute path to the upload keystore outside Git.
- `FOLLOW_MONEY_UPLOAD_STORE_PASSWORD`: keystore password.
- `FOLLOW_MONEY_UPLOAD_KEY_ALIAS`: upload alias.
- `FOLLOW_MONEY_UPLOAD_KEY_PASSWORD`: private key password.

Do not paste secrets into chats, command history, source or CI. Do not reuse a debug signing key. Preserve an encrypted backup of the upload key and its recovery information. An existing Play application must use its registered upload certificate; do not invent a replacement key.

```powershell
.\tools\build-release.ps1
# On the original Windows account with the local DPAPI signing setup:
.\tools\sign-local-release.ps1
python tools/check-release.py
```

The script requires all four variables, builds the AAB/APK, runs release lint and checks that the AAB is signed. `app/build/outputs/bundle/release/app-release.aab` is the bundle to review. Verify its signer fingerprint against the Play upload certificate before upload. Play App Signing uses the app signing key for delivered APKs; the upload key is separate. Increment `versionCode` for subsequent uploads; confirm whether package/version 2 has already been uploaded before doing so.

## Permissions Declaration draft (English)

Use case: **SMS-based money management**.

Follow Money is a personal money management application whose primary capture workflow automatically creates a local financial timeline from supported bank/payment SMS. READ_SMS is needed to preview historical financial messages and let the user select transactions for import. RECEIVE_SMS is needed to process new financial messages when the interface is not open. The app extracts supported transaction amounts, currencies, merchants, account/card suffixes, balances and recurring-payment information, helping users review spending and budgets without retyping each bank message.

Processing occurs locally. Saved matching financial source text is encrypted using Android Keystore for transaction review; the financial SQLite ledger is not fully encrypted. SMS content is not transmitted to the developer or a server. Recognized verification-code messages are excluded; recognition is rule-based and does not guarantee every format. The app requests no SEND_SMS, call-log or default-SMS-handler role.

Each applicable permission request is preceded by an in-app explanation of the data, purpose, background processing, storage and choices. Users can decline/revoke access. Manual entry and sharing selected text are fallbacks; selected payment notifications can capture new notifications but do not provide historical SMS. These alternatives do not provide the same automatic historical-and-new SMS workflow. We request only the two SMS permissions used for this core workflow and understand the exception remains subject to Google's review.

Reviewer video URL: **[PENDING: record and host a synthetic-data demonstration]**. Confirm the published build and store listing match this draft before submission. Do not paste internal QA data or account numbers.

## Data Safety working draft

- SMS, financial records, optional profile name/photo and app choices are accessed/stored locally. If no path transmits these off device, they are not “collected” under the form's definition. Their access/storage still belongs in the privacy policy and in-app disclosures.
- Support can transfer a user-written description and app/device metadata to the email app or browser after user review. Assess collection and the user-initiated-sharing exception separately: an exception to declaring sharing does not automatically establish an exception to collection.
- System speech recognition can involve an external provider. Do not assert offline audio processing or mark audio handling without checking the actual provider/integration and the form's scope.
- CSV export uses a user-selected document provider; users may choose a cloud destination. Local source SMS is excluded from CSV. Debug report sharing may contain financial data; this entry is unavailable in the release UI.
- No in-app accounts, telemetry, advertising SDK or cloud sync are implemented. Do not complete an account-deletion workflow for a nonexistent app account; describe actual local deletion instead.
- **Final form answers remain pending review.** Check the signed artifact, all external handoffs, selected SDKs and the exact Console questions. The absence of INTERNET alone is insufficient evidence.

## Store listing drafts

Name: **Follow Money** (12 characters).

Arabic short description:

> تتبّع معاملاتك وميزانيتك تلقائيًا من رسائل البنك، مع مراجعة محلية لبياناتك.

Arabic full description:

> Follow Money يساعدك تتابع المصروفات والدخل والتحويلات والالتزامات من مكان واحد.
>
> بعد موافقتك، يستورد المعاملات المدعومة من رسائل SMS بمعاينة قبل الحفظ، ويلتقط الرسائل المالية الجديدة تلقائيًا. ويمكنك اختيار تطبيقات الدفع لالتقاط إشعاراتها الجديدة، أو إضافة العمليات يدويًا ومشاركة نص محدد.
>
> راجع مصدر العملية، وتصنيفها، والحساب أو البطاقة عندما تسمح بيانات المصدر. تابع ميزانيتك اليومية والتزاماتك، واستعرض التحليلات والويدجت. العملات منفصلة؛ الأرصدة المحسوبة موضحة كتقديرية.
>
> البيانات المالية محفوظة محليًا. نصوص المصادر المالية المحفوظة مشفرة، بينما قاعدة المعاملات نفسها ليست مشفرة بالكامل. تصدير CSV والدعم والصوت خيارات تستخدم خدمات الجهاز أو خدمات تختارها، كما توضح سياسة الخصوصية.
>
> التعرف على كل صيغة غير مضمون؛ راجع العمليات الملتبسة قبل الاعتماد على الإجماليات. التطبيق أداة تسجيل وميزانية، ولا ينفذ تحويلات أو يقدم قروضًا أو يجلب رصيدًا مباشرًا من البنك.

English short description:

> Track money and budgets automatically from bank SMS, with local review.

English full description:

> Follow Money brings supported expenses, income, transfers and recurring payments into one local financial timeline.
>
> With your permission, preview and import historical bank SMS and automatically capture new supported financial messages. Select payment apps for notification capture, or add entries manually and share selected text.
>
> Review transaction sources, categories and available account/card details. Follow daily budgets, commitments, insights and home-screen widgets. Currencies stay separate; calculated balances are labeled as estimates.
>
> Financial records are stored locally. Saved financial source text is encrypted; the SQLite ledger is not fully encrypted. Optional CSV export, support and voice entry use device services or providers you select, as explained in the privacy policy.
>
> Message recognition and completeness are not guaranteed. Review ambiguous records before relying on totals. This is a recording and budgeting tool: it does not execute payments, issue loans or retrieve live bank balances.

## Reviewer demonstration and QA

Use a disposable emulator with invented bank/card/amount data. Never record a real phone ledger.

1. Fresh install: choose SMS onboarding; record disclosure, cancel, then request access through Settings and verify disclosure appears again before the system prompt.
2. Grant SMS: send a synthetic completed debit to the emulator; show its extracted amount/currency/category and source. Import historical messages using preview and selected save. Reimport and confirm no duplicate booking.
3. Reject permission: manual entry/text share remain usable; explain reduced automatic coverage without pressuring consent. Revoke permission in Android and confirm capture stops.
4. Notification Access: test onboarding, Settings toggle and source selection paths. Decline first. Then select a synthetic payment test app, enable access and show new capture; unselected apps, SMS app and Truecaller must not create entries.
5. Voice entry: disclosure before system provider; cancel without launching, then verify transcription review and cancellation without saving a transaction.
6. Privacy: open the exact policy from Help and Data/Privacy; check Arabic readability and scrolling. Verify exported CSV excludes original SMS text, and explain that deleting app data cannot delete external files.
7. Release: no developer review tools; basic support metadata contains no ledger/SMS. Financial totals, refunds, own-account transfers and estimated balances need synthetic regression checks.
8. Provide the reviewer video link and tested navigation instructions in Console; do not claim automated reviewer SMS delivery if it has not been tested.

## Official sources checked 12 October 2026

- [SMS and call log permissions](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Declare permissions, including testing tracks and demonstration video](https://support.google.com/googleplay/android-developer/answer/9214102)
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [User data, disclosure and privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Data Safety scope](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)

Policy statements are a dated preparation reference. Re-check before actual submission and confirm the state shown by the owner's Console.

## Validation record — 12 October 2026

- 267 standalone JVM checks passed; debug build/lint and instrumentation APK built successfully.
- 47 synthetic Android checks passed on emulator-5554 (18 reconciliation, 14 phase-one, 15 feature checks); no private phone fixtures were requested.
- Production release AAB/APK and release lint succeeded with R8 enabled. Release lint reported 0 errors and 61 warnings; this is not a warning-free sign-off.
- A separate `.playqa` package was used for release UI checks without touching the installed financial app. Settings showed the policy and no developer review entry. Native policy displayed readable Arabic and scrolling; SMS Settings path displayed disclosure before any permission request, and cancelling returned to the app. Notification Access displayed its own disclosure.
- Release shrinker output lists the private diagnostics/review methods as removed; check-release.py also checks actual bundle Dex content and the production manifest. Full grant/revoke flows, selected notification integration, system speech, the final reviewer video and store graphics remain separate QA/media tasks.
- Claude Code completed an independent read-only review. Its snapshot excluded DB/evidence storage and artifacts; it is supporting feedback, not an approval or completed release test. The stale public support policy, broad verification-code claim and exception logging it flagged were addressed.
- No Google Play upload, SMS declaration submission, or Play approval has occurred.

## App checkpoint after classification and navigation work — 12 October 2026

- 343 JVM checks and 50 synthetic Android tests passed (18 reconciliation, 14 phase-one, 18 feature tests). Debug/release builds succeeded; lint still has 61 warnings and no errors.
- Merchant identities and saved category corrections are local. Known processor prefixes preserve the actual shop even when it is unknown. Health care and cafés are distinct categories; previous user edits, manual/CSV choices, transfer fees and transfer semantics are protected.
- The actual isolated UI was checked in light and dark mode, including percentage badges, active navigation, and opting into a saved merchant correction through the edit dialog. This does not establish full real-device background SMS/notification reliability.
- The verified debug APK was installed over the owner's existing Samsung app on 12 October, retaining its data. The app version remains 0.2.0 / versionCode 2. Owner acceptance testing is still pending.
- Synthetic UI captures are local QA evidence, not final Play listing media. The final feature graphic and reviewer demo video are not complete. The new media AVD was unstable; use the working emulator with an isolated package for further recording.
- This checkpoint includes classification/navigation changes and testing/media preparation tools. Review later working-tree changes before any commit or push; signing material, generated binaries and phone financial data stay outside Git. The [next-day handoff](NEXT_STEPS_2026-10-13.md) records remaining work.
- Before publishing: resolve account-type eligibility with Google, arrange portable signing-key recovery, finish release consent/capture/revoke QA, complete store media and Console declarations, and run the required testing track. Google sign-in/cloud backup remains deferred.
