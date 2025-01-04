package eu.kanade.tachiyomi.ui.browse.anime.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

data class AnimeMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): AnimeMigrationFlag {
            return AnimeMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object AnimeMigrationFlags {

    private const val EPISODES = 0b00001
    private const val CATEGORIES = 0b00010
    private const val CUSTOM_COVER = 0b01000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: AnimeCoverCache by injectLazy()
    private val downloadCache: AnimeDownloadCache by injectLazy()

    fun hasEpisodes(value: Int): Boolean {
        return value and EPISODES != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    /** Returns information about applicable flags with default selections. */
    fun getFlags(anime: Anime?, defaultSelectedBitMap: Int): List<AnimeMigrationFlag> {
        val flags = mutableListOf<AnimeMigrationFlag>()
        flags += AnimeMigrationFlag.create(EPISODES, defaultSelectedBitMap, MR.strings.episodes)
        flags += AnimeMigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)

        if (anime != null) {
            if (anime.hasCustomCover(coverCache)) {
                flags += AnimeMigrationFlag.create(
                    CUSTOM_COVER,
                    defaultSelectedBitMap,
                    MR.strings.custom_cover,
                )
            }
            if (downloadCache.getDownloadCount(anime) > 0) {
                flags += AnimeMigrationFlag.create(
                    DELETE_DOWNLOADED,
                    defaultSelectedBitMap,
                    MR.strings.delete_downloaded,
                )
            }
        }
        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<AnimeMigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
