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

package labelingStudy.nctu.minuku.manager;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import labelingStudy.nctu.minuku.R;
import labelingStudy.nctu.minuku.config.Constants;

public class MinukuNotificationManager {

    public static int reminderNotificationID = 21;
    public static int ongoingNotificationID = 42;
    public static String ongoingNotificationText = Constants.RUNNING_APP_DECLARATION;
    public static Intent toTimeline;


    public static void setIntentToTimeline(Intent intentToTimeline){

        toTimeline = intentToTimeline;
    }

    public static int getNotificationIcon(Notification.Builder notificationBuilder) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            notificationBuilder.setColor(Color.TRANSPARENT);
            return R.drawable.muilab_icon_noti;

        }
        return R.drawable.muilab_icon;
    }

    public static Notification.Builder getUploadingNotification(String text, Context context, Class<?> className) {

        Notification.Builder noti = new Notification.Builder(context)
                .setContentTitle(Constants.APP_FULL_NAME)
                .setContentText(text)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .setChannelId(Constants.UPLOAD_CHANNEL_ID);
        } else {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .setPriority(Notification.PRIORITY_MAX)
                    ;
        }
    }

    public static Notification.Builder getUploadingNotification(Context context, Class<?> className) {

        Notification.Builder noti = new Notification.Builder(context)
                .setContentTitle(Constants.APP_FULL_NAME)
                .setContentText(Constants.DOWNLOADING)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .setChannelId(Constants.UPLOAD_CHANNEL_ID);
        } else {
            return noti
                    .setSmallIcon(getNotificationIcon(noti))
                    .setPriority(Notification.PRIORITY_HIGH)
                    ;
        }
    }

    public static void updateNotificationByProgress(int notificationID, int progressMax, int progressNum, Notification.Builder notification, NotificationManager notificationManager){

        notification.setProgress(progressMax, progressNum, false);
        notificationManager.notify(notificationID, notification.build());
    }

    public static void startUpdatingNotificationByProgress(int notificationID, int progressMax, Notification.Builder notification, NotificationManager notificationManager){

        updateNotificationByProgress(notificationID, progressMax, 0, notification, notificationManager);
    }

    public static void finishUpdatingNotificationByProgress(int notificationID, Notification.Builder notification, NotificationManager notificationManager){

        updateNotificationByProgress(notificationID, 0, 0, notification, notificationManager);
    }
}
