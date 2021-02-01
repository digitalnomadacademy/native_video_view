package cl.ceisufro.native_video_view


import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.CREATED
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.DESTROYED
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.PAUSED
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.RESUMED
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.STARTED
import cl.ceisufro.native_video_view.NativeVideoViewPlugin.Companion.STOPPED
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.platform.PlatformView
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class ExoPlayerController(private val id: Int,
                          activityState: AtomicInteger,
                          private val registrar: PluginRegistry.Registrar)
    : Application.ActivityLifecycleCallbacks,
        MethodChannel.MethodCallHandler,
        PlatformView,
        Player.EventListener {
    private val methodChannel: MethodChannel = MethodChannel(registrar.messenger(), "native_video_view_$id")
    private val registrarActivityHashCode: Int
    private val constraintLayout: ConstraintLayout
    private val surfaceView: SurfaceView
    private val exoPlayer: SimpleExoPlayer
    private var dataSource: String? = null
    private var requestAudioFocus: Boolean = true
    private var volume: Double = 1.0
    private var mute: Boolean = false
    private var disposed: Boolean = false
    private var playerState: PlayerState = PlayerState.NOT_INITIALIZED

    private var playerView: PlayerView
    private var adsLoader: ImaAdsLoader? = null


    init {
        this.methodChannel.setMethodCallHandler(this)
        this.registrarActivityHashCode = registrar.activity().hashCode()
        this.constraintLayout = LayoutInflater.from(registrar.activity())
                .inflate(R.layout.exoplayer_layout, null) as ConstraintLayout

        this.surfaceView = constraintLayout.findViewById(R.id.exo_player_surface_view)
        val trackSelector = DefaultTrackSelector(registrar.activity())
        this.exoPlayer = SimpleExoPlayer.Builder(registrar.activity())
                .setTrackSelector(trackSelector)
                .build()



         playerView =  constraintLayout.findViewById(R.id.player_view)
        playerView.controllerAutoShow = false;

        adsLoader = ImaAdsLoader(getView().context, Uri.parse("https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dskippablelinear&correlator="))



        playerView?.player = exoPlayer;
        adsLoader?.setPlayer(exoPlayer);



        Log.d("DEBUG", adsLoader?.toString())

        when (activityState.get()) {
            STOPPED -> {
                stopPlayback()
            }
            PAUSED -> {
                pausePlayback()
            }
            RESUMED -> {
                // Not implemented
            }
            STARTED -> {
                // Not implemented
            }
            CREATED -> {
                configurePlayer()
            }
            DESTROYED -> {
                // Not implemented
            }
            else -> throw IllegalArgumentException(
                    "Cannot interpret " + activityState.get() + " as an activity state")
        }
        registrar.activity().application.registerActivityLifecycleCallbacks(this)
    }

    override fun getView(): View {
        return constraintLayout
    }


    fun getActivity():Activity {
        return  registrar.activity()

    }
    override fun dispose() {
        if (disposed) return
        disposed = true
        methodChannel.setMethodCallHandler(null)
        this.destroyVideoView()
        registrar.activity().application.unregisterActivityLifecycleCallbacks(this)
        Log.d("VIDEO. NVV", "Disposed view $id")
    }

    fun enterPictureInPicture(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            getActivity().enterPictureInPictureMode(PictureInPictureParams.Builder().build())

        }
     }

    fun exitPictureInPicture(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            getActivity().moveTaskToBack(false)
        }
    }


    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "player#setVideoSource" -> {
                val videoPath: String? = call.argument("videoSource")
                val sourceType: String? = call.argument("sourceType")
                requestAudioFocus = call.argument("requestAudioFocus") as Boolean? ?: true
                if (videoPath != null) {
                    if (sourceType.equals("VideoSourceType.asset")
                            || sourceType.equals("VideoSourceType.file")) {
                        initVideo("file://$videoPath")
                    } else {
                        initVideo(videoPath)
                    }
                }
                result.success(null)
            }
            "player#start" -> {
                startPlayback()
                result.success(null)
            }
            "player#enterPIP" -> {
                enterPictureInPicture()
                result.success(null)
            }
            "player#exitPIP" -> {
                exitPictureInPicture()
                result.success(null)
            }
            "player#pause" -> {
                pausePlayback()
                result.success(null)
            }
            "player#stop" -> {
                stopPlayback()
                result.success(null)
            }
            "player#currentPosition" -> {
                val arguments = HashMap<String, Any>()
                arguments["currentPosition"] = exoPlayer.currentPosition
                result.success(arguments)
            }
            "player#isPlaying" -> {
                val arguments = HashMap<String, Any>()
                arguments["isPlaying"] = exoPlayer.isPlaying
                result.success(arguments)
            }
            "player#seekTo" -> {
                val position: Int? = call.argument("position")
                if (position != null)
                    exoPlayer.seekTo(position.toLong())
                result.success(null)
            }
            "player#toggleSound" -> {
                mute = !mute
                configureVolume()
                result.success(null)
            }
            "player#setVolume" -> {
                val volume: Double? = call.argument("volume")
                if (volume != null) {
                    this.mute = false
                    this.volume = volume
                    configureVolume()
                }
                result.success(null)
            }
        }
    }

    override fun onActivityCreated(activity: Activity?, p1: Bundle?) {
        this.configurePlayer()
    }

    override fun onActivityStarted(activity: Activity?) {
        // Not implemented
    }

    override fun onActivityResumed(activity: Activity?) {
        // Not implemented
    }

    override fun onActivityPaused(activity: Activity?) {
        if (disposed || activity.hashCode() != registrarActivityHashCode) return
        this.pausePlayback()
    }

    override fun onActivityStopped(activity: Activity?) {
        if (disposed || activity.hashCode() != registrarActivityHashCode) return
        this.stopPlayback()
    }

    override fun onActivityDestroyed(activity: Activity?) {
        if (disposed || activity.hashCode() != registrarActivityHashCode) return
        this.destroyVideoView()
    }

    override fun onActivitySaveInstanceState(activity: Activity?, p1: Bundle?) {
        // Not implemented
    }

    private fun configurePlayer() {
        try {
            exoPlayer.addListener(this)
            exoPlayer.setVideoSurfaceView(surfaceView)
            handleAudioFocus()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun handleAudioFocus() {
        exoPlayer.setAudioAttributes(getAudioAttributes(), requestAudioFocus)
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MOVIE)
                .build()
    }

    private fun configureVolume() {
        if (mute) {
            exoPlayer.volume = 0f
        } else {
            exoPlayer.volume = volume.toFloat()
        }
    }

    private fun initVideo(dataSource: String?) {
        this.configurePlayer()
        if (dataSource != null) {



            val uri = Uri.parse(dataSource)
            val dataSourceFactory = getDataSourceFactory(uri)

            val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

            // Create the MediaSource for the content you wish to play.

            // Create the MediaSource for the content you wish to play.
            val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(uri)
            val adsMediaSource = AdsMediaSource(mediaSource, dataSourceFactory, adsLoader, playerView)


            this.exoPlayer.prepare(adsMediaSource)
             exoPlayer.playWhenReady = true
            this.dataSource = dataSource
        }
    }

    private fun getDataSourceFactory(uri: Uri): DataSource.Factory {
        val scheme: String? = uri.scheme
        return if (scheme != null && (scheme == "http" || scheme == "https")) {
            DefaultHttpDataSourceFactory(
                    "ExoPlayer",
                    null,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true)
        } else {
            DefaultDataSourceFactory(registrar.activity(), "ExoPlayer")
        }
    }

    private fun startPlayback() {
        if (playerState != PlayerState.PLAYING && dataSource != null) {
            if (playerState != PlayerState.NOT_INITIALIZED) {
                exoPlayer.playWhenReady = true
                playerState = PlayerState.PLAYING
            } else {
                playerState = PlayerState.PLAY_WHEN_READY
                initVideo(dataSource)
            }
        }
    }

    private fun pausePlayback() {
        exoPlayer.stop()
        playerState = PlayerState.PAUSED
    }

    private fun stopPlayback() {
        exoPlayer.stop(true)
        playerState = PlayerState.NOT_INITIALIZED
    }

    private fun destroyVideoView() {
        exoPlayer.stop(true)
        exoPlayer.removeListener(this)
        exoPlayer.release()
    }

    private fun notifyPlayerPrepared() {
        val arguments = HashMap<String, Any>()
        val videoFormat = exoPlayer.videoFormat
        if (videoFormat != null) {
            arguments["height"] = videoFormat.height
            arguments["width"] = videoFormat.width
            arguments["duration"] = exoPlayer.duration
        }
        playerState = PlayerState.PREPARED
        methodChannel.invokeMethod("player#onPrepared", arguments)
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            stopPlayback()
            methodChannel.invokeMethod("player#onCompletion", null)
        } else if (playbackState == Player.STATE_READY) {
            configureVolume()
            if (playerState == PlayerState.PLAY_WHEN_READY) {
                this.startPlayback()
            } else {
                notifyPlayerPrepared()
            }
        }
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        dataSource = null
        playerState = PlayerState.NOT_INITIALIZED
        val arguments = HashMap<String, Any>()
        arguments["what"] = error.type
        arguments["extra"] = error.message ?: ""
        methodChannel.invokeMethod("player#onError", arguments)
    }
}