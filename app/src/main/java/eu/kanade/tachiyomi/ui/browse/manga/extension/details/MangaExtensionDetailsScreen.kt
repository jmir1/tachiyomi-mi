package eu.kanade.tachiyomi.ui.browse.manga.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MangaExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class MangaExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            MangaExtensionDetailsScreenModel(
                pkgName = pkgName,
                context = context,
            )
        }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        MangaExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(MangaSourcePreferencesScreen(it)) },
            onClickEnableAll = { screenModel.toggleSources(true) },
            onClickDisableAll = { screenModel.toggleSources(false) },
            onClickClearCookies = screenModel::clearCookies,
            onClickUninstall = screenModel::uninstallExtension,
            onClickSource = screenModel::toggleSource,
            onClickIncognito = screenModel::toggleIncognito,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is MangaExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
