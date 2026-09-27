package com.example.hzcar;

import android.app.Service;
import android.car.Car;
import android.car.hardware.property.CarPropertyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import java.io.FileOutputStream;

/**
 * Horizon privileged vehicle-data helper. Runs in /system/priv-app so it holds
 * the signature|privileged android.car permissions the testkey launcher can't.
 * Polls CarPropertyManager once a second and writes /sdcard/horizon/live.json,
 * which the cockpit WebView already fetches. Raw SI units; the page converts.
 */
public class HzCarService extends Service {
    private static final String TAG = "HzCar";
    private Car car;
    private CarPropertyManager cpm;
    private final Handler h = new Handler();

    @Override public int onStartCommand(Intent i, int flags, int startId) { connect(); return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    private void connect() {
        try {
            car = Car.createCar(this, new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder b) {
                    try { cpm = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE); Log.i(TAG, "car connected"); h.post(pollR); }
                    catch (Throwable e) { Log.e(TAG, "getManager " + e); }
                }
                public void onServiceDisconnected(ComponentName n) { cpm = null; }
            });
            car.connect();
        } catch (Throwable e) { Log.e(TAG, "connect " + e); h.postDelayed(new Runnable(){ public void run(){ connect(); } }, 5000); }
    }

    private final Runnable pollR = new Runnable() { public void run() { writeOnce(); h.postDelayed(this, 1000); } };

    private Float f(int id, int area) { try { return cpm.getFloatProperty(id, area); } catch (Throwable e) { return null; } }
    private Integer in(int id, int area) { try { return cpm.getIntProperty(id, area); } catch (Throwable e) { return null; } }

    private void writeOnce() {
        if (cpm == null) return;
        try {
            StringBuilder j = new StringBuilder("{\"_ts\":").append(System.currentTimeMillis() / 1000);
            addF(j, "speed_ms", f(0x11600207, 0));   // PERF_VEHICLE_SPEED
            addF(j, "rpm",      f(0x11600305, 0));    // ENGINE_RPM
            addF(j, "fuel",     f(0x11600307, 0));    // FUEL_LEVEL
            addF(j, "oil_c",    f(0x11600304, 0));    // ENGINE_OIL_TEMP
            addF(j, "out_c",    f(0x11600703, 0));    // ENV_OUTSIDE_TEMPERATURE
            Integer gear = in(0x11400400, 0);  if (gear != null) j.append(",\"gear\":").append(gear);   // GEAR_SELECTION
            Integer fan  = in(0x15400500, 75); if (fan  != null) j.append(",\"fan\":").append(fan);     // HVAC_FAN_SPEED area 75
            j.append("}");
            // /sdcard/horizon is not writable by this app under Android 9's storage
            // mount namespace even with WRITE_EXTERNAL_STORAGE declared and granted
            // (verified 2026-09-23: FileNotFoundException/Permission denied, no 1015
            // group, survives reboot). getExternalFilesDir() needs no permission at
            // all, and the cockpit WebView reads it with allowUniversalAccessFromFileURLs.
            java.io.File dir = getExternalFilesDir(null);
            java.io.File dst = (dir != null) ? new java.io.File(dir, "live.json")
                                             : new java.io.File(getFilesDir(), "live.json");
            // tmp+rename so the page never reads a half-written file
            java.io.File tmp = new java.io.File(dst.getAbsolutePath() + ".tmp");
            FileOutputStream fo = new FileOutputStream(tmp);
            fo.write(j.toString().getBytes("UTF-8"));
            fo.close();
            tmp.renameTo(dst);
            dst.setReadable(true, false);
        } catch (Throwable e) { Log.e(TAG, "write " + e); }
    }

    private void addF(StringBuilder j, String k, Float v) {
        if (v != null && !v.isNaN() && !v.isInfinite()) j.append(",\"").append(k).append("\":").append(v);
    }
}
