package eu.kanade.presentation.theme.colorscheme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import eu.kanade.domain.ui.UiPreferences

internal class CustomColorScheme(uiPreferences: UiPreferences) : BaseColorScheme() {

    private val seed = uiPreferences.colorTheme().get()

    private val custom = CustomCompatColorScheme(seed)

    override val darkScheme
        get() = custom.darkScheme

    override val lightScheme
        get() = custom.lightScheme
}

private class CustomCompatColorScheme(seed: Int) : BaseColorScheme() {

    override val lightScheme = generateColorSchemeFromSeed(seed = seed, dark = false)
    override val darkScheme = generateColorSchemeFromSeed(seed = seed, dark = true)

    companion object {
        private fun Int.toComposeColor(): Color = Color(this)

        @SuppressLint("RestrictedApi")
        private fun generateColorSchemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
            val scheme = SchemeContent(
                Hct.fromInt(seed),
                dark,
                0.0,
            )
            val dynamicColors = MaterialDynamicColors()
            return ColorScheme(
                primary = dynamicColors.primary().getArgb(scheme).toComposeColor(),
                onPrimary = dynamicColors.onPrimary().getArgb(scheme).toComposeColor(),
                primaryContainer = dynamicColors.primaryContainer().getArgb(scheme).toComposeColor(),
                onPrimaryContainer = dynamicColors.onPrimaryContainer().getArgb(scheme).toComposeColor(),
                inversePrimary = dynamicColors.inversePrimary().getArgb(scheme).toComposeColor(),
                secondary = dynamicColors.secondary().getArgb(scheme).toComposeColor(),
                onSecondary = dynamicColors.onSecondary().getArgb(scheme).toComposeColor(),
                secondaryContainer = dynamicColors.secondaryContainer().getArgb(scheme).toComposeColor(),
                onSecondaryContainer = dynamicColors.onSecondaryContainer().getArgb(scheme).toComposeColor(),
                tertiary = dynamicColors.tertiary().getArgb(scheme).toComposeColor(),
                onTertiary = dynamicColors.onTertiary().getArgb(scheme).toComposeColor(),
                tertiaryContainer = dynamicColors.tertiary().getArgb(scheme).toComposeColor(),
                onTertiaryContainer = dynamicColors.onTertiaryContainer().getArgb(scheme).toComposeColor(),
                background = dynamicColors.background().getArgb(scheme).toComposeColor(),
                onBackground = dynamicColors.onBackground().getArgb(scheme).toComposeColor(),
                surface = dynamicColors.surface().getArgb(scheme).toComposeColor(),
                onSurface = dynamicColors.onSurface().getArgb(scheme).toComposeColor(),
                surfaceVariant = dynamicColors.surfaceVariant().getArgb(scheme).toComposeColor(),
                onSurfaceVariant = dynamicColors.onSurfaceVariant().getArgb(scheme).toComposeColor(),
                surfaceTint = dynamicColors.surfaceTint().getArgb(scheme).toComposeColor(),
                inverseSurface = dynamicColors.inverseSurface().getArgb(scheme).toComposeColor(),
                inverseOnSurface = dynamicColors.inverseOnSurface().getArgb(scheme).toComposeColor(),
                error = dynamicColors.error().getArgb(scheme).toComposeColor(),
                onError = dynamicColors.onError().getArgb(scheme).toComposeColor(),
                errorContainer = dynamicColors.errorContainer().getArgb(scheme).toComposeColor(),
                onErrorContainer = dynamicColors.onErrorContainer().getArgb(scheme).toComposeColor(),
                outline = dynamicColors.outline().getArgb(scheme).toComposeColor(),
                outlineVariant = dynamicColors.outlineVariant().getArgb(scheme).toComposeColor(),
                scrim = Color.Black,
                surfaceBright = dynamicColors.surfaceBright().getArgb(scheme).toComposeColor(),
                surfaceDim = dynamicColors.surfaceDim().getArgb(scheme).toComposeColor(),
                surfaceContainer = dynamicColors.surfaceContainer().getArgb(scheme).toComposeColor(),
                surfaceContainerHigh = dynamicColors.surfaceContainerHigh().getArgb(scheme).toComposeColor(),
                surfaceContainerHighest = dynamicColors.surfaceContainerHighest().getArgb(scheme).toComposeColor(),
                surfaceContainerLow = dynamicColors.surfaceContainerLow().getArgb(scheme).toComposeColor(),
                surfaceContainerLowest = dynamicColors.surfaceContainerLowest().getArgb(scheme).toComposeColor(),
            )
        }
    }
}
