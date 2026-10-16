# كتالوج شعارات مستقل لـFollow Money

10 أكتوبر 2026، محدَّث 16 أكتوبر 2026 — **37 ملف شعار صالحًا**: 11 بنكًا و26 علامة/تاجرًا/خدمة. 45 سجلًا إجمالًا؛ 8 سجلات بدون ملف جاهز. ليست قائمة MoneyLoop الداخلية وليست حصرًا لكل البنوك أو التجار.

- [المعاينة](preview.html) مع فلتر للبنوك والخدمات والناقص.
- [CSV](catalog.csv): مناسب للمراجعة في Excel.
- [JSON](catalog.json): مصادر وأسماء بديلة وحالة الدمج وSHA-256 وحقوق العلامة.
- `originals/`: الملفات الأصلية كما وصلت؛ لا صور مجمعة مقصوصة أو شعارات متخيلة.

## المتاح فعليًا

البنوك: بنك مصر، QNB، FABMISR، HSBC، ALEXBANK، ADIB، saib، Bank ABC، Bank NXT، KFH Egypt، Crédit Agricole.

العلامات والخدمات: Starbucks، Carrefour، McDonald's، KFC، Uber، Netflix، Spotify، IKEA، Shell، Airbnb، Booking.com، Apple، Samsung، YouTube، Vodafone، Orange، PayPal، Visa، MasterCard، Talabat، مجموعة مستشفيات كليوباترا، Fawry، InstaPay، Espressolab، **Uber Eats**، **Claude**.

أُضيف في 16 أكتوبر 2026: **Uber Eats** (لأن "Uber Eats" كان يُطابق شعار Uber بخطأ البادئة) و**Claude**. مصدرهما Simple Icons باللون المنشور لكل علامة (`06C167` و`D97757`)، والـSVG الأصلي والـsha256 في `originals/` و`android-assets.json`.

تم دمج37 شعارًا محليًا في Android، مع الحفاظ على PNG السبعة السابقة. الموارد والـhashes موثقة في `android-assets.json`؛ `integrated_in_app` و`android_resource` يعكسان الحالة الحالية. الصور الأصلية لم تتغير.

قيد الاستكمال: NBE وCIB وBanque du Caire وAAIB وHDB وJumia وAmazon وWE. بعض المواقع رجعت حماية/خطأ اتصال أو لم تعطِ شعارًا صالحًا مباشرًا. ظهور اسم في CSV لا يعني وجود ملف أو دعم parser.

## نسخ الشعارات وجودتها

16 ملفًا من مواقع رسمية و21 SVG من [Simple Icons](https://github.com/simple-icons/simple-icons). Simple Icons هنا أشكال أحادية اللون؛ المعاينة تعرض لون العلامة المنشور، وليست بديلًا مضمونًا لكل نسخة full-color. الملفات الأصلية لم تتغير. نسخة ALEXBANK البيضاء معروضة على خلفية غامقة، FABMISR علامة مختصرة فقط، وsaib نسخة موقعه بمناسبة50 عامًا. هذه نسخ تصلح للمراجعة ويحتاج اختيار النسخة النهائية لقواعد مساحات واضحة عند الدمج.

تنبيه جودة في التطبيق: `brand_fabmisr.png` و`brand_alexbank.png` أبيضان بالكامل على خلفية شفافة، لذلك يظهران على قرص غامق (`BRAND`) لا على الدائرة البيضاء الافتراضية. كل أماكن عرض الشعار (الحسابات، التجار، معاينة استيراد SMS) تستخدم مكوّنًا واحدًا هو `UiKit.logoBadge()`، والقائمة البيضاء في `UiKit.whiteOnlyLogo()`.

تحققنا من decoding الصور/بنية SVG وhashes وعدم وجود scripts/foreignObject. المعاينة فتحت في متصفح محلي، الصور الـ15 حمّلت في المعاينة الأولى؛ الأصل المصري الجديد لـEspressolab فُحص منفصلًا وظهر على Samsung، وفلاتر الكتالوج عملت. مشاهدة المعاينة لا تثبت صلاحية VectorDrawable أو اكتمال حقوق الإصدار التجاري.

## أسماء البنوك القديمة

لا نعتمد الكولاج المرسل كقائمة حالية. أمثلة مؤكدة من المصادر الرسمية:

- [QNB ALAHLI أصبح QNB](https://www.qnb.com.eg/sites/qnb/qnbegypt/en/enbrandunification.html).
- [Bank Audi Egypt اندمج تحت FABMISR](https://www.fabmisr.com.eg/en/press-room/2022/legal-merger).
- [aiBANK أصبح Bank NXT](https://nxtwin.banknxteg.com/pages/about).
- [Ahli United Bank Egypt أصبح KFH Egypt](https://www.eg.kfh.com/wp-content/uploads/2025/07/Financial-Statements-Q2-Consolidated-English.pdf).
- [BLOM Egypt اندمج مع Bank ABC Egypt](https://www.bank-abc.com/en/Media%20Relations/PressRelease/Pages/Bank-ABC-Egypt-successfully-completes-integration--with-BLOM-Bank-Egypt.aspx).

الأسماء القديمة للمساعدة في فهم الرسائل القديمة؛ لا تُستخدم لتغيير bank/account history أو دمج حسابين تلقائيًا. `Arab Investment Bank/aiBANK` يختلف عن `Arab International Bank/AIB`: يلزم sender أو package أو تأكيد إضافي قبل التعيين.

## طريقة الاستخدام المقترحة

احتفظ بـ`entity_id` منفصل عن نص التاجر الأصلي، وحدد merchant وbank وpayment provider/network كلًا على حدة. Visa/InstaPay/Fawry ليست دليلًا على هوية المحل المستفيد. اربط الاسم والشعار بقائمة aliases مؤكدة وأولوية exact match؛ لا تطابق `ABC` أو `WE` أو `CA` مع substring عشوائي. المجهول يعرض حرفًا/أيقونة تصنيف مع إمكانية تصحيح الاسم من المستخدم.

تخزين محلي للشعارات أنسب للإصدار الحالي بلا اتصال شبكة: ظهور سريع ويعمل offline، ولا تُرسل أسماء المتاجر أو الرسائل إلى logo API. حزم لاحقة اختيارية لكل بلد أفضل من إدخال آلاف العلامات العالمية التي لا تخدم المستخدم الحالي.

## الحقوق

الأصول المتاحة لا تعني إذن استخدام تجاري مفتوح لكل علامة. خزّنا `rights_status=review_brand_terms_before_release` ومصادر/guidelines عند توفرها. [تنبيه Simple Icons](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md) يفرّق بين CC0 للمشروع وحقوق العلامات الفردية. لا توجد شراكة أو endorsement مع البنوك أو التجار؛ مراجعة شروط العلامة جزء من اعتماد الأصول للإصدار.

تم الدمج في مرحلة1 دون إضافة مكتبة Android، ودون نسخ assets من MoneyLoop أو commit/push. أداة تحويل SVG تستخدم sharp المتاح محليًا خارج التطبيق.
