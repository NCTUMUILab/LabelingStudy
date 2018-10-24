package labelingStudy.nctu.minuku_2.controller;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Utilities.CSVHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.ActionLogVar;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.SessionManager;
import labelingStudy.nctu.minuku.model.Session;
import labelingStudy.nctu.minuku_2.R;

/**
 * Created by Lawrence on 2017/11/8.
 */

public class CheckPointActivity extends AppCompatActivity {

    private final String TAG = "CheckPointActivity";

    private Button checkpoint;

    private SharedPreferences sharedPrefs;

    public CheckPointActivity(){}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.checkpoint_activity);

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, MODE_PRIVATE);
    }

    @Override
    public void onResume(){
        super.onResume();

        initCheckPoint();
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPrefs.edit().putString("lastActivity", getClass().getName()).apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {

            CheckPointActivity.this.finish();

            if(isTaskRoot()){
                startActivity(new Intent(this, WelcomeActivity.class));
            }

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    public void initCheckPoint() {

        checkpoint = (Button) findViewById(R.id.check);

        checkpoint.setOnClickListener(checkpointing);
    }

    private Button.OnClickListener checkpointing = new Button.OnClickListener() {

        public void onClick(View v) {

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_CHECKPOINT+" - "+TAG);

            Log.d(TAG,"checkpointing clicked");

            final AlertDialog.Builder builder = new AlertDialog.Builder(CheckPointActivity.this);
            final LayoutInflater inflater = LayoutInflater.from(CheckPointActivity.this);
            final View layout = inflater.inflate(R.layout.checkpoint_confirm_dialog,null);

            try {

                Session lastSession = SessionManager.getLastSession();

                Log.d(TAG, "lastSession id : "+lastSession.getId());

                if(SessionManager.sessionIsWaiting){

                    lastSession.setUserPressOrNot(true);

                    SessionManager.updateCurSession(lastSession.getId(), ScheduleAndSampleManager.getCurrentTimeInMillis(), lastSession.isUserPress());

                    sharedPrefs.edit().putInt("emptyOngoingSessionid", Constants.INVALID_INT_VALUE).apply();

                    SessionManager.sessionIsWaiting = false;

                    checkpointMessage(builder, layout);

                    return;
                }

                //end the ongoing session first
                long endTime = ScheduleAndSampleManager.getCurrentTimeInMillis();
                lastSession.setEndTime(endTime);

                //end the current session
                SessionManager.endCurSession(lastSession);
                sharedPrefs.edit().putInt("ongoingSessionid", Constants.INVALID_INT_VALUE).apply();

                //if the current session is empty ongoing, remove it from the empty ongoing list
                //then add a new session for the future activity
                addEmptySession();

            }catch (IndexOutOfBoundsException e){

                Log.d(TAG, "[test triggering] No ongoing session now");
                Log.e(TAG, "IndexOutOfBoundsException", e);

                addEmptySession();
            }

//            Toast.makeText(CheckPointActivity.this, getResources().getString(R.string.reminder_checkpoint_successfully), Toast.LENGTH_SHORT).show();

            checkpointMessage(builder, layout);
        }
    };

    private void checkpointMessage(AlertDialog.Builder builder, View layout){

        builder.setView(layout)
                .setPositiveButton(R.string.ok, null);

        final AlertDialog mAlertDialog = builder.create();
        mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(final DialogInterface dialogInterface) {
                Button posButton = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                posButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_OK+" - "+TAG);

                        dialogInterface.dismiss();
                    }
                });
            }
        });

        mAlertDialog.show();
    }

    private void addEmptySession(){

        int sessionCount = SessionManager.getNumOfSession();
        int sessionId = (int) sessionCount + 1;
        Session sessionEmptyActivity = new Session(sessionId);

        Log.d(TAG, "addEmptySession id : "+sessionId);

        long initTime = ScheduleAndSampleManager.getCurrentTimeInMillis();
        sessionEmptyActivity.setStartTime(initTime);
        sessionEmptyActivity.setCreatedTime(initTime);

        //don't add transportation here

        //set the UserPressOrNot true, because it's the checkpointed session
        sessionEmptyActivity.setUserPressOrNot(true);

        sessionEmptyActivity.setModified(false);
        sessionEmptyActivity.setIsSent(Constants.SESSION_SHOULDNT_BEEN_SENT_FLAG);
        sessionEmptyActivity.setType(Constants.SESSION_TYPE_DETECTED_BY_USER);
        sessionEmptyActivity.setHidedOrNot(Constants.SESSION_NEVER_GET_HIDED_FLAG);

//        SessionManager.addEmptyOngoingSessionid(sessionId);
//        SessionManager.getOngoingSessionIdList().add(sessionId);

        sharedPrefs.edit().putInt("emptyOngoingSessionid", sessionId).apply();
        CSVHelper.storeToCSV(CSVHelper.CSV_CHECK_SESSION,"update empty sessionid to "+sessionId);
        sharedPrefs.edit().putInt("ongoingSessionid", sessionId).apply();
        CSVHelper.storeToCSV(CSVHelper.CSV_CHECK_SESSION,"update sessionid to "+sessionId);

        DBHelper.insertSessionTable(sessionEmptyActivity);
    }

}
