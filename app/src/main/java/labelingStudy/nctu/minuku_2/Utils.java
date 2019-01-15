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

package labelingStudy.nctu.minuku_2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.javatuples.Tuple;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import labelingStudy.nctu.minuku.streamgenerator.TransportationModeStreamGenerator;

/**
 * Created by neera_000 on 3/26/2016.
 *
 * modified by shriti 07/22/2016
 */
public class Utils {

    public static String TAG = "UtilityClass";
    /**
     * Given an email Id as string, encodes it so that it can go into the firebase object without
     * any issues.
     * @param unencodedEmail
     * @return
     */
    public static final String encodeEmail(String unencodedEmail) {
        if (unencodedEmail == null) return null;
        return unencodedEmail.replace(".", ",");
    }

    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static String toPythonTuple(Tuple tuple){

        String pythontuple = "("+tuple.toString().replace("[","").replace("]","")+")";

        return pythontuple;
    }

    public static boolean isEnglish(String charaString){

        return charaString.matches("^[a-zA-Z]*");

    }

    public static String tupleConcat(Tuple tuple1, Tuple tuple2){

        String pythontuple = "(" +tuple1.toString().replace("[","").replace("]","")+
                ", "+tuple2.toString().replace("[","").replace("]","")+
                ")";

        return pythontuple;
    }

    public static boolean haveNetworkConnection(Context context) {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

    public static String getActivityStringType(String activityString){

        switch (activityString){

            case TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_ON_FOOT:
                return "walk";
            case TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_ON_BICYCLE:
                return "bike";
            case TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_IN_VEHICLE:
                return "car";
            case TransportationModeStreamGenerator.TRANSPORTATION_MODE_NAME_NO_TRANSPORTATION:
                return "static";
            default:
                return "";
        }
    }

    protected boolean areDatesEqual(String LOG_TAG, long currentTime, long previousTime) {
        Log.d(LOG_TAG, "Checking if the both dates are the same");

        Calendar currentDate = Calendar.getInstance();
        Calendar previousDate = Calendar.getInstance();

        currentDate.setTimeInMillis(currentTime);
        previousDate.setTimeInMillis(previousTime);
        Log.d(LOG_TAG, "Current Year:" + currentDate.get(Calendar.YEAR) + " Previous Year:" + previousDate.get(Calendar.YEAR));
        Log.d(LOG_TAG, "Current Day:" + currentDate.get(Calendar.DAY_OF_YEAR) + " Previous Day:" + previousDate.get(Calendar.DAY_OF_YEAR));
        Log.d(LOG_TAG, "Current Month:" + currentDate.get(Calendar.MONTH) + " Previous Month:" + previousDate.get(Calendar.MONTH));

        boolean sameDay = (currentDate.get(Calendar.YEAR) == previousDate.get(Calendar.YEAR)) &&
                (currentDate.get(Calendar.DAY_OF_YEAR) == previousDate.get(Calendar.DAY_OF_YEAR)) &&
                (currentDate.get(Calendar.MONTH) == previousDate.get(Calendar.MONTH));

        if(sameDay)
            Log.d(LOG_TAG, "it is the same day, should not create a new object");
        else
            Log.d(LOG_TAG, "it is not the same day - a new day, should create a new object");
        return sameDay;
    }

    public static String getDateCurrentTimeZone(long timestamp) {
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

    public static void triggerNoResponding(){

        for(int index = 0; index < Integer.MAX_VALUE; index++){

            for(int y = 0; y < Integer.MAX_VALUE; y++){

            }
        }
    }

    public static void triggerStopRunning(){

        ArrayList<String> test = new ArrayList<>();
        test.get(Integer.MAX_VALUE);
    }

}