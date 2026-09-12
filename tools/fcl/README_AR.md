# تنصيب مودباك Tensura على FCL (Fold Craft Launcher)

أدوات جاهزة لتنصيب مودباك **Official Tensura: Reincarnated Survival Server**
(أو أي مودباك من CurseForge) داخل مشغّل **FCL** على الأندرويد، عن طريق **Termux**.

| ملف | وش يسوي |
|---|---|
| `fcl-modpack-install.sh` | يفك ملف المودباك، يقرأ `manifest.json`، ينزّل كل المودات، وينسخ مجلد `overrides` داخل نسخة FCL |
| `tensura-voice.sh` | يلقى إعدادات «صوت العالم / Voice of the World» ويشغّلها أو يطفيها |
| `android-exclude.example.txt` | قائمة استثناء اختيارية لاستبعاد مودات تكرش على الجوال |

> معلومات المودباك حسب صفحته على CurseForge: آخر إصدار **6.0.0.4** يشتغل على
> **Minecraft 1.21.1 + NeoForge** وفيه حوالي **100 مود**.
> تأكد من رقم النسخة اللي تنزّلها لأن السكربت يقرأ النسخة من الملف نفسه.

---

## 1) المتطلبات

- جوال بذاكرة **6 جيجا رام أو أكثر** (المودباك ثقيل)، ومساحة فاضية **4 جيجا** على الأقل.
- تطبيق **FCL** مثبّت من مصدره الرسمي: <https://github.com/FCL-Team/FoldCraftLauncher/releases>
- تطبيق **Termux** (من F-Droid أو GitHub — نسخة Play Store قديمة ولا تنفع).
- حساب **Minecraft Java** أصلي إذا بتلعب أونلاين على السيرفر الرسمي.

### تجهيز Termux

```bash
termux-setup-storage          # وافق على إذن الملفات
pkg update -y && pkg upgrade -y
pkg install -y bash curl unzip jq git rsync
```

---

## 2) تنزيل ملف المودباك

CurseForge يمنع التحميل الآلي لملف المودباك نفسه، فحمّله يدويًا من المتصفح:

1. افتح صفحة الملفات:
   <https://www.curseforge.com/minecraft/modpacks/official-tensura-reincarnated-server-modpack/files/all>
2. اختر آخر ملف (مثلاً `Official Tensura Reincarnated Survival Server 6.0.0.4.zip`)
   واضغط **Download** — لازم يكون ملف **Client** مو Server.
3. الملف بينزل في `/sdcard/Download/`.

---

## 3) تشغيل السكربت

```bash
git clone https://github.com/caspin123/Store-.git
cd Store-/tools/fcl
chmod +x *.sh

./fcl-modpack-install.sh -n Tensura6 "/sdcard/Download/Official Tensura Reincarnated Survival Server 6.0.0.4.zip"
```

السكربت بيطبع النسخة المطلوبة، وينزّل المودات بالتوازي، وينسخ ملفات `config` و`kubejs`.

### خيارات مفيدة

| الخيار | الفايدة |
|---|---|
| `-n, --name` | اسم النسخة داخل FCL (خليه بسيط بدون مسافات، مثل `Tensura6`) |
| `-d, --dir` | مسار مجلد `.minecraft` (الافتراضي `/storage/emulated/0/FCL/.minecraft`) |
| `-k, --api-key` | مفتاح CurseForge API — ينزّل أسرع وأثبت (اطلبه مجانًا من <https://console.curseforge.com/>) |
| `-j, --jobs` | عدد التحميلات المتوازية (خفّضه لـ `2` إذا نتك ضعيف) |
| `-x, --exclude` | ملف استثناء (انسخ `android-exclude.example.txt` وعدّله) |
| `--dry-run` | تجربة بدون تحميل |

**مهم:** السكربت آمن للإعادة — لو انقطع النت شغّله بنفس الأمر وبيكمل من وين وقف
ويتخطى اللي نزل.

مع مفتاح API:

```bash
export CURSEFORGE_API_KEY='ضع_مفتاحك_هنا'
./fcl-modpack-install.sh -n Tensura6 /sdcard/Download/المودباك.zip
```

---

## 4) إعدادات FCL

1. **الإعدادات → عزل النسخ (Version Isolation)**: فعّلها، عشان كل نسخة يكون
   لها مجلد `mods` خاص فيها.
2. **تنصيب اللودر**: من FCL سوّي نسخة جديدة باسم **نفس اسم `-n` بالضبط**
   (مثلاً `Tensura6`)، واختر **Minecraft 1.21.1** ثم **NeoForge**
   بالإصدار اللي طبعه السكربت (مثلاً `neoforge-21.1.xx`).
   - لو نصّبت اللودر بعد تشغيل السكربت، أعد تشغيل السكربت مرة ثانية — بيتخطى
     كل شي منزّل ويحط المودات في مكانها.
3. **الرام**: 3–4 جيجا. لا تعطيه كل رام الجهاز، خلّ للنظام نصيب.
4. **جافا**: **Java 21** (ضروري لـ 1.21.1).
5. **الرندرر**: لازم يدعم OpenGL 3.2+ → اختر **Zink** (مع تعريف **Turnip**
   على معالجات Adreno). خيارات مثل Holy GL4ES مخصصة للنسخ القديمة ولن تشتغل هنا.
6. أول تشغيل يمكن يأخذ 5–10 دقائق ويوقف عند شاشة الموجاك — هذا طبيعي.

---

## 5) تشغيل «صوت العالم» — World Voice / Voice of the World

«صوت العالم» في Tensura ما يجي داخل المود الأساسي، يجي من **إضافة منفصلة**.
في أكثر من إضافة والأسماء متشابهة:

| الإضافة | وش تسوي |
|---|---|
| **Tensura: Voice** | تضيف أصوات وأنميشن صوت العالم للمود الأصلي — تنطفي وتشتغل من الإعدادات |
| **Tensura ReVoice of The World** | صوت العالم «يحكم» على أسلوب لعبك ويعطيك مهارات فريدة |
| **Voice of the World** | إعداداتها في `config/voiceoftheworld-common.toml` |

### الخطوات

1. **تأكد أن الإضافة موجودة أصلاً:**

   ```bash
   ls /storage/emulated/0/FCL/.minecraft/versions/Tensura6/mods | grep -i voice
   ```

   إذا ما طلع شي، نزّل الإضافة المناسبة لنسخة **1.21.1 / NeoForge** من CurseForge
   وحطها في نفس مجلد `mods`، وتأكد أن إصدارها متوافق مع إصدار
   Tensura: Reincarnated الموجود عندك (ملفات غير متوافقة = كراش عند الإقلاع).

2. **شغّل اللعبة مرة وحدة وادخل العالم**، لأن NeoForge ما ينشئ ملفات الإعدادات
   إلا بعد أول تشغيل.

3. **اعرض الإعدادات:**

   ```bash
   ./tensura-voice.sh -i Tensura6 list
   ```

4. **فعّلها:**

   ```bash
   ./tensura-voice.sh -i Tensura6 enable
   ```

   يسوي نسخة احتياطية `*.toml.bak` قبل أي تعديل، ويقلب خيارات
   (voice / announce / sound / animation / broadcast) إلى `true`.
   للتراجع: `./tensura-voice.sh -i Tensura6 disable` أو ارجع ملف `.bak`.

5. **أعد تشغيل اللعبة.**

### ملاحظات مهمة

- الإعدادات موجودة في مكانين، والسكربت يفحص الاثنين:
  - `config/*.toml` → إعدادات عامة للعميل (أصوات، أنميشن، صوت).
  - `saves/<اسم العالم>/serverconfig/*.toml` → إعدادات خاصة بكل عالم (الإعلانات والمهارات).
- **على السيرفر الرسمي (ملتي بلاير):** إعدادات السيرفر هي اللي تحكم. تقدر تتحكم
  فقط في أصوات وأنميشن جهازك؛ إعلانات صوت العالم يقررها الأدمن.
- لو ما اشتغل الصوت بعد التفعيل: تأكد من مستوى الصوت داخل اللعبة
  (Options → Music & Sounds → **Ambient/Blocks** وليس Master فقط)،
  وتأكد أن جهازك مو على الوضع الصامت.

---

## 6) مشاكل شائعة

| المشكلة | الحل |
|---|---|
| `Permission denied` أو ما يقدر يكتب | شغّل `termux-setup-storage` وأعطِ الإذن، وتأكد أن المسار صحيح |
| بعض المودات `FAIL` | أعد تشغيل نفس الأمر (يكمل الناقص)، أو استخدم مفتاح API، أو قلّل `-j 2` |
| كراش عند الإقلاع | تأكد أن اللودر NeoForge نفس الإصدار المطبوع، وأن `mods` ما فيه ملفات من نسخة قديمة |
| `Missing mods` عند الدخول للسيرفر | عدد المودات ناقص — شغّل السكربت مرة ثانية وتأكد أن العدد يطابق `manifest` |
| اللعبة تطلع (Out of memory) | ارفع الرام لـ 4 جيجا، وسكّر التطبيقات الثانية، وقلّل Render Distance إلى 6 |
| شاشة سوداء / أشكال غريبة | غيّر الرندرر إلى Zink، واحذف الشيدرز إذا مركّبها |

---

## مصادر

- [صفحة المودباك على CurseForge](https://www.curseforge.com/minecraft/modpacks/official-tensura-reincarnated-server-modpack)
- [ملفات المودباك](https://www.curseforge.com/minecraft/modpacks/official-tensura-reincarnated-server-modpack/files/all)
- [Fold Craft Launcher — الإصدارات](https://github.com/FCL-Team/FoldCraftLauncher/releases)
- [Tensura: Voice](https://www.curseforge.com/minecraft/mc-mods/tensura-voice)
- [Tensura ReVoice of The World](https://www.curseforge.com/minecraft/mc-mods/tensura-revoice-of-the-world)
- [إعدادات Tensura: Reincarnated](https://tensura.wiki.gg/wiki/Config)
