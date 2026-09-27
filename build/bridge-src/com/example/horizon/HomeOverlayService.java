package com.example.horizon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Draws an always-on-top HOME button so the driver can return to the Horizon cockpit
 * from any stock app (radio, CarPlay, camera) once the stock nav bar has been hidden.
 * A small tab on the left edge — where the old CarSystemUI rail used to live.
 */
public class HomeOverlayService extends Service {
    private WindowManager wm;
    private View chip;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundQuietly();
        try { addChip(); } catch (Exception e) { /* overlay perm missing: no-op */ }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void addChip() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        TextView t = new TextView(this);
        t.setText("⌂");            // house glyph
        t.setTextColor(0xFFFF6A00);      // Mojave orange
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        t.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00A0D12);
        bg.setCornerRadii(new float[]{0, 0, dp(20), dp(20), dp(20), dp(20), 0, 0});
        bg.setStroke(dp(2), 0xFFFF6A00);   // orange edge so it stands out over any app
        t.setBackground(bg);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(66), dp(124), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goHome(); }
        });

        wm.addView(t, lp);
        chip = t;
    }

    private void goHome() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception e) { }
    }

    private void startForegroundQuietly() {
        try {
            String ch = "horizon_home";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel c = new NotificationChannel(ch, "Horizon Home", NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
                Notification n = new Notification.Builder(this, ch)
                        .setContentTitle("Horizon cockpit")
                        .setSmallIcon(android.R.drawable.ic_menu_view)
                        .build();
                startForeground(7, n);
            }
        } catch (Exception e) { }
    }

    @Override
    public void onDestroy() {
        try { if (chip != null && wm != null) wm.removeView(chip); } catch (Exception e) { }
        super.onDestroy();
    }
}
