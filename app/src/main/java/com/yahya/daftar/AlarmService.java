package com.yahya.daftar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * خدمة أمامية: تشغّل نغمة المنبّه والاهتزاز وتعرض إشعارًا بملء الشاشة،
 * وتتوقف تلقائيًا بعد 90 ثانية أو عند ضغط «إيقاف».
 */
public class AlarmService extends Service {
    static final String ACTION_START = "com.yahya.daftar.START";
    static final String ACTION_STOP = "com.yahya.daftar.STOP";
    static final String ACTION_STOPPED = "com.yahya.daftar.ALARM_STOPPED";
    static final String CHANNEL = "daftar_alarm_silent";
    static final String CHANNEL_FB = "daftar_alarm_fallback";
    static final int NOTIF_ID = 4242;
    static final long MAX_MS = 90_000L; // دقيقة ونصف

    private MediaPlayer player;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            stopAlarm();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAlarm();
            return START_NOT_STICKY;
        }
        String title = intent != null ? intent.getStringExtra("title") : null;
        String body = intent != null ? intent.getStringExtra("body") : null;
        if (title == null) title = "تنبيه الحصة";
        if (body == null) body = "";

        Notification n = build(this, title, body, false);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        releasePlayers();
        acquireWakeLock();
        startSound();
        startVibration();

        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, MAX_MS);
        return START_NOT_STICKY;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daftar:alarm");
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(MAX_MS + 10_000L);
        } catch (Exception ignored) {
        }
    }

    private void startSound() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) return;
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(aa);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            releasePlayer();
            try {
                ringtone = RingtoneManager.getRingtone(this, uri);
                if (ringtone != null) {
                    ringtone.setAudioAttributes(aa);
                    if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
                    ringtone.play();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 700, 400, 700, 400};
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), aa);
            } else {
                vibrator.vibrate(pattern, 0, aa);
            }
        } catch (Exception ignored) {
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void releasePlayers() {
        releasePlayer();
        if (ringtone != null) {
            try {
                ringtone.stop();
            } catch (Exception ignored) {
            }
            ringtone = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private void stopAlarm() {
        handler.removeCallbacks(autoStop);
        releasePlayers();
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        releasePlayers();
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    // ---------- الإشعارات ----------

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel silent = new NotificationChannel(CHANNEL, "تنبيهات الحصص", NotificationManager.IMPORTANCE_HIGH);
        silent.setDescription("يظهر عند انطلاق تنبيه الحصة (الصوت من الخدمة نفسها)");
        silent.setSound(null, null);
        silent.enableVibration(false);
        silent.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(silent);

        NotificationChannel fb = new NotificationChannel(CHANNEL_FB, "تنبيهات احتياطية", NotificationManager.IMPORTANCE_HIGH);
        fb.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        fb.enableVibration(true);
        fb.setVibrationPattern(new long[]{0, 700, 400, 700, 400, 700});
        fb.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fb);
    }

    @SuppressWarnings("deprecation")
    static Notification build(Context c, String title, String body, boolean fallback) {
        ensureChannels(c);
        Intent ai = new Intent(c, AlarmActivity.class)
                .putExtra("title", title)
                .putExtra("body", body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(c, 1, ai,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent st = new Intent(c, AlarmService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(c, 2, st,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, fallback ? CHANNEL_FB : CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(!fallback)
                .setAutoCancel(fallback)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(R.drawable.ic_stat_alarm, "إيقاف", stop);
        if (fallback && Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(MAX_MS);
        return b.build();
    }

    static void postFallback(Context c, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID + 1, build(c, title == null ? "تنبيه الحصة" : title, body == null ? "" : body, true));
        } catch (Exception ignored) {
        }
    }
}
