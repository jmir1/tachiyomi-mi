package eu.kanade.tachiyomi.ui.stats.manga

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.fastCountNot
import eu.kanade.core.util.fastFilterNot
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaStatsScreenModel(
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTotalReadDuration: GetTotalReadDuration = Injekt.get(),
    private val getTracks: GetMangaTracks = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers().filter { it is MangaTracker } }

    init {
        screenModelScope.launchIO {
            val libraryManga = getLibraryManga.await()

            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val mangaTrackMap = getMangaTrackMap(distinctLibraryManga)
            val scoredMangaTrackerMap = getScoredMangaTrackMap(mangaTrackMap)

            val meanScore = getTrackMeanScore(scoredMangaTrackerMap)

            val overviewStatData = StatsData.MangaOverview(
                libraryMangaCount = distinctLibraryManga.size,
                completedMangaCount = distinctLibraryManga.count {
                    it.manga.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L
                },
                totalReadDuration = getTotalReadDuration.await(),
            )

            val titlesStatData = StatsData.MangaTitles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryManga),
                startedMangaCount = distinctLibraryManga.count { it.hasStarted },
                localMangaCount = distinctLibraryManga.count { it.manga.isLocal() },
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryManga.sumOf { it.totalChapters }.toInt(),
                readChapterCount = distinctLibraryManga.sumOf { it.readCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = mangaTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            mutableState.update {
                StatsScreenState.SuccessManga(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private fun getGlobalUpdateItemCount(libraryManga: List<LibraryManga>): Int {
        val includedCategories = preferences.mangaUpdateCategories().get().map { it.toLong() }
        val includedManga = if (includedCategories.isNotEmpty()) {
            libraryManga.filter { it.category in includedCategories }
        } else {
            libraryManga
        }

        val excludedCategories = preferences.mangaUpdateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (excludedCategories.isNotEmpty()) {
            libraryManga.fastMapNotNull { manga ->
                manga.id.takeIf { manga.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateItemRestrictions().get()
        return includedManga
            .fastFilterNot { it.manga.id in excludedMangaIds }
            .fastDistinctBy { it.manga.id }
            .fastCountNot {
                (ENTRY_NON_COMPLETED in updateRestrictions && it.manga.status.toInt() == SManga.COMPLETED) ||
                    (ENTRY_HAS_UNVIEWED in updateRestrictions && it.unreadCount != 0L) ||
                    (ENTRY_NON_VIEWED in updateRestrictions && it.totalChapters > 0 && !it.hasStarted)
            }
    }

    private suspend fun getMangaTrackMap(libraryManga: List<LibraryManga>): Map<Long, List<MangaTrack>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryManga.associate { manga ->
            val tracks = getTracks.await(manga.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            manga.id to tracks
        }
    }

    private fun getScoredMangaTrackMap(mangaTrackMap: Map<Long, List<MangaTrack>>): Map<Long, List<MangaTrack>> {
        return mangaTrackMap.mapNotNull { (mangaId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            mangaId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredMangaTrackMap: Map<Long, List<MangaTrack>>): Double {
        return scoredMangaTrackMap
            .map { (_, tracks) ->
                tracks.map(::get10PointScore).average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private fun get10PointScore(track: MangaTrack): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.mangaService.get10PointScore(track)
    }
}
