/*
 * Copyright (c) 2016.
 *
 * DReflect and Minuku Libraries by Shriti Raj (shritir@umich.edu) and Neeraj Kumar(neerajk@uci.edu) is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.
 * Based on a work at https://github.com/Shriti-UCI/Minuku-2.
 *
 *
 * You are free to (only if you meet the terms mentioned below) :
 *
 * Share — copy and redistribute the material in any medium or format
 * Adapt — remix, transform, and build upon the material
 *
 * The licensor cannot revoke these freedoms as long as you follow the license terms.
 *
 * Under the following terms:
 *
 * Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.
 * NonCommercial — You may not use the material for commercial purposes.
 * ShareAlike — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.
 * No additional restrictions — You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits.
 */

package labelingStudy.nctu.minuku_2.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import labelingStudy.nctu.minuku.Utilities.CSVHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.MinukuStreamManager;
import labelingStudy.nctu.minuku.manager.MobilityManager;
import labelingStudy.nctu.minuku.manager.SessionManager;
import labelingStudy.nctu.minuku.model.Session;
import labelingStudy.nctu.minuku.streamgenerator.TransportationModeStreamGenerator;
import labelingStudy.nctu.minuku_2.R;
import labelingStudy.nctu.minuku_2.Receiver.RestarterBroadcastReceiver;
import labelingStudy.nctu.minuku_2.Receiver.WifiReceiver;
import labelingStudy.nctu.minuku_2.Utils;
import labelingStudy.nctu.minuku_2.controller.Dispatch;
import labelingStudy.nctu.minuku_2.manager.InstanceManager;
import labelingStudy.nctu.minuku_2.manager.PostManager;

public class BackgroundService extends Service {

    private static final String TAG = "BackgroundService";

    final static String CHECK_RUNNABLE_ACTION = "checkRunnable";
    final static String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    WifiReceiver mWifiReceiver;
    IntentFilter intentFilter;

    MinukuStreamManager streamManager;

    private ScheduledExecutorService mScheduledExecutorService;
    ScheduledFuture<?> mScheduledFuture, mScheduledFutureIsAlive;

    private String ongoingNotificationText = Constants.RUNNING_APP_DECLARATION;

    NotificationManager mNotificationManager;

    public static boolean isBackgroundServiceRunning = false;
    public static boolean isBackgroundRunnableRunning = false;

    private SharedPreferences sharedPrefs;

    public BackgroundService() {
        super();
    }

    @Override
    public void onCreate() {

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, MODE_PRIVATE);

        isBackgroundServiceRunning = false;
        isBackgroundRunnableRunning = false;

        streamManager = MinukuStreamManager.getInstance();
        mScheduledExecutorService = Executors.newScheduledThreadPool(Constants.MAIN_THREAD_SIZE);

        //TODO deprecated
//        intentFilter = new IntentFilter();
//        intentFilter.addAction(CONNECTIVITY_ACTION);
//        intentFilter.addAction(Constants.ACTION_CONNECTIVITY_CHANGE);
//
//        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "Is wifiReceiver existed ? "+(mWifiReceiver == null));
//
//        if(mWifiReceiver == null) {
//
//            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "wifiReceiver is gone, create a new one.");
//
//            mWifiReceiver = new WifiReceiver();
//        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "isBackgroundServiceRunning ? "+isBackgroundServiceRunning);
        CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "isBackgroundRunnableRunning ? "+isBackgroundRunnableRunning);

        String onStart = "BackGround, start service";
        CSVHelper.storeToCSV(CSVHelper.CSV_ESM, onStart);
        CSVHelper.storeToCSV(CSVHelper.CSV_CAR, onStart);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        createNotificationChannel(Constants.ONGOING_CHANNEL_NAME, Constants.ONGOING_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
        createNotificationChannel(Constants.SURVEY_CHANNEL_NAME, Constants.SURVEY_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);
        createNotificationChannel(Constants.UPLOAD_CHANNEL_NAME, Constants.UPLOAD_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);

        //make the WifiReceiver start sending availSite to the server.
        //TODO deprecated
//        registerReceiver(mWifiReceiver, intentFilter);
        registerConnectivityNetworkMonitorForAPI21AndUp();

        IntentFilter checkRunnableFilter = new IntentFilter(CHECK_RUNNABLE_ACTION);
        registerReceiver(CheckRunnableReceiver, checkRunnableFilter);

        //building the ongoing notification to the foreground
        startForeground(Constants.ONGOING_NOTIFICATION_ID, getOngoingNotification(ongoingNotificationText));

        if (!isBackgroundServiceRunning) {

            Log.d(TAG, "Initialize the Manager");

            isBackgroundServiceRunning = true;

            CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "Going to judge the condition is ? "+(!InstanceManager.isInitialized()));

            if (!InstanceManager.isInitialized()) {

                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "Going to start the runnable.");

                InstanceManager.getInstance(this);
                SessionManager.getInstance(this);
                MobilityManager.getInstance(this);
            }

            updateNotificationAndStreamManagerThread();
        }

        // read test file
//        FileHelper fileHelper = FileHelper.getInstance(getApplicationContext());
//        FileHelper.readTestFile();

        return START_REDELIVER_INTENT;
    }

    private void updateNotificationAndStreamManagerThread() {

        mScheduledFuture = mScheduledExecutorService.scheduleAtFixedRate(
                updateStreamManagerRunnable,
                Constants.STREAM_UPDATE_DELAY,
                Constants.STREAM_UPDATE_FREQUENCY,
                TimeUnit.SECONDS);

        mScheduledFutureIsAlive = mScheduledExecutorService.scheduleAtFixedRate(
                isAliveRunnable,
                Constants.ISALIVE_UPDATE_DELAY,
                Constants.ISALIVE_UPDATE_FREQUENCY,
                TimeUnit.SECONDS
        );
    }

    Runnable isAliveRunnable = new Runnable() {
        @Override
        public void run() {

            Log.d(TAG, "sendingIsAliveData");

            CSVHelper.storeToCSV(CSVHelper.CSV_CHECK_ISALIVE, "sendingIsAliveData");

            Constants.DEVICE_ID = sharedPrefs.getString("DEVICE_ID",  Constants.DEVICE_ID);

            if(!Constants.DEVICE_ID.equals(Constants.INVALID_STRING_VALUE)) {

                sendingIsAliveData();
            }
        }
    };

    Runnable updateStreamManagerRunnable = new Runnable() {
        @Override
        public void run() {

            try {

                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "isBackgroundServiceRunning ? "+isBackgroundServiceRunning);
                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "isBackgroundRunnableRunning ? "+isBackgroundRunnableRunning);

                streamManager.updateStreamGenerators();
            }catch (Exception e){

                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "Background, service update, stream, Exception");
                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, Utils.getStackTrace(e));
            }
        }
    };

    private Notification getOngoingNotification(String text) {

        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
        bigTextStyle.setBigContentTitle(Constants.APP_NAME);
        bigTextStyle.bigText(text);

        Intent resultIntent = new Intent(this, Dispatch.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder noti = new Notification.Builder(this)
                .setContentTitle(Constants.APP_FULL_NAME)
                .setContentText(text)
                .setStyle(bigTextStyle)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .setChannelId(Constants.ONGOING_CHANNEL_ID)
                    .build();
        } else {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .build();
        }
    }

    private int getNotificationIcon(Notification.Builder notificationBuilder) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            notificationBuilder.setColor(Color.TRANSPARENT);
            return R.drawable.muilab_icon_noti;
        }

        return R.drawable.muilab_icon;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        stopTheSessionByServiceClose();

        isBackgroundServiceRunning = false;
        isBackgroundRunnableRunning = false;

        String onDestroy = "BackGround, onDestroy";
        CSVHelper.storeToCSV(CSVHelper.CSV_ESM, onDestroy);
        CSVHelper.storeToCSV(CSVHelper.CSV_CAR, onDestroy);

        mNotificationManager.cancel(Constants.ONGOING_NOTIFICATION_ID);

        sharedPrefs.edit().putInt("CurrentState", TransportationModeStreamGenerator.mCurrentState).apply();
        sharedPrefs.edit().putInt("ConfirmedActivityType", TransportationModeStreamGenerator.mConfirmedActivityType).apply();

//        checkingRemovedFromForeground();
        removeRunnable();

        sendBroadcastToStartService();

        unregisterReceiver(mWifiReceiver);
    }

    @Override
    public void onTaskRemoved(Intent intent){
        super.onTaskRemoved(intent);

        mNotificationManager.cancel(Constants.ONGOING_NOTIFICATION_ID);

        isBackgroundServiceRunning = false;
        isBackgroundRunnableRunning = false;

        String onTaskRemoved = "BackGround, onTaskRemoved";
        CSVHelper.storeToCSV(CSVHelper.CSV_CheckService_alive, onTaskRemoved);

        sharedPrefs.edit().putInt("CurrentState", TransportationModeStreamGenerator.mCurrentState).apply();
        sharedPrefs.edit().putInt("ConfirmedActivityType", TransportationModeStreamGenerator.mConfirmedActivityType).apply();

//        checkingRemovedFromForeground();
        removeRunnable();

        sendBroadcastToStartService();
    }

    private void registerConnectivityNetworkMonitorForAPI21AndUp() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();

        connectivityManager.registerNetworkCallback(
                builder.build(),
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities){
                        sendBroadcast(
                                getConnectivityIntent("onCapabilitiesChanged : "+networkCapabilities.toString())
                        );
                    }
                }
        );

    }

    private void checkingRemovedFromForeground(){

        Log.d(TAG,"stopForeground");

        stopForeground(true);

        try {

            unregisterReceiver(CheckRunnableReceiver);
        }catch (IllegalArgumentException e){

        }

        mScheduledExecutorService.shutdown();
    }

    private void stopTheSessionByServiceClose(){

        int ongoingSessionid = sharedPrefs.getInt("ongoingSessionid", Constants.INVALID_INT_VALUE);

        //if the background service is killed, set the end time of the ongoing trip (if any) using the current timestamp
//        if (SessionManager.getOngoingSessionIdList().size()>0){

        if(ongoingSessionid != Constants.INVALID_INT_VALUE){

            Session session = SessionManager.getSession(ongoingSessionid) ;

            //if we end the current session, we should update its time and set a long enough flag
            if (session.getEndTime()==0){
                long endTime = ScheduleAndSampleManager.getCurrentTimeInMillis();
                session.setEndTime(endTime);
            }

            //end the current session
            SessionManager.endCurSession(session);

            //keep it when the service is gone to recover to the Arraylist
            sharedPrefs.edit().putInt("ongoingSessionid", Constants.INVALID_INT_VALUE).apply();
        }
    }

    private void removeRunnable(){

        mScheduledFuture.cancel(true);
        mScheduledFutureIsAlive.cancel(true);
    }

    private void sendBroadcastToStartService(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            sendBroadcast(new Intent(this, RestarterBroadcastReceiver.class).setAction(Constants.CHECK_SERVICE_ACTION));
        } else {

            Intent checkServiceIntent = new Intent(Constants.CHECK_SERVICE_ACTION);
            sendBroadcast(checkServiceIntent);
        }
    }

    private Intent getConnectivityIntent(String message) {

        Intent intent = new Intent();

        intent.setAction(Constants.ACTION_CONNECTIVITY_CHANGE);

        intent.putExtra("message", message);

        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void createNotificationChannel(String channelName, String channelID, int channelImportance) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = channelName;
            int importance = channelImportance;
            NotificationChannel channel = new NotificationChannel(channelID, name, importance);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendingIsAliveData(){

        final String postIsAliveUrl_insert = Constants.SERVER_URL +"isAlive&action=insert&id=";

        String currentCondition = getResources().getString(labelingStudy.nctu.minuku.R.string.current_task);

        //making isAlive
        JSONObject dataInJson = new JSONObject();
        try {

            long currentTime = new Date().getTime();
            String currentTimeString = ScheduleAndSampleManager.getTimeString(currentTime);

            dataInJson.put("time", currentTime);
            dataInJson.put("timeString", currentTimeString);
            dataInJson.put("device_id", Constants.DEVICE_ID);
            dataInJson.put("condition", currentCondition);

        }catch (JSONException e){
            e.printStackTrace();
        }

        Log.d(TAG, "isAlive availSite uploading : " + dataInJson.toString());

        String curr = Utils.getDateCurrentTimeZone(new Date().getTime());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                new PostManager().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        postIsAliveUrl_insert + Constants.DEVICE_ID,
                        dataInJson.toString(),
                        "isAlive",
                        curr).get();
            else
                new PostManager().execute(
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

    BroadcastReceiver CheckRunnableReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(CHECK_RUNNABLE_ACTION)) {

                Log.d(TAG, "[check runnable] going to check if the runnable is running");

                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "going to check if the runnable is running");
                CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "is the runnable running ? " + isBackgroundRunnableRunning);

                if (!isBackgroundRunnableRunning) {

                    Log.d(TAG, "[check runnable] the runnable is not running, going to restart it.");

                    CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "the runnable is not running, going to restart it");

                    updateNotificationAndStreamManagerThread();

                    Log.d(TAG, "[check runnable] the runnable is restarted.");

                    CSVHelper.storeToCSV(CSVHelper.CSV_RUNNABLE_CHECK, "the runnable is restarted");
                }

                PendingIntent pi = PendingIntent.getBroadcast(BackgroundService.this, 0, new Intent(CHECK_RUNNABLE_ACTION), 0);

                AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    alarm.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + Constants.PROMPT_SERVICE_REPEAT_MILLISECONDS,
                            pi);
                }else{

                    alarm.set(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + Constants.PROMPT_SERVICE_REPEAT_MILLISECONDS,
                            pi
                    );
                }
            }
        }
    };

    //TODO if the thread have to be launched in service, uncomment it back
    /*private static void sendTripData(QueryDataManager queryDataManager, SharedPreferences sharedPrefs, final Context context, final NotificationManager mNotificationManager) throws JSONException, InterruptedException, ExecutionException{

        Log.d(TAG, "sendTripData");
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sendTripData");

        long boundaryTime = setSentBoundaryTime();

        ArrayList<JSONObject> tripInJson = queryDataManager.queryTripData(boundaryTime);

        Log.d(TAG, "TRIP_NOTI_TEXT : " + Config.TRIP_NOTI_TEXT);
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "TRIP_NOTI_TEXT : " + Config.TRIP_NOTI_TEXT);

        Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(Constants.DOWNLOADING+Config.TRIP_NOTI_TEXT, context, Dispatch.class);

        int progressMax = tripInJson.size();
        MinukuNotificationManager.startUpdatingNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, progressMax, uploadingNotification, mNotificationManager);

        for(int index = 0; index < tripInJson.size(); index++){

            JSONObject data = tripInJson.get(index);

            String response = sendData(Constants.SERVER_URL + Constants.COLLECTION_TRIP_ACTION_INSERT + Constants.DEVICE_ID
                    , data, Constants.COLLECTION_TRIP);

            Log.d(TAG, "response : " + response);

            response = Utils.getRidOfBrackets(response);

            Log.d(TAG, "response : " + response);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "response : " + response);

            JSONObject responseInJson = new JSONObject(response);

            Log.d(TAG, "is "+QueryDataManager.TRIP_COMPARED_FIELD+" Same ? "+isCertainFieldValueSame(data, responseInJson, QueryDataManager.TRIP_COMPARED_FIELD));
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "is "+QueryDataManager.TRIP_COMPARED_FIELD+" Same ? "+isCertainFieldValueSame(data, responseInJson, QueryDataManager.TRIP_COMPARED_FIELD));

            if(response.equals(Constants.INVALID_STRING_VALUE)){

            } else if (isCertainFieldValueSame(data, responseInJson, QueryDataManager.TRIP_COMPARED_FIELD)) {

                long lastSentStartTime = Long.valueOf(responseInJson.getString(QueryDataManager.TRIP_COMPARED_FIELD));
                sharedPrefs.edit().putLong("lastSentStarttime", lastSentStartTime).apply();

                String sentSessionId = data.getString("sessionid");
                DataHandler.updateSession(Integer.valueOf(sentSessionId), Constants.SESSION_IS_ALREADY_SENT_FLAG);
            }

            MinukuNotificationManager.updateNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, progressMax, index, uploadingNotification, mNotificationManager);
        }

        MinukuNotificationManager.finishUpdatingNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, uploadingNotification, mNotificationManager);
    }

    private static void sendDumpData(QueryDataManager queryDataManager, SharedPreferences sharedPrefs, final Context context, final NotificationManager mNotificationManager) throws JSONException, InterruptedException, ExecutionException{

        Log.d(TAG, "sendDumpData");
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sendDumpData");

        long boundaryTime = setSentBoundaryTime();
        long endTime = queryDataManager.getEndTime();

        boolean tryToSendData = true;
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "tryToSendData ? " + tryToSendData);

        Log.d(TAG,"before send dump data BoundaryTimeString : " + ScheduleAndSampleManager.getTimeString(boundaryTime));
        Log.d(TAG,"before send dump data EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));
        Log.d(TAG, "boundary time > end ? " + (boundaryTime > endTime));
        Log.d(TAG, "DUMP_NOTI_TEXT : " + Config.DUMP_NOTI_TEXT);

        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "before send dump data BoundaryTimeString : " + ScheduleAndSampleManager.getTimeString(boundaryTime));
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "before send dump data EndTimeString : " + ScheduleAndSampleManager.getTimeString(endTime));
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "boundary time > end ? " + (boundaryTime > endTime));
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "DUMP_NOTI_TEXT : " + Config.DUMP_NOTI_TEXT);

        Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(Constants.DOWNLOADING+Config.DUMP_NOTI_TEXT, context, Dispatch.class);

        int progressMax = 0;
        if(boundaryTime > endTime) {

            progressMax = getHourDiff(boundaryTime, endTime);
            MinukuNotificationManager.startUpdatingNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, progressMax, uploadingNotification, mNotificationManager);
        }

        int progressCount = 0;
        while(boundaryTime > endTime && tryToSendData) {

            //TODO testing the thread canceled issue
            Thread.sleep(Constants.MILLISECONDS_PER_SECOND);

            //return the boolean value to check if the network is connected
            tryToSendData = sendEachPieceOfDumpData(queryDataManager, sharedPrefs);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "tryToSendData ? " + tryToSendData);

            //update boundaryTime, endTime
            boundaryTime = setSentBoundaryTime();
            endTime = queryDataManager.getEndTime();

            Log.d(TAG, "boundary time > end ? " + (boundaryTime > endTime));
            Log.d(TAG, "boundaryTime : " + ScheduleAndSampleManager.getTimeString(boundaryTime));
            Log.d(TAG, "endTime : " + ScheduleAndSampleManager.getTimeString(endTime));

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "boundary time > end ? " + (boundaryTime > endTime));

            progressCount++;
            MinukuNotificationManager.updateNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, progressMax, progressCount, uploadingNotification, mNotificationManager);
        }

        MinukuNotificationManager.finishUpdatingNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, uploadingNotification, mNotificationManager);
    }

    private static int getHourDiff(long boundaryTime, long endTime){

        int hourDiff = Constants.INVALID_INT_VALUE;

        long start = ScheduleAndSampleManager.getHourLowerTimeInMillis(boundaryTime);

        hourDiff = (int) ((start - endTime) / Constants.MILLISECONDS_PER_HOUR);

        return hourDiff;
    }

    private static long setSentBoundaryTime(){

        Log.d(TAG, "setSentBoundaryTime");

        long boundaryTime = new Date().getTime() - Constants.MILLISECONDS_PER_DAY;

        return boundaryTime;
    }

    private static boolean sendEachPieceOfDumpData(QueryDataManager queryDataManager, SharedPreferences sharedPrefs) throws JSONException, InterruptedException, ExecutionException{

        JSONObject dumpInJson = queryDataManager.queryDumpData();

        Log.d(TAG, "dumpInJson : "+dumpInJson);
        Log.d(TAG, "dumpInJson endtime : "+dumpInJson.getString(QueryDataManager.DUMP_COMPARED_FIELD));

        String response = sendData(Constants.SERVER_URL + Constants.COLLECTION_DUMP_ACTION_INSERT + Constants.DEVICE_ID
                , dumpInJson, Constants.COLLECTION_DUMP);

        Log.d(TAG, "response : "+response);
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "response : " + response);

        JSONObject responseInJson = new JSONObject(response);

        Log.d(TAG, "is "+QueryDataManager.DUMP_COMPARED_FIELD+" Same ? "+isCertainFieldValueSame(dumpInJson, responseInJson, QueryDataManager.DUMP_COMPARED_FIELD));
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "is "+QueryDataManager.DUMP_COMPARED_FIELD+" Same ? "+isCertainFieldValueSame(dumpInJson, responseInJson, QueryDataManager.DUMP_COMPARED_FIELD));

        if(response.equals(Constants.INVALID_STRING_VALUE)){

            return false;
        } else if (isCertainFieldValueSame(dumpInJson, responseInJson, QueryDataManager.DUMP_COMPARED_FIELD)) {

            long lastSentStartTime = Long.valueOf(responseInJson.getString(QueryDataManager.DUMP_COMPARED_FIELD));
            sharedPrefs.edit().putLong("lastSentStarttime", lastSentStartTime).apply();

            //update startTime, endTime
            queryDataManager.setStartTime(lastSentStartTime);
            queryDataManager.setEndTime(lastSentStartTime + Constants.MILLISECONDS_PER_HOUR);

            return true;
        }

        //default is to not try to send due to it might stuck on the loop
        return false;
    }

    private static String sendData(String insertUrl, JSONObject dataInJson, String dataType) throws InterruptedException, ExecutionException{

        String response = Constants.INVALID_STRING_VALUE;

        String curr = Utils.getDateCurrentTimeZone(new Date().getTime());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            response = new PostManager().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    insertUrl,
                    dataInJson.toString(),
                    dataType,
                    curr).get();
        else
            response = new PostManager().execute(
                    insertUrl,
                    dataInJson.toString(),
                    dataType,
                    curr).get();

        return response;
    }

    private static boolean isCertainFieldValueSame(JSONObject dumpInJson, JSONObject responseInJson, String fieldName) throws JSONException{

        return dumpInJson.getString(fieldName).equals(responseInJson.getString(fieldName));
    }

    private static String message = Constants.INVALID_STRING_VALUE;

    public static String startSendingDumpAndTripData(final QueryDataManager queryDataManager, final SharedPreferences sharedPrefs, final Context context, final NotificationManager mNotificationManager){

        //TODO solution: launch the thread in service
        //TODO thread would be dead if the process is too long. Stop updating notification
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(context, Dispatch.class);
                mNotificationManager.notify(Constants.UPLOADING_NOTIFICATION_ID, uploadingNotification.build());

                try {

                    sendDumpData(queryDataManager, sharedPrefs, context, mNotificationManager);
                    sendTripData(queryDataManager, sharedPrefs, context, mNotificationManager);

                }catch (JSONException e){
                    Log.e(TAG, "JSONException", e);
                    message = context.getResources().getString(R.string.reminder_sending_process_get_wrong);
                }catch (InterruptedException e){
                    Log.e(TAG, "InterruptedException", e);
                    message = context.getResources().getString(R.string.reminder_sending_process_get_wrong);
                }catch (ExecutionException e){
                    Log.e(TAG, "ExecutionException", e);
                    message = context.getResources().getString(R.string.reminder_sending_process_get_wrong);
                }

                mNotificationManager.cancel(Constants.UPLOADING_NOTIFICATION_ID);
            }
        });

        thread.start();

        try{

            //wait for the thread finish
            thread.join();

            //if no error occur
            if(!message.equals(context.getResources().getString(R.string.reminder_sending_process_get_wrong).toString()))
                message = context.getResources().getString(R.string.reminder_data_sent_successfully).toString();

        }catch (InterruptedException e){
            Log.e(TAG, "InterruptedException", e);
            message = context.getResources().getString(R.string.reminder_sending_process_get_wrong);
        }

        return message;
    }*/

}
