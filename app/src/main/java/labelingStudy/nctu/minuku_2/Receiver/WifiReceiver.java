package labelingStudy.nctu.minuku_2.Receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.javatuples.Decade;
import org.javatuples.Octet;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Quintet;
import org.javatuples.Septet;
import org.javatuples.Triplet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Data.DataHandler;
import labelingStudy.nctu.minuku.Utilities.CSVHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.DBManager;
import labelingStudy.nctu.minuku.manager.SessionManager;
import labelingStudy.nctu.minuku.model.Annotation;
import labelingStudy.nctu.minuku.model.AnnotationSet;
import labelingStudy.nctu.minuku.model.Session;
import labelingStudy.nctu.minuku_2.Utils;

/**
 * Created by Lawrence on 2017/8/16.
 */

public class WifiReceiver extends BroadcastReceiver {

    private final String TAG = "WifiReceiver";

    private SharedPreferences sharedPrefs;

    private int year,month,day,hour,min;

    private long latestUpdatedTime = -9999;
    private long nowTime = -9999;
    private long startTime = -9999;
    private long endTime = -9999;

    private String currentCondition;

    public static final int HTTP_TIMEOUT = 10000; // millisecond
    public static final int SOCKET_TIMEOUT = 10000; // millisecond

    private static final String postDumpUrl_insert = "http://18.219.118.106:5000/find_latest_and_insert?collection=dump&action=insert&id=";//&action=insert, search
    private static final String postDumpUrl_search = "http://18.219.118.106:5000/find_latest_and_insert?collection=dump&action=search&id=";//&action=insert, search

    private static final String postTripUrl_insert = "http://18.219.118.106:5000/find_latest_and_insert?collection=trip&action=insert&id=";//&action=insert, search
    private static final String postTripUrl_search = "http://18.219.118.106:5000/find_latest_and_insert?collection=trip&action=search&id=";//&action=insert, search

    private static final String postIsAliveUrl_insert = "http://18.219.118.106:5000/find_latest_and_insert?collection=isAlive&action=insert&id=";//&action=insert, search

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onReceive");

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        //get timzone //prevent the issue when the user start the app in wifi available environment.
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tz);
        int mYear = cal.get(Calendar.YEAR);
        int mMonth = cal.get(Calendar.MONTH);
        int mDay = cal.get(Calendar.DAY_OF_MONTH);
        int mHour = cal.get(Calendar.HOUR_OF_DAY);

        sharedPrefs = context.getSharedPreferences(Constants.sharedPrefString, context.MODE_PRIVATE);

        year = sharedPrefs.getInt("StartYear", mYear);
        month = sharedPrefs.getInt("StartMonth", mMonth);
        day = sharedPrefs.getInt("StartDay", mDay);

        Constants.USER_ID = sharedPrefs.getString("userid","NA");
        Constants.GROUP_NUM = sharedPrefs.getString("groupNum","NA");

        hour = sharedPrefs.getInt("StartHour", mHour);
        min = sharedPrefs.getInt("StartMin",0);

        currentCondition = context.getResources().getString(labelingStudy.nctu.minuku.R.string.current_task);

        Log.d(TAG, "year : "+ year+" month : "+ month+" day : "+ day+" hour : "+ hour+" min : "+ min);

        setDataStartEndTime();

        if (Constants.ACTION_CONNECTIVITY_CHANGE.equals(intent.getAction())) {

            if(activeNetwork != null &&
                    activeNetwork.getType() == ConnectivityManager.TYPE_WIFI){

                boolean firstTimeToLogCSV_Wifi = sharedPrefs.getBoolean(CSVHelper.CSV_Wifi, true);

                if(firstTimeToLogCSV_Wifi) {
                    CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "describeContents", "getDetailedState", "getExtraInfo",
                            "getReason", "getState", "getSubtypeName", "getTypeName", "isAvailable", "isConnected",
                            "isConnectedOrConnecting", "isFailover", "isRoaming");

                    sharedPrefs.edit().putBoolean(CSVHelper.CSV_Wifi, false).apply();
                }

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, String.valueOf(activeNetwork.describeContents()), activeNetwork.getDetailedState().toString()
                        , activeNetwork.getExtraInfo(), activeNetwork.getReason(), activeNetwork.getState().toString(), String.valueOf(activeNetwork.getSubtypeName())
                        , activeNetwork.getTypeName(), String.valueOf(activeNetwork.isAvailable()),
                        String.valueOf(activeNetwork.isConnected()), String.valueOf(activeNetwork.isConnectedOrConnecting()),
                        String.valueOf(activeNetwork.isFailover()), String.valueOf(activeNetwork.isRoaming()));

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, activeNetwork.toString());

                setDataStartEndTime();

                uploadData();
            }
        }
    }

    public void sendingDumpData(){

        Log.d(TAG, "sendingDumpData");

        JSONObject dataInJson = new JSONObject();

        try {

            dataInJson.put("device_id", Constants.DEVICE_ID);

            dataInJson.put("condition", currentCondition);
            dataInJson.put("startTime", String.valueOf(startTime));
            dataInJson.put("endTime", String.valueOf(endTime));
            dataInJson.put("startTimeString", ScheduleAndSampleManager.getTimeString(startTime));
            dataInJson.put("endTimeString", ScheduleAndSampleManager.getTimeString(endTime));
        }catch (JSONException e){

        }

        storeTransporatation(dataInJson);
        storeLocation(dataInJson);
        storeActivityRecognition(dataInJson);
        storeRinger(dataInJson);
        storeConnectivity(dataInJson);
        storeBattery(dataInJson);
        storeAppUsage(dataInJson);
        storeTelephony(dataInJson);
        storeSensor(dataInJson);
        storeAccessibility(dataInJson);

        Log.d(TAG,"final availSite : "+ dataInJson.toString());

        CSVHelper.storeToCSV("Dump.csv", dataInJson.toString());

        String curr = getDateCurrentTimeZone(new Date().getTime());

        String lastTimeInServer;

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                lastTimeInServer = new HttpAsyncPostJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        postDumpUrl_insert+ Constants.DEVICE_ID,
                    dataInJson.toString(),
                    "Dump",
                    curr).get();
            else
                lastTimeInServer = new HttpAsyncPostJsonTask().execute(
                        postDumpUrl_insert+ Constants.DEVICE_ID,
                        dataInJson.toString(),
                        "Dump",
                        curr).get();

            //if it was updated successfully, return the end time
            Log.d(TAG, "[show availSite response] Trip lastTimeInServer : " + lastTimeInServer);

            JSONObject lasttimeInServerJson = new JSONObject(lastTimeInServer);

            Log.d(TAG, "[show availSite response] check sent endTime : " + dataInJson.getString("endTime"));
            Log.d(TAG, "[show availSite response] check latest availSite in server's endTime : " + lasttimeInServerJson.getString("endTime"));
            Log.d(TAG, "[show availSite response] check condition : " + dataInJson.getString("endTime").equals(lasttimeInServerJson.getString("endTime")));

            if(dataInJson.getString("endTime").equals(lasttimeInServerJson.getString("endTime"))){

                //update next time range
                latestUpdatedTime = endTime;

                startTime = latestUpdatedTime;

                long nextinterval = Constants.MILLISECONDS_PER_HOUR;

                endTime = startTime + nextinterval;

                Log.d(TAG, "[show data response] next iteration startTime : " + startTime);
                Log.d(TAG, "[show data response] next iteration startTimeString : " + ScheduleAndSampleManager.getTimeString(startTime));

                Log.d(TAG, "[show data response] next iteration endTime : " + endTime);
                Log.d(TAG, "[show data response] next iteration endTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));

                sharedPrefs.edit().putLong("lastSentStarttime", startTime).apply();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

    private void uploadData(){

        Constants.DEVICE_ID = sharedPrefs.getString("DEVICE_ID",  Constants.DEVICE_ID);

        Log.d(TAG, "DEVICE_ID : "+ Constants.DEVICE_ID);

        if(!Constants.DEVICE_ID.equals("NA")) {

            setNowTime();

            Log.d(TAG, "NowTimeString : " + ScheduleAndSampleManager.getTimeString(nowTime));
            Log.d(TAG, "endTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));
            Log.d(TAG, "now > end ? " + (nowTime > endTime));

            //TODO might cause the infinite loop
            while(nowTime > endTime) {

                Log.d(TAG,"before send dump data NowTimeString : " + ScheduleAndSampleManager.getTimeString(nowTime));

                Log.d(TAG,"before send dump data EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));

                //TODO return the boolean value to check if the network is connected
                sendingDumpData();

                //update nowTime
                setNowTime();
            }

            // Trip, isAlive
            sendingTripData(nowTime);

            sendingIsAliveData();

        }
    }

    private void setDataStartEndTime(){

        Log.d(TAG, "setDataStartEndTime");

        long lastSentStarttime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);

        if (lastSentStarttime == Constants.INVALID_TIME_VALUE) {

            //if it doesn't response the setting with initialize ones
            //initialize
            Calendar designatedStartTime = Calendar.getInstance();
            designatedStartTime.set(year, month, day, hour, min);

            long startstartTime = designatedStartTime.getTimeInMillis();
            startTime = sharedPrefs.getLong("StartTime", startstartTime); //default
            Log.d(TAG, "StartTimeString : " + ScheduleAndSampleManager.getTimeString(startTime));

            sharedPrefs.edit().putLong("StartTime", startTime).apply();

            long startendTime = startstartTime + Constants.MILLISECONDS_PER_HOUR;
            endTime = sharedPrefs.getLong("EndTime", startendTime);
            Log.d(TAG, "EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));

            sharedPrefs.edit().putLong("EndTime", endTime).apply();

        } else {

            //if it do reponse the setting with initialize ones
            startTime = Long.valueOf(lastSentStarttime);
            Log.d(TAG, "StartTimeString : " + ScheduleAndSampleManager.getTimeString(startTime));

            endTime = Long.valueOf(lastSentStarttime) + Constants.MILLISECONDS_PER_HOUR;
            Log.d(TAG, "EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));
        }
    }

    private void setNowTime(){

//        nowTime = new Date().getTime() - Constants.MILLISECONDS_PER_DAY;

        nowTime = new Date().getTime(); //TODO for testing
    }

    private void sendingTripData(long time24HrAgo){

        Log.d(TAG, "sendingTripData");

        ArrayList<JSONObject> datas = getSessionData(time24HrAgo);

        Log.d(TAG, "tripData size : "+datas.size());

        for(int index = 0; index < datas.size(); index++){

            JSONObject data = datas.get(index);

            Log.d(TAG, "[test Trip sending] trip availSite uploading : " + data.toString());

            String curr = getDateCurrentTimeZone(new Date().getTime());

            String lastTimeInServer;

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    lastTimeInServer = new HttpAsyncPostJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                            postTripUrl_insert + Constants.DEVICE_ID,
                            data.toString(),
                            "Trip",
                            curr).get();
                else
                    lastTimeInServer = new HttpAsyncPostJsonTask().execute(
                            postTripUrl_insert + Constants.DEVICE_ID,
                            data.toString(),
                            "Trip",
                            curr).get();

                //if it was updated successfully, return the end time
                Log.d(TAG, "[show availSite response] Trip lastTimeInServer : " + lastTimeInServer);

                JSONObject lasttimeInServerJson = new JSONObject(lastTimeInServer);

                Log.d(TAG, "[show availSite response] check sent createdTime : " + data.getString("createdTime"));
                Log.d(TAG, "[show availSite response] check latest availSite in server's createdTime : " + lasttimeInServerJson.getString("createdTime"));
                Log.d(TAG, "[show availSite response] check condition : " + data.getString("createdTime").equals(lasttimeInServerJson.getString("createdTime")));

                if(data.getString("createdTime").equals(lasttimeInServerJson.getString("createdTime"))){

                    //update the sent Session to already be sent
                    String sentSessionId = data.getString("sessionid");
                    DataHandler.updateSession(Integer.valueOf(sentSessionId), Constants.SESSION_IS_ALREADY_SENT_FLAG);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (JSONException e){
                e.printStackTrace();
            }
        }
    }


    private void sendingIsAliveData(){

        //making isAlive
        JSONObject dataInJson = new JSONObject();
        try {
            long currentTime = new Date().getTime();
            String currentTimeString = getTimeString(currentTime);

            dataInJson.put("time", currentTime);
            dataInJson.put("timeString", currentTimeString);
            dataInJson.put("device_id", Constants.DEVICE_ID);
            dataInJson.put("condition", currentCondition);

        }catch (JSONException e){
            e.printStackTrace();
        }

        Log.d(TAG, "isAlive availSite uploading : " + dataInJson.toString());

        String curr = getDateCurrentTimeZone(new Date().getTime());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                new HttpAsyncPostJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        postIsAliveUrl_insert + Constants.DEVICE_ID,
                        dataInJson.toString(),
                        "isAlive",
                        curr).get();
            else
                new HttpAsyncPostJsonTask().execute(
                        postIsAliveUrl_insert + Constants.DEVICE_ID,
                        dataInJson.toString(),
                        "isAlive",
                        curr).get();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

    //use HTTPAsyncTask to poHttpAsyncPostJsonTask availSite
    private class HttpAsyncPostJsonTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            String result = null;
            String url = params[0];
            String data = params[1];
            String dataType = params[2];
            String lastSyncTime = params[3];

            result = postJSON(url, data, dataType, lastSyncTime);

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "get http post result : " + result);
        }

    }

    public HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    public String postJSON (String address, String json, String dataType, String lastSyncTime) {

        Log.d(TAG, "[postJSON] testbackend post availSite to " + address);

        InputStream inputStream = null;
        String result = "";
        HttpURLConnection conn = null;
        try {

            URL url = new URL(address);
            conn = (HttpURLConnection) url.openConnection();
            Log.d(TAG, "[postJSON] testbackend connecting to " + address);

            if (url.getProtocol().toLowerCase().equals("https")) {
                Log.d(TAG, "[postJSON] [using https]");
                trustAllHosts();
                HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                https.setHostnameVerifier(DO_NOT_VERIFY);
                conn = https;
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }

            SSLContext sc;
            sc = SSLContext.getInstance("TLS");
            sc.init(null, null, new java.security.SecureRandom());

            //TODO testing to solve the SocketTimeoutException issue
            conn.setReadTimeout(HTTP_TIMEOUT);
            conn.setConnectTimeout(SOCKET_TIMEOUT);

            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            //TODO might need to use long instead of int is for the larger size but restricted to the api level should over 19
            conn.setFixedLengthStreamingMode(json.getBytes().length);
            conn.setRequestProperty("Content-Type","application/json");
            conn.connect();

            OutputStreamWriter wr= new OutputStreamWriter(conn.getOutputStream());
            wr.write(json);
            wr.close();

            Log.d(TAG, "Post:\t" + dataType + "\t" + "for lastSyncTime:" + lastSyncTime);

            int responseCode = conn.getResponseCode();

            if(responseCode >= HttpsURLConnection.HTTP_BAD_REQUEST)
                inputStream = conn.getErrorStream();
            else
                inputStream = conn.getInputStream();

            result = convertInputStreamToString(inputStream);

            Log.d(TAG, "[postJSON] the result response code is " + responseCode);
            Log.d(TAG, "[postJSON] the result is " + result);

//            if (conn!=null)
//                conn.disconnect();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            Log.d(TAG, "NoSuchAlgorithmException", e);
        } catch (KeyManagementException e) {
            e.printStackTrace();
            Log.d(TAG, "KeyManagementException", e);
        } catch (ProtocolException e) {
            e.printStackTrace();
            Log.d(TAG, "ProtocolException", e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d(TAG, "MalformedURLException", e);
        } catch (java.net.SocketTimeoutException e){

            Log.d(TAG, "SocketTimeoutException EE", e);
            conn.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }finally {

            if (conn != null) {

                try {

                    conn.disconnect();
                } catch (Exception e) {

                    Log.d(TAG, "exception", e);
                }
            }
        }

        return result;
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException{

        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null){
//            Log.d(LOG_TAG, "[syncWithRemoteDatabase] " + line);
            result += line;
        }

        inputStream.close();
        return result;

    }

    /***
     * trust all host....
     */
    private void trustAllHosts() {

        X509TrustManager easyTrustManager = new X509TrustManager() {

            public void checkClientTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            public void checkServerTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

        };

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                sessionJson.put("condition", currentCondition);
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

    private JSONObject getAnnotationSetIntoJson(AnnotationSet annotationSet) throws JSONException{


        JSONObject annotationSetJson = new JSONObject();

        ArrayList<Annotation> detected_transportation = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY);

        String detected_transportationInString = getLatestAnnotation(detected_transportation);

        Log.d(TAG, "detected_transportationInString : "+detected_transportationInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY, detected_transportationInString);


        ArrayList<Annotation> detected_sitename = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_SITENAME);

        String detected_sitenameInString = getLatestAnnotation(detected_sitename);

        Log.d(TAG, "detected_sitenameInString : "+detected_sitenameInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITENAME, detected_sitenameInString);


        ArrayList<Annotation> detected_sitelocation = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_SITELOCATION);

        String detected_sitelocationInString = getLatestAnnotation(detected_sitelocation);

        Log.d(TAG, "detected_sitelocationInString : "+detected_sitelocationInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITELOCATION, detected_sitelocationInString);


        ArrayList<Annotation> labels = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_Label);

        String labelsInString = getLatestAnnotation(labels);

        Log.d(TAG, "labelsInString : "+labelsInString);

        labelsInString = labelsInString.trim();

        if(!labelsInString.equals("")){

            annotationSetJson.put(Constants.ANNOTATION_TAG_Label, new JSONObject(labelsInString));
        }else{

            annotationSetJson.put(Constants.ANNOTATION_TAG_Label, labelsInString);
        }

        return annotationSetJson;
    }

    private String getLatestAnnotation(ArrayList<Annotation> annotationArrayList){

        if(annotationArrayList.size() == 0)
            return "";

        return annotationArrayList.get(annotationArrayList.size()-1).getContent();
    }

    private void storeTransporatation(JSONObject data){

        try {

            JSONArray transportationAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.transportationMode_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();
            if(rows!=0){

                cursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String transportation = cursor.getString(2);

                    //Log.d(TAG,"transportation : "+transportation+" timestamp : "+timestamp);

                    //convert into second
//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamps, Transportation>
                    Pair<String, String> transportationTuple = new Pair<>(timestamp, transportation);

                    String dataInPythonTuple = Utils.toPythonTuple(transportationTuple);

                    transportationAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("TransportationMode",transportationAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeLocation(JSONObject data){

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

                    //Log.d(TAG,"timestamp : "+timestamp+" latitude : "+latitude+" longtitude : "+longtitude+" accuracy : "+accuracy);

                    //convert into second
//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, latitude, longitude, accuracy>
                    Quartet<String, String, String, String> locationTuple =
                            new Quartet<>(timestamp, latitude, longtitude, accuracy);

                    String dataInPythonTuple = Utils.toPythonTuple(locationTuple);

                    locationAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Location",locationAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeActivityRecognition(JSONObject data){

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

                    //convert into Second
//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamps, MostProbableActivity, ProbableActivities>
                    Triplet<String, String, String> arTuple =
                            new Triplet<>(timestamp, mostProbableActivity, probableActivities);

                    String dataInPythonTuple = Utils.toPythonTuple(arTuple);

                    arAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("ActivityRecognition",arAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeRinger(JSONObject data){

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

                    //Log.d(TAG,"timestamp : "+timestamp+" RingerMode : "+RingerMode+" AudioMode : "+AudioMode+
//                            " StreamVolumeMusic : "+StreamVolumeMusic+" StreamVolumeNotification : "+StreamVolumeNotification
//                            +" StreamVolumeRing : "+StreamVolumeRing +" StreamVolumeVoicecall : "+StreamVolumeVoicecall
//                            +" StreamVolumeSystem : "+StreamVolumeSystem);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestampInSec, streamVolumeSystem, streamVolumeVoicecall, streamVolumeRing,
                    // streamVolumeNotification, streamVolumeMusic, audioMode, ringerMode>
                    Octet<String, String, String, String, String, String, String, String> ringerTuple
                            = new Octet<>(timestamp, streamVolumeSystem, streamVolumeVoicecall, streamVolumeRing,
                            streamVolumeNotification, streamVolumeMusic, audioMode, ringerMode);

                    String dataInPythonTuple = Utils.toPythonTuple(ringerTuple);

                    ringerAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Ringer",ringerAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeConnectivity(JSONObject data){

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

                    //Log.d(TAG,"timestamp : "+timestamp+" NetworkType : "+NetworkType+" IsNetworkAvailable : "+IsNetworkAvailable
//                            +" IsConnected : "+IsConnected+" IsWifiAvailable : "+IsWifiAvailable
//                            +" IsMobileAvailable : "+IsMobileAvailable +" IsWifiConnected : "+IsWifiConnected
//                            +" IsMobileConnected : "+IsMobileConnected);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestampInSec, IsMobileConnected, IsWifiConnected, IsMobileAvailable,
                    // IsWifiAvailable, IsConnected, IsNetworkAvailable, NetworkType>
                    Octet<String, String, String, String, String, String, String, String> connectivityTuple
                            = new Octet<>(timestamp, IsMobileConnected, IsWifiConnected, IsMobileAvailable,
                            IsWifiAvailable, IsConnected, IsNetworkAvailable, NetworkType);

                    String dataInPythonTuple = Utils.toPythonTuple(connectivityTuple);

                    connectivityAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Connectivity",connectivityAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeBattery(JSONObject data){

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

                    //Log.d(TAG,"timestamp : "+timestamp+" BatteryLevel : "+BatteryLevel+" BatteryPercentage : "+
//                            BatteryPercentage+" BatteryChargingState : "+BatteryChargingState+" isCharging : "+isCharging);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamps, isCharging, BatteryChargingState, BatteryPercentage, BatteryLevel>
                    Quintet<String, String, String, String, String> batteryTuple
                            = new Quintet<>(timestamp, isCharging, BatteryChargingState, BatteryPercentage, BatteryLevel);

                    String dataInPythonTuple = Utils.toPythonTuple(batteryTuple);

                    batteryAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Battery", batteryAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeAppUsage(JSONObject data){

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

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, ScreenStatus, Latest_Used_App, Latest_Foreground_Activity>
                    Quartet<String, String, String, String> appUsageTuple
                            = new Quartet<>(timestamp, ScreenStatus, Latest_Used_App, Latest_Foreground_Activity);

                    String dataInPythonTuple = Utils.toPythonTuple(appUsageTuple);

                    appUsageAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("AppUsage",appUsageAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    public void storeTelephony(JSONObject data){

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

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, networkOperatorName, CallState, PhoneSignalType_col, gsmSignalStrength, LTESignalStrength, CdmaSignalStrengthLevel>
                    Septet<String, String, String, String, String, String ,String> telephonyTuple
                            = new Septet<>(timestamp, networkOperatorName, callState, phoneSignalType, gsmSignalStrength, LTESignalStrength, CdmaSignalStrengthLevel);

                    String dataInPythonTuple = Utils.toPythonTuple(telephonyTuple);

                    telephonyAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Telephony",telephonyAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    public void storeSensor(JSONObject data){

        try {

            JSONArray sensorAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

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

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, accelerometer, gyroscope, gravity, linear_acceleration, ROTATION_VECTOR, PROXIMITY, MAGNETIC_FIELD, LIGHT, PRESSURE>
                    Decade<String, String, String, String, String, String ,String, String, String, String> sensorTuple1
                            = new Decade<>(timestamp, accelerometer, gyroscope, gravity, linear_acceleration, rotation_vector, proximity, magnetic_field, light, pressure);

                    String relative_humidity = cursor.getString(11);
                    String ambient_temperature = cursor.getString(12);

                    //<RELATIVE_HUMIDITY, AMBIENT_TEMPERATURE>
                    Pair<String, String> sensorTuple2 = new Pair<>(relative_humidity, ambient_temperature);

                    String dataInPythonTuple = Utils.tupleConcat(sensorTuple1, sensorTuple2);

                    Log.d(TAG, "Sensor availSite : "+dataInPythonTuple);

                    sensorAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Sensor",sensorAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    public void storeAccessibility(JSONObject data){

        try {

            JSONArray accessibilityAndtimestampsJson = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.

            int rows = cursor.getCount();

            if(rows!=0){

                cursor.moveToFirst();

                for(int i=0;i<rows;i++) {

                    String timestamp = cursor.getString(1);
                    String pack = cursor.getString(2);
                    String text = cursor.getString(3);
                    String type = cursor.getString(4);
                    String extra = cursor.getString(5);

//                    String timestampInSec = timestamp.substring(0, timestamp.length()-3);

                    //<timestamp, pack, text, type, extra>
                    Quintet<String, String, String, String, String> accessibilityTuple
                            = new Quintet<>(timestamp, pack, text, type, extra);

                    String dataInPythonTuple = Utils.toPythonTuple(accessibilityTuple);

                    accessibilityAndtimestampsJson.put(dataInPythonTuple);

                    cursor.moveToNext();
                }

                data.put("Accessibility", accessibilityAndtimestampsJson);
            }
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private long getSpecialTimeInMillis(String givenDateFormat){
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_NOW);
        long timeInMilliseconds = 0;
        try {
            Date mDate = sdf.parse(givenDateFormat);
            timeInMilliseconds = mDate.getTime();
            Log.d(TAG,"Date in milli :: " + timeInMilliseconds);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timeInMilliseconds;
    }

    public static String getTimeString(long time){

        SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_for_storing);
        String currentTimeString = sdf_now.format(time);

        return currentTimeString;
    }

    public String makingDataFormat(int year,int month,int date,int hour,int min){
        String dataformat= "";

        dataformat = addZero(year)+"/"+addZero(month)+"/"+addZero(date)+" "+addZero(hour)+":"+addZero(min)+":00";
        Log.d(TAG,"dataformat : " + dataformat);

        return dataformat;
    }

    public String getDateCurrentTimeZone(long timestamp) {
        try{
            Calendar calendar = Calendar.getInstance();
            TimeZone tz = TimeZone.getDefault();
            calendar.setTimeInMillis(timestamp);
            calendar.add(Calendar.MILLISECOND, tz.getOffset(calendar.getTimeInMillis()));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date currenTimeZone = (Date) calendar.getTime();
            return sdf.format(currenTimeZone);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String addZero(int date){
        if(date<10)
            return String.valueOf("0"+date);
        else
            return String.valueOf(date);
    }

}
