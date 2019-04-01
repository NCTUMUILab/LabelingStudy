package labelingStudy.nctu.minuku_2.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.javatuples.Decade;
import org.javatuples.Ennead;
import org.javatuples.Octet;
import org.javatuples.Quintet;
import org.javatuples.Sextet;
import org.javatuples.Triplet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.DBManager;
import labelingStudy.nctu.minuku.manager.SessionManager;
import labelingStudy.nctu.minuku.model.Annotation;
import labelingStudy.nctu.minuku.model.AnnotationSet;
import labelingStudy.nctu.minuku.model.Session;
import labelingStudy.nctu.minuku_2.Utils;

/**
 * Created by Lawrence on 2019/3/3.
 */

public class QueryDataManager {

    private final String TAG = "QueryDataManager";


    public long startTime = Constants.INVALID_TIME_VALUE;
    public long endTime = Constants.INVALID_TIME_VALUE;

    public static final String DUMP_COMPARED_FIELD = "endTime";
    public static final String TRIP_COMPARED_FIELD = "createdTime";

    public QueryDataManager(){}

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void preparedCurrentTimeBoundary(SharedPreferences sharedPrefs, Context context){

        startTime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);
        endTime = getDataStartTime(sharedPrefs, context);

        if(startTime != Constants.INVALID_TIME_VALUE){

            endTime = startTime + Constants.MILLISECONDS_PER_HOUR;
        }else{

            startTime = endTime - Constants.MILLISECONDS_PER_HOUR;
        }
    }

    private long getDataStartTime(SharedPreferences sharedPrefs, Context context){

        long startTime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);

        if(startTime == Constants.INVALID_TIME_VALUE) {

            //change to the time that the app being downloaded
            long downloadedTime = Utils.getDownloadedTime(context);

            startTime = ScheduleAndSampleManager.getHourLowerTimeInMillis(downloadedTime);
        }

        Log.d(TAG, "getDataStartTime startTime : "+ScheduleAndSampleManager.getTimeString(startTime));

        return startTime;
    }

    public JSONObject queryDumpData() throws JSONException {

        Log.d(TAG, "queryingDumpData");

        JSONObject dataInJson = new JSONObject();

        queryDumpInfo(dataInJson);

        queryTransporatation(dataInJson);
        queryLocation(dataInJson);
        queryActivityRecognition(dataInJson);
        queryRinger(dataInJson);
        queryConnectivity(dataInJson);
        queryBattery(dataInJson);
        queryAppUsage(dataInJson);
        queryTelephony(dataInJson);
        querySensor(dataInJson);
        queryAccessibility(dataInJson);
        queryActionLog(dataInJson);

//        Log.d(TAG,"final dump data : "+ dataInJson.toString());

        return dataInJson;
    }

    public ArrayList<JSONObject> queryTripData(long time24HrAgo) {

        Log.d(TAG, "queryTripData");

        ArrayList<JSONObject> datas = getSessionData(time24HrAgo);

        Log.d(TAG, "tripData size : "+datas.size());

        return datas;
    }

    private void queryDumpInfo(JSONObject data) throws JSONException {

//        data.put("_id", Constants.DEVICE_ID+"_"+endTime);

        data.put("device_id", Constants.DEVICE_ID);
        data.put("condition", Constants.CURRENT_TASK);
        data.put("startTime", String.valueOf(startTime));
        data.put("endTime", String.valueOf(endTime));
        data.put("startTimeString", ScheduleAndSampleManager.getTimeString(startTime));
        data.put("endTimeString", ScheduleAndSampleManager.getTimeString(endTime));
    }

    private ArrayList<JSONObject> getSessionData(long time24HrAgo){

        Log.d(TAG, "getSessionData");

        ArrayList<JSONObject> sessionJsons = new ArrayList<>();

        ArrayList<String> overTimeSessions = DBHelper.querySessions(time24HrAgo);

        Log.d(TAG, "unsentSessions size : "+ overTimeSessions.size());

        for(int index = 0; index < overTimeSessions.size(); index++){

            try {

                JSONObject sessionJson = new JSONObject();

                String eachData = overTimeSessions.get(index);

                Session sessionToSend = SessionManager.convertStringToSession(eachData);

                //we can't call "_id" because of MongoDB, it will have its own ?

                sessionJson.put("_id", Constants.DEVICE_ID+"_"+sessionToSend.getCreatedTime());
                sessionJson.put("device_id", Constants.DEVICE_ID);
                sessionJson.put("condition", Constants.CURRENT_TASK);
                sessionJson.put("createdTime", String.valueOf(sessionToSend.getCreatedTime()));
                sessionJson.put("startTime", String.valueOf(sessionToSend.getStartTime()));
                sessionJson.put("endTime", String.valueOf(sessionToSend.getEndTime()));
                sessionJson.put("startTimeString", ScheduleAndSampleManager.getTimeString(sessionToSend.getStartTime()));
                sessionJson.put("endTimeString", ScheduleAndSampleManager.getTimeString(sessionToSend.getEndTime()));
                sessionJson.put("sessionid", sessionToSend.getId());
                sessionJson.put("detected_Type", sessionToSend.getType());
                sessionJson.put("annotations", getAnnotationSetIntoJson(sessionToSend.getAnnotationsSet()));

                sessionJsons.add(sessionJson);
            }catch (JSONException e){

                Log.e(TAG, "exception", e);
            }
        }

        return sessionJsons;
    }

    private JSONObject getAnnotationSetIntoJson(AnnotationSet annotationSet) throws JSONException {


        JSONObject annotationSetJson = new JSONObject();

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY, getLatestAnnotationByTag(annotationSet, Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY));

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITENAME, getLatestAnnotationByTag(annotationSet, Constants.ANNOTATION_TAG_DETECTED_SITENAME));

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITELOCATION, getLatestAnnotationByTag(annotationSet, Constants.ANNOTATION_TAG_DETECTED_SITELOCATION));

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITE_PLACEID, getLatestAnnotationByTag(annotationSet, Constants.ANNOTATION_TAG_DETECTED_SITE_PLACEID));

        //TODO deleted after checked
//        ArrayList<Annotation> labels = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_Label);
//        JSONArray labelsInJSONArray = getLabelsAnnotation(labels);

        annotationSetJson.put(Constants.ANNOTATION_TAG_Label, getLabelsAnnotationByTag(annotationSet, Constants.ANNOTATION_TAG_Label));

//        Log.d(TAG, "labels in json : "+annotationSetJson);


        return annotationSetJson;
    }

    private String getLatestAnnotationByTag(AnnotationSet annotationSet, String tag) {

        ArrayList<Annotation> allAnnotations = annotationSet.getAnnotationByTag(tag);

        String latestAnnotation = getLatestAnnotation(allAnnotations);

        return latestAnnotation;
    }

    private JSONArray getLabelsAnnotationByTag(AnnotationSet annotationSet, String tag) throws JSONException {

        ArrayList<Annotation> allAnnotations = annotationSet.getAnnotationByTag(tag);

        JSONArray latestAnnotation = getLabelsAnnotation(allAnnotations);

        return latestAnnotation;
    }

    private String getLatestAnnotation(ArrayList<Annotation> annotationArrayList){

        if(annotationArrayList.size() == 0)
            return "";

        return annotationArrayList.get(annotationArrayList.size()-1).getContent();
    }

    private JSONArray getLabelsAnnotation(ArrayList<Annotation> annotationArrayList) throws JSONException{

        if(annotationArrayList.size() == 0)
            return new JSONArray();

        JSONArray labels = new JSONArray();

        for(Annotation annotation : annotationArrayList) {

            labels.put(new JSONObject(annotation.getContent()));
        }

        Log.d(TAG, "labels : "+labels);

        return labels;
    }

    private void queryTransporatation(JSONObject data) throws JSONException {

        try {

            JSONArray transportationAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+ DBHelper.transportationMode_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();
            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String confirmedTransportation = cursor.getString(2);
                    String suspectedTransportationTime = cursor.getString(3);
                    String suspectedStartTransportation = cursor.getString(4);
                    String suspectedStopTransportation = cursor.getString(5);
                    String sessionid = cursor.getString(6);

                    //Log.d(TAG,"transportation : "+transportation+" timestamp : "+timestamp);

                    //<timestamps, confirmedTransportation, suspectedTransportationTime, suspectedStartTransportation, suspectedStartTransportation, suspectedStopTransportation, sessionid>
                    Sextet<String, String, String, String, String, String> transportationTuple = new Sextet<>(timestamp, confirmedTransportation, suspectedTransportationTime, suspectedStartTransportation, suspectedStopTransportation, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(transportationTuple);

                    transportationAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("TransportationMode", transportationAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryLocation(JSONObject data) throws JSONException {

        try {

            JSONArray locationAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.location_table +" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
            //Log.d(TAG,"SELECT * FROM "+DBHelper.STREAM_TYPE_LOCATION +" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String latitude = cursor.getString(2);
                    String longtitude = cursor.getString(3);
                    String accuracy = cursor.getString(4);
                    String altitude = cursor.getString(5);
                    String speed = cursor.getString(6);
                    String bearing = cursor.getString(7);
                    String provider = cursor.getString(8);
                    String sessionid = cursor.getString(9);

                    //Log.d(TAG,"timestamp : "+timestamp+" latitude : "+latitude+" longtitude : "+longtitude+" accuracy : "+accuracy);

                    //convert into second
//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, latitude, longitude, accuracy, altitude, speed, bearing, provider, sessionid>
                    Ennead<String, String, String, String, String, String, String, String, String> locationTuple
                            = new Ennead<>(timestamp, latitude, longtitude, accuracy, altitude, speed, bearing, provider, "("+sessionid+")");

                    String dataInPythonTuple = Utils.toPythonTuple(locationTuple);

                    locationAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Location",locationAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryActivityRecognition(JSONObject data) throws JSONException {

        try {

            JSONArray arAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.activityRecognition_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String mostProbableActivity = cursor.getString(2);
                    String probableActivities = cursor.getString(3);
                    String detectedTime = cursor.getString(4);
                    String sessionid = cursor.getString(5);

                    //split the mostProbableActivity into "type:conf"
                    String[] subMostActivity = mostProbableActivity.split(",");

                    String type = subMostActivity[0].split("=")[1];
                    String confidence = subMostActivity[1].split("=")[1].replaceAll("]","");

                    mostProbableActivity = type+":"+confidence;

                    //choose the top two of the probableActivities and split it into "type:conf"
                    String[] subprobableActivities = probableActivities.split("\\,");
//                    //Log.d(TAG, "subprobableActivities : "+ subprobableActivities);

                    int lastIndex = 0;
                    int count = 0;

                    while(lastIndex != -1){

                        lastIndex = probableActivities.indexOf("DetectedActivity",lastIndex);

                        if(lastIndex != -1){
                            count ++;
                            lastIndex += "DetectedActivity".length();
                        }
                    }

                    if(count == 1){
                        String type1 = subprobableActivities[0].split("=")[1];
                        String confidence1 = subprobableActivities[1].split("=")[1].replaceAll("]","");

                        probableActivities = type1+":"+confidence1;

                    }else if(count > 1){
                        String type1 = subprobableActivities[0].split("=")[1];
                        String confidence1 = subprobableActivities[1].split("=")[1].replaceAll("]","");
                        String type2 = subprobableActivities[2].split("=")[1];
                        String confidence2 = subprobableActivities[3].split("=")[1].replaceAll("]","");

                        probableActivities = type1+":"+confidence1+Constants.DELIMITER+type2+":"+confidence2;

                    }

                    //Log.d(TAG,"timestamp : "+timestamp+", mostProbableActivity : "+mostProbableActivity+", probableActivities : "+probableActivities);

                    //<timestamps, MostProbableActivity, ProbableActivities, detectedTime, sessionid>
                    Quintet<String, String, String, String, String> arTuple =
                            new Quintet<>(timestamp, mostProbableActivity, probableActivities, detectedTime, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(arTuple);

                    arAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("ActivityRecognition", arAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryRinger(JSONObject data) throws JSONException {

        try {

            JSONArray ringerAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.ringer_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String ringerMode = cursor.getString(2);
                    String audioMode = cursor.getString(3);
                    String streamVolumeMusic = cursor.getString(4);
                    String streamVolumeNotification = cursor.getString(5);
                    String streamVolumeRing = cursor.getString(6);
                    String streamVolumeVoicecall = cursor.getString(7);
                    String streamVolumeSystem = cursor.getString(8);
                    String sessionid = cursor.getString(9);

                    //<timestampInSec, streamVolumeSystem, streamVolumeVoicecall, streamVolumeRing,
                    // streamVolumeNotification, streamVolumeMusic, audioMode, ringerMode, sessionid>
                    Ennead<String, String, String, String, String, String, String, String, String> ringerTuple
                            = new Ennead<>(timestamp, streamVolumeSystem, streamVolumeVoicecall, streamVolumeRing,
                            streamVolumeNotification, streamVolumeMusic, audioMode, ringerMode, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(ringerTuple);

                    ringerAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Ringer",ringerAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryConnectivity(JSONObject data) throws JSONException {

        try {

            JSONArray connectivityAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.connectivity_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String NetworkType = cursor.getString(2);
                    String IsNetworkAvailable = cursor.getString(3);
                    String IsConnected = cursor.getString(4);
                    String IsWifiAvailable = cursor.getString(5);
                    String IsMobileAvailable = cursor.getString(6);
                    String IsWifiConnected = cursor.getString(7);
                    String IsMobileConnected = cursor.getString(8);
                    String sessionid = cursor.getString(9);

                    //<timestampInSec, IsMobileConnected, IsWifiConnected, IsMobileAvailable,
                    // IsWifiAvailable, IsConnected, IsNetworkAvailable, NetworkType>
                    Ennead<String, String, String, String, String, String, String, String, String> connectivityTuple
                            = new Ennead<>(timestamp, IsMobileConnected, IsWifiConnected, IsMobileAvailable,
                            IsWifiAvailable, IsConnected, IsNetworkAvailable, NetworkType, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(connectivityTuple);

                    connectivityAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Connectivity",connectivityAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryBattery(JSONObject data) throws JSONException {

        try {

            JSONArray batteryAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.battery_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String BatteryLevel = cursor.getString(2);
                    String BatteryPercentage = cursor.getString(3);
                    String BatteryChargingState = cursor.getString(4);
                    String isCharging = cursor.getString(5);
                    String sessionid = cursor.getString(6);

                    //Log.d(TAG,"timestamp : "+timestamp+" BatteryLevel : "+BatteryLevel+" BatteryPercentage : "+
//                            BatteryPercentage+" BatteryChargingState : "+BatteryChargingState+" isCharging : "+isCharging);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamps, isCharging, BatteryChargingState, BatteryPercentage, BatteryLevel, sessionid>
                    Sextet<String, String, String, String, String, String> batteryTuple
                            = new Sextet<>(timestamp, isCharging, BatteryChargingState, BatteryPercentage, BatteryLevel, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(batteryTuple);

                    batteryAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Battery", batteryAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryAppUsage(JSONObject data) throws JSONException {

        try {

            JSONArray appUsageAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.appUsage_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();

                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String ScreenStatus = cursor.getString(2);
                    String Latest_Used_App = cursor.getString(3);
                    String Latest_Foreground_Activity = cursor.getString(4);
                    String sessionid = cursor.getString(5);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, ScreenStatus, Latest_Used_App, Latest_Foreground_Activity>
                    Quintet<String, String, String, String, String> appUsageTuple
                            = new Quintet<>(timestamp, ScreenStatus, Latest_Used_App, Latest_Foreground_Activity, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(appUsageTuple);

                    appUsageAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("AppUsage",appUsageAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryTelephony(JSONObject data) throws JSONException {

        try {

            JSONArray telephonyAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.telephony_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();

                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String networkOperatorName = cursor.getString(2);
                    String callState = cursor.getString(3);
                    String phoneSignalType = cursor.getString(4);
                    String gsmSignalStrength = cursor.getString(5);
                    String LTESignalStrength = cursor.getString(6);
                    String CdmaSignalStrengthLevel = cursor.getString(7);
                    String sessionid = cursor.getString(8);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, networkOperatorName, CallState, PhoneSignalType_col, gsmSignalStrength, LTESignalStrength, CdmaSignalStrengthLevel, sessionid>
                    Octet<String, String, String, String, String, String, String, String> telephonyTuple
                            = new Octet<>(timestamp, networkOperatorName, callState, phoneSignalType, gsmSignalStrength, LTESignalStrength, CdmaSignalStrengthLevel, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(telephonyTuple);

                    telephonyAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Telephony",telephonyAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void querySensor(JSONObject data) throws JSONException {

        try {

            JSONArray sensorAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null);
            Log.d(TAG, "rawQuery : " + "SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = cursor.getCount();

            Log.d(TAG, "Sensor rows : " + rows);

            if(rows!=0){

                cursor.moveToFirst();

                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String accelerometer = cursor.getString(2);
                    String gyroscope = cursor.getString(3);
                    String gravity = cursor.getString(4);
                    String linear_acceleration = cursor.getString(5);
                    String rotation_vector = cursor.getString(6);
                    String proximity = cursor.getString(7);
                    String magnetic_field = cursor.getString(8);
                    String light = cursor.getString(9);
                    String pressure = cursor.getString(10);

                    //<timestamp, accelerometer, gyroscope, gravity, linear_acceleration, ROTATION_VECTOR, PROXIMITY, MAGNETIC_FIELD, LIGHT, PRESSURE>
                    Decade<String, String, String, String, String, String, String, String, String, String> sensorTuple1
                            = new Decade<>(timestamp, accelerometer, gyroscope, gravity, linear_acceleration, rotation_vector, proximity, magnetic_field, light, pressure);

                    String relative_humidity = cursor.getString(11);
                    String ambient_temperature = cursor.getString(12);
                    String sessionid = cursor.getString(13);

                    //<RELATIVE_HUMIDITY, AMBIENT_TEMPERATURE, sessionid>
                    Triplet<String, String, String> sensorTuple2 = new Triplet<>(relative_humidity, ambient_temperature, sessionid);

                    String dataInPythonTuple = Utils.tupleConcat(sensorTuple1, sensorTuple2);

                    sensorAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Sensor", sensorAndtimestampsJson);
            }
        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }
    }

    private void queryAccessibility(JSONObject data) throws JSONException {

        try {

            JSONArray accessibilityAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.accessibility_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null);
            Log.d(TAG, "rawQuery : " + "SELECT * FROM "+DBHelper.accessibility_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();

                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String pack = cursor.getString(2);
                    String text = cursor.getString(3);
                    String type = cursor.getString(4);
                    String extra = cursor.getString(5);
                    String sessionid = cursor.getString(6);

                    //<timestamp, pack, text, type, extra>
                    Sextet<String, String, String, String, String, String> accessibilityTuple
                            = new Sextet<>(timestamp, pack, text, type, extra, sessionid);

                    String dataInPythonTuple = Utils.toPythonTuple(accessibilityTuple);

                    accessibilityAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Accessibility", accessibilityAndtimestampsJson);
            }
        }catch(NullPointerException e){
        }
    }

    private void queryActionLog(JSONObject data) throws JSONException {

        try {

            JSONArray actionLogAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.actionLog_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();
            if(rows!=0){
                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = cursor.getString(1);
                    String action = cursor.getString(2);
                    String userpresent = cursor.getString(3);

                    //convert into second
                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamps, action>
                    Triplet<String, String, String> actionLogTuple = new Triplet<>(timestampInSec, action, userpresent);

                    String dataInPythonTuple = Utils.toPythonTuple(actionLogTuple);

                    actionLogAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("ActionLog", actionLogAndtimestampsJson);

            }
        }catch (JSONException e){

        }catch (NullPointerException e){

        }
    }
}
