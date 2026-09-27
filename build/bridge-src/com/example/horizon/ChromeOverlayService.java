package com.example.horizon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * HORIZON12 chrome: the always-on-top left rail and right quick switcher.
 *
 * Replaces HomeOverlayService. That service existed only to float a single HOME
 * button, because this unit has NO hardware HOME key (input devices are
 * goodix-ts and goodix-ts_key, which emits only BTN_TOUCH). The rail's HOME
 * button does that job now, permanently and visibly.
 *
 * GEOMETRY, confirmed from dumpsys window on the live unit (do not guess these):
 *   mStable = [170,0][1920,1080]        CarSystemUI nav bar insets apps to x>=170
 *   mBounds = [0,0][1491,1080]          split primary
 *   mBounds = [1491,0][1920,1080]       split secondary
 * So: rail 170 wide at x=0, switcher 429 wide at x=1491, center 1321 between.
 * Display is 1920x1080 at density 160, so dp == px. Sizes below are raw pixels
 * on purpose: the HTML is designed pixel-exact and must not be rescaled.
 *
 * WHY WE DO NOT COVER CARPLAY:
 * We deliberately restore the stock reservations rather than fight them.
 * CarSystemUI keeps insetting apps to x>=170 and com.skypine.split keeps the
 * right 429px reserved, so the center pane geometry stays exactly stock. These
 * two overlays paint over strips that no app draws into. That is the whole
 * trick; see CHROME-REDESIGN-PLAN.md section 3.1.
 *
 * NOTE: writes nothing to /system and needs no platform signature. The 09-11
 * boot loop came from a priv-app with a BootReceiver polling CarService on the
 * main thread. Nothing here touches CarService.
 */
public class ChromeOverlayService extends Service {

    private static final String TAG = "HorizonChrome";

    private static final int RAIL_W     = 170;
    private static final int SWITCHER_W = 429;
    private static final int SCREEN_H   = 1080;

    private WindowManager wm;
    private WebView rail;
    private WebView switcher;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundQuietly();
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            // OUR RAIL IS OFF BY DEFAULT (2026-09-23, the author's call).
            // A system navigation-bar window outranks TYPE_APPLICATION_OVERLAY, so when
            // CarSystemUI shows LeftCarNavigationBar (which it does whenever CarPlay is
            // up) it draws ABOVE our rail and pushes ours to x=170..340, on top of the
            // centre app. And that same nav bar is what insets apps to x>=170, so hiding
            // it to win the left strip would also lose the inset that makes the whole
            // three-surface trick work. We keep the stock rail and recolour it with a
            // Runtime Resource Overlay instead; the switcher is unaffected and stays.
            // Drop a file at /sdcard/horizon/rail.enabled to bring ours back, no rebuild.
            if (new java.io.File("/sdcard/horizon/rail.enabled").exists()) {
                rail = addPane("file:///sdcard/horizon/rail.html",
                               RAIL_W, Gravity.LEFT | Gravity.TOP);
                Log.i(TAG, "rail overlay ON (rail.enabled present)");
            } else {
                Log.i(TAG, "rail overlay OFF - stock LeftCarNavigationBar owns x=0..170");
            }
            switcher = addPane("file:///sdcard/horizon/switcher.html",
                               SWITCHER_W, Gravity.RIGHT | Gravity.TOP);
            Log.i(TAG, "chrome overlays up: switcher " + SWITCHER_W + (rail != null ? " + rail " + RAIL_W : " (stock rail)"));
        } catch (Exception e) {
            // SYSTEM_ALERT_WINDOW not granted, or WindowManager refused the type.
            // Fail quiet: the center cockpit still works without chrome.
            Log.e(TAG, "overlay add failed: " + e);
        }
    }

    private WebView addPane(String url, int width, int gravity) {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        w.setBackgroundColor(0xFF07090D);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.addJavascriptInterface(new ChromeBridge(), "Horizon");
        w.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.i("HorizonWeb", "[chrome] " + m.message()
                        + " @" + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // NOT_FOCUSABLE keeps us out of the input-focus chain (no keyboard, and the
        // center app keeps focus) while still receiving touch. NOT_TOUCH_MODAL lets
        // touches outside our rect reach whatever is underneath. Same flag set as
        // the floating HOME chip, which is proven on this unit.
        // Do NOT add FLAG_NOT_TOUCHABLE: that would pass our own touches through too.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, SCREEN_H, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        // LAYOUT_NO_LIMITS is load-bearing: without it the window manager
                        // insets this overlay past CarSystemUI's 170px nav bar, so the rail
                        // lands at x=170..340 on top of the centre app instead of at x=0.
                        // The switcher looked fine only because there is no right inset.
                        // Measured on the unit 2026-09-23.
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = 0;
        lp.y = 0;

        wm.addView(w, lp);
        w.loadUrl(url);
        return w;
    }

    /** JS bridge for both chrome panes. Mirrors MainActivity's Bridge, plus nav/volume. */
    private class ChromeBridge {

        /** Route a nav request to the center WebView, bringing it to front first. */
        @JavascriptInterface
        public void nav(String screen) {
            try {
                Intent i = new Intent(ChromeOverlayService.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                MainActivity.navTo(screen);
                Log.i(TAG, "nav " + screen);
            } catch (Exception e) { Log.e(TAG, "nav err: " + e); }
        }

        /** up | down | mute | unmute on STREAM_MUSIC. */
        @JavascriptInterface
        public void volume(String dir) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                int adj;
                if ("up".equals(dir))          adj = AudioManager.ADJUST_RAISE;
                else if ("down".equals(dir))   adj = AudioManager.ADJUST_LOWER;
                else if ("mute".equals(dir))   adj = AudioManager.ADJUST_MUTE;
                else if ("unmute".equals(dir)) adj = AudioManager.ADJUST_UNMUTE;
                else                           adj = AudioManager.ADJUST_SAME;
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, AudioManager.FLAG_SHOW_UI);
                Log.i(TAG, "volume " + dir);
            } catch (Exception e) { Log.e(TAG, "volume err: " + e); }
        }

        /** next | prev | play, dispatched as media keys. Same as MainActivity.Bridge. */
        @JavascriptInterface
        public void mediaKey(String action) {
            try { MainActivity.dispatchMediaKey(ChromeOverlayService.this, action); }
            catch (Exception e) { Log.e(TAG, "mediaKey err: " + e); }
        }

        /** Launch a component, "pkg/.Activity". */
        @JavascriptInterface
        public void launch(String comp) {
            try { MainActivity.launchComponent(ChromeOverlayService.this, comp); }
            catch (Exception e) { Log.e(TAG, "launch err: " + e); }
        }

        /** Climate write. No-op until the HzCar command channel exists; UI updates today. */
        @JavascriptInterface
        public void setHvac(String kv) {
            Log.i(TAG, "setHvac " + kv + " (no-op: helper command channel not wired yet)");
        }

        /** Voice trigger. Placeholder until the assistant target is picked. */
        @JavascriptInterface
        public void voice(String unused) {
            Log.i(TAG, "voice (not wired)");
        }
    }

    private void startForegroundQuietly() {
        try {
            String ch = "horizon_chrome";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel c = new NotificationChannel(
                        ch, "Horizon Chrome", NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
                Notification n = new Notification.Builder(this, ch)
                        .setContentTitle("Horizon chrome")
                        .setSmallIcon(android.R.drawable.ic_menu_view)
                        .build();
                startForeground(8, n);
            }
        } catch (Exception e) { }
    }

    private void drop(WebView w) {
        try {
            if (w != null && wm != null) { wm.removeView(w); w.destroy(); }
        } catch (Exception e) { }
    }

    @Override
    public void onDestroy() {
        drop(rail);
        drop(switcher);
        rail = null;
        switcher = null;
        super.onDestroy();
    }
}
