package com.salestrack.salestrack;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.format.Time;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.salestrack.salestrack.Networking.OkHttpRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;


/**
 *The service running in the background that will serve for the background task of tracking and reporting to the server.
 *The service keeps running in the background. It wakes up at the scheduled time and does its functioning.
 *If the user is logged in the location is fetched and reported to the server.
 *If the user is logged out, the service runs but doesn't do any task and dies off immediately till the next time of running.
 */
public class TrackingService extends Service implements
        OnConnectionFailedListener, ConnectionCallbacks{

    //Time gap provided for the service to set up and start and run first time in milliseconds.
    // 1000 milli sec or 1 sec of start time
    private static final int FIRST_RUN_TIMEOUT_MILLISEC = 1000;

    //Time gap provided for the service to set up and start and run between successive calls in milliseconds.
    // 1000 milli sec or 1 sec of start time
    private static final int SERVICE_STARTER_INTERVAL_MILLISEC = 1000;

    //Time delay before the calls to the location API for fetching the latitude and longitude in seconds.
    //60 for 1 min
    //600 for 10 min
    private static final int SERVICE_TASK_TIMEOUT_SEC = 600;

    //Android's internal code for Alarm Manager
    final int REQUEST_CODE = 1;

    //Alarm Manager, responsible for running the tracking service at the designated time
    private AlarmManager serviceStarterAlarmManager = null;

    /*
    Async task, for running it on the main thread. Service doesn't run on the main thread.
    Once the location is fetched the POST request is created on the main thread and
    the started on background thread to keep the main UIThread free.*/
    private FetchLocationTask asyncTask = null;

    //Shared Preferences file for storing the app related data. The status of the login/logout and email id are stored here.
    private SharedPreferences prefs;

    //Shared Preferences file name for the app.
    String PREFS_NAME = "SALES_TRACK";

    /*
       Boolean to indicate whether the user has logged in to make sure the tracking should be continued.
       If true: The user is logged in and the tracking is in progress. We continue updating the latitude, longitude, battery values to the server.
       If true: The user is logged out and the tracking is not in progress. We do not track or  update any values to the server.
    */
    private Boolean start = false;

    //Google API for location
    GoogleApiClient googleApiClient;

    //Object of the OKHttp class and the OKHTTP request class for making requests to the server
    OkHttpClient client = new OkHttpClient();
    OkHttpRequest request;


    //Service's internal method
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //Service's internal method. Called when the service is started.
    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(PREFS_NAME, 0);
        //Check from the shared preferences if the start boolean is true, if true set it true to the start boolean in this page.
        if(prefs.contains("start")) {
            start = prefs.getBoolean("start", false);
        }
        //If start is true i.e user is logged in, start the service that handles the location fetching and updating.
        if(start) {
            startServiceStarter();
            // Start performing service task
            serviceTask();
        } else {
            //If start is false i.e user is logged in, stop the service.

            Handler mHandler = new Handler(getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    TrackingService.this.stopSelf();
                }
            });

        }
    }

    //Stopping the service
    private void StopPerformingServiceTask() {
        if(asyncTask == null)
            serviceTask();
        asyncTask.cancel(false);
    }

    /*
        Service's internal class. Called when the service is destroyed.
        This is overridden to make sure that every time the service is destroyed,
        either by the OS or by external third party service ending apps or by the app itself, the service starts again.
        If the app user was logged in the service will start the reporting again.
      */
    @Override
    public void onDestroy() {
        // performs when user or system kills our service
        SharedPreferences prefs = getSharedPreferences("SALES_TRACK", 0);
        if(prefs.contains("start")) {
            if(prefs.getBoolean("start", true))
                StopPerformingServiceTask();
            else
                super.onDestroy();
        } else
            super.onDestroy();
    }

    private void serviceTask() {
        asyncTask = new FetchLocationTask();
        asyncTask.execute();
    }




    /**
     The async task that handles the creation of the alarms. The service sleeps till it is started again.
     The alarm manager is asked to time out for the given duration.
     When it wakes up after the time out duration it continues the task mentioned in it's internal publishProgress method.
     If canceled it breaks the loop.
    */
    class FetchLocationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params){
            try {
                if(prefs.getBoolean("start", true)) {
                    for (; ; ) {
                        TimeUnit.SECONDS.sleep(SERVICE_TASK_TIMEOUT_SEC);
                        // Initiating of onProgressUpdate callback that has access to UI

                        if (isCancelled() || !prefs.getBoolean("start", true)) {
                            break;
                        }
                        publishProgress();
                    }
                }
            } catch (InterruptedException e) {
                //Although bad practice to have an empty catch block, it's needed in a service.
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... progress) {
            /*Check if the start boolean in the Shared Preferences is true or false.
             If true the service fetches the location and updates the server.

            */
            prefs = getSharedPreferences(PREFS_NAME, 0);
            if(prefs.contains("start")) {
                start = prefs.getBoolean("start", false);
            }
            if(start) {
                super.onProgressUpdate(progress);
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        /*
                        The next_update boolean is used to set the time for the next update.
                        It is used to make sure that if the device is shut down then the service is started again if user is logged in.
                         */
                        prefs.edit().putLong("next_update", System.currentTimeMillis() / 1000 + SERVICE_TASK_TIMEOUT_SEC).apply();
                        getLocation();

                    }
                });
            }
        }
    }

    /* Here we register our service in the AlarmManager service
     for performing periodical starting of our service by the system.
     If the user is logged in the AlarmManager is asked to wake up the service at the designated timestamp
    */
    private void startServiceStarter() {
        if(prefs.contains("start")) {
            start = prefs.getBoolean("start", false);
        }
        if(start) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, this.REQUEST_CODE, intent, 0);

            if (pendingIntent != null) {
                if (serviceStarterAlarmManager == null) {
                    serviceStarterAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                }

                    serviceStarterAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
                            SystemClock.elapsedRealtime() + FIRST_RUN_TIMEOUT_MILLISEC,
                            SERVICE_STARTER_INTERVAL_MILLISEC, pendingIntent);
            }
        } else {
            //If start boolean is false
            Handler mHandler = new Handler(getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopSelf();
                }
            });
        }
    }



    //When the GoogleAPI is connected the following method is called. We connect to the API to register for the location updates.
    @Override
    public void onConnected(Bundle bundle) {
        if(googleApiClient.isConnected()) {
            try {
                LocationRequest locationRequest = LocationRequest.create();
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                locationRequest.setInterval(10000);
                locationRequest.setFastestInterval(10000);
                updateLocation();
            } catch (SecurityException e) {
                //Although bad practice to have an empty catch block, it's needed in a service.
            }
                googleApiClient.disconnect();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {}


    /**
     * It builds the googleApiClient object and connects to the GoogleAPI
     */
    private void getLocation() {
        if(checkPlayServices()) {
            if (googleApiClient == null) {
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(TrackingService.this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
            }
            googleApiClient.connect();
        }
    }

    /**
     *  The method tests for the presence of the play services on the device and in the app.
     * @return boolean If the connection to the Play Services was successful
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS)
            return false;

        return true;
    }


    /**
     * Update the location, timestamp and the battery value to the server.
     */

    private void updateLocation() {
        Location mLastLocation = LocationServices.FusedLocationApi
                .getLastLocation(googleApiClient);

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            if (mLastLocation != null) {
                long currentTimeMillis = System.currentTimeMillis();
                Time nextUpdateTime = new Time();
                nextUpdateTime.set(currentTimeMillis);
                float batteryPct = 1;
                try {
                    Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    //The javadoc specifies that the registerReceiver handles the null pointer
                    // caused due to absence of a sticky intent to handle the request.
                    //We handle it by returning the batteryPct as -1 to avoid exception from stopping the code
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPct = level / (float)scale;

                } catch (NullPointerException e){
                    //Although bad practice to have an empty catch block, it's needed in a service.
                }

                sendStatus(String.valueOf(mLastLocation.getLatitude()),
                        String.valueOf(mLastLocation.getLongitude()),
                        String.valueOf(currentTimeMillis/1000),
                        String.valueOf(batteryPct),
                        getUserId());
            }
        }else{
            Toast.makeText(this, getResources().getString(R.string.gps_off), Toast.LENGTH_SHORT).show();
            Intent gpsOptionsIntent = new Intent(
                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            gpsOptionsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(gpsOptionsIntent);
        }
    }

    /**
     * Send the status to the server for updating the location to the database.
     * @param latitude Latitude of the device's location
     * @param longitude Longitude of the device's location
     * @param time Timestamp on the device in milliseconds
     * @param battery Battery status of the device in decimal value. 1 indicates full battery, 0.5 indicates 50% battery and 0 indicates 0% battery
     * @param user_id The user_id used during login
     */
    private void sendStatus(String latitude, String longitude,
                            String time, String battery,
                            String user_id) {

         String url = getApplication().getResources().getString(R.string.update_location_url);

        request = new OkHttpRequest(client);
        HashMap<String, String> params = new HashMap<>();
        params.put("latitude", latitude);
        params.put("longitude", longitude);
        params.put("timestamp", time);
        params.put("battery", battery);
        params.put("user_id", user_id);

        request.POST(url, params, new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                //Do nothing on failure of update
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                //Do nothing on successful update
            }
        });
    }

    private String getUserId() {
        if(prefs == null)
            prefs = getSharedPreferences("SALES_TRACK", 0);
        return prefs.getString("user_id", "no_user_id");
    }
}