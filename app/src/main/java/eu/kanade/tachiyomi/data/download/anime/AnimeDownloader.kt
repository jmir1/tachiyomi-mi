package eu.kanade.tachiyomi.data.download.anime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.SessionState
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownloadPart
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.size
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okio.Throttler
import okio.buffer
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * This class is the one in charge of downloading episodes.
 *
 * Its queue contains the list of episodes to download. In order to download them, the downloader
 * subscription must be running and the list of episodes must be sent to them by [downloaderJob].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class AnimeDownloader(
    private val context: Context,
    private val provider: AnimeDownloadProvider,
    private val cache: AnimeDownloadCache,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) {
    /**
     * Store for persisting downloads across restarts.
     */
    private val store = AnimeDownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { AnimeDownloadNotifier(context) }

    /**
     * The throttler used to control the download speed.
     */
    private val throttler = Throttler()

    /**
     * Coroutine scope used for download job scheduling
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Job object for download queue management
     */
    private var downloaderJob: Job? = null

    /**
     * Preference for user's choice of external downloader
     */
    private val preferences: DownloadPreferences by injectLazy()

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether FFmpeg is running.
     */
    @Volatile
    var isFFmpegRunning: Boolean = false

    init {
        scope.launch {
            val episodes = async { store.restore() }
            addAllToQueue(episodes.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != AnimeDownload.State.DOWNLOADED }
        pending.forEach { if (it.status != AnimeDownload.State.QUEUE) it.status = AnimeDownload.State.QUEUE }

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach { it.status = AnimeDownload.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        AnimeDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach { it.status = AnimeDownload.State.QUEUE }
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the jobs to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= AnimeDownload.State.DOWNLOADING.value
                        } // Ignore completed downloads, leave them in the queue
                        .groupBy { it.source }
                        .toList().take(3) // Concurrently download from 5 different sources
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break

                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(AnimeDownload::statusFlow)) { states ->
                            states.contains(AnimeDownload.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }

                if (areAllAnimeDownloadsFinished()) stop()
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<AnimeDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    /**
     * Launch the job responsible for download a single video
     */
    private fun CoroutineScope.launchDownloadJob(download: AnimeDownload) = launchIO {
        // This try-catch manages the job cancellation
        try {
            downloadEpisode(download)

            // Remove successful download from queue
            if (download.status == AnimeDownload.State.DOWNLOADED) {
                removeFromQueue(download)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                notifier.onError("Download cancelled")
            } else {
                notifier.onError(e.message)
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        isFFmpegRunning = false
        FFmpegKitConfig.getSessions().filter {
            it.isFFmpeg && (it.state == SessionState.CREATED || it.state == SessionState.RUNNING)
        }.forEach {
            it.cancel()
        }

        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every episode and adds them to the downloads queue.
     *
     * @param anime the anime of the episodes to download.
     * @param episodes the list of episodes to download.
     * @param autoStart whether to start the downloader after enqueing the episodes.
     */
    fun queueEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean,
        changeDownloader: Boolean = false,
        video: Video? = null,
    ) {
        if (episodes.isEmpty()) return

        val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()

        val episodesToQueue = episodes.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findEpisodeDir(it.name, it.scanlator, anime.title, source) == null }
            // Add episodes to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { episode -> queueState.value.none { it.episode.id == episode.id } }
            // Create a download for each one.
            .map { AnimeDownload(source, anime, it, changeDownloader, video) }
            .toList()

        if (episodesToQueue.isNotEmpty()) {
            addAllToQueue(episodesToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads =
                    queueState.value.count { it: AnimeDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                // TODO: show warnings in stable
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(MR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(
                            context,
                            AnimeLibraryUpdateNotifier.HELP_WARNING_URL,
                        ),
                    )
                }
                AnimeDownloadJob.start(context)
            }
        }
    }

    /**
     * Download the video associated with download object
     *
     * @param download the episode to be downloaded.
     */
    private suspend fun downloadEpisode(download: AnimeDownload) {
        // This try catch manages errors during download
        try {
            val animeDir = provider.getAnimeDir(download.anime.title, download.source)

            val availSpace = DiskUtil.getAvailableStorageSpace(animeDir)
            if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
                throw Exception(context.stringResource(MR.strings.download_insufficient_space))
            }

            val episodeDirname = provider.getEpisodeDirName(download.episode.name, download.episode.scanlator)
            val tmpDir = animeDir.createDirectory(episodeDirname + TMP_DIR_SUFFIX)!!

            if (download.video == null) {
                // Pull video from network and add them to download object
                try {
                    val hosters = EpisodeLoader.getHosters(download.episode, download.anime, download.source)
                    val fetchedVideo = HosterLoader.getBestVideo(download.source, hosters)!!

                    download.video = fetchedVideo
                } catch (e: Exception) {
                    throw Exception(context.stringResource(MR.strings.video_list_empty_error))
                }
            }

            getOrDownloadVideoFile(download, tmpDir)

            ensureSuccessfulAnimeDownload(download, animeDir, tmpDir, episodeDirname)
        } catch (e: Exception) {
            download.status = AnimeDownload.State.ERROR
            notifier.onError(e.message, download.episode.name, download.anime.title, download.anime.id)
        } finally {
            notifier.dismissProgress()
        }
    }

    /**
     * Gets the video file if already downloaded, otherwise downloads it
     *
     * @param download the download of the video.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadVideoFile(
        download: AnimeDownload,
        tmpDir: UniFile,
    ): Video {
        val video = download.video!!

        video.status = Video.State.LOAD_VIDEO

        var progressJob: Job? = null

        // Get filename from download info
        val filename = DiskUtil.buildValidFilename(download.episode.name)

        // Get VideoFile if existing
        val videoFile = tmpDir.listFiles()?.firstOrNull { it.name!!.startsWith("$filename.mp4") }

        try {
            // If the video is already downloaded, do nothing. Otherwise download from network
            val file = when {
                videoFile != null -> videoFile
                else -> {
                    notifier.onProgressChange(download)

                    download.status = AnimeDownload.State.DOWNLOADING
                    download.progress = 0

                    // If videoFile is not existing then download it
                    if (preferences.useExternalDownloader().get() == download.changeDownloader) {
                        progressJob = scope.launch {
                            while (download.status == AnimeDownload.State.DOWNLOADING) {
                                delay(50)
                                notifier.onProgressChange(download)
                            }
                        }

                        attemptDownload(download, tmpDir, filename, preferences.safeDownload().get())
                    } else {
                        val betterFileName = DiskUtil.buildValidFilename(
                            "${download.anime.title} - ${download.episode.name}",
                        )
                        downloadVideoExternal(download.video!!, download.source, tmpDir, betterFileName)
                    }
                }
            }

            video.videoUrl = file.uri.path ?: ""
            download.progress = 100
            video.status = Video.State.READY
            progressJob?.cancel()
        } catch (e: Exception) {
            video.status = Video.State.ERROR
            progressJob?.cancel()

            throw e
        }

        return video
    }

    /**
     * Define a retry routine in order to accommodate some errors that can be raised
     *
     * @param download the download reference
     * @param tmpDir the directory where placing the file
     * @param filename the name to give to download file
     * @param safe whether to use safe mode for each try
     */
    private suspend fun attemptDownload(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
        safe: Boolean,
    ): UniFile {
        // If we attempt always in safe mode then initial threads count is 1
        val threads = if (safe) {
            1
        } else {
            preferences.numberOfThreads().get()
        }

        var file: UniFile? = null

        val downloadScope = CoroutineScope(coroutineContext)

        for (tries in 1..3) {
            // At each try we reduce the number of thread used (this is due to the fact that sometimes
            // download fails because there are too many threads for limited max download speed
            // we then ensures that we have at least one thread
            var newThreads = threads.floorDiv(tries)
            if (newThreads < 1) newThreads = 1

            if (downloadScope.isActive) {
                file = try {
                    if (isHls(download.video!!) || isMpd(download.video!!)) {
                        ffmpegDownload(download, tmpDir, filename)
                    } else {
                        httpDownload(download, tmpDir, filename, newThreads, safe)
                    }
                } catch (e: Exception) {
                    notifier.onError(
                        e.message + ", retrying..",
                        download.episode.name,
                        download.anime.title,
                        download.anime.id,
                    )
                    delay(2 * 1000L)
                    null
                }
            }

            // If download has been completed successfully we break from retry loop
            if (file != null) break
        }

        // If download has completed successfully we return the file,
        // otherwise we attempt a final try forcing safe mode
        return if (downloadScope.isActive) {
            file ?: try {
                if (isHls(download.video!!) || isMpd(download.video!!)) {
                    ffmpegDownload(download, tmpDir, filename)
                } else {
                    httpDownload(download, tmpDir, filename, 1, true)
                }
            } catch (e: Exception) {
                notifier.onError(e.message, download.episode.name, download.anime.title, download.anime.id)
                throw e
            }
        } else {
            throw Exception("Download has been stopped")
        }
    }

    private fun isMpd(video: Video): Boolean {
        return video.videoUrl?.toHttpUrl()?.encodedPath?.endsWith(".mpd") ?: false
    }

    private fun isHls(video: Video): Boolean {
        return video.videoUrl?.toHttpUrl()?.encodedPath?.endsWith(".m3u8") ?: false
    }

    // ffmpeg is always on safe mode
    private fun ffmpegDownload(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        val video = download.video!!

        isFFmpegRunning = true

        // always delete tmp file
        tmpDir.findFile("$filename.tmp")?.delete()
        val videoFile = tmpDir.createFile("$filename.tmp")!!

        val ffmpegFilename = { videoFile.uri.toFFmpegString(context) }

        val headers = video.headers ?: download.source.headers
        val headerOptions = headers.joinToString("", "-headers '", "'") {
            "${it.first}: ${it.second}\r\n"
        }

        val ffmpegOptions = getFFmpegOptions(video, headerOptions, ffmpegFilename())
        val ffprobeCommand = { file: String, ffprobeHeaders: String? ->
            FFmpegKitConfig.parseArguments(
                "${ffprobeHeaders?.plus(" ") ?: ""}-v error -show_entries " +
                    "format=duration -of default=noprint_wrappers=1:nokey=1 \"$file\"",
            )
        }

        var duration = 0L
        var nextLineIsDuration = false

        val logCallback = LogCallback { log ->
            if (nextLineIsDuration) {
                parseDuration(log.message)?.let { duration = it }
                nextLineIsDuration = false
            }
            if (log.level <= Level.AV_LOG_WARNING) log.message?.let { logcat { it } }
            if (duration != 0L && log.message.startsWith("frame=")) {
                val outTime = log.message
                    .substringAfter("time=", "")
                    .substringBefore(" ", "")
                    .let { parseTimeStringToSeconds(it) }
                if (outTime != null && outTime > 0L) download.progress = (100 * outTime / duration).toInt()
            }
        }

        val session = FFmpegSession.create(ffmpegOptions, {}, logCallback, {})
        val inputDuration = getDuration(ffprobeCommand(video.videoUrl!!, headerOptions)) ?: 0F

        duration = inputDuration.toLong()
        FFmpegKitConfig.ffmpegExecute(session)

        val outputDuration = getDuration(ffprobeCommand(ffmpegFilename(), null)) ?: 0F
        // allow for slight errors
        if (inputDuration > outputDuration * 1.01F) {
            tmpDir.findFile("$filename.tmp")?.delete()
        }

        session.failStackTrace?.let { trace ->
            logcat(LogPriority.ERROR) { trace }
            throw Exception("Error in ffmpeg!")
        }

        val file = tmpDir.findFile("$filename.tmp")?.apply {
            renameTo("$filename.mkv")
        }

        file ?: throw Exception("Downloaded file not found")
        return file
    }

    private fun parseTimeStringToSeconds(timeString: String): Long? {
        val parts = timeString.split(":")
        if (parts.size != 3) {
            // Invalid format
            return null
        }

        return try {
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            val secondsAndMilliseconds = parts[2].split(".")
            val seconds = secondsAndMilliseconds[0].toInt()
            val milliseconds = secondsAndMilliseconds[1].toInt()

            (hours * 3600 + minutes * 60 + seconds + milliseconds / 100.0).toLong()
        } catch (e: NumberFormatException) {
            // Invalid number format
            null
        }
    }

    private fun getFFmpegOptions(video: Video, headerOptions: String, ffmpegFilename: String): Array<String> {
        val subtitleInputs = video.subtitleTracks.joinToString(" ", postfix = " ") {
            "-i \"${it.url}\""
        }
        val subtitleMaps = video.subtitleTracks.indices.joinToString(" ") {
            val index = it + 1
            "-map $index:s"
        }
        val subtitleMetadata = video.subtitleTracks.mapIndexed { i, sub ->
            "-metadata:s:s:$i \"title=${sub.lang}\""
        }.joinToString(" ")

        Locale("")
        return FFmpegKitConfig.parseArguments(
            headerOptions +
                " -i \"${video.videoUrl}\" " + subtitleInputs +
                "-map 0:v -map 0:a " + subtitleMaps + " -map 0:s?" +
                " -f matroska -c:a copy -c:v copy -c:s ass " +
                subtitleMetadata +
                " \"$ffmpegFilename\" -y",
        )
    }

    private fun getDuration(ffprobeCommand: Array<String>): Float? {
        val session = FFprobeSession.create(ffprobeCommand)
        FFmpegKitConfig.ffprobeExecute(session)
        return session.allLogsAsString.trim().toFloatOrNull()
    }

    /**
     * Returns the parsed duration in milliseconds
     *
     * @param durationString the string formatted in HOURS:MINUTES:SECONDS.HUNDREDTHS
     */
    private fun parseDuration(durationString: String): Long? {
        val splitString = durationString.split(":")
        if (splitString.lastIndex != 2) return null
        val hours = splitString[0].toLong()
        val minutes = splitString[1].toLong()
        val secondsString = splitString[2].split(".")
        if (secondsString.lastIndex != 1) return null
        val fullSeconds = secondsString[0].toLong()
        val hundredths = secondsString[1].toLong()
        return hours * 3600000L + minutes * 60000L + fullSeconds * 1000L + hundredths * 10L
    }

    /**
     * Routine for download a file using http requests.
     *
     * @param download the download reference
     * @param tmpDir the directory where to place the downloaded file
     * @param filename the name to give to video file
     * @param threadNumber max number of parallel request
     * @param safeDownload whether to use safe download mode
     */
    private suspend fun httpDownload(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
        threadNumber: Int,
        safeDownload: Boolean,
    ): UniFile {
        val downloadScope = CoroutineScope(coroutineContext)
        val video = download.video!!

        // reset bytesDownloaded and totalBytesDownloaded
        download.resetProgress()

        if (safeDownload) {
            tmpDir.listFiles().orEmpty()
                .filter {
                    if (it.name == null) {
                        false
                    } else {
                        it.name!!.endsWith("part.tmp")
                    }
                }
                .forEach { it.delete() }
        }

        val downloadParts = mutableListOf<AnimeDownloadPart>()

        // on safe mode only one range is requested
        if (safeDownload) {
            val range = Pair(0L, 0L)
            val part = AnimeDownloadPart(tmpDir, range)
            part.listener =
                object : ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        download.update(bytesRead, contentLength, false)
                    }
                }
            part.request = download.source.safeVideoRequest(video)
            part.file = tmpDir.createFile("0.part.tmp")!!
            part.completed = false
            downloadParts.add(part)

            // on safe mode the tmp file has been deleted, so when content length will be updated
            // it will be the set to the effective video size
            download.bytesDownloaded = 0L
            download.totalContentLength = 0
        } else {
            // otherwise we get all needed ranges

            // first we fetch the size of the video
            val videoSize: Long = download.source.getVideoSize(video, 3)
            if (videoSize == -1L) {
                throw Exception("Could not get video size")
            }

            // on normal mode, since the download can resume from tmp file, the download length can be different
            // from the effective video size. To prevent strange behaviours on progress update we manually set the
            // download total length as the known video file size, in this way any other update will always skip
            // the total length update since it will always be <= than the video file size
            download.totalContentLength = videoSize

            // Get all download parts (completed and not-completed) sorted on increasing start byte value
            downloadParts.addAll(
                getDownloadParts(
                    download,
                    video,
                    tmpDir,
                    threadNumber,
                    videoSize,
                ).sortedBy { it.range.first },
            )
        }

        // set throttler max bound
        throttler.apply {
            bytesPerSecond(preferences.downloadSpeedLimit().get().toLong() * 1024)
        }

        // set download as not failed and not stopped
        var failedDownload = false

        // support object for job management and progress funneling
        val partProgressLock = Object()
        var partProgress = -1

        // do an initial update
        download.update(download.bytesDownloaded, download.totalContentLength, false)

        val mergeWaiter = Object()

        for (i in 1..threadNumber) {
            downloadScope.launchIO {
                var index: Int
                while (true) {
                    synchronized(partProgressLock) {
                        partProgress += 1
                        index = partProgress
                    }
                    if (index < downloadParts.size) {
                        // If failed before even starting, then just return
                        if (failedDownload || !isActive) return@launchIO

                        val part = downloadParts[index]
                        var response: Response? = null
                        // try to open the file and append the bytes
                        try {
                            if (!part.completed) {
                                response = download.source.getVideo(part.request!!, part.listener!!)

                                response.body.source()
                                    .use { source ->
                                        part.file
                                            .openOutputStream(true)
                                            .use { output ->
                                                val sink = output.sink().buffer()
                                                val buffer = ByteArray(4 * 1024)
                                                var bytesRead: Int
                                                val throttledSource = throttler.source(source).buffer()

                                                // part file downloading loop
                                                while (throttledSource.read(buffer).also { bytesRead = it }
                                                        .toLong() != -1L
                                                ) {
                                                    // If job has been asked to close then set the download as failed and collaborate
                                                    // on closing (as job closure ask to do)
                                                    if (!isActive) {
                                                        break
                                                    }

                                                    // Write the bytes to the file
                                                    sink.write(buffer, 0, bytesRead)
                                                    sink.emitCompleteSegments()
                                                    download.bytesDownloaded = bytesRead.toLong()
                                                }
                                                sink.flush()
                                                sink.close()
                                                throttledSource.close()
                                            }
                                    }
                                part.completed = true
                            }
                        } catch (e: Exception) {
                            response?.close()
                            failedDownload = true
                        } finally {
                            synchronized(mergeWaiter) {
                                mergeWaiter.notifyAll()
                            }
                        }
                    } else {
                        break
                    }
                }
            }
        }

        var baseFile: UniFile? = null

        // scan for jobs following starting bytes order
        for (part in downloadParts) {
            // await for job to be completed (download of part has finished
            // should we really stop if download scope is not active? I think we should merge at least the next part
            synchronized(mergeWaiter) {
                while (!part.completed && downloadScope.isActive && !failedDownload) {
                    mergeWaiter.wait()
                }
            }

            // If some of part download has failed, then throw exception
            if (failedDownload || !downloadScope.isActive) {
                throw Exception("Download failed")
            } else {
                // otherwise transfer part into general tmp file

                baseFile = if (baseFile == null) {
                    part.file
                } else {
                    try {
                        mergeFile(baseFile, part.file)
                    } catch (e: Exception) {
                        throw Exception("Cannot merge tmp part file")
                    }
                }
            }
        }

        // do a final update, to ensure that the progress is 100%
        download.update(download.totalContentLength, download.totalContentLength, true)

        return if (downloadScope.isActive) {
            if (baseFile != null) {
                baseFile.renameTo("$filename.mp4")
                baseFile
            } else {
                throw Exception("Base file not found")
            }
        } else {
            throw Exception("Download stopped")
        }
    }

    /**
     * Retrieve part files placed into given directory, deletes all other files
     * A file is considered part file if his name is formatted as x.part.file where x is a Long
     *
     * @param tmpDir the root dir
     * @return a list of well formatted part file placed into given dir
     */
    private fun cleanAndGetPartFile(tmpDir: UniFile): List<UniFile> {
        // Filter file on extension and name formatting as Long
        val files = tmpDir.listFiles().orEmpty().filter {
            // If name is null also delete them
            if (it.name == null) {
                it.delete()
                false
            } else {
                // If extension is not .part.tmp also delete it
                if (!it.name!!.endsWith(".part.tmp")) {
                    it.delete()
                    false
                } else {
                    try {
                        it.name!!.substringBefore(".").toLong()
                        // If size of the file is 0 also delete it
                        if (it.size() == 0L) {
                            it.delete()
                            false
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        // If name is not formatted as Long throws an error, delete it in the case
                        it.delete()
                        false
                    }
                }
            }
        }

        return files
    }

    /**
     * Given a list of part files (their name must be composed by x.extension, where x is a Long),
     * return a list of pair that associates each file to the download range that it covers
     *
     * @param files list of part file, correctly formatted (x.extension, x = Long)
     * @return a list of pair associating file to a download range
     */
    private fun getRangesAndFiles(files: List<UniFile>): List<Pair<Pair<Long, Long>, UniFile>> {
        val result = mutableListOf<Pair<Pair<Long, Long>, UniFile>>()

        files.forEach {
            val startByte = it.name!!.substringBefore(".").toLong()
            val endByte = startByte + it.size() - 1
            result.add(Pair(Pair(startByte, endByte), it))
        }

        return result.toList()
    }

    /**
     * Merge download parts in order to reduce the total number of file used then in the downloader
     * Two successive parts are merged if the previous is completed and the following not
     */
    private fun mergeSuccessiveParts(parts: List<AnimeDownloadPart>): List<AnimeDownloadPart> {
        val result = mutableListOf<AnimeDownloadPart>()

        var i = 0
        val sortedParts = parts.sortedBy { it.range.first }

        // -1 since the last one has no successive to merge
        while (i < sortedParts.size - 1) {
            val part = sortedParts[i]
            result.add(part)
            if (part.completed && !sortedParts[i + 1].completed) {
                part.completed = false // not completed anymore
                part.range = sortedParts[i].range.copy(second = sortedParts[i + 1].range.second) // extends range
                part.request = sortedParts[i + 1].request // Assumes that not completed parts have at least a Request
                part.listener = sortedParts[i + 1].listener // same for listener
                i += 1 // skip the merged part
            }
            i += 1
        }
        // if the last one has not been merged then add it
        if (i < sortedParts.size) {
            result.add(sortedParts[i])
        }

        return result.toList()
    }

    /**
     * Check if two subsequent download ranges are touching each other,
     * in that case merge the two ranges and the corresponding files
     *
     * @param parts not merged nor sorted list of download ranges
     * @return a merged, not sorted, list of download ranges
     */
    private fun mergeSuccessiveFiles(
        parts: List<Pair<Pair<Long, Long>, UniFile>>,
    ): List<Pair<Pair<Long, Long>, UniFile>> {
        val newRanges = mutableListOf<Pair<Pair<Long, Long>, UniFile>>()

        // support variable that is used to merge multiple ranges
        var tempRange: Pair<Pair<Long, Long>, UniFile>? = null

        // sort range on ascending order, then for each one...
        parts.sortedBy { it.first.first }.forEach {
            tempRange = if (tempRange == null) {
                // If a temp range has not already been assigned then assign it
                it
            } else if (tempRange!!.first.second != it.first.first - 1) {
                // If the current range isn't touched by the temp one then add the previous to the final
                // list and set the current as the temp range
                newRanges.add(tempRange!!)
                it
            } else {
                // If the current range touches the temp one then merge them and assign the result to the temp
                Pair(
                    Pair(tempRange!!.first.first, it.first.second),
                    mergeFile(tempRange!!.second, it.second),
                )
            }
        }
        // This ensures that the last temp range is added to the list if present
        if (tempRange != null) {
            newRanges.add(tempRange!!)
        }

        return newRanges
    }

    /**
     * Takes two file and merge them appending the source to the sink
     *
     * @param sinkFile the sink
     * @param sourceFile the source
     * @return a file composed by appending the source to the sink
     */
    private fun mergeFile(sinkFile: UniFile?, sourceFile: UniFile): UniFile {
        if (sinkFile == null) {
            return sourceFile
        }

        val buffer = ByteArray(4 * 1024)
        val output = sinkFile.openOutputStream(true)
        val input = sourceFile.openInputStream()

        var bytesRead = input.read(buffer)
        while (bytesRead > 0) {
            output.write(buffer, 0, bytesRead)
            bytesRead = input.read(buffer)
        }

        output.flush()
        output.close()
        input.close()

        sourceFile.delete()

        return sinkFile
    }

    private fun getComplementaryRanges(
        range: Pair<Long, Long>,
        toRemove: List<Pair<Long, Long>>,
    ): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()

        var tempRange = range.copy()
        toRemove.sortedBy { it.first }.forEach {
            if (it.first > tempRange.first) {
                result.add(Pair(tempRange.first, it.first - 1))
            }
            tempRange = tempRange.copy(first = it.second + 1)
        }
        if (tempRange.first <= tempRange.second) {
            result.add(tempRange)
        }

        return result.toList()
    }

    private fun getDownloadParts(
        download: AnimeDownload,
        video: Video,
        tmpDir: UniFile,
        threadNumber: Int,
        videoSize: Long,
    ): List<AnimeDownloadPart> {
        // Get non empty part files
        val partFiles = cleanAndGetPartFile(tmpDir)

        // Retrieve from part files the downloaded ranges
        var downloadedRanges = getRangesAndFiles(partFiles).sortedByDescending { it.first.first }

        // Merge ranges and files that can form a unique range and file
        downloadedRanges = mergeSuccessiveFiles(downloadedRanges)

        // Get total downloaded size
        var downloadedSize = 0L
        downloadedRanges.forEach {
            downloadedSize += (it.first.second - it.first.first)
        }

        // Get all ranges that aren't downloaded
        val tempRanges = mutableListOf<Pair<Long, Long>>()
        downloadedRanges.forEach { tempRanges.add(it.first) }
        val complementaryRanges = getComplementaryRanges(Pair(0, videoSize - 1), tempRanges)

        // Calculate the parts size on new threadNumber value
        val partSize = maxOf(
            1024 * 1024,
            minOf(
                1024 * 1024 * 10,
                (videoSize - downloadedSize).floorDiv(threadNumber),
            ),
        )

        // Get part subdivision of non-downloaded ranges
        val rangesToDownload = mutableListOf<Pair<Long, Long>>()

        complementaryRanges.forEach { entry ->
            var partialToDownloadSize = entry.second - entry.first
            var tempStart = entry.first
            var tempEnd = tempStart + partSize
            // we subdivide in parts of at least partSize bytes
            while (partialToDownloadSize > 2 * partSize) {
                rangesToDownload.add(Pair(tempStart, tempEnd))
                partialToDownloadSize -= partSize
                tempStart = tempEnd + 1
                tempEnd = tempStart + partSize
            }
            rangesToDownload.add(Pair(tempStart, entry.second))
        }

        val downloadParts = mutableListOf<AnimeDownloadPart>()

        // Add downloaded ranges to parts as completed parts
        downloadedRanges.forEach { rF ->
            val part = AnimeDownloadPart(tmpDir, rF.first)
            part.file = rF.second
            part.completed = true
            downloadParts.add(part)
        }

        // Add ranges to download to parts as non-completed parts
        rangesToDownload.forEach { r ->
            val request = download.source.videoRequest(video, r.first, r.second)
            val listener = object : ProgressListener {
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                    download.update(download.bytesDownloaded, download.totalContentLength, false)
                }
            }
            val part = AnimeDownloadPart(tmpDir, r)
            part.completed = false
            part.request = request
            part.listener = listener
            downloadParts.add(part)
        }

        val mergedDownloadParts = mergeSuccessiveParts(downloadParts)

        // update downloaded size at sum of downloaded parts size
        download.bytesDownloaded = downloadedSize

        return mergedDownloadParts.toList()
    }

    /**
     * Returns the observable which downloads the video with an external downloader.
     *
     * @param video the video to download.
     * @param source the source of the video.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the video.
     */
    private fun downloadVideoExternal(
        video: Video,
        source: AnimeHttpSource,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        try {
            val file = tmpDir.createFile("${filename}_tmp.mp4")!!
            context.copyToClipboard("Episode download location", tmpDir.filePath!!.substringBeforeLast("_tmp"))

            // TODO: support other file formats!!
            // start download with intent
            val pm = context.packageManager
            val pkgName = preferences.externalDownloaderSelection().get()
            val intent: Intent
            if (pkgName.isNotEmpty()) {
                intent = pm.getLaunchIntentForPackage(pkgName) ?: throw Exception(
                    "Launch intent not found",
                )
                when {
                    // 1DM
                    pkgName.startsWith("idm.internet.download.manager") -> {
                        intent.apply {
                            component = ComponentName(
                                pkgName,
                                "idm.internet.download.manager.Downloader",
                            )
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(video.videoUrl)
                            putExtra("extra_filename", filename)
                        }
                    }
                    // ADM
                    pkgName.startsWith("com.dv.adm") -> {
                        val headers = (video.headers ?: source.headers).toList()
                        val bundle = Bundle()
                        headers.forEach { a ->
                            bundle.putString(
                                a.first,
                                a.second.replace("http", "h_ttp"),
                            )
                        }

                        intent.apply {
                            component = ComponentName(pkgName, "$pkgName.AEditor")
                            action = Intent.ACTION_VIEW
                            putExtra(
                                "com.dv.get.ACTION_LIST_ADD",
                                "${Uri.parse(video.videoUrl)}<info>$filename.mp4",
                            )
                            putExtra(
                                "com.dv.get.ACTION_LIST_PATH",
                                tmpDir.filePath!!.substringBeforeLast("_"),
                            )
                            putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                        }
                        file.delete()
                        tmpDir.delete()
                        queueState.value.find { anime -> anime.video == video }?.let { download ->
                            download.status = AnimeDownload.State.DOWNLOADED
                            // Delete successful downloads from queue
                            if (download.status == AnimeDownload.State.DOWNLOADED) {
                                // Remove downloaded episode from queue
                                removeFromQueue(download)
                            }
                            if (areAllAnimeDownloadsFinished()) {
                                stop()
                            }
                        }
                    }
                }
            } else {
                intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(Uri.parse(video.videoUrl), "video/*")
                    putExtra("extra_filename", filename)
                }
            }
            context.startActivity(intent)
            return file
        } catch (e: Exception) {
            tmpDir.findFile("${filename}_tmp.mp4")?.delete()
            throw e
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param animeDir the anime directory of the download.
     * @param tmpDir the directory where the download is currently stored.
     * @param dirname the real (non temporary) directory name of the download.
     */
    private suspend fun ensureSuccessfulAnimeDownload(
        download: AnimeDownload,
        animeDir: UniFile,
        tmpDir: UniFile,
        dirname: String,
    ) {
        // Ensure that the episode folder has the full video
        val downloadedVideo = tmpDir.listFiles().orEmpty().filterNot { it.extension == ".tmp" }

        download.status = if (downloadedVideo.size == 1) {
            // Only rename the directory if it's downloaded
            val filename = DiskUtil.buildValidFilename("${download.anime.title} - ${download.episode.name}")
            tmpDir.findFile("${filename}_tmp.mp4")?.delete()
            tmpDir.renameTo(dirname)

            cache.addEpisode(dirname, animeDir, download.anime)

            DiskUtil.createNoMediaFile(tmpDir, context)
            AnimeDownload.State.DOWNLOADED
        } else {
            throw Exception("Unable to finalize download")
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllAnimeDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= AnimeDownload.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<AnimeDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = AnimeDownload.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: AnimeDownload) {
        _queueState.update {
            store.remove(download)
            if (download.status == AnimeDownload.State.DOWNLOADING || download.status == AnimeDownload.State.QUEUE) {
                download.status = AnimeDownload.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (AnimeDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == AnimeDownload.State.DOWNLOADING ||
                    download.status == AnimeDownload.State.QUEUE
                ) {
                    download.status = AnimeDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads.toSet()
        }
    }

    fun removeFromQueue(episodes: List<Episode>) {
        val episodeIds = episodes.map { it.id }
        removeFromQueueIf { it.episode.id in episodeIds }
    }

    fun removeFromQueue(anime: Anime) {
        removeFromQueueIf { it.anime.id == anime.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == AnimeDownload.State.DOWNLOADING ||
                    download.status == AnimeDownload.State.QUEUE
                ) {
                    download.status = AnimeDownload.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<AnimeDownload>) {
        if (queueState == downloads) return

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        val wasRunning = isRunning

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 10
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 20
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
