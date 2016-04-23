/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.app.Utility.getArtResourceForWeatherCondition;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchfaceService extends CanvasWatchFaceService
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    // to store values received from the phone
    private String mMinTemp = "--" ;
    private String mMaxTemp= "--";
    private int mWeatherId = 800;
    private boolean mIsround = false;

    private GoogleApiClient mGoogleApiClient;

    @Override
    public Engine onCreateEngine() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();

        return new Engine();
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("SunshineWatchfaceSvc", "onConnected(): Successfully connected to Google API client");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("SunshineWatchfaceSvc", "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("SunshineWatchfaceSvc", "onConnectionFailed(): Failed to connect, with result: " + connectionResult);
    }




    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchfaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchfaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchfaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mTemperaturePaint;

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mTimeYOffset;

        float mDateYOffset;

        float mTemperatureYOffset;

        float mWeatherIconWidth;
        float mWeatherIconHeight;
        float mWeatherIconYOffset;


        SimpleDateFormat mFormatedDateNormal = new SimpleDateFormat("EEE, MMM/dd/y");
        SimpleDateFormat mFormatedDateAmbient = new SimpleDateFormat("MM/dd/y");

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Wearable.DataApi.addListener(mGoogleApiClient, this);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchfaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchfaceService.this.getResources();

            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
            mDateYOffset = resources.getDimension(R.dimen.digital_date_y_offset);
            mTemperatureYOffset = resources.getDimension(R.dimen.digital_temperature_y_offset);

            mWeatherIconWidth = resources.getDimension(R.dimen.digital_weather_icon_width);
            mWeatherIconHeight = resources.getDimension(R.dimen.digital_weather_icon_heigh);
            mWeatherIconYOffset= resources.getDimension(R.dimen.digital_weather_icon_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTemperaturePaint = new Paint();
            mTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_temp));


            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mGoogleApiClient.disconnect();
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchfaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchfaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchfaceService.this.getResources();
            boolean isRound = insets.isRound();
            mIsround = isRound;
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            float temperatureTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temperature_text_size_round : R.dimen.digital_temperature_text_size);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mTemperaturePaint.setTextSize(temperatureTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchfaceService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {


            float watchWidth = bounds.width();


            Resources resources = SunshineWatchfaceService.this.getResources();

            // Draw the background and set offsets
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mTemperaturePaint.setColor(resources.getColor(R.color.digital_text));
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                mTemperaturePaint.setColor(resources.getColor(R.color.digital_temp));
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();

            String time = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute,mTime.second);
            float timeTextwidth = mTimePaint.measureText(time);
            canvas.drawText(time, (watchWidth-timeTextwidth)/2, mTimeYOffset, mTimePaint);

            // Draw Date
            String date = mAmbient
                    ? mFormatedDateAmbient.format(mTime.toMillis(true))
                    : mFormatedDateNormal.format(mTime.toMillis(true));
            float dateTextWidth = mDatePaint.measureText(date);
            canvas.drawText(date, (watchWidth-dateTextWidth)/2 , mDateYOffset, mDatePaint);

            if (!isInAmbientMode()){
                //Draw wheather Icon only if not in ambient mode
                Drawable d = ResourcesCompat.getDrawable(getResources(),getArtResourceForWeatherCondition(mWeatherId),null);
                int watchWidthInt = (int) watchWidth;
                int l = (watchWidthInt / 4) - ((int)mWeatherIconWidth/2);
                int t = (int)mWeatherIconYOffset;
                int r = l + (int)mWeatherIconWidth;
                int b = (int)mWeatherIconYOffset + (int)mWeatherIconHeight;
                if (mIsround){
                    int increment = resources.getDimensionPixelOffset(R.dimen.digital_weather_icon_x_offset_Increment);
                    l = l + increment;
                    r = r + increment;
                }
                d.setBounds(l,t,r,b);
                d.draw(canvas);
            }


            //Draw Temperature
            String temperature = mMaxTemp + "  " + mMinTemp;
            float temperatureTextWidth = mTemperaturePaint.measureText(temperature);
            if (isInAmbientMode()){
                canvas.drawText(temperature, (watchWidth - temperatureTextWidth )/2 , mTemperatureYOffset, mTemperaturePaint);
            }else{
                canvas.drawText(temperature, (watchWidth/2)+10 , mTemperatureYOffset, mTemperaturePaint);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /**
         * Handle updating Temperature information.
         */
        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            for (DataEvent dataEvent : dataEventBuffer){
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path =dataEvent.getDataItem().getUri().getPath();
                    if (path.equals("/today-weather-data")){
                        mWeatherId = dataMap.getInt("weatherId");
                        mMaxTemp = dataMap.getString("high","");
                        mMinTemp = dataMap.getString("low","");
                        Log.d("SunshineWatchfaceSvc", "onDataChanged(): weatherId: " + mWeatherId + " high: " + mMaxTemp + "low: " + mMinTemp);
                        invalidate();
                    }
                }
            }
        }
    }
}
