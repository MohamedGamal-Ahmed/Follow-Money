# Prompt لـOpus — اعتماد المحرك الجديد وتنظيف الإعدادات والكود (0.4.0)

أنت تنفّذ على المستودع Follow Money (Android/Java، عربي)، فرع `codex/telda-capture-fixes` من `4ab28ed`. المواصفة المعتمدة من المالك والمراجِع: `docs/superpowers/specs/2026-10-22-new-engine-adoption-and-cleanup-design.md` — اقرأها كاملة أولًا؛ هي المرجع الحاكم، وهذا الـprompt ترتيب تنفيذ لها لا بديل عنها. المراجِع (Claude) سيراجع كل commit على `git diff` ويقارنه بالمواصفة؛ لا يراجع النوايا.

## قواعد عامة
- أربع مهام متسلسلة **أ ← ب ← ج ← د**، كل مهمة commit واحد منفصل برسالة تصف السلوك لا الملفات. لا تبدأ مهمة قبل أن تمرّ السابقة بالفحوص.
- TDD: لكل تغيير سلوكي اختبار يفشل أولًا (JVM في `tools/*.java` عبر `run-checks.ps1`، أو جهاز في `app/src/androidTest`) ثم أقل كود يُنجحه. حذف كود بلا سلوك جديد لا يحتاج اختبارًا جديدًا، لكن يحتاج أن تبقى المجموعة خضراء.
- قبل كل commit: `python tools/check-repo.py` ثم `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\tools\run-checks.ps1` (JVM + assembleDebug + lintDebug + androidTest compile)، ثم اختبارات الجهاز المتأثرة على المحاكي (`tools/run-shadow-store.ps1` وغيرها). لا تعلن نجاحًا لم تره في مخرجات الأمر.
- ممنوع: تغيير تعريفات `MoneyFlow`، تغيير مخطط قاعدة البيانات، إعادة تسمية كلاسات `Shadow*`، لمس `MainActivity` خارج المواضع المسماة في المواصفة، حذف أي شيء في قائمة «يبقى» بالمواصفة.
- عند أي غموض بين الـprompt والمواصفة، المواصفة تحكم؛ عند غموض داخل المواصفة، توقف واسأل المالك بسؤال واحد محدد بدل التخمين.
- كل commit ينتهي في رسالته بقسم «ما لم أستطع إثباته» صادق (اختبار لم يُشغَّل، افتراض عن صيغة رسالة، …).

## المهمة أ — قرار واحد (المواصفة: «المهمة أ» + «تعريفات حاكمة» م1، م4، م7)
1. `PipelineDecider.decide` يعيد `Result`: `Decided(decision, observation)` أو `Failed(reason)` بالأسباب المسماة في المواصفة. أضف `PipelineDecider.Context` (قواعد التجار + learning محمّلة مرة) و`decide(Context, CaptureRequest)`.
2. `ShadowStore.recordFailure(...)` تكتب صف `PIPELINE_FAILED` بلا observation وبلا إعادة تشغيل المحرك. احذف `CaptureTransaction.recordDecided/shadow/observeWithoutCapture` وكل ما يخص `LEGACY_OPINION`.
3. احذف `DecisionSwitch` وprefs `parser_decision` وختم التفعيل في `MainActivity.onCreate`؛ نظّف `ParsingPolicy` (الأعلام الخمسة، الرأس) وأضف `IMPORT_STORE_MAX_ROWS`.
4. `ExpenseDb.addAutomaticCapture`: `parseTransfer/parse/parseIncome` داخل `decided == null` فقط؛ أزل قيد «قبل التفعيل».
5. `app/build.gradle`: بلا `- Test`، `0.4.0` / `versionCode 10`. أضف قسم `0.4.0` في `assets/whats-new.txt` وCHANGELOG (يكتمل عبر المهام).
6. اختبارات: `ShadowStoreTest` بلا `DecisionSwitch` + حالة الفشل (`PipelineDecider.failNextFor` داخل DEBUG فقط) → القديم يسجّل + صف `PIPELINE_FAILED` بسببه؛ `tools/ParserShadowTest.java` بلا فحوص نصوص الملفات المحذوفة.

## المهمة ب — الاستيراد على المحرك الجديد (المواصفة: «المهمة ب» م2، م3، م4، م5، م7)
1. أنشئ `tools/fixtures/owner-day-2026-10-22.tsv` بالرسائل الست المذكورة نصًا في المواصفة، واكتب أولًا اختبار جهاز في `SmsImportUiTest` (أو `AuditRegressionTest`) يستورد 1–3 و5 ثم يلتقط 4 و6 حيًا ويتوقع النتيجة المذكورة (مصادر، تحويلان داخليان، رسم 0.50، دخل 87,650، لا دخل منفرد). شاهده يفشل.
2. `SmsPreview` حامل صف + `decision` + `fromDecision(...)`؛ `Session.start` بسياق واحد للدفعة وقرار واحد لكل رسالة يُحمل مع الصف؛ `addHistoricalSmsObserved` يمرّر القرار ويسجّل بـ`origin="import"`؛ `offsetOf` يبحث في origin الرسالة؛ التقليم لكل origin بحده.
3. أبقِ صريحًا: `automaticCancellation=false`، مفاتيح الاستلام، `smsCaptureStatus`، `sms_import_receipts`، `repairBetweenInCallerTransaction`، عدم إظهار نجاح قبل الالتزام.
4. احذف قسم «صيغة غير متعرّفة… أرسل صيغتها» وما يخدمه فقط.

## المهمة ج — التنظيف (المواصفة: «المهمة ج» + القرار 5/م6)
1. أضف أولًا صفحة م6 «رسائل من بنوكك ما اتسجلتش» (قارئ `ununderstood_messages`، بلّغ/تجاهل) ووسّع `rememberUnunderstood` لتشمل `IGNORE` من مرسل بنك معروف بنص فيه مبلغ، مع اختبار جهاز.
2. ثم احذف أقسام الإعدادات والكود والاختبارات والأدوات المذكورة بالترتيب في المواصفة، وانقل «تصحيح البيانات» إلى «الخصوصية والملفات». أبقِ `repairTrailingSigns` بتعليق م7.
3. حدّث `run-checks.ps1`، `check-repo.py`، README، docs، CHANGELOG، whats-new.

## المهمة د — ملاحظات المالك (المواصفة: «المهمة د»)
1. Telda مصدر «تطبيق دفع» بلا عرض ربط/بنك + ترحيل مرة واحدة في `repairAll` + رصيد Telda من `balance_minor` (اختبار جهاز في `ShadowStoreTest` بند جديد).
2. بطاقة «لقينا خصم متكرر»: «مش التزام · تجاهل» دائم + «أضفه» يفتح صفحة الالتزام الشهري معبّأة (اختبار UI).
3. «تحويلات غرضها مش محدد»: اختبار جهاز لكل طريقة من الثلاث يفشل أولًا بإعادة إنتاج العيب، ثم الإصلاح؛ اذكر السبب الجذري الفعلي في رسالة الـcommit.

## تسليم كل مهمة
رسالة commit: سطر عنوان سلوكي، ثم ما تغيّر ولماذا بالإشارة لبند المواصفة، ثم الاختبارات بأسمائها وأعدادها، ثم «ما لم أستطع إثباته». ثم أرسل للمراجِع: `git log -1 --stat` ومخرجات `run-checks` الأخيرة ومخرجات اختبارات الجهاز.
