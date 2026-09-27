package com.example.horizon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import com.skypine.common.MusicInfo;
import com.skypine.common.FMInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;

/**
 * HORIZON12 cockpit shell.
 * WebView launcher loading the UI from /sdcard/horizon/index.html (iterate with no reflash).
 * Exposes window.Horizon.launch() so tiles can open stock activities (CarPlay/camera/HDMI),
 * and pushes real Bluetooth now-playing into window.HorizonApply(...).
 */
public class MainActivity extends Activity {
    private static final String TAG = "HorizonBridge";
    /** Rail width and centre width, measured from dumpsys. 170 + 1321 + 429 = 1920. */
    private static final int RAIL_W   = 170;
    private static final int CENTER_W = 1321;

    private WebView web;
    private MediaBrowser browser;
    private MediaController controller;
    private final Handler ui = new Handler();

    /* ---------- PAC vehicle widgets ----------
     * The PAC "widgets" are AppWidgetProviders, not Activities, so they cannot be
     * started -- they have to be HOSTED. Worth the trouble because PAC reads OBD-II
     * (VehicleStatus.onJ1979Bank0Changed, J1979 = the OBD-II PID standard), which is a
     * far richer feed than CarPropertyManager: on this unit CarService yields only oil
     * temp, outside temp, gear and a dead RPM, which is why our fuel/altitude/heading/
     * speed read blank. Hosting shows PAC's own rendering, so the numbers are real
     * even where we have no property to read.
     * Binding needs permission this app does not hold; grant it once from the laptop:
     *     adb shell appwidget grantbind --package com.example.horizon --user 0
     */
    private static final int PAC_HOST_ID = 0x48525A31;   // "HRZ1"
    private AppWidgetHost pacHost;
    private AppWidgetManager pacMgr;
    private FrameLayout pacPane;      // covers the WebView while a widget is shown
    private boolean pacListening;

    private static String pacProvider(String which) {
        if ("gauges".equals(which))   return "com.aampglobal.radio/.widgets.GaugesWidget";
        if ("vehicle".equals(which))  return "com.aampglobal.radio/.widgets.VehicleInfoWidget";
        if ("offroad".equals(which))  return "com.aampglobal.radio/.widgets.OffRoadWidget";
        if ("hvac".equals(which))     return "com.aampglobal.radio/.widgets.HVACWidget";
        if ("camera".equals(which))   return "com.aampglobal.radio/.widgets.CameraWidget";
        return null;
    }

    /** Bind and show a PAC widget over the centre pane. Returns false if it could not. */
    private boolean showPacWidget(String which) {
        String comp = pacProvider(which);
        if (comp == null) { Log.e(TAG, "pac: unknown widget " + which); return false; }
        try {
            if (pacMgr == null)  pacMgr  = AppWidgetManager.getInstance(this);
            if (pacHost == null) pacHost = new AppWidgetHost(this, PAC_HOST_ID);
            if (!pacListening) { pacHost.startListening(); pacListening = true; }

            ComponentName provider = ComponentName.unflattenFromString(comp);
            int id = pacHost.allocateAppWidgetId();
            boolean bound = pacMgr.bindAppWidgetIdIfAllowed(id, provider);
            if (!bound) {
                pacHost.deleteAppWidgetId(id);
                Log.e(TAG, "pac: bind refused for " + comp
                        + " -- run: adb shell appwidget grantbind --package "
                        + getPackageName() + " --user 0");
                return false;
            }
            AppWidgetProviderInfo info = pacMgr.getAppWidgetInfo(id);
            if (info == null) {
                pacHost.deleteAppWidgetId(id);
                Log.e(TAG, "pac: no provider info for " + comp);
                return false;
            }
            AppWidgetHostView v = pacHost.createView(getApplicationContext(), id, info);
            v.setAppWidget(id, info);
            // Tell the provider how much room it has, so it lays out for the centre pane
            // rather than a phone-sized home screen cell.
            Bundle opts = new Bundle();
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,  CENTER_W);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,  CENTER_W);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 1080);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 1080);
            try { v.updateAppWidgetOptions(opts); } catch (Throwable ignore) {}

            pacPane.removeAllViews();
            pacPane.addView(v, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            pacPane.setVisibility(View.VISIBLE);
            Log.i(TAG, "pac: showing " + comp + " id=" + id);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "pac: show failed: " + e);
            return false;
        }
    }

    private void hidePacWidget() {
        try {
            if (pacPane != null) { pacPane.setVisibility(View.GONE); pacPane.removeAllViews(); }
            Log.i(TAG, "pac: hidden");
        } catch (Throwable e) { Log.e(TAG, "pac: hide failed: " + e); }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        // FLAG_FULLSCREEN dropped 2026-09-23 with the immersive flags: it suppresses
        // system chrome, and the stock rail is now part of the design.
        // LAYOUT_NO_LIMITS also dropped: it made the window span 0..1491 and ignore the
        // nav bar inset, which is what forced the manual offset in the first place.
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // The overlays already set this; the centre did not, so a freshly pushed
        // index.html could keep rendering a cached copy. Added 2026-09-23.
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // Page console -> logcat. Without this a JS error in the surfaces is silent,
        // which cost real time on 2026-09-23. Read with: adb logcat -s HorizonWeb:*
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.i("HorizonWeb", m.messageLevel() + " " + m.message()
                        + " @" + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                // /sdcard may not be mounted yet at boot -> retry until the file loads
                if (failingUrl != null && failingUrl.indexOf("horizon/index.html") >= 0 && loadTries < 40) {
                    ui.postDelayed(new Runnable() { public void run() { loadUi(); } }, 1000);
                }
            }
        });
        web.addJavascriptInterface(new Bridge(), "Horizon");
        // Handle for ChromeOverlayService: the rail and switcher are separate WebViews
        // in their own JS contexts, so a rail nav tap has to be routed through native
        // into this WebView. No onDestroy here; this is the HOME activity.
        sInstance = this;
        // The window spans the split primary (0..1491) because of FLAG_LAYOUT_NO_LIMITS,
        // so inset the WebView by hand instead of trusting system insets: exactly
        // 1321px wide at x=170. Codex's surface.css is pixel-exact at that size and
        // must not be rescaled. Before this, mFrame was [0,0][1491,1080] and the rail
        // overlay covered the left 170px of the cockpit. Measured on the unit 2026-09-23.
        // The manual RAIL_W offset is gone as of 2026-09-23. With the immersive flags
        // removed the decor view applies the system window insets itself, so the stock
        // nav bar already pushes us to x=170; adding our own 170 on top double-inset the
        // centre to 340 and clipped it against the switcher at 1491. Let the system place
        // us and just fill what we are given, which is exactly the 1321px design width.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07090D);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        // PAC widgets render on top of the cockpit and are hidden until asked for.
        pacPane = new FrameLayout(this);
        pacPane.setBackgroundColor(0xFF07090D);
        pacPane.setVisibility(View.GONE);
        root.addView(pacPane, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        loadUi();
        hideBars();
        connectMedia();
        // startHomeOverlay() retired 2026-09-23: the rail's HOME button replaces the
        // floating chip, which existed only because this unit has no hardware HOME key.
        // Leaving both running put a stray 66x124 overlay on screen. Method kept for
        // rollback; see CHROME-REDESIGN-PLAN.md section 3.2.
        startLocation();
        registerNowPlaying();
        startCarHelper();
    }

    /** Start the privileged vehicle-data helper AFTER boot (it has no BootReceiver, because
     *  auto-starting it at boot crashed CarService before it was ready). Delay so CarService
     *  is fully up. */
    private void startCarHelper() {
        ui.postDelayed(new Runnable() { public void run() {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.example.hzcar", "com.example.hzcar.HzCarService"));
                startService(i);
                Log.i(TAG, "started car helper");
            } catch (Exception e) { Log.e(TAG, "car helper start: " + e); }
        }}, 12000);
    }

    // ---- GPS: altitude / heading / speed (normal perms, actively requested) ----
    private LocationManager lm;
    private final LocationListener locListener = new LocationListener() {
        public void onLocationChanged(Location loc) {
            try {
                StringBuilder j = new StringBuilder("{\"src_gps\":1");
                j.append(",\"alt_m\":").append(loc.getAltitude());
                if (loc.hasBearing() && loc.getSpeed() > 0.5f) j.append(",\"heading\":").append(loc.getBearing());
                if (loc.hasSpeed()) j.append(",\"gspeed_ms\":").append(loc.getSpeed());
                j.append("}");
                apply(j.toString());
            } catch (Exception e) {}
        }
        public void onStatusChanged(String p, int s, Bundle b) {}
        public void onProviderEnabled(String p) {}
        public void onProviderDisabled(String p) {}
    };
    private void startLocation() {
        try {
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locListener); } catch (Exception e) { Log.e(TAG, "gps req: " + e); }
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, locListener); } catch (Exception e) {}
            Location last = null;
            try { last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception e) {}
            if (last != null) locListener.onLocationChanged(last);
        } catch (Exception e) { Log.e(TAG, "startLocation err: " + e); }
    }

    // ---- now-playing: skypine broadcasts carrying MusicInfo/FMInfo Parcelables ----
    private BroadcastReceiver npRx;
    private void registerNowPlaying() {
        try {
            npRx = new BroadcastReceiver() {
                public void onReceive(Context c, Intent it) {
                    try {
                        if (it == null || it.getAction() == null) return;
                        String a = it.getAction();
                        it.setExtrasClassLoader(getClassLoader());
                        if (a.equals("com.skypine.main.updateFM")) {
                            FMInfo fm = it.getParcelableExtra("fm_key");
                            if (fm == null) return;
                            String t = (fm.title != null && fm.title.length() > 0) ? fm.title : (fm.frequency != null ? fm.frequency : "FM RADIO");
                            StringBuilder j = new StringBuilder("{\"source\":\"FM\"");
                            j.append(",\"track\":\"").append(esc(t)).append("\"");
                            if (fm.artist != null && fm.artist.length() > 0) j.append(",\"artist\":\"").append(esc(fm.artist)).append("\"");
                            else if (fm.frequency != null) j.append(",\"artist\":\"").append(esc((fm.band != null ? fm.band + " " : "") + fm.frequency)).append("\"");
                            j.append("}");
                            apply(j.toString());
                        } else {
                            MusicInfo mi = it.getParcelableExtra("music_key");
                            if (mi == null) return;
                            String src = a.indexOf("BTMusic") >= 0 ? "BLUETOOTH" : a.indexOf("CarPlay") >= 0 ? "CARPLAY" : a.indexOf("AAMusic") >= 0 ? "ANDROID AUTO" : "USB";
                            StringBuilder j = new StringBuilder("{\"source\":\"").append(src).append("\"");
                            if (mi.title != null && mi.title.length() > 0) j.append(",\"track\":\"").append(esc(mi.title)).append("\"");
                            if (mi.singer != null && mi.singer.length() > 0) j.append(",\"artist\":\"").append(esc(mi.singer)).append("\"");
                            if (mi.albumName != null && mi.albumName.length() > 0) j.append(",\"album\":\"").append(esc(mi.albumName)).append("\"");
                            j.append("}");
                            apply(j.toString());
                        }
                    } catch (Exception e) { Log.e(TAG, "np rx err: " + e); }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction("com.skypine.main.updateMusic");
            f.addAction("com.skypine.main.updateBTMusic");
            f.addAction("com.skypine.main.updateFM");
            f.addAction("com.skypine.main.updateCarPlayMusic");
            f.addAction("com.skypine.main.updateAAMusic");
            registerReceiver(npRx, f);
            try { sendBroadcast(new Intent("com.skypine.main.REQ_INFO_ACTION")); } catch (Exception e) {}
        } catch (Exception e) { Log.e(TAG, "registerNowPlaying err: " + e); }
    }

    private int loadTries = 0;
    /** Load the UI from /sdcard, waiting/retrying until external storage is mounted (boot race). */
    private void loadUi() {
        try {
            java.io.File f = new java.io.File("/sdcard/horizon/index.html");
            if (f.exists()) { web.loadUrl("file:///sdcard/horizon/index.html"); return; }
        } catch (Exception e) {}
        if (loadTries++ < 40) ui.postDelayed(new Runnable() { public void run() { loadUi(); } }, 800);
        else web.loadUrl("file:///sdcard/horizon/index.html");
    }

    private void startHomeOverlay() {
        try {
            Intent svc = new Intent(this, HomeOverlayService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Exception e) { Log.e(TAG, "overlay start err: " + e); }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { if (pacListening && pacHost != null) { pacHost.stopListening(); pacListening = false; } }
        catch (Throwable ignore) {}
    }

    private void hideBars() {
        // Was HIDE_NAVIGATION | FULLSCREEN | IMMERSIVE_STICKY, re-applied on every focus
        // gain, which hid the stock car nav bar whenever the cockpit was focused. That
        // suited the old full-takeover design. Since 2026-09-23 the stock left rail is
        // deliberately our left chrome, recoloured by the RRO com.example.horizon.sysui,
        // so it must stay visible at all times -- not only when CarPlay forces it up.
        // LAYOUT_STABLE alone keeps our 1321px centre inset from shifting.
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    // ---- Bluetooth now-playing via the A2DP sink MediaBrowserService ----
    private void connectMedia() {
        try {
            ComponentName cn = new ComponentName("com.android.bluetooth",
                    "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService");
            browser = new MediaBrowser(this, cn, new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() {
                    try {
                        controller = new MediaController(MainActivity.this, browser.getSessionToken());
                        controller.registerCallback(mcb);
                        Log.i(TAG, "media connected");
                        pushNowPlaying();
                    } catch (Exception e) { Log.e(TAG, "onConnected err: " + e); }
                }
                @Override public void onConnectionFailed() { Log.e(TAG, "media connect failed"); retryMedia(); }
                @Override public void onConnectionSuspended() { Log.i(TAG, "media suspended"); }
            }, null);
            browser.connect();
        } catch (Exception e) { Log.e(TAG, "connectMedia err: " + e); retryMedia(); }
    }

    private void retryMedia() { ui.postDelayed(new Runnable(){ public void run(){ connectMedia(); } }, 5000); }

    private final MediaController.Callback mcb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { pushNowPlaying(); }
        @Override public void onPlaybackStateChanged(PlaybackState st) { pushNowPlaying(); }
        @Override public void onSessionDestroyed() { controller = null; connectMedia(); }
    };

    private void pushNowPlaying() {
        try {
            if (controller == null) return;
            MediaMetadata m = controller.getMetadata();
            PlaybackState ps = controller.getPlaybackState();
            String track = null, artist = null, album = null;
            if (m != null) {
                track = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
            }
            boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            StringBuilder j = new StringBuilder("{");
            j.append("\"source\":\"BLUETOOTH\"");
            if (track != null && track.length() > 0) j.append(",\"track\":\"").append(esc(track)).append("\"");
            if (artist != null && artist.length() > 0) j.append(",\"artist\":\"").append(esc(artist)).append("\"");
            if (album != null && album.length() > 0) j.append(",\"album\":\"").append(esc(album)).append("\"");
            j.append(",\"playing\":").append(playing).append("}");
            apply(j.toString());
            Log.i(TAG, "nowplaying " + track + " / " + artist);
        } catch (Exception e) { Log.e(TAG, "pushNowPlaying err: " + e); }
    }

    private void apply(final String json) {
        ui.post(new Runnable() { public void run() {
            try { web.evaluateJavascript("window.HorizonApply && window.HorizonApply(" + json + ")", null); }
            catch (Exception e) {}
        }});
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /* ------------------------------------------------------------------
       Static helpers, so ChromeOverlayService's rail and switcher reuse the
       exact same media and launch paths instead of forking a second copy.
       ------------------------------------------------------------------ */

    /** Set in onCreate. Lets the chrome overlays drive this WebView. */
    private static MainActivity sInstance;

    /** Route a nav request from the rail into the center WebView. */
    public static void navTo(final String screen) {
        final MainActivity a = sInstance;
        if (a == null || a.web == null || screen == null) return;
        a.ui.post(new Runnable() { public void run() {
            try {
                a.web.evaluateJavascript(
                        "window.HorizonNav && window.HorizonNav(\"" + esc(screen) + "\")", null);
                Log.i(TAG, "navTo " + screen);
            } catch (Exception e) { Log.e(TAG, "navTo err: " + e); }
        }});
    }

    /** next | prev | anything else = play/pause. */
    public static void dispatchMediaKey(Context ctx, String action) {
        try {
            int code = "next".equals(action) ? KeyEvent.KEYCODE_MEDIA_NEXT
                     : "prev".equals(action) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                     : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            // AudioManager.dispatchMediaKeyEvent routes through the MediaSession stack.
            // On this unit that is the wrong target: the skypine media app playing the
            // SD card publishes NO MediaSession, and the only active session is an idle
            // com.android.bluetooth one, so the key is delivered there and nothing moves.
            // A raw key injection DOES reach skypine -- verified 2026-09-23, `input
            // keyevent 87` produced com.skypine.main.updateMusic / source usb_audio.
            // ro.secure=0 and SELinux is permissive here, so exec'ing input works.
            // Keep the session dispatch as a fallback for sources that do publish one.
            boolean injected = false;
            try {
                Runtime.getRuntime().exec(new String[] { "input", "keyevent",
                        Integer.toString(code) }).waitFor();
                injected = true;
                Log.i(TAG, "mediaKey " + action + " (injected " + code + ")");
            } catch (Throwable ex) {
                Log.i(TAG, "mediaKey inject failed (" + ex + "), falling back to session");
            }
            if (!injected) {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
                Log.i(TAG, "mediaKey " + action + " (session)");
            }
        } catch (Exception e) { Log.e(TAG, "mediaKey err: " + e); }
    }

    /** Launch "pkg/.Activity" or a bare package, with a launch-intent fallback. */
    public static void launchComponent(Context ctx, String comp) {
        Log.i(TAG, "launch() called with: " + comp);
        if (comp == null || comp.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            if (comp.indexOf('/') >= 0) {
                String[] p = comp.split("/", 2);
                String pkg = p[0];
                String act = p[1];
                if (act.startsWith(".")) act = pkg + act;
                i.setComponent(new ComponentName(pkg, act));
            } else {
                i.setPackage(comp);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(i);
            Log.i(TAG, "startActivity OK: " + comp);
        } catch (Exception e) {
            Log.e(TAG, "explicit launch failed: " + e);
            try {
                String pkg = comp.indexOf('/') >= 0 ? comp.split("/", 2)[0] : comp;
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                if (li != null) {
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(li);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** JS-facing bridge. Reachable from the page as window.Horizon. */
    public class Bridge {
        @JavascriptInterface
        public void mediaKey(String action) { dispatchMediaKey(MainActivity.this, action); }

        @JavascriptInterface
        public void launch(String comp) { launchComponent(MainActivity.this, comp); }

        @JavascriptInterface
        public void nav(String screen) { navTo(screen); }

        /** Show a PAC vehicle widget over the centre: gauges|vehicle|offroad|hvac|camera. */
        @JavascriptInterface
        public boolean pacWidget(final String which) {
            final boolean[] ok = new boolean[1];
            try {
                ui.post(new Runnable() { public void run() { ok[0] = showPacWidget(which); } });
            } catch (Throwable e) { Log.e(TAG, "pacWidget err: " + e); }
            return true;   // the real result lands in logcat; JS must not block on the UI thread
        }

        @JavascriptInterface
        public void pacClose() {
            ui.post(new Runnable() { public void run() { hidePacWidget(); } });
        }
    }
}
