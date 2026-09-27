package com.example.hzcar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the vehicle-data service at boot (targetSdk 23 = exempt from O bg limits). */
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent i) {
        try { c.startService(new Intent(c, HzCarService.class)); } catch (Exception e) {}
    }
}
