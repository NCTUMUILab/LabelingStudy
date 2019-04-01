package labelingStudy.nctu.minuku_2.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.NearbyPlaces.GetUrl;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.Utilities.Utils;
import labelingStudy.nctu.minuku.config.ActionLogVar;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.MinukuStreamManager;
import labelingStudy.nctu.minuku_2.R;

public class PlaceSelection extends FragmentActivity implements OnMapReadyCallback {

    private final String TAG = "PlaceSelection";

    private SharedPreferences sharedPrefs;

    private ArrayList<String> MarkerName = new ArrayList<String>();
    private ArrayList<String> MarkerLat = new ArrayList<String>();
    private ArrayList<String> MarkerLng = new ArrayList<String>();

    private Button AddPlace;
    private String siteNetJson = "";

    private static double lat = 0;
    private static double lng = 0;
    public static String markerTitle = "";
    public static LatLng markerLocation;

    private String yourPlace;

    private Marker customizedMarker, currentLocationMarker;
    private ArrayList<Marker> customizedMarkers = new ArrayList<>();
    private Bundle bundle;

    public static boolean fromTimeLineFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_selection);

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, Context.MODE_PRIVATE);

        yourPlace = getResources().getString(R.string.placeselection_yourplace);

        bundle = getIntent().getExtras();
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPrefs.edit().putString("lastActivity", getClass().getName()).apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {

            PlaceSelection.this.finish();

            if(isTaskRoot()){

                startActivity(new Intent(this, WelcomeActivity.class));
            }

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG,"onResume");

        try {

            fromTimeLineFlag = bundle.getBoolean("fromTimeLineFlag", false);

            if(fromTimeLineFlag){

                lat = bundle.getDouble("lat", lat);
                lng = bundle.getDouble("lng", lng);
            }

        }catch (NullPointerException e){

            fromTimeLineFlag = false;
        }

        initPlaceSelection();
    }

    private void initPlaceSelection(){

        AddPlace = (Button)findViewById(R.id.btn_addplace);

        AddPlace.setOnClickListener(onClick);

        ((MapFragment) getFragmentManager().findFragmentById(R.id.Mapfragment)).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap map) {

                //prepare the customizedSite from the DB for the marker
                ArrayList<String> customizedSite = DBHelper.queryCustomizedSites();

                Log.d(TAG, "current lat : "+lat+", current lng : "+lng);

                if(customizedSite.size() != 0){

                    //check the distance between the session's first location and the customizedSite
                    for(int index = 0; index < customizedSite.size(); index++){

                        String eachData = customizedSite.get(index);

                        String[] dataPieces = eachData.split(Constants.DELIMITER);

//                        Log.d(TAG, "sitename : "+dataPieces[1]+", siteLat : "+dataPieces[2]+", siteLng : "+dataPieces[3]);

                        double siteLat = Double.parseDouble(dataPieces[2]);
                        double siteLng = Double.parseDouble(dataPieces[3]);

                        float[] results = new float[1];
                        Location.distanceBetween(lat, lng, siteLat, siteLng, results);
                        float distance = results[0];

//                        Log.d(TAG, "sitename : "+dataPieces[1]+", siteLat : "+siteLat+", siteLng : "+siteLng);

                        if(distance <= Constants.siteRange){

                            LatLng latLng = new LatLng(siteLat, siteLng);

                            Marker marker = map.addMarker(new MarkerOptions().position(latLng).title(dataPieces[1]));

                            customizedMarkers.add(marker);
                        }
                    }
                }

                //show the customize site in different color
                for(int index = 0; index < customizedMarkers.size(); index++){

                    LatLng latlng = customizedMarkers.get(index).getPosition();

                    customizedMarker = map.addMarker(new MarkerOptions().position(latlng).title(customizedMarkers.get(index).getTitle()));
                    customizedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                }


                map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

                    @Override
                    public void onMapClick(LatLng latLng) {

                        DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_MAP+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_MAP+" - "+TAG);

                        AddPlace.setText(getResources().getString(R.string.placeselection_newsite));

                        if (customizedMarker != null) {
                            customizedMarker.remove();
                        }

                        customizedMarker = map.addMarker(new MarkerOptions()
                                .position(new LatLng(latLng.latitude, latLng.longitude))
                                .draggable(true).visible(true));

                        Log.d(TAG, "site lat : "+latLng.latitude+", site lng : "+latLng.longitude);

                        customizedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

                        customizedMarkers.add(customizedMarker);

                        triggerAlertDialog(customizedMarker);
                    }
                });

                map.setOnMarkerClickListener(onMarkerClicked);

                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                try{

                    lat = bundle.getDouble("lat", MinukuStreamManager.getInstance().getLocationDataRecord().getLatitude());
                    lng = bundle.getDouble("lng", MinukuStreamManager.getInstance().getLocationDataRecord().getLongitude());
                } catch (NullPointerException e){

                    //if there are no availSite corresponding to the session; get the current one.
                    lat = MinukuStreamManager.getInstance().getLocationDataRecord().getLatitude();
                    lng = MinukuStreamManager.getInstance().getLocationDataRecord().getLongitude();
                } catch(Exception e) {

                    Log.e(TAG, "exception", e);
                }

                final double finalLat = lat;
                final double finalLng = lng;
                final CountDownLatch latch = new CountDownLatch(1);

                Thread thread = new Thread() {

                    public void run() {

                        String name = "";
                        String latitude = "";
                        String longitude = "";

                        siteNetJson = Utils.getJSON(GetUrl.getUrl(finalLat, finalLng));

                        JSONObject jsonObject = null;

                        try {

                            jsonObject = new JSONObject(siteNetJson);
                            JSONArray results = jsonObject.getJSONArray("results");
                            for (int i = 0; i < results.length(); i++) {

                                JSONObject result = results.getJSONObject(i);

                                String placeTypes = result.getString("types");
                                Log.d(TAG, "placeTypes : "+placeTypes);
                                Log.d(TAG, "placeTypes is political ? "+(placeTypes.contains("political")));
                                Log.d(TAG, "place name : "+result.getString("name"));
                                if(placeTypes.contains("political")){

                                    continue;
                                }

                                name = result.getString("name");
                                MarkerName.add(name);
                                latitude = result.getJSONObject("geometry").getJSONObject("location").getString("lat");
                                MarkerLat.add(latitude);
                                longitude = result.getJSONObject("geometry").getJSONObject("location").getString("lng");
                                MarkerLng.add(longitude);
                                Log.d(TAG, "name: " + name + "latitude: " + latitude + "longitude: " + longitude);
                            }

                            latch.countDown();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                };
                thread.start();


                try {

                    latch.await();

                    for(int index = 0; index < MarkerLat.size(); index++){

                        LatLng latlng = new LatLng(Double.parseDouble(MarkerLat.get(index)), Double.parseDouble(MarkerLng.get(index)));

                        Marker marker = map.addMarker(new MarkerOptions().position(latlng).title(MarkerName.get(index)));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                LatLng currentLatLng = new LatLng(lat, lng);

                currentLocationMarker = map.addMarker(new MarkerOptions().position(currentLatLng).title(yourPlace));

                currentLocationMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));


                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 20));
            }
        });

    }

    private GoogleMap.OnMarkerClickListener onMarkerClicked = new GoogleMap.OnMarkerClickListener() {

        @Override
        public boolean onMarkerClick(final Marker marker) {

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_MARKER+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_MARKER+" - "+TAG);

            if(marker.equals(currentLocationMarker)) {

                triggerAlertDialog(marker);
            }else {

                Log.d(TAG, "marker is not the customized one");

                try {

                    markerTitle = marker.getTitle().toString();
                    markerLocation = marker.getPosition();
                }catch (NullPointerException e){

                    triggerAlertDialog(marker);
                }

                AddPlace.setText(getResources().getString(R.string.confirm_in_chinese));
            }

            return false;
        }
    };

    private void triggerAlertDialog(final Marker marker){

        final LayoutInflater inflater = LayoutInflater.from(PlaceSelection.this);
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlaceSelection.this);
        final View layout = inflater.inflate(R.layout.sitemarker_dialog,null);

        builder.setView(layout)
                .setPositiveButton(R.string.ok, null);

        final AlertDialog mAlertDialog = builder.create();
        mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(final DialogInterface dialogInterface) {

                Button button = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        EditText sitenameInEditText = (EditText) layout.findViewById(R.id.sitename_edittext);

                        sitenameInEditText.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_EDITTEXT+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_SITENAME_EDITTEXT+" - "+TAG);
                            }
                        });

                        sitenameInEditText.addTextChangedListener(new TextWatcher() {

                            @Override
                            public void afterTextChanged(Editable s) {}

                            @Override
                            public void beforeTextChanged(CharSequence s, int start,
                                                          int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start,
                                                      int before, int count) {

                                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_EDITTEXT+" - "+ActionLogVar.ACTION_TEXT_CHANGED+" - "+ActionLogVar.MEANING_SITENAME_EDITTEXT+" - "+TAG);
                            }
                        });

                        String sitename = sitenameInEditText.getText().toString();

                        //if sitename is null or empty, don't insert it, to make sure that the input is not null
                        sitename = sitename.trim();
                        if(sitename.equals("")){

                            Toast.makeText(PlaceSelection.this, getResources().getString(R.string.reminder_enter_site), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        marker.setTitle(sitename);

                        markerTitle = marker.getTitle();
                        markerLocation = marker.getPosition();

                        String markerPlaceId = GetUrl.getPlaceId(markerLocation);

                        Log.d(TAG, "markerTitle : "+markerTitle+", markerLocation : "+markerLocation+", markerPlaceId : "+markerPlaceId);

                        DBHelper.insertCustomizedSiteTable(markerTitle, markerLocation, markerPlaceId);

                        addToConvenientSiteTable();

                        Toast.makeText(PlaceSelection.this, getResources().getString(R.string.reminder_enter_site_successfully), Toast.LENGTH_SHORT).show();
                        dialogInterface.dismiss();

                        //After enter the name, jump to the previous page directly.
                        PlaceSelection.this.finish();
                    }
                });
            }
        });

        mAlertDialog.show();
    }

    private Button.OnClickListener onClick = new Button.OnClickListener() {
        @Override
        public void onClick(View view) {

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_SITENAME_ADDPLACE+" - "+TAG);

            final View v = LayoutInflater.from(PlaceSelection.this).inflate(R.layout.addplace, null);

            if (AddPlace.getText().equals(getResources().getString(R.string.placeselection_newsite))) {

                triggerAlertDialog(currentLocationMarker);

            }else if (AddPlace.getText().equals(getResources().getString(R.string.confirm_in_chinese))) {

                addToConvenientSiteTable();
            }
        }
    };

    private void addToConvenientSiteTable(){

        String sitename = markerTitle;

        DBHelper.insertConvenientSiteTable(sitename, markerLocation);

        Log.d(TAG, "[test add site] fromTimeLineFlag : "+fromTimeLineFlag);

        //addToConvenientSiteTable the functionality of this
        if(!fromTimeLineFlag) {

            //Timer_site is initialized or alive or not.
            Timer_site.availSite.add(sitename);

            Log.d(TAG, " availSite : "+ Timer_site.availSite);
            Log.d(TAG, " dataSize : "+ Timer_site.availSite.size());
        }else{

            Timeline.selectedSiteName = sitename;
            Timeline.selectedSiteLoc = "("+markerLocation.latitude+","+markerLocation.longitude+")";
            Timeline.dChoosingSite.setText(Timeline.selectedSiteName);
        }

        PlaceSelection.this.finish();
    }

    @Override
    public void onMapReady(GoogleMap map) {

        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}