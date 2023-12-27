package ar.rulosoft.gean;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ErrorMessageProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

/** An activity that plays media using {@link ExoPlayer}. */
public class PlayActivity extends AppCompatActivity
        implements OnClickListener, PlayerView.ControllerVisibilityListener {

    // Saved instance state keys.

    private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
    private static final String KEY_ITEM_INDEX = "item_index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";

    protected PlayerView playerView;
    protected LinearLayout debugRootView;
    protected @Nullable ExoPlayer player;

    //private Button selectTracksButton;
    private TrackSelectionParameters trackSelectionParameters;
    private Tracks lastSeenTracks;
    private boolean startAutoPlay;
    private int startItemIndex;
    private long startPosition;
    private Handler handler;
    private ProgressBar bufferProgress;

    private long lastUpPress = 0;
    int[] increments = {5000, 10000, 15000, 30000, 30000, 60000, 300000, 600000};
    private boolean doublepress = false;
    private long timeoutdoublepress = 500;
    private int lastPressedKeyCode = -1;
    private long lastPressedTime = -1;
    private int doubleAccumulator = 0;
    private int maxDoubleAccumulator = 7;
    // Activity lifecycle.

    @OptIn(markerClass = UnstableApi.class) @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView();
        bufferProgress = findViewById(R.id.buffer_progress);
        bufferProgress.setMax(100);
        bufferProgress.getProgressDrawable().setColorFilter(
                Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        handler = new Handler(Looper.getMainLooper());
        //selectTracksButton.setOnClickListener(this);

        playerView = findViewById(R.id.player_view);
        playerView.setControllerShowTimeoutMs(1500);
        playerView.setControllerVisibilityListener(this);
        playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
        playerView.requestFocus();

        if (savedInstanceState != null) {
            trackSelectionParameters =
                    TrackSelectionParameters.fromBundle(
                            savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS));
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            trackSelectionParameters = new TrackSelectionParameters.Builder(/* context= */ this).build();
            clearStartPosition();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        releasePlayer();
        clearStartPosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            showToast("Ha denegado el acceso al alamacenamiento");
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        updateTrackSelectorParameters();
        updateStartPosition();
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
        outState.putInt(KEY_ITEM_INDEX, startItemIndex);
        outState.putLong(KEY_POSITION, startPosition);
    }

    // Activity input

    @Override
    public void onBackPressed() {
        InetTools.cacheInfo.cacheStop = true;
        InetTools.cacheInfo.cacheLink = "";
        InetTools.cacheInfo.deleteFile();
        super.onBackPressed();
    }

    @OptIn(markerClass = UnstableApi.class) @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        doublepress = lastPressedKeyCode == keyCode &&
                        System.currentTimeMillis() - lastPressedTime - timeoutdoublepress < 0;
        lastPressedKeyCode = keyCode;
        lastPressedTime = System.currentTimeMillis();
        if (doublepress) {
            doubleAccumulator += 1;
            doubleAccumulator = Math.min(
                    maxDoubleAccumulator,
                    doubleAccumulator);
        } else {
            doubleAccumulator = 0;
        }

        if(action == ACTION_DOWN) {
            if(keyCode == KEYCODE_BACK){
                onBackPressed();
            }

            switch (keyCode) {
                case 66://space
                case 23:
                case 20://down
                case 62://enter pause etc
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    break;
                case 19://up
                    long ctime = System.currentTimeMillis();
                    if ((ctime - lastUpPress) < 500) {

                        onBackPressed();
                    }
                    lastUpPress = ctime;
                    break;
                case 22://right
                    playerView.showController();
                    player.seekTo(player.getCurrentPosition() + increments[doubleAccumulator]);
                    break;
                case 21://left
                    playerView.showController();
                    player.seekTo(player.getCurrentPosition() - increments[doubleAccumulator]);
                    break;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        return true;// playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }



    // OnClickListener methods

    // PlayerView.ControllerVisibilityListener implementation

    @Override
    public void onVisibilityChanged(int visibility) {
        bufferProgress.setVisibility(visibility);
    }

    // Internal methods

    protected void setContentView() {
        setContentView(R.layout.activity_play);
    }

    /**
     *
     */
    protected void initializePlayer() {
        if (player == null) {
            Intent intent = getIntent();
            Context context = getApplicationContext();

            MediaItem mediaItem = MediaItem.fromUri(intent.getData());
            lastSeenTracks = Tracks.EMPTY;
            ExoPlayer.Builder playerBuilder =
                    new ExoPlayer.Builder(/* context= */ this);
            //setRenderersFactory(playerBuilder, intent.getBooleanExtra(IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA, false));
            player = playerBuilder.build();
            player.setTrackSelectionParameters(trackSelectionParameters);
            player.addListener(new PlayerEventListener());
            player.addAnalyticsListener(new EventLogger());
            player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
            player.setPlayWhenReady(startAutoPlay);
            player.setMediaItem(mediaItem);
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                    if(reason == Player.DISCONTINUITY_REASON_SEEK){
                        Log.e("seeking", " " + newPosition.toString());
                    }else{
                        Log.e("dicontinuity", "other_reason");
                    }
                    Player.Listener.super.onPositionDiscontinuity(oldPosition, newPosition, reason);
                }

                @Override
                public void onRenderedFirstFrame() {
                    InetTools.cacheInfo.setCacheListener(new InetTools.CacheListener() {
                        @Override
                        public void onCacheProgressUpdate(int progress) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    bufferProgress.setProgress(progress);
                                }
                            });
                        }
                    });
                    Player.Listener.super.onRenderedFirstFrame();
                }
            });
            // debugViewHelper = new DebugTextViewHelper(player, debugTextView);
            //debugViewHelper.start();
            if(intent.getData().toString().contains("/cache/")){

            }
        }
        boolean haveStartPosition = startItemIndex != C.INDEX_UNSET;
        if (haveStartPosition) {
            player.seekTo(startItemIndex, startPosition);
        }


        player.prepare();
        player.play();
    }



    protected void releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters();
            updateStartPosition();
            player.release();
            player = null;
            playerView.setPlayer(/* player= */ null);
        }
    }



    private void updateTrackSelectorParameters() {
        if (player != null) {
            trackSelectionParameters = player.getTrackSelectionParameters();
        }
    }

    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startItemIndex = player.getCurrentMediaItemIndex();
            startPosition = Math.max(0, player.getContentPosition());
        }
    }

    protected void clearStartPosition() {
        startAutoPlay = true;
        startItemIndex = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    // User controls

    private void showControls() {

    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onClick(View v) {

    }

    private class PlayerEventListener implements Player.Listener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                showControls();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
            } else {
                showControls();
            }
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(Tracks tracks) {
            if (tracks == lastSeenTracks) {
                return;
            }
            if (tracks.containsType(C.TRACK_TYPE_VIDEO)
                    && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true)) {
                showToast("Video no soportado");
            }
            if (tracks.containsType(C.TRACK_TYPE_AUDIO)
                    && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true)) {
                showToast("Audio no soportado");
            }
            lastSeenTracks = tracks;
        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<PlaybackException> {

        @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
        @Override
        public Pair<Integer, String> getErrorMessage(PlaybackException e) {
            String errorString = "Eroor ...";
            Throwable cause = e.getCause();
            if (cause instanceof DecoderInitializationException) {
                // Special case for decoder initialization failures.
                DecoderInitializationException decoderInitializationException =
                        (DecoderInitializationException) cause;
                if (decoderInitializationException.codecInfo == null) {
                    if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
                        errorString = "Error encontrando el docoder";
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = "Error de seguridad en el docoder";
                    } else {
                        errorString = "Error de seguridad en el docoder";
                    }
                } else {
                    errorString = "Error de inicializacion en el docoder";
                }
            }
            return Pair.create(0, errorString);
        }
    }
}