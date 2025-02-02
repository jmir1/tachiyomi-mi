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
 * Code is a mix between PlayerViewModel from mpvKt and the former
 * PlayerViewModel from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.runtime.Immutable
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.items.episode.model.toDbEpisode
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonFetchState
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.AniSkipApi
import eu.kanade.tachiyomi.util.SkipType
import eu.kanade.tachiyomi.util.Stamp
import eu.kanade.tachiyomi.util.TrackSelect
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.episode.filterDownloadedEpisodes
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.util.Date

class PlayerViewModelProviderFactory(
    private val activity: PlayerActivity,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return PlayerViewModel(activity, extras.createSavedStateHandle()) as T
    }
}

class PlayerViewModel @JvmOverloads constructor(
    private val activity: PlayerActivity,
    private val savedState: SavedStateHandle,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val upsertHistory: UpsertAnimeHistory = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
    private val trackSelect: TrackSelect = Injekt.get(),
    private val getIncognitoState: GetAnimeIncognitoState = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
) : ViewModel() {

    private val _currentPlaylist = MutableStateFlow<List<Episode>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    private val _currentAnime = MutableStateFlow<Anime?>(null)
    val currentAnime = _currentAnime.asStateFlow()

    private val _currentSource = MutableStateFlow<AnimeSource?>(null)
    val currentSource = _currentSource.asStateFlow()

    private val _isEpisodeOnline = MutableStateFlow(false)
    val isEpisodeOnline = _isEpisodeOnline.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode = _isLoadingEpisode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(getDecoderFromValue(MPVLib.getPropertyString("hwdec")))
    val currentDecoder = _currentDecoder.asStateFlow()

    val mediaTitle = MutableStateFlow("")
    val animeTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val playbackSpeed = MutableStateFlow(playerPreferences.playerSpeed().get())

    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()
    private val _selectedSubtitles = MutableStateFlow(Pair(-1, -1))
    val selectedSubtitles = _selectedSubtitles.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()
    private val _selectedAudio = MutableStateFlow(-1)
    val selectedAudio = _selectedAudio.asStateFlow()

    val isLoadingTracks = MutableStateFlow(true)

    private val _videoList = MutableStateFlow<List<Video>>(emptyList())
    val videoList = _videoList.asStateFlow()
    private val _selectedVideoIndex = MutableStateFlow(-1)
    val selectedVideoIndex = _selectedVideoIndex.asStateFlow()

    private val _chapters = MutableStateFlow<List<IndexedSegment>>(emptyList())
    val chapters = _chapters.asStateFlow()
    private val _currentChapter = MutableStateFlow<IndexedSegment?>(null)
    val currentChapter = _currentChapter.asStateFlow()

    private val _pos = MutableStateFlow(0f)
    val pos = _pos.asStateFlow()

    val duration = MutableStateFlow(0f)

    private val _readAhead = MutableStateFlow(0f)
    val readAhead = _readAhead.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()

    // False because the video shouldn't start paused
    private val _pausedState = MutableStateFlow<Boolean?>(false)
    val pausedState = _pausedState.asStateFlow()

    private val _controlsShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(
        runCatching {
            Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f },
    )
    val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val currentMPVVolume = MutableStateFlow(MPVLib.getPropertyInt("volume"))
    var volumeBoostCap: Int = MPVLib.getPropertyInt("volume-max")

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)
    val dialogShown = MutableStateFlow<Dialogs>(Dialogs.None)

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    private val _aniskipButton = MutableStateFlow<String?>(null)
    val aniskipButton = _aniskipButton.asStateFlow()

    val cachePath: String = activity.cacheDir.path

    private val _customButtons = MutableStateFlow<CustomButtonFetchState>(CustomButtonFetchState.Loading)
    val customButtons = _customButtons.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButton?>(null)
    val primaryButton = _primaryButton.asStateFlow()

    private fun updateAniskipButton(value: String?) {
        _aniskipButton.update { _ -> value }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buttons = getCustomButtons.getAll()
                buttons.firstOrNull { it.isFavorite }?.let {
                    _primaryButton.update { _ -> it }
                    // If the button text is not empty, it has been set buy a lua script in which
                    // case we don't want to override it
                    if (_primaryButtonTitle.value.isEmpty()) {
                        setPrimaryCustomButtonTitle(it)
                    }
                }
                activity.setupCustomButtons(buttons)
                _customButtons.update { _ -> CustomButtonFetchState.Success(buttons.toImmutableList()) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _customButtons.update { _ -> CustomButtonFetchState.Error(e.message ?: "Unable to fetch buttons") }
            }
        }
    }

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            pause()
            withUIContext { Injekt.get<Application>().toast(MR.strings.toast_sleep_timer_ended) }
        }
    }

    fun isEpisodeOnline(): Boolean? {
        val anime = currentAnime.value ?: return null
        val episode = currentEpisode.value ?: return null
        val source = currentSource.value ?: return null
        return source is AnimeHttpSource &&
            !EpisodeLoader.isDownload(
                episode.toDomainEpisode()!!,
                anime,
            )
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        _isLoadingEpisode.update { _ -> value }
    }

    private fun updateEpisodeList(episodeList: List<Episode>) {
        _currentPlaylist.update { _ -> filterEpisodeList(episodeList) }
    }

    fun getDecoder() {
        _currentDecoder.update { getDecoderFromValue(activity.player.hwdecActive) }
    }

    fun updateDecoder(decoder: Decoder) {
        MPVLib.setPropertyString("hwdec", decoder.value)
    }

    val getTrackLanguage: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/lang") ?: ""
        } else {
            activity.stringResource(MR.strings.off)
        }
    }
    val getTrackTitle: (Int) -> String = {
        if (it != -1) {
            MPVLib.getPropertyString("track-list/$it/title") ?: ""
        } else {
            activity.stringResource(MR.strings.off)
        }
    }
    val getTrackMPVId: (Int) -> Int = {
        if (it != -1) {
            MPVLib.getPropertyInt("track-list/$it/id")
        } else {
            -1
        }
    }
    val getTrackType: (Int) -> String? = {
        MPVLib.getPropertyString("track-list/$it/type")
    }

    private var trackLoadingJob: Job? = null
    fun loadTracks() {
        trackLoadingJob?.cancel()
        trackLoadingJob = viewModelScope.launch {
            val possibleTrackTypes = listOf("audio", "sub")
            val subTracks = mutableListOf<VideoTrack>()
            val audioTracks = mutableListOf(
                VideoTrack(-1, activity.stringResource(MR.strings.off), null),
            )
            try {
                val tracksCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                for (i in 0..<tracksCount) {
                    val type = getTrackType(i)
                    if (!possibleTrackTypes.contains(type) || type == null) continue
                    when (type) {
                        "sub" -> subTracks.add(VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                        "audio" -> audioTracks.add(VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                        else -> error("Unrecognized track type")
                    }
                }
            } catch (e: NullPointerException) {
                logcat(LogPriority.ERROR) { "Couldn't load tracks, probably cause mpv was destroyed" }
                return@launch
            }
            _subtitleTracks.update { subTracks }
            _audioTracks.update { audioTracks }

            if (!isLoadingTracks.value) {
                onFinishLoadingTracks()
            }
        }
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onFinishLoadingTracks() {
        val preferredSubtitle = trackSelect.getPreferredTrackIndex(subtitleTracks.value)
        (preferredSubtitle ?: subtitleTracks.value.firstOrNull())?.let {
            activity.player.sid = it.id
            activity.player.secondarySid = -1
        }

        val preferredAudio = trackSelect.getPreferredTrackIndex(audioTracks.value, subtitle = false)
        (preferredAudio ?: audioTracks.value.getOrNull(1))?.let {
            activity.player.aid = it.id
        }

        isLoadingTracks.update { _ -> true }
        updateIsLoadingEpisode(false)
        setPausedState()
    }

    @Immutable
    data class VideoTrack(
        val id: Int,
        val name: String,
        val language: String?,
    )

    fun loadChapters() {
        val chapters = mutableListOf<IndexedSegment>()
        val count = MPVLib.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyInt("chapter-list/$i/time")!!
            chapters.add(
                IndexedSegment(
                    name = title,
                    start = time.toFloat(),
                    index = 0,
                ),
            )
        }
        updateChapters(chapters.sortedBy { it.start })
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        _chapters.update { _ -> chapters }
    }

    fun selectChapter(index: Int) {
        val time = chapters.value[index].start
        seekTo(time.toInt())
    }

    fun updateChapter(index: Long) {
        if (chapters.value.isEmpty() || index == -1L) return
        _currentChapter.update { chapters.value.getOrNull(index.toInt()) ?: return }
    }

    fun updateVideoList(videoList: List<Video>) {
        _videoList.update { _ -> videoList }
    }

    fun setVideoIndex(idx: Int) {
        _selectedVideoIndex.update { _ -> idx }
    }

    fun selectVideo(video: Video) {
        updateIsLoadingEpisode(true)

        val idx = videoList.value.indexOf(video)

        updatePausedState()
        // Pause until everything has loaded
        pause()

        activity.setVideoList(
            qualityIndex = idx,
            videos = videoList.value,
        )
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(activity) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(activity) else null
        if (name == null) {
            MPVLib.command(arrayOf("audio-add", path, "cached"))
        } else {
            MPVLib.command(arrayOf("audio-add", path, "cached", name))
        }
    }

    fun selectAudio(id: Int) {
        activity.player.aid = id
    }

    fun updateAudio(id: Int) {
        _selectedAudio.update { id }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(activity) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(activity) else null
        if (name == null) {
            MPVLib.command(arrayOf("sub-add", path, "cached"))
        } else {
            MPVLib.command(arrayOf("sub-add", path, "cached", name))
        }
    }

    fun selectSub(id: Int) {
        val selectedSubs = selectedSubtitles.value
        _selectedSubtitles.update {
            when (id) {
                selectedSubs.first -> Pair(selectedSubs.second, -1)
                selectedSubs.second -> Pair(selectedSubs.first, -1)
                else -> {
                    if (selectedSubs.first != -1) {
                        Pair(selectedSubs.first, id)
                    } else {
                        Pair(id, -1)
                    }
                }
            }
        }
        activity.player.secondarySid = _selectedSubtitles.value.second
        activity.player.sid = _selectedSubtitles.value.first
    }

    fun updateSubtitle(sid: Int, secondarySid: Int) {
        _selectedSubtitles.update { Pair(sid, secondarySid) }
    }

    fun updatePlayBackPos(pos: Float) {
        onSecondReached(pos.toInt(), duration.value.toInt())
        _pos.update { pos }
    }

    fun updateReadAhead(value: Long) {
        _readAhead.update { value.toFloat() }
    }

    private fun updatePausedState() {
        _pausedState.update { _ -> paused.value }
    }

    private fun setPausedState() {
        pausedState.value?.let {
            if (it) {
                pause()
            } else {
                unpause()
            }

            _pausedState.update { _ -> null }
        }
    }

    fun pauseUnpause() {
        if (paused.value) {
            unpause()
        } else {
            pause()
        }
    }

    fun pause() {
        activity.player.paused = true
        _paused.update { true }
        runCatching {
            activity.setPictureInPictureParams(activity.createPipParams())
        }
    }

    fun unpause() {
        activity.player.paused = false
        _paused.update { false }
    }

    private val showStatusBar = playerPreferences.showSystemStatusBar().get()
    fun showControls() {
        if (sheetShown.value != Sheets.None ||
            panelShown.value != Panels.None ||
            dialogShown.value != Dialogs.None
        ) {
            return
        }
        if (showStatusBar) {
            activity.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        activity.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun showSheet(sheet: Sheets) {
        sheetShown.update { sheet }
        if (sheet == Sheets.None) {
            showControls()
        } else {
            hideControls()
            panelShown.update { Panels.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showPanel(panel: Panels) {
        panelShown.update { panel }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showDialog(dialog: Dialogs) {
        dialogShown.update { dialog }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
        }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        MPVLib.command(arrayOf("seek", offset.toString(), if (precise) "relative+exact" else "relative"))
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        if (position !in 0..(activity.player.duration ?: 0)) return
        MPVLib.command(arrayOf("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes"))
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        currentBrightness.update { _ -> brightness.coerceIn(-0.75f, 1f) }
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = brightness.coerceIn(0f, 1f)
        }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun changeVolumeBy(change: Int) {
        val mpvVolume = MPVLib.getPropertyInt("volume")
        if (volumeBoostCap > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
            if (finalMPVVolume in 100..volumeBoostCap + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        activity.audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0,
        )
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    fun setMPVVolume(volume: Int) {
        if (volume != currentMPVVolume.value) displayVolumeSlider()
        currentMPVVolume.update { volume }
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            MR.strings.enable_auto_play
        } else {
            MR.strings.disable_auto_play
        }
        playerUpdate.update { PlayerUpdates.ShowTextResource(textRes) }
        playerPreferences.autoplayEnabled().set(value)
    }

    @Suppress("DEPRECATION")
    fun changeVideoAspect(aspect: VideoAspect) {
        var ratio = -1.0
        var pan = 1.0
        when (aspect) {
            VideoAspect.Crop -> {
                pan = 1.0
            }

            VideoAspect.Fit -> {
                pan = 0.0
                MPVLib.setPropertyDouble("panscan", 0.0)
            }

            VideoAspect.Stretch -> {
                val dm = DisplayMetrics()
                activity.windowManager.defaultDisplay.getRealMetrics(dm)
                ratio = dm.widthPixels / dm.heightPixels.toDouble()
                pan = 0.0
            }
        }
        MPVLib.setPropertyDouble("panscan", pan)
        MPVLib.setPropertyDouble("video-aspect-override", ratio)
        playerPreferences.aspectState().set(aspect)
        playerUpdate.update { PlayerUpdates.AspectRatio }
    }

    fun cycleScreenRotations() {
        activity.requestedOrientation = when (activity.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorPortrait)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            else -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }
                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        dialogShown.update { Dialogs.None }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> showPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> showPanel(Panels.SubtitleDelay)
                    "audio_delay" -> showPanel(Panels.AudioDelay)
                    "video_filters" -> showPanel(Panels.VideoFilters)
                }
            }
            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }
            "reset_button_title" -> {
                _customButtons.value.getButtons().firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }
            "switch_episode" -> {
                when (data) {
                    "n" -> changeEpisode(false)
                    "p" -> changeEpisode(true)
                }
            }
            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = MPVLib.getPropertyInt(pickerProperty)
                showDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = { MPVLib.setPropertyInt(pickerProperty, it) },
                        onDismissRequest = { showDialog(Dialogs.None) },
                    ),
                )
            }
            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }
            "seek_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                if (seekValue.toFloat() < pos.value) {
                    leftSeekToWithText(seekValue.toInt(), text)
                } else {
                    rightSeekToWithText(seekValue.toInt(), text)
                }
            }
            "toggle_button" -> {
                when (data) {
                    "h" -> _primaryButton.update { null }
                    "s" -> {
                        if (_primaryButton.value == null) {
                            _primaryButton.update {
                                _customButtons.value.getButtons().firstOrNull { it.isFavorite }
                            }
                        }
                    }
                }
            }
            "seek_by" -> {
                val (dir, seekValue) = data.split("|")
                when (dir) {
                    "l" -> leftSeekBy(seekValue.toInt())
                    "r" -> rightSeekBy(seekValue.toInt())
                }
            }
        }

        MPVLib.setPropertyString(property, "")
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference().get()

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(value: String?) {
        _seekText.update { _ -> value }
    }

    fun leftSeekBy(value: Int) {
        if (pos.value > 0) {
            _doubleTapSeekAmount.value -= value
        }
        _isSeekingForwards.value = false
        seekBy(-value, gesturePreferences.playerSmoothSeek().get())
        if (gesturePreferences.showSeekBar().get()) showSeekBar()
    }

    fun rightSeekBy(value: Int) {
        if (pos.value < duration.value) {
            _doubleTapSeekAmount.value += value
        }
        _isSeekingForwards.value = true
        seekBy(value, gesturePreferences.playerSmoothSeek().get())
        if (gesturePreferences.showSeekBar().get()) showSeekBar()
    }

    fun leftSeek() {
        leftSeekBy(doubleTapToSeekDuration)
    }

    fun rightSeek() {
        rightSeekBy(doubleTapToSeekDuration)
    }

    private fun leftSeekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = false
        _doubleTapSeekAmount.value = -1
        _seekText.update { _ -> text }
        seekTo(seekValue)
        if (gesturePreferences.showSeekBar().get()) showSeekBar()
    }

    private fun rightSeekToWithText(seekDuration: Int, text: String?) {
        _isSeekingForwards.value = true
        _doubleTapSeekAmount.value = 1
        _seekText.update { _ -> text }
        seekTo(seekDuration)
        if (gesturePreferences.showSeekBar().get()) showSeekBar()
    }

    fun changeEpisode(previous: Boolean, autoPlay: Boolean = false) {
        if (previous && !hasPreviousEpisode.value) {
            activity.toast(activity.stringResource(MR.strings.no_prev_episode))
            return
        }

        if (!previous && !hasNextEpisode.value) {
            activity.toast(activity.stringResource(MR.strings.no_next_episode))
            return
        }

        activity.changeEpisode(getAdjacentEpisodeId(previous = previous), autoPlay = autoPlay)
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences.leftDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapLeft.keyCode))
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(true)
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences.centerDoubleTapGesture().get()) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapCenter.keyCode))
            }
            SingleActionGesture.Seek -> {}
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences.rightDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapRight.keyCode))
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(false)
        }
    }

    override fun onCleared() {
        if (currentEpisode.value != null) {
            saveWatchingProgress(currentEpisode.value!!)
            episodeToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    // ====== OLD ======

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(currentAnime.value?.source) }
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching().get()

    internal val relativeTime = uiPreferences.relativeTime().get()
    internal val dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get())

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Int>("quality_index") ?: 0
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The episode id of the currently loaded episode. Used to restore from process kill.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
        }

    private var episodeToDownload: AnimeDownload? = null

    private var currentVideoList: List<Video>? = null

    private fun filterEpisodeList(episodes: List<Episode>): List<Episode> {
        val anime = currentAnime.value ?: return episodes
        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        val episodesForPlayer = episodes.filterNot {
            anime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN &&
                !it.seen ||
                anime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN &&
                it.seen ||
                anime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED &&
                !downloadManager.isEpisodeDownloaded(
                    it.name,
                    it.scanlator,
                    anime.title,
                    anime.source,
                ) ||
                anime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED &&
                downloadManager.isEpisodeDownloaded(
                    it.name,
                    it.scanlator,
                    anime.title,
                    anime.source,
                ) ||
                anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED &&
                !it.bookmark ||
                anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED &&
                it.bookmark
        }.toMutableList()

        if (episodesForPlayer.all { it.id != episodeId }) {
            episodesForPlayer += listOf(selectedEpisode)
        }

        return episodesForPlayer
    }

    fun getCurrentEpisodeIndex(): Int {
        return currentPlaylist.value.indexOfFirst { currentEpisode.value?.id == it.id }
    }

    private fun getAdjacentEpisodeId(previous: Boolean): Long {
        val newIndex = if (previous) getCurrentEpisodeIndex() - 1 else getCurrentEpisodeIndex() + 1

        return when {
            previous && getCurrentEpisodeIndex() == 0 -> -1L
            !previous && currentPlaylist.value.lastIndex == getCurrentEpisodeIndex() -> -1L
            else -> currentPlaylist.value.getOrNull(newIndex)?.id ?: -1L
        }
    }

    fun updateHasNextEpisode(value: Boolean) {
        _hasNextEpisode.update { _ -> value }
    }

    fun updateHasPreviousEpisode(value: Boolean) {
        _hasPreviousEpisode.update { _ -> value }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentEpisode = currentEpisode.value ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    /**
     * Whether this presenter is initialized yet.
     */
    private fun needsInit(): Boolean {
        return currentAnime.value == null || currentEpisode.value == null
    }

    /**
     * Initializes this presenter with the given [animeId] and [initialEpisodeId]. This method will
     * fetch the anime from the database and initialize the episode.
     */
    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        vidList: String,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentVideoList, 0, null)
        if (!needsInit()) return Pair(defaultResult, Result.success(true))
        return try {
            val anime = getAnime.await(animeId)
            if (anime != null) {
                _currentAnime.update { _ -> anime }
                animeTitle.update { _ -> anime.title }
                sourceManager.isInitialized.first { it }
                if (episodeId == -1L) episodeId = initialEpisodeId

                checkTrackers(anime)

                updateEpisodeList(initEpisodeList(anime))

                val episode = currentPlaylist.value.first { it.id == episodeId }
                mediaTitle.update { _ -> episode.name }

                val source = sourceManager.getOrStub(anime.source)

                _currentEpisode.update { _ -> episode }
                _currentSource.update { _ -> source }
                _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }

                _hasPreviousEpisode.update { _ -> getCurrentEpisodeIndex() != 0 }
                _hasNextEpisode.update { _ -> getCurrentEpisodeIndex() != currentPlaylist.value.size - 1 }

                // Write to mpv table
                MPVLib.setPropertyString("user-data/current-anime/anime-title", anime.title)
                MPVLib.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                MPVLib.setPropertyString(
                    "user-data/current-anime/category",
                    getAnimeCategories.await(anime.id).joinToString {
                        it.name
                    },
                )

                val currentEp = currentEpisode.value ?: throw Exception("No episode loaded.")
                if (vidList.isNotBlank()) {
                    currentVideoList = vidList.toVideoList().ifEmpty {
                        currentVideoList = null
                        throw Exception("Video selected from empty list?")
                    }
                    qualityIndex = vidIndex
                } else {
                    EpisodeLoader.getLinks(currentEp.toDomainEpisode()!!, anime, source)
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentVideoList = it }
                        ?: run {
                            currentVideoList = null
                            throw Exception("Video list is empty.")
                        }
                }

                val result = InitResult(
                    videoList = currentVideoList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            Pair(defaultResult, Result.failure(e))
        }
    }

    data class InitResult(
        val videoList: List<Video>?,
        val videoIndex: Int,
        val position: Long?,
    )

    private fun initEpisodeList(anime: Anime): List<Episode> {
        val episodes = runBlocking { getEpisodesByAnimeId.await(anime.id) }

        return episodes
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedEpisodes(anime)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }
    }

    private var hasTrackers: Boolean = false
    private val checkTrackers: (Anime) -> Unit = { anime ->
        val tracks = runBlocking { getTracks.await(anime.id) }
        hasTrackers = tracks.isNotEmpty()
    }

    suspend fun loadEpisode(episodeId: Long?): Pair<List<Video>?, String>? {
        val anime = currentAnime.value ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = currentPlaylist.value.firstOrNull { ep -> ep.id == episodeId } ?: return null

        _currentEpisode.update { _ -> chosenEpisode }
        _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }

        return withIOContext {
            try {
                val currentEpisode = currentEpisode.value ?: throw Exception("No episode loaded.")
                currentVideoList = EpisodeLoader.getLinks(
                    currentEpisode.toDomainEpisode()!!,
                    anime,
                    source,
                )
                this@PlayerViewModel.episodeId = currentEpisode.id!!
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            Pair(currentVideoList, anime.title + " - " + chosenEpisode.name)
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    private fun onSecondReached(position: Int, duration: Int) {
        if (isLoadingEpisode.value) return
        val currentEp = currentEpisode.value ?: return
        if (episodeId == -1L) return
        if (duration == 0) return

        val seconds = position * 1000L
        val totalSeconds = duration * 1000L
        // Save last second seen and mark as seen if needed
        currentEp.last_second_seen = seconds
        currentEp.total_seconds = totalSeconds

        episodePosition = seconds

        val progress = playerPreferences.progressPreference().get()
        val shouldTrack = !incognitoMode || hasTrackers
        if (seconds >= totalSeconds * progress && shouldTrack) {
            currentEp.seen = true
            updateTrackEpisodeSeen(currentEp)
            deleteEpisodeIfNeeded(currentEp)
        }

        saveWatchingProgress(currentEp)

        val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
        if (inDownloadRange) {
            downloadNextEpisodes()
        }
    }

    private fun downloadNextEpisodes() {
        if (downloadAheadAmount == 0) return
        val anime = currentAnime.value ?: return

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        if (getCurrentEpisodeIndex() == currentPlaylist.value.lastIndex) return
        val currentEpisode = currentEpisode.value ?: return

        val nextEpisode = currentPlaylist.value[getCurrentEpisodeIndex() + 1]
        val episodesAreDownloaded =
            EpisodeLoader.isDownload(currentEpisode.toDomainEpisode()!!, anime) &&
                EpisodeLoader.isDownload(nextEpisode.toDomainEpisode()!!, anime)

        viewModelScope.launchIO {
            if (!episodesAreDownloaded) {
                return@launchIO
            }
            val episodesToDownload = getNextEpisodes.await(anime.id, nextEpisode.id!!)
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = currentPlaylist.value.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        val episodeToDelete = currentPlaylist.value.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    fun saveCurrentEpisodeWatchingProgress() {
        currentEpisode.value?.let { saveWatchingProgress(it) }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        if (!incognitoMode || hasTrackers) {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id!!,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    lastSecondSeen = episode.last_second_seen,
                    totalSeconds = episode.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (!incognitoMode) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                AnimeHistoryUpdate(episodeId, seenAt),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        MPVLib.command(arrayOf("screenshot-to-file", filename, subtitleFlag))
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(anime.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as cover and notifies the UI of the result.
     */
    fun setAsCover(imageStream: () -> InputStream) {
        val anime = currentAnime.value ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                anime.editCover(Injekt.get(), imageStream())
                if (anime.isLocal() || anime.favorite) {
                    SetAsCover.Success
                } else {
                    SetAsCover.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsCover.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (basePreferences.incognitoMode().get() || !hasTrackers) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val anime = currentAnime.value ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = currentAnime.value ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength().get()
        val anime = currentAnime.value ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = currentAnime.value ?: return
        if (!anime.favorite) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            logcat(LogPriority.INFO) { "New Skip Intro Length is ${anime.skipIntroLength}" }
            _currentAnime.update { _ -> getAnime.await(anime.id) }
        }
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = currentEpisode.value ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<Stamp>? {
        val animeId = currentAnime.value?.id ?: return null
        val trackerManager = Injekt.get<TrackerManager>()
        var malId: Long?
        val episodeNumber = currentEpisode.value?.episode_number?.toInt() ?: return null
        if (getTracks.await(animeId).isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        getTracks.await(animeId).map { track ->
            val tracker = trackerManager.get(track.trackerId)
            malId = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> AniSkipApi().getMalIdFromAL(track.remoteId)
                else -> null
            }
            val duration = playerDuration ?: return null
            return malId?.let {
                AniSkipApi().getResult(it.toInt(), episodeNumber, duration.toLong())
            }
        }
        return null
    }

    val aniSkipEnable = gesturePreferences.aniSkipEnabled().get()
    private val netflixStyle = gesturePreferences.enableNetflixStyleAniSkip().get()

    var aniSkipInterval: List<Stamp>? = null
    private val defaultWaitingTime = gesturePreferences.waitingTimeAniSkip().get()
    var waitingAniSkip = defaultWaitingTime

    private var skipType: SkipType? = null

    fun aniSkipStuff(position: Long) {
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
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                if (waitingAniSkip == defaultWaitingTime) {
                    activity.toast(
                        "AniSkip: ${activity.stringResource(
                            MR.strings.player_aniskip_dontskip_toast,
                            skipType.getString(),
                            waitingAniSkip,
                        )}",
                    )
                }
                showAniskipButton(aniSkipInterval!!, skipType, waitingAniSkip)
                waitingAniSkip--
            } else if (autoSkipAniSkip) {
                rightSeekToWithText(
                    seekDuration = aniSkipInterval!!.first { it.skipType == skipType }.interval.endTime.toInt(),
                    text = activity.stringResource(MR.strings.player_aniskip_skip, skipType.getString()),
                )
            } else {
                showAniskipButton(skipType)
            }
        } ?: run {
            updateAniskipButton(null)
            waitingAniSkip = defaultWaitingTime
        }
    }

    private fun showAniskipButton(skipType: SkipType) {
        val skipButtonString = when (skipType) {
            SkipType.ED -> MR.strings.player_aniskip_ed
            SkipType.OP -> MR.strings.player_aniskip_op
            SkipType.RECAP -> MR.strings.player_aniskip_recap
            SkipType.MIXED_OP -> MR.strings.player_aniskip_mixedOp
        }

        updateAniskipButton(activity.stringResource(skipButtonString))
    }

    private fun showAniskipButton(aniSkipResponse: List<Stamp>, skipType: SkipType, waitingTime: Int) {
        val skipTime = when (skipType) {
            SkipType.ED -> aniSkipResponse.first { it.skipType == SkipType.ED }.interval
            SkipType.OP -> aniSkipResponse.first { it.skipType == SkipType.OP }.interval
            SkipType.RECAP -> aniSkipResponse.first { it.skipType == SkipType.RECAP }.interval
            SkipType.MIXED_OP -> aniSkipResponse.first { it.skipType == SkipType.MIXED_OP }.interval
        }
        if (waitingTime > -1) {
            if (waitingTime > 0) {
                updateAniskipButton(activity.stringResource(MR.strings.player_aniskip_dontskip))
            } else {
                rightSeekToWithText(
                    seekDuration = skipTime.endTime.toInt(),
                    text = activity.stringResource(MR.strings.player_aniskip_skip, skipType.getString()),
                )
            }
        } else {
            // when waitingTime is -1, it means that the user cancelled the skip
            showAniskipButton(skipType)
        }
    }

    fun aniskipPressed() {
        if (skipType != null) {
            // this stops the counter
            if (waitingAniSkip > 0 && netflixStyle) {
                waitingAniSkip = -1
                return
            }
            rightSeekToWithText(
                seekDuration = aniSkipInterval!!.first { it.skipType == skipType }.interval.endTime.toInt(),
                text = activity.stringResource(MR.strings.player_aniskip_skip, skipType!!.getString()),
            )
        }
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        _primaryButtonTitle.update { _ -> button.name }
    }

    sealed class Event {
        data class SetCoverResult(val result: SetAsCover) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
    }
}

fun CustomButton.execute() {
    MPVLib.command(arrayOf("script-message", "call_button_$id"))
}

fun CustomButton.executeLongPress() {
    MPVLib.command(arrayOf("script-message", "call_button_${id}_long"))
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
