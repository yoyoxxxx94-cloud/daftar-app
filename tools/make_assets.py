#!/usr/bin/env python3
"""
يجهّز نسخة التطبيق من الصفحة:  web/index.html  ->  app/src/main/assets/index.html
- يزيل تسجيل الـ service worker (غير لازم داخل التطبيق)
- يوقف تنبيهات الصفحة نفسها (التنبيهات تُدار بالمنبّه الأصلي)
- يربط الحفظ (save) بمزامنة التنبيهات
- يحقن جسر أندرويد (tools/native-shim.js)
يعمل تلقائيًا داخل GitHub Actions قبل البناء. لا تحتاج تشغيله بنفسك.
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "web", "index.html")
SHIM = os.path.join(ROOT, "tools", "native-shim.js")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(OUT_DIR, "index.html")

html = open(SRC, encoding="utf-8").read()
shim = open(SHIM, encoding="utf-8").read()


def patch(old, new, required=True, label=""):
    """يستبدل أول ظهور فقط. required=True: يفشل البناء بوضوح إن لم يجد النص."""
    global html
    if old not in html:
        msg = "لم أجد النص المطلوب للتعديل: " + (label or old[:60])
        if required:
            sys.exit("خطأ: " + msg + "\n(الصفحة تغيّرت؟ أرسل لي هذا السطر وأحدّث السكربت)")
        print("تنبيه: " + msg)
        return
    html = html.replace(old, new, 1)


# 1) علامة أن الصفحة داخل التطبيق (قبل أي سكربت آخر)
patch("<head>", "<head>\n<script>window.__NATIVE=!!window.Android;</script>", label="<head>")

# 2) إزالة تسجيل service worker
i = html.find("<script>if('serviceWorker' in navigator")
if i < 0:
    print("تنبيه: لم أجد سكربت service worker (لا بأس)")
else:
    j = html.find("</script>", i) + len("</script>")
    html = html[:i] + html[j:]

# 3) إيقاف التنبيهات داخل الصفحة (المنبّه الأصلي يتولاها)
patch("function alertTick(){", "function alertTick(){if(window.__NATIVE)return;", label="function alertTick")

# 4) كل حفظ يطلب مزامنة التنبيهات
patch("function save(){\n  try{undoHook();}catch(e){}",
      "function save(){\n  try{undoHook();}catch(e){}\n  try{window.nativeSyncSoon&&nativeSyncSoon();}catch(e){}",
      label="function save")

# 5) نصوص بطاقة التنبيهات (تجميلية)
patch("، ما دامت الصفحة مفتوحة.</p>",
      "'+(window.__NATIVE?'، حتى لو أُغلق التطبيق أو أُطفئت الشاشة.':'، ما دامت الصفحة مفتوحة.')+'</p>",
      required=False, label="وصف بطاقة التنبيهات")
patch(">السماح بإشعارات المتصفح</button>",
      ">'+(window.__NATIVE?'أذونات التنبيهات':'السماح بإشعارات المتصفح')+'</button>",
      required=False, label="زر أذونات المتصفح")

# 6) حقن الجسر قبل نهاية الصفحة
patch("</body>", "<script>\n" + shim + "\n</script>\n</body>", label="</body>")

os.makedirs(OUT_DIR, exist_ok=True)
open(OUT, "w", encoding="utf-8").write(html)
print("تم: %s (%.1f MB)" % (OUT, len(html.encode("utf-8")) / 1e6))
