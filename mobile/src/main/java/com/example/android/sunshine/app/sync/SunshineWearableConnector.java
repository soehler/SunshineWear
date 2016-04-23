

package com.example.android.sunshine.app.sync;

/*
 * Created by soehler on 21/04/16 16:32.
 */


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class SunshineWearableConnector implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private GoogleApiClient mGoogleApiClient;


    public SunshineWearableConnector(Context context){

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();

    }

public void notifyWearable(int weatherId, String high, String low){

    PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/today-weather-data");
    putDataMapRequest.getDataMap().putInt("weatherId",weatherId);
    putDataMapRequest.getDataMap().putString("high",high);
    putDataMapRequest.getDataMap().putString("low",low);

    PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
    Wearable.DataApi.putDataItem(mGoogleApiClient,putDataRequest)
            .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (!dataItemResult.getStatus().isSuccess()){
                        Log.e("notifyWearable", "Error sending weather data");
                    }else{
                        Log.d("notifyWearable", "Success sending weather data");
                    }
                }
            });



    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
