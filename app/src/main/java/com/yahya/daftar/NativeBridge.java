package com.yahya.daftar;

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * الجسر: يظهر في الصفحة ككائن اسمه Android.
 * أمثلة: Android.setAlarm("id", Date.now()+60000, "تنبيه", "نص"); Android.cancelAlarm("id");
 */
public class NativeBridge {
    private final MainActivity act;
    private final Context app;

    NativeBridge(MainActivity a) {
        this.act = a;
        this.app = a.getApplicationContext();
    }

    // ---------- التنبيهات ----------

    @JavascriptInterface
    public boolean isNative() {
        return true;
    }

    /** ضبط تنبيه واحد: at = وقت الانطلاق بالميلي ثانية (Date.now() + ...). */
    @JavascriptInterface
    public void setAlarm(String id, double atMillis, String title, String body) {
        AlarmScheduler.set(app, id, (long) atMillis, title, body);
    }

    @JavascriptInterface
    public void setAlarmMeta(String id, double atMillis, String title, String body, String meta) {
        AlarmScheduler.set(app, id, (long) atMillis, title, body, meta);
    }

    /** يحفظ نغمة من جهاز المستخدم ليشغّلها التنبيه الحقيقي. */
    @JavascriptInterface
    public boolean storeTone(String id, String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            java.io.File dir = new java.io.File(app.getFilesDir(), "tones");
            if (!dir.exists()) dir.mkdirs();
            java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(dir, id.replaceAll("[^A-Za-z0-9_]", "_")));
            os.write(data);
            os.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private android.media.MediaRecorder rec;
    private java.io.File recFile;

    private void recQuiet() {
        try {
            if (rec != null) {
                try {
                    rec.stop();
                } catch (Exception ignored) {
                }
                rec.release();
            }
        } catch (Exception ignored) {
        }
        rec = null;
    }

    /** تسجيل صوتي مباشر عبر أندرويد (أضمن من ميكروفون الصفحة). */
    @JavascriptInterface
    public String recStart() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "perm";
            }
            recQuiet();
            recFile = new java.io.File(app.getCacheDir(), "rec.m4a");
            rec = new android.media.MediaRecorder();
            rec.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            rec.setAudioChannels(1);
            rec.setAudioSamplingRate(22050);
            rec.setAudioEncodingBitRate(64000);
            rec.setOutputFile(recFile.getAbsolutePath());
            rec.prepare();
            rec.start();
            return "ok";
        } catch (Exception e) {
            recQuiet();
            return "err:" + e;
        }
    }

    @JavascriptInterface
    public String recStop() {
        try {
            recQuiet();
            if (recFile == null || !recFile.exists()) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(recFile);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            recFile.delete();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean removeTone(String id) {
        try {
            return new java.io.File(new java.io.File(app.getFilesDir(), "tones"), id.replaceAll("[^A-Za-z0-9_]", "_")).delete();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void pickRingtone(final String kind) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.pickRingtone(kind);
            }
        });
    }

    @JavascriptInterface
    public void previewRingtone(final String uri) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.previewRingtone(uri);
            }
        });
    }

    @JavascriptInterface
    public void startListening(final String lang) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.startListen(lang);
            }
        });
    }

    @JavascriptInterface
    public void stopListening() {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.stopListen();
            }
        });
    }

    @JavascriptInterface
    public void cancelAlarm(String id) {
        AlarmScheduler.cancel(app, id);
    }

    @JavascriptInterface
    public void cancelAll() {
        AlarmScheduler.cancelAll(app);
    }

    /** يستبدل كل التنبيهات دفعة واحدة. json = [{id,at,title,body},...]. يعيد عدد ما جُدول. */
    @JavascriptInterface
    public int setAlarms(String json) {
        return AlarmScheduler.replaceAll(app, json);
    }

    @JavascriptInterface
    public int alarmCount() {
        return AlarmScheduler.count(app);
    }

    /** تنبيه تجريبي بعد عدد من الثواني. */
    @JavascriptInterface
    public void testAlarm(int seconds) {
        long at = System.currentTimeMillis() + Math.max(3, seconds) * 1000L;
        AlarmScheduler.set(app, "test", at, "تنبيه تجريبي", "هكذا سيصلك تنبيه الحصة، حتى لو أُغلق التطبيق أو أُطفئت الشاشة.");
    }

    // ---------- الأذونات ----------

    @JavascriptInterface
    public boolean canScheduleExact() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ((AlarmManager) app.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    @JavascriptInterface
    public boolean isIgnoringBattery() {
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(app.getPackageName());
    }

    @JavascriptInterface
    public void requestPermissions() {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.askPermissions(true);
            }
        });
    }

    // ---------- الملفات ----------

    private static String safeName(String n) {
        if (n == null || n.trim().isEmpty()) n = "file";
        return n.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    /** يحفظ ملفًا (base64): الصور في المعرض، والباقي في التنزيلات. يعيد "ok" أو نصًا فارغًا عند الفشل. */
    @JavascriptInterface
    public boolean saveFileAs(final String b64, final String name, final String mime) {
        try {
            final byte[] data = Base64.decode(b64, Base64.DEFAULT);
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    act.startSaveAs(data, safeName(name), mime);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String saveFile(String b64, String name, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            name = safeName(name);
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            boolean image = mime.startsWith("image/");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver r = app.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                Uri coll;
                if (image) {
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/دفتر المعلم");
                    coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } else {
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                }
                Uri u = r.insert(coll, v);
                if (u == null) return "";
                OutputStream os = r.openOutputStream(u);
                if (os == null) return "";
                os.write(data);
                os.close();
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream os = new FileOutputStream(f);
                os.write(data);
                os.close();
                MediaScannerConnection.scanFile(app, new String[]{f.getPath()}, null, null);
            }
            return "ok";
        } catch (Exception e) {
            return "";
        }
    }

    /** يفتح نافذة المشاركة (واتساب وغيره) بالملف. */
    @JavascriptInterface
    public boolean shareFile(String b64, String name, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            name = safeName(name);
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            File dir = new File(app.getCacheDir(), "shared");
            dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream os = new FileOutputStream(f);
            os.write(data);
            os.close();
            final Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", f);
            final String type = mime;
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType(type);
                    s.putExtra(Intent.EXTRA_STREAM, uri);
                    s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent ch = Intent.createChooser(s, "مشاركة");
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    act.startActivity(ch);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
