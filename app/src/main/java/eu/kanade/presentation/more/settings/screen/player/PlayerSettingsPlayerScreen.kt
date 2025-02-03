package eu.kanade.presentation.more.settings.screen.player

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.JUST_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_KT
import eu.kanade.tachiyomi.ui.player.MPV_KT_PREVIEW
import eu.kanade.tachiyomi.ui.player.MPV_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_REMOTE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_FREE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_PRO
import eu.kanade.tachiyomi.ui.player.NEXT_PLAYER
import eu.kanade.tachiyomi.ui.player.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.VLC_PLAYER
import eu.kanade.tachiyomi.ui.player.WEB_VIDEO_CASTER
import eu.kanade.tachiyomi.ui.player.X_PLAYER
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

object PlayerSettingsPlayerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_internal

    @Composable
    override fun getPreferences(): List<Preference> {
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val deviceSupportsPip = basePreferences.deviceHasPip()

        return listOfNotNull(
            Preference.PreferenceItem.ListPreference(
                pref = playerPreferences.progressPreference(),
                title = stringResource(MR.strings.pref_progress_mark_as_seen),
                entries = persistentMapOf(
                    1.00F to stringResource(MR.strings.pref_progress_100),
                    0.95F to stringResource(MR.strings.pref_progress_95),
                    0.90F to stringResource(MR.strings.pref_progress_90),
                    0.85F to stringResource(MR.strings.pref_progress_85),
                    0.80F to stringResource(MR.strings.pref_progress_80),
                    0.75F to stringResource(MR.strings.pref_progress_75),
                    0.70F to stringResource(MR.strings.pref_progress_70),
                ),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = playerPreferences.preserveWatchingPosition(),
                title = stringResource(MR.strings.pref_preserve_watching_position),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = playerPreferences.defaultPlayerOrientationType(),
                title = stringResource(MR.strings.pref_category_player_orientation),
                entries = PlayerOrientation.entries.associateWith {
                    stringResource(it.titleRes)
                }.toPersistentMap(),
            ),
            getControlsGroup(playerPreferences = playerPreferences),
            getHosterGroup(playerPreferences = playerPreferences),
            getDisplayGroup(playerPreferences = playerPreferences),
            if (deviceSupportsPip) getPipGroup(playerPreferences = playerPreferences) else null,
            getExternalPlayerGroup(
                playerPreferences = playerPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getControlsGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val allowGestures = playerPreferences.allowGestures()
        val showLoading = playerPreferences.showLoadingCircle()
        val showChapter = playerPreferences.showCurrentChapter()
        val rememberPlayerBrightness = playerPreferences.rememberPlayerBrightness()
        val rememberPlayerVolume = playerPreferences.rememberPlayerVolume()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_controls),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = allowGestures,
                    title = stringResource(MR.strings.pref_controls_allow_gestures_in_panels),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = showLoading,
                    title = stringResource(MR.strings.pref_controls_show_loading),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = showChapter,
                    title = stringResource(MR.strings.pref_controls_show_chapter_indicator),
                    subtitle = stringResource(MR.strings.pref_controls_show_chapter_indicator_info),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rememberPlayerBrightness,
                    title = stringResource(MR.strings.pref_remember_brightness),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = rememberPlayerVolume,
                    title = stringResource(MR.strings.pref_remember_volume),
                ),
            ),
        )
    }

    @Composable
    private fun getHosterGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val showFailure = playerPreferences.showFailedHosters()
        val showEmpty = playerPreferences.showEmptyHosters()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_hosters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = showFailure,
                    title = stringResource(MR.strings.pref_hosters_show_failure),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = showEmpty,
                    title = stringResource(MR.strings.pref_hosters_show_empty),
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val fullScreen = playerPreferences.playerFullscreen()
        val hideControls = playerPreferences.hideControls()
        val displayVol = playerPreferences.displayVolPer()
        val showSystemBar = playerPreferences.showSystemStatusBar()
        val reduceMotion = playerPreferences.reduceMotion()
        val hideTime = playerPreferences.playerTimeToDisappear()

        val panelOpacityPref = playerPreferences.panelOpacity()
        val panelOpacity by panelOpacityPref.collectAsState()
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = fullScreen,
                    title = stringResource(MR.strings.pref_player_fullscreen),
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = hideControls,
                    title = stringResource(MR.strings.pref_player_hide_controls),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = displayVol,
                    title = stringResource(MR.strings.pref_controls_display_volume_percentage),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = showSystemBar,
                    title = stringResource(MR.strings.pref_show_system_bar),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = reduceMotion,
                    title = stringResource(MR.strings.pref_reduce_motion),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = hideTime,
                    title = stringResource(MR.strings.pref_player_time_to_disappear),
                    entries = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000).associateWith {
                        stringResource(MR.strings.pref_player_time_to_disappear_summary, it)
                    }.toPersistentMap(),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = panelOpacity,
                    title = stringResource(MR.strings.pref_panel_opacity),
                    subtitle = numberFormat.format(panelOpacity / 100f),
                    min = 0,
                    max = 100,
                    onValueChanged = {
                        panelOpacityPref.set(it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getPipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val enablePip = playerPreferences.enablePip()
        val pipEpisodeToasts = playerPreferences.pipEpisodeToasts()
        val pipOnExit = playerPreferences.pipOnExit()
        val pipReplaceWithPrevious = playerPreferences.pipReplaceWithPrevious()

        val isPipEnabled by enablePip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_pip),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = enablePip,
                    title = stringResource(MR.strings.pref_enable_pip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = pipEpisodeToasts,
                    title = stringResource(MR.strings.pref_pip_episode_toasts),
                    enabled = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = pipOnExit,
                    title = stringResource(MR.strings.pref_pip_on_exit),
                    enabled = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = pipReplaceWithPrevious,
                    title = stringResource(MR.strings.pref_pip_replace_with_previous),
                    enabled = isPipEnabled,
                ),
            ),
        )
    }

    @Composable
    private fun getExternalPlayerGroup(
        playerPreferences: PlayerPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer()
        val externalPlayerPreference = playerPreferences.externalPlayerPreference()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedPlayers = installedPackages.filter { it.packageName in externalPlayers }

        val packageNames = supportedPlayers.map { it.packageName }
        val packageNamesReadable = supportedPlayers
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            packageNames.zip(packageNamesReadable)
                .toMap()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_external_player),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = alwaysUseExternalPlayer,
                    title = stringResource(MR.strings.pref_always_use_external_player),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = externalPlayerPreference,
                    title = stringResource(MR.strings.pref_external_player_preference),
                    entries = (mapOf("" to "None") + packageNamesMap).toPersistentMap(),
                ),
            ),
        )
    }
}

val externalPlayers = listOf(
    MPV_PLAYER,
    MX_PLAYER,
    MX_PLAYER_FREE,
    MX_PLAYER_PRO,
    VLC_PLAYER,
    MPV_KT,
    MPV_KT_PREVIEW,
    MPV_REMOTE,
    JUST_PLAYER,
    NEXT_PLAYER,
    X_PLAYER,
    WEB_VIDEO_CASTER,
)
