package eu.kanade.tachiyomi.data.library.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.anime.model.AnimeSourceNotInstalledException
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AnimeLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: AnimeDownloadManager = Injekt.get()
    private val coverCache: AnimeCoverCache = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val updateAnime: UpdateAnime = Injekt.get()
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get()
    private val animeFetchInterval: AnimeFetchInterval = Injekt.get()
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get()

    private val notifier = AnimeLibraryUpdateNotifier(context)

    private var animeToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = Injekt.get<LibraryPreferences>()
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addAnimeToQueue(categoryId)

        return withIOContext {
            try {
                updateEpisodeList()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = AnimeLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },

        )
    }

    /**
     * Adds list of anime to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addAnimeToQueue(categoryId: Long) {
        val libraryAnime = getLibraryAnime.await()

        val listToUpdate = if (categoryId != -1L) {
            libraryAnime.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.animeUpdateCategories().get().map { it.toLong() }
            val includedAnime = if (categoriesToUpdate.isNotEmpty()) {
                libraryAnime.filter { it.category in categoriesToUpdate }
            } else {
                libraryAnime
            }

            val categoriesToExclude = libraryPreferences.animeUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedAnimeIds = if (categoriesToExclude.isNotEmpty()) {
                libraryAnime.filter { it.category in categoriesToExclude }.map { it.anime.id }
            } else {
                emptyList()
            }

            includedAnime
                .filterNot { it.anime.id in excludedAnimeIds }
                .distinctBy { it.anime.id }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Anime, String?>>()
        val (_, fetchWindowUpperBound) = animeFetchInterval.getWindow(ZonedDateTime.now())

        animeToUpdate = listToUpdate
            .filter {
                when {
                    it.anime.updateStrategy != AnimeUpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unseenCount != 0L -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalEpisodes > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }
                    else -> true
                }
            }
            .sortedBy { it.anime.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(animeToUpdate)

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates anime in [animeToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each anime it calls [updateAnime] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateEpisodeList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()
        val newUpdates = CopyOnWriteArrayList<Pair<Anime, Array<Episode>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Anime, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = animeFetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            animeToUpdate.groupBy { it.anime.source }.values
                .map { animeInSource ->
                    async {
                        semaphore.withPermit {
                            animeInSource.forEach { libraryAnime ->
                                val anime = libraryAnime.anime
                                ensureActive()

                                // Don't continue to update if anime is not in library
                                if (getAnime.await(anime.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    anime,
                                ) {
                                    try {
                                        val newEpisodes = updateAnime(anime, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newEpisodes.isNotEmpty()) {
                                            val episodesToDownload = filterEpisodesForDownload.await(anime, newEpisodes)

                                            if (episodesToDownload.isNotEmpty()) {
                                                hasDownloads.set(true)
                                            }

                                            libraryPreferences.newAnimeUpdatesCount()
                                                .getAndSet { it + newEpisodes.size }

                                            // Convert to the anime that contains new episodes
                                            newUpdates.add(anime to newEpisodes.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoEpisodesException -> context.stringResource(
                                                MR.strings.no_episodes_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is AnimeSourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(anime to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
    }

    private fun downloadEpisodes(anime: Anime, episodes: List<Episode>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadEpisodes(anime, episodes, false)
    }

    /**
     * Updates the episodes for the given anime and adds them to the database.
     *
     * @param anime the anime to update.
     * @return a pair of the inserted and removed episodes.
     */
    private suspend fun updateAnime(anime: Anime, fetchWindow: Pair<Long, Long>): List<Episode> {
        val source = sourceManager.getOrStub(anime.source)

        // Update anime metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkAnime = source.getAnimeDetails(anime.toSAnime())
            updateAnime.awaitUpdateFromSource(anime, networkAnime, manualFetch = false, coverCache)
        }

        val episodes = source.getEpisodeList(anime.toSAnime())

        // Get anime from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbAnime = getAnime.await(anime.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncEpisodesWithSource.await(episodes, dbAnime, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingAnime: CopyOnWriteArrayList<Anime>,
        completed: AtomicInteger,
        anime: Anime,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAnime.add(anime)
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            animeToUpdate.size,
        )

        block()

        ensureActive()

        updatingAnime.remove(anime)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            animeToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Anime, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("aniyomi_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Anime
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, animes) ->
                        out.write("\n! ${error}\n")
                        animes.groupBy { it.source }.forEach { (srcId, animes) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            animes.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "AnimeLibraryUpdate"
        private const val WORK_NAME_AUTO = "AnimeLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "AnimeLibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/docs/guides/troubleshooting/"

        private const val ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "animeCategory"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<AnimeLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }
        fun startNow(
            context: Context,
            category: Category? = null,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
            )
            val request = OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
