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

        TextView title = new TextView(this);
        title.setText("MIRRORORO");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        status = new TextView(this);
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setText(
                "\nRoom code: MIRRORORO\n\n" +
                "Connect your TV receiver first."
        );

        Button start = new Button(this);
        start.setText("Start screen sharing");
        start.setOnClickListener(v -> requestCapture());

        layout.addView(title);
        layout.addView(status);
        layout.addView(start);

        setContentView(layout);
    }

    private void requestCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                        MEDIA_PROJECTION_SERVICE);

        if (manager != null) {
            startActivityForResult(
                    manager.createScreenCaptureIntent(),
                    CAPTURE_REQUEST);
        } else {
            status.setText("Screen capture is unavailable.");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != CAPTURE_REQUEST) {
            return;
        }

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
                    "Starting Mirrororo service...");
        } else {
            status.setText("Screen sharing permission denied.");
        }
    }
}
