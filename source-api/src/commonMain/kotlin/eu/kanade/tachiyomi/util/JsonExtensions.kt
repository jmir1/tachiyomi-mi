package eu.kanade.tachiyomi.util

import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * App provided default [Json] instance. Configured as
 * ```
 * Json {
 *     ignoreUnknownKeys = true
 *     explicitNulls = false
 * }
 * ```
 *
 * @since extensions-lib 16
 */
val defaultJson: Json = Injekt.get<Json>()
