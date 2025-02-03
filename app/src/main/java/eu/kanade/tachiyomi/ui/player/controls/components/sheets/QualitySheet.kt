package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

sealed class HosterState(open val name: String) {
    data class Idle(override val name: String) : HosterState(name)
    data class Loading(override val name: String) : HosterState(name)
    data class Error(override val name: String) : HosterState(name)
    data class Ready(
        override val name: String,
        val videoList: List<Video>,
        val videoState: List<Video.State>,
    ) : HosterState(name)
}

fun HosterState.Ready.getChangedAt(index: Int, newVideo: Video, newState: Video.State): HosterState.Ready {
    return HosterState.Ready(
        name = this.name,
        videoList = this.videoList.mapIndexed { idx, video ->
            if (idx == index) newVideo else video
        },
        videoState = this.videoState.mapIndexed { idx, state ->
            if (idx == index) newState else state
        },
    )
}

@Composable
fun QualitySheet(
    isLoadingHosters: Boolean,
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(
        onDismissRequest = {
            onDismissRequest()
        },
        dismissEvent = dismissSheet,
        modifier = modifier,
    ) {
        AnimatedVisibility(
            visible = isLoadingHosters,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 2 },
            ),
            exit = fadeOut() + slideOutVertically(
                targetOffsetY = { it / 2 },
            ),
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.padding.medium),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        AnimatedVisibility(
            visible = !isLoadingHosters,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 2 },
            ),
            exit = fadeOut() + slideOutVertically(
                targetOffsetY = { it / 2 },
            ),
        ) {
            if (hosterState.size == 1 &&
                hosterState.first().name == Hoster.NO_HOSTER_LIST &&
                hosterState.first() is HosterState.Ready
            ) {
                QualitySheetVideoContent(
                    videoList = (hosterState.first() as HosterState.Ready).videoList,
                    videoState = (hosterState.first() as HosterState.Ready).videoState,
                    selectedVideoIndex = selectedVideoIndex.second,
                    onClickVideo = onClickVideo,
                    modifier = modifier.padding(MaterialTheme.padding.medium),
                )
            } else {
                QualitySheetHosterContent(
                    hosterState = hosterState,
                    expandedState = expandedState,
                    selectedVideoIndex = selectedVideoIndex,
                    onClickHoster = onClickHoster,
                    onClickVideo = onClickVideo,
                    displayHosters = displayHosters,
                    modifier = modifier.padding(MaterialTheme.padding.medium),
                )
            }
        }
    }
}

@Composable
fun QualitySheetVideoContent(
    videoList: List<Video>,
    videoState: List<Video.State>,
    selectedVideoIndex: Int,
    onClickVideo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier
            .fillMaxWidth(),
    ) {
        itemsIndexed(videoList) { videoIdx, video ->
            VideoTrack(
                video = video,
                videoState = videoState[videoIdx],
                selected = selectedVideoIndex == videoIdx,
                onClick = { onClickVideo(0, videoIdx) },
                noHoster = true,
            )
        }
    }
}

@Composable
fun QualitySheetHosterContent(
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,
    modifier: Modifier = Modifier,
) {
    val validHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Idle ||
            state is HosterState.Loading ||
            (state is HosterState.Ready && state.videoList.isNotEmpty())
    }
    val failedHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Error
    }
    val emptyHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Ready && state.videoList.isEmpty()
    }

    LazyColumn(
        modifier.fillMaxWidth(),
    ) {
        hosterContent(
            hosters = validHosters,
            expandedState = expandedState,
            selectedVideoIndex = selectedVideoIndex,
            onClickHoster = onClickHoster,
            onClickVideo = onClickVideo,
        )

        if (displayHosters.first) {
            hosterContent(
                hosters = failedHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
            )
        }

        if (displayHosters.second) {
            hosterContent(
                hosters = emptyHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
            )
        }
    }
}

internal fun LazyListScope.hosterContent(
    hosters: List<IndexedValue<HosterState>>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
) {
    hosters.forEach { (hosterIdx, hoster) ->
        val isExpanded = expandedState.getOrNull(hosterIdx) ?: false

        item {
            HosterTrack(
                hoster = hoster,
                selected = selectedVideoIndex.first == hosterIdx,
                isExpanded = isExpanded,
                onClick = { onClickHoster(hosterIdx) },
            )

            AnimatedVisibility(
                visible = hoster is HosterState.Ready && isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                (hoster as? HosterState.Ready)?.let {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        it.videoList.forEachIndexed { videoIdx, video ->
                            VideoTrack(
                                video = video,
                                videoState = hoster.videoState[videoIdx],
                                selected = selectedVideoIndex == Pair(hosterIdx, videoIdx),
                                onClick = { onClickVideo(hosterIdx, videoIdx) },
                                noHoster = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HosterTrack(
    hoster: HosterState,
    selected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = hoster.name,
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = MaterialTheme.padding.small),
        )

        when (hoster) {
            is HosterState.Idle -> {
                Text(
                    text = stringResource(MR.strings.player_hoster_tap_to_load),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
            }
            is HosterState.Error -> {
                Text(
                    text = stringResource(MR.strings.player_hoster_failed),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            }
            is HosterState.Loading -> {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.then(Modifier.size(24.dp)),
                    strokeWidth = 2.dp,
                )
            }
            is HosterState.Ready -> {
                Text(
                    text = pluralStringResource(
                        MR.plurals.hoster_video_count,
                        hoster.videoList.size,
                        hoster.videoList.size,
                    ),
                    modifier = Modifier.alpha(DISABLED_ALPHA),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isExpanded) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                } else {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }
        }
    }
}

@Composable
fun VideoTrack(
    video: Video,
    videoState: Video.State,
    selected: Boolean,
    onClick: () -> Unit,
    noHoster: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (noHoster) {
            VideoText(
                video = video,
                selected = selected,
                noHoster = true,
                modifier = Modifier.weight(1f),
            )
            VideoIcon(
                videoState = videoState,
                noHoster = true,
            )
        } else {
            VideoIcon(
                videoState = videoState,
                noHoster = false,
            )
            VideoText(
                video = video,
                selected = selected,
                noHoster = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VideoIcon(
    videoState: Video.State,
    noHoster: Boolean,
) {
    Box(
        modifier = Modifier.size(if (noHoster) 28.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (videoState) {
            Video.State.QUEUE, Video.State.READY -> {}
            Video.State.LOAD_VIDEO -> {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                )
            }
            Video.State.ERROR -> {
                Icon(
                    Icons.Default.ErrorOutline,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun VideoText(
    video: Video,
    selected: Boolean,
    noHoster: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = video.videoTitle,
        fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(
                vertical = if (noHoster) MaterialTheme.padding.small else MaterialTheme.padding.extraSmall,
            ),
    )
}
