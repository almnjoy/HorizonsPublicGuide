package com.example.horizon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Starts the chrome overlays (left rail + right quick switcher) at power-on,
 * independently of the cockpit activity, so the driver always has a way back even
 * when the unit auto-resumes the stock radio (or CarPlay) on boot. Also nudges the
 * cockpit to the front.
 *
 * Replaces the old floating-HOME-chip start. The rail's HOME button does that job now.
 *
 * BOOT SAFETY: this is NOT the 2026-09-11 boot-loop pattern. That loop came from
 * HzCar, a /system/priv-app whose BootReceiver polled CarService on the main thread
 * before the system was ready, crashing system_server. This receiver only touches
 * WindowManager and WebView from a /data app. Nothing here talks to CarService.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent it) {
        Log.i("HorizonBridge", "BootReceiver: " + (it != null ? it.getAction() : "null"));
        // 1) Guarantee the rail and switcher are up.
        try {
            Intent svc = new Intent(ctx, ChromeOverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        } catch (Exception e) {
            Log.e("HorizonBridge", "boot chrome err: " + e);
        }
        // 2) Try to bring the cockpit to the front (allowed on API 28).
        try {
            Intent act = new Intent(ctx, MainActivity.class);
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            ctx.startActivity(act);
        } catch (Exception e) {
            Log.e("HorizonBridge", "boot activity err: " + e);
        }
    }
}
