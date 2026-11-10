# دفتر المعلم: تطبيق أندرويد

غلاف WebView للصفحة، مع تنبيهات حقيقية عبر AlarmManager تعمل والشاشة مطفأة والتطبيق مغلق وبدون إنترنت.

## بنية المستودع

| المسار | وظيفته |
|---|---|
| `web/index.html` | **صفحة الدفتر نفسها.** هذا هو الملف الوحيد الذي تستبدله عند كل تحديث |
| `tools/make_assets.py` و`tools/native-shim.js` | يجهّزان الصفحة للتطبيق تلقائيًا أثناء البناء (لا تلمسهما) |
| `app/` | كود أندرويد (Java) والأيقونات والمفتاح الثابت `debug.keystore` |
| `.github/workflows/build.yml` | ملف البناء (يُنشأ من `workflow/build.yml`) |

## الرفع (مختصر)

1. أنشئ مستودعًا جديدًا **Public** باسم `daftar-app`.
2. ارفع محتويات هذا المجلد (Add file ← Upload files): اسحب `app` و`tools` و`web` وملفات الجذر (`build.gradle` و`settings.gradle` و`gradle.properties` و`README.md`). المجلد `workflow` اختياري.
3. أنشئ ملف البناء يدويًا (المتصفح لا يرفع المجلدات التي تبدأ بنقطة): **Add file ← Create new file**، واكتب في الاسم `.github/workflows/build.yml`، والصق محتوى `workflow/build.yml`، ثم **Commit**.
4. افتح تبويب **Actions**، وانتظر 5 إلى 10 دقائق حتى تكتمل علامة ✔ الخضراء.
5. من **Releases** (يمين صفحة المستودع) نزّل `app-debug.apk` على جوالك وثبّته.

## التحديث

- استبدل `web/index.html` بالنسخة الجديدة ثم Commit. يبني GitHub تلقائيًا ويحدّث الملف في Releases.
- ثبّت الـ APK الجديد فوق القديم: لن يُحذف شيء لأن `applicationId` والمفتاح ثابتان، ورقم الإصدار يزداد تلقائيًا.

## لا تغيّر أبدًا

- `applicationId` (`com.yahya.daftar`) في `app/build.gradle`.
- الملف `app/debug.keystore`.

وإلا فلن يُقبل التحديث فوق النسخة القديمة.
