# ASTRA PHYSICS 0.0.10-alpha — Smooth Water + Configurable Engine

Minecraft 1.21.11 / Fabric / Java 21 / Pure Java / Android ARM64-FCL target.

## أهم التغييرات

- إصلاح سلاسة حركة الـConstruct: الرندر الآن يعمل Interpolation فعلي بين مواقع السيرفر بدل القفز 20 مرة بالثانية.
- إزالة دفع الأمواج الأفقي X/Z الذي كان يسبب micro-jitter، مع فلترة Submerged Fraction حتى تغيّر الطفو نفسه يصير تدريجي. الأمواج الآن حركة عمودية خفيفة فقط.
- تحسين Water Damping، خصوصاً الحركة العمودية، لتقليل النط والتقطيع.
- رفع قوة الطفو مع نظام الكتلة الموحدة حتى يستقر الهيكل أقرب إلى سطح الماء.
- إعادة موازنة Sail وEngine؛ السرعات لا تتجمع بلا حدود.
- الدفع صار Target-Speed controller: تسارع وتباطؤ تدريجي بدل زيادة سرعة كبيرة كل Tick.

## Engine Control

بعد Assemble اضغط على أي ASTRA Engine:

- Right Click: يغيّر القوة: OFF -> 25% -> 50% -> 75% -> 100% -> OFF.
- Shift + Right Click: يبدّل وضع المحرك: MARINE <-> AIRCRAFT.

المحرك يبدأ افتراضياً على MARINE / 50%.

### MARINE

مخصص للسفن والقوارب. Propeller أقوى في الماء، وسرعته القصوى متوازنة حتى ما تتحول السفينة إلى صاروخ.

### AIRCRAFT

مخصص للطائرات/المركبات الجوية. Propeller أقوى في الهواء، السرعة الجوية أعلى، Wings تستفيد من السرعة لتوليد Lift، وThruster يعمل بكفاءة رفع أعلى.

في هذا الـAlpha إعداد الـMode والـPower مشترك لكل Engines داخل نفس الـConstruct.

## Helm

Right Click على Helm للدخول. W/S = throttle، A/D = steering، Shift أو Jump = خروج.

## Build — Termux

```bash
gradle clean build --no-daemon
```

الـJAR المتوقع:

```text
build/libs/astra-physics-0.0.10-alpha.jar
```

## ملاحظة تقنية

الدوران الحقيقي Quaternion/Yaw الكامل لم يدخل بعد؛ A/D ما زال Steering انتقالي ناعم. هذه النسخة تركز على نعومة الماء، توازن السرعة، والمحرك ثنائي الوضع.
