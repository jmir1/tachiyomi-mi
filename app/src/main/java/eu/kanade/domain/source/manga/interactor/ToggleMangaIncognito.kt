package eu.kanade.domain.source.manga.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class ToggleMangaIncognito(
    private val preferences: SourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoMangaExtensions().getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
