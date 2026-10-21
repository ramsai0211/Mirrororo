
package com.mirrororo.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String TAG = "MirrororoTest";
    private static final String CHANNEL = "mirrororo_capture";

    private MediaProjection projection;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Mirrororo Screen Test",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(
            Intent intent, int flags, int startId) {

        try {
            Notification notification =
                    new Notification.Builder(this, CHANNEL)
                            .setContentTitle("Mirrororo test")
                            .setContentText(
                                    "Screen capture service is running")
                            .setSmallIcon(
                                    android.R.drawable.ic_menu_view)
                            .build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(1, notification);
            }

            if (intent == null) {
                throw new IllegalStateException("Missing intent");
            }

            int resultCode = intent.getIntExtra(
                    EXTRA_RESULT_CODE, 0);

            Intent resultData;

            if (Build.VERSION.SDK_INT >= 33) {
                resultData = intent.getParcelableExtra(
                        EXTRA_RESULT_DATA, Intent.class);
            } else {
                resultData = intent.getParcelableExtra(
                        EXTRA_RESULT_DATA);
            }

            if (resultData == null) {
                throw new IllegalStateException(
                        "Missing screen permission data");
            }

            MediaProjectionManagerHelper.start(
                    this, resultCode, resultData);

        } catch (Exception e) {
            Log.e(TAG, "Screen test failed", e);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (projection != null) {
            projection.stop();
            projection = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class MediaProjectionManagerHelper {

        static void start(
                ScreenCaptureService service,
                int resultCode,
                Intent resultData) {

            android.media.projection.MediaProjectionManager manager =
                    (android.media.projection.MediaProjectionManager)
                            service.getSystemService(
                                    MEDIA_PROJECTION_SERVICE);

            service.projection =
                    manager.getMediaProjection(resultCode, resultData);

            if (service.projection == null) {
                throw new IllegalStateException(
                        "Android did not create screen capture");
            }

            Log.i(TAG, "Screen capture started without WebRTC");
        }
    }
}
