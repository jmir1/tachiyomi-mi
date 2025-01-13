package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.SingleActionGesture
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsGesturesScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_gestures

    @Composable
    override fun getPreferences(): List<Preference> {
        val gesturePreferences = remember { Injekt.get<GesturePreferences>() }

        return listOf(
            getSlidersGroup(gesturePreferences = gesturePreferences),
            getSeekingGroup(gesturePreferences = gesturePreferences),
            getDoubleTapGroup(gesturePreferences = gesturePreferences),
            getMediaControlsGroup(gesturePreferences = gesturePreferences),
        )
    }

    @Composable
    private fun getSlidersGroup(gesturePreferences: GesturePreferences): Preference.PreferenceGroup {
        val enableVolumeBrightnessGestures = gesturePreferences.gestureVolumeBrightness()
        val swapVol = gesturePreferences.swapVolumeBrightness()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_player_sliders),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = enableVolumeBrightnessGestures,
                    title = stringResource(MR.strings.enable_volume_brightness_gestures),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = swapVol,
                    title = stringResource(MR.strings.pref_controls_swap_vol_brightness),
                ),
            ),
        )
    }

    @Composable
    private fun getSeekingGroup(gesturePreferences: GesturePreferences): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val enableHorizontalSeekGesture = gesturePreferences.gestureHorizontalSeek()
        val showSeekbar = gesturePreferences.showSeekBar()
        val defaultSkipIntroLength by gesturePreferences.defaultIntroLength().stateIn(scope).collectAsState()
        val skipLengthPreference = gesturePreferences.skipLengthPreference()
        val playerSmoothSeek = gesturePreferences.playerSmoothSeek()

        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            SkipIntroLengthDialog(
                initialSkipIntroLength = defaultSkipIntroLength,
                onDismissRequest = { showDialog = false },
                onValueChanged = { skipIntroLength ->
                    gesturePreferences.defaultIntroLength().set(skipIntroLength)
                    showDialog = false
                },
            )
        }

        // Aniskip
        val enableAniSkip = gesturePreferences.aniSkipEnabled()
        val enableAutoAniSkip = gesturePreferences.autoSkipAniSkip()
        val enableNetflixAniSkip = gesturePreferences.enableNetflixStyleAniSkip()
        val waitingTimeAniSkip = gesturePreferences.waitingTimeAniSkip()

        val isAniSkipEnabled by enableAniSkip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_player_seeking),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = enableHorizontalSeekGesture,
                    title = stringResource(MR.strings.enable_horizontal_seek_gesture),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = showSeekbar,
                    title = stringResource(MR.strings.pref_show_seekbar),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_default_intro_length),
                    subtitle = "${defaultSkipIntroLength}s",
                    onClick = { showDialog = true },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = skipLengthPreference,
                    title = stringResource(MR.strings.pref_skip_length),
                    entries = persistentMapOf(
                        30 to stringResource(MR.strings.pref_skip_30),
                        20 to stringResource(MR.strings.pref_skip_20),
                        10 to stringResource(MR.strings.pref_skip_10),
                        5 to stringResource(MR.strings.pref_skip_5),
                        3 to stringResource(MR.strings.pref_skip_3),
                        0 to stringResource(MR.strings.pref_skip_disable),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = playerSmoothSeek,
                    title = stringResource(MR.strings.pref_player_smooth_seek),
                    subtitle = stringResource(MR.strings.pref_player_smooth_seek_summary),
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_category_player_aniskip_info),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = enableAniSkip,
                    title = stringResource(MR.strings.pref_enable_aniskip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = enableAutoAniSkip,
                    title = stringResource(MR.strings.pref_enable_auto_skip_ani_skip),
                    enabled = isAniSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = enableNetflixAniSkip,
                    title = stringResource(MR.strings.pref_enable_netflix_style_aniskip),
                    enabled = isAniSkipEnabled,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = waitingTimeAniSkip,
                    title = stringResource(MR.strings.pref_waiting_time_aniskip),
                    entries = persistentMapOf(
                        5 to stringResource(MR.strings.pref_waiting_time_aniskip_5),
                        6 to stringResource(MR.strings.pref_waiting_time_aniskip_6),
                        7 to stringResource(MR.strings.pref_waiting_time_aniskip_7),
                        8 to stringResource(MR.strings.pref_waiting_time_aniskip_8),
                        9 to stringResource(MR.strings.pref_waiting_time_aniskip_9),
                        10 to stringResource(MR.strings.pref_waiting_time_aniskip_10),
                    ),
                    enabled = isAniSkipEnabled,
                ),
            ),
        )
    }

    @Composable
    private fun getDoubleTapGroup(gesturePreferences: GesturePreferences): Preference.PreferenceGroup {
        val leftDoubleTap = gesturePreferences.leftDoubleTapGesture()
        val centerDoubleTap = gesturePreferences.centerDoubleTapGesture()
        val rightDoubleTap = gesturePreferences.rightDoubleTapGesture()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_double_tap),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = leftDoubleTap,
                    title = stringResource(MR.strings.pref_left_double_tap),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.Seek,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Switch,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = centerDoubleTap,
                    title = stringResource(MR.strings.pref_center_double_tap),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = rightDoubleTap,
                    title = stringResource(MR.strings.pref_right_double_tap),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.Seek,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Switch,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_double_tap_info),
                ),
            ),
        )
    }

    @Composable
    private fun getMediaControlsGroup(gesturePreferences: GesturePreferences): Preference.PreferenceGroup {
        val mediaPrevious = gesturePreferences.mediaPreviousGesture()
        val mediaPlayPause = gesturePreferences.mediaPlayPauseGesture()
        val mediaNext = gesturePreferences.mediaNextGesture()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_media_controls),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = mediaPrevious,
                    title = stringResource(MR.strings.pref_media_previous),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.Seek,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Switch,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = mediaPlayPause,
                    title = stringResource(MR.strings.pref_media_playpause),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = mediaNext,
                    title = stringResource(MR.strings.pref_media_next),
                    entries = listOf(
                        SingleActionGesture.None,
                        SingleActionGesture.Seek,
                        SingleActionGesture.PlayPause,
                        SingleActionGesture.Switch,
                        SingleActionGesture.Custom,
                    ).associateWith { stringResource(it.stringRes) }.toPersistentMap(),
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_media_info),
                ),
            ),
        )
    }

    @Composable
    fun SkipIntroLengthDialog(
        initialSkipIntroLength: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (skipIntroLength: Int) -> Unit,
    ) {
        val skipIntroLengthValue by rememberSaveable { mutableStateOf(initialSkipIntroLength) }
        var newLength = 0
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MR.strings.pref_intro_length)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        WheelTextPicker(
                            modifier = Modifier.align(Alignment.Center),
                            items = remember { 0..255 }.map {
                                stringResource(
                                    MR.strings.seconds_short,
                                    it,
                                )
                            }.toImmutableList(),
                            onSelectionChanged = {
                                newLength = it
                            },
                            startIndex = skipIntroLengthValue,
                        )
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = { onValueChanged(newLength) }) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }
}
