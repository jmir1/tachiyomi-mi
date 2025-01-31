/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Code is a mix between PlayerActivity from mpvKt and the former
 * PlayerActivity from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

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
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.hippo.unifile.UniFile
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.PlayerLayoutBinding
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.Stamp
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs
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
    private val gesturePreferences: GesturePreferences by lazy { viewModel.gesturePreferences }
    private val playerPreferences: PlayerPreferences by lazy { viewModel.playerPreferences }
    private val audioPreferences: AudioPreferences = Injekt.get()
    private val advancedPlayerPreferences: AdvancedPlayerPreferences = Injekt.get()
    private val networkPreferences: NetworkPreferences = Injekt.get()
    private val storageManager: StorageManager = Injekt.get()

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}

    private var pipRect: Rect? = null
    val isPipSupportedAndEnabled by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            playerPreferences.enablePip().get()
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
            viewModel.updateIsLoadingEpisode(true)

            val initResult = viewModel.init(animeId, episodeId, vidList, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withUIContext {
                    setInitialEpisodeError(exception)
                }
            }

            lifecycleScope.launch {
                setVideoList(
                    qualityIndex = initResult.first.videoIndex,
                    videos = initResult.first.videoList,
                    position = initResult.first.position,
                )
            }
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

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.controls.setContent {
            TachiyomiTheme {
                PlayerControls(
                    viewModel = viewModel,
                    onBackPress = {
                        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
                            enterPictureInPictureMode(createPipParams())
                        } else {
                            finish()
                        }
                    },
                    modifier = Modifier.onGloballyPositioned {
                        pipRect = run {
                            val boundsInWindow = it.boundsInWindow()
                            Rect(
                                boundsInWindow.left.toInt(),
                                boundsInWindow.top.toInt(),
                                boundsInWindow.right.toInt(),
                                boundsInWindow.bottom.toInt(),
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

        player.isExiting = true
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
        window.attributes.screenBrightness.let {
            if (playerPreferences.rememberPlayerBrightness().get() && it != -1f) {
                playerPreferences.playerBrightnessValue().set(it)
            }
        }

        if (isInPictureInPictureMode) {
            finishAndRemoveTask()
        }

        super.onStop()
    }

    override fun onUserLeaveHint() {
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isPipSupportedAndEnabled && player.paused == false && playerPreferences.pipOnExit().get()) {
            if (viewModel.sheetShown.value == Sheets.None &&
                viewModel.panelShown.value == Panels.None &&
                viewModel.dialogShown.value == Dialogs.None
            ) {
                enterPictureInPictureMode()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
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

    private fun executeMPVCommand(commands: Array<String>) {
        if (!player.isExiting) {
            MPVLib.command(commands)
        }
    }

    private fun setupPlayerMPV() {
        val logLevel = if (networkPreferences.verboseLogging().get()) "info" else "warn"

        val configDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            storageManager.getMPVConfigDirectory()!!.filePath!!
        } else {
            applicationContext.filesDir.path
        }

        val mpvConfFile = File("$configDir/mpv.conf")
        advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
        val mpvInputFile = File("$configDir/input.conf")
        advancedPlayerPreferences.mpvInput().get().let { mpvInputFile.writeText(it) }

        copyScripts()
        copyAssets(configDir)
        copyFontsDirectory()

        MPVLib.setOptionString("sub-ass-force-margins", "yes")
        MPVLib.setOptionString("sub-use-margins", "yes")

        player.initialize(
            configDir = configDir,
            cacheDir = applicationContext.cacheDir.path,
            logLvl = logLevel,
        )
        MPVLib.addLogObserver(playerObserver)
        MPVLib.addObserver(playerObserver)
    }

    private fun copyScripts() {
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
        if (advancedPlayerPreferences.mpvScripts().get()) {
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

        // Copy over the bridge file
        val luaFile = scriptsDir()?.createFile("aniyomi.lua")
        val luaBridge = assets.open("aniyomi.lua")
        luaFile?.openOutputStream()?.bufferedWriter()?.use { scriptLua ->
            luaBridge.bufferedReader().use { scriptLua.write(it.readText()) }
        }
    }

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

    fun setupCustomButtons(buttons: List<CustomButton>) {
        CoroutineScope(Dispatchers.IO).launchIO {
            val scriptsDir = {
                UniFile.fromFile(applicationContext.filesDir)?.createDirectory("scripts")
            }

            val primaryButtonId = viewModel.primaryButton.value?.id ?: 0L

            val customButtonsContent = buildString {
                appendLine("local lua_modules = mp.find_config_file('scripts')")
                appendLine("if lua_modules then")
                append(
                    "package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '",
                )
                append(scriptsDir()!!.filePath)
                appendLine("' .. '/?.lua'")
                appendLine("end")
                appendLine("local aniyomi = require 'aniyomi'")
                buttons.forEach { button ->
                    appendLine(button.getButtonOnStartup(primaryButtonId))
                    appendLine("function button${button.id}()")
                    appendLine(button.getButtonContent(primaryButtonId))
                    appendLine("end")
                    appendLine("mp.register_script_message('call_button_${button.id}', button${button.id})")
                    appendLine("function button${button.id}long()")
                    appendLine(button.getButtonLongPressContent(primaryButtonId))
                    appendLine("end")
                    appendLine("mp.register_script_message('call_button_${button.id}_long', button${button.id}long)")
                }
            }

            val file = scriptsDir()?.createFile("custombuttons.lua")
            file?.openOutputStream()?.bufferedWriter()?.use {
                it.write(customButtonsContent)
            }

            file?.let {
                MPVLib.command(arrayOf("load-script", it.filePath))
            }
        }
    }

    private fun setupPlayerAudio() {
        with(audioPreferences) {
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

    override fun onResume() {
        super.onResume()

        viewModel.currentVolume.update {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.changeVideoAspect(playerPreferences.aspectState().get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    // A bunch of observers

    internal fun onObserverEvent(property: String, value: Long) {
        if (player.isExiting) return
        when (property) {
            "time-pos" -> {
                viewModel.updatePlayBackPos(value.toFloat())
                viewModel.aniSkipStuff(value)
            }
            "demuxer-cache-time" -> viewModel.updateReadAhead(value = value)
            "volume" -> viewModel.setMPVVolume(value.toInt())
            "volume-max" -> viewModel.volumeBoostCap = value.toInt() - 100
            "chapter" -> viewModel.updateChapter(value)
            "duration" -> viewModel.duration.update { value.toFloat() }
            "user-data/current-anime/intro-length" -> viewModel.setAnimeSkipIntroLength(value)
        }
    }

    internal fun onObserverEvent(property: String) {
        if (player.isExiting) return
        when (property) {
            "chapter-list" -> {
                viewModel.loadChapters()
                viewModel.updateChapter(0)
            }
            "track-list" -> viewModel.loadTracks()
        }
    }

    internal fun onObserverEvent(property: String, value: Boolean) {
        if (player.isExiting) return
        when (property) {
            "pause" -> {
                if (value && player.paused == true) {
                    viewModel.pause()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!value && player.paused == false) {
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
        if (player.isExiting) return
        when (property.substringBeforeLast("/")) {
            "aid" -> trackId(value)?.let { viewModel.updateAudio(it) }
            "sid" -> trackId(value)?.let { viewModel.updateSubtitle(it, viewModel.selectedSubtitles.value.second) }
            "secondary-sid" -> trackId(value)?.let {
                viewModel.updateSubtitle(viewModel.selectedSubtitles.value.first, it)
            }
            "hwdec", "hwdec-current" -> viewModel.getDecoder()
            "user-data/aniyomi" -> viewModel.handleLuaInvocation(property, value)
        }
    }

    @SuppressLint("NewApi")
    internal fun onObserverEvent(property: String, value: Double) {
        if (player.isExiting) return
        when (property) {
            "speed" -> viewModel.playbackSpeed.update { value.toFloat() }
            "video-params/aspect" -> if (isPipSupportedAndEnabled) createPipParams()
        }
    }

    internal fun event(eventId: Int) {
        if (player.isExiting) return
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                viewModel.viewModelScope.launchIO { fileLoaded() }
            }
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel.isLoading.update { true }
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
        }
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val anime = viewModel.currentAnime.value
            val episode = viewModel.currentEpisode.value

            if (anime != null && episode != null) {
                builder.setTitle(anime.title).setSubtitle(episode.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit().get()
            builder.setAutoEnterEnabled(player.paused == false && autoEnter)
            builder.setSeamlessResizeEnabled(player.paused == false && autoEnter)
        }
        builder.setActions(
            createPipActions(
                context = this,
                isPaused = player.paused ?: true,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
                playlistCount = viewModel.currentPlaylist.value.size,
                playlistPosition = viewModel.getCurrentEpisodeIndex(),
            ),
        )
        builder.setSourceRectHint(pipRect)
        player.videoH?.let {
            val height = it
            val width = it * player.getVideoOutAspect()!!
            val rational = Rational(height, width.toInt()).toFloat()
            if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
        }
        return builder.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }
        } else {
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
                        PIP_NEXT -> viewModel.changeEpisode(false)
                        PIP_PREVIOUS -> viewModel.changeEpisode(true)
                        PIP_SKIP -> viewModel.seekBy(10)
                    }
                    setPictureInPictureParams(createPipParams())
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setupPlayerOrientation() {
        if (player.isExiting) return
        requestedOrientation = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((player.getVideoOutAspect() ?: 0.0) > 1.0) {
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
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

                            SingleActionGesture.Switch -> {}
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

                            SingleActionGesture.Switch -> {}
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

                            SingleActionGesture.Switch -> viewModel.changeEpisode(true)
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

                            SingleActionGesture.Switch -> viewModel.changeEpisode(false)
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
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // ==== END MPVKT ====

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    override fun finishAndRemoveTask() {
        viewModel.deletePendingEpisodes()
        super.finishAndRemoveTask()
    }

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        viewModel.sheetShown.update { _ -> Sheets.None }
        viewModel.panelShown.update { _ -> Panels.None }
        viewModel.pause()
        viewModel.isLoading.update { _ -> true }

        aniskipStamps = emptyList()

        lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()

            when (val switchMethod = viewModel.loadEpisode(episodeId)) {
                null -> {
                    if (viewModel.currentAnime.value != null && !autoPlay) {
                        launchUI { toast(MR.strings.no_next_episode) }
                    }
                    viewModel.isLoading.update { _ -> false }
                }

                else -> {
                    if (switchMethod.first != null) {
                        when {
                            switchMethod.first!!.isEmpty() -> setInitialEpisodeError(
                                Exception("Video list is empty."),
                            )
                            else -> {
                                setVideoList(qualityIndex = 0, switchMethod.first!!)
                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (isInPictureInPictureMode && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.second) }
                    }
                }
            }
        }

        viewModel.updateHasPreviousEpisode(
            viewModel.getCurrentEpisodeIndex() != 0,
        )
        viewModel.updateHasNextEpisode(
            viewModel.getCurrentEpisodeIndex() != viewModel.currentPlaylist.value.size - 1,
        )
    }

    fun setVideoList(
        qualityIndex: Int,
        videos: List<Video>?,
        fromStart: Boolean = false,
        position: Long? = null,
    ) {
        if (player.isExiting) return
        viewModel.updateVideoList(videos ?: emptyList())
        if (videos == null) return

        videos.getOrNull(qualityIndex)?.let {
            viewModel.setVideoIndex(qualityIndex)
            setHttpOptions(it)
            if (viewModel.isLoadingEpisode.value) {
                viewModel.currentEpisode.value?.let { episode ->
                    val preservePos = playerPreferences.preserveWatchingPosition().get()
                    val resumePosition = position
                        ?: if ((episode.seen && !preservePos) || fromStart) {
                            0L
                        } else {
                            episode.last_second_seen
                        }
                    MPVLib.command(arrayOf("set", "start", "${resumePosition / 1000F}"))
                }
            } else {
                player.timePos?.let {
                    MPVLib.command(arrayOf("set", "start", "${player.timePos}"))
                }
            }

            MPVLib.command(arrayOf("loadfile", parseVideoUrl(it.videoUrl)))
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        toast(error.message)
        logcat(LogPriority.ERROR, error)
        finish()
    }

    private fun parseVideoUrl(videoUrl: String?): String? {
        return Uri.parse(videoUrl).resolveUri(this)
            ?: videoUrl
    }

    private fun setHttpOptions(video: Video) {
        if (viewModel.isEpisodeOnline() != true) return
        val source = viewModel.currentSource.value as? AnimeHttpSource ?: return

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

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

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

    // TODO: exception java.util.ConcurrentModificationException:
    //  UPDATE: MAY HAVE BEEN FIXED
    // at java.lang.Object java.util.ArrayList$Itr.next() (ArrayList.java:860)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.fileLoaded() (PlayerActivity.kt:1874)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.event(int) (PlayerActivity.kt:1566)
    // at void is.xyz.mpv.MPVLib.event(int) (MPVLib.java:86)
    private fun fileLoaded() {
        if (player.isExiting) return
        setMpvMediaTitle()
        setupPlayerOrientation()
        setupTracks()

        // aniSkip stuff
        viewModel.waitingAniSkip = gesturePreferences.waitingTimeAniSkip().get()
        runBlocking {
            if (viewModel.aniSkipEnable) {
                viewModel.aniSkipInterval = viewModel.aniSkipResponse(player.duration)
                viewModel.aniSkipInterval?.let {
                    aniskipStamps = it
                    updateChapters(it, player.duration)
                }
            }
        }
    }

    private fun setupTracks() {
        if (player.isExiting) return
        viewModel.isLoadingTracks.update { _ -> true }

        val audioTracks = viewModel.videoList.value.getOrNull(viewModel.selectedVideoIndex.value)
            ?.audioTracks?.takeIf { it.isNotEmpty() }
        val subtitleTracks = viewModel.videoList.value.getOrNull(viewModel.selectedVideoIndex.value)
            ?.subtitleTracks?.takeIf { it.isNotEmpty() }

        // If no external audio or subtitle tracks are present, loadTracks() won't be
        // called and we need to call onFinishLoadingTracks() manually
        if (audioTracks == null && subtitleTracks == null) {
            viewModel.onFinishLoadingTracks()
            return
        }

        audioTracks?.forEach { audio ->
            executeMPVCommand(arrayOf("audio-add", audio.url, "auto", audio.lang))
        }
        subtitleTracks?.forEach { sub ->
            executeMPVCommand(arrayOf("sub-add", sub.url, "auto", sub.lang))
        }

        viewModel.isLoadingTracks.update { _ -> false }
    }

    private fun setMpvMediaTitle() {
        if (player.isExiting) return
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return

        viewModel.animeTitle.update { _ -> anime.title }
        viewModel.mediaTitle.update { _ -> episode.name }

        // Write to mpv table
        MPVLib.setPropertyString("user-data/current-anime/episode-title", episode.name)

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

    private fun endFile(eofReached: Boolean) {
        if (eofReached && playerPreferences.autoplayEnabled().get()) {
            viewModel.changeEpisode(previous = false, autoPlay = true)
        }
    }

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
            val startChapter = IndexedSegment(
                index = -2, // Index -2 is used to indicate that this is an AniSkip chapter
                name = it.skipType.getString(),
                start = startTime.toFloat(),
                color = Color(0xFFD8BBDF),
            )
            val nextStart = sortedAniskipStamps.getOrNull(i + 1)?.interval?.startTime
            val isNotLastChapter = abs(it.interval.endTime - (duration?.toDouble() ?: -2.0)) > 1.0
            val isNotAdjacent = nextStart == null || (abs(it.interval.endTime - nextStart) > 1.0)
            if (isNotLastChapter && isNotAdjacent) {
                val endChapter = IndexedSegment(
                    index = -1,
                    name = "",
                    start = it.interval.endTime.toFloat(),
                )
                return@mapIndexed listOf(startChapter, endChapter)
            } else {
                listOf(startChapter)
            }
        }.flatten()
        val playerChapters = viewModel.chapters.value.filter { playerChapter ->
            aniskipChapters.none { aniskipChapter ->
                abs(aniskipChapter.start - playerChapter.start) < 1.0 && aniskipChapter.index == -2
            }
        }.map {
            IndexedSegment(it.name, it.start, it.color)
        }.sortedBy { it.start }.mapIndexed { i, it ->
            if (i == 0 && it.start < 1.0) {
                IndexedSegment(
                    it.name,
                    0.0f,
                    index = it.index,
                )
            } else {
                it
            }
        }
        val filteredAniskipChapters = aniskipChapters.filter { aniskipChapter ->
            playerChapters.none { playerChapter ->
                abs(aniskipChapter.start - playerChapter.start) < 1.0 && aniskipChapter.index != -2
            }
        }
        val startChapter = if ((playerChapters + filteredAniskipChapters).isNotEmpty() &&
            playerChapters.none { it.start == 0.0f } &&
            filteredAniskipChapters.none { it.start == 0.0f }
        ) {
            listOf(
                IndexedSegment(
                    index = -1,
                    name = "",
                    start = 0.0f,
                ),
            )
        } else {
            emptyList()
        }
        val combinedChapters = (startChapter + playerChapters + filteredAniskipChapters).sortedBy { it.start }
        viewModel.updateChapters(combinedChapters)
    }
}
