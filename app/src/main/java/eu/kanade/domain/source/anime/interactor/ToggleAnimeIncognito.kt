package eu.kanade.domain.source.anime.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class ToggleAnimeIncognito(
    private val preferences: SourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoAnimeExtensions().getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
