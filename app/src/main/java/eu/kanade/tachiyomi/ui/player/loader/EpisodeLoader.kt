package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.domain.items.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Loader used to retrieve the video links for a given episode.
 */
class EpisodeLoader {

    companion object {

        private var errorMessage = ""

        /**
         * Returns an observable list of videos of an [episode] based on the type of [source] used.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        suspend fun getLinks(episode: Episode, anime: Anime, source: AnimeSource): List<Video> {
            val isDownloaded = isDownload(episode, anime)
            return when {
                isDownloaded -> isDownload(episode, anime, source)
                source is AnimeHttpSource -> isHttp(episode, source)
                source is LocalAnimeSource -> isLocal(episode)
                else -> error("source not supported")
            }
        }

        /**
         * Returns true if the given [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         */
        fun isDownload(episode: Episode, anime: Anime): Boolean {
            val downloadManager: AnimeDownloadManager = Injekt.get()
            return downloadManager.isEpisodeDownloaded(
                episode.name,
                episode.scanlator,
                anime.title,
                anime.source,
                skipCache = true,
            )
        }

        /**
         * Returns an list of videos when the [episode] is online.
         *
         * @param episode the episode being parsed.
         * @param source the online source of the episode.
         */
        private suspend fun isHttp(episode: Episode, source: AnimeHttpSource): List<Video> {
            val videos = source.getVideoList(episode.toSEpisode()).map {
                it.apply { status = Video.State.LOAD_VIDEO }
            }

            videos.filter { it.videoUrl.isNullOrEmpty() }.forEach { video ->
                try {
                    video.videoUrl = source.getVideoUrl(video)
                    video.status = Video.State.READY
                } catch (e: Throwable) {
                    video.status = Video.State.ERROR
                }
            }

            return videos
        }

        /**
         * Returns an observable list of videos when the [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        private fun isDownload(
            episode: Episode,
            anime: Anime,
            source: AnimeSource,
        ): List<Video> {
            val downloadManager: AnimeDownloadManager = Injekt.get()
            return try {
                val video = downloadManager.buildVideo(source, anime, episode)
                listOf(video)
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /**
         * Returns an list of videos when the [episode] is from local source.
         *
         * @param episode the episode being parsed.
         */
        private fun isLocal(
            episode: Episode,
        ): List<Video> {
            return try {
                val (animeDirName, episodeName) = episode.url.split('/', limit = 2)
                val fileSystem: LocalAnimeSourceFileSystem = Injekt.get()
                val videoFile = fileSystem.getBaseDirectory()
                    ?.findFile(animeDirName)
                    ?.findFile(episodeName)
                val videoUri = videoFile!!.uri

                val video = Video(
                    videoUri.toString(),
                    "Local source: ${episode.url}",
                    videoUri.toString(),
                    videoUri,
                )
                listOf(video)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error getting links"
                emptyList()
            }
        }
    }
}
