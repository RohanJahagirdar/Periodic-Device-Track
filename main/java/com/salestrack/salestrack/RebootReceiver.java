package com.salestrack.salestrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 *The RebootReceiver is a Receiver class that extends Broadcast Receiver.
 * It has been registered in the Manifest.xml file to be notified when the device is restarted.
 *
 * This restarts the TrackingService if the user was logged in and an update was missed.
 *
 * In case the service was running and  the last update timestamp was missed the
 * update starts the service and the latest location is sent.
 *
 * In case the service was running and  the last update timestamp was not missed,
 * the AlarmManager will take care of running the update and the Reboot Receiver has to do nothing.
 */

public class RebootReceiver extends BroadcastReceiver {
    SharedPreferences prefs;

    @Override
    public void onReceive(Context context, Intent intent) {
        prefs = context.getSharedPreferences("SALES_TRACK", 0);
        if (prefs.contains("start")) {
            if (prefs.getBoolean("start", false)) {
                if (prefs.contains("next_update")) {
                    long current_time_sec = System.currentTimeMillis() / 1000;
                    long next_update = prefs.getLong("next_update", 0);
                    if (next_update < current_time_sec) {
                        Intent serviceLauncher = new Intent(context, TrackingService.class);
                        context.startService(serviceLauncher);
                    }
                }
            }
        }
    }
}
