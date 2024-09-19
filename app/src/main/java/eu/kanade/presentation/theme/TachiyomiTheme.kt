package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CloudflareColorScheme
import eu.kanade.presentation.theme.colorscheme.CottoncandyColorScheme
import eu.kanade.presentation.theme.colorscheme.CustomColorScheme
import eu.kanade.presentation.theme.colorscheme.DoomColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MatrixColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MochaColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.SapphireColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme().get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled().get(),
        content = content,
    )
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(appTheme, isAmoled, content)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = getThemeColorScheme(appTheme, isAmoled),
        content = content,
    )
}

@Composable
@ReadOnlyComposable
private fun getThemeColorScheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
): ColorScheme {
    val uiPreferences = Injekt.get<UiPreferences>()
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(LocalContext.current)
    } else if (appTheme == AppTheme.CUSTOM) {
        CustomColorScheme(uiPreferences)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isSystemInDarkTheme(),
        isAmoled,
    )
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CLOUDFLARE to CloudflareColorScheme,
    AppTheme.COTTONCANDY to CottoncandyColorScheme,
    AppTheme.DOOM to DoomColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MATRIX to MatrixColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MOCHA to MochaColorScheme,
    AppTheme.SAPPHIRE to SapphireColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)
