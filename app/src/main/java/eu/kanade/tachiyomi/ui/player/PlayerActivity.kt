package eu.kanade.tachiyomi.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.hippo.unifile.UniFile
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.PlayerLayoutBinding
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.util.AniSkipApi
import eu.kanade.tachiyomi.util.SkipType
import eu.kanade.tachiyomi.util.Stamp
import eu.kanade.tachiyomi.util.SubtitleSelect
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.floor

class PlayerActivity : BaseActivity() {
    private val viewModel by viewModels<PlayerViewModel>(factoryProducer = { PlayerViewModelProviderFactory(this) })
    private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
    private val playerObserver by lazy { PlayerObserver(this) }
    val player by lazy { binding.player }
    val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
    val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var mediaSession: MediaSession? = null
    internal val playerPreferences: PlayerPreferences = Injekt.get()
    internal val gesturePreferences: GesturePreferences = Injekt.get()
    internal val subtitlePreferences: SubtitlePreferences = Injekt.get()
    internal val audioPreferences: AudioPreferences = Injekt.get()
    internal val advancedPlayerPreferences: AdvancedPlayerPreferences = Injekt.get()
    internal val networkPreferences: NetworkPreferences = Injekt.get()
    private val storageManager: StorageManager = Injekt.get()

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: Rect? = null
    val isPipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private var pipReceiver: BroadcastReceiver? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /*
    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle(viewModel.mediaTitle.value)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit().get()
            builder.setAutoEnterEnabled(player.paused == false && autoEnter)
            builder.setSeamlessResizeEnabled(player.paused == false && autoEnter)
        }
        // TODO
        // builder.setActions(createPipActions(this, player.paused ?: true))
        builder.setSourceRectHint(pipRect)
        player.videoH?.let {
            val height = it
            val width = it * player.getVideoOutAspect()!!
            val rational = Rational(height, width.toInt()).toFloat()
            if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
        }
        return builder.build()
    }
    */

    companion object {
        fun newIntent(
            context: Context,
            animeId: Long?,
            episodeId: Long?,
            vidList: List<Video>? = null,
            vidIndex: Int? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                vidIndex?.let { putExtra("vidIndex", it) }
                vidList?.let { putExtra("vidList", it.serialize()) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        private const val MAX_BRIGHTNESS = 255F
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val animeId = intent.extras?.getLong("animeId") ?: -1
        val episodeId = intent.extras?.getLong("episodeId") ?: -1
        val vidList = intent.extras?.getString("vidList") ?: ""
        val vidIndex = intent.extras?.getInt("vidIndex") ?: 0
        if (animeId == -1L || episodeId == -1L) {
            finish()
            return
        }
        NotificationReceiver.dismissNotification(
            this,
            animeId.hashCode(),
            Notifications.ID_NEW_EPISODES,
        )

        viewModel.saveCurrentEpisodeWatchingProgress()

        lifecycleScope.launchNonCancellable {
            viewModel.mutableState.update {
                it.copy(isLoadingEpisode = true)
            }

            val initResult = viewModel.init(animeId, episodeId, vidList, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withUIContext {
                    setInitialEpisodeError(exception)
                }
            }
            // TODO(videolist)
            /*
            lifecycleScope.launch {
                setVideoList(
                    qualityIndex = initResult.first.videoIndex,
                    videos = initResult.first.videoList,
                    position = initResult.first.position,
                )
            }
            */
        }

        setIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        registerSecureActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupPlayerMPV()
        setupPlayerAudio()
        setupMediaSession()
        setupPlayerOrientation()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            toast(throwable.message)
            logcat(LogPriority.ERROR, throwable)
            finish()
        }

        binding.controls.setContent {
            TachiyomiTheme {
                PlayerControls(
                    viewModel = viewModel,
                    onBackPress = ::finish,
                    modifier = Modifier.onGloballyPositioned {
                        pipRect = run {
                            val boundsInWindow = it.boundsInWindow()
                            Rect(
                                boundsInWindow.left.toInt(),
                                boundsInWindow.top.toInt(),
                                boundsInWindow.right.toInt(),
                                boundsInWindow.bottom.toInt()
                            )
                        }
                    },
                )
            }
        }

        onNewIntent(this.intent)
    }

    override fun onDestroy() {
        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null
        mediaSession?.release()
        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        MPVLib.removeLogObserver(playerObserver)
        MPVLib.removeObserver(playerObserver)
        player.destroy()

        super.onDestroy()
    }

    override fun onPause() {
        if (!isInPictureInPictureMode) {
            viewModel.pause()
        }
        viewModel.saveCurrentEpisodeWatchingProgress()
        super.onPause()
    }

    override fun onStop() {
        viewModel.pause()
        viewModel.saveCurrentEpisodeWatchingProgress()
        player.isExiting = true
        window.attributes.screenBrightness.let {
            if (playerPreferences.rememberPlayerBrightness().get() && it != -1f) {
                playerPreferences.playerBrightnessValue().set(it)
            }
        }

        super.onStop()
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = player.paused ?: false
                viewModel.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) viewModel.unpause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "0.5"))
                restoreAudioFocus = {
                    MPVLib.command(arrayOf("multiply", "volume", "2"))
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }

            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                logcat(LogPriority.DEBUG) { "didn't get audio focus" }
            }
        }
    }

    /*
    private val animationHandler = Handler(Looper.getMainLooper())

    private val streams: PlayerViewModel.VideoStreams
        get() = viewModel.state.value.videoStreams

    private var currentVideoList: List<Video>? = null
        set(list) {
            field = list
            streams.quality.tracks = field?.map { Track("", it.quality) }?.toTypedArray() ?: emptyArray()
        }


    private var hadPreviousSubs = false

    private var hadPreviousAudio = false
     */

    private fun copyAssets(configDir: String) {
        val assetManager = this.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = File("$configDir/$filename")
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    logcat(LogPriority.VERBOSE) { "Skipping copy of asset file (exists same size): $filename" }
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                logcat(LogPriority.WARN) { "Copied asset file: $filename" }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to copy asset file: $filename" }
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun setupPlayerMPV() {
        val logLevel = if (networkPreferences.verboseLogging().get()) "info" else "warn"

        val configDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            storageManager.getMPVConfigDirectory()!!.filePath!!
        } else {
            if (advancedPlayerPreferences.mpvScripts().get()) {
                copyScripts()
            }
            applicationContext.filesDir.path
        }

        val mpvConfFile = File("$configDir/mpv.conf")
        advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
        val mpvInputFile = File("$configDir/input.conf")
        advancedPlayerPreferences.mpvInput().get().let { mpvInputFile.writeText(it) }

        copyAssets(configDir)

        MPVLib.setOptionString("sub-ass-force-margins", "yes")
        MPVLib.setOptionString("sub-use-margins", "yes")

        player.initialize(
            configDir = configDir,
            cacheDir = applicationContext.cacheDir.path,
            logLvl = logLevel,
        )
        MPVLib.addObserver(playerObserver)
    }

    private fun setupPlayerAudio() {
        with(audioPreferences) {
            // audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioChannels().get().let { MPVLib.setPropertyString(it.property, it.value) }

            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
                it.setAudioAttributes(
                    AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
                )
                it.setOnAudioFocusChangeListener(audioFocusChangeListener)
            }.build()
            AudioManagerCompat.requestAudioFocus(audioManager, request).let {
                if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
                audioFocusRequest = request
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun setupPlayerSubtitles() {
        with(subtitlePreferences) {
            val overrideType = if (overrideSubsASS().get()) "force" else "no"
            MPVLib.setPropertyString("sub-ass-override", overrideType)

            if (subtitlePreferences.rememberSubtitlesDelay().get()) {
                MPVLib.setPropertyDouble("sub-delay", subtitlesDelay().get() / 1000.0)
            }

            copyFontsDirectory()

            if (subtitleFont().get().trim() != "") {
                MPVLib.setPropertyString("sub-font", subtitleFont().get())
            } else {
                MPVLib.setPropertyString("sub-font", "Sans Serif")
            }

            MPVLib.setPropertyString("sub-bold", if (boldSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyString("sub-italic", if (italicSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyInt("sub-font-size", subtitleFontSize().get())
            MPVLib.setPropertyString("sub-color", textColorSubtitles().get().toHexString())
            MPVLib.setPropertyString("sub-border-color", borderColorSubtitles().get().toHexString())
            MPVLib.setPropertyString(
                "sub-back-color",
                backgroundColorSubtitles().get().toHexString(),
            )
        }
    }

    private fun copyFontsDirectory() {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        CoroutineScope(Dispatchers.IO).launchIO {
            storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
                val outFile = UniFile.fromFile(applicationContext.filesDir)?.createFile(font.name)
                outFile?.let {
                    font.openInputStream().copyTo(it.openOutputStream())
                }
            }
            MPVLib.setPropertyString(
                "sub-fonts-dir",
                applicationContext.filesDir.path,
            )
            MPVLib.setPropertyString(
                "osd-fonts-dir",
                applicationContext.filesDir.path,
            )
        }
    }

    private fun copyScripts() {
        CoroutineScope(Dispatchers.IO).launchIO {
            // First, delete all present scripts
            val scriptsDir = {
                UniFile.fromFile(applicationContext.filesDir)?.createDirectory("scripts")
            }
            val scriptOptsDir = {
                UniFile.fromFile(applicationContext.filesDir)?.createDirectory("script-opts")
            }
            scriptsDir()?.delete()
            scriptOptsDir()?.delete()

            // Then, copy the scripts from the Aniyomi directory
            storageManager.getScriptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getScriptOptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptOptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
        }
    }

    private fun getMaxBrightness(): Float {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return MAX_BRIGHTNESS
        val brightnessField = powerManager.javaClass.declaredFields.find {
            it.name == "BRIGHTNESS_ON"
        } ?: return MAX_BRIGHTNESS

        brightnessField.isAccessible = true
        return try {
            (brightnessField.get(powerManager) as Int).toFloat()
        } catch (e: IllegalAccessException) {
            logcat(LogPriority.ERROR, e) { "Unable to access BRIGHTNESS_ON field" }
            MAX_BRIGHTNESS
        }
    }

    private fun getCurrentBrightness(): Float {
        // check if window has brightness set
        val lp = window.attributes
        if (lp.screenBrightness >= 0f) return lp.screenBrightness
        val resolver = contentResolver
        return try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / getMaxBrightness()
        } catch (e: Settings.SettingNotFoundException) {
            logcat(LogPriority.ERROR, e) { "Unable to get screen brightness" }
            0.5F
        }
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences.mediaPreviousGesture().get()
        val playAction = gesturePreferences.mediaPlayPauseGesture().get()
        val nextAction = gesturePreferences.mediaNextGesture().get()

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }

                            SingleActionGesture.Switch -> TODO()
                        }
                    }

                    override fun onPause() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPlay.keyCode))
                            }

                            SingleActionGesture.Switch -> TODO()
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaPrevious.keyCode))
                            }

                            SingleActionGesture.Switch -> TODO()
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                MPVLib.command(arrayOf("keypress", CustomKeyCodes.MediaNext.keyCode))
                            }

                            SingleActionGesture.Switch -> TODO()
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        this@PlayerActivity.onStop()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT
                    )
                    .build()
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // Create Player -- End --

    // Override PlayerActivity lifecycle -- Start --

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
    }

    override fun finishAndRemoveTask() {
        viewModel.deletePendingEpisodes()
        super.finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        /*
        if (supportedAndEnabled) {
            if (player.paused == false && playerPreferences.pipOnExit().get()) {
                updatePip(true)
            } else {
                finishAndRemoveTask()
                super.onBackPressed()
            }
        } else {
            finishAndRemoveTask()
            super.onBackPressed()
        }

         */
    }

    override fun onStart() {
        super.onStart()
        // setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen().get()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (playerPreferences.rememberPlayerBrightness().get()) {
            playerPreferences.playerBrightnessValue().get().let {
                if (it != -1f) viewModel.changeBrightnessTo(it)
            }
        }
    }

    override fun onUserLeaveHint() {
        if (isPipSupported && player.paused == false && playerPreferences.pipOnExit().get()) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences.aspectState().get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    // Override PlayerActivity lifecycle -- End --

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        viewModel.closeDialogSheet()

        player.paused = true
        showLoadingIndicator(true)
        // TODO(aniskip)
        // aniskipStamps = emptyList()

        lifecycleScope.launch {
            viewModel.mutableState.update { it.copy(isLoadingEpisode = true) }

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()

            when (val switchMethod = viewModel.loadEpisode(episodeId)) {
                null -> {
                    if (viewModel.currentAnime != null && !autoPlay) {
                        launchUI { toast(MR.strings.no_next_episode) }
                    }
                    showLoadingIndicator(false)
                }

                else -> {
                    if (switchMethod.first != null) {
                        when {
                            switchMethod.first!!.isEmpty() -> setInitialEpisodeError(
                                Exception("Video list is empty."),
                            )
                            // TODO(videolist)
                            // setVideoList(qualityIndex = 0, switchMethod.first!!)
                            else -> {

                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (PipState.mode == PipState.ON && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.second) }
                    }
                }
            }
        }
    }

    internal fun showLoadingIndicator(visible: Boolean) {
        // viewModel.isLoading.update { visible }
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("SourceLockedOrientationActivity")
    fun rotatePlayer(view: View) {
        /*
        if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            this.requestedOrientation = playerPreferences.defaultPlayerOrientationLandscape().get()
        } else {
            this.requestedOrientation = playerPreferences.defaultPlayerOrientationPortrait().get()
        }

         */
    }

    private fun setupPlayerOrientation() {
        requestedOrientation = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player.videoAspect ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { player.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    // Removing this causes mpv to repeat the last repeated input
    // that's not specified in onKeyDown indefinitely for some reason
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.currentAnime ?: return
        val episode = viewModel.currentEpisode ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_screenshot_info, anime.title, episode.name, seconds),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as cover or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsCoverResult(result: SetAsCover) {
        toast(
            when (result) {
                SetAsCover.Success -> MR.strings.cover_updated
                SetAsCover.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsCover.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /*
    @Suppress("UNUSED_PARAMETER")
    fun cycleSpeed(view: View) {
        player.cycleSpeed()
        refreshUi()
    }

    @Suppress("UNUSED_PARAMETER")
    fun skipIntro(view: View) {
        if (skipType != null) {
            // this stops the counter
            if (waitingAniSkip > 0 && netflixStyle) {
                waitingAniSkip = -1
                return
            }
            skipType.let {
                MPVLib.command(
                    arrayOf(
                        "seek",
                        "${aniSkipInterval!!.first{it.skipType == skipType}.interval.endTime}",
                        "absolute",
                    ),
                )
            }
            AniSkipApi.PlayerUtils(binding, aniSkipInterval!!).skipAnimation(skipType!!)
        } else if (playerControls.binding.controlsSkipIntroBtn.text != "") {
            doubleTapSeek(viewModel.getAnimeSkipIntroLength(), isDoubleTap = false)
            playerControls.resetControlsFade()
        }
    }
    */

    /**
     * Updates the player UI text and controls in a separate thread
    internal fun refreshUi() {
        viewModel.viewModelScope.launchUI {
            setVisibilities()
            player.timePos?.let { playerControls.updatePlaybackPos(it) }
            player.duration?.let { playerControls.updatePlaybackDuration(it) }
            updatePlaybackStatus(player.paused ?: return@launchUI)
            updatePip(start = false)
            playerControls.updateEpisodeText()
            playerControls.updatePlaylistButtons()
            playerControls.updateSpeedButton()
        }
    }
    */

    // TODO: Move into function once compose is implemented

    /*
    val supportedAndEnabled = Injekt.get<BasePreferences>().deviceHasPip() && playerPreferences.enablePip().get()
    internal fun updatePip(start: Boolean) {
        val anime = viewModel.currentAnime ?: return
        val episode = viewModel.currentEpisode ?: return
        val paused = player.paused ?: return
        val videoAspect = player.videoAspect ?: return
        if (supportedAndEnabled) {
            PictureInPictureHandler().update(
                context = this,
                title = anime.title,
                subtitle = episode.name,
                paused = paused,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
                pipOnExit = playerPreferences.pipOnExit().get() && !paused,
                videoAspect = videoAspect * 10000,
                playlistCount = viewModel.getCurrentEpisodeIndex(),
                playlistPosition = viewModel.currentPlaylist.size,
            ).let {
                setPictureInPictureParams(it)
                if (PipState.mode == PipState.OFF && start) {
                    PipState.mode = PipState.STARTED
                    playerControls.hideControls(hide = true)
                    enterPictureInPictureMode(it)
                }
            }
        }
    }

    */

    /*
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
            return
        }
        setPictureInPictureParams(createPipParams())
        viewModel.hideControls()
        viewModel.hideSeekBar()
        viewModel.isBrightnessSliderShown.update { false }
        viewModel.isVolumeSliderShown.update { false }
        viewModel.sheetShown.update { Sheets.None }
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                    PIP_PAUSE -> viewModel.pause()
                    PIP_PLAY -> viewModel.unpause()
                    PIP_NEXT -> viewModel.handleRightDoubleTap()
                    PIP_PREVIOUS -> viewModel.handleLeftDoubleTap()
                }
                setPictureInPictureParams(createPipParams())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
        }
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }
    */

    /*
    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        PipState.mode = if (isInPictureInPictureMode) PipState.ON else PipState.OFF

        playerControls.lockControls(locked = PipState.mode == PipState.ON)
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        if (PipState.mode == PipState.ON) {
            // On Android TV it is required to hide controller in this PIP change callback
            playerControls.hideControls(true)
            binding.loadingIndicator.indicatorSize = binding.loadingIndicator.indicatorSize / 2
            mReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        PIP_PLAY -> {
                            player.paused = false
                        }
                        PIP_PAUSE -> {
                            player.paused = true
                        }
                        PIP_PREVIOUS -> {
                            changeEpisode(viewModel.getAdjacentEpisodeId(previous = true))
                        }
                        PIP_NEXT -> {
                            changeEpisode(viewModel.getAdjacentEpisodeId(previous = false))
                        }
                        PIP_SKIP -> {
                            doubleTapSeek(time = 10)
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mReceiver, IntentFilter(ACTION_MEDIA_CONTROL), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(mReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
            }
        } else {
            if (player.paused!!) playerControls.hideControls(false)
            binding.loadingIndicator.indicatorSize = binding.loadingIndicator.indicatorSize * 2
            if (mReceiver != null) {
                unregisterReceiver(mReceiver)
                mReceiver = null
            }
        }
    }

     */

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        toast(error.message)
        logcat(LogPriority.ERROR, error)
        finish()
    }

    // TODO(videolist)
    /*
    private fun setVideoList(
        qualityIndex: Int,
        videos: List<Video>?,
        fromStart: Boolean = false,
        position: Long? = null,
    ) {
        if (playerIsDestroyed) return
        currentVideoList = videos
        currentVideoList?.getOrNull(qualityIndex)?.let {
            streams.quality.index = qualityIndex
            setHttpOptions(it)
            if (viewModel.state.value.isLoadingEpisode) {
                viewModel.currentEpisode?.let { episode ->
                    val preservePos = playerPreferences.preserveWatchingPosition().get()
                    val resumePosition = position
                        ?: if ((episode.seen && !preservePos) || fromStart) {
                            0L
                        } else {
                            episode.last_second_seen
                        }
                    MPVLib.command(arrayOf("set", "start", "${resumePosition / 1000F}"))
                    playerControls.updatePlaybackDuration(resumePosition.toInt() / 1000)
                }
            } else {
                player.timePos?.let {
                    MPVLib.command(arrayOf("set", "start", "${player.timePos}"))
                }
            }
            streams.subtitle.tracks = arrayOf(Track("nothing", "None")) + it.subtitleTracks.toTypedArray()
            streams.audio.tracks = arrayOf(Track("nothing", "None")) + it.audioTracks.toTypedArray()
            MPVLib.command(arrayOf("loadfile", parseVideoUrl(it.videoUrl)))
        }
        refreshUi()
    }

     */

    // TODO(videolist)
    private fun parseVideoUrl(videoUrl: String?): String? {
        val uri = Uri.parse(videoUrl)
        return openContentFd(uri) ?: videoUrl
    }

    // TODO(videolist)
    private fun openContentFd(uri: Uri): String? {
        if (uri.scheme != "content") return null
        val resolver = applicationContext.contentResolver
        logcat { "Resolving content URI: $uri" }
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            logcat { "Failed to open content fd: $e" }
            return null
        }
        // Find out real file path and see if we can read it directly
        try {
            val path = File("/proc/self/fd/$fd").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                logcat { "Found real file path: $path" }
                ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
                return path
            }
        } catch (_: Exception) {}
        // Else, pass the fd to mpv
        return "fdclose://$fd"
    }

    // TODO(videolist)
    private fun setHttpOptions(video: Video) {
        if (viewModel.isEpisodeOnline() != true) return
        val source = viewModel.currentSource as AnimeHttpSource

        val headers = (video.headers ?: source.headers)
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }
            .toMutableMap()

        val httpHeaderString = headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        MPVLib.setOptionString("http-header-fields", httpHeaderString)

        // need to fix the cache
        // MPVLib.setOptionString("cache-on-disk", "yes")
        // val cacheDir = File(applicationContext.filesDir, "media").path
        // MPVLib.setOptionString("cache-dir", cacheDir)
    }

    // TODO(videolist)
    /*
    private fun clearTracks() {
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!player.tracks.containsKey(type)) {
                continue
            }
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            when (type) {
                "video" -> MPVLib.command(arrayOf("video-remove", "$mpvId"))
                "audio" -> MPVLib.command(arrayOf("audio-remove", "$mpvId"))
                "sub" -> MPVLib.command(arrayOf("sub-remove", "$mpvId"))
            }
        }
    }

     */

    // TODO(videolist)
    /*
    private val subtitleSelect = SubtitleSelect(subtitlePreferences)
    private fun selectSubtitle(subtitleTracks: List<Track>, index: Int, embedded: Boolean = false) {
        val offset = if (embedded) 0 else 1
        streams.subtitle.index = index + offset
        val tracks = player.tracks.getValue("sub")
        val selectedLoadedTrack = tracks.firstOrNull {
            it.name == subtitleTracks[index].url ||
                it.mpvId.toString() == subtitleTracks[index].url
        }
        selectedLoadedTrack?.let { player.sid = it.mpvId }
            ?: MPVLib.command(
                arrayOf(
                    "sub-add",
                    subtitleTracks[index].url,
                    "select",
                    subtitleTracks[index].url,
                ),
            )
    }
    */

    // TODO: exception java.util.ConcurrentModificationException:
    //  UPDATE: MAY HAVE BEEN FIXED
    // at java.lang.Object java.util.ArrayList$Itr.next() (ArrayList.java:860)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.fileLoaded() (PlayerActivity.kt:1874)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.event(int) (PlayerActivity.kt:1566)
    // at void is.xyz.mpv.MPVLib.event(int) (MPVLib.java:86)
    internal suspend fun fileLoaded() {
        setMpvMediaTitle()
        // TODO(videolist)
        // clearTracks()
        // player.loadTracks()
        //setupSubtitleTracks()
        //setupAudioTracks()

        /*
        viewModel.viewModelScope.launchUI {
            if (playerPreferences.adjustOrientationVideoDimensions().get()) {
                if ((player.videoW ?: 1) / (player.videoH ?: 1) >= 1) {
                    this@PlayerActivity.requestedOrientation =
                        playerPreferences.defaultPlayerOrientationLandscape().get()

                    switchControlsOrientation(true)
                } else {
                    this@PlayerActivity.requestedOrientation =
                        playerPreferences.defaultPlayerOrientationPortrait().get()

                    switchControlsOrientation(false)
                }
            }

            viewModel.mutableState.update {
                it.copy(isLoadingEpisode = false)
            }
        }

         */
        // aniSkip stuff
        // TODO(aniskip)
        /*
        waitingAniSkip = gesturePreferences.waitingTimeAniSkip().get()
        runBlocking {
            if (aniSkipEnable) {
                aniSkipInterval = viewModel.aniSkipResponse(player.duration)
                aniSkipInterval?.let {
                    aniskipStamps = it
                    updateChapters(it, player.duration)
                }
            }
        }

         */
    }

    // TODO(videolist)
    /*
    private fun setupSubtitleTracks() {
        streams.subtitle.tracks += player.tracks.getOrElse("sub") { emptyList() }
            .drop(1).map { track ->
                Track(track.mpvId.toString(), track.name)
            }.toTypedArray()
        if (hadPreviousSubs) {
            streams.subtitle.tracks.getOrNull(streams.subtitle.index)?.let { sub ->
                MPVLib.command(arrayOf("sub-add", sub.url, "select", sub.url))
            }
            return
        }
        val subtitleTracks = currentVideoList?.getOrNull(streams.quality.index)
            ?.subtitleTracks?.takeIf { it.isNotEmpty() }

        subtitleTracks?.let { tracks ->
            val preferredIndex = subtitleSelect.getPreferredSubtitleIndex(tracks) ?: 0
            hadPreviousSubs = true
            selectSubtitle(tracks, preferredIndex)
        } ?: let {
            val tracks = streams.subtitle.tracks.toList()
            val preferredIndex = subtitleSelect.getPreferredSubtitleIndex(tracks)
                ?: let {
                    val mpvSub = player.tracks["sub"]?.toTypedArray()?.firstOrNull { player.sid == it.mpvId }
                    mpvSub?.let {
                        streams.subtitle.tracks.indexOfFirst { it.url == mpvSub.mpvId.toString() }
                    }?.coerceAtLeast(0) ?: 0
                }
            selectSubtitle(tracks, preferredIndex, embedded = true)
        }
    }

     */

    // TODO(videolist)
    /*
    private fun setupAudioTracks() {
        val localLangName = LocaleHelper.getSimpleLocaleDisplayName()

        streams.audio.tracks += player.tracks.getOrElse("audio") { emptyList() }
            .drop(1).map { track ->
                Track(track.mpvId.toString(), track.name)
            }.toTypedArray()

        if (hadPreviousAudio) {
            streams.audio.tracks.getOrNull(streams.audio.index)?.let { audio ->
                MPVLib.command(arrayOf("audio-add", audio.url, "select", audio.url))
            }
        } else {
            currentVideoList?.getOrNull(streams.quality.index)
                ?.audioTracks?.let { tracks ->
                    val langIndex = tracks.indexOfFirst {
                        it.lang.contains(localLangName)
                    }
                    val requestedLanguage = if (langIndex == -1) 0 else langIndex
                    tracks.getOrNull(requestedLanguage)?.let { audio ->
                        hadPreviousAudio = true
                        streams.audio.index = requestedLanguage + 1
                        MPVLib.command(arrayOf("audio-add", audio.url, "select", audio.url))
                    }
                } ?: run {
                val mpvAudio = player.tracks["audio"]?.toTypedArray()?.firstOrNull { player.aid == it.mpvId }
                streams.audio.index = mpvAudio?.let {
                    streams.audio.tracks.indexOfFirst { it.url == mpvAudio.mpvId.toString() }
                }?.coerceAtLeast(0) ?: 0
            }
        }
    }

     */

    private fun setMpvMediaTitle() {
        val anime = viewModel.currentAnime ?: return
        val episode = viewModel.currentEpisode ?: return

        val epNumber = episode.episode_number.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = stringResource(
            MR.strings.mpv_media_title,
            anime.title,
            epNumber,
            episode.name,
        )

        MPVLib.setPropertyString("force-media-title", title)
    }

    /*
    private var aniskipStamps: List<Stamp> = emptyList()
    private fun updateChapters(stamps: List<Stamp>? = null, duration: Int? = null) {
        val aniskipStamps = stamps ?: aniskipStamps
        val sortedAniskipStamps = aniskipStamps.sortedBy { it.interval.startTime }
        val aniskipChapters = sortedAniskipStamps.mapIndexed { i, it ->
            val startTime = if (i == 0 && it.interval.startTime < 1.0) {
                0.0
            } else {
                it.interval.startTime
            }
            val startChapter = VideoChapter(
                index = -2, // Index -2 is used to indicate that this is an AniSkip chapter
                title = it.skipType.getString(),
                time = startTime,
            )
            val nextStart = sortedAniskipStamps.getOrNull(i + 1)?.interval?.startTime
            val isNotLastChapter = abs(it.interval.endTime - (duration?.toDouble() ?: -2.0)) > 1.0
            val isNotAdjacent = nextStart == null || (abs(it.interval.endTime - nextStart) > 1.0)
            if (isNotLastChapter && isNotAdjacent) {
                val endChapter = VideoChapter(
                    index = -1,
                    title = null,
                    time = it.interval.endTime,
                )
                return@mapIndexed listOf(startChapter, endChapter)
            } else {
                listOf(startChapter)
            }
        }.flatten()
        val playerChapters = player.loadChapters().filter { playerChapter ->
            aniskipChapters.none { aniskipChapter ->
                abs(aniskipChapter.time - playerChapter.time) < 1.0 && aniskipChapter.index == -2
            }
        }.sortedBy { it.time }.mapIndexed { i, it ->
            if (i == 0 && it.time < 1.0) {
                VideoChapter(
                    it.index,
                    it.title,
                    0.0,
                )
            } else {
                it
            }
        }
        val filteredAniskipChapters = aniskipChapters.filter { aniskipChapter ->
            playerChapters.none { playerChapter ->
                abs(aniskipChapter.time - playerChapter.time) < 1.0 && aniskipChapter.index != -2
            }
        }
        val startChapter = if ((playerChapters + filteredAniskipChapters).isNotEmpty() &&
            playerChapters.none { it.time == 0.0 } &&
            filteredAniskipChapters.none { it.time == 0.0 }
        ) {
            listOf(
                VideoChapter(
                    index = -1,
                    title = null,
                    time = 0.0,
                ),
            )
        } else {
            emptyList()
        }
        val combinedChapters = (startChapter + playerChapters + filteredAniskipChapters).sortedBy { it.time }
        videoChapters = combinedChapters
    }

    private val aniSkipEnable = gesturePreferences.aniSkipEnabled().get()
    private val netflixStyle = gesturePreferences.enableNetflixStyleAniSkip().get()

    private var aniSkipInterval: List<Stamp>? = null
    private var waitingAniSkip = gesturePreferences.waitingTimeAniSkip().get()

    private var skipType: SkipType? = null

    private suspend fun aniSkipStuff(position: Long) {
        if (!aniSkipEnable) return
        // if it doesn't find any interval it will show the +85 button
        if (aniSkipInterval == null) return

        val autoSkipAniSkip = gesturePreferences.autoSkipAniSkip().get()

        skipType =
            aniSkipInterval
                ?.firstOrNull {
                    it.interval.startTime <= position &&
                        it.interval.endTime > position
                }?.skipType
        skipType?.let { skipType ->
            val aniSkipPlayerUtils = AniSkipApi.PlayerUtils(binding, aniSkipInterval!!)
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                if (waitingAniSkip == gesturePreferences.waitingTimeAniSkip().get()) {
                    toast(
                        "AniSkip: ${stringResource(MR.strings.player_aniskip_dontskip_toast,waitingAniSkip)}",
                    )
                }
                aniSkipPlayerUtils.showSkipButton(skipType, waitingAniSkip)
                waitingAniSkip--
            } else if (autoSkipAniSkip) {
                skipType.let {
                    MPVLib.command(
                        arrayOf(
                            "seek",
                            "${aniSkipInterval!!.first{it.skipType == skipType}.interval.endTime}",
                            "absolute",
                        ),
                    )
                }
            } else {
                aniSkipPlayerUtils.showSkipButton(skipType)
            }
        } ?: run {
            refreshUi()
        }
    }
    */

    // mpv events

    internal fun onObserverEvent(property: String, value: Long) {
        // if (player.isExiting) return
        when (property) {
            "time-pos" -> viewModel.updatePlayBackPos(value.toFloat())
            "demuxer-cache-time" -> viewModel.updateReadAhead(value = value)
            "volume" -> viewModel.setMPVVolume(value.toInt())
            "volume-max" -> viewModel.volumeBoostCap = value.toInt() - 100
            "chapter" -> viewModel.updateChapter(value)
            "duration" -> viewModel.duration.update { value.toFloat() }
        }
    }

    internal fun onObserverEvent(property: String) {
        // if (player.isExiting) return
        when (property) {
            "chapter-list" -> {
                viewModel.loadChapters()
                viewModel.updateChapter(0)
            }
            // TODO(tracklist)
            // "track-list" -> viewModel.loadTracks()
        }
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player.isExiting) return
        when (property) {
            "pause" -> {
                if (value) {
                    viewModel.pause()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    viewModel.unpause()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            "paused-for-cache" -> {
                viewModel.isLoading.update { value }
            }

            "seeking" -> {
                viewModel.isLoading.update { value }
            }

            "eof-reached" -> {
                endFile(value)
            }
        }
    }

    val trackId: (String) -> Int? = {
        when (it) {
            "auto" -> null
            "no" -> -1
            else -> it.toInt()
        }
    }

    internal fun onObserverEvent(property: String, value: String) {
        // if (player.isExiting) return
        when (property.substringBeforeLast("/")) {
            "aid" -> trackId(value)?.let { viewModel.updateAudio(it) }
            "sid" -> trackId(value)?.let { viewModel.updateSubtitle(it, viewModel.selectedSubtitles.value.second) }
            "secondary-sid" -> trackId(value)?.let { viewModel.updateSubtitle(viewModel.selectedSubtitles.value.first, it) }
            "hwdec", "hwdec-current" -> viewModel.getDecoder()
            // TODO(custombutton)
            // "user-data/mpvkt" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @SuppressLint("NewApi")
    internal fun onObserverEvent(property: String, value: Double) {
        // if (player.isExiting) return
        when (property) {
            "speed" -> viewModel.playbackSpeed.update { value.toFloat() }
            // "video-params/aspect" -> if (isPipSupported) createPipParams()
        }
    }

    internal fun event(eventId: Int) {
        // if (player.isExiting) return
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                viewModel.viewModelScope.launchIO { fileLoaded() }
                /*
                fileName = getFileName(intent)
                viewModel.mediaTitle.update {
                    val mediaTitle = MPVLib.getPropertyString("media-title")
                    if (mediaTitle.isBlank() || mediaTitle.isDigitsOnly()) fileName else mediaTitle
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    loadVideoPlaybackState(fileName)
                }
                setOrientation()
                viewModel.changeVideoAspect(playerPreferences.videoAspect.get())

                 */
            }

            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel.isLoading.update { true }
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
        }
    }

    private fun endFile(eofReached: Boolean) {
        if (eofReached && playerPreferences.autoplayEnabled().get()) {
            // delay(1000L)
            changeEpisode(
                viewModel.getAdjacentEpisodeId(previous = false),
                autoPlay = true,
            )
        }
    }
}
