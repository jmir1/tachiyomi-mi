package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.presentation.browse.anime.components.RemoveEntryDialog
import eu.kanade.presentation.browse.manga.BrowseSourceContent
import eu.kanade.presentation.browse.manga.MissingSourceScreen
import eu.kanade.presentation.browse.manga.components.BrowseMangaSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.manga.LocalMangaSource

data class BrowseMangaSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifMangaSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { BrowseMangaSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(
                    null,
                )
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubMangaSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalMangaSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    BrowseMangaSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(MangaSourcePreferencesScreen(sourceId)) },
                        onSearch = screenModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as CatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                onMangaClick = { navigator.push((MangaScreen(it.id, true))) },
                onMangaLongClick = { manga ->
                    scope.launchIO {
                        val duplicateManga = screenModel.getDuplicateLibraryManga(manga)
                        when {
                            manga.favorite -> screenModel.setDialog(
                                BrowseMangaSourceScreenModel.Dialog.RemoveManga(manga),
                            )
                            duplicateManga != null -> screenModel.setDialog(
                                BrowseMangaSourceScreenModel.Dialog.AddDuplicateManga(
                                    manga,
                                    duplicateManga,
                                ),
                            )
                            else -> screenModel.addFavorite(manga)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseMangaSourceScreenModel.Dialog.Filter -> {
                SourceFilterMangaDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.setDialog(
                            BrowseMangaSourceScreenModel.Dialog.Migrate(dialog.manga, dialog.duplicate),
                        )
                    },
                )
            }

            is BrowseMangaSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    screenModel = MigrateMangaDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.RemoveManga -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    entryToRemove = dialog.manga.title,
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(CategoriesTab)
                        CategoriesTab.showMangaCategory()
                    },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            else -> {}
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
