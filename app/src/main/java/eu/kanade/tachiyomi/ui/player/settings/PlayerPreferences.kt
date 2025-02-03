package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.ui.player.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.VideoAspect
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preserveWatchingPosition() = preferenceStore.getBoolean(
        "pref_preserve_watching_position",
        false,
    )
    fun progressPreference() = preferenceStore.getFloat("pref_progress_preference", 0.85F)
    fun defaultPlayerOrientationType() = preferenceStore.getEnum(
        "pref_default_player_orientation_type_key",
        PlayerOrientation.SensorLandscape,
    )

    // Controls

    fun allowGestures() = preferenceStore.getBoolean("pref_allow_gestures_in_panels", false)
    fun showLoadingCircle() = preferenceStore.getBoolean("pref_show_loading", true)
    fun showCurrentChapter() = preferenceStore.getBoolean("pref_show_current_chapter", true)
    fun rememberPlayerBrightness() = preferenceStore.getBoolean("pref_remember_brightness", false)
    fun playerBrightnessValue() = preferenceStore.getFloat("player_brightness_value", -1.0F)
    fun rememberPlayerVolume() = preferenceStore.getBoolean("pref_remember_volume", false)
    fun playerVolumeValue() = preferenceStore.getFloat("player_volume_value", -1.0F)

    // Hoster

    fun showFailedHosters() = preferenceStore.getBoolean("pref_show_failed_hosters", false)
    fun showEmptyHosters() = preferenceStore.getBoolean("pref_show_empty_hosters", false)

    // Display

    fun playerFullscreen() = preferenceStore.getBoolean("player_fullscreen", true)
    fun hideControls() = preferenceStore.getBoolean("player_hide_controls", false)
    fun displayVolPer() = preferenceStore.getBoolean("pref_display_vol_as_per", true)
    fun showSystemStatusBar() = preferenceStore.getBoolean("pref_show_system_status_bar", false)
    fun reduceMotion() = preferenceStore.getBoolean("pref_reduce_motion", false)
    fun playerTimeToDisappear() = preferenceStore.getInt("pref_player_time_to_disappear", 4000)
    fun panelOpacity() = preferenceStore.getInt("pref_panel_opacity", 60)

    // PiP

    fun enablePip() = preferenceStore.getBoolean("pref_enable_pip", true)
    fun pipEpisodeToasts() = preferenceStore.getBoolean("pref_pip_episode_toasts", true)
    fun pipOnExit() = preferenceStore.getBoolean("pref_pip_on_exit", false)
    fun pipReplaceWithPrevious() = preferenceStore.getBoolean("pip_replace_with_previous", false)

    // External player

    fun alwaysUseExternalPlayer() = preferenceStore.getBoolean(
        "pref_always_use_external_player",
        false,
    )
    fun externalPlayerPreference() = preferenceStore.getString("external_player_preference", "")

    // Non-preferences

    fun playerSpeed() = preferenceStore.getFloat("pref_player_speed", 1f)
    fun speedPresets() = preferenceStore.getStringSet(
        "default_speed_presets",
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
    )
    fun invertDuration() = preferenceStore.getBoolean("invert_duration", false)
    fun aspectState() = preferenceStore.getEnum("pref_player_aspect_state", VideoAspect.Fit)

    // Old

    fun autoplayEnabled() = preferenceStore.getBoolean("pref_auto_play_enabled", false)
}
