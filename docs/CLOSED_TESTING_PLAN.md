# خطة اختبار Follow Money قبل Google Play

الحساب الموجود من 2017 هو Google/Gmail، وليس حساب مطوّر Play Console. التسجيل مؤجل إلى بداية نوفمبر 2026. عند إنشاء حساب Personal جديد، يلزم وجود 12 مختبرًا على الأقل اشتركوا في closed testing باستمرار لمدة 14 يومًا قبل طلب production access. التسجيل في الاختبار يبدأ عند الاشتراك الفعلي، ولا يُحتسب اختبار APK خارج Play ضمن هذه المدة. الوصول للإنتاج وصلاحيات SMS يحتاجان مراجعة مستقلة؛ لا توجد موافقة مضمونة.

## قبل دفع رسوم التسجيل

اختيار نوع الحساب يحتاج حسمًا. صفحة Google عن أنواع الحسابات تطلب Organization عند تقديم financial products and services، وسياسة Financial Services تعرفها بعبارة واسعة تشمل إدارة الأموال. لم نجد استثناءً رسميًا صريحًا لتطبيق budgeting/expense tracking محلي فقط. عدم تقديم قروض أو تنفيذ تحويلات لا يثبت وحده أن Personal مسموح.

سؤال جاهز لدعم Google؛ لم يتم إرساله:

> I plan to publish Follow Money, a local-only personal budgeting and expense tracker. With user consent, it parses bank SMS and selected payment notifications on device. It does not execute payments, hold funds, issue or broker loans, provide investment or personalized financial advice, or connect to bank accounts. The developer is an individual with no financial institution. Under the account-type guidance and Financial Services policy, does this app require an organization developer account, or can it be published from a personal account? Please identify the applicable official policy text.

لا نختار Organization باسم شركة المستخدم الوظيفية؛ التطبيق مشروعه الشخصي. لا ننشئ كيانًا أو D-U-N-S ولا ندفع رسومًا اعتمادًا على افتراض غير مؤكد.

## التجهيز

- استهدف 15–20 مختبرًا، ليظل 12 على الأقل مشتركين دون انقطاع.
- استخدم بيانات مصطنعة للاختبارات التي تضيف/تحذف/تصلح المعاملات. لا تطلب رسائل البنك أو قواعد البيانات أو ملفات مالية من المختبرين.
- اسم الإصدار ورقم البناء وطراز الجهاز ونسخة Android تكفي لبدء البلاغ.
- أخفِ الأرقام الشخصية في أي لقطة شاشة. بيانات الدعم عبر الإيميل/المتصفح تحتاج مراجعة المستخدم.
- أعط كل مختبر معرفًا مثل T01، واحتفظ بقائمة الإيميلات والاشتراكات محليًا خارج Git.
- بعد تسجيل الحساب: أكمل التطبيق والتصاريح المطلوبة، ارفع إلى closed track، ثم شارك رابط opt-in. تصريح SMS يشمل مسارات الاختبار أيضًا.

## برنامج الأيام

| الأيام | الاختبار |
| --- | --- |
| 1–2 | تثبيت نظيف، الترحيب، عرض السياسة والإفصاح، رفض الأذونات وتجربة الإضافة اليدوية. |
| 3–4 | مصروف/دخل بعملات منفصلة، تعديل التصنيف والقيمة، حذف عملية مصطنعة فقط، وربط الحساب/البطاقة. |
| 5–7 | عند الموافقة: SMS تاريخي بمعاينة، اختيار صفوف قبل الحفظ، إعادة الاستيراد دون تكرار، رسالة جديدة أثناء إغلاق الواجهة، وفشل/OTP/رسالة مستقبلية لا تُحجز كمصروف مؤكد. |
| 8–9 | إشعارات تطبيقات الدفع المختارة، تجاهل التطبيقات غير المختارة، سحب الإذن، والعودة إلى الإدخال اليدوي. لا نفترض أن إشعارات قديمة يمكن استرجاعها. |
| 10–11 | الميزانية اليومية، فترات التحليلات، البحث والتصفية، التزامات مؤكدة، الويدجت وإخفاء الأرقام. |
| 12 | تصدير CSV ببيانات مصطنعة، تأكد عدم وجود نصوص SMS الأصلية، حذف السجل المحلي وفهم أنه لا يحذف رسائل الهاتف أو الملفات المصدرة. |
| 13 | فتح/إغلاق متكرر، الأداء، تغير يوم/توقيت الجهاز على جهاز اختبار، وحالات offline وعدم توفر تطبيق بريد/خدمة صوت. |
| 14 | إعادة فحص الإصلاحات، جمع الملاحظات، وتأكيد بقاء الاشتراك في closed test قبل طلب الإنتاج. |

الفتح اليومي وإرسال ملاحظات عن الاستخدام الحقيقي توصية لاختبار مفيد؛ لا نقدمهما كشرط رقمي إضافي من Google. تحديثات الإصلاح أثناء الاختبار لا تعفي من استمرار الاشتراك المطلوب. عند خروج مختبر ثم عودته، مدة اشتراكه المتواصل تبدأ من جديد.

## البلاغ المختصر

```text
Tester ID:
App version / versionCode:
Device / Android:
Feature:
Permission state (SMS / notifications):
Steps using invented data:
Expected:
Actual:
Severity (crash / wrong amount / capture / UI):
Sanitized screenshot (optional):
Retest result:
```

يمكن إرسال البلاغ من التطبيق كمسودة بريد بعد مراجعة بيانات الجهاز. لا توجد مراسلات تلقائية للمختبرين في هذه الخطة، ولا ننسخ بياناتهم إلى GitHub.

## حدود الجاهزية الحالية

- آخر build debug/release ناجح، مع 343 JVM و50 Android tests (18 reconciliation، 14 phase-one، 18 feature). هذه لا تغطي كل صيغ البنوك أو ضمان صحة رصيد حقيقي. تجربة حفظ التصحيح من الواجهة تمت على بيانات مصطنعة، والتحقق من الخلفية/الأذونات على التليفون والإصدار النهائي ما زال مطلوبًا.
- الجهاز الفعلي حصل على تحديث debug بطلب المستخدم مع الحفاظ على بياناته. رقم النسخة ما زال 0.2.0 / 2، فلا تستخدم رقم النسخة وحده لإثبات أن ملفات build متطابقة.
- اختبار Play signing/runtime permissions يجب أن يستخدم release المطابق للـAAB. لا تثبت مفتاح upload فوق تطبيق debug على الهاتف بحذف بياناته؛ التوقيع مختلف.
- مراجعة Claude كانت قراءة مستقلة وصياغة خطة، ولم تكن تشغيلًا للمحاكي أو إثباتًا للموافقة على نوع الحساب.

## المراجع الرسمية

- [متطلبات الحسابات الشخصية الجديدة](https://support.google.com/googleplay/android-developer/answer/14151465)
- [اختيار نوع حساب المطور](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Financial Services](https://support.google.com/googleplay/android-developer/answer/9876821)
- [التصاريح في مسارات الاختبار](https://support.google.com/googleplay/android-developer/answer/9214102)

تمت مراجعة المصادر في 12 أكتوبر 2026. أعد التحقق قبل الدفع والتقديم الفعلي.
