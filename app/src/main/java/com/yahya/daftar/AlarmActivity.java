package com.yahya.daftar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** شاشة التنبيه: تظهر فوق قفل الشاشة وتوقظ الجهاز، وتُغلق مع توقف الخدمة (بعد 90 ثانية كحد أقصى). */
public class AlarmActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView titleView;
    private TextView bodyView;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUi();
        applyIntent(getIntent());

        ContextCompat.registerReceiver(this, stopped, new IntentFilter(AlarmService.ACTION_STOPPED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, AlarmService.MAX_MS + 3000L);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    private void applyIntent(Intent in) {
        String t = in.getStringExtra("title");
        String body = in.getStringExtra("body");
        titleView.setText(t == null ? "تنبيه الحصة" : t);
        bodyView.setText(body == null ? "" : body);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button makeButton(String text, int bg, int fg) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setTextSize(20);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setTextColor(fg);
        bt.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(18));
        bt.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(14);
        bt.setLayoutParams(lp);
        return bt;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#1e3a8a"));
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView icon = new TextView(this);
        icon.setText("⏰");
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(28);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView);

        bodyView = new TextView(this);
        bodyView.setTextColor(Color.parseColor("#e0e7ff"));
        bodyView.setTextSize(22);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(18);
        bp.bottomMargin = dp(30);
        bodyView.setLayoutParams(bp);
        root.addView(bodyView);

        Button stop = makeButton("إيقاف التنبيه", Color.parseColor("#f59e0b"), Color.parseColor("#1f2937"));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(false);
            }
        });
        root.addView(stop);

        Button open = makeButton("فتح الدفتر", Color.parseColor("#3b5bb5"), Color.WHITE);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(true);
            }
        });
        root.addView(open);

        setContentView(root);
    }

    private void stopService(boolean openApp) {
        try {
            startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP));
        } catch (Exception ignored) {
        }
        if (openApp) {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try {
                unregisterReceiver(stopped);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
