package eu.kanade.domain.source.anime.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetAnimeIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: AnimeExtensionManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        val extensionPackage = extensionManager.getExtensionPackage(sourceId) ?: return false
        return extensionPackage in sourcePreferences.incognitoAnimeExtensions().get()
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()
        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoAnimeExtensions().changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
        ) { incognito, incognitoExtensions, extensionPackage ->
            incognito || (extensionPackage in incognitoExtensions)
        }
            .distinctUntilChanged()
    }
}
