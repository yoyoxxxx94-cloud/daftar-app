package com.yahya.daftar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يعيد جدولة التنبيهات بعد إعادة التشغيل (BOOT_COMPLETED) أو تحديث التطبيق (MY_PACKAGE_REPLACED). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        String a = in.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            AlarmScheduler.rescheduleAll(c);
        }
    }
}
