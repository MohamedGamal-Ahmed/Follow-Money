# Follow Money — تكملة الثلاثاء 13 أكتوبر 2026

## الوضع الحالي المؤكد

- المشروع: D:\M.Gamal\Learn\Projects\1؛ Java native / SQLite، إصدار 0.2.0، versionCode 2، targetSdk 36.
- آخر debug نزل على Samsung يوم 12 أكتوبر مع الاحتفاظ ببياناته. نسخة محفوظة: .tmp/category-review/Follow-Money-debug.apk. وجود الملف داخل app/build/outputs قد يتغير لو Android Studio أعاد البناء؛ لا تثبت package المعاينة على التليفون.
- التصنيف المحلي، هوية التاجر، حفظ التصحيح، مقاهي/رعاية صحية، الرئيسية، مدخل التقارير، عرض النسبة والتبويب النشط جاهزة.
- 343 JVM + 50 Android ناجحين؛ build debug/release ناجح؛ lint بدون أخطاء و61 warning قائمة. توقيع release APK طابق شهادة الرفع المحلية، وفحص AAB أكد غياب أدوات التشخيص وINTERNET.
- تجربة واجهة التعلم تمت فعلًا مع التحقق من حفظ القاعدة؛ صور فاتح/داكن مصطنعة في .tmp/category-review/screenshots.
- هذه التكملة تشمل الكود والاختبارات ومستندات الجاهزية. ملفات المعاينة وartifacts محلية وخارج Git. لا تعمل clean/reset أو تستبدل ملفات Claude؛ راجع git diff قبل أي تغيير جديد.

## 1. تجربة Mohamed على التليفون

- التصنيفات المعروفة تلقائيًا، التاجر المجهول، واختلاف الفروع وKASHIER؛ لا تفترض أن كل التجار معروفون.
- النسب والمبالغ منفصلة، والضغط على التصنيف يفتح العمليات لنفس الشهر/العملة.
- وضوح التبويب النشط في الرئيسية/العمليات/الالتزامات/التحليلات، والصفحات الفرعية والرجوع.
- تذكّر تصحيح التاجر للجديد، ثم معاينة عدد العمليات السابقة قبل الموافقة عليها؛ لا تغير عملية سبق تعديلها.
- SMS وإشعار دفع جديدان أثناء إغلاق الواجهة، ورفض/سحب الأذونات وتجنب التكرار. الاختبار الفعلي على الهاتف لم يكتمل.
- الويدجت على Samsung، التذكير وتحديث منتصف الليل. لا تعتبر اختبارات المحاكي إثباتًا للبطارية/الخلفية أو تحديث منتصف الليل.
- راجع الحسابات والتحويلات والتوقيتات؛ فرق المراجعة المالية السابق وdry-run بعد تصحيح التوقيت لم يُغلقا بإثبات جديد في مهمة التصنيف. أي تصدير أو repair لبيانات الهاتف يحتاج نطاقًا واضحًا وموافقة المستخدم.
- لا تمسح بيانات التطبيق ولا تعمل uninstall للتليفون. لا تصنع تحويلًا ماليًا حقيقيًا لأجل الاختبار.

## 2. إقفال الكود

- أصلح البلاغات المحددة فقط، وسجل trigger / expected / actual مع نسخة Android والجهاز. أخفِ البيانات المالية في اللقطات المشتركة.
- راجع git diff لأي تغييرات لاحقة. Commit/push بعد طلب المستخدم، مع إبقاء الريبو الخاص خاصًا.
- عند تعديل الكود: JAVA_HOME = C:\Program Files\Android\Android Studio\jbr، ثم tools/run-checks.ps1. اختبارات -Android على emulator فقط، وليس Samsung.
- اختبار تحديث قاعدة البيانات على أقدم Android مدعوم أو جهاز أقدم ما زال ضمن خطة النشر؛ التشغيل اليوم كان على محاكي حديث وSamsung.

## 3. تجهيز المتجر

- السياسة والدعم جاهزان: https://mohamedgamal-ahmed.github.io/Follow-Money-Support/privacy.html
- الصور الحالية QA مصطنعة 1080×2400؛ جهّز صور المتجر النهائية بالمقاسات المقبولة. لا تستخدم صور حسابات Mohamed أو صور Money Loop في listing.
- icon أولي في release/play-store/2026-10-12/app-icon.png؛ أكمل مراجعته، feature graphic عربي/إنجليزي، وصور المتجر بعد تثبيت الواجهة.
- سجل demo حقيقي على release في package معزولة، ببيانات مصطنعة: الإفصاح → طلب الإذن → عملية مستخرجة وتصنيفها؛ أظهر رفض/سحب الإذن ضمن QA. الفيديو لم يُنتج بعد.
- المحاكي FollowMoneyPlayMedia واجه crash/فشل خدمة التخزين وأُغلق. المحاكي العامل وpackage المعاينة categoryqa يصلحان لـdebug QA؛ تسجيل release يحتاج package إصدار معزولة، لا fixture مالية حقيقية.

## 4. قبل دفع رسوم Google Play

- تسجيل Play مؤجل إلى 1 نوفمبر 2026. Gmail القديم من 2017 لا يعني وجود حساب مطور قديم.
- احسم أهلية Personal مقابل Organization لتطبيق budgeting محلي مع دعم Google. السياسة تذكر Organization للخدمات المالية؛ انطباقها على التطبيق لم يُحسم. السؤال الجاهز في docs/CLOSED_TESTING_PLAN.md ولم يُرسل.
- upload key موجود محليًا خارج الريبو. رتّب backup مشفر مستقل واستعادة password قابلة للنقل؛ ملف DPAPI وحده مقيد بحساب Windows/الجهاز الحالي. لا ترسل أي مفتاح أو password في الشات أو Git.
- Google login/cloud backup مؤجلان؛ تسجيل الدخول وحده ليس مزامنة ولا backup.

## 5. بعد التسجيل

- Play App Signing؛ آخر AAB موقع؛ تصريح SMS؛ Data Safety؛ financial features؛ content rating؛ app access؛ audience؛ بيانات التواصل وlisting.
- مسودات النصوص والتصريح في docs/GOOGLE_PLAY_RELEASE.md؛ إجابات Console النهائية لم تُرسل.
- لو الحساب Personal جديد: 12 مختبرًا opted-in باستمرار 14 يومًا؛ هدف التجهيز 15–20. مدة APK خارج Play لا تُحسب. احتفظ بتعليقات واختبارات فعلية؛ الوصول للإنتاج وموافقة SMS غير مضمونين.

## مصادر وسياسات راجعناها 12 أكتوبر

- نوع الحساب: https://support.google.com/googleplay/android-developer/answer/13634885
- الاختبار المغلق: https://support.google.com/googleplay/android-developer/answer/14151465
- التصاريح: https://support.google.com/googleplay/android-developer/answer/9214102
- أصول المتجر: https://support.google.com/googleplay/android-developer/answer/9866151

ابدأ غدًا بتجربة التليفون وتجميع بلاغات محددة، ثم الإصلاحات ومواد المتجر؛ لا توجد رسوم مطلوبة لهذه الخطوات.

دليل النشر: [Google Play release preparation](GOOGLE_PLAY_RELEASE.md). خطة الاختبار: [Closed testing](CLOSED_TESTING_PLAN.md).
