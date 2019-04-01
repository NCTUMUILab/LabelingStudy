package labelingStudy.nctu.minuku.Utilities;

import android.os.Environment;

import com.opencsv.CSVWriter;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import labelingStudy.nctu.minuku.config.Constants;
import labelingStudy.nctu.minuku.model.DataRecord.ActivityRecognitionDataRecord;

/**
 * Created by Lawrence on 2018/3/31.
 */

public class CSVHelper {

    public static final String TAG = "CSVHelper";

    public static CSVWriter csv_writer = null;

    public static final String CSV_CheckService_alive = "CheckService.csv";

    public static final String CSV_POS = "CheckPosition.csv";

    public static final String CSV_Wifi = "CheckWifi.csv";
    public static final String CSV_ESM = "CheckESM.csv";
    public static final String CSV_CAR = "CheckCAR.csv";
    public static final String CSV_CHECK_SESSION = "CheckSession.csv";
    public static final String CSV_CHECK_ISALIVE = "CheckIsAlive.csv";
    public static final String CSV_CHECK_TRANSPORTATION = "CheckTransportationMode.csv";
    public static final String CSV_RUNNABLE_CHECK = "Runnable_check.csv";
    public static final String CSV_WIFI_RECEIVER_CHECK = "Wifi_Receiver_check.csv";
    public static final String CSV_EXAMINE_COMBINE_SESSION = "ExamineCombineSession.csv";
    public static final String CSV_SERVER_DATA_STATE = "ServerDataState.csv";

    public static final String CSV_AR_DATA = "ARdata.csv";

    public static void storeToCSV(String fileName, String... texts){

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            //Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+fileName,true));

            List<String[]> data = new ArrayList<String[]>();

            long timestamp = ScheduleAndSampleManager.getCurrentTimeInMillis();

            String timeString = ScheduleAndSampleManager.getTimeString(timestamp);


            List<String> textInList = new ArrayList<>();

            textInList.add(timeString);

            for(int index = 0; index < texts.length;index++){

                textInList.add(texts[index]);
            }

            String[] textInArray = textInList.toArray(new String[0]);

            data.add(textInArray);

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            //e.printStackTrace();
        }/*catch (Exception e){
            //e.printStackTrace();
        }*/
    }

    public static void TransportationState_StoreToCSV(long timestamp, String state, String activitySofar){

        String sFileName = "TransportationState.csv"; //Static.csv

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            //Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = ScheduleAndSampleManager.getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, state, String.valueOf(activitySofar)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            //e.printStackTrace();
        }/*catch (Exception e){
            //e.printStackTrace();
        }*/
    }

    public static void dataUploadingCSV(String dataType, String json){

        String sFileName = "DataUploaded.csv"; //Static.csv

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            //Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            data.add(new String[]{dataType, json});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            //e.printStackTrace();
        }catch (Exception e){
            //e.printStackTrace();
        }
    }

    public static void windowDataCSV(String sFileName, ArrayList<ActivityRecognitionDataRecord> windowData){

        try{

            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            //Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            for(ActivityRecognitionDataRecord eachWindowData : windowData){

                data.add(new String[]{eachWindowData.getMostProbableActivity().toString(),
                        eachWindowData.getProbableActivities().toString(),
                        String.valueOf(eachWindowData.getDetectedtime()), eachWindowData.getSessionid()});
            }

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            //e.printStackTrace();
        }catch (Exception e){
            //e.printStackTrace();
        }
    }
}
