
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.webrtc.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String TAG = "MirrororoSender";
    private static final String CHANNEL = "mirrororo_capture";
    private static final String ROOM = "MIRRORORO";
    private static final String SERVER =
            "wss://mirrororo.onrender.com";

    private WebSocketClient socket;
    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private ScreenCapturerAndroid capturer;
    private SurfaceTextureHelper textureHelper;
    private VideoSource videoSource;
    private VideoTrack videoTrack;

    private final List<IceCandidate> pendingCandidates =
            new ArrayList<>();

    private boolean remoteDescriptionSet = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notification notification =
                    new Notification.Builder(this, CHANNEL)
                            .setContentTitle("Mirrororo")
                            .setContentText("Connecting to your TV...")
                            .setSmallIcon(android.R.drawable.ic_menu_view)
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
                throw new IllegalStateException("Missing service intent");
            }

            int resultCode = intent.getIntExtra(
                    EXTRA_RESULT_CODE, 0);

            Intent permissionData;

            if (Build.VERSION.SDK_INT >= 33) {
                permissionData = intent.getParcelableExtra(
                        EXTRA_RESULT_DATA, Intent.class);
            } else {
                permissionData = intent.getParcelableExtra(
                        EXTRA_RESULT_DATA);
            }

            if (resultCode != android.app.Activity.RESULT_OK
                    || permissionData == null) {
                throw new IllegalStateException(
                        "Screen permission is missing");
            }

            startWebRtc(permissionData);

        } catch (Exception e) {
            Log.e(TAG, "Unable to start sender", e);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startWebRtc(Intent permissionData) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(getApplicationContext())
                        .createInitializationOptions()
        );

        eglBase = EglBase.create();

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(
                                eglBase.getEglBaseContext(),
                                true,
                                true))
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(
                                eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        if (factory == null) {
            throw new IllegalStateException(
                    "WebRTC factory creation failed");
        }

        PeerConnection.RTCConfiguration config =
                new PeerConnection.RTCConfiguration(
                        Collections.singletonList(
                                PeerConnection.IceServer
                                        .builder("stun:stun.l.google.com:19302")
                                        .createIceServer()
                        )
                );

        peer = factory.createPeerConnection(
                config,
                new PeerConnection.Observer() {
                    @Override
                    public void onSignalingChange(
                            PeerConnection.SignalingState state) {
                        Log.d(TAG, "Signaling: " + state);
                    }

                    @Override
                    public void onIceConnectionChange(
                            PeerConnection.IceConnectionState state) {
                        Log.d(TAG, "ICE: " + state);
                    }

                    @Override
                    public void onIceConnectionReceivingChange(
                            boolean receiving) {}

                    @Override
                    public void onIceGatheringChange(
                            PeerConnection.IceGatheringState state) {
                        Log.d(TAG, "ICE gathering: " + state);
                    }

                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        sendCandidate(candidate);
                    }

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
                            RtpReceiver receiver,
                            MediaStream[] streams) {}
                }
        );

        if (peer == null) {
            throw new IllegalStateException(
                    "WebRTC peer creation failed");
        }

        videoSource = factory.createVideoSource(true);

        textureHelper = SurfaceTextureHelper.create(
                "MirrororoCapture",
                eglBase.getEglBaseContext());

        capturer = new ScreenCapturerAndroid(
                permissionData,
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

        videoTrack = factory.createVideoTrack(
                "mirrororo-video",
                videoSource);

        videoTrack.setEnabled(true);

        peer.addTrack(
                videoTrack,
                Collections.singletonList("mirrororo-stream"));

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm =
                (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int width = Math.max(2, metrics.widthPixels / 2);
        int height = Math.max(2, metrics.heightPixels / 2);

        capturer.startCapture(width, height, 15);

        connectSignaling();
    }

    private void connectSignaling() {
        try {
            socket = new WebSocketClient(new URI(SERVER)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    try {
                        JSONObject join = new JSONObject();
                        join.put("type", "join");
                        join.put("room", ROOM);
                        send(join.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Join failed", e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject data = new JSONObject(message);
                        String type = data.optString("type");

                        if ("joined".equals(type)) {
                            Log.i(TAG, "Joined room " + ROOM);
                            createOffer();

                        } else if ("answer".equals(type)) {
                            String sdp = data.getString("sdp");

                            peer.setRemoteDescription(
                                    new SimpleSdpObserver() {
                                        @Override
                                        public void onSetSuccess() {
                                            remoteDescriptionSet = true;
                                            flushCandidates();
                                            Log.i(TAG,
                                                    "TV answer accepted");
                                        }

                                        @Override
                                        public void onSetFailure(
                                                String error) {
                                            Log.e(TAG,
                                                    "Answer failed: " + error);
                                        }
                                    },
                                    new SessionDescription(
                                            SessionDescription.Type.ANSWER,
                                            sdp));

                        } else if ("candidate".equals(type)) {
                            IceCandidate candidate =
                                    new IceCandidate(
                                            data.optString("sdpMid", null),
                                            data.optInt("sdpMLineIndex", 0),
                                            data.getString("candidate"));

                            if (remoteDescriptionSet) {
                                peer.addIceCandidate(candidate);
                            } else {
                                pendingCandidates.add(candidate);
                            }
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Signaling message failed", e);
                    }
                }

                @Override
                public void onClose(
                        int code, String reason, boolean remote) {
                    Log.i(TAG, "Socket closed: " + reason);
                }

                @Override
                public void onError(Exception error) {
                    Log.e(TAG, "Socket error", error);
                }
            };

            socket.connect();

        } catch (Exception e) {
            Log.e(TAG, "Could not connect signaling", e);
            stopSelf();
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();

        peer.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                peer.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        try {
                            JSONObject message = new JSONObject();
                            message.put("type", "offer");
                            message.put("sdp", offer.description);
                            sendMessage(message);
                            Log.i(TAG, "Offer sent to TV");
                        } catch (Exception e) {
                            Log.e(TAG, "Offer send failed", e);
                        }
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Local SDP failed: " + error);
                    }
                }, offer);
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Offer creation failed: " + error);
            }
        }, constraints);
    }

    private void sendCandidate(IceCandidate candidate) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "candidate");
            message.put("candidate", candidate.sdp);
            message.put("sdpMid", candidate.sdpMid);
            message.put("sdpMLineIndex", candidate.sdpMLineIndex);
            sendMessage(message);
        } catch (Exception e) {
            Log.e(TAG, "Candidate send failed", e);
        }
    }

    private void sendMessage(JSONObject message) {
        WebSocketClient current = socket;
        if (current != null && current.isOpen()) {
            current.send(message.toString());
        }
    }

    private void flushCandidates() {
        for (IceCandidate candidate : pendingCandidates) {
            peer.addIceCandidate(candidate);
        }
        pendingCandidates.clear();
    }

    private abstract static class SimpleSdpObserver
            implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription sdp) {}

        @Override
        public void onSetSuccess() {}

        @Override
        public void onCreateFailure(String error) {
            Log.e(TAG, "SDP create error: " + error);
        }

        @Override
        public void onSetFailure(String error) {
            Log.e(TAG, "SDP set error: " + error);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            "Mirrororo Screen Sharing",
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
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }

            if (capturer != null) {
                capturer.stopCapture();
                capturer.dispose();
                capturer = null;
            }

            if (videoTrack != null) {
                videoTrack.dispose();
                videoTrack = null;
            }

            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }

            if (textureHelper != null) {
                textureHelper.dispose();
                textureHelper = null;
            }

            if (peer != null) {
                peer.close();
                peer.dispose();
                peer = null;
            }

            if (factory != null) {
                factory.dispose();
                factory = null;
            }

            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Cleanup error", e);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
