package labelingStudy.nctu.minuku_2.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.ActionLogVar;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.MinukuNotificationManager;
import labelingStudy.nctu.minuku.manager.SessionManager;
import labelingStudy.nctu.minuku.model.Annotation;
import labelingStudy.nctu.minuku.model.AnnotationSet;
import labelingStudy.nctu.minuku.model.Session;
import labelingStudy.nctu.minuku.streamgenerator.TransportationModeStreamGenerator;
import labelingStudy.nctu.minuku_2.R;


//import edu.ohio.minuku_2.R;

/**
 * Created by Lawrence on 2017/4/22.
 */

public class Timer_move extends AppCompatActivity {

    final private String TAG = "Timer_move";

    private Button walk, bike, car, site;

    private TextView blackTextView;

    private String blackTextViewDefault;

    public static String trafficType;

    private SharedPreferences sharedPrefs;

    private boolean firstTimeOrNot;

    public Timer_move(){}

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.timer_move);

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, MODE_PRIVATE);

        blackTextViewDefault = getResources().getString(R.string.timer_move_black_text_default);

        popupPermissionSettingAtFirstTime();

        inittimer_move();
    }

    //TODO might no need or could move to Utils
    private void popupPermissionSettingAtFirstTime(){

        firstTimeOrNot = sharedPrefs.getBoolean("firstTimeOrNot", true);

        if(firstTimeOrNot) {

            startpermission();
            firstTimeOrNot = false;
            sharedPrefs.edit().putBoolean("firstTimeOrNot", firstTimeOrNot).apply();
        }
    }

    public void startpermission(){

        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));  // 協助工具

        Intent intent1 = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);  //usage
        startActivity(intent1);

//        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); //notification
//        startActivity(intent);

        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));	//location
    }

    @Override
    protected void onResume(){
        super.onResume();

        inittimer_move();
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPrefs.edit().putString("lastActivity", getClass().getName()).apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {

            Timer_move.this.finish();

            if(isTaskRoot()){
                startActivity(new Intent(this, WelcomeActivity.class));
            }

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    public void inittimer_move(){

        walk = (Button) findViewById(R.id.walk);
        bike = (Button) findViewById(R.id.bike);
        car = (Button) findViewById(R.id.car);
        site = (Button) findViewById(R.id.site);

        walk.setOnClickListener(walkingTime);
        bike.setOnClickListener(bikingTime);
        car.setOnClickListener(carTime);
        site.setOnClickListener(siting);

        blackTextView = (TextView) findViewById(R.id.blackTextView);

        if(!MinukuNotificationManager.ongoingNotificationText.equals(Constants.RUNNING_APP_DECLARATION)){

            blackTextView.setText(MinukuNotificationManager.ongoingNotificationText);
        }else{

            blackTextView.setText(blackTextViewDefault);
        }

    }

    private void imagebuttonWork(String activityType){

//        ArrayList<Integer> ongoingSessionIdList = SessionManager.getOngoingSessionIdList();

        int ongoingSessionid = sharedPrefs.getInt("ongoingSessionid", Constants.INVALID_INT_VALUE);

        //if there is an ongoing session
//        if(ongoingSessionIdList.size()>0){
        if(ongoingSessionid != Constants.INVALID_INT_VALUE){

            int sessionId = ongoingSessionid;
            Session ongoingSession = SessionManager.getSession(sessionId);

            AnnotationSet ongoingAnnotationSet = ongoingSession.getAnnotationsSet();
            ArrayList<Annotation> ongoingAnnotations = ongoingAnnotationSet.getAnnotationByTag(Constants.ANNOTATION_TAG_DETECTED_TRANSPORTATION_ACTIVITY);
            Annotation ongoingAnnotation = ongoingAnnotations.get(ongoingAnnotations.size()-1);
            String ongoingActivity = ongoingAnnotation.getContent();

            String buttonActivity = getActivityTypeString(activityType);

            if(!buttonActivity.equals(ongoingActivity)){

                Toast toast = Toast.makeText(Timer_move.this, getResources().getString(R.string.reminder_your_must_stop_the_current_activity) + getActivityTypeInChinese(trafficType), Toast.LENGTH_SHORT);
                toast.show();
            }else {

                startButtonActivity(activityType);
            }
        }else {

            startButtonActivity(activityType);
        }
    }

    private void startButtonActivity(String activityType){

        if(activityType.equals("static")){

            trafficType = "site";
            startActivity(new Intent(Timer_move.this, Timer_site.class));
        }else {

            trafficType = activityType;

            Bundle bundle = new Bundle();
            bundle.putString("trafficType", trafficType);

            Intent intentToRecord = new Intent(Timer_move.this, CounterActivity.class);
            intentToRecord.putExtras(bundle);

            startActivity(intentToRecord);
        }
    }

    private ImageButton.OnClickListener walkingTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {

            String buttonActivity = "walk";

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_IMAGE_VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_WALK+" - "+TAG);

            imagebuttonWork(buttonActivity);
        }
    };

    private ImageButton.OnClickListener bikingTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {

            String buttonActivity = "bike";

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_IMAGE_VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_BIKE+" - "+TAG);

            imagebuttonWork(buttonActivity);
        }
    };

    private ImageButton.OnClickListener carTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {

            String buttonActivity = "car";

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_IMAGE_VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_CAR+" - "+TAG);

            imagebuttonWork(buttonActivity);
        }
    };

    //to view Timer_site
    private Button.OnClickListener siting = new Button.OnClickListener() {
        public void onClick(View v) {

            String buttonActivity = "static";

            DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_IMAGE_VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_SITE+" - "+TAG);

            imagebuttonWork(buttonActivity);
        }
    };

    //TODO Clean Code: encounter Constant Expressions issue if utilitize "case" in string.xml file
    private String getActivityTypeString(String activityType){

        switch (activityType){
            case "walk":
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_ON_FOOT;
            case "bike":
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_ON_BICYCLE;
            case "car" :
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_IN_VEHICLE;
            case "static":
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_NO_TRANSPORTATION;
            default:
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_NA;
        }
    }

    private String getActivityTypeInChinese(String activityType){

        switch (activityType){
            case "walk":
                return getResources().getString(R.string.walk_activity_type_in_chinese);
            case "bike":
                return getResources().getString(R.string.bike_activity_type_in_chinese);
            case "car" :
                return getResources().getString(R.string.car_activity_type_in_chinese);
            case "static":
                return getResources().getString(R.string.static_activity_type_in_chinese);
            default:
                return TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_NA;
        }
    }

}
