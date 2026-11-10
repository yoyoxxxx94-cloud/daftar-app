package com.yahya.daftar;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/** غلاف WebView: يحمّل index.html من داخل التطبيق (يعمل بدون إنترنت) ويطلب أذونات التنبيهات. */
public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int REQ_FILE = 11;
    private static final int REQ_NOTIF = 12;
    private static final int REQ_STORAGE = 13;
    private static final int REQ_FSI = 14;
    private static final int REQ_BATT = 15;
    private static final int REQ_EXACT = 16;

    private WebView web;
    private ValueCallback<Uri[]> filePathCb;
    private boolean forcePerm = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("daftar_prefs", MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String sc = u.getScheme();
                if ("blob".equals(sc) || "data".equals(sc) || "about".equals(sc)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new NativeBridge(this), "Android");

        if (b == null || web.restoreState(b) == null) web.loadUrl(HOME);

        askPermissions(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // أعد مزامنة تنبيهات الأسابيع القادمة كلما فُتح التطبيق
        web.evaluateJavascript("try{window.nativeSyncNow&&nativeSyncNow()}catch(e){}", null);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("Android");
            web.destroy();
        }
        super.onDestroy();
    }

    // ---------- اختيار الملفات (استيراد النسخ الاحتياطية والصور) ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            if (filePathCb == null) return;
            Uri[] r = null;
            if (res == RESULT_OK && data != null) {
                r = WebChromeClient.FileChooserParams.parseResult(res, data);
            }
            filePathCb.onReceiveValue(r);
            filePathCb = null;
        } else if (req == REQ_FSI) {
            stepBattery();
        } else if (req == REQ_BATT || req == REQ_EXACT) {
            // انتهت السلسلة
        }
    }

    // ---------- سلسلة الأذونات ----------

    /** force=true: يعيد السؤال حتى لو سُئل من قبل. */
    void askPermissions(boolean force) {
        forcePerm = force;
        stepStorage();
    }

    private void stepStorage() {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        stepNotifications();
    }

    private void stepNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        stepExact();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STORAGE) stepNotifications();
        else if (code == REQ_NOTIF) stepExact();
    }

    private boolean shouldAsk(String key) {
        if (forcePerm) return true;
        if (prefs.getBoolean(key, false)) return false;
        prefs.edit().putBoolean(key, true).apply();
        return true;
    }

    private void ask(String msg, final Runnable yes, final Runnable no) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("متابعة", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        yes.run();
                    }
                })
                .setNegativeButton("لاحقًا", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        no.run();
                    }
                })
                .show();
    }

    private void stepExact() {
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 33) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!am.canScheduleExactAlarms() && shouldAsk("asked_exact")) {
                ask("للتنبيه في الوقت بالضبط، اسمح للتطبيق بـ «المنبّهات والتذكيرات» في الشاشة التالية.",
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:" + getPackageName())), REQ_EXACT);
                                } catch (Exception e) {
                                    stepFullScreen();
                                }
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                stepFullScreen();
                            }
                        });
                return;
            }
        }
        stepFullScreen();
    }

    private void stepFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.canUseFullScreenIntent() && shouldAsk("asked_fsi")) {
                ask("لتظهر شاشة التنبيه فوق القفل، فعّل «الإشعارات بملء الشاشة» للتطبيق في الشاشة التالية.",
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            Uri.parse("package:" + getPackageName())), REQ_FSI);
                                } catch (Exception e) {
                                    stepBattery();
                                }
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                stepBattery();
                            }
                        });
                return;
            }
        }
        stepBattery();
    }

    private void stepBattery() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName()) && shouldAsk("asked_batt")) {
            ask("حتى لا يوقف الجهاز التنبيهات لتوفير البطارية، اسمح للتطبيق بالعمل دون قيود البطارية.",
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + getPackageName())), REQ_BATT);
                            } catch (Exception ignored) {
                            }
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
        }
    }
}
