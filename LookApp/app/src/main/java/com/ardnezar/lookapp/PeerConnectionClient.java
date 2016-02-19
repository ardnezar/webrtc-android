/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.ardnezar.lookapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.ardnezar.lookapp.activities.LookAppLauncherActivity;
import com.ardnezar.lookapp.activities.LookAppMainActivity;
import com.ardnezar.lookapp.util.LooperExecutor;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaCodecVideoEncoder;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaConstraints.KeyValuePair;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Peer connection client implementation.
 *
 * <p>All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
public class PeerConnectionClient {
	public static final String VIDEO_TRACK_ID = "ARDAMSv0";
	public static final String AUDIO_TRACK_ID = "ARDAMSa0";
	private static final String TAG = "PCRTCClient";
	private static final String FIELD_TRIAL_AUTOMATIC_RESIZE =
			"WebRTC-MediaCodecVideoEncoder-AutomaticResize/Enabled/";
	private static final String VIDEO_CODEC_VP8 = "VP8";
	private static final String VIDEO_CODEC_VP9 = "VP9";
	private static final String VIDEO_CODEC_H264 = "H264";
	private static final String AUDIO_CODEC_OPUS = "opus";
	private static final String AUDIO_CODEC_ISAC = "ISAC";
	private static final String VIDEO_CODEC_PARAM_START_BITRATE =
			"x-google-start-bitrate";
	private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
	private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
	private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT= "googAutoGainControl";
	private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter";
	private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
	private static final String MAX_VIDEO_WIDTH_CONSTRAINT = "maxWidth";
	private static final String MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth";
	private static final String MAX_VIDEO_HEIGHT_CONSTRAINT = "maxHeight";
	private static final String MIN_VIDEO_HEIGHT_CONSTRAINT = "minHeight";
	private static final String MAX_VIDEO_FPS_CONSTRAINT = "maxFrameRate";
	private static final String MIN_VIDEO_FPS_CONSTRAINT = "minFrameRate";
	private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
	private static final int HD_VIDEO_WIDTH = 1280;
	private static final int HD_VIDEO_HEIGHT = 720;
	private static final int MAX_VIDEO_WIDTH = 1280;
	private static final int MAX_VIDEO_HEIGHT = 1280;
	private static final int MAX_VIDEO_FPS = 30;

	private static final PeerConnectionClient instance = new PeerConnectionClient();
	private final LooperExecutor executor;

	private PeerConnectionFactory factory;
	private PeerConnection peerConnection;
	PeerConnectionFactory.Options options = null;
	private VideoSource videoSource;
	private boolean videoCallEnabled;
	private boolean preferIsac;
	private String preferredVideoCodec;
	private boolean videoSourceStopped;
	private boolean isError;
	private Timer statsTimer;
	private VideoRenderer.Callbacks localRender;
	private VideoRenderer.Callbacks remoteRender;
	private AppRTCClient.SignalingParameters signalingParameters;
//	private MediaConstraints pcConstraints;
	private MediaConstraints videoConstraints;
	private MediaConstraints audioConstraints;
	private ParcelFileDescriptor aecDumpFileDescriptor;
	private MediaConstraints sdpMediaConstraints;
	private PeerConnectionParameters peerConnectionParameters;
	// Queued remote ICE candidates are consumed only after both local and
	// remote descriptions are set. Similarly local ICE candidates are sent to
	// remote peer after both local and remote description are set.
	private LinkedList<IceCandidate> queuedRemoteCandidates;
	private PeerConnectionEvents events;
	private boolean isInitiator;
	private SessionDescription localSdp; // either offer or answer SDP
	private MediaStream mediaStream;
	private int numberOfCameras;
	private VideoCapturerAndroid videoCapturer;
	// enableVideo is set to true if video should be rendered and sent.
	private boolean renderVideo;
	private VideoTrack localVideoTrack;
	private VideoTrack remoteVideoTrack;

	//Socket client
	private Socket client;
	private Socket client2;
	private HashMap<String, Peer> peers = new HashMap<>();
	private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
	private final static int MAX_PEER = 1;
	private boolean[] endPoints = new boolean[MAX_PEER];
	private Manager manager;

	private String mSessionId;

	private Context mContext;

	private String mId;

//	private RtcListener mListener;

//	/**
//	 * Implement this interface to be notified of events.
//	 */
//	public interface RtcListener{
//		void onCallReady(String callId);
//
//		void onStatusChanged(String newStatus);
//
//		void onLocalStream(MediaStream localStream);
//
//		void onAddRemoteStream(MediaStream remoteStream, int endPoint);
//
//		void onRemoveRemoteStream(int endPoint);
//	}



	/**
	 * Peer connection parameters.
	 */
	public static class PeerConnectionParameters {
		public final boolean videoCallEnabled;
		public final boolean loopback;
		public final boolean tracing;
		public final int videoWidth;
		public final int videoHeight;
		public final int videoFps;
		public final int videoStartBitrate;
		public final String videoCodec;
		public final boolean videoCodecHwAcceleration;
		public final boolean captureToTexture;
		public final int audioStartBitrate;
		public final String audioCodec;
		public final boolean noAudioProcessing;
		public final boolean aecDump;
		public final boolean useOpenSLES;

		public PeerConnectionParameters(
				boolean videoCallEnabled, boolean loopback, boolean tracing,
				int videoWidth, int videoHeight, int videoFps, int videoStartBitrate,
				String videoCodec, boolean videoCodecHwAcceleration, boolean captureToTexture,
				int audioStartBitrate, String audioCodec,
				boolean noAudioProcessing, boolean aecDump, boolean useOpenSLES) {
			this.videoCallEnabled = videoCallEnabled;
			this.loopback = loopback;
			this.tracing = tracing;
			this.videoWidth = videoWidth;
			this.videoHeight = videoHeight;
			this.videoFps = videoFps;
			this.videoStartBitrate = videoStartBitrate;
			this.videoCodec = videoCodec;
			this.videoCodecHwAcceleration = videoCodecHwAcceleration;
			this.captureToTexture = captureToTexture;
			this.audioStartBitrate = audioStartBitrate;
			this.audioCodec = audioCodec;
			this.noAudioProcessing = noAudioProcessing;
			this.aecDump = aecDump;
			this.useOpenSLES = useOpenSLES;
		}
	}

	/**
	 * Peer connection events.
	 */
	public static interface PeerConnectionEvents {
		/**
		 * Callback fired once local SDP is created and set.
		 */
		public void onLocalDescription(final SessionDescription sdp);

		/**
		 * Callback fired once local Ice candidate is generated.
		 */
		public void onIceCandidate(final IceCandidate candidate);

		/**
		 * Callback fired once connection is established (IceConnectionState is
		 * CONNECTED).
		 */
		public void onIceConnected();

		/**
		 * Callback fired once connection is closed (IceConnectionState is
		 * DISCONNECTED).
		 */
		public void onIceDisconnected();

		/**
		 * Callback fired once peer connection is closed.
		 */
		public void onPeerConnectionClosed();

		/**
		 * Callback fired once peer connection statistics is ready.
		 */
//		public void onPeerConnectionStatsReady(final StatsReport[] reports);

		/**
		 * Callback fired once peer connection error happened.
		 */
		public void onPeerConnectionError(final String description);
	}

	private PeerConnectionClient() {
		executor = new LooperExecutor();
		// Looper thread is started once in private ctor and is used for all
		// peer connection API calls to ensure new peer connection factory is
		// created on the same thread as previously destroyed factory.
		executor.requestStart();
	}

	public static PeerConnectionClient getInstance() {
		return instance;
	}

	public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
		this.options = options;
	}

	private MessageHandler messageHandler;

	public void createPeerConnectionFactory(
			final Context context,
			final String host,
			String id) {

		mContext = context;
		mId = id;
		// Reset variables to initial states.
		factory = null;
		peerConnection = null;
		preferIsac = false;
		videoSourceStopped = false;
		isError = false;
		queuedRemoteCandidates = null;
		localSdp = null; // either offer or answer SDP
		mediaStream = null;
		videoCapturer = null;
		renderVideo = true;
		localVideoTrack = null;
		remoteVideoTrack = null;
		statsTimer = new Timer();
		messageHandler = new MessageHandler();
		PreferenceManager.getDefaultSharedPreferences(mContext).edit().
				putString(LookAppLauncherActivity.LOOK_SESSION_ID, "").apply();



		iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

		mHost = host;

		executor.execute(new Runnable() {
			@Override
			public void run() {
				createPeerConnectionFactoryInternal(context, host);
			}
		});
	}

	private String mHost;


		/*
	 * Message Types:
	 * 1. INIT - Server --> Client to send current sessionId
	 * 2. INIT-REPLY - Client --> Server sends this message along with its UserId (#Phone number) and current sessionId
	 * 		sent in the previous INIT message
	 * 3. PRESENCE - Server --> Client When a new client connects, the Server sends this message to all the clients current
	 * 4. READY-TO-CONNECT - Client --> Server sends this message when it wants to connect to another client
	 * 5. MESSAGE - Client <--> Server sends a message to another client via Server.
	 * 				The server sends this message to the designated client.
	 * 6. INVITE - Server-->Client sends this message to a Client if another client has send a READY-TO-CONNECT message
	 * 7. LEAVE - Server sends to all clients when someone leaves the connection
	 *
	 *
	 * Video Communication Protocol:
	 *
	 * Peer A 									-------- INVITE ------> Peer B
	 *
	 *    										<------- READY ---------
	 *
	 * 	(Create Offer and Send it back)			-------- OFFER ------->
	 *
	 *											<-------- ANSWER -----> (Create answer and send it back)
	 *
	 *  (Update remote sdp for the connection)
	 *
	 */

	private static final String INIT_MESSAGE = "INIT";
	private static final String INIT_REPLY_MESSAGE = "INIT-REPLY";
	private static final String PRESENCE_MESSAGE = "PRESENCE";
	private static final String LEAVE_MESSAGE = "LEAVE";
	private static final String TEXT_MESSAGE = "MESSAGE";

	private static final String INVITE_MESSAGE = "INVITE";
	private static final String READY_MESSAGE = "READY-TO-CONNECT";
	private static final String OFFER_MESSAGE = "OFFER";
	private static final String ANSWER_MESSAGE = "ANSWER";

	private static final String FROM_TAG = "from";
	private static final String TYPE_TAG = "type";
	private static final String PAYLOAD_TAG = "payload";
	private static final String LABEL_TAG = "label";
	private static final String ID_TAG = "id";
	private static final String SDP_TAG = "sdp";
	private static final String CANDIDATE_TAG = "candidate";

	private static final String AVAILABLE_USERS_MESSAGE = "AVAILABLE-USERS";


	private static final String ICE_CANDIDATE_MESSAGE = "CANDIDATE";
	private static final String RTC_MESSAGE = "RTC_MESSAGE";

	public void createPeerConnection(
			final EglBase.Context renderEGLContext,
			final VideoRenderer.Callbacks localRender,
			final VideoRenderer.Callbacks remoteRender,
			final PeerConnectionEvents events,
			final PeerConnectionParameters peerConnectionParameters) {
		this.peerConnectionParameters = peerConnectionParameters;
		this.events = events;
		videoCallEnabled = peerConnectionParameters.videoCallEnabled;
//
//		PeerConnectionFactory.initializeAndroidGlobals(, true, true,
//				false);
//		factory = new PeerConnectionFactory();

//		if (peerConnectionParameters == null) {
//			Log.e(TAG, "Creating peer connection without initializing factory.");
//			return;
//		}
		this.localRender = localRender;
		this.remoteRender = remoteRender;

		executor.execute(new Runnable() {
			@Override
			public void run() {
				createMediaConstraintsInternal();
//				createPeerConnectionInternal(renderEGLContext, iceServers);
				if(mediaStream == null) {
					mediaStream = factory.createLocalMediaStream("ARDAMS");
					if (videoCallEnabled) {
						String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
						String frontCameraDeviceName =
								CameraEnumerationAndroid.getNameOfFrontFacingDevice();
						if (numberOfCameras > 1 && frontCameraDeviceName != null) {
							cameraDeviceName = frontCameraDeviceName;
						}
						Log.d(TAG, "Opening camera: " + cameraDeviceName);
						videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null,
								peerConnectionParameters.captureToTexture ? renderEGLContext : null);
						if (videoCapturer == null) {
							reportError("Failed to open camera");
							return;
						}
						mediaStream.addTrack(createVideoTrack(videoCapturer));
					}

					mediaStream.addTrack(factory.createAudioTrack(
							AUDIO_TRACK_ID,
							factory.createAudioSource(audioConstraints)));
				}
				try {
					manager = new Manager(new URI(mHost));
					client = manager.socket("/");
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				client
						.on(INIT_MESSAGE, messageHandler.onInitMessage)
						.on(TEXT_MESSAGE, messageHandler.onTextMessage)
//						.on(INVITE_MESSAGE, messageHandler.onInviteMessage)
//						.on(READY_MESSAGE, messageHandler.onReadyMessage)
//						.on(OFFER_MESSAGE, messageHandler.onOfferMessage)
//						.on(ANSWER_MESSAGE, messageHandler.onAnswerMessage)
//						.on(ICE_CANDIDATE_MESSAGE, messageHandler.onCandidateMessage)
						.on(RTC_MESSAGE, messageHandler.onRtcMessage)
						.on(LEAVE_MESSAGE, messageHandler.onLeaveMessage)
						.on(AVAILABLE_USERS_MESSAGE, messageHandler.onAvailablePeersMessage)
						.on(PRESENCE_MESSAGE, messageHandler.onPresenceMessage);
				client.connect();
			}
		});

	}

	private class CreateOfferCommand implements Command{
		public void execute(String peerId, JSONObject payload) throws JSONException {
			Log.d(TAG, "CreateOfferCommand");
			Peer peer = peers.get(peerId);
			peer.pc.createOffer(peer, sdpMediaConstraints);
		}
	}

	private class CreateAnswerCommand implements Command{
		public void execute(String peerId, JSONObject payload) throws JSONException {
			Log.d(TAG,"CreateAnswerCommand");
			Peer peer = peers.get(peerId);
			SessionDescription sdp = new SessionDescription(
					SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
					payload.getString(SDP_TAG)
			);
			peer.pc.setRemoteDescription(peer, sdp);
			peer.pc.createAnswer(peer, sdpMediaConstraints);
		}
	}

	private class SetRemoteSDPCommand implements Command{
		public void execute(String peerId, JSONObject payload) throws JSONException {
			Log.d(TAG,"SetRemoteSDPCommand");
			Peer peer = peers.get(peerId);
			SessionDescription sdp = new SessionDescription(
					SessionDescription.Type.fromCanonicalForm(payload.getString(TYPE_TAG)),
					payload.getString(SDP_TAG)
			);
			peer.pc.setRemoteDescription(peer, sdp);
		}
	}

	private class AddIceCandidateCommand implements Command{
		public void execute(String peerId, JSONObject payload) throws JSONException {
			Log.d(TAG,"AddIceCandidateCommand");
			PeerConnection pc = peers.get(peerId).pc;
			if (pc.getRemoteDescription() != null) {
				IceCandidate candidate = new IceCandidate(
						payload.getString(ID_TAG),
						payload.getInt(LABEL_TAG),
						payload.getString(CANDIDATE_TAG)
				);
				pc.addIceCandidate(candidate);
			}
		}
	}

	private class MessageHandler {
		private HashMap<String, Command> commandMap;

		private MessageHandler() {
			this.commandMap = new HashMap<>();
			commandMap.put(INIT_MESSAGE, new CreateOfferCommand());
			commandMap.put(INVITE_MESSAGE, new CreateAnswerCommand());
			commandMap.put(PRESENCE_MESSAGE, new SetRemoteSDPCommand());
		}

//		private Emitter.Listener onInviteMessage = new Emitter.Listener() {
//			@Override
//			public void call(Object... args) {
//				Log.d(TAG, "onInviteMessage..");
//
//			}
//		};

		private Emitter.Listener onCandidateMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {

				JSONObject data = (JSONObject) args[0];

				if(data != null && data.length() > 0) {
					Log.d(TAG, "onCandidateMessage..");
					try {
						String from = data.getString("from");;
						new CreateAnswerCommand().execute(from, data);
					} catch (JSONException ex) {
					}
				}
			}
		};

		private Emitter.Listener onPresenceMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				String peerId = (String) args[0];
				Log.d(TAG, "onPresenceMessage..peerId:" + peerId);
				Intent intent = new Intent();
				intent.setAction(LookAppMainActivity.PEER_ADD_ACTION);
				intent.putExtra(LookAppMainActivity.PEER_ID, peerId);
				mContext.sendBroadcast(intent);

				if (peerId != null && !peers.containsKey(peerId) && !peerId.equals(mSessionId)) {
					// if MAX_PEER is reach, ignore the call
					int endPoint = findEndPoint();
					if (endPoint != MAX_PEER) {
						Peer peer = addPeer(peerId, endPoint);
						peer.pc.addStream(mediaStream);
					}
				}
			}
		};

		private Emitter.Listener onLeaveMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				String peerId = (String) args[0];
				Log.d(TAG, "onLeaveMessage..peerId:"+peerId);
				Intent intent = new Intent();
				intent.setAction(LookAppMainActivity.PEER_REMOVE_ACTION);
				intent.putExtra(LookAppMainActivity.PEER_ID, peerId);
				mContext.sendBroadcast(intent);
			}
		};

		private Emitter.Listener onAvailablePeersMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONArray peerIds = (JSONArray) args[0];
				Log.d(TAG, "onAvailablePeersMessage..peers:" + peerIds);
				if(peerIds != null && peerIds.length() > 0) {
					List<String> list = new ArrayList<String>();
					try {
						for (int i = 0; i < peerIds.length(); i++) {
							if(mSessionId != null &&
									peerIds.getString(i) != null &&
									!peerIds.getString(i).equals(mSessionId)) {
								list.add(peerIds.getString(i));
							}
						}
						Log.d(TAG, "onAvailablePeersMessage..peer count:" + peerIds.length());
						Intent intent = new Intent();
						intent.setAction(LookAppMainActivity.PEER_ADD_ACTION);
						intent.putExtra(LookAppMainActivity.PEER_IDS, list.toArray(new String[list.size()]));
						mContext.sendBroadcast(intent);

						for(String pid: list) {

							if (!peers.containsKey(pid)) {
								// if MAX_PEER is reach, ignore the call
								int endPoint = findEndPoint();
								if (endPoint != MAX_PEER) {
									Peer peer = addPeer(pid, endPoint);
									peer.pc.addStream(mediaStream);

									//Inviting the peer for a video session

									JSONObject payload = new JSONObject();
									payload.put(FROM_TAG, mSessionId);
									payload.put(TYPE_TAG, INVITE_MESSAGE);
									sendMessage(pid, RTC_MESSAGE, payload);
								}
							}
						}
					} catch(JSONException ex){
						Log.d(TAG, "onAvailablePeersMessage..exception");
					}
				}
			}
		};

		private Emitter.Listener onRtcMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
//				String message = (String) args[0];
				JSONObject data = (JSONObject) args[0];
				Log.d(TAG, "onRtcMessage.message..data:"+data.toString());
				try {
					String type = data.getString(TYPE_TAG);
					String peerId = data.getString(FROM_TAG);
					if (type.equals(INVITE_MESSAGE)) {
						client.emit(READY_MESSAGE, peerId);
					} else if(type.equals(READY_MESSAGE)) {
						new CreateOfferCommand().execute(peerId, null);
					} else if(type.equals(OFFER_MESSAGE)) {
						new CreateAnswerCommand().execute(peerId, data);
					} else if(type.equals(ANSWER_MESSAGE)) {
						new SetRemoteSDPCommand().execute(peerId, data);
					}

				} catch (JSONException ex){}
			}
		};

		private Emitter.Listener onTextMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
//				String message = (String) args[0];


				JSONObject data = (JSONObject) args[0];
				Log.d(TAG, "onTextMessage.message..data1:"+data.toString());
				try {
					String from = data.getString("from");
					String type = data.getString("type");
					String payload = data.getString("payload");
					if (type.equals("text") && payload != null) {
						Log.d(TAG, "onTextMessage.message:"+payload.toString());
						Intent intent = new Intent();
						intent.setAction(LookAppMainActivity.MESSAGE_RECEIVED_ACTION);
						intent.putExtra(LookAppMainActivity.MESSAGE, payload.toString());
						mContext.sendBroadcast(intent);
					}


				} catch (JSONException ex){}
			}
		};

		private Emitter.Listener onInitMessage = new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				String id = (String) args[0];
				Log.d(TAG, "onCallReady..id:"+id);

				mSessionId = id;

				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
				prefs.edit().putString(LookAppLauncherActivity.LOOK_SESSION_ID, mSessionId).apply();

				//Send INIT-REPLY MESSAGE with current phone number

				client.emit(INIT_REPLY_MESSAGE, prefs.getString(LookAppLauncherActivity.LOOK_APP_ID, null));
				Log.d(TAG, "onCallReady..done");
			}
		};
	}

	private int findEndPoint() {
		for(int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
		return MAX_PEER;
	}

	private class Peer implements SdpObserver, PeerConnection.Observer{
		private PeerConnection pc;
		private String id;
		private int endPoint;

		@Override
		public void onCreateSuccess(final SessionDescription sdp) {
			// TODO: modify sdp to use pcParams prefered codecs
			try {
				JSONObject payload = new JSONObject();
				payload.put("type", sdp.type.canonicalForm());
				payload.put("sdp", sdp.description);
				sendMessage(id, sdp.type.canonicalForm(), payload);
				pc.setLocalDescription(Peer.this, sdp);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onSetSuccess() {}

		@Override
		public void onCreateFailure(String s) {}

		@Override
		public void onSetFailure(String s) {}

		@Override
		public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

		@Override
		public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
			Log.d(TAG, "onIceConnectionChange..iceConnectionState:"+iceConnectionState);
			if(iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
				removePeer(id);
//				mListener.onStatusChanged("DISCONNECTED");
			}
		}

		@Override
		public void onIceConnectionReceivingChange(boolean b) {

		}

		@Override
		public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

		@Override
		public void onIceCandidate(final IceCandidate candidate) {

			try {
				JSONObject payload = new JSONObject();
				payload.put(LABEL_TAG, candidate.sdpMLineIndex);
				payload.put("id", candidate.sdpMid);
				payload.put("candidate", candidate.sdp);
				Log.d(TAG, "onAddStream...candidate:" + payload.toString());
//				sendTextMessage(id, "candidate", payload);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onAddStream(MediaStream mediaStream) {
			Log.d(TAG,"onAddStream "+mediaStream.label());
			// remote streams are displayed from 1 to MAX_PEER (0 is localStream)
//			mListener.onAddRemoteStream(mediaStream, endPoint+1);
		}

		@Override
		public void onRemoveStream(MediaStream mediaStream) {
			Log.d(TAG,"onRemoveStream "+mediaStream.label());
			removePeer(id);
		}

		@Override
		public void onDataChannel(DataChannel dataChannel) {}

		@Override
		public void onRenegotiationNeeded() {

		}

		public Peer(String id, int endPoint) {
			Log.d(TAG,"new Peer: "+id + " " + endPoint);
			this.pc = factory.createPeerConnection(iceServers, sdpMediaConstraints, this);
			this.id = id;
			this.endPoint = endPoint;

			pc.addStream(mediaStream); //, new MediaConstraints()

//			mListener.onStatusChanged("CONNECTING");
		}
	}

	public void sendMessage(String to, String type, JSONObject msg) {
		try {
			JSONObject message = new JSONObject();
			message.put("to", to);
			message.put("type", type);
			message.put("payload", msg);
			client.emit("message", message);
		} catch(JSONException ex){}
	}

	public void sendTextMessage(String to, String type, String msg) {
		Log.d(TAG, "sendTextMessage..to:"+to+",msg:"+msg);
		try {
			JSONObject message = new JSONObject();
			message.put("to", to);
			message.put("type", type);
			message.put("payload", msg);
			client.emit(TEXT_MESSAGE, message);
		} catch(JSONException ex){

		}
	}

	private Peer addPeer(String id, int endPoint) {
		Peer peer = new Peer(id, endPoint);
		peers.put(id, peer);

		endPoints[endPoint] = true;
		return peer;
	}

	private void removePeer(String id) {
		Peer peer = peers.get(id);
//		mListener.onRemoveRemoteStream(peer.endPoint);
		peer.pc.close();
		peers.remove(peer.id);
		endPoints[peer.endPoint] = false;
	}

	private interface Command{
		void execute(String peerId, JSONObject payload) throws JSONException;
	}



	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				closeInternal();
			}
		});
	}

	public boolean isVideoCallEnabled() {
		return videoCallEnabled;
	}

	private void createPeerConnectionFactoryInternal(Context context, String host) {
		PeerConnectionFactory.initializeInternalTracer();
		if (peerConnectionParameters.tracing) {
			PeerConnectionFactory.startInternalTracingCapture(
					Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
							+ "webrtc-trace.txt");
		}
		Log.d(TAG, "Create peer connection factory. Use video: " +
				peerConnectionParameters.videoCallEnabled);
		isError = false;

		// Initialize field trials.
		PeerConnectionFactory.initializeFieldTrials(FIELD_TRIAL_AUTOMATIC_RESIZE);

		// Check preferred video codec.
		preferredVideoCodec = VIDEO_CODEC_VP8;
		if (videoCallEnabled && peerConnectionParameters.videoCodec != null) {
			if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_VP9)) {
				preferredVideoCodec = VIDEO_CODEC_VP9;
			} else if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_H264)) {
				preferredVideoCodec = VIDEO_CODEC_H264;
			}
		}
		Log.d(TAG, "Pereferred video codec: " + preferredVideoCodec);

		// Check if ISAC is used by default.
		preferIsac = false;
		if (peerConnectionParameters.audioCodec != null
				&& peerConnectionParameters.audioCodec.equals(AUDIO_CODEC_ISAC)) {
			preferIsac = true;
		}

		// Enable/disable OpenSL ES playback.
		if (!peerConnectionParameters.useOpenSLES) {
			Log.d(TAG, "Disable OpenSL ES audio even if device supports it");
			WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true /* enable */);
		} else {
			Log.d(TAG, "Allow OpenSL ES audio if device supports it");
			WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
		}

		// Create peer connection factory.
		if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true,
				peerConnectionParameters.videoCodecHwAcceleration)) {
			events.onPeerConnectionError("Failed to initializeAndroidGlobals");
		}
		if (options != null) {
			Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
		}
		factory = new PeerConnectionFactory();
		Log.d(TAG, "Peer connection factory created.");


	}

	private void createMediaConstraintsInternal() {
		// Create peer connection constraints.
//		pcConstraints = new MediaConstraints();
//		// Enable DTLS for normal calls and disable for loopback calls.
//		if (peerConnectionParameters.loopback) {
//			pcConstraints.optional.add(
//					new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "false"));
//		} else {
//			pcConstraints.optional.add(
//					new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
//		}

		// Check if there is a camera on device and disable video call if not.
		numberOfCameras = CameraEnumerationAndroid.getDeviceCount();
		if (numberOfCameras == 0) {
			Log.w(TAG, "No camera on device. Switch to audio only call.");
			videoCallEnabled = false;
		}
		// Create video constraints if video call is enabled.
		if (videoCallEnabled) {
			videoConstraints = new MediaConstraints();
			int videoWidth = peerConnectionParameters.videoWidth;
			int videoHeight = peerConnectionParameters.videoHeight;

			// If VP8 HW video encoder is supported and video resolution is not
			// specified force it to HD.
			if ((videoWidth == 0 || videoHeight == 0)
					&& peerConnectionParameters.videoCodecHwAcceleration
					&& MediaCodecVideoEncoder.isVp8HwSupported()) {
				videoWidth = HD_VIDEO_WIDTH;
				videoHeight = HD_VIDEO_HEIGHT;
			}

			// Add video resolution constraints.
			if (videoWidth > 0 && videoHeight > 0) {
				videoWidth = Math.min(videoWidth, MAX_VIDEO_WIDTH);
				videoHeight = Math.min(videoHeight, MAX_VIDEO_HEIGHT);
				videoConstraints.mandatory.add(new KeyValuePair(
						MIN_VIDEO_WIDTH_CONSTRAINT, Integer.toString(videoWidth)));
				videoConstraints.mandatory.add(new KeyValuePair(
						MAX_VIDEO_WIDTH_CONSTRAINT, Integer.toString(videoWidth)));
				videoConstraints.mandatory.add(new KeyValuePair(
						MIN_VIDEO_HEIGHT_CONSTRAINT, Integer.toString(videoHeight)));
				videoConstraints.mandatory.add(new KeyValuePair(
						MAX_VIDEO_HEIGHT_CONSTRAINT, Integer.toString(videoHeight)));
			}

			// Add fps constraints.
			int videoFps = peerConnectionParameters.videoFps;
			if (videoFps > 0) {
				videoFps = Math.min(videoFps, MAX_VIDEO_FPS);
				videoConstraints.mandatory.add(new KeyValuePair(
						MIN_VIDEO_FPS_CONSTRAINT, Integer.toString(videoFps)));
				videoConstraints.mandatory.add(new KeyValuePair(
						MAX_VIDEO_FPS_CONSTRAINT, Integer.toString(videoFps)));
			}
		}

		// Create audio constraints.
		audioConstraints = new MediaConstraints();
		// added for audio performance measurements
		if (peerConnectionParameters.noAudioProcessing) {
			Log.d(TAG, "Disabling audio processing");
			audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
			audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
			audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
			audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
		}
		// Create SDP constraints.
		sdpMediaConstraints = new MediaConstraints();
		sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		if (videoCallEnabled || peerConnectionParameters.loopback) {
			sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					"OfferToReceiveVideo", "true"));
		} else {
			sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
					"OfferToReceiveVideo", "false"));
		}
	}

	private void createPeerConnectionInternal(EglBase.Context renderEGLContext) {
		createPeerConnectionInternal(renderEGLContext, null);
	}

	private void createPeerConnectionInternal(EglBase.Context renderEGLContext, List<PeerConnection.IceServer> list) {
		if (factory == null || isError) {
			Log.e(TAG, "Peerconnection factory is not created");
			return;
		}
		Log.d(TAG, "Create peer connection.");

//		Log.d(TAG, "PCConstraints: " + pcConstraints.toString());
		if (videoConstraints != null) {
			Log.d(TAG, "VideoConstraints: " + videoConstraints.toString());
		}
		queuedRemoteCandidates = new LinkedList<IceCandidate>();

		if (videoCallEnabled) {
			Log.d(TAG, "EGLContext: " + renderEGLContext);
			factory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
		}

		PeerConnection.RTCConfiguration rtcConfig = null;
		if(list != null) {
			rtcConfig =
					new PeerConnection.RTCConfiguration(list);
		} else {
			rtcConfig =
					new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
		}
		// TCP candidates are only useful when connecting to a server that supports
		// ICE-TCP.
		rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
		rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
		rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
		// Use ECDSA encryption.
		rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

//		peerConnection = factory.createPeerConnection(
//				rtcConfig, pcConstraints, pcObserver);
		isInitiator = false;

		// Set default WebRTC tracing and INFO libjingle logging.
		// NOTE: this _must_ happen while |factory| is alive!
		Logging.enableTracing(
				"logcat:",
				EnumSet.of(Logging.TraceLevel.TRACE_DEFAULT),
				Logging.Severity.LS_INFO);

		mediaStream = factory.createLocalMediaStream("ARDAMS");
		if (videoCallEnabled) {
			String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
			String frontCameraDeviceName =
					CameraEnumerationAndroid.getNameOfFrontFacingDevice();
			if (numberOfCameras > 1 && frontCameraDeviceName != null) {
				cameraDeviceName = frontCameraDeviceName;
			}
			Log.d(TAG, "Opening camera: " + cameraDeviceName);
			videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null,
					peerConnectionParameters.captureToTexture ? renderEGLContext : null);
			if (videoCapturer == null) {
				reportError("Failed to open camera");
				return;
			}
			mediaStream.addTrack(createVideoTrack(videoCapturer));
		}

		mediaStream.addTrack(factory.createAudioTrack(
				AUDIO_TRACK_ID,
				factory.createAudioSource(audioConstraints)));
		peerConnection.addStream(mediaStream);

		if (peerConnectionParameters.aecDump) {
			try {
				aecDumpFileDescriptor = ParcelFileDescriptor.open(
						new File("/sdcard/Download/audio.aecdump"),
						ParcelFileDescriptor.MODE_READ_WRITE |
								ParcelFileDescriptor.MODE_CREATE |
								ParcelFileDescriptor.MODE_TRUNCATE);
				factory.startAecDump(aecDumpFileDescriptor.getFd());
			} catch(IOException e) {
				Log.e(TAG, "Can not open aecdump file", e);
			}
		}

		Log.d(TAG, "Peer connection created.");
	}

	private void closeInternal() {
//		if (factory != null && peerConnectionParameters.aecDump) {
//			factory.stopAecDump();
//		}
//		Log.d(TAG, "Closing peer connection.");
//		statsTimer.cancel();
//		if (peerConnection != null) {
//			peerConnection.dispose();
//			peerConnection = null;
//		}
//		Log.d(TAG, "Closing video source.");
//		if (videoSource != null) {
//			videoSource.dispose();
//			videoSource = null;
//		}
//		Log.d(TAG, "Closing peer connection factory.");
//		if (factory != null) {
//			factory.dispose();
//			factory = null;
//		}
//		options = null;
//		Log.d(TAG, "Closing peer connection done.");
//		events.onPeerConnectionClosed();
//		if(client != null) {
//			client.close();
//		}
//		if(client2 != null) {
//			client2.close();
//		}
//		PeerConnectionFactory.stopInternalTracingCapture();
//		PeerConnectionFactory.shutdownInternalTracer();
	}

	public boolean isHDVideo() {
		if (!videoCallEnabled) {
			return false;
		}
		int minWidth = 0;
		int minHeight = 0;
		for (KeyValuePair keyValuePair : videoConstraints.mandatory) {
			if (keyValuePair.getKey().equals("minWidth")) {
				try {
					minWidth = Integer.parseInt(keyValuePair.getValue());
				} catch (NumberFormatException e) {
					Log.e(TAG, "Can not parse video width from video constraints");
				}
			} else if (keyValuePair.getKey().equals("minHeight")) {
				try {
					minHeight = Integer.parseInt(keyValuePair.getValue());
				} catch (NumberFormatException e) {
					Log.e(TAG, "Can not parse video height from video constraints");
				}
			}
		}
		if (minWidth * minHeight >= 1280 * 720) {
			return true;
		} else {
			return false;
		}
	}

	private void getStats() {
//		if (peerConnection == null || isError) {
//			return;
//		}
//		boolean success = peerConnection.getStats(new StatsObserver() {
//			@Override
//			public void onComplete(final StatsReport[] reports) {
//				events.onPeerConnectionStatsReady(reports);
//			}
//		}, null);
//		if (!success) {
//			Log.e(TAG, "getStats() returns false!");
//		}
	}

	public void enableStatsEvents(boolean enable, int periodMs) {
		if (enable) {
			try {
				statsTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								getStats();
							}
						});
					}
				}, 0, periodMs);
			} catch (Exception e) {
				Log.e(TAG, "Can not schedule statistics timer", e);
			}
		} else {
			statsTimer.cancel();
		}
	}

	public void setVideoEnabled(final boolean enable) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				renderVideo = enable;
				if (localVideoTrack != null) {
					localVideoTrack.setEnabled(renderVideo);
				}
				if (remoteVideoTrack != null) {
					remoteVideoTrack.setEnabled(renderVideo);
				}
			}
		});
	}

//	public void createOffer() {
//		executor.execute(new Runnable() {
//			@Override
//			public void run() {
//				if (peerConnection != null && !isError) {
//					Log.d(TAG, "PC Create OFFER");
//					isInitiator = true;
//					peerConnection.createOffer(, sdpMediaConstraints);
//				}
//			}
//		});
//	}
//
//	public void createAnswer() {
//		executor.execute(new Runnable() {
//			@Override
//			public void run() {
//				if (peerConnection != null && !isError) {
//					Log.d(TAG, "PC create ANSWER");
//					isInitiator = false;
//					peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
//				}
//			}
//		});
//	}
//
//	public void addRemoteIceCandidate(final IceCandidate candidate) {
//		executor.execute(new Runnable() {
//			@Override
//			public void run() {
//				if (peerConnection != null && !isError) {
//					if (queuedRemoteCandidates != null) {
//						queuedRemoteCandidates.add(candidate);
//					} else {
//						peerConnection.addIceCandidate(candidate);
//					}
//				}
//			}
//		});
//	}

	public void stopVideoSource() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (videoSource != null && !videoSourceStopped) {
					Log.d(TAG, "Stop video source.");
					videoSource.stop();
					videoSourceStopped = true;
				}
			}
		});
	}

	public void startVideoSource() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (videoSource != null && videoSourceStopped) {
					Log.d(TAG, "Restart video source.");
					videoSource.restart();
					videoSourceStopped = false;
				}
			}
		});
	}

	private void reportError(final String errorMessage) {
//		Log.e(TAG, "Peerconnection error: " + errorMessage);
//		executor.execute(new Runnable() {
//			@Override
//			public void run() {
//				if (!isError) {
//					events.onPeerConnectionError(errorMessage);
//					isError = true;
//				}
//			}
//		});
	}

	private VideoTrack createVideoTrack(VideoCapturerAndroid capturer) {
		videoSource = factory.createVideoSource(capturer, videoConstraints);

		localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
		localVideoTrack.setEnabled(renderVideo);
		localVideoTrack.addRenderer(new VideoRenderer(localRender));
		return localVideoTrack;
	}

	private static String setStartBitrate(String codec, boolean isVideoCodec,
										  String sdpDescription, int bitrateKbps) {
		String[] lines = sdpDescription.split("\r\n");
		int rtpmapLineIndex = -1;
		boolean sdpFormatUpdated = false;
		String codecRtpMap = null;
		// Search for codec rtpmap in format
		// a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
		String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
		Pattern codecPattern = Pattern.compile(regex);
		for (int i = 0; i < lines.length; i++) {
			Matcher codecMatcher = codecPattern.matcher(lines[i]);
			if (codecMatcher.matches()) {
				codecRtpMap = codecMatcher.group(1);
				rtpmapLineIndex = i;
				break;
			}
		}
		if (codecRtpMap == null) {
			Log.w(TAG, "No rtpmap for " + codec + " codec");
			return sdpDescription;
		}
		Log.d(TAG, "Found " +  codec + " rtpmap " + codecRtpMap
				+ " at " + lines[rtpmapLineIndex]);

		// Check if a=fmtp string already exist in remote SDP for this codec and
		// update it with new bitrate parameter.
		regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
		codecPattern = Pattern.compile(regex);
		for (int i = 0; i < lines.length; i++) {
			Matcher codecMatcher = codecPattern.matcher(lines[i]);
			if (codecMatcher.matches()) {
				Log.d(TAG, "Found " +  codec + " " + lines[i]);
				if (isVideoCodec) {
					lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE
							+ "=" + bitrateKbps;
				} else {
					lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE
							+ "=" + (bitrateKbps * 1000);
				}
				Log.d(TAG, "Update remote SDP line: " + lines[i]);
				sdpFormatUpdated = true;
				break;
			}
		}

		StringBuilder newSdpDescription = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			newSdpDescription.append(lines[i]).append("\r\n");
			// Append new a=fmtp line if no such line exist for a codec.
			if (!sdpFormatUpdated && i == rtpmapLineIndex) {
				String bitrateSet;
				if (isVideoCodec) {
					bitrateSet = "a=fmtp:" + codecRtpMap + " "
							+ VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
				} else {
					bitrateSet = "a=fmtp:" + codecRtpMap + " "
							+ AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
				}
				Log.d(TAG, "Add remote SDP line: " + bitrateSet);
				newSdpDescription.append(bitrateSet).append("\r\n");
			}

		}
		return newSdpDescription.toString();
	}

	private static String preferCodec(
			String sdpDescription, String codec, boolean isAudio) {
		String[] lines = sdpDescription.split("\r\n");
		int mLineIndex = -1;
		String codecRtpMap = null;
		// a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
		String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
		Pattern codecPattern = Pattern.compile(regex);
		String mediaDescription = "m=video ";
		if (isAudio) {
			mediaDescription = "m=audio ";
		}
		for (int i = 0; (i < lines.length)
				&& (mLineIndex == -1 || codecRtpMap == null); i++) {
			if (lines[i].startsWith(mediaDescription)) {
				mLineIndex = i;
				continue;
			}
			Matcher codecMatcher = codecPattern.matcher(lines[i]);
			if (codecMatcher.matches()) {
				codecRtpMap = codecMatcher.group(1);
				continue;
			}
		}
		if (mLineIndex == -1) {
			Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
			return sdpDescription;
		}
		if (codecRtpMap == null) {
			Log.w(TAG, "No rtpmap for " + codec);
			return sdpDescription;
		}
		Log.d(TAG, "Found " +  codec + " rtpmap " + codecRtpMap + ", prefer at "
				+ lines[mLineIndex]);
		String[] origMLineParts = lines[mLineIndex].split(" ");
		if (origMLineParts.length > 3) {
			StringBuilder newMLine = new StringBuilder();
			int origPartIndex = 0;
			// Format is: m=<media> <port> <proto> <fmt> ...
			newMLine.append(origMLineParts[origPartIndex++]).append(" ");
			newMLine.append(origMLineParts[origPartIndex++]).append(" ");
			newMLine.append(origMLineParts[origPartIndex++]).append(" ");
			newMLine.append(codecRtpMap);
			for (; origPartIndex < origMLineParts.length; origPartIndex++) {
				if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
					newMLine.append(" ").append(origMLineParts[origPartIndex]);
				}
			}
			lines[mLineIndex] = newMLine.toString();
			Log.d(TAG, "Change media description: " + lines[mLineIndex]);
		} else {
			Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
		}
		StringBuilder newSdpDescription = new StringBuilder();
		for (String line : lines) {
			newSdpDescription.append(line).append("\r\n");
		}
		return newSdpDescription.toString();
	}

	private void drainCandidates() {
		if (queuedRemoteCandidates != null) {
			Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
			for (IceCandidate candidate : queuedRemoteCandidates) {
				peerConnection.addIceCandidate(candidate);
			}
			queuedRemoteCandidates = null;
		}
	}

	private void switchCameraInternal() {
		if (!videoCallEnabled || numberOfCameras < 2 || isError || videoCapturer == null) {
			Log.e(TAG, "Failed to switch camera. Video: " + videoCallEnabled + ". Error : "
					+ isError + ". Number of cameras: " + numberOfCameras);
			return;  // No video is sent or only one camera is available or error happened.
		}
		Log.d(TAG, "Switch camera");
		videoCapturer.switchCamera(null);
	}

	public void switchCamera() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				switchCameraInternal();
			}
		});
	}

	public void changeCaptureFormat(final int width, final int height, final int framerate) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				changeCaptureFormatInternal(width, height, framerate);
			}
		});
	}

	private void changeCaptureFormatInternal(int width, int height, int framerate) {
		if (!videoCallEnabled || isError || videoCapturer == null) {
			Log.e(TAG, "Failed to change capture format. Video: " + videoCallEnabled + ". Error : "
					+ isError);
			return;
		}
		videoCapturer.onOutputFormatRequest(width, height, framerate);
	}

	// Implementation detail: observe ICE & stream changes and react accordingly.
	private class PCObserver implements PeerConnection.Observer {
		@Override
		public void onIceCandidate(final IceCandidate candidate) {
//			executor.execute(new Runnable() {
//				@Override
//				public void run() {
//					events.onIceCandidate(candidate);
//				}
//			});
		}

		@Override
		public void onSignalingChange(
				PeerConnection.SignalingState newState) {
			Log.d(TAG, "SignalingState: " + newState);
		}

		@Override
		public void onIceConnectionChange(
				final PeerConnection.IceConnectionState newState) {
//			executor.execute(new Runnable() {
//				@Override
//				public void run() {
//					Log.d(TAG, "IceConnectionState: " + newState);
//					if (newState == IceConnectionState.CONNECTED) {
//						events.onIceConnected();
//					} else if (newState == IceConnectionState.DISCONNECTED) {
//						events.onIceDisconnected();
//					} else if (newState == IceConnectionState.FAILED) {
//						reportError("ICE connection failed.");
//					}
//				}
//			});
		}

		@Override
		public void onIceGatheringChange(
				PeerConnection.IceGatheringState newState) {
			Log.d(TAG, "IceGatheringState: " + newState);
		}

		@Override
		public void onIceConnectionReceivingChange(boolean receiving) {
			Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
		}

		@Override
		public void onAddStream(final MediaStream stream) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					if (peerConnection == null || isError) {
						return;
					}
					if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
						reportError("Weird-looking stream: " + stream);
						return;
					}
					if (stream.videoTracks.size() == 1) {
						remoteVideoTrack = stream.videoTracks.get(0);
						remoteVideoTrack.setEnabled(renderVideo);
						remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
					}
				}
			});
		}

		@Override
		public void onRemoveStream(final MediaStream stream) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					remoteVideoTrack = null;
				}
			});
		}

		@Override
		public void onDataChannel(final DataChannel dc) {
			reportError("AppRTC doesn't use data channels, but got: " + dc.label()
					+ " anyway!");
		}

		@Override
		public void onRenegotiationNeeded() {
			// No need to do anything; AppRTC follows a pre-agreed-upon
			// signaling/negotiation protocol.
		}
	}
}
