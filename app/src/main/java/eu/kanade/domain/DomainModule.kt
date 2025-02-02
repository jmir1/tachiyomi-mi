package eu.kanade.domain

import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.ToggleMangaIncognito
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.tachiyomi.util.TrackSelect
import mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl
import mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepoCount
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl
import tachiyomi.data.category.manga.MangaCategoryRepositoryImpl
import tachiyomi.data.custombutton.CustomButtonRepositoryImpl
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl
import tachiyomi.data.history.manga.MangaHistoryRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.anime.AnimeSourceRepositoryImpl
import tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl
import tachiyomi.data.track.anime.AnimeTrackRepositoryImpl
import tachiyomi.data.track.manga.MangaTrackRepositoryImpl
import tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl
import tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory
import tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.manga.interactor.UpdateMangaCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodes
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.manga.interactor.GetMangaSourcesWithNonLibraryManga
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AnimeCategoryRepository> { AnimeCategoryRepositoryImpl(get()) }
        addFactory { GetAnimeCategories(get()) }
        addFactory { GetVisibleAnimeCategories(get()) }
        addFactory { ResetAnimeCategoryFlags(get(), get()) }
        addFactory { SetAnimeDisplayMode(get()) }
        addFactory { SetSortModeForAnimeCategory(get(), get()) }
        addFactory { CreateAnimeCategoryWithName(get(), get()) }
        addFactory { RenameAnimeCategory(get()) }
        addFactory { ReorderAnimeCategory(get()) }
        addFactory { UpdateAnimeCategory(get()) }
        addFactory { HideAnimeCategory(get()) }
        addFactory { DeleteAnimeCategory(get()) }

        addSingletonFactory<MangaCategoryRepository> { MangaCategoryRepositoryImpl(get()) }
        addFactory { GetMangaCategories(get()) }
        addFactory { GetVisibleMangaCategories(get()) }
        addFactory { ResetMangaCategoryFlags(get(), get()) }
        addFactory { SetMangaDisplayMode(get()) }
        addFactory { SetSortModeForMangaCategory(get(), get()) }
        addFactory { CreateMangaCategoryWithName(get(), get()) }
        addFactory { RenameMangaCategory(get()) }
        addFactory { ReorderMangaCategory(get()) }
        addFactory { UpdateMangaCategory(get()) }
        addFactory { HideMangaCategory(get()) }
        addFactory { DeleteMangaCategory(get()) }

        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryAnime(get()) }
        addFactory { GetAnimeFavorites(get()) }
        addFactory { GetLibraryAnime(get()) }
        addFactory { GetAnimeWithEpisodes(get(), get()) }
        addFactory { GetAnimeByUrlAndSourceId(get()) }
        addFactory { GetAnime(get()) }
        addFactory { GetNextEpisodes(get(), get(), get()) }
        addFactory { GetUpcomingAnime(get()) }
        addFactory { ResetAnimeViewerFlags(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }
        addFactory { AnimeFetchInterval(get()) }
        addFactory { SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { SetAnimeViewerFlags(get()) }
        addFactory { NetworkToLocalAnime(get()) }
        addFactory { UpdateAnime(get(), get()) }
        addFactory { SetAnimeCategories(get()) }

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetMangaFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get()) }
        addFactory { GetUpcomingManga(get()) }
        addFactory { ResetMangaViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { MangaFetchInterval(get()) }
        addFactory {
            SetMangaDefaultChapterFlags(
                get(),
                get(),
                get(),
            )
        }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { SetMangaCategories(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addSingletonFactory<AnimeTrackRepository> { AnimeTrackRepositoryImpl(get()) }
        addFactory { TrackEpisode(get(), get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { RefreshAnimeTracks(get(), get(), get(), get()) }
        addFactory { DeleteAnimeTrack(get()) }
        addFactory { GetTracksPerAnime(get()) }
        addFactory { GetAnimeTracks(get()) }
        addFactory { InsertAnimeTrack(get()) }
        addFactory { SyncEpisodeProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<MangaTrackRepository> { MangaTrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddMangaTracks(get(), get(), get(), get()) }
        addFactory { RefreshMangaTracks(get(), get(), get(), get()) }
        addFactory { DeleteMangaTrack(get()) }
        addFactory { GetTracksPerManga(get()) }
        addFactory { GetMangaTracks(get()) }
        addFactory { InsertMangaTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
        addFactory { GetEpisode(get()) }
        addFactory { GetEpisodesByAnimeId(get()) }
        addFactory { GetEpisodeByUrlAndAnimeId(get()) }
        addFactory { UpdateEpisode(get()) }
        addFactory { SetSeenStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbEpisode() }
        addFactory { SyncEpisodesWithSource(get(), get(), get(), get(), get(), get(), get()) }
        addFactory { FilterEpisodesForDownload(get(), get(), get()) }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }
        addFactory { FilterChaptersForDownload(get(), get(), get()) }

        addSingletonFactory<AnimeHistoryRepository> { AnimeHistoryRepositoryImpl(get()) }
        addFactory { GetAnimeHistory(get()) }
        addFactory { UpsertAnimeHistory(get()) }
        addFactory { RemoveAnimeHistory(get()) }

        addFactory { DeleteEpisodeDownload(get(), get()) }

        addFactory { GetAnimeExtensionsByType(get(), get()) }
        addFactory { GetAnimeExtensionSources(get()) }
        addFactory { GetAnimeExtensionLanguages(get(), get()) }

        addSingletonFactory<MangaHistoryRepository> { MangaHistoryRepositoryImpl(get()) }
        addFactory { GetMangaHistory(get()) }
        addFactory { UpsertMangaHistory(get()) }
        addFactory { RemoveMangaHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteChapterDownload(get(), get()) }

        addFactory { GetMangaExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetMangaExtensionLanguages(get(), get()) }

        addSingletonFactory<AnimeUpdatesRepository> { AnimeUpdatesRepositoryImpl(get()) }
        addFactory { GetAnimeUpdates(get()) }

        addSingletonFactory<MangaUpdatesRepository> { MangaUpdatesRepositoryImpl(get()) }
        addFactory { GetMangaUpdates(get()) }

        addSingletonFactory<AnimeSourceRepository> { AnimeSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<AnimeStubSourceRepository> { AnimeStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledAnimeSources(get(), get()) }
        addFactory { GetLanguagesWithAnimeSources(get(), get()) }
        addFactory { GetRemoteAnime(get()) }
        addFactory { GetAnimeSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetAnimeSourcesWithNonLibraryAnime(get()) }
        addFactory { ToggleAnimeSource(get()) }
        addFactory { ToggleAnimeSourcePin(get()) }

        addSingletonFactory<MangaSourceRepository> { MangaSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<MangaStubSourceRepository> { MangaStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledMangaSources(get(), get()) }
        addFactory { GetLanguagesWithMangaSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetMangaSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetMangaSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleMangaSource(get()) }
        addFactory { ToggleMangaSourcePin(get()) }
        addFactory { TrustAnimeExtension(get(), get()) }
        addFactory { TrustMangaExtension(get(), get()) }

        addFactory { ExtensionRepoService(get(), get()) }

        addSingletonFactory<AnimeExtensionRepoRepository> { AnimeExtensionRepoRepositoryImpl(get()) }
        addFactory { GetAnimeExtensionRepo(get()) }
        addFactory { GetAnimeExtensionRepoCount(get()) }
        addFactory { CreateAnimeExtensionRepo(get(), get()) }
        addFactory { DeleteAnimeExtensionRepo(get()) }
        addFactory { ReplaceAnimeExtensionRepo(get()) }
        addFactory { UpdateAnimeExtensionRepo(get(), get()) }
        addFactory { ToggleAnimeIncognito(get()) }
        addFactory { GetAnimeIncognitoState(get(), get(), get()) }

        addSingletonFactory<MangaExtensionRepoRepository> { MangaExtensionRepoRepositoryImpl(get()) }
        addFactory { GetMangaExtensionRepo(get()) }
        addFactory { GetMangaExtensionRepoCount(get()) }
        addFactory { CreateMangaExtensionRepo(get(), get()) }
        addFactory { DeleteMangaExtensionRepo(get()) }
        addFactory { ReplaceMangaExtensionRepo(get()) }
        addFactory { UpdateMangaExtensionRepo(get(), get()) }
        addFactory { ToggleMangaIncognito(get()) }
        addFactory { GetMangaIncognitoState(get(), get(), get()) }

        addSingletonFactory<CustomButtonRepository> { CustomButtonRepositoryImpl(get()) }
        addFactory { CreateCustomButton(get()) }
        addFactory { DeleteCustomButton(get()) }
        addFactory { GetCustomButtons(get()) }
        addFactory { UpdateCustomButton(get()) }
        addFactory { ReorderCustomButton(get()) }
        addFactory { ToggleFavoriteCustomButton(get()) }

        addFactory { TrackSelect(get(), get()) }
    }
}
