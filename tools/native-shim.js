/* ===== جسر أندرويد: يُحقن تلقائيًا في نسخة التطبيق فقط (لا يؤثر على نسخة الويب) ===== */
(function () {
  if (!window.Android) return;

  /* ---------- 1) الحفظ والمشاركة عبر الجسر ---------- */
  function b64OfBytes(u8) {
    var s = '', CH = 0x8000;
    for (var i = 0; i < u8.length; i += CH) s += String.fromCharCode.apply(null, u8.subarray(i, i + CH));
    return btoa(s);
  }
  async function toB64(data) {
    if (typeof data === 'string') return b64OfBytes(new TextEncoder().encode(data));
    var buf = await data.arrayBuffer();
    return b64OfBytes(new Uint8Array(buf));
  }
  function mimeOf(name, data) {
    if (data && data.type) return data.type;
    if (/\.json$/i.test(name)) return 'application/json';
    if (/\.png$/i.test(name)) return 'image/png';
    if (/\.(jpe?g)$/i.test(name)) return 'image/jpeg';
    if (/\.csv$/i.test(name)) return 'text/csv';
    return 'application/octet-stream';
  }

  /* الصفحة تستعمل claude.use('downloads').save({filename,data}) إن وُجد */
  window.claude = {
    use: async function (name) {
      if (name !== 'downloads') return null;
      return {
        save: async function (o) {
          var b64 = await toB64(o.data), mime = mimeOf(o.filename, o.data);
          if (/^image\//.test(mime)) {
            /* الصور: تُحفظ في الاستوديو */
            var r = Android.saveFile(b64, o.filename, mime);
            if (r !== 'ok') throw new Error('save failed');
          } else {
            /* الملفات: تفتح قائمة المشاركة (مدير الملفات، واتساب، جيميل...) */
            if (!Android.shareFile(b64, o.filename, mime)) throw new Error('share failed');
          }
          return true;
        }
      };
    }
  };

  /* المشاركة (واتساب وغيره) */
  navigator.canShare = function (d) { return !!(d && d.files && d.files.length); };
  navigator.share = async function (d) {
    var f = d && d.files && d.files[0];
    if (!f) throw new Error('nothing to share');
    var b64 = await toB64(f);
    if (!Android.shareFile(b64, f.name, f.type || mimeOf(f.name))) throw new Error('share failed');
  };

  /* حفظ ملف: قائمة المشاركة (مدير الملفات وغيره) */
  window.__saveAsFile = async function (data, name, mime) {
    try {
      var b64 = await toB64(data);
      return !!Android.shareFile(b64, name, mime || mimeOf(name, data));
    } catch (e) { return false; }
  };

  /* ---------- 2) مزامنة تنبيهات الجدول إلى المنبّه الأصلي ---------- */
  var WINDOW_DAYS = 14, lastJson = '', timer = null;

  function isoOf(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  function msOf(iso, min) {
    var p = iso.split('-').map(Number);
    return new Date(p[0], p[1] - 1, p[2], 0, 0, 0, 0).getTime() + Math.round(min * 60000);
  }
  function isHoliday(iso) {
    var c = S.cal || {};
    for (var i = 0; i < FIXED_HOL.length; i++) {
      var h = FIXED_HOL[i];
      if (inRange(iso, c[h[0]], c[h[1]])) return true;
    }
    return (c.hol || []).some(function (h) { return inRange(iso, h.s, h.e); });
  }

  function buildList() {
    var out = [], now = Date.now() + 2000, a = S.alerts || {}, sc = S.sched, bk = S.backup;
    var base = new Date();

    /* تذكير النسخة الاحتياطية (اليوم وغدًا) */
    if (bk && bk.remind) {
      for (var o = 0; o < 2; o++) {
        var dd = new Date(base.getFullYear(), base.getMonth(), base.getDate() + o, 12);
        var isoB = isoOf(dd);
        if (o === 0 && bk.last === isoB) continue;
        var tt = (bk.time || '14:00').split(':').map(Number);
        var atB = msOf(isoB, (tt[0] || 0) * 60 + (tt[1] || 0));
        if (atB > now) out.push({ id: 'bk|' + isoB, at: atB, title: 'تذكير النسخة الاحتياطية', body: 'احفظ نسخة اليوم من دفترك', meta: { tone: 'bell', say: 'احفظ نسخة اليوم من دفترك' } });
      }
    }

    if (!a.on || !sc) return out;
    var mk = function (k, say, vk) {
      var t = 'bell';
      try { t = toneOf(k); } catch (e) { }
      try { if (vk && typeof hasVoice === 'function' && hasVoice(vk)) t = 'c_v_' + vk; } catch (e) { }
      return { tone: t, say: say };
    };

    for (var off = 0; off < WINDOW_DAYS; off++) {
      var d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + off, 12);
      var iso = isoOf(d), day = JSDAY[d.getDay()];
      if (!sc.days.includes(day) || isHoliday(iso)) continue;
      if ((S.cal.yStart && iso < S.cal.yStart) || (S.cal.yEnd && iso > S.cal.yEnd)) continue;
      var n = dayCount(sc, day);
      var taught = function (p) { var c = sc.cells[day + '|' + p]; return !!(c && (c.subject || c.grade || c.cls)); };
      var first = 0;
      for (var p0 = 1; p0 <= n; p0++) { if (taught(p0)) { first = p0; break; } }

      periodTimes(sc, day).filter(function (t) { return t.p <= n; }).forEach(function (t) {
        var c = sc.cells[day + '|' + t.p];
        if (first && t.p === first && +a.goMin > 0) {
          var atG = msOf(iso, t.fm - a.goMin);
          if (atG > now) out.push({
            id: iso + '|go', at: atG, title: 'وقت التحرك',
            body: 'حصتك الأولى اليوم (الحصة ' + (ORD[t.p - 1] || t.p) + ') بعد ' + arMin(a.goMin) + (c && c.subject ? ' · ' + c.subject : ''),
            meta: mk('S', 'وقت التحرك', 'go')
          });
        }
        [['s', t.fm, 'بداية'], ['e', t.tm, 'نهاية']].forEach(function (x) {
          if (!taught(t.p)) return;
          var m = alertMin(t.p, x[0]);
          if (!m) return;
          var at = msOf(iso, x[1] - m);
          if (at <= now) return;
          var l1 = c ? [c.grade, c.cls].filter(Boolean).join('/') : '';
          out.push({
            id: iso + '|' + t.p + x[0], at: at, title: 'تنبيه الحصة',
            body: 'تبقى ' + arMin(m) + ' على ' + x[2] + ' الحصة ' + (ORD[t.p - 1] || t.p) + (c && c.subject ? ' · ' + c.subject : '') + (l1 ? ' · ' + l1 : ''),
            meta: mk(x[0] === 'e' ? 'E' : 'S', 'تبقى ' + arMin(m) + ' على ' + x[2] + ' الحصة ' + (ORD[t.p - 1] || t.p), x[0] + t.p)
          });
        });
      });
    }
    out.sort(function (x, y) { return x.at - y.at; });
    return out.slice(0, 400);
  }

  var toneSent = {};
  function syncTones() {
    try {
      var list = JSON.parse(localStorage.getItem('tb_tones') || '[]');
      list.forEach(function (t) {
        var key = t.id + ':' + (t.data || '').length;
        if (toneSent[key] || !t.data) return;
        var i = t.data.indexOf(',');
        if (i < 0) return;
        if (Android.storeTone(t.id, t.data.slice(i + 1))) toneSent[key] = 1;
      });
    } catch (e) { }
  }

  window.nativeSyncNow = function () {
    try {
      syncTones();
      var list = buildList(), json = JSON.stringify(list);
      if (json === lastJson && Android.alarmCount() === list.length) return;
      Android.setAlarms(json);
      lastJson = json;
    } catch (e) { /* لا نوقف الصفحة بسبب خطأ في المزامنة */ }
  };
  window.nativeSyncSoon = function () {
    clearTimeout(timer);
    timer = setTimeout(window.nativeSyncNow, 1500);
  };

  addEventListener('load', function () { setTimeout(window.nativeSyncNow, 2500); });
  document.addEventListener('visibilitychange', function () { if (!document.hidden) window.nativeSyncNow(); });

  /* ---------- الإملاء الصوتي عبر محرك أندرويد ---------- */
  if (!window.SpeechRecognition && !window.webkitSpeechRecognition && Android.startListening) {
    var curSR = null;
    var NativeSR = function () { this.lang = 'ar-SA'; this.continuous = false; this.interimResults = false; };
    NativeSR.prototype.start = function () { curSR = this; Android.startListening(this.lang || 'ar-SA'); };
    NativeSR.prototype.stop = function () { if (curSR === this) curSR = null; try { Android.stopListening(); } catch (e) { } if (this.onend) this.onend(); };
    window.__spRes = function (t, fin) {
      if (!curSR || !curSR.onresult) return;
      var alt = [{ transcript: t }]; alt.isFinal = !!fin;
      curSR.onresult({ resultIndex: 0, results: [alt] });
    };
    window.__spEnd = function () { var r = curSR; if (r && r.onend) r.onend(); };
    window.__spErr = function (code) {
      var r = curSR; curSR = null;
      if (code === 'unavailable') { try { toast('خدمة التعرف على الكلام غير متوفرة في جوالك'); } catch (e) { } code = 'service-not-allowed'; }
      if (r && r.onerror) r.onerror({ error: code });
    };
    window.SpeechRecognition = NativeSR;
    window.webkitSpeechRecognition = NativeSR;
  }

  /* ---------- 3) أزرار الاختبار والأذونات في بطاقة التنبيهات ---------- */
  window.addEventListener('click', function (ev) {
    var b = ev.target && ev.target.closest && ev.target.closest('[data-act]');
    if (!b) return;
    var act = b.getAttribute('data-act');
    if (act === 'alTest') {
      ev.stopImmediatePropagation(); ev.preventDefault();
      window.nativeSyncNow();
      try {
        syncTones();
        Android.setAlarmMeta('test|' + Date.now(), Date.now() + 10000, 'تنبيه تجريبي', 'هذا اختبار نغمة بداية الحصة', JSON.stringify({ tone: toneOf('S'), say: 'الحصة الثالثة' }));
      } catch (e2) { Android.testAlarm(10); }
      try { toast('سيصلك تنبيه تجريبي بعد ١٠ ثوانٍ، أطفئ الشاشة لتجرّبه'); } catch (e) { }
    } else if (act === 'alPerm') {
      ev.stopImmediatePropagation(); ev.preventDefault();
      Android.requestPermissions();
    }
  }, true);
})();
