/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.traccar.client;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;

/**
 *  IntentService for handling incoming intents that are generated as a result of requesting
 *  activity updates using
//
 */
public class DetectedActivitiesIntentService extends IntentService {

    protected static final String TAG = "DetectedActivitiesIS";
    private static int prevConfidence = 0;
    private static int prevType = 0;

    /**
     * This constructor is required, and calls the super IntentService(String)
     * constructor with the name for a worker thread.
     */
    public DetectedActivitiesIntentService() {
        // Use the TAG to name the worker thread.
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * Handles incoming intents.
     * @param intent The Intent is provided (inside a PendingIntent) when requestActivityUpdates()
     *               is called.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void onHandleIntent(Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

        ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

        int maxConfidence = 0;
        int maxType = -1;

        // Log each activity.
        for (DetectedActivity da : detectedActivities) {
            if (maxConfidence < da.getConfidence()) {
                maxConfidence = da.getConfidence();
                maxType = da.getType();
            }
        }
        try {
            if (maxConfidence > 50) { //Detect when only over 50% confidence

                switch (maxType) {
                    case DetectedActivity.IN_VEHICLE:
                    case DetectedActivity.ON_BICYCLE:
                    case DetectedActivity.ON_FOOT:
                    case DetectedActivity.RUNNING:
                    case DetectedActivity.WALKING:
                        PositionProvider.setIsMovingFlag(true);
                        if(prevType != 1) {
                            StatusActivity.addMessage(this.getString(R.string.status_activity_moving) + String.valueOf(maxConfidence));
                        }
                        prevType = 1;
                        break;
                    case DetectedActivity.STILL:
                    case DetectedActivity.TILTING:
                    case DetectedActivity.UNKNOWN:
                        if(prevType != -1) {
                            StatusActivity.addMessage("(" + this.getString(R.string.status_activity_staying) + String.valueOf(maxConfidence) + ")");
                        }
                        prevType = -1;
                        break;
                    default:
                        break;
                }
                prevConfidence = maxConfidence;
            } else {
                //Do nothing when no dominant type
                StatusActivity.addMessage("(maxType:" + String.valueOf(maxType) + ", low confidence:" + String.valueOf(maxConfidence) + ")");
            }
        }catch(Exception e){

        }
    }
}
