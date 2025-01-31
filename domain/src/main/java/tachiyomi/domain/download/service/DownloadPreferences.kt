package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun showEpisodeFileSize() = preferenceStore.getBoolean("pref_downloaded_episode_size", true)

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun useExternalDownloader() = preferenceStore.getBoolean("use_external_downloader", false)

    fun externalDownloaderSelection() = preferenceStore.getString(
        "external_downloader_selection",
        "",
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)
    fun autoDownloadWhileWatching() = preferenceStore.getInt("auto_download_while_watching", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(
        "remove_exclude_categories",
        emptySet(),
    )
    fun removeExcludeAnimeCategories() = preferenceStore.getStringSet(
        "remove_exclude_anime_categories",
        emptySet(),
    )

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)
    fun downloadNewEpisodes() = preferenceStore.getBoolean("download_new_episode", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(
        "download_new_categories",
        emptySet(),
    )
    fun downloadNewEpisodeCategories() = preferenceStore.getStringSet(
        "download_new_anime_categories",
        emptySet(),
    )

    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet(
        "download_new_categories_exclude",
        emptySet(),
    )
    fun downloadNewEpisodeCategoriesExclude() = preferenceStore.getStringSet(
        "download_new_anime_categories_exclude",
        emptySet(),
    )

    fun numberOfDownloads() = preferenceStore.getInt("download_slots", 1)
    fun safeDownload() = preferenceStore.getBoolean("safe_download", true)
    fun numberOfThreads() = preferenceStore.getInt("download_threads", 1)
    fun downloadSpeedLimit() = preferenceStore.getInt("download_speed_limit", 0)

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)
    fun downloadNewUnseenEpisodesOnly() = preferenceStore.getBoolean("download_new_unread_episodes_only", false)
}
