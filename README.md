# GawharaMagic 2.3.10 — Elemental Spinjitzu Tornado + Main-hand Air Cast

## 2.3.10 — Elemental Spinjitzu
- Reworked Spin from a one-time burst into a sustained, 3-strand elemental tornado wrapped around the player, with fast counter-rotating ground and crown rings.
- Each element uses its own particle signature and receives a themed pulse effect (fire burns, water/earth slow, wind lifts, ice/crystal freeze-slow, etc.).
- The spin pulses damage and knock back nearby enemies once per second by default. Players with the same element and spectators are excluded.
- Default duration is 5 seconds. Re-activate Spin to stop it early. No cooldown was added; the configured mana cost is paid once when starting.
- Duration, radius, pulse damage, pulse interval, and knockback are editable in `config.yml` under `spin`.
- The animation follows the player while they move. The camera is not forcibly rotated, avoiding disorientation on Bedrock/mobile.

## 2.3.9 — Reliable right-click in empty air (Java + Bedrock/Geyser)
- Power Mode now temporarily places its marked Power Core in the player's **main hand**, not the offhand. This gives the client a real held item while using right-click, making `RIGHT_CLICK_AIR` detectable even when the player is not aiming at any block.
- The exact original main-hand item and hotbar slot are saved and restored when Power Mode is disabled, the player quits, or the player dies. The player's offhand item is left untouched.
- F toggles Power Mode even when the player was holding an item; the held item is restored afterward. Scrolling still changes the selected technique rather than changing hotbar slots.
- Right-click in open air casts immediately. Right-click on ordinary blocks casts. Chests, doors, buttons, and other interactable blocks keep their normal interaction; use Shift + right-click or `/gwm use` while targeting one of those blocks.
- Shift + F Guard and Shift + left-click Spin continue to work while Power Mode is active.
- Added compatibility cleanup for players who upgrade while still using the old 2.3.7/2.3.8 offhand-core mode.
- No cooldown was added. The 5ms duplicate packet guard remains only to avoid one physical click being handled twice.

## 2.3.8 — Bedrock right-click interaction fix and touch controls
- Added `/gwm menu` (`/gwm touch`, `/gwm mobile`, `/gwm controls`) to open a touch-friendly in-game control panel.
- Added tap/click buttons for Power Mode, previous/next technique, casting, technique selection, Shapeshift forms, and OP element switching.
- Added a paginated 50-technique browser that shows lock levels and Shapeshift kill requirements.
- The GUI uses a normal server inventory, which Geyser translates for Bedrock. It is an on-demand menu, not a permanent floating HUD overlay.


## OP access
- OP has virtual level 100 for GawharaMagic checks; this does not write artificial XP/points to the player's saved progression.
- All 50 techniques of the selected element are unlocked immediately, with no leveling or secret trials required.
- All ten Shapeshift forms are immediately available to OP without killing the corresponding mobs.
- OP bypasses the level/reputation gates of the advanced power systems and continues to use abilities without mana costs.
- OP can switch between all 13 elements with `/gmagic choose` (GUI) or `/gmagic element <ELEMENT>` without resetting saved progress.
- Non-OP players retain the normal progression, trials, kill-to-learn forms, and mana requirements.

## Admin commands
Requires `gawhara.magic.admin` (default: OP). Targets must be online for these live edits.

| Command | Effect |
|---|---|
| `/gmagic admin help` | Show all admin commands |
| `/gmagic admin info <player>` | View element, level, points, trials, mana, and reputation |
| `/gmagic admin element <player> <FIRE/WIND/...>` | Change the player's element without wiping their existing progress |
| `/gmagic admin level <player> <1-100>` | Set level and matching completed secret trials |
| `/gmagic admin points <player> <amount>` | Set progression points |
| `/gmagic admin trials <player> <0-20>` | Set completed secret trials |
| `/gmagic admin mana <player> <amount>` | Set current AuraSkills mana (clamped to the player's maximum) |
| `/gmagic admin reputation <player> <amount>` | Set elemental reputation |
| `/gmagic admin forms <player> all` | Unlock all ten Shapeshift forms |
| `/gmagic admin forms <player> <SPIDER/CREEPER/...>` | Unlock one form |
| `/gmagic admin forms <player> clear` | Clear learned forms |
| `/gmagic admin reset <player>` | Reset the player's GawharaMagic progress and magic items |

Element values accept English enum names or Arabic labels such as `النار`, `الرياح`, `الماء`, `التحول`.

## Mobile / Bedrock controls — Touch GUI (2.3.8)
The plugin now includes an in-game touch-friendly control panel. With Geyser, Bedrock inventory screens are translated to Bedrock UI, so mobile players can tap the menu buttons and Bedrock PC players can click them with a mouse.

Open the panel with `/gwm` or `/gwm menu` (also `/gwm touch`, `/gwm mobile`, or `/gwm controls`). It provides:
- Power Mode on/off button.
- Previous/next technique buttons.
- A clear Cast button for the selected technique.
- A paginated list of all 50 techniques; tap an unlocked technique to select it and see locked requirements.
- Quick buttons for Spin, Guard, Ultimate, Awakening, Forbidden Power, and rare transformation (the normal unlock requirements still apply).
- Shapeshift form list when using the Shapeshift element.
- OP element switching without erasing saved progress.
- Typing `/gwm` with no arguments also opens the touch panel; `/gmagic` remains the status/help command.

The menu is an on-demand inventory screen, not permanent floating HUD buttons. This is intentional: a normal Paper plugin cannot inject custom touch buttons into the Bedrock HUD without a client-side resource pack/add-on. Chat commands remain available as a fallback:

| Command | Action |
|---|---|
| `/gwm` or `/gwm menu` | Open the touch control panel (recommended for mobile/Bedrock PC) |
| `/gwm pw` | Toggle Power Mode |
| `/gwm next` | Select next unlocked technique |
| `/gwm prev` | Select previous unlocked technique |
| `/gwm use` | Cast selected technique |
| `/gwm ab` | List the techniques available to the selected element |
| `/gmagic forms` | View Shapeshift forms |
| `/gmagic unmorph` | Return to normal appearance |

Java PC controls: F toggles Power Mode, hotbar scroll switches moves, right-click casts (including in open air), and Shift stops flight. During Power Mode, the plugin temporarily holds a marked Power Core in the main hand and restores the exact previous item/slot when the mode ends. Bedrock PC and mobile can use the touch panel with mouse/tap input or the command fallback.

---

# GawharaMagic 2.3.5 — 50 تقنية متدرجة + طيران الرياح والنار + تعزيز البيئة

كل عنصر يحتفظ بعشر حركات أصلية مستقلة، ثم يفتح أربع درجات تطور إضافية لهذه الحركات. بذلك توجد 50 تقنية قابلة للفتح لكل عنصر: أول 10 حركات أصلية، ثم 40 نسخة متطورة تضيف تأثيرًا خاصًا بالعنصر. عنصر التحول يحتفظ بالأشكال العشرة التي يجب تعلمها بالقتل، ثم تتاح تطورات قدراتها مع التقدم.


## تحديث 2.3.4 — القوة المرتبطة بالبيئة

أصبح لكل عنصر بيئة طبيعية تزيد من فعالية قوته عندما يكون اللاعب داخلها. الزيادة الافتراضية 25% (والبرق 30%)، ويمكن تعديلها أو إيقافها من قسم `environmental-bonus` في `config.yml`.

| العنصر | البيئة التي تعززه |
|---|---|
| النار | النذر، الصحاري والسافانا والبادلانـدز، أو قرب النار والحمم |
| الماء | تحت الماء أو أثناء المطر في مكان مكشوف للسماء |
| الأرض | تحت سطح الأرض وفي الكهوف |
| الرياح | على ارتفاع 30 بلوك أو أكثر فوق مستوى البحر في العالم العادي |
| البرق | أثناء العاصفة الرعدية |
| الظلام | الأماكن شديدة الظلام، عند مستوى إضاءة 4 أو أقل |
| الجليد | البيئات الثلجية والباردة أو قرب الثلج والجليد |
| المعدن | قرب خامات الحديد/النحاس/الذهب وكتلها أو أدوات الحدادة |
| الطبيعة | الغابات والأدغال والتايغا والمستنقعات والمروج والكهوف الخضراء |
| النور | النهار الصافي في مكان مكشوف ومضيء |
| الصوت | الكهوف العميقة أو قرب السكالك وبلوكات النوت |
| الكريستال | قرب الجيود وبلورات الأميثيست |
| التحول | لا يملك تعزيزًا بيئيًا عامًا؛ قوته مرتبطة بالأشكال التي يفتحها اللاعب |

التعزيز يرفع الضرر والشفاء ومدد التأثيرات والتحكم وقوة الاندفاع/الدفع والحرق والتجميد. يشمل ضربات الأسلحة العادية ومهاراتها، ويُحسب تعزيز السلاح حسب عنصر السلاح نفسه حتى لو كان اختيار اللاعب الحالي عنصرًا آخر. يظهر إشعار `تعزيز البيئة` عند استخدام القدرة في مكان مناسب. تم تخزين إعدادات التعزيز في `config.yml` حتى يمكن موازنة كل عنصر بشكل مستقل.

## فتح الحركات وتطور القوة بالمستوى — 2.3.5

- لفل 1 يفتح أول حركة، وكل لفل يفتح تقنية جديدة حتى لفل 50.
- أول 10 تقنيات هي الحركات الأساسية المستقلة لكل عنصر. المستويات 11–50 تفتح أربع درجات متطورة، كل درجة تضيف تأثيرًا تكميليًا خاصًا بالعنصر إلى الحركة الأساسية المرتبطة بها.
- عند لفل 50 تصبح التقنيات الخمسون متاحة. من لفل 51 إلى 100 لا تظهر حركات جديدة؛ ترتفع قوة الضرر والتأثيرات تدريجيًا كل 10 مستويات.
- السكرول يتخطى الحركات المقفولة تلقائيًا، ومحاولة استخدام حركة غير مفتوحة لا تخصم المانا.
- عنصر التحول يظل محتاجًا قتل الكائن لفتح شكله، وخانة شكل جديدة تفتح مع كل لفل حتى 10. بعد ذلك تُفتح تطورات تقنيات الأشكال حتى لفل 50، بشرط تعلم الشكل الأساسي.
- الاختبارات السرية تظل مطلوبة لفتح سقف التقدم التالي كل 5 مستويات.

## طيران الرياح والنار

- **الرياح:** طيران مستمر بثلاث مراحل. من لفل 4 تظهر دوامات هواء حول الجسم، من لفل 15 تبدأ دفعات الهواء من القدمين، ومن لفل 30 تتكاثف السحب حول الجسم لتعطي هيئة رياح كاملة. استهلاكها الافتراضي 0.15 مانا كل ثانيتين.
- **النار:** طيران مستمر بثلاث مراحل، مع نفث اللهب من اليدين إلى أسفل لدفع اللاعب للأعلى والأمام. سرعتها أعلى من الرياح، وتزداد عند لفل 15 و30. استهلاكها الافتراضي 0.8 مانا كل ثانية.
- اضغط Shift لإيقاف الطيران. عند نفاد المانا يتوقف تلقائيًا. لاعبو OP لا تُخصم منهم المانا وفق النظام السابق.
- تكاليف التفعيل والسحب الدوري ومراحل التطور قابلة للتعديل في قسم `flight` داخل `config.yml`. النار تستمر في الحمم بفضل طبيعتها النارية، لكن الماء يطفئ الدفع.

## تحديث 2.3.3 — استخدام بلا كولداون وامتياز OP

- لا توجد فترات انتظار بين استخدام القدرات: جميع قدرات العناصر، ضربات القوة، مهارات الأسلحة، المهارات المتقدمة، الآثار، الاندماج، والتحولات قابلة لإعادة الاستخدام فورًا.
- اللاعب العادي يحتاج فقط إلى امتلاك مانا كافية لكل استخدام.
- اللاعب الذي يملك صلاحية OP يتجاوز تكلفة المانا بالكامل، حتى لو كانت ماناه صفرًا أو AuraSkills غير متاحة.
- في اندماج لاعبين، كل لاعب غير OP يدفع حصته؛ لاعب OP لا يدفع.
- شروط المستوى وفتح القدرات والأشكال تظل كما هي. لم يتم تجاوز نظام التقدم.
- يتم التقاط حدث اليد الأساسية فقط لمنع تنفيذ ضغطة الكليك مرتين؛ لا يوجد مؤقت انتظار بين الاستخدامات.

## عنصر التحول

- اختَر «التحول» من شاشة اختيار القوى.
- كل مستوى من مستوى العنصر يفتح خانة شكل إضافية؛ المستوى 1 يفتح خانة واحدة، والمستوى 10 يفتح الخانات العشر كاملة.
- اقتُل أحد الكائنات المحددة لتتعلم شكله: سبايدر، كريبر، سكيلتون، بليز، جاست، واردن، آيرون جولم، إيفوكر، تنين الإند، إندرمان.
- كل شكل يملك قدرة قتالية مختلفة وميزة سلبية/قتالية أثناء التحول. استخدم F لتفعيل وضع القوة، وScroll للتبديل بين الأشكال المكتسبة، وكليك يمين لتفعيل قدرة الشكل المختار. مدة التحول الافتراضية 18–30 ثانية حسب درجة القوة.
- `/gmagic forms` يعرض الأشكال المفتوحة والمقفولة. `/gmagic unmorph` يرجعك لشكلك الطبيعي.
- تغيير موديل اللاعب إلى شكل الكائن يحتاج إضافة **LibsDisguises** الاختيارية. قدرات الأشكال، جمعها، والآثار القتالية تعمل من دونها، لكن مظهر اللاعب يظل كما هو.


- لا يوجد `Kind` أو dispatcher لأنواع عامة مثل BOLT / NOVA / DASH / STORM.
- كل قدرة إضافية لها دالة تنفيذ مستقلة؛ الأدوات المشتركة مقتصرة على عمليات منخفضة المستوى مثل الضرر، وفحص الأهداف، والمؤثرات، وتأمين موضع الانتقال.
- تم الحفاظ على طريقة الاستخدام الحالية: `F` لتفعيل وضع القوة، السكرول للتبديل، وكليك يمين للإطلاق.
- أُلغي الكولداون من جميع القدرات والهجمات والمهارات والأسلحة والآثار والاندماج والتحولات؛ يمكن تكرار الاستخدام فورًا ما دامت المانا كافية.
- لاعبو OP لا تُخصم منهم المانا ويمكنهم استخدام القدرات حتى لو كانت المانا صفرًا أو AuraSkills غير متاحة. في الاندماج، كل لاعب غير OP يدفع حصته فقط.
- شروط فتح العناصر والمستويات والأشكال المكتسبة ما زالت مطبقة؛ تجاوز OP خاص بتكلفة المانا وليس فتح المحتوى المقفول.
- تكاليف المانا ما زالت قابلة للتعديل من `config.yml`، وهي الحد الوحيد للاستخدام المتكرر للاعب العادي.

## كتالوج القدرات

| العنصر | القدرات 1–3 | القدرات 4–10 — ميكانيكا مستقلة |
|---|---|---|
| النار | حلقة اللهب، كرة النار، طيران النار | لغم الجمر: فخ اقتراب؛ درب الرماد: مسار حارق؛ كماشة الحمم: حصار أعمدة؛ قوس اللهب: مسح هلالي؛ بعث العنقاء: استعادة وتطهير؛ مطر الشرر: قصف متتابع؛ زفير التنين: مخروط ناري متحرك |
| الماء | موجة المد، سجن الفقاعة، سباحة الموج | السحب العكسي: جذب متدرج؛ مرآة الماء: تمويه وانعكاسات؛ مطر الينبوع: علاج متكرر بموقع محدد؛ نفاث الضغط: دفع مركز؛ دوامة المد: دوران حول مركز؛ ستار الضباب: إخفاء وتشويش؛ زحف الليفياثان: جدار موجي يجرف الخصوم |
| الأرض | رمح الصخر، درع الأرض، الزلزال | الصخرة الدوارة: مقذوف أرضي متدحرج؛ الصدع الزاحف: شق يتقدم؛ مرساة الجبل: تثقيل ومنع قفز؛ نبض الصفائح: أعمدة متتابعة؛ خطوة الهضبة: رفع ثم هبوط؛ انهيار الوادي: تساقط صخري؛ زحزحة القارات: جذب مركزي ثم قذف |
| الريح | هبّة الرياح، قفزة الريح، الإعصار | طيران الرياح: طيران مستمر بثلاث مراحل؛ شفرة الضغط: شفرات متقاطعة؛ انزلاق الغيم: انزلاق أرضي؛ رفع الإعصار: رفع ودوران؛ المجرى الهوائي: ممر سرعة للحلفاء؛ جيب الفراغ: سحب هواء ثم تفريغ؛ كاسر السماء: رفع ثم سحق |
| البرق | الشرارة، اندفاعة البرق، المجال الساكن | المكثف: تخزين شحنة بالحركة؛ سحب الشحنة: سرقة حركة؛ قضبان التأريض: نقاط استدعاء صواعق؛ الانتقال الكهرومغناطيسي: جذب وانتقال؛ فرقعة الرعد: صدمة مؤجلة؛ الحمل الزائد: شحن وضربات دورية؛ قفص العاصفة: حصار بصواعق |
| الظلام | خطوة الظل، ضربة الظلام، عباءة الظلام | النسخ الشبحية: صور وهمية مهاجمة؛ تبادل الظلال: تبديل موقع؛ نظرة الرعب: إجبار على التراجع؛ مصاص الليل: استنزاف وامتصاص؛ الطعنة الغائبة: ظهور خلف الهدف؛ دين الظلام: لعنة مؤجلة؛ مطاردة الكسوف: مطاردة هدف بضربة ختامية |
| الجليد | نوفا الصقيع، وابل الجليد، عاصفة الثلج | انزلاق الصقيع: مسار تجميد؛ وسم الصقيع: تراكم طبقات برد؛ سجن البلور الجليدي: قفص يضيق؛ تقاطع النوازل: ضربات من اتجاهات متعددة؛ العمى الأبيض: حجب وإبطاء؛ مزلجة النهر: اندفاع جليدي؛ الصفر المطلق: تجميع صقيع ثم تجميد مساحة |
| المعدن | درع الفولاذ، المغناطيس، قبضة الحديد | حقل الجذب: مغناطيسية للأعداء والأغراض؛ مدار الشفرات: شفرات مدارية؛ الارتداد المعدني: مقذوف مرتد؛ خطاف السلاسل: سحب حسب نقطة الإصابة؛ النبضة القطبية: عكس الحركة؛ الحدادة الحية: صفائح تمتص الضرر؛ كبش الحصار: صدمة اندفاع ثقيل |
| الطبيعة | قيد الجذور، زهرة الشفاء، انفجار الأشواك | وسادة الوثب: ورقة نابضة تقذف من يطأها؛ غبار اللقاح: سحابة إرباك؛ رابطة الحياة: مشاركة الشفاء؛ سوط الكروم: كرمة تشد الهدف؛ الزهرة المفترسة: فخ عضوي؛ النمو البري: آثار علاج/ضرر؛ نداء القطيع: ذئاب حليفة |
| النور | وميضة، شعاع النور، نعمة النور | انكسار الطيف: أشعة متعددة المسارات؛ وسم الشمس: علامة تنفجر عند الانكشاف؛ ملاذ الفجر: تطهير الحلفاء؛ خطوة الطيف: انتقال لموضع مضيء آمن؛ التركيز الشمسي: تعزيز حسب الإضاءة؛ هالة الكشف: كشف وإضعاف؛ حكم السماء: أعمدة نور تتعقب الأهداف |
| الصوت | موجة الصدمة، الانفجار الصوتي، الصدى | رنين الحركة: انفجار عند حركة الهدف؛ حقل الصمت: منطقة خمود؛ رادار الصدى: كشف خلف الجدران؛ خطوة الصدى: عودة لموضع سابق؛ الرفع التوافقي: رفع بنبضات؛ التردد المعاكس: عكس الاندفاع؛ النغمة الأخيرة: ثلاث موجات متصاعدة |
| الكريستال | وابل الشظايا، درع المنشور، قفص الكريستال | منجم الجيود: ألغام شظايا؛ قلب الأوجه: درع يتشقق؛ انكسار المنشور: أشعة منعكسة؛ نمو البلور: أعمدة حبس؛ كسر الرنين: شقوق تنفجر؛ خطوة التشظي: انتقال مع شظايا خلفية؛ إزهار الماس: تفتح طبقي وشظايا |
| التحول | 10 قدرات مستقلة للأشكال | سبايدر: شبكة وتسلّق الجدران؛ كريبر: انفجار دفع بلا تكسير؛ سكيلتون: وابل سهام؛ بليز: قذيفة ومقاومة نار؛ جاست: كرة نارية ثقيلة؛ واردن: موجة صوتية؛ آيرون جولم: صدمة ودرع؛ إيفوكر: أنياب سحرية؛ تنين الإند: كرة تنفس التنين؛ إندرمان: انتقال خلف الهدف |

## المتطلبات وطريقة البناء

- Paper 1.21.4
- Java 21
- AuraSkills 2.2.8 (اعتماد `provided`؛ يجب تثبيته على السيرفر)

شغّل `mvn -B clean package` لإنتاج `target/GawharaMagic.jar`، أو استخدم GitHub Actions الموجود في `.github/workflows/`.

## التحكم الحالي

| الإدخال | الوظيفة |
|---|---|
| F واليد الأساسية فاضية | تشغيل / إيقاف وضع القوة |
| سكرول الماوس | التنقل بين التقنيات الخمسين |
| كليك يمين | استخدام القدرة المختارة |
| كليك شمال على هدف | ضربة القوة الأساسية بتكلفة مانا منخفضة |
| Shift + F واليد الأساسية فاضية | درع الحماية |

> سيرفر Paper لا يستطيع قراءة أزرار كيبورد مخصصة مثل G / C / X من دون مود على جهاز اللاعب. الأوامر `/gwm pw` و`/gwm next` و`/gwm prev` و`/gwm use` بدائل متاحة.

---

# GawharaMagic 2.0.3

GawharaMagic adds 13 elemental schools, 100 levels, secret trials every 5 levels, five distinct weapon tiers per element, empty-hand moves, and optional RPG progression systems.

## Requirements

- Paper 1.21.4
- Java 21
- AuraSkills 2.2.8 (or a compatible build that exposes the AuraSkills API)

The AuraSkills API is a Maven `provided` dependency. It is not bundled into the plugin JAR; install AuraSkills on the server before GawharaMagic.

## Mana

All new GawharaMagic abilities, weapon skills, and empty-hand elemental moves spend the player's actual AuraSkills mana. Abilities have no cooldown: players can recast immediately while they have enough mana. OP players bypass mana costs entirely, even with zero mana or without AuraSkills. AuraSkills Wisdom controls the maximum mana for non-OP players.

## New skills

- Empty main hand, any of these trigger the selected element's base move: `Shift + Right Click` on a normal block, `Shift + Right Click` on a mob/player, double-tap `Shift`, or `/gwm m`. (Minecraft does not send a right-click-in-air event when the hand is empty, so it cannot work in the air.)
- `Shift + Left Click` in the air with an empty main hand: Elemental Spin.
- `Shift + F` with an empty main hand: Elemental Guard.
- `/gmagic ultimate`: ultimate aura (unlocks at level 60 by default).
- `/gmagic awaken`: Awakening (unlocks at level 100 by default).

Spin and Ultimate use particles, buffs and non-damaging crowd control; Guard reduces incoming damage while active.

## Additional systems (idea set 4)

- `/gmagic fusion <player>`: two nearby players with different elements combine their powers. Both players spend AuraSkills mana.
- `/gmagic forbidden`: short, high-cost self-enhancement with a recovery slowdown afterward.
- `/gmagic relics`: lists relics. Admins can issue one with `/gmagic relic give <player> <tide|focus|guardian>`. Right-click the relic to activate it.
- `/gmagic spirit [summon|dismiss]`: summon or dismiss a persistent visual Allay companion.
- `/gmagic bounty`: view a contract. Accept one with `/gmagic bounty accept <mine|cast|travel>` and claim it with `/gmagic bounty claim`.
- `/gmagic reputation`: view reputation and title. Reputation is earned from contracts, fusion and progression milestones.
- `/gmagic transform`: unlock and activate the element's rare transformation (level 80 + 150 reputation by default).

The bounty board uses non-combat server contracts (mining, casting skills, and travelling); it does not place bounties on players.

## Existing commands

- `/gmagic` - status and help
- `/gmagic powers` - list the 13 elements
- `/gmagic task` - secret trial status
- `/gmagic reset <player>` - admin reset
- `/gmagic level <player> <1-100>` - admin level adjustment
- `/grestore` - restore items saved by the existing death-recovery system

## Build

Run `mvn -B clean package`. The resulting JAR is `target/GawharaMagic.jar`.


## GawharaMagic 2.0 — AuraSkills mana systems

**Required server plugin:** AuraSkills 2.2.8 for Paper 1.21.4. GawharaMagic reads the real AuraSkills mana pool and spends it through the public API. Mana costs, unlock levels, relic reputation thresholds, and the spirit's mana pulse are configurable in `config.yml`. Ability cooldowns are disabled globally; OP players bypass mana costs.

### Combat abilities (no weapon required)
- `Shift + Left Click` in the air with an empty main hand: Elemental Spin.
- `Shift + F` with an empty main hand: Elemental Guard.
- `/gmagic ultimate`: team-oriented ultimate ability (default unlock level 60).
- `/gmagic awaken`: Awakening form (default unlock level 100).
- The element's regular empty-hand move works with `Shift + Right Click` on a normal non-interactable block or on a mob/player, or by double-tapping `Shift`. Use `/gwm m` as a direct fallback.
- `/gmagic mana` displays the player's current and maximum AuraSkills mana.

### Additional systems
- `/gmagic fusion <player>` sends a 30-second invitation; `/gmagic fusion accept` confirms it and only then both players pay AuraSkills mana. `/gmagic fusion decline` rejects it.
- `/gmagic forbidden` — high-level temporary power with an after-effect.
- `/gmagic relics` and `/gmagic relic claim <tide|focus|guardian>` — claim reusable relics after reaching the configured reputation threshold. Admins can use `/gmagic relic give <player> <id>`.
- `/gmagic spirit [summon|dismiss]` — summon an elemental Allay companion; while active it periodically restores a small amount of AuraSkills mana.
- `/gmagic bounty` — non-PvP contracts for mining, casting abilities, or travelling; claim completed contracts for reputation and mana.
- `/gmagic reputation` — view elemental reputation and title.
- `/gmagic transform` — unlock and activate an element-specific rare transformation after meeting its level and reputation requirements.

Player progression, reputation, contracts, relic claims, and companion ownership are stored in `systems.yml`. A `/gmagic reset <player>` also removes this data, relic items, companion, and active power states.


## 2.0.1 — Quick Commands and Unarmed Skill Fix

- Added `/gwm` as a short root command alias (alongside `/magic`).
- Added short aliases: `/gwm m`, `/gwm s`, `/gwm g`, `/gwm u`, `/gwm a`, `/gwm f`, `/gwm mn`, `/gwm fu`, `/gwm tr`, `/gwm b`, and `/gwm rep`.
- Added `/gwm m` as a direct trigger for the selected element's unarmed ability.
- Kept the sneak + right-click gesture (air or a non-interactable block) for the unarmed ability.
- Separated the unarmed ability cooldown from weapon ability cooldowns.
- Mana failures now show the current and required AuraSkills mana instead of failing silently.
- The unarmed skill now reports if the power was not selected or the advanced mana system did not start.


### Quick command reference (2.0.3)

- `/gwm m` — selected element move (no weapon).
- `/gwm s` — Elemental Spin.
- `/gwm g` — Elemental Guard.
- `/gwm u` — Ultimate.
- `/gwm a` — Awakening.
- `/gwm f` — Forbidden Power.
- `/gwm mn` — show AuraSkills mana.
- `/gwm fu <player>` — request Fusion.
- `/gwm tr` — rare transformation.
- `/gwm b` — bounties.
- `/gwm rep` — reputation.

The unarmed move costs 8 AuraSkills mana by default (configurable under `mana.base-move`). If mana is too low, the plugin now displays the exact required and current amount.


## 2.0.2 — Distinct Elemental Weapon Skills

- Every one of the 13 elements now receives all five weapon tiers; the unarmed move remains available as an additional option.
- Weapon tiers have different active styles: targeted slash, heavy area impact, ranged elemental arrow, wide cyclone, and tier-5 elemental burst.
- Lightning weapons have five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Elemental weapon hits now have distinct passive effects for all 13 elements, including chained lightning, freeze, poison, shadow wither, sound knockback, and crystal slow.
- Weapon abilities spend AuraSkills mana (default 18, tier-scaled) and use longer cooldowns for higher tiers.
- Weapon lore now describes its tier-specific technique. Normal chest/door/button interactions remain usable.

### Elemental weapon tiers (2.0.2)

Every element now grants its five weapons as the player unlocks weapon tiers. The five active weapon techniques are intentionally different: (1) focused elemental slash, (2) heavy area impact, (3) ranged elemental arrow, (4) wide cyclone, and (5) tier-five elemental burst. Each element applies its own hit effect; examples include fire burn, water slow, earth weakness/absorption, wind knockback, shadow wither, ice freeze, nature poison/healing, sound knockback/nausea, crystal slow, and lightning chain strikes.

Lightning has five dedicated weapon skills: Spark Bolt (tier 1), Thunder Chain (tier 2), Lightning Arrow (tier 3), Lightning Storm (tier 4), and Thunder Judgment (tier 5).

Weapon skills cost AuraSkills mana (`mana.weapon`, default 18) and have tier-scaled cooldowns (`cooldowns.weapon-base`, default 8 seconds, plus `cooldowns.weapon-per-tier`, default 3 seconds per tier). The unarmed move is still available for every element. Existing owners of any element can reclaim unlocked weapons with `/gmagic weapons`.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — Per-element weapon identities

Each of the 12 schools now has five distinct weapon names, five distinct skill names, and five tier descriptions. The weapon ability dispatcher is element-specific: fire uses flame arcs and inferno bursts; water uses pressure lances and tidal waves; earth uses stone spikes and quakes; wind uses gusts and tornado movement; lightning uses five separate lightning techniques; shadow uses eclipse and phantom-step skills; ice uses freeze waves and blizzards; metal uses magnetic pulls and steel guards; nature uses roots and healing blooms; light uses radiant flashes; sound uses shockwaves and echo pulses; crystal uses shards and prism cages. All active weapon abilities continue to spend AuraSkills mana and use tier-scaled cooldowns.

## Signature effects (per element)

Every element has its own particle shape and texture (`Fx.java`), used by the empty-hand base move, weapon skills, Spin, Guard, Ultimate, Awakening and Transformation auras:

| Element | Look |
|---|---|
| Fire | twin flame helix rising + ember ring (blue soul-fire at high tier) |
| Water | two orbiting water ribbons + bubbles |
| Earth | ground crack line with erupting rock shards + stomp ring |
| Wind | widening tornado cone + expanding gust rings |
| Lightning | jagged sky-to-player bolts + crackling arcs |
| Shadow | collapsing dark ring + orbiting shadow orbs + soul smoke |
| Ice | rotating six-armed snowflake on the ground + falling snow |
| Metal | counter-rotating steel rings that clamp in, then spark outward |
| Nature | growing vines that bloom into flowers |
| Light | sun-star rays + rotating halo |
| Sound | concentric sound-wave rings travelling forward |
| Crystal | rotating glowing octahedron |


## 2.3.8 — Initial right-click fix (superseded by 2.3.9)
- Initially accepted right-click events from both hands when the offhand contained the Power Core.
- Added a 5ms duplicate-event guard only to prevent one dual-hand packet from casting twice; it does not add an ability cooldown.
- 2.3.9 replaces the offhand core with a main-hand core to make RIGHT_CLICK_AIR more dependable on Bedrock/Geyser clients.
