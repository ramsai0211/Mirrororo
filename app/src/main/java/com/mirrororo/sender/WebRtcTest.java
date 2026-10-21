
package com.mirrororo.sender;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;

public class WebRtcTest extends Activity {

    private static final String TAG = "MirrororoWebRTC";
    private EglBase eglBase;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 24, 24, 24);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        status.setText("WebRTC native-library test");

        Button test = new Button(this);
        test.setText("Test WebRTC");
        test.setOnClickListener(v -> testWebRtc());

        layout.addView(status);
        layout.addView(test);
        setContentView(layout);
    }

    private void testWebRtc() {
        status.setText("Initializing WebRTC...");
        Log.i(TAG, "Starting WebRTC test");

        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(getApplicationContext())
                    .createInitializationOptions()
            );

            eglBase = EglBase.create();

            PeerConnectionFactory factory =
                PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(
                            eglBase.getEglBaseContext(),
                            true,
                            true
                        )
                    )
                    .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(
                            eglBase.getEglBaseContext()
                        )
                    )
                    .createPeerConnectionFactory();

            if (factory == null) {
                status.setText("FAILED: factory is null");
                return;
            }

            status.setText(
                "WebRTC initialized successfully!"
            );

            Log.i(TAG, "WebRTC initialization succeeded");

            factory.dispose();
            eglBase.release();
            eglBase = null;

        } catch (Exception e) {
            Log.e(TAG, "WebRTC test failed", e);
            status.setText(
                "WebRTC failed: " + e.getClass().getSimpleName()
            );
        }
    }
}
