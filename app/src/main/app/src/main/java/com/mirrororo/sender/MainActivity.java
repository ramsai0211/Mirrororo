
package com.mirrororo.sender;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 501;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(28, 28, 28, 28);

        status = new TextView(this);
        status.setTextSize(20);
        status.setText(
                "Mirrororo Sender\n\n" +
                "Room code: MIRRORORO\n\n" +
                "Tap below to allow screen capture."
        );

        Button start = new Button(this);
        start.setText("Start screen sharing");
        start.setOnClickListener(v -> requestScreenCapture());

        layout.addView(status);
        layout.addView(start);
        setContentView(layout);
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                        MEDIA_PROJECTION_SERVICE);

        startActivityForResult(
                manager.createScreenCaptureIntent(),
                CAPTURE_REQUEST
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != CAPTURE_REQUEST) return;

        if (resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(
                    this, ScreenCaptureService.class);

            service.putExtra(
                    ScreenCaptureService.EXTRA_RESULT_CODE,
                    resultCode);

            service.putExtra(
                    ScreenCaptureService.EXTRA_RESULT_DATA,
                    data);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }

            status.setText(
                    "Screen permission granted.\n" +
                    "Streaming connection is still being built."
            );
        } else {
            status.setText("Screen capture permission was denied.");
        }
    }
}
