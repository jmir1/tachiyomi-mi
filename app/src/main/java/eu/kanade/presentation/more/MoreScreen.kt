package eu.kanade.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.Constants
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Divider
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.injectLazy

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    isFDroid: Boolean,
    onClickAlt: () -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickStorage: () -> Unit,
    onClickBackupAndRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
            ) {
                if (isFDroid) {
                    WarningBanner(
                        textRes = R.string.fdroid_warning,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://akiled.org/help/faq/#how-do-i-migrate-from-the-f-droid-version")
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            item {
                LogoHeader()
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(R.string.label_downloaded_only),
                    subtitle = stringResource(R.string.downloaded_only_summary),
                    icon = Icons.Outlined.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(R.string.pref_incognito_mode),
                    subtitle = stringResource(R.string.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                )
            }

            item { Divider() }

            val libraryPreferences: LibraryPreferences by injectLazy()

            item {
                val bottomNavStyle = libraryPreferences.bottomNavStyle().get()
                val titleRes = when (bottomNavStyle) {
                    0 -> R.string.label_recent_manga
                    1 -> R.string.label_recent_updates
                    else -> R.string.label_manga
                }
                val icon = when (bottomNavStyle) {
                    0 -> Icons.Outlined.History
                    1 -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
                    else -> Icons.Outlined.CollectionsBookmark
                }
                TextPreferenceWidget(
                    title = stringResource(titleRes),
                    icon = icon,
                    onPreferenceClick = onClickAlt,
                )
            }

            item {
                val downloadQueueState = downloadQueueStateProvider()
                TextPreferenceWidget(
                    title = stringResource(R.string.label_download_queue),
                    subtitle = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(R.string.paused)
                            } else {
                                "${stringResource(R.string.paused)} • ${
                                pluralStringResource(
                                    id = R.plurals.download_queue_summary,
                                    count = pending,
                                    pending,
                                )
                                }"
                            }
                        }
                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(id = R.plurals.download_queue_summary, count = pending, pending)
                        }
                    },
                    icon = Icons.Outlined.GetApp,
                    onPreferenceClick = onClickDownloadQueue,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.general_categories),
                    icon = Icons.Outlined.Label,
                    onPreferenceClick = onClickCategories,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.label_stats),
                    icon = Icons.Outlined.QueryStats,
                    onPreferenceClick = onClickStats,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.label_storage),
                    icon = Icons.Outlined.Storage,
                    onPreferenceClick = onClickStorage,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.label_backup),
                    icon = Icons.Outlined.SettingsBackupRestore,
                    onPreferenceClick = onClickBackupAndRestore,
                )
            }

            item { Divider() }

            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.label_settings),
                    icon = Icons.Outlined.Settings,
                    onPreferenceClick = onClickSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.pref_category_about),
                    icon = Icons.Outlined.Info,
                    onPreferenceClick = onClickAbout,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.label_help),
                    icon = Icons.Outlined.HelpOutline,
                    onPreferenceClick = { uriHandler.openUri(Constants.URL_HELP) },
                )
            }
        }
    }
}
