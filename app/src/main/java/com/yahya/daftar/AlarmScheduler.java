package com.yahya.daftar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * يجدول التنبيهات بـ AlarmManager.setExactAndAllowWhileIdle ويحفظها في SharedPreferences
 * حتى تُعاد جدولتها بعد إعادة تشغيل الجهاز أو تحديث التطبيق.
 */
public final class AlarmScheduler {
    static final String PREFS = "alarms_store";
    static final String KEY = "alarms";
    static final String ACTION_FIRE = "com.yahya.daftar.ALARM_FIRE";
    static final int MAX_ALARMS = 400; // الحد الأعلى في النظام 500 لكل تطبيق

    private AlarmScheduler() {}

    private static synchronized JSONObject load(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static synchronized void store(Context c, JSONObject o) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).commit();
    }

    private static PendingIntent pi(Context c, String id, String title, String body, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("daftar://alarm/" + Uri.encode(id)));
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void arm(Context c, String id, long at, String title, String body) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, id, title, body, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    private static void disarm(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, id, "", "", PendingIntent.FLAG_NO_CREATE);
        if (p != null) {
            am.cancel(p);
            p.cancel();
        }
    }

    /** ضبط تنبيه واحد (يستبدل أي تنبيه بنفس المعرّف). */
    public static void set(Context c, String id, long at, String title, String body) {
        if (id == null || id.isEmpty()) return;
        if (at <= System.currentTimeMillis()) {
            cancel(c, id);
            return;
        }
        JSONObject all = load(c);
        try {
            all.put(id, new JSONObject().put("at", at).put("title", title).put("body", body));
        } catch (Exception ignored) {
        }
        store(c, all);
        arm(c, id, at, title, body);
    }

    public static void cancel(Context c, String id) {
        if (id == null) return;
        JSONObject all = load(c);
        all.remove(id);
        store(c, all);
        disarm(c, id);
    }

    public static void cancelAll(Context c) {
        JSONObject all = load(c);
        List<String> ids = new ArrayList<>();
        Iterator<String> it = all.keys();
        while (it.hasNext()) ids.add(it.next());
        for (String id : ids) disarm(c, id);
        store(c, new JSONObject());
    }

    /** يستبدل كل التنبيهات بالقائمة الجديدة: [{id, at, title, body}, ...] */
    public static int replaceAll(Context c, String json) {
        cancelAll(c);
        int n = 0;
        try {
            JSONArray arr = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length() && n < MAX_ALARMS; i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = (long) o.getDouble("at");
                if (at <= now) continue;
                set(c, o.getString("id"), at, o.optString("title", "تنبيه"), o.optString("body", ""));
                n++;
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    /** يُستدعى من الاستقبال بعد انطلاق التنبيه: يحذفه من المخزن فقط. */
    static void forget(Context c, String id) {
        JSONObject all = load(c);
        all.remove(id);
        store(c, all);
    }

    /** إعادة الجدولة بعد التشغيل أو التحديث أو تغيّر الوقت. */
    public static void rescheduleAll(Context c) {
        JSONObject all = load(c);
        long now = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        Iterator<String> it = all.keys();
        while (it.hasNext()) ids.add(it.next());
        for (String id : ids) {
            JSONObject o = all.optJSONObject(id);
            if (o == null) {
                all.remove(id);
                continue;
            }
            long at = o.optLong("at", 0);
            if (at > now) {
                arm(c, id, at, o.optString("title"), o.optString("body"));
            } else {
                all.remove(id);
            }
        }
        store(c, all);
    }

    public static int count(Context c) {
        return load(c).length();
    }
}
