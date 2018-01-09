package com.wigdis.player;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {


    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private final IBinder iBinder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int resumePosition = 0;

    private ArrayList<Audio> audioList;
    static Audio activeAudio;

    private static final String URL = "http://sapphire.memleak.pl:8080";

    public static final String ACTION_PLAY = "com.wigdis.player.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.wigdis.player.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.wigdis.player.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.wigdis.player.ACTION_NEXT";
    public static final String ACTION_STOP = "com.wigdis.player.ACTION_STOP";

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    private ResultReceiver resultReceiver;

    private static final int NOTIFICATION_ID = 101;

    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();

                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }


        });
    }


    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                R.drawable.musiclogo);

        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();

            resultReceiver.send(1, new Bundle());
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();

            resultReceiver.send(0, new Bundle());
        }
    }

    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            resultReceiver.send(1, new Bundle());
        }
    }

    private void skipToNext() {
        Double reward = getReward();
        postRequestCall(reward);

    }

    private Double getReward() {
        Double reward = mediaPlayer.getCurrentPosition()/45000. *100.;

        return BigDecimal.valueOf(reward)
                .setScale(2, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        StorageUtil storage = new StorageUtil(getApplicationContext());
        audioList = storage.loadAudio();

        if (!requestAudioFocus()) {
            stopSelf();
        }

        if (mediaSessionManager == null) {

            resultReceiver = intent.getParcelableExtra("receiver");
            getRequestCall();

        }

        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        callStateListener();

        registerBecomingNoisyReceiver();
        register_playNewAudio();
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stopMedia();
        stopSelf();

        Double reward = 100.;
        postRequestCall(reward);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK "
                        + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        // When source ready for playback
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:

                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:

                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:

                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    public class LocalBinder extends Binder implements IBinder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;

        }
    }


    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {

        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };

        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            //Get the new media index form SharedPreferences
            if (intent.getAction() == PlayerActivity.Broadcast_PLAY_NEW_AUDIO) {
                if (mediaPlayer.isPlaying()){
                    pauseMedia();
                    buildNotification(PlaybackStatus.PAUSED);
                } else {
                    resumeMedia();
                    buildNotification(PlaybackStatus.PLAYING);
                }
            } else {
                Double reward = getReward();
                postRequestCall(reward);
            }

        }
    };

    private void register_playNewAudio() {

        IntentFilter filter = new IntentFilter(PlayerActivity.Broadcast_PLAY_NEW_AUDIO);
        IntentFilter filter2 = new IntentFilter(PlayerActivity.Broadcast_PLAY_ANOTHER);
        registerReceiver(playNewAudio, filter);
        registerReceiver(playNewAudio, filter2);
    }

    private void buildNotification(PlaybackStatus playbackStatus) {

        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent play_pauseAction = null;

        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.musiclogo);

        NotificationCompat.Builder notificationBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setShowWhen(false)
                        .setStyle(new NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0, 1, 2))
                        .setColor(getResources().getColor(R.color.colorPrimary))
                        .setLargeIcon(largeIcon)
                        .setSmallIcon(android.R.drawable.stat_sys_headset)
                        .setContentText(activeAudio.getArtist())
                        .setContentTitle(activeAudio.getTitle())
                        .setContentInfo(activeAudio.getAlbum())
                        //need to be added because of backward compatibility
                        .addAction(android.R.drawable.menuitem_background, "previous", playbackAction(3))
                        .addAction(notificationAction, "pause", play_pauseAction)
                        .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }

    private final Response.Listener<JSONObject> postResponseHandler = new Response.Listener<JSONObject>() {

        @Override
        public void onResponse(JSONObject response) {
            try {
                int audioIndex = Integer.parseInt((String) response.get("id"));
                System.out.print(audioIndex);
                new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

                if (audioIndex != -1 ) {

                    activeAudio = audioList.get(audioIndex);

                    resultReceiver.send(200, new Bundle());
                } else {
                    stopSelf();
                }

                stopMedia();
                mediaPlayer.reset();
                initMediaPlayer();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private final Response.Listener<JSONObject> getResponseHandler = new Response.Listener<JSONObject>() {

        @Override
        public void onResponse(JSONObject response) {
            try {
                int audioIndex = Integer.parseInt((String) response.get("id"));
                System.out.print(audioIndex);
                new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
                if (audioIndex != -1 ) {

                    activeAudio = audioList.get(audioIndex);
                    resultReceiver.send(200, new Bundle());
                } else {
                    stopSelf();
                }
                initMediaSession();

                initMediaPlayer();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private void getRequestCall() {

        int initial_timeout_ms = 5000;

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, URL, null, getResponseHandler, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.out.println(error);

                    }
                });

        jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(
                initial_timeout_ms,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));


        Volley.newRequestQueue(this).add(jsObjRequest);
    }

    private void postRequestCall(Double reward) {
        Map<String, String> params = new HashMap<String, String>();
        StorageUtil storageUtil = new StorageUtil(getApplicationContext());
        Mood mood = storageUtil.loadMood();

        params.put("reward", reward.toString());
        params.put("valence", mood.getValence().toString());
        params.put("arousal", mood.getArousal().toString());

        int initial_timeout_ms = 5000;

        JsonObjectRequest postRequest =
                new JsonObjectRequest(Request.Method.POST, URL,
                        new JSONObject(params), postResponseHandler, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.out.println("Error POST");
                    }
                });

        postRequest.setRetryPolicy(new DefaultRetryPolicy(
                initial_timeout_ms,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));


        Volley.newRequestQueue(this).add(postRequest);
    }
}