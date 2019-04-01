package labelingStudy.nctu.minuku_2.controller;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Data.DataHandler;
import labelingStudy.nctu.minuku.Utilities.CSVHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.ActionLogVar;
import labelingStudy.nctu.minuku.config.Config;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.MinukuNotificationManager;
import labelingStudy.nctu.minuku_2.R;
import labelingStudy.nctu.minuku_2.Utils;
import labelingStudy.nctu.minuku_2.manager.PostManager;
import labelingStudy.nctu.minuku_2.manager.QueryDataManager;

public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";

    public final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;

    private Button chooseMyMobility, watchMyTimeline, sendData;

    private String current_task;

    private SharedPreferences sharedPrefs;

    private boolean firstTimeOrNot;

    private NotificationManager mNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, MODE_PRIVATE);


        chooseMyMobility = (Button) findViewById(R.id.chooseMyMobility);
        chooseMyMobility.setOnClickListener(choosingMyMobility);

        watchMyTimeline = (Button) findViewById(R.id.watchMyTimeline);
        watchMyTimeline.setOnClickListener(watchingMyTimeline);

        sendData = (Button) findViewById(R.id.sendData);
        sendData.setOnClickListener(sendingData);
        setSendingButtonStyle();

        current_task = getResources().getString(R.string.current_task);
        Constants.CURRENT_TASK = current_task;

        if(current_task.equals(getResources().getString(R.string.task_ESM))) {

            chooseMyMobility.setVisibility(View.GONE);
        }else if(current_task.equals(getResources().getString(R.string.task_CAR))){

            chooseMyMobility.setText(R.string.homepage_switch_activity_button);
        }

//        EventBus.getDefault().register(this);

        int sdk_int = Build.VERSION.SDK_INT;
        if(sdk_int >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }else{
            startSetting();
        }

        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);


        Intent intent = new Intent(getApplicationContext(), Timeline.class);
        MinukuNotificationManager.setIntentToTimeline(intent);
    }

    public void onResume(){
        super.onResume();

        getDeviceid();

        //if there have data to be sent, change the button style with the red rectangle
        setSendingButtonStyle();
    }

    private void setSendingButtonStyle(){

        QueryDataManager queryDataManager = new QueryDataManager();
        queryDataManager.preparedCurrentTimeBoundary(sharedPrefs, WelcomeActivity.this);

        Log.d(TAG, "after preparation");
        Log.d(TAG, "startTime : "+ScheduleAndSampleManager.getTimeString(queryDataManager.getStartTime()));
        Log.d(TAG, "endTime : "+ScheduleAndSampleManager.getTimeString(queryDataManager.getEndTime()));

        boolean isDataToBeSent = checkDataPreparation(setSentBoundaryTime(), queryDataManager);
        if(!isDataToBeSent){

            sendData.setBackgroundResource(R.drawable.button_style);
        }else{

            sendData.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPrefs.edit().putString("lastActivity", getClass().getName()).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_deviceid, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_getDeviceId:
                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_OPTIONITEM+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_GET_DEVICEID+" - "+TAG);
                startActivity(new Intent(WelcomeActivity.this, DeviceIdPage.class));
                return true;

            case R.id.action_permissions:
                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_OPTIONITEM+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_POP_UP_PERMISSIONS+" - "+TAG);

                int sdk_int = Build.VERSION.SDK_INT;
                if(sdk_int >= Build.VERSION_CODES.M) {
                    checkAndRequestPermissions();
                }

                startpermission();
                sharedPrefs.edit().putBoolean("firstTimeOrNot", false).apply();
                return true;

            case R.id.action_datasending_network:

                setToSendDataByWifiOrMobile();

                return true;

        }
        return true;
    }

    private Button.OnClickListener choosingMyMobility = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {

                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_CHOOSE_MOBILITY+" - "+TAG);

                String current_task = getResources().getString(R.string.current_task);

                if(current_task.equals(getResources().getString(R.string.task_PART))) {

                    Intent intent = new Intent(WelcomeActivity.this, Timer_move.class);
                    startActivity(intent);
                }else if(current_task.equals(getResources().getString(R.string.task_CAR))){

                    Intent intent = new Intent(WelcomeActivity.this, CheckPointActivity.class);
                    startActivity(intent);
                }
            }
    };

    private Button.OnClickListener watchingMyTimeline = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_CHOOSE_TIMELINE+" - "+TAG);

            Intent intent = new Intent(WelcomeActivity.this, Timeline.class);
            startActivity(intent);
        }
    };


    public String message = Constants.INVALID_STRING_VALUE;

    private Button.OnClickListener sendingData = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_SEND_DATA+" - "+TAG);

            //check the user would like to send the data via Wi-Fi or Mobile
            Config.is_all_you_can_eat = sharedPrefs.getBoolean("is_all_you_can_eat", Config.is_all_you_can_eat);

            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if(activeNetwork != null && activeNetwork.isConnected()){

                Log.d(TAG, "is_all_you_can_eat : "+Config.is_all_you_can_eat);

                //if user don't want to send data by their mobile
                if(!Config.is_all_you_can_eat){

                    if(activeNetwork.getType() != ConnectivityManager.TYPE_WIFI){

                        Toast.makeText(WelcomeActivity.this, getResources().getString(R.string.reminder_check_wifi_connection), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {

                Toast.makeText(WelcomeActivity.this, getResources().getString(R.string.reminder_check_connection), Toast.LENGTH_SHORT).show();
                return;
            }

            //send data process
            //to make sure that the send manager is alive if the user close the page during sending data
            //proposal: pop up a new page to show the sending process (0% ~ 100%)
            final QueryDataManager queryDataManager = new QueryDataManager();
            queryDataManager.preparedCurrentTimeBoundary(sharedPrefs, WelcomeActivity.this);

            Log.d(TAG, "after preparation");
            Log.d(TAG, "startTime : "+ScheduleAndSampleManager.getTimeString(queryDataManager.getStartTime()));
            Log.d(TAG, "endTime : "+ScheduleAndSampleManager.getTimeString(queryDataManager.getEndTime()));

            //before send the data check if there have any data to send (By Query)
            long boundaryTime = setSentBoundaryTime();
            boolean isDataToBeSent = checkDataPreparation(boundaryTime, queryDataManager);

            if(isDataToBeSent){

                message = getResources().getString(R.string.reminder_data_not_enough).toString();
            } else {

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(getApplicationContext(), Dispatch.class);
                        mNotificationManager.notify(Constants.UPLOADING_NOTIFICATION_ID, uploadingNotification.build());

                        try {

                            sendDumpData(queryDataManager);
                            sendTripData(queryDataManager);

                        }catch (JSONException e){
                            Log.e(TAG, "JSONException", e);
                            message = getResources().getString(R.string.reminder_sending_process_get_wrong);
                        }catch (InterruptedException e){
                            Log.e(TAG, "InterruptedException", e);
                            message = getResources().getString(R.string.reminder_sending_process_get_wrong);
                        }catch (ExecutionException e){
                            Log.e(TAG, "ExecutionException", e);
                            message = getResources().getString(R.string.reminder_sending_process_get_wrong);
                        }

                        mNotificationManager.cancel(Constants.UPLOADING_NOTIFICATION_ID);
                    }
                });

                thread.start();

                try{

                    //wait for the thread finish
                    thread.join();

                    //if no error occur
                    if(!message.equals(getResources().getString(R.string.reminder_sending_process_get_wrong).toString()))
                        message = getResources().getString(R.string.reminder_data_sent_successfully).toString();

                }catch (InterruptedException e){
                    Log.e(TAG, "InterruptedException", e);
                    message = getResources().getString(R.string.reminder_sending_process_get_wrong);
                }

                setSendingButtonStyle();
            }

            Toast.makeText(WelcomeActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };

    private boolean checkDataPreparation(long boundaryTime, QueryDataManager queryDataManager){

        boolean isDataToBeSent = false;

        int dumpCount = getDumpCount(boundaryTime, queryDataManager);
        int tripCount = getTripCount(boundaryTime);

        Config.DUMP_NOTI_TEXT = Constants.EMPTY_STRING_VALUE;
        Config.TRIP_NOTI_TEXT = Constants.EMPTY_STRING_VALUE;

        if(dumpCount == 0 && tripCount == 0){

            isDataToBeSent = true;
        }else if(dumpCount == 0){

            Config.DUMP_NOTI_TEXT = Constants.NOTI_TEXT_1;
        }else if(tripCount == 0){

            Config.TRIP_NOTI_TEXT = Constants.NOTI_TEXT_1;
        }else{

            Config.DUMP_NOTI_TEXT = Constants.NOTI_TEXT_1_2;
            Config.TRIP_NOTI_TEXT = Constants.NOTI_TEXT_2_2;
        }

        return isDataToBeSent;
    }

    private int getDumpCount(long boundaryTime, QueryDataManager queryDataManager){

        long endTime = queryDataManager.getEndTime();
        int dumpCount = 0;
        if(boundaryTime > endTime) {

            dumpCount = getHourDiff(boundaryTime, endTime);
        }

        return dumpCount;
    }

    private int getTripCount(long boundaryTime){

        String tripCountString = DBHelper.querySessionsSize(boundaryTime);

        int tripCount = Integer.valueOf(tripCountString);

        return tripCount;
    }

    private void sendTripData(QueryDataManager queryDataManager) throws JSONException, InterruptedException, ExecutionException{

        Log.d(TAG, "sendTripData");
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "sendTripData");

        long boundaryTime = setSentBoundaryTime();

        ArrayList<JSONObject> tripInJson = queryDataManager.queryTripData(boundaryTime);

        Log.d(TAG, "TRIP_NOTI_TEXT : " + Config.TRIP_NOTI_TEXT);
        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "TRIP_NOTI_TEXT : " + Config.TRIP_NOTI_TEXT);

        Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(Constants.DOWNLOADING+Config.TRIP_NOTI_TEXT, getApplicationContext(), Dispatch.class);

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

    private void sendDumpData(QueryDataManager queryDataManager) throws JSONException, InterruptedException, ExecutionException{

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

        Notification.Builder uploadingNotification = MinukuNotificationManager.getUploadingNotification(Constants.DOWNLOADING+Config.DUMP_NOTI_TEXT, getApplicationContext(), Dispatch.class);

        int progressMax = 0;
        if(boundaryTime > endTime) {

            progressMax = getHourDiff(boundaryTime, endTime);
            MinukuNotificationManager.startUpdatingNotificationByProgress(Constants.UPLOADING_NOTIFICATION_ID, progressMax, uploadingNotification, mNotificationManager);
        }

        int progressCount = 0;
        while(boundaryTime > endTime && tryToSendData) {

            //return the boolean value to check if the network is connected
            tryToSendData = sendEachPieceOfDumpData(queryDataManager);
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

    private int getHourDiff(long boundaryTime, long endTime){

        int hourDiff = Constants.INVALID_INT_VALUE;

        long start = ScheduleAndSampleManager.getHourLowerTimeInMillis(boundaryTime);

        hourDiff = (int) ((start - endTime) / Constants.MILLISECONDS_PER_HOUR);

        return hourDiff;
    }

    private long setSentBoundaryTime(){

        Log.d(TAG, "setSentBoundaryTime");

        long boundaryTime = new Date().getTime() - Constants.MILLISECONDS_PER_DAY;

        return boundaryTime;
    }

    private boolean sendEachPieceOfDumpData(QueryDataManager queryDataManager) throws JSONException, InterruptedException, ExecutionException{

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

    private boolean isCertainFieldValueSame(JSONObject dumpInJson, JSONObject responseInJson, String fieldName) throws JSONException{

        return dumpInJson.getString(fieldName).equals(responseInJson.getString(fieldName));
    }

    private String sendData(String insertUrl, JSONObject dataInJson, String dataType) throws InterruptedException, ExecutionException{

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


    public void startpermission(){

        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));  // 協助工具

        Intent intent1 = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);  //usage
        startActivity(intent1);

//        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); //notification
//        startActivity(intent);

        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));	//location
    }

    private void checkAndRequestPermissions() {

        Log.d(TAG,"checkingAndRequestingPermissions");

        int permissionReadExternalStorage = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionWriteExternalStorage = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        int permissionFineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionCoarseLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int permissionStatus= ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE);

        List<String> listPermissionsNeeded = new ArrayList<>();


        if (permissionReadExternalStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (permissionWriteExternalStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissionFineLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (permissionCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.READ_PHONE_STATE);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            Log.d(TAG, "!listPermissionsNeeded.isEmpty() : "+!listPermissionsNeeded.isEmpty() );

            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),REQUEST_ID_MULTIPLE_PERMISSIONS);
        }else{
            startSetting();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {

                Map<String, Integer> perms = new HashMap<>();

                // Initialize the map with both permissions
                perms.put(android.Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                //perms.put(Manifest.permission.SYSTEM_ALERT_WINDOW, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.ACCESS_COARSE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(android.Manifest.permission.READ_PHONE_STATE, PackageManager.PERMISSION_GRANTED);
//                perms.put(android.Manifest.permission.BODY_SENSORS, PackageManager.PERMISSION_GRANTED);

                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // Check for both permissions
                    if (perms.get(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && perms.get(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
//                            && perms.get(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
                            ){
//                        Log.d("permission", "[permission test]all permission granted");
                        //permission_ok=1;
                        startSetting();
                    } else {
                        Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG).show();
                    }
                }

            }
        }
    }

    public void setToSendDataByWifiOrMobile(){

        Log.d(TAG, "setToSendDataByWifiOrMobile");

        AlertDialog.Builder builder = new AlertDialog.Builder(WelcomeActivity.this);
        builder.setMessage(R.string.ask_for_all_you_can_eat)
                .setPositiveButton(R.string.decide_in_chinese, null)
                .setNegativeButton(R.string.undecide_in_chinese, null);

        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {

                //even KEYCODE_BACK, call, others won't kill the dialog
                return true;
            }
        });

        final AlertDialog mAlertDialog = builder.create();

        mAlertDialog.setCanceledOnTouchOutside(false);

        mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(final DialogInterface dialogInterface) {
                Button posButton = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                posButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_OK+" - "+TAG);

                        Config.is_all_you_can_eat = true;

                        sharedPrefs.edit().putBoolean("is_all_you_can_eat", Config.is_all_you_can_eat).apply();

                        Toast.makeText(WelcomeActivity.this, getResources().getString(R.string.reminder_data_send_not_only_via_wifi), Toast.LENGTH_SHORT).show();

                        dialogInterface.dismiss();
                    }
                });

                Button negaButton = mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                negaButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_NO+" - "+TAG);

                        Config.is_all_you_can_eat = false;

                        sharedPrefs.edit().putBoolean("is_all_you_can_eat", Config.is_all_you_can_eat).apply();

                        Toast.makeText(WelcomeActivity.this, getResources().getString(R.string.reminder_data_send_via_wifi), Toast.LENGTH_SHORT).show();

                        dialogInterface.dismiss();
                    }
                });
            }
        });

        mAlertDialog.show();
    }

    public void startSetting(){

        Log.d(TAG, "startSetting");

        getDeviceid();

        popupPermissionSettingAtFirstTime();
    }

    private void popupPermissionSettingAtFirstTime(){

        firstTimeOrNot = sharedPrefs.getBoolean("firstTimeOrNot", true);

        if(firstTimeOrNot) {

            startpermission();
            setToSendDataByWifiOrMobile();
            firstTimeOrNot = false;
            sharedPrefs.edit().putBoolean("firstTimeOrNot", firstTimeOrNot).apply();
        }
    }

    public void getDeviceid(){

        Log.d(TAG, "getDeviceid");

        TelephonyManager mngr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        int permissionStatus= ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE);
        if(permissionStatus==PackageManager.PERMISSION_GRANTED){

            Constants.DEVICE_ID = mngr.getDeviceId();

            sharedPrefs.edit().putString("DEVICE_ID",  mngr.getDeviceId()).apply();
        }
    }
}
