package com.yahya.daftar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/** يستقبل انطلاق المنبّه من AlarmManager ويشغّل خدمة الصوت وشاشة التنبيه. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        String id = in.getStringExtra("id");
        String title = in.getStringExtra("title");
        String body = in.getStringExtra("body");
        if (id != null) AlarmScheduler.forget(c, id);

        Intent s = new Intent(c, AlarmService.class)
                .setAction(AlarmService.ACTION_START)
                .putExtra("title", title)
                .putExtra("body", body);
        try {
            ContextCompat.startForegroundService(c, s);
        } catch (Exception e) {
            // إن منع النظام بدء الخدمة: إشعار عالي الأهمية بصوت واهتزاز
            AlarmService.postFallback(c, title, body);
        }
    }
}
