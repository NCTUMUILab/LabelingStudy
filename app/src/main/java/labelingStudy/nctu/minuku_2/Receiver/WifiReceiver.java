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
import org.javatuples.Ennead;
import org.javatuples.Octet;
import org.javatuples.Quintet;
import org.javatuples.Sextet;
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

    private int year,month,day,hour,min,sec;

    private long nowTime = -9999;
    private long startTime = -9999;
    private long endTime = -9999;

    private String currentCondition;

    public static final int HTTP_TIMEOUT = 10000;
    public static final int SOCKET_TIMEOUT = 10000;

    private static final String serverUrl = "http://18.219.118.106:5000/find_latest_and_insert?collection=";

    private static final String postDumpUrl_insert = serverUrl+"dump&action=insert&id=";
    private static final String postDumpUrl_search = serverUrl+"dump&action=search&id=";

    private static final String postTripUrl_insert = serverUrl+"trip&action=insert&id=";
    private static final String postTripUrl_search = serverUrl+"trip&action=search&id=";
    private static final String queryTripUrl_searchAllExistTime = serverUrl+"trip&action=search_all_exist_time&id=";
    private static final String queryTripUrl_searchAll = serverUrl+"trip&action=search_all&id=";

    private static final String postIsAliveUrl_insert = serverUrl+"isAlive&action=insert&id=";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onReceive");

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        //get timzone, prevent the issue when the user start the app in wifi available environment.
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
        sec = sharedPrefs.getInt("StartSec",0);
        currentCondition = context.getResources().getString(labelingStudy.nctu.minuku.R.string.current_task);

        Log.d(TAG, "year : "+ year+" month : "+ month+" day : "+ day+" hour : "+ hour+" min : "+ min+" sec : "+sec);

        if (Constants.ACTION_CONNECTIVITY_CHANGE.equals(intent.getAction())) {

            if(activeNetwork != null &&
                    activeNetwork.getType() == ConnectivityManager.TYPE_WIFI
                    //assure the situation
                    && activeNetwork.isConnected()
                    ){

//                updateTripState();

                uploadData();
            }
        }
    }

    private void updateTripState(){

        try {

            JSONArray tripDataJson = queryDataFromServer(queryTripUrl_searchAll);

            Log.d(TAG, "tripDataJson : "+tripDataJson);

            //based on the createdTime, update the trip data.
            for(int index = 0; index < tripDataJson.length(); index++){

                JSONObject tripDataPiece = new JSONObject(tripDataJson.get(index).toString());

                Log.d(TAG, "tripDataPiece : "+tripDataPiece);

                if(!tripDataPiece.has("createdTime"))
                    continue;

                String createdTime = tripDataPiece.getString("createdTime");
                Log.d(TAG, "Sent createdTime : "+createdTime);
                CSVHelper.storeToCSV(CSVHelper.CSV_SERVER_DATA_STATE, "Sent createdTime : "+createdTime);

                DBHelper.updateSessionTableByCreatedTime(Long.valueOf(createdTime), Constants.SESSION_IS_ALREADY_SENT_FLAG);
            }
        }catch (JSONException e){
            e.printStackTrace();
        }
    }

    private void uploadData(){

        Constants.DEVICE_ID = sharedPrefs.getString("DEVICE_ID",  Constants.DEVICE_ID);

        Log.d(TAG, "DEVICE_ID : "+ Constants.DEVICE_ID);

        if(!Constants.DEVICE_ID.equals(Constants.INVALID_STRING_VALUE)) {

            setNowTime();

            startTime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);
            endTime = getDataStartTime();

            if(startTime != Constants.INVALID_TIME_VALUE){

                endTime = startTime + Constants.MILLISECONDS_PER_HOUR;
            }else{

                startTime = endTime - Constants.MILLISECONDS_PER_HOUR;
            }

            Log.d(TAG, "NowTimeString : " + ScheduleAndSampleManager.getTimeString(nowTime));
            Log.d(TAG, "startTimeString : " + ScheduleAndSampleManager.getTimeString(startTime));
            Log.d(TAG, "endTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));
            Log.d(TAG, "now > end ? " + (nowTime > endTime));

            boolean tryToSendData = true;

            //TODO might cause the infinite loop
            while(nowTime > endTime && tryToSendData) {

                Log.d(TAG,"before send dump data NowTimeString : " + ScheduleAndSampleManager.getTimeString(nowTime));

                Log.d(TAG,"before send dump data EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));

                //TODO return the boolean value to check if the network is connected
                tryToSendData = sendingDumpData(startTime, endTime);

                //update nowTime
                setNowTime();

                //update endTime
                long lastEndTime = endTime;

                startTime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);
                endTime = getDataStartTime();

                if(startTime != Constants.INVALID_TIME_VALUE){

                    endTime = startTime + Constants.MILLISECONDS_PER_HOUR;
                }

                //if the data didn't be sent successfully, don't try to send again
                if(lastEndTime == endTime){

                    break;
                }

                Log.d(TAG, "now > end ? " + (nowTime > endTime));
            }

            // Trip, isAlive
            sendingTripData(nowTime);
        }
    }

    private void setNowTime(){

        nowTime = new Date().getTime() - Constants.MILLISECONDS_PER_DAY;

//        nowTime = new Date().getTime(); //TODO for testing
    }

    public boolean sendingDumpData(long startTime, long endTime){

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
        storeActionLog(dataInJson);

//        Log.d(TAG,"final dump data : "+ dataInJson.toString());


        String curr = getDateCurrentTimeZone(new Date().getTime());

        String lastTimeInServer;

        try {

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sending dump data endTime : ", dataInJson.getString("endTime"));
            long endtimeToLog = Long.valueOf(dataInJson.getString("endTime"));
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sending dump data endTime String : ", ScheduleAndSampleManager.getTimeString(endtimeToLog));

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

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "responded dump endTime : ", lasttimeInServerJson.getString("endTime"));
            long respondedEndtimeToLog = Long.valueOf(lasttimeInServerJson.getString("endTime"));
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "responded dump endTime String : ", ScheduleAndSampleManager.getTimeString(respondedEndtimeToLog));

            if(dataInJson.getString("endTime").equals(lasttimeInServerJson.getString("endTime"))){

                long lastSentStartTime = Long.valueOf(lasttimeInServerJson.getString("endTime"));

                sharedPrefs.edit().putLong("lastSentStarttime", lastSentStartTime).apply();

                return true;
            }else{

                //if connected fail, stop trying and wait for the next time
                return false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e){
            e.printStackTrace();
        }

        //default is to not try to send due to it might stuck on the loop
        return false;
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

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sending trip data createdTime : ", data.getString("createdTime"));
                long createdTimeToLog = Long.valueOf(data.getString("createdTime"));
                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sending trip data createdTime String : ", ScheduleAndSampleManager.getTimeString(createdTimeToLog));

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

                lastTimeInServer = lastTimeInServer.replace("[","").replace("]","");

                Log.d(TAG, "[show availSite response] Trip get rid of [ & ], lastTimeInServer : " + lastTimeInServer);

                JSONObject lasttimeInServerJson = new JSONObject(lastTimeInServer);

                Log.d(TAG, "[show availSite response] check sent createdTime : " + data.getString("createdTime"));
                Log.d(TAG, "[show availSite response] check latest availSite in server's createdTime : " + lasttimeInServerJson.getString("createdTime"));
                Log.d(TAG, "[show availSite response] check condition : " + data.getString("createdTime").equals(lasttimeInServerJson.getString("createdTime")));

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "responded trip data createdTime : ", lasttimeInServerJson.getString("createdTime"));
                long respondedCreatedTimeToLog = Long.valueOf(lasttimeInServerJson.getString("createdTime"));
                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "responded trip data createdTime String : ", ScheduleAndSampleManager.getTimeString(respondedCreatedTimeToLog));

                if(data.getString("createdTime").equals(lasttimeInServerJson.getString("createdTime"))){

                    //TODO deprecated
                    //update the sent Session to already be sent
                    String sentSessionId = data.getString("sessionid");
                    DataHandler.updateSession(Integer.valueOf(sentSessionId), Constants.SESSION_IS_ALREADY_SENT_FLAG);
                } else{

                    //if connected fail, stop trying and wait for the next time
                    break;
                }

            } catch (InterruptedException e) {
                Log.e(TAG,"InterruptedException", e);
                e.printStackTrace();
            } catch (ExecutionException e) {
                Log.e(TAG,"ExecutionException", e);
                e.printStackTrace();
            } catch (JSONException e){
                Log.e(TAG,"JSONException", e);
                e.printStackTrace();
            }
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

            try {

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to send " + dataType + " data by postJSON, time : ", new JSONObject(data).getString("endTime"));
            }catch (JSONException e){

            }

            result = postJSON(url, data, dataType, lastSyncTime);

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "after sending " + dataType + " data by postJSON, result : ", result);

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

            if(responseCode != HttpsURLConnection.HTTP_OK){

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "fail to connect to the server, error code: "+responseCode);
                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to throw IOException");

                throw new IOException("HTTP error code: " + responseCode);
            } else {

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "connected to the server successfully");

                inputStream = conn.getInputStream();
            }
            result = convertInputStreamToString(inputStream);

            Log.d(TAG, "[postJSON] the result response code is " + responseCode);
            Log.d(TAG, "[postJSON] the result is " + result);

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
        } catch (IOException e) {
            e.printStackTrace();

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "IOException", Utils.getStackTrace(e));
        }finally {

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "connection is null ? "+(conn != null));

            if (conn != null) {

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to disconnect");

                conn.disconnect();

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "disconnected successfully");
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

    private long getDataStartTime(){

        long startTime = sharedPrefs.getLong("lastSentStarttime", Constants.INVALID_TIME_VALUE);

        if(startTime == Constants.INVALID_TIME_VALUE) {

            Calendar designatedStartTime = Calendar.getInstance();
            designatedStartTime.set(year, month, day-1, hour, min, sec);

            //get the current time in sharp
            startTime = designatedStartTime.getTimeInMillis();
        }

        Log.d(TAG, "getDataStartTime startTime : "+ScheduleAndSampleManager.getTimeString(startTime));

        return startTime;
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

//        Log.d(TAG, "detected_transportationInString : "+detected_transportationInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY, detected_transportationInString);


        ArrayList<Annotation> detected_sitename = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_SITENAME);

        String detected_sitenameInString = getLatestAnnotation(detected_sitename);

//        Log.d(TAG, "detected_sitenameInString : "+detected_sitenameInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITENAME, detected_sitenameInString);


        ArrayList<Annotation> detected_sitelocation = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_SITELOCATION);

        String detected_sitelocationInString = getLatestAnnotation(detected_sitelocation);

//        Log.d(TAG, "detected_sitelocationInString : "+detected_sitelocationInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITELOCATION, detected_sitelocationInString);


        ArrayList<Annotation> detected_sitePlaceId = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_SITE_PLACEID);

        String detected_sitePlaceIdInString = getLatestAnnotation(detected_sitePlaceId);

//        Log.d(TAG, "detected_sitePlaceIdInString : "+detected_sitePlaceIdInString);

        annotationSetJson.put(Constants.ANNOTATION_TAG_DETECTED_SITE_PLACEID, detected_sitePlaceIdInString);


        ArrayList<Annotation> labels = annotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_Label);

        JSONArray labelsInJSONArray = getLabelsAnnotation(labels);

        annotationSetJson.put(Constants.ANNOTATION_TAG_Label, labelsInJSONArray);

//        Log.d(TAG, "labels in json : "+annotationSetJson);


        return annotationSetJson;
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
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    public void storeSensor(JSONObject data){

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

    public void storeAccessibility(JSONObject data){

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
        }catch (JSONException e){
        }catch(NullPointerException e){
        }
    }

    private void storeActionLog(JSONObject data){

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

    private JSONArray queryDataFromServer(String queryLink) throws JSONException{

        if(Constants.DEVICE_ID.equals(Constants.INVALID_STRING_VALUE)){
            return new JSONArray();
        }

        String data, link = queryLink+Constants.DEVICE_ID;

        JSONArray dataJson = new JSONArray();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                data = new HttpAsyncGetJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        link).get();
            else
                data = new HttpAsyncGetJsonTask().execute(
                        link).get();

            dataJson = new JSONArray(data);

        } catch (InterruptedException e) {

        } catch (ExecutionException e) {

        } catch (NullPointerException e){

        }

        return dataJson;
    }

    private class HttpAsyncGetJsonTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute(){

        }

        @Override
        protected String doInBackground(String... params) {

            String result=null;

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoInput(true);
                connection.setDoOutput(true);

                connection.setReadTimeout(HTTP_TIMEOUT);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }

                InputStream stream = connection.getInputStream();

                if (stream != null) {

                    reader = new BufferedReader(new InputStreamReader(stream));

                    StringBuffer buffer = new StringBuffer();
                    String line = "";

                    while ((line = reader.readLine()) != null) {
                        buffer.append(line+"\n");
                    }

                    return buffer.toString();
                }else{

                    return "";
                }

            } catch (MalformedURLException e) {
                Log.e(TAG, "MalformedURLException");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "IOException");
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {

                }
            }

            return result;
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "get http post result " + result);
        }
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

}
