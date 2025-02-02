package eu.kanade.tachiyomi.util

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.OffsetDateTime
import java.time.ZoneId

class CrashLogUtil(
    private val context: Context,
    private val mangaExtensionManager: MangaExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
) {

    suspend fun dumpLogs(exception: Throwable? = null) = withNonCancellableContext {
        try {
            val file = context.createFileInCacheDir("aniyomi_crash_logs.txt")

            file.appendText(getDebugInfo() + "\n\n")
            getMangaExtensionsInfo()?.let { file.appendText("$it\n\n") }
            getAnimeExtensionsInfo()?.let { file.appendText("$it\n\n") }
            exception?.let { file.appendText("$it\n\n") }

            Runtime.getRuntime().exec("logcat *:E -d -v year -v zone -f ${file.absolutePath}").waitFor()

            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (e: Throwable) {
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    fun getDebugInfo(): String {
        return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${WebViewUtil.getVersion(context)}
            Current time: ${OffsetDateTime.now(ZoneId.systemDefault())}
            MPV version: 6764488
            Libplacebo version: v7.349.0
            FFmpeg version: n7.1
        """.trimIndent()
        // TODO: Use this again (from aniyomi-mpv-lib 1.17.n onwards):

        //    MPV version: ${Utils.VERSIONS.mpv}
        //    Libplacebo version: ${Utils.VERSIONS.libPlacebo}
        //    FFmpeg version: ${Utils.VERSIONS.ffmpeg}
    }

    private fun getMangaExtensionsInfo(): String? {
        val availableExtensions = mangaExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val extensionInfoList = mangaExtensionManager.installedExtensionsFlow.value
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Obsolete: ${it.isObsolete}
                """.trimIndent()
            }

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }

    private fun getAnimeExtensionsInfo(): String? {
        val availableExtensions = animeExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val extensionInfoList = animeExtensionManager.installedExtensionsFlow.value
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Obsolete: ${it.isObsolete}
                """.trimIndent()
            }

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}
