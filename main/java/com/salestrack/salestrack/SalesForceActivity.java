package com.salestrack.salestrack;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.salestrack.salestrack.Networking.OkHttpRequest;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * The activity that is started when the user starts the application.
 * This activity is responsible for the view being displayed and the login and logout to be handled.
 * The activity also handles the location update when the user logs in. Hence it has the location update code.
 */

public class SalesForceActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    Button start, stop;
    EditText enter_user_id, enter_password;

    private SharedPreferences prefs;
    String PREFS_NAME = "SALES_TRACK";
    Resources res;

    GoogleApiClient googleApiClient;
    OkHttpClient client = new OkHttpClient();
    OkHttpRequest request;
    RelativeLayout loading_layout;

    //Called when the Activity is Created i.e at app start
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_force);
        res = getResources();

        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        enter_user_id = (EditText) findViewById(R.id.enter_user_id);
        enter_password = (EditText) findViewById(R.id.enter_password);
        prefs = getSharedPreferences(PREFS_NAME, 0);

        loading_layout = (RelativeLayout) findViewById(R.id.loading_layout);
        loading_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Purposely kept empty to avoid any clicks till the loading is complete.
            }
        });
        enter_user_id.setText(prefs.getString("user_id", ""));

        if(prefs.getBoolean("start", false)) {
            start.setVisibility(View.GONE);
            stop.setVisibility(View.VISIBLE);
        } else {
            start.setVisibility(View.VISIBLE);
            stop.setVisibility(View.GONE);
        }

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isNetworkAvailable()) {
                    if (checkGPSEnabled()) {
                        if (!enter_user_id.getText().toString().isEmpty()) {
                            start.setEnabled(false);
                            prefs.edit().putString("user_id", enter_user_id.getText().toString()).apply();
                            loading_layout.setVisibility(View.VISIBLE);
                            login(enter_user_id.getText().toString(), enter_password.getText().toString());
                        } else {
                            Toast.makeText(SalesForceActivity.this, res.getString(R.string.invalid_login_credentials),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                           Toast.makeText(SalesForceActivity.this, res.getString(R.string.gps_off), Toast.LENGTH_SHORT).show();
                        getSharedPreferences("SALES_TRACK", 0).edit().putBoolean("start", false).apply();
                        Intent gpsOptionsIntent = new Intent(
                                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        gpsOptionsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(gpsOptionsIntent);
                    }
                } else {
                    Toast.makeText(SalesForceActivity.this, res.getString(R.string.network_error), Toast.LENGTH_LONG).show();
                }
            }
        });


        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SalesForceActivity.this, res.getString(R.string.logout_success), Toast.LENGTH_SHORT).show();
                stop.setVisibility(View.GONE);
                start.setVisibility(View.VISIBLE);
                start.setEnabled(true);
                enter_user_id.setEnabled(true);
                enter_password.setEnabled(true);

                getSharedPreferences("SALES_TRACK", 0).edit().putBoolean("start", false).apply();
                stopService(new Intent(SalesForceActivity.this, TrackingService.class));
            }
        });
    }


    /**
     * Check if the GPS location sensing is enabled.
     * @return boolean true if location is enabled, false otherwise.
     */

    private boolean checkGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * Check if hte play store services is present
     * @return boolean true if GooglePlayServices is present, false otherwise
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            return false;
        }
        return true;
    }

    /**
     * Instantiate googleApiClient object
     */

    private void getLocation () {
        if(checkPlayServices()) {
            if (googleApiClient == null) {
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(SalesForceActivity.this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
            }
            googleApiClient.connect();
        }
    }

    private void updateLocation() {
        Location mLastLocation = LocationServices.FusedLocationApi
                .getLastLocation(googleApiClient);

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            //    Toast.makeText(this, "GPS is Enabled in your device", Toast.LENGTH_SHORT).show();
            if (mLastLocation != null) {
                //    double latitude = mLastLocation.getLatitude();
                //   double longitude = mLastLocation.getLongitude();

                long currentTimeMillis = System.currentTimeMillis();
                Time nextUpdateTime = new Time();
                nextUpdateTime.set(currentTimeMillis);

                float batteryPct = 1;

                try {
                    Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    batteryPct = level / (float)scale;

                } catch (NullPointerException e){
                    //Although bad practice to have an empty catch block, it's needed in a service.
                }

                sendStatus(String.valueOf(mLastLocation.getLatitude()),
                        String.valueOf(mLastLocation.getLongitude()),
                        String.valueOf(currentTimeMillis / 1000),
                        String.valueOf(batteryPct),
                        enter_user_id.getText().toString());
            }
        } else {
            Toast.makeText(this, res.getString(R.string.gps_off), Toast.LENGTH_LONG).show();
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

        String url = res.getString(R.string.update_location_url);

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
                Handler mHandler = new Handler(getMainLooper());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SalesForceActivity.this, res.getString(R.string.location_update_failure),
                                Toast.LENGTH_SHORT).show();
                        startService(new Intent(SalesForceActivity.this, TrackingService.class));

                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
            }
        });
    }

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
                Toast.makeText(SalesForceActivity.this, res.getString(R.string.gps_error), Toast.LENGTH_SHORT).show();
            }

            googleApiClient.disconnect();
        }

    }


    /**Send the user credentials to the server to verify with the database. If the user id and password are correct present
     * in the database, login is successful and if the password or email id aren't correct, login fails
     *
     * @param user_id The user id received from the user as input
     * @param password The password received from the user as input
     */

    private void login(final String user_id, String password) {
        if (!user_id.trim().equals("") && !password.trim().equals("")) {
            String url = getResources().getString(R.string.login_url);
            HashMap<String, String> params = new HashMap<>();
            params.put("user_id",user_id.trim());
            params.put("password", password.trim());

            request = new OkHttpRequest(client);

            request.POST(url, params, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            start.setVisibility(View.VISIBLE);

                            start.setEnabled(true);
                            enter_user_id.setEnabled(true);
                            enter_password.setEnabled(true);

                            Toast.makeText(SalesForceActivity.this, res.getString(R.string.login_error),
                                    Toast.LENGTH_LONG).show();
                            loading_layout.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                loading_layout.setVisibility(View.GONE);
                                String responseStr = response.body().string();
                                if(responseStr.length() > 0) {
                                    JSONObject json = new JSONObject(responseStr);
                                    String valid = json.getString("valid");
                                    if (valid.equals("1")) {
                                        //Valid Response
                                        Toast.makeText(SalesForceActivity.this, res.getString(R.string.login_success),
                                                Toast.LENGTH_SHORT).show();
                                        getSharedPreferences("SALES_TRACK", 0).
                                                edit().putString("user_id", user_id).
                                                putBoolean("start", true).apply();
                                        getLocation();
                                        startService(new Intent(SalesForceActivity.this, TrackingService.class));
                                        start.setVisibility(View.GONE);
                                        stop.setVisibility(View.VISIBLE);
                                        enter_user_id.setEnabled(false);
                                        enter_password.setEnabled(false);
                                    } else {
                                        //Invalid Response
                                        Toast.makeText(SalesForceActivity.this, res.getString(R.string.login_failure),
                                                Toast.LENGTH_SHORT).show();
                                        start.setVisibility(View.VISIBLE);
                                        stop.setVisibility(View.GONE);

                                        start.setEnabled(true);
                                        enter_user_id.setEnabled(true);
                                        enter_password.setEnabled(true);
                                    }
                                    //To prevent incorrect
                                } else {
                                    Toast.makeText(SalesForceActivity.this, res.getString(R.string.login_failure),
                                            Toast.LENGTH_SHORT).show();
                                    start.setVisibility(View.VISIBLE);

                                    start.setEnabled(true);
                                    enter_user_id.setEnabled(true);
                                    enter_password.setEnabled(true);
                                }
                            } catch (Exception e) {
                                Toast.makeText(SalesForceActivity.this, res.getString(R.string.login_failure),
                                        Toast.LENGTH_SHORT).show();
                                start.setVisibility(View.VISIBLE);
                                stop.setVisibility(View.GONE);

                                start.setEnabled(true);
                                enter_user_id.setEnabled(true);
                                enter_password.setEnabled(true);

                            }
                        }
                    });
                }
            });
        } else {
            Toast.makeText(SalesForceActivity.this, res.getString(R.string.invalid_login_credentials),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    /**
     * Check if the network is available
     * @return true if Internet is available, false otherwise
     */

    public boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        if (info != null && info.isAvailable())
            return true;
        else
            return false;
    }
}
