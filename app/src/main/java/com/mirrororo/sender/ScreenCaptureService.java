
package com.mirrororo.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String TAG = "MirrororoTest";
    private static final String CHANNEL = "mirrororo_capture";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjection projection;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            if (intent == null) {
                throw new IllegalStateException(
                        "Service intent is missing");
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
                        "Screen permission data is missing");
            }

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE);

            if (manager == null) {
                throw new IllegalStateException(
                        "Screen capture manager unavailable");
            }

            projection = manager.getMediaProjection(
                    resultCode, resultData);

            if (projection == null) {
                throw new IllegalStateException(
                        "Android did not create screen capture");
            }

            projection.registerCallback(
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            Log.i(TAG, "Screen capture stopped");
                            stopSelf();
                        }
                    },
                    new Handler(Looper.getMainLooper())
            );

            Log.i(TAG,
                    "Screen capture started without WebRTC");

        } catch (Exception e) {
            Log.e(TAG, "Screen test failed", e);

            String message = e.getClass().getSimpleName()
                    + ": " + e.getMessage();

            showError(message);
            stopSelf();

        } catch (Error e) {
            Log.e(TAG, "Fatal screen capture error", e);

            showError("Fatal error: "
                    + e.getClass().getSimpleName());
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void showError(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(
                        getApplicationContext(),
                        "Mirrororo: " + message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            "Mirrororo Screen Test",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
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
}
