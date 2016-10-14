package com.salestrack.salestrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

/**
 *The AlarmReceiver is a Receiver class that extends Broadcast Receiver.
 *
 * The Tracking Service sets an intent in the Alarm Manager of the device's OS.
 * A object of the AlarmReceiver class is set as the receiver of the event i.e to update the location parameters.
 *
 * Once the Alarm Manager is asked to set the AlarmReceiver intent at a fixed time, it initiates the AlarmReceiver at that time and the OnReceive is called.
 * This starts the Tracking service again and updates the server.
 */

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceLauncher = new Intent(context, TrackingService.class);
        context.startService(serviceLauncher);
    }
}
