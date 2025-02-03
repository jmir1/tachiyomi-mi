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

package eu.kanade.tachiyomi.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.Decoder
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.AudioTracksSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.ChaptersSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.MoreSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.PlaybackSpeedSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.ScreenshotSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.SubtitlesSheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.custombuttons.model.CustomButton
import java.io.InputStream

@Composable
fun PlayerSheets(
    sheetShown: Sheets,

    // subtitles sheet
    subtitles: ImmutableList<VideoTrack>,
    selectedSubtitles: ImmutableList<Int>,
    onAddSubtitle: (Uri) -> Unit,
    onSelectSubtitle: (Int) -> Unit,

    // audio sheet
    audioTracks: ImmutableList<VideoTrack>,
    selectedAudio: Int,
    onAddAudio: (Uri) -> Unit,
    onSelectAudio: (Int) -> Unit,

    // video sheet
    isLoadingHosters: Boolean,
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,

    // chapters sheet
    chapter: Segment?,
    chapters: ImmutableList<Segment>,
    onSeekToChapter: (Int) -> Unit,

    // Decoders sheet
    decoder: Decoder,
    onUpdateDecoder: (Decoder) -> Unit,

    // Speed sheet
    speed: Float,
    onSpeedChange: (Float) -> Unit,

    // More sheet
    sleepTimerTimeRemaining: Int,
    onStartSleepTimer: (Int) -> Unit,
    buttons: ImmutableList<CustomButton>,

    // Screenshot sheet
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,
    cachePath: String,
    onSetAsCover: (() -> InputStream) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (String, Boolean) -> InputStream?,
    onDismissScreenshot: () -> Unit,

    onOpenPanel: (Panels) -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
) {
    when (sheetShown) {
        Sheets.None -> {}
        Sheets.SubtitleTracks -> {
            val subtitlesPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) {
                if (it == null) return@rememberLauncherForActivityResult
                onAddSubtitle(it)
            }
            SubtitlesSheet(
                tracks = subtitles.toImmutableList(),
                selectedTracks = selectedSubtitles,
                onSelect = onSelectSubtitle,
                onAddSubtitle = { subtitlesPicker.launch(arrayOf("*/*")) },
                onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
                onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
                onDismissRequest = onDismissRequest,
            )
        }

        Sheets.AudioTracks -> {
            val audioPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) {
                if (it == null) return@rememberLauncherForActivityResult
                onAddAudio(it)
            }
            AudioTracksSheet(
                tracks = audioTracks,
                selectedId = selectedAudio,
                onSelect = onSelectAudio,
                onAddAudioTrack = { audioPicker.launch(arrayOf("*/*")) },
                onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
                onDismissRequest,
            )
        }

        Sheets.QualityTracks -> {
            QualitySheet(
                isLoadingHosters = isLoadingHosters,
                hosterState = hosterState,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
                displayHosters = displayHosters,
                onDismissRequest = onDismissRequest,
                dismissSheet = dismissSheet,
            )
        }

        Sheets.Chapters -> {
            if (chapter == null) return
            ChaptersSheet(
                chapters,
                currentChapter = chapter,
                onClick = { onSeekToChapter(chapters.indexOf(it)) },
                onDismissRequest,
            )
        }

        Sheets.More -> {
            MoreSheet(
                selectedDecoder = decoder,
                onSelectDecoder = onUpdateDecoder,
                remainingTime = sleepTimerTimeRemaining,
                onStartTimer = onStartSleepTimer,
                onDismissRequest = onDismissRequest,
                onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
                customButtons = buttons,
            )
        }

        Sheets.PlaybackSpeed -> {
            PlaybackSpeedSheet(
                speed,
                onSpeedChange = onSpeedChange,
                onDismissRequest = onDismissRequest,
            )
        }

        Sheets.Screenshot -> {
            ScreenshotSheet(
                hasSubTracks = subtitles.isNotEmpty(),
                showSubtitles = showSubtitles,
                onToggleShowSubtitles = onToggleShowSubtitles,
                cachePath = cachePath,
                onSetAsCover = onSetAsCover,
                onShare = onShare,
                onSave = onSave,
                takeScreenshot = takeScreenshot,
                onDismissRequest = onDismissScreenshot,
            )
        }
    }
}
