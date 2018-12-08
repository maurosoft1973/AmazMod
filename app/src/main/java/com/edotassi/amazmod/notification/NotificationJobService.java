package com.edotassi.amazmod.notification;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.edotassi.amazmod.AmazModApplication;
import com.edotassi.amazmod.event.local.IsWatchConnectedLocal;
import com.huami.watch.transport.DataBundle;
import com.huami.watch.transport.DataTransportResult;
import com.huami.watch.transport.Transporter;
import com.huami.watch.transport.TransporterClassic;
import com.pixplicity.easyprefs.library.Prefs;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayOutputStream;

import amazmod.com.transport.Constants;
import amazmod.com.transport.Transport;
import amazmod.com.transport.data.NotificationData;

public class NotificationJobService extends JobService {

    JobParameters params;

    private static Transporter transporterNotifications, transporterHuami;

    public static final String NOTIFICATION_KEY = "notification_key";
    public static final String NOTIFICATION_MODE = "notification_mode";
    public static final int NOTIFICATION_POSTED_STANDARD_UI = 1000;
    public static final int NOTIFICATION_POSTED_CUSTOM_UI = 2000;
    public static final int NOTIFICATION_REMOVED = 3000;

    private static String result;
    private static int retries = 0;

    @Override
    public boolean onStartJob(JobParameters params) {

        this.params = params;

        final int id = params.getJobId();
        final String key = params.getExtras().getString(NOTIFICATION_KEY, null);
        final int mode = params.getExtras().getInt(NOTIFICATION_MODE, -1);

        int std = NotificationStore.getStandardNotificationCount();
        int cst = NotificationStore.getCustomNotificationCount();
        int bs = NotificationStore.getNotificationBundleCount();

        transporterNotifications = TransporterClassic.get(this, Transport.NAME_NOTIFICATION);

        if (!transporterNotifications.isTransportServiceConnected()) {
            Log.w(Constants.TAG, "NotificationJobService onStartJob transporterNotifications not connected, connecting...");
            transporterNotifications.connectTransportService();
        } else {
            Log.d(Constants.TAG, "NotificationJobService onStartJob transporterNotifications already connected");
        }

        transporterHuami = TransporterClassic.get(this, "com.huami.action.notification");
        if (!transporterHuami.isTransportServiceConnected()) {
            Log.w(Constants.TAG, "NotificationJobService onStartJob transporterHuami not connected, connecting...");
            transporterHuami.connectTransportService();
        } else {
            Log.d(Constants.TAG, "NotificationJobService onStartJob transportedHuami already connected");
        }

        Log.d(Constants.TAG, "NotificationJobService onStartJob id: " + id + " \\ mode: " + mode + " \\ key: " + key);
        Log.d(Constants.TAG, "NotificationJobService onStartJob std#: " + std + " \\ cst#: " + cst +  " \\ bs#: " + bs);

        int delay = 690;

        if (key != null) {

            Log.d(Constants.TAG, "NotificationJobService onStartJob transporterNotifications.isAvailable: " + transporterNotifications.isAvailable());
            Log.d(Constants.TAG, "NotificationJobService onStartJob transporterHuami.isAvailable: " + transporterHuami.isAvailable());

            if (transporterNotifications.isAvailable() && transporterHuami.isAvailable())
                delay = 10;

            final Handler handler = new Handler();

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    switch (mode) {

                        case NOTIFICATION_POSTED_STANDARD_UI:
                            processStandardNotificationPosted(key, mode);
                            break;

                        case NOTIFICATION_POSTED_CUSTOM_UI:
                            processCustomNotificationPosted(key, mode);
                            break;

                        case NOTIFICATION_REMOVED:
                            processNotificationRemoved(key, mode);
                            break;

                        default:
                            Log.e(Constants.TAG, "NotificationJobService onStartJob error: no NOTIFICATION_MODE found!");

                    }
                }
            }, delay + 10);

        } else
            Log.e(Constants.TAG, "NotificationJobService onStartJob error: null key!");

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(Constants.TAG, "NotificationJobService onStopJob id: " + params.getJobId());

        return true;
    }

    @Override
    public void onDestroy() {

        Log.d(Constants.TAG, "NotificationJobService onDestroy");

        super.onDestroy();
    }

    private void disconnectTransports() {

        if (transporterNotifications.isTransportServiceConnected()) {
            Log.i(Constants.TAG, "NotificationJobService disconnectTransports disconnecting transporterNotifications…");
            transporterNotifications.disconnectTransportService();
        }

        if (transporterHuami.isTransportServiceConnected()) {
            Log.i(Constants.TAG, "NotificationJobService disconnectTransports disconnecting transporterHuami…");
            transporterHuami.disconnectTransportService();
        }
    }

    private void processStandardNotificationPosted(final String key, final int mode) {

        Log.d(Constants.TAG, "NotificationJobService processStandardNotificationPosted key: " + key + " \\ try: " + retries);

        DataBundle dataBundle = NotificationStore.getStandardNotification(key);

        if (transporterHuami.isTransportServiceConnected()) {
            Log.i(Constants.TAG, "NotificationJobService processStandardNotificationPosted transport already connected");
            AmazModApplication.isWatchConnected = true;
        } else {
            Log.w(Constants.TAG, "NotificationJobService processStandardNotificationPosted transport not connected, connecting...");
            transporterHuami.connectTransportService();
            AmazModApplication.isWatchConnected = false;
        }

        Log.i(Constants.TAG, "NotificationJobService processStandardNotificationPosted transporterHuami.isAvailable: " + transporterHuami.isAvailable());

        transporterHuami.send("add", dataBundle, new Transporter.DataSendResultCallback() {
            @Override
            public void onResultBack(DataTransportResult dataTransportResult) {
                result = dataTransportResult.toString();
                Log.d(Constants.TAG, "NotificationJobService processStandardNotificationPosted result: " +result);
                //transporterHuami.disconnectTransportService();

                if (result.toLowerCase().contains("ok")) {
                    Log.d(Constants.TAG, "NotificationJobService processStandardNotificationPosted OK");
                    NotificationStore.removeStandardNotification(key);
                    jobFinished(params, false);
                } else {
                    Log.d(Constants.TAG, "NotificationJobService processStandardNotificationPosted try: " + retries);
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        retries++;
                        processStandardNotificationPosted(key, mode);
                    } else {
                        Log.d(Constants.TAG, "NotificationJobService processStandardNotificationPosted rescheduling…");
                        retries = 0;
                        jobFinished(params, true);
                    }
                }
            }
        });

    }


    public void processNotificationRemoved(final String key, final int mode) {

        Log.d(Constants.TAG, "NotificationJobService processNotificationRemoved key: " + key + " \\ try: " + retries);
        result = "";

        DataBundle dataBundle = NotificationStore.getStandardNotification(key);

        if (transporterHuami.isTransportServiceConnected()) {
            Log.i(Constants.TAG, "NotificationJobService processNotificationRemoved transport already connected");
            AmazModApplication.isWatchConnected = true;
        } else {
            Log.w(Constants.TAG, "NotificationJobService processNotificationRemoved transport not connected, connecting...");
            transporterHuami.connectTransportService();
            AmazModApplication.isWatchConnected = false;
        }

        Log.i(Constants.TAG, "NotificationJobService processNotificationRemoved transporterHuami.isAvailable: " + transporterHuami.isAvailable());

        transporterHuami.send("del", dataBundle, new Transporter.DataSendResultCallback() {
            @Override
            public void onResultBack(DataTransportResult dataTransportResult) {
                result = dataTransportResult.toString();
                Log.d(Constants.TAG, "NotificationJobService processNotificationRemoved result: " + result);
                //transporterHuami.disconnectTransportService();

                if (result.toLowerCase().contains("ok")) {
                    Log.d(Constants.TAG, "NotificationJobService processNotificationRemoved OK");
                    NotificationStore.removeStandardNotification(key);
                    jobFinished(params, false);
                } else {
                    Log.d(Constants.TAG, "NotificationJobService processNotificationRemoved try: " + retries);
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        retries++;
                        processNotificationRemoved(key, mode);
                    } else {
                        Log.d(Constants.TAG, "NotificationJobService processNotificationRemoved rescheduling…");
                        retries = 0;
                        jobFinished(params, true);
                    }
                }
            }
        });

    }

    private void processCustomNotificationPosted(final String key, final int mode) {

        Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted key: " + key);

        NotificationData notificationData = NotificationStore.getCustomNotification(key);

        if (mode == NOTIFICATION_POSTED_CUSTOM_UI) {
            Bundle bundle = NotificationStore.getNotificationBundle(key);
            if (bundle != null)
                extractImagesFromNotification(bundle, notificationData);
        }

        //final Transporter transporter2 = TransporterClassic.get(this, Transport.NAME_NOTIFICATION);

        if (transporterNotifications.isTransportServiceConnected()) {
            Log.i(Constants.TAG,"NotificationJobService processCustomNotificationPosted isTransportServiceConnected: true");
        } else {
            Log.w(Constants.TAG,"NotificationJobService processCustomNotificationPosted isTransportServiceConnected = false, connecting...");
            transporterNotifications.connectTransportService();
            AmazModApplication.isWatchConnected = false;
        }

        boolean isTransportConnected = transporterNotifications.isTransportServiceConnected();
        result = null;
        if (!isTransportConnected) {
            if (AmazModApplication.isWatchConnected || (EventBus.getDefault().getStickyEvent(IsWatchConnectedLocal.class) == null)) {
                AmazModApplication.isWatchConnected = false;
                EventBus.getDefault().removeAllStickyEvents();
                EventBus.getDefault().postSticky(new IsWatchConnectedLocal(AmazModApplication.isWatchConnected));
            }
            Log.w(Constants.TAG, "NotificationJobService processCustomNotificationPosted isTransportConnected: false");
        }

        Log.i(Constants.TAG, "NotificationJobService processCustomNotificationPosted transporterNotifications.isAvailable: " + transporterNotifications.isAvailable());

        DataBundle dataBundle = new DataBundle();
        notificationData.toDataBundle(dataBundle);
        transporterNotifications.send(Transport.INCOMING_NOTIFICATION, dataBundle, new Transporter.DataSendResultCallback() {
            @Override
            public void onResultBack(DataTransportResult dataTransportResult) {
                result = dataTransportResult.toString();
                Log.i(Constants.TAG, "NotificationJobService processCustomNotificationPosted send result: " + result);
                //transporterNotifications.disconnectTransportService();

                if (result.toLowerCase().contains("ok")) {
                    Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted OK");
                    NotificationStore.removeCustomNotification(key);
                    if (mode == NOTIFICATION_POSTED_CUSTOM_UI)
                        NotificationStore.removeNotificationBundle(key);
                    jobFinished(params, false);
                } else {
                    Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted try: " + retries);
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        retries++;
                        processCustomNotificationPosted(key, mode);
                    } else {
                        retries = 0;
                        if (AmazModApplication.isWatchConnected) {
                            Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted rescheduling…");
                            jobFinished(params, true);
                        } else {
                            Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted finishing…");
                            NotificationStore.removeCustomNotification(key);
                            if (mode == NOTIFICATION_POSTED_CUSTOM_UI)
                                NotificationStore.removeNotificationBundle(key);
                            jobFinished(params, false);
                        }
                    }
                }
            }
        });



        /*
        Watch.get().postNotification(notificationData).continueWith(new Continuation<Void, Object>() {
            @Override
            public Object then(@NonNull Task<Void> task) throws Exception {
                if (task.isSuccessful())
                    result = "ok";
                else
                    result = "failed";

                if (task.getException() != null) {
                    Log.e(Constants.TAG, "NotificationJobService processCustomNotificationPosted throw: " + task.getException().toString());
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted try: " + retries);
                        retries++;
                        processCustomNotificationPosted(key, mode);
                    } else {
                        retries = 0;
                        NotificationStore.removeCustomNotification(key);
                        NotificationStore.removeNotificationBundle(key);
                        jobFinished(params, false);
                    }
                    throw task.getException();
                    }

                Log.i(Constants.TAG, "NotificationJobService processCustomNotificationPosted result: " + result);

                if (result.toLowerCase().contains("ok")) {
                    Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted OK");
                    NotificationStore.removeCustomNotification(key);
                    NotificationStore.removeNotificationBundle(key);
                    jobFinished(params, false);

                } else {
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        Log.d(Constants.TAG, "NotificationJobService processCustomNotificationPosted try: " + retries);
                        retries++;
                        processCustomNotificationPosted(key, mode);
                    } else {
                        retries = 0;
                        NotificationStore.removeCustomNotification(key);
                        NotificationStore.removeNotificationBundle(key);
                        jobFinished(params, false);
                    }
                }
                return null;
            }
        });
        */
    }

    private void extractImagesFromNotification(Bundle bundle, NotificationData notificationData) {

        if (!Prefs.getBoolean(Constants.PREF_NOTIFICATIONS_DISABLE_LARGE_ICON, false)) {
            extractLargeIcon(bundle, notificationData);
        }

        if (!Prefs.getBoolean(Constants.PREF_NOTIFICATIONS_DISABLE_PICTURE, false)) {
            extractPicture(bundle, notificationData);
        }
    }

    private void extractLargeIcon(Bundle bundle, NotificationData notificationData) {
        try {
            Bitmap largeIcon = (Bitmap) bundle.get("android.largeIcon");
            if (largeIcon != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                largeIcon.compress(Bitmap.CompressFormat.PNG, 80, stream);
                byte[] byteArray = stream.toByteArray();

                notificationData.setLargeIcon(byteArray);
                notificationData.setLargeIconWidth(largeIcon.getWidth());
                notificationData.setLargeIconHeight(largeIcon.getHeight());
            }
        } catch (Exception exception) {
            Log.e(Constants.TAG, exception.getMessage(), exception);
        }
    }

    private void extractPicture(Bundle bundle, NotificationData notificationData) {
        try {
            Bitmap originalBitmap = (Bitmap) bundle.get("android.picture");
            if (originalBitmap != null) {
                Bitmap scaledBitmap = scaleBitmap(originalBitmap);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 80, stream);
                byte[] byteArray = stream.toByteArray();

                notificationData.setPicture(byteArray);
                notificationData.setPictureWidth(scaledBitmap.getWidth());
                notificationData.setPictureHeight(scaledBitmap.getHeight());
            }
        } catch (Exception exception) {
            Log.e(Constants.TAG, exception.getMessage(), exception);
        }
    }

    private Bitmap scaleBitmap(Bitmap bitmap) {
        if (bitmap.getWidth() <= 320) {
            return bitmap;
        }

        float horizontalScaleFactor = bitmap.getWidth() / 320f;
        float destHeight = bitmap.getHeight() / horizontalScaleFactor;

        return Bitmap.createScaledBitmap(bitmap, 320, (int) destHeight, false);
    }


    //Send CustomUI without scheduling
    public static void sendCustomNotification(final Context context, final NotificationData notificationData) {

        Log.d(Constants.TAG, "NotificationJobService sendCustomNotification key: " + notificationData.getKey());

        //final Transporter transporter2 = TransporterClassic.get(context, Transport.NAME_NOTIFICATION);

        if (transporterNotifications.isTransportServiceConnected()) {
            Log.i(Constants.TAG,"NotificationJobService sendCustomNotification isTransportServiceConnected: true");
        } else {
            Log.w(Constants.TAG,"NotificationJobService sendCustomNotification isTransportServiceConnected = false, connecting...");
            transporterNotifications.connectTransportService();
            AmazModApplication.isWatchConnected = false;
        }

        boolean isTransportConnected = transporterNotifications.isTransportServiceConnected();
        result = null;
        if (!isTransportConnected) {
            if (AmazModApplication.isWatchConnected || (EventBus.getDefault().getStickyEvent(IsWatchConnectedLocal.class) == null)) {
                AmazModApplication.isWatchConnected = false;
                EventBus.getDefault().removeAllStickyEvents();
                EventBus.getDefault().postSticky(new IsWatchConnectedLocal(AmazModApplication.isWatchConnected));
            }
            Log.w(Constants.TAG, "NotificationJobService sendCustomNotification isTransportConnected: false");
        }

        Log.i(Constants.TAG, "NotificationJobService sendCustomNotification transporterNotifications.isAvailable: " + transporterNotifications.isAvailable());

        DataBundle dataBundle = new DataBundle();
        notificationData.toDataBundle(dataBundle);
        transporterNotifications.send(Transport.INCOMING_NOTIFICATION, dataBundle, new Transporter.DataSendResultCallback() {
            @Override
            public void onResultBack(DataTransportResult dataTransportResult) {
                result = dataTransportResult.toString();
                Log.i(Constants.TAG, "NotificationJobService sendCustomNotification send result: " + result);
                transporterNotifications.disconnectTransportService();

                if (result.toLowerCase().contains("ok")) {
                    Log.d(Constants.TAG, "NotificationJobService sendCustomNotification OK");

                } else {
                    Log.d(Constants.TAG, "NotificationJobService sendCustomNotification try: " + retries);
                    if (AmazModApplication.isWatchConnected && retries < 4) {
                        retries++;
                        sendCustomNotification(context, notificationData);
                    } else {
                        Log.d(Constants.TAG, "NotificationJobService sendCustomNotification finishing…");
                        retries = 0;
                    }
                }
            }
        });
    }

}