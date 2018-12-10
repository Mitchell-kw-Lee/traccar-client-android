/*
 * Copyright 2013 - 2017 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

public class PositionProvider implements LocationListener{

    private static final String TAG = PositionProvider.class.getSimpleName();

    static final long DETECTION_INTERVAL_IN_MILLISECONDS = 60 * 1000; // 30 seconds

    public interface PositionListener {
        void onPositionUpdate(Position position);
    }

    private final PositionListener listener;

    private final Context context;
    private SharedPreferences preferences;
    private LocationManager locationManager;
    private ActivityRecognitionClient mActivityRecognitionClient;

    public static boolean isIsMoving() {
        return isMoving;
    }

    public static void setIsMoving(boolean isMoving) {
        PositionProvider.isMoving = isMoving;
    }

    private static boolean isMoving = false;

    private String deviceId;
    private long interval;
    private double distance;
    private double angle;

    private String authorization;
    private boolean isForceFrequency = false;

    private Location lastLocation;

    private boolean isStarted = false;

    public PositionProvider(Context context, PositionListener listener){
        this.context = context;
        this.listener = listener;

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        mActivityRecognitionClient = new ActivityRecognitionClient(context);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        deviceId = preferences.getString(MainFragment.KEY_DEVICE, "undefined");
        interval = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL, "600")) * 1000;
        distance = Integer.parseInt(preferences.getString(MainFragment.KEY_DISTANCE, "0"));
        angle = Integer.parseInt(preferences.getString(MainFragment.KEY_ANGLE, "0"));
        authorization = preferences.getString(MainFragment.KEY_AUTH, "");
        isForceFrequency = preferences.getBoolean(MainFragment.KEY_FORCING, false);
    }

    @SuppressLint("MissingPermission")
    synchronized public void startUpdates() {
        if(isStarted){
            return;
        }
        isStarted = true;
        try {
			//Use this for Android OS specific efficiency
            locationManager.requestLocationUpdates(
                    getProvider(preferences.getString(MainFragment.KEY_ACCURACY, "medium")),
                    //distance > 0 || angle > 0 ? MINIMUM_INTERVAL : interval,
                    interval, //<<-- This is important. There is no reason to get frequently since user set the interval
                    isForceFrequency ? 0 : (float) distance, this);

			/*
			//Use this for common logic - more battery draining
			locationManager.requestLocationUpdates(
                    getProvider(preferences.getString(MainFragment.KEY_ACCURACY, "medium")),
                    0,
                    0.0f, this);
			*/
//            ActivityRecognitionClient
//            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
//            sensorManager.registerListener(this, accelerometer, MINIMUM_INTERVAL_ACCL_SENSOR_IN_SECOND * 1000000);
            requestActivityUpdatesButtonHandler();

        } catch (RuntimeException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(context, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void requestActivityUpdatesButtonHandler() {
        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent());

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
//                Toast.makeText(context,
//                        getString(R.string.activity_updates_enabled),
//                        Toast.LENGTH_SHORT)
//                        .show();
//                setUpdatesRequestedState(true);
//                updateDetectedActivitiesList();
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
//                Log.w(TAG, getString(R.string.activity_updates_not_enabled));
//                Toast.makeText(mContext,
//                        getString(R.string.activity_updates_not_enabled),
//                        Toast.LENGTH_SHORT)
//                        .show();
//                setUpdatesRequestedState(false);
                StatusActivity.addMessage("Activity detect listener adding failed");
            }
        });
    }

    public void removeActivityUpdatesButtonHandler() {
        Task<Void> task = mActivityRecognitionClient.removeActivityUpdates(
                getActivityDetectionPendingIntent());
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
//                Toast.makeText(mContext,
//                        getString(R.string.activity_updates_removed),
//                        Toast.LENGTH_SHORT)
//                        .show();
//                setUpdatesRequestedState(false);
//                // Reset the display.
//                mAdapter.updateActivities(new ArrayList<DetectedActivity>());
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
//                Log.w(TAG, "Failed to enable activity recognition.");
//                Toast.makeText(mContext, getString(R.string.activity_updates_not_removed),
//                        Toast.LENGTH_SHORT).show();
//                setUpdatesRequestedState(true);
                StatusActivity.addMessage("Activity detect listener removing failed");
            }
        });
    }

    public static String getProvider(String accuracy) {
        switch (accuracy) {
            case "high":
                return LocationManager.GPS_PROVIDER;
            case "low":
                return LocationManager.PASSIVE_PROVIDER;
            default:
                return LocationManager.NETWORK_PROVIDER;
        }
    }

    public boolean isMoving(){
        if(PositionProvider.isMoving){
            return true;
        }

        StatusActivity.addMessage(context.getString(R.string.status_movement_not_detected));
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        double diffDistance = lastLocation != null ? location.distanceTo(lastLocation) : 0;
        double diffBearing = lastLocation != null ? Math.abs(location.getBearing() - lastLocation.getBearing()) : 0;
        if (location != null &&
            (lastLocation == null
                ||
                (
                    location.getTime() - lastLocation.getTime() >= interval
                    &&
                        (
                            isForceFrequency
                            ||
                            (
                                    isMoving() && //보정하기 위해 넣는다.
                                    (
                                        distance > 0 ? diffDistance >= distance : true
                                        ||
                                        angle > 0 ? diffBearing >= angle : true
                                    )
                            )
                        )
                )
            )
         ) {
            Log.i(TAG, "location new");
            lastLocation = location;
            listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context), authorization));
        } else {
            Log.i(TAG, location != null ? "location ignored" : "location nil");
            StatusActivity.addMessage(context.getString(R.string.status_location_ignored));
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    synchronized public void stopUpdates() {
        removeActivityUpdatesButtonHandler();

        locationManager.removeUpdates(this);
        isStarted = false;
    }

    public static double getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        }
        return 0;
    }
}
