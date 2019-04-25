package labelingStudy.nctu.minuku_2.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import labelingStudy.nctu.minuku.Data.DBHelper;
import labelingStudy.nctu.minuku.Utilities.ScheduleAndSampleManager;
import labelingStudy.nctu.minuku.config.ActionLogVar;
import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.manager.DBManager;
import labelingStudy.nctu.minuku_2.R;
import labelingStudy.nctu.minuku_2.Utils;


/**
 * Created by Lawrence on 2017/4/22.
 */

public class Timer_site extends AppCompatActivity {

    final private String TAG = "Timer_site";

    private ListView listview;
    public static ArrayList<String> availSite;
    public ArrayList<String> availSiteId;

    private SharedPreferences sharedPrefs;

    public Timer_site(){}

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.timer_site);

        sharedPrefs = getSharedPreferences(Constants.sharedPrefString, Context.MODE_PRIVATE);

        availSite = new ArrayList<>();
        availSiteId = new ArrayList<>();
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPrefs.edit().putString("lastActivity", getClass().getName()).apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {

            Timer_site.this.finish();

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

        inittimer_site();
    }

    public void inittimer_site(){

        listview = (ListView) findViewById(R.id.convenientList);

        availSite = new ArrayList<>();
        availSiteId = new ArrayList<>();

        final String ADD_NEW_SITE_NAME = getResources().getString(R.string.timer_site_add_new_site);
        availSite.add(ADD_NEW_SITE_NAME);

        //output their custom sites from sqlite
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        Cursor cursor = db.rawQuery("SELECT "+DBHelper.id + " ,"  + DBHelper.convenientsite_col +" FROM "+ DBHelper.convenientsite_table, null);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {

            Log.d(TAG,"sitename id : "+cursor.getString(0));
            Log.d(TAG,"sitename : "+cursor.getString(1));

            availSite.add(cursor.getString(1));
            availSiteId.add(cursor.getString(0));
            cursor.moveToNext();
        }

        Log.d(TAG, "availSite : "+ availSite);

        Timer_site_ListAdapter timer_site_ListAdapter = new Timer_site_ListAdapter(
                this,
                R.id.recording_list,
                availSite
        );

        listview.setAdapter(timer_site_ListAdapter);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_LIST+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_CONV_SITE_LIST+" - "+TAG);

                if(position == 0) {

                    if(!Utils.haveNetworkConnection(getApplicationContext()))
                        Toast.makeText(Timer_site.this, getResources().getString(R.string.reminder_check_connection), Toast.LENGTH_SHORT).show();
                    else
                        startActivity(new Intent(Timer_site.this, PlaceSelection.class));
                }else{

                    String sitename = (String) parent.getItemAtPosition(position);

                    Log.d(TAG, "SiteName : " + sitename);

                    //add siteName to the ongoing Session
                    Intent intent = new Intent(Timer_site.this, CounterActivity.class);
                    intent.putExtra("SiteName", sitename);
                    startActivity(intent);

                    Timer_site.this.finish();
                }
            }
        });

        listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {

                DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_LIST+" - "+ ActionLogVar.ACTION_LONG_CLICK+" - "+ActionLogVar.MEANING_CONV_SITE_LIST+" - "+TAG);

                if(position != 0) {

                    //ask to delete the current item
                    AlertDialog.Builder builder = new AlertDialog.Builder(Timer_site.this);
                    builder.setMessage(R.string.timer_site_delete_site)
                            .setPositiveButton(R.string.decide_in_chinese, null)
                            .setNegativeButton(R.string.undecide_in_chinese, null);

                    final AlertDialog mAlertDialog = builder.create();
                    mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

                        @Override
                        public void onShow(final DialogInterface dialogInterface) {
                            Button posButton = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                            posButton.setOnClickListener(new View.OnClickListener() {

                                @Override
                                public void onClick(View view) {

                                    DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_OK+" - "+TAG);

                                    //delete
                                    String targetSiteId = availSiteId.get(position-1);

                                    Log.d(TAG, "targetSiteId : "+ targetSiteId);
                                    DBHelper.deleteConvSite(targetSiteId);

                                    dialogInterface.dismiss();

                                    refresh();
                                }
                            });

                            Button negaButton = mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                            negaButton.setOnClickListener(new View.OnClickListener() {

                                @Override
                                public void onClick(View view) {

                                    DBHelper.insertActionLogTable(ScheduleAndSampleManager.getCurrentTimeInMillis(), ActionLogVar.VIEW_BUTTON+" - "+ ActionLogVar.ACTION_CLICK+" - "+ActionLogVar.MEANING_NO+" - "+TAG);

                                    dialogInterface.dismiss();
                                }
                            });
                        }
                    });

                    mAlertDialog.show();
                }

                return true;
            }
        });

    }

    public void refresh(){

        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

}
