package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.service.DelayedAnimeTrackingUpdateJob
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Date

class ExternalIntents {

    /**
     * The common variables
     * Used to dictate what video is sent an external player.
     */
    lateinit var anime: Anime
    lateinit var source: AnimeSource
    lateinit var episode: Episode

    /**
     * Returns the [Intent] to be sent to an external player.
     *
     * @param context the application context.
     * @param animeId the id of the anime.
     * @param episodeId the id of the episode.
     */
    suspend fun getExternalIntent(
        context: Context,
        animeId: Long,
        episodeId: Long,
        chosenVideo: Video?,
    ): Intent? {
        anime = getAnime.await(animeId) ?: return null
        source = sourceManager.get(anime.source) ?: return null
        episode = getEpisodesByAnimeId.await(anime.id).find { it.id == episodeId } ?: return null
        val hosters = EpisodeLoader.getHosters(episode, anime, source)

        val video = chosenVideo
            ?: HosterLoader.getBestVideo(source, hosters)
            ?: throw Exception("Video list is empty")

        val videoUrl = getVideoUrl(source, context, video) ?: return null

        val pkgName = playerPreferences.externalPlayerPreference().get()

        return if (pkgName.isEmpty()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(videoUrl, getMime(videoUrl))
                addExtrasAndFlags(false, this)
                addVideoHeaders(false, video, this)
            }
        } else {
            getIntentForPackage(pkgName, context, videoUrl, video)
        }
    }

    /**
     * Returns the [Uri] of the given video.
     *
     * @param context the application context.
     * @param video the video being sent to the external player.
     */
    private suspend fun getVideoUrl(source: AnimeSource, context: Context, video: Video): Uri? {
        val resolvedVideo = HosterLoader.getResolvedVideo(source, video)

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            makeErrorToast(context, Exception("Video URL is empty."))
            return null
        } else {
            val uri = resolvedVideo.videoUrl.toUri()

            val isOnDevice = if (anime.source == LocalAnimeSource.ID) {
                true
            } else {
                downloadManager.isEpisodeDownloaded(
                    episodeName = episode.name,
                    episodeScanlator = episode.scanlator,
                    animeTitle = anime.title,
                    sourceId = anime.source,
                    skipCache = true,
                )
            }

            return if (isOnDevice && uri.scheme != "content") {
                FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    File(uri.path!!),
                )
            } else {
                uri
            }
        }
    }

    /**
     * Returns the second to start the external player at.
     */
    private fun getLastSecondSeen(): Long {
        val preserveWatchPos = playerPreferences.preserveWatchingPosition().get()
        val isEpisodeWatched = episode.lastSecondSeen == episode.totalSeconds

        return if (episode.seen && (!preserveWatchPos || (preserveWatchPos && isEpisodeWatched))) {
            1L
        } else {
            episode.lastSecondSeen
        }
    }

    /**
     * Display an error toast in this [context].
     *
     * @param context the application context.
     * @param e the exception error to be displayed.
     */
    private suspend fun makeErrorToast(context: Context, e: Exception?) {
        withUIContext { context.toast(e?.message ?: "Cannot open episode") }
    }

    /**
     * Returns the [Intent] with added data to send to the given external player.
     *
     * @param pkgName the name of the package to send the [Intent] to.
     * @param context the application context.
     * @param uri the path data of the video.
     * @param video the video being sent to the external player.
     */
    private fun getIntentForPackage(pkgName: String, context: Context, uri: Uri, video: Video): Intent {
        return when (pkgName) {
            WEB_VIDEO_CASTER -> webVideoCasterIntent(pkgName, context, uri, video)
            else -> standardIntentForPackage(pkgName, context, uri, video)
        }
    }

    private fun webVideoCasterIntent(pkgName: String, context: Context, uri: Uri, video: Video): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            if (isPackageInstalled(pkgName, context.packageManager)) setPackage(WEB_VIDEO_CASTER)
            addExtrasAndFlags(true, this)

            val headers = Bundle()
            video.headers?.forEach {
                headers.putString(it.first, it.second)
            }

            val localLangName = LocaleHelper.getSimpleLocaleDisplayName()
            video.subtitleTracks.firstOrNull {
                it.lang.contains(localLangName, true)
            }?.let {
                putExtra("subtitle", it.url)
            } ?: video.subtitleTracks.firstOrNull()?.let {
                putExtra("subtitle", it.url)
            }

            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            putExtra("secure_uri", true)
        }
    }

    /**
     * Returns the [Intent] with added data to send to the given external player.
     *
     * @param pkgName the name of the package to send the [Intent] to.
     * @param context the application context.
     * @param uri the path data of the video.
     * @param video the video being sent to the external player.
     */
    private fun standardIntentForPackage(pkgName: String, context: Context, uri: Uri, video: Video): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            if (isPackageInstalled(pkgName, context.packageManager)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pkgName.contains("vlc")) {
                    setPackage(pkgName)
                } else {
                    component = getComponent(pkgName)
                }
            }
            setDataAndType(uri, "video/*")
            addExtrasAndFlags(true, this)
            addVideoHeaders(true, video, this)

            // Add support for Subtitles to external players

            val localLangName = LocaleHelper.getSimpleLocaleDisplayName()
            val langIndex = video.subtitleTracks.indexOfFirst {
                it.lang.contains(localLangName, true)
            }
            val requestedLanguage = if (langIndex == -1) 0 else langIndex
            val requestedUrl = video.subtitleTracks.getOrNull(requestedLanguage)?.url

            // Just, Next, MX Player, mpv
            putExtra("subs", video.subtitleTracks.map { Uri.parse(it.url) }.toTypedArray())
            putExtra("subs.name", video.subtitleTracks.map { it.lang }.toTypedArray())
            putExtra("subs.enable", requestedUrl?.let { arrayOf(Uri.parse(it)) } ?: emptyArray())

            // VLC - seems to only work for local sub files
            requestedUrl?.let { putExtra("subtitles_location", it) }
        }
    }

    /**
     * Adds extras and flags to the given [Intent].
     *
     * @param isSupportedPlayer is it a supported external player.
     * @param intent the [Intent] that the extras and flags are added to.
     */
    private fun addExtrasAndFlags(isSupportedPlayer: Boolean, intent: Intent): Intent {
        return intent.apply {
            putExtra("title", anime.title + " - " + episode.name)
            putExtra("position", getLastSecondSeen().toInt())
            putExtra("return_result", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isSupportedPlayer) putExtra("secure_uri", true)
        }
    }

    /**
     * Adds the headers of the video to the given [Intent].
     *
     * @param isSupportedPlayer is it a supported external player.
     * @param video the [Video] to get the headers from.
     * @param intent the [Intent] that the headers are added to.
     */
    private fun addVideoHeaders(isSupportedPlayer: Boolean, video: Video, intent: Intent): Intent {
        return intent.apply {
            val headers = video.headers ?: (source as? AnimeHttpSource)?.headers
            if (headers != null) {
                var headersArray = arrayOf<String>()
                for (header in headers) {
                    headersArray += arrayOf(header.first, header.second)
                }
                putExtra("headers", headersArray)
                val headersString = headersArray.drop(2).joinToString(": ")
                if (!isSupportedPlayer) putExtra("http-header-fields", headersString)
            }
        }
    }

    /**
     * Returns the MIME type based on the video's extension.
     *
     * @param uri the path data of the video.
     */
    private fun getMime(uri: Uri): String {
        return when (uri.path?.substringAfterLast(".")) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "m3u8" -> "application/x-mpegURL"
            else -> "video/any"
        }
    }

    /**
     * Returns the specific activity to be called.
     * If the package is a part of the supported external players
     *
     * @param packageName the name of the package.
     */
    private fun getComponent(packageName: String): ComponentName? {
        return when (packageName) {
            MPV_PLAYER -> ComponentName(packageName, "$packageName.MPVActivity")
            MX_PLAYER, MX_PLAYER_FREE, MX_PLAYER_PRO -> ComponentName(
                packageName,
                "$packageName.ActivityScreen",
            )
            VLC_PLAYER -> ComponentName(packageName, "$packageName.gui.video.VideoPlayerActivity")
            MPV_KT, MPV_KT_PREVIEW -> ComponentName(packageName, "live.mehiz.mpvkt.ui.player.PlayerActivity")
            MPV_REMOTE -> ComponentName(packageName, "$packageName.MainActivity")
            JUST_PLAYER -> ComponentName(packageName, "$packageName.PlayerActivity")
            NEXT_PLAYER -> ComponentName(packageName, "$packageName.feature.player.PlayerActivity")
            X_PLAYER -> ComponentName(packageName, "com.inshot.xplayer.activities.PlayerActivity")
            else -> null
        }
    }

    /**
     * Returns true if the given package is installed on the device.
     *
     * @param packageName the name of the package to be found.
     * @param packageManager the instance of the package manager provided by the device.
     */
    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Saves the episode's data based on whats returned by the external player.
     *
     * @param intent the [Intent] that contains the episode's position and duration.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("DEPRECATION")
    fun onActivityResult(intent: Intent?) {
        val data = intent ?: return
        val anime = anime
        val currentExtEpisode = episode
        val currentPosition: Long
        val duration: Long
        val cause = data.getStringExtra("end_by") ?: ""

        // Check for position and duration as Long values
        if (cause.isNotEmpty()) {
            val positionExtra = data.extras?.get("position")
            currentPosition = if (positionExtra is Int) {
                positionExtra.toLong()
            } else {
                positionExtra as? Long ?: 0L
            }
            val durationExtra = data.extras?.get("duration")
            duration = if (durationExtra is Int) {
                durationExtra.toLong()
            } else {
                durationExtra as? Long ?: 0L
            }
        } else {
            if (data.extras?.get("extra_position") != null) {
                currentPosition = data.getLongExtra("extra_position", 0L)
                duration = data.getLongExtra("extra_duration", 0L)
            } else {
                currentPosition = data.getIntExtra("position", 0).toLong()
                duration = data.getIntExtra("duration", 0).toLong()
            }
        }

        // Update the episode's progress and history
        launchIO {
            if (cause == "playback_completion" || (currentPosition == duration && duration == 0L)) {
                saveEpisodeProgress(
                    currentExtEpisode,
                    anime,
                    currentExtEpisode.totalSeconds,
                    currentExtEpisode.totalSeconds,
                )
            } else {
                saveEpisodeProgress(currentExtEpisode, anime, currentPosition, duration)
            }
            saveEpisodeHistory(currentExtEpisode)
        }
    }

    // List of all the required Injectable classes
    private val upsertHistory: UpsertAnimeHistory = Injekt.get()
    private val updateEpisode: UpdateEpisode = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get()
    private val getTracks: GetAnimeTracks = Injekt.get()
    private val insertTrack: InsertAnimeTrack = Injekt.get()
    private val downloadManager: AnimeDownloadManager by injectLazy()
    private val delayedTrackingStore: DelayedAnimeTrackingStore = Injekt.get()
    private val playerPreferences: PlayerPreferences = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()
    private val trackPreferences: TrackPreferences = Injekt.get()
    private val basePreferences: BasePreferences by injectLazy()

    /**
     * Saves this episode's last seen history if incognito mode isn't on.
     *
     * @param currentEpisode the episode to update.
     */
    private suspend fun saveEpisodeHistory(currentEpisode: Episode) {
        if (basePreferences.incognitoMode().get()) return
        upsertHistory.await(
            AnimeHistoryUpdate(currentEpisode.id, Date()),
        )
    }

    /**
     * Saves this episode's progress (last seen second and whether it's seen).
     * Only if incognito mode isn't on
     *
     * @param currentEpisode the episode to update.
     * @param anime the anime of the episode.
     * @param lastSecondSeen the position of the episode.
     * @param totalSeconds the duration of the episode.
     */
    private suspend fun saveEpisodeProgress(
        currentEpisode: Episode?,
        anime: Anime,
        lastSecondSeen: Long,
        totalSeconds: Long,
    ) {
        if (basePreferences.incognitoMode().get()) return
        val currEp = currentEpisode ?: return

        if (totalSeconds > 0L) {
            val progress = playerPreferences.progressPreference().get()
            val seen = if (!currEp.seen) lastSecondSeen >= totalSeconds * progress else true
            updateEpisode.await(
                EpisodeUpdate(
                    id = currEp.id,
                    seen = seen,
                    bookmark = currEp.bookmark,
                    lastSecondSeen = lastSecondSeen,
                    totalSeconds = totalSeconds,
                ),
            )
            if (trackPreferences.autoUpdateTrack().get() && currEp.seen) {
                updateTrackEpisodeSeen(currEp.episodeNumber.toDouble(), anime)
            }
            if (seen) {
                deleteEpisodeIfNeeded(currentEpisode, anime)
            }
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     *
     * @param episode the episode, which is going to be marked as seen.
     * @param anime the anime of the episode.
     */
    private suspend fun deleteEpisodeIfNeeded(episode: Episode, anime: Anime) {
        // Determine which episode should be deleted and enqueue
        val sortFunction: (Episode, Episode) -> Int = when (anime.sorting) {
            Anime.EPISODE_SORTING_SOURCE -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
            Anime.EPISODE_SORTING_NUMBER -> { c1, c2 -> c1.episodeNumber.compareTo(c2.episodeNumber) }
            Anime.EPISODE_SORTING_UPLOAD_DATE -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
            else -> throw NotImplementedError("Unknown sorting method")
        }

        val episodes = getEpisodesByAnimeId.await(anime.id)
            .sortedWith { e1, e2 -> sortFunction(e1, e2) }

        val currentEpisodePosition = episodes.indexOf(episode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        val episodeToDelete = episodes.getOrNull(currentEpisodePosition - removeAfterSeenSlots)

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete, anime)
        }
    }

    /**
     * Starts the service that updates the last episode seen in sync services.
     * This operation will run in a background thread and errors are ignored.
     *
     * @param episodeNumber the episode number to be updated.
     * @param anime the anime of the episode.
     */
    private suspend fun updateTrackEpisodeSeen(episodeNumber: Double, anime: Anime) {
        if (!trackPreferences.autoUpdateTrack().get()) return

        val trackerManager = Injekt.get<TrackerManager>()
        val context = Injekt.get<Application>()

        withIOContext {
            getTracks.await(anime.id)
                .mapNotNull { track ->
                    val tracker = trackerManager.get(track.trackerId)
                    if (tracker != null &&
                        tracker.isLoggedIn &&
                        tracker is AnimeTracker &&
                        episodeNumber > track.lastEpisodeSeen
                    ) {
                        val updatedTrack = track.copy(lastEpisodeSeen = episodeNumber)

                        // We want these to execute even if the presenter is destroyed and leaks
                        // for a while. The view can still be garbage collected.
                        async {
                            runCatching {
                                if (context.isOnline()) {
                                    tracker.animeService.update(updatedTrack.toDbTrack(), true)
                                    insertTrack.await(updatedTrack)
                                } else {
                                    delayedTrackingStore.addAnime(track.animeId, lastEpisodeSeen = episodeNumber)
                                    DelayedAnimeTrackingUpdateJob.setupTask(context)
                                }
                            }
                        }
                    } else {
                        null
                    }
                }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }

    /**
     * Enqueues an [Episode] to be deleted later.
     *
     * @param episode the episode being deleted.
     * @param anime the anime of the episode.
     */
    private suspend fun enqueueDeleteSeenEpisodes(episode: Episode, anime: Anime) {
        if (episode.seen) {
            withIOContext {
                downloadManager.enqueueEpisodesToDelete(
                    listOf(episode),
                    anime,
                )
            }
        }
    }

    companion object {

        val externalIntents: ExternalIntents by injectLazy()

        /**
         * Used to direct the [Intent] of a chosen episode to an external player.
         *
         * @param context the application context.
         * @param animeId the id of the anime.
         * @param episodeId the id of the episode.
         */
        suspend fun newIntent(context: Context, animeId: Long, episodeId: Long, video: Video?): Intent? {
            return externalIntents.getExternalIntent(context, animeId, episodeId, video)
        }
    }
}

// List of supported external players and their packages
const val MPV_PLAYER = "is.xyz.mpv"
const val MX_PLAYER = "com.mxtech.videoplayer"
const val MX_PLAYER_FREE = "com.mxtech.videoplayer.ad"
const val MX_PLAYER_PRO = "com.mxtech.videoplayer.pro"
const val VLC_PLAYER = "org.videolan.vlc"
const val MPV_KT = "live.mehiz.mpvkt"
const val MPV_KT_PREVIEW = "live.mehiz.mpvkt.preview"
const val MPV_REMOTE = "com.husudosu.mpvremote"
const val JUST_PLAYER = "com.brouken.player"
const val NEXT_PLAYER = "dev.anilbeesetti.nextplayer"
const val X_PLAYER = "video.player.videoplayer"
const val WEB_VIDEO_CASTER = "com.instantbits.cast.webvideo"
