
package com.mirrororo.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.webrtc.*;

import java.net.URI;
import java.util.Collections;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String TAG = "Mirrororo";
    private static final String CHANNEL = "mirrororo_capture";
    private static final String ROOM = "MIRRORORO";
    private static final String SERVER =
            "wss://mirrororo.onrender.com";

    private WebSocketClient socket;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private EglBase egl;
    private ScreenCapturerAndroid capturer;
    private SurfaceTextureHelper textureHelper;
    private VideoSource videoSource;
    private VideoTrack videoTrack;

    private boolean stopped = false;
    private boolean offerPending = false;

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
                            .setContentTitle("Mirrororo")
                            .setContentText("Starting screen sharing")
                            .setSmallIcon(
                                    android.R.drawable.ic_menu_view)
                            .build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        1,
                        notification,
                        ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }

            if (intent == null) {
                showError("Service received no screen data");
                stopSelf();
                return START_NOT_STICKY;
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
                showError("Screen permission data is missing");
                stopSelf();
                return START_NOT_STICKY;
            }

            startCapture(resultCode, resultData);

        } catch (Exception e) {
            Log.e(TAG, "Service startup failed", e);
            showError("Startup failed: " + e.getMessage());
            stopSelf();
        } catch (Error e) {
            Log.e(TAG, "Fatal WebRTC error", e);
            showError("Fatal error: " + e.getClass().getSimpleName());
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startCapture(
            int resultCode, Intent resultData) throws Exception {

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(getApplicationContext())
                        .createInitializationOptions());

        egl = EglBase.create();

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(
                                egl.getEglBaseContext(),
                                true,
                                true))
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(
                                egl.getEglBaseContext()))
                .createPeerConnectionFactory();

        videoSource = factory.createVideoSource(true);

        textureHelper = SurfaceTextureHelper.create(
                "MirrororoCapture",
                egl.getEglBaseContext());

        if (textureHelper == null) {
            throw new IllegalStateException(
                    "Could not create screen texture");
        }

        capturer = new ScreenCapturerAndroid(
                resultData,
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.i(TAG, "Screen capture stopped");
                        stopSelf();
                    }
                });

        capturer.initialize(
                textureHelper,
                getApplicationContext(),
                videoSource.getCapturerObserver());

        capturer.startCapture(1280, 720, 15);

        videoTrack = factory.createVideoTrack(
                "mirrororo_screen", videoSource);

        PeerConnection.RTCConfiguration config =
                new PeerConnection.RTCConfiguration(
                        Collections.singletonList(
                                PeerConnection.IceServer
                                        .builder(
                                          "stun:stun.l.google.com:19302")
                                        .createIceServer()));

        peer = factory.createPeerConnection(
                config,
                new PeerConnection.Observer() {

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "candidate");
                    msg.put("candidate", candidate.sdp);
                    msg.put("sdpMid", candidate.sdpMid);
                    msg.put("sdpMLineIndex",
                            candidate.sdpMLineIndex);
                    sendMessage(msg);
                } catch (Exception e) {
                    Log.e(TAG, "ICE candidate error", e);
                }
            }

            @Override
            public void onSignalingChange(
                    PeerConnection.SignalingState state) {}

            @Override
            public void onIceConnectionChange(
                    PeerConnection.IceConnectionState state) {
                Log.i(TAG, "ICE state: " + state);
            }

            @Override
            public void onIceConnectionReceivingChange(
                    boolean receiving) {}

            @Override
            public void onIceGatheringChange(
                    PeerConnection.IceGatheringState state) {}

            @Override
            public void onIceCandidatesRemoved(
                    IceCandidate[] candidates) {}

            @Override
            public void onAddStream(MediaStream stream) {}

            @Override
            public void onRemoveStream(MediaStream stream) {}

            @Override
            public void onDataChannel(DataChannel channel) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(
                    RtpReceiver receiver, MediaStream[] streams) {}
        });

        if (peer == null) {
            throw new IllegalStateException(
                    "Could not create WebRTC connection");
        }

        peer.addTrack(
                videoTrack,
                Collections.singletonList("mirrororo_stream"));

        connectServer();
    }

    private void connectServer() throws Exception {
        socket = new WebSocketClient(new URI(SERVER)) {

            @Override
            public void onOpen(ServerHandshake handshake) {
                try {
                    JSONObject join = new JSONObject();
                    join.put("type", "join");
                    join.put("room", ROOM);
                    send(join.toString());
                    Log.i(TAG, "Joined room " + ROOM);
                } catch (Exception e) {
                    Log.e(TAG, "Room join error", e);
                    showError("Could not join room");
                }
            }

            @Override
            public void onMessage(String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String type = msg.optString("type");

                    if ("peer-joined".equals(type)) {
                        createOffer();

                    } else if ("offer".equals(type)) {
                        acceptOffer(msg);

                    } else if ("answer".equals(type)) {
                        SessionDescription answer =
                                new SessionDescription(
                                        SessionDescription.Type.ANSWER,
                                        msg.getString("sdp"));

                        peer.setRemoteDescription(
                                new SdpObserverAdapter(), answer);

                    } else if ("candidate".equals(type)) {
                        String candidateText =
                                msg.optString("candidate", "");

                        if (!candidateText.isEmpty()
                                && peer != null) {
                            peer.addIceCandidate(
                                    new IceCandidate(
                                            msg.optString(
                                                    "sdpMid", "0"),
                                            msg.optInt(
                                                    "sdpMLineIndex", 0),
                                            candidateText));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Signaling message error", e);
                    showError("Connection message error");
                }
            }

            @Override
            public void onClose(
                    int code, String reason, boolean remote) {
                Log.i(TAG, "Socket closed: " + reason);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "WebSocket error", e);
                showError("Server connection failed");
            }
        };

        socket.connect();
    }

    private void createOffer() {
        if (peer == null || offerPending || stopped) return;

        offerPending = true;

        peer.createOffer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(
                    SessionDescription offer) {

                peer.setLocalDescription(
                        new SdpObserverAdapter() {
                    @Override
                    public void onSetSuccess() {
                        try {
                            JSONObject msg = new JSONObject();
                            msg.put("type", "offer");
                            msg.put("sdp", offer.description);
                            sendMessage(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Offer send error", e);
                            showError("Could not send video offer");
                        }
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Local SDP error: " + error);
                        showError("WebRTC setup failed");
                    }
                }, offer);
            }

            @Override
            public void onCreateFailure(String error) {
                offerPending = false;
                Log.e(TAG, "Offer creation failed: " + error);
                showError("Could not create video offer");
            }
        }, new MediaConstraints());
    }

    private void acceptOffer(JSONObject msg) throws Exception {
        SessionDescription offer =
                new SessionDescription(
                        SessionDescription.Type.OFFER,
                        msg.getString("sdp"));

        peer.setRemoteDescription(new SdpObserverAdapter() {
            @Override
            public void onSetSuccess() {
                peer.createAnswer(new SdpObserverAdapter() {
                    @Override
                    public void onCreateSuccess(
                            SessionDescription answer) {

                        peer.setLocalDescription(
                                new SdpObserverAdapter() {
                            @Override
                            public void onSetSuccess() {
                                try {
                                    JSONObject response =
                                            new JSONObject();
                                    response.put("type", "answer");
                                    response.put(
                                            "sdp", answer.description);
                                    sendMessage(response);
                                } catch (Exception e) {
                                    Log.e(TAG, "Answer send error", e);
                                }
                            }
                        }, answer);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        Log.e(TAG, "Answer creation failed: " + error);
                        showError("Could not answer video offer");
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Remote SDP error: " + error);
                showError("Could not accept video offer");
            }
        }, offer);
    }

    private void sendMessage(JSONObject msg) {
        WebSocketClient current = socket;

        if (current != null && current.isOpen()) {
            current.send(msg.toString());
        } else {
            Log.w(TAG, "Message not sent: server is disconnected");
        }
    }

    private void showError(String message) {
        Log.e(TAG, message);

        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(
                        getApplicationContext(),
                        "Mirrororo: " + message,
                        Toast.LENGTH_LONG).show());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            "Mirrororo screen sharing",
                            NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopped = true;

        if (socket != null) {
            socket.close();
            socket = null;
        }

        if (capturer != null) {
            try {
                capturer.stopCapture();
                capturer.dispose();
            } catch (Exception e) {
                Log.e(TAG, "Capture cleanup error", e);
            }
            capturer = null;
        }

        if (peer != null) {
            peer.close();
            peer.dispose();
            peer = null;
        }

        if (videoTrack != null) videoTrack.dispose();
        if (videoSource != null) videoSource.dispose();
        if (textureHelper != null) textureHelper.dispose();
        if (factory != null) factory.dispose();
        if (egl != null) egl.release();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class SdpObserverAdapter
            implements SdpObserver {

        @Override
        public void onCreateSuccess(
                SessionDescription description) {}

        @Override
        public void onSetSuccess() {}

        @Override
        public void onCreateFailure(String error) {}

        @Override
        public void onSetFailure(String error) {}
    }
}
