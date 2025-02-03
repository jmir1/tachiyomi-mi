package eu.kanade.presentation.entries.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetHosterContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetVideoContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class EpisodeOptionsDialogScreen(
    private val useExternalDownloader: Boolean,
    private val episodeTitle: String,
    private val episodeId: Long,
    private val animeId: Long,
    private val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val sm = rememberScreenModel {
            EpisodeOptionsDialogScreenModel(
                episodeId = episodeId,
                animeId = animeId,
                sourceId = sourceId,
            )
        }

        val episode by sm.episode.collectAsState()
        val anime by sm.anime.collectAsState()
        val hosterState by sm.hosterState.collectAsState()
        val hosterExpandedList by sm.hosterExpandedList.collectAsState()
        val selectedHosterVideoIndex by sm.selectedHosterVideoIndex.collectAsState()
        val currentVideo by sm.currentVideo.collectAsState()
        val showAllQualities by sm.showAllQualities.collectAsState()

        EpisodeOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            episodeTitle = episodeTitle,
            episode = episode,
            anime = anime,
            showAllQualities = showAllQualities,
            resultList = hosterState,
            expandedList = hosterExpandedList,
            currentVideo = currentVideo,
            selectedHosterVideoIndex = selectedHosterVideoIndex,
            onShowAllQualities = sm::onShowAllQualities,
            onClickHoster = sm::onClickHoster,
            onClickVideo = sm::onClickVideo,
            getHosterList = sm::getHosterList,
        )
    }

    companion object {
        var onDismissDialog: () -> Unit = {}
    }
}

class EpisodeOptionsDialogScreenModel(
    episodeId: Long,
    animeId: Long,
    sourceId: Long,
) : ScreenModel {
    private val sourceManager: AnimeSourceManager = Injekt.get()

    private val _hosterState = MutableStateFlow<Result<List<HosterState>>?>(null)
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _episode = MutableStateFlow<Episode?>(null)
    val episode = _episode.asStateFlow()
    private val _anime = MutableStateFlow<Anime?>(null)
    val anime = _anime.asStateFlow()

    @Suppress("ktlint:standard:backing-property-naming")
    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())

    @Suppress("ktlint:standard:backing-property-naming")
    private val _source = MutableStateFlow<AnimeSource?>(null)

    private val _showAllQualities = MutableStateFlow(false)
    val showAllQualities = _showAllQualities.asStateFlow()

    init {
        val hasFoundPreferredVideo = AtomicBoolean(false)

        screenModelScope.launchIO {
            val episode = Injekt.get<GetEpisode>().await(episodeId)!!
            val anime = Injekt.get<GetAnime>().await(animeId)!!
            val source = sourceManager.getOrStub(sourceId)

            _episode.update { _ -> episode }
            _anime.update { _ -> anime }
            _source.update { _ -> source }

            val hosterListResult = withIOContext {
                try {
                    Result.success(EpisodeLoader.getHosters(episode, anime, source))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            if (hosterListResult.isFailure) {
                _hosterState.update { _ -> Result.failure(hosterListResult.exceptionOrNull()!!) }
                return@launchIO
            }

            val hosterList = hosterListResult.getOrThrow()
            _hosterList.update { _ -> hosterList }
            _hosterExpandedList.update { _ ->
                List(hosterList.size) { true }
            }

            val initialHosterState = hosterList.map { hoster ->
                if (hoster.videoList == null) {
                    HosterState.Loading(hoster.hosterName)
                } else {
                    val videoList = hoster.videoList!!
                    HosterState.Ready(
                        hoster.hosterName,
                        videoList,
                        List(videoList.size) { Video.State.LOAD_VIDEO },
                    )
                }
            }

            _hosterState.update { _ -> Result.success(initialHosterState) }

            try {
                hosterList.mapIndexed { hosterIdx, hoster ->
                    async {
                        val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)

                        _hosterState.updateAt(hosterIdx, hosterState)

                        if (hosterState is HosterState.Ready) {
                            val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                            if (prefIndex != -1) {
                                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                    val success =
                                        loadVideo(source, hosterState.videoList[prefIndex], hosterIdx, prefIndex)
                                    if (!success) {
                                        hasFoundPreferredVideo.set(false)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()

                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                    val hosterStateList = hosterState.value!!.getOrThrow()
                    val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                    if (hosterIdx == -1) {
                        _hosterState.update { _ ->
                            Result.failure(NoSuchElementException("No available videos"))
                        }
                        return@launchIO
                    }

                    val video = (hosterStateList[hosterIdx] as HosterState.Ready).videoList[videoIdx]

                    loadVideo(source, video, hosterIdx, videoIdx)
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    Result.success(hosterList.map { HosterState.Idle(it.hosterName) })
                }

                throw e
            }
        }
    }

    private suspend fun loadVideo(source: AnimeSource, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val selectedHosterState = (_hosterState.value!!.getOrThrow()[hosterIndex] as? HosterState.Ready) ?: return false

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
        )

        val resolvedVideo = if (selectedHosterState.videoState[videoIndex] != Video.State.READY) {
            HosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )

                val hosterStateList = hosterState.value?.getOrNull() ?: return false

                val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                if (newHosterIdx == -1) {
                    _hosterState.update { _ ->
                        Result.failure(NoSuchElementException("No available videos"))
                    }
                    return false
                }

                val newVideo = (hosterStateList[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]

                return loadVideo(source, newVideo, newHosterIdx, newVideoIdx)
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
        )
        _currentVideo.update { _ -> resolvedVideo }

        return true
    }

    private fun <T> MutableStateFlow<Result<List<T>>?>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values?.getOrNull()?.let {
                Result.success(
                    it.toMutableList().apply {
                        this[index] = newValue
                    },
                )
            } ?: values
        }
    }

    fun onShowAllQualities(value: Boolean) {
        _showAllQualities.update { _ -> value }
    }

    fun onClickHoster(hosterIndex: Int) {
        val hosterState = hosterState.value?.getOrNull()?.getOrNull(hosterIndex) ?: return

        when (hosterState) {
            is HosterState.Ready -> {
                _hosterExpandedList.update { values ->
                    values.toMutableList().apply {
                        this[hosterIndex] = !hosterExpandedList.value[hosterIndex]
                    }
                }
            }
            is HosterState.Error, is HosterState.Idle -> {
                val hosterName = hosterState.name
                _hosterState.updateAt(hosterIndex, HosterState.Loading(hosterName))

                screenModelScope.launchIO {
                    val newHosterState = EpisodeLoader.loadHosterVideos(
                        _source.value!!,
                        _hosterList.value[hosterIndex],
                    )
                    _hosterState.updateAt(hosterIndex, newHosterState)
                }
            }
            is HosterState.Loading -> {}
        }
    }

    fun onClickVideo(hosterIndex: Int, videoIndex: Int) {
        val video = (_hosterState.value?.getOrNull()?.getOrNull(hosterIndex) as? HosterState.Ready)
            ?.videoList
            ?.getOrNull(videoIndex)
            ?: return

        screenModelScope.launchIO {
            val success = loadVideo(_source.value!!, video, hosterIndex, videoIndex)
            if (success) {
                _showAllQualities.update { _ -> false }
            }
        }
    }

    fun getHosterList(): List<Hoster>? {
        val hosterStateList = hosterState.value?.getOrNull() ?: return null
        return _hosterList.value.mapIndexed { index, h ->
            if (hosterStateList[index] is HosterState.Ready) {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = (hosterStateList[index] as HosterState.Ready).videoList,
                )
            } else {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = h.videoList,
                )
            }
        }
    }
}

@Composable
fun EpisodeOptionsDialog(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episode: Episode?,
    anime: Anime?,
    showAllQualities: Boolean,
    resultList: Result<List<HosterState>>? = null,
    expandedList: List<Boolean>,
    currentVideo: Video?,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                top = TabbedDialogPaddings.Vertical,
                bottom = TabbedDialogPaddings.Vertical + MaterialTheme.padding.medium,
            )
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = episodeTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(MR.strings.choose_video_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        val onError: () -> Unit = {
            logcat(LogPriority.ERROR) { "Error getting links" }
            scope.launchUI { context.toast("No available videos") }
            EpisodeOptionsDialogScreen.onDismissDialog()
        }
        if (resultList?.isFailure == true) {
            onError()
        }

        if (resultList == null || episode == null || anime == null || currentVideo == null) {
            LoadingScreen()
        } else {
            val hosterStateList = resultList.getOrNull()
            if (!hosterStateList.isNullOrEmpty()) {
                VideoList(
                    useExternalDownloader = useExternalDownloader,
                    episode = episode,
                    anime = anime,
                    showAllQualities = showAllQualities,
                    hosterStateList = hosterStateList,
                    expandedList = expandedList,
                    currentVideo = currentVideo,
                    selectedHosterVideoIndex = selectedHosterVideoIndex,
                    onShowAllQualities = onShowAllQualities,
                    onClickHoster = onClickHoster,
                    onClickVideo = onClickVideo,
                    getHosterList = getHosterList,
                )
            } else {
                onError()
            }
        }
    }
}

@Composable
private fun VideoList(
    useExternalDownloader: Boolean,
    episode: Episode,
    anime: Anime,
    showAllQualities: Boolean,
    hosterStateList: List<HosterState>,
    expandedList: List<Boolean>,
    currentVideo: Video,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
) {
    val downloadManager = Injekt.get<AnimeDownloadManager>()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(MR.strings.copied_video_link_to_clipboard)

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (currentVideo.videoUrl.isNotEmpty() && !showAllQualities) {
                ClickableRow(
                    text = currentVideo.videoTitle,
                    icon = null,
                    onClick = { onShowAllQualities(true) },
                    showDropdownArrow = true,
                )

                val downloadEpisode: (Boolean) -> Unit = {
                    downloadManager.downloadEpisodes(
                        anime,
                        listOf(episode),
                        true,
                        it,
                        currentVideo,
                    )
                }

                QualityOptions(
                    onDownloadClicked = { downloadEpisode(useExternalDownloader) },
                    onExtDownloadClicked = { downloadEpisode(!useExternalDownloader) },
                    onCopyClicked = {
                        clipboardManager.setText(AnnotatedString(currentVideo.videoUrl))
                        scope.launch { context.toast(copiedString) }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                true,
                                currentVideo,
                            )
                        }
                    },
                    onIntPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                false,
                                currentVideo,
                                selectedHosterVideoIndex.first,
                                selectedHosterVideoIndex.second,
                                getHosterList(),
                            )
                        }
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .heightIn(max = 600.dp),
            ) {
                if (
                    hosterStateList.size == 1 &&
                    hosterStateList.first().name == Hoster.NO_HOSTER_LIST &&
                    hosterStateList.first() is HosterState.Ready
                ) {
                    QualitySheetVideoContent(
                        videoList = (hosterStateList.first() as HosterState.Ready).videoList,
                        videoState = (hosterStateList.first() as HosterState.Ready).videoState,
                        selectedVideoIndex = selectedHosterVideoIndex.second,
                        onClickVideo = onClickVideo,
                    )
                } else {
                    QualitySheetHosterContent(
                        hosterState = hosterStateList,
                        expandedState = expandedList,
                        selectedVideoIndex = selectedHosterVideoIndex,
                        onClickHoster = onClickHoster,
                        onClickVideo = onClickVideo,
                        displayHosters = Pair(false, false),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
    onIntPlayerClicked: () -> Unit = {},
) {
    val closeMenu = { EpisodeOptionsDialogScreen.onDismissDialog() }

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_internally),
            icon = Icons.Outlined.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_externally),
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_play_externally),
            icon = Icons.Outlined.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_play_internally),
            icon = Icons.Outlined.Input,
            onClick = {
                onIntPlayerClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showDropdownArrow) {
            Icon(
                imageVector = Icons.Outlined.NavigateNext,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
