package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),
    CUSTOM(MR.strings.theme_custom),
    CLOUDFLARE(MR.strings.theme_cloudflare),
    COTTONCANDY(MR.strings.theme_cottoncandy),
    DOOM(MR.strings.theme_doom),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MATRIX(MR.strings.theme_matrix),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),
    MOCHA(MR.strings.theme_mocha),
    SAPPHIRE(MR.strings.theme_sapphire),
    NORD(MR.strings.theme_nord),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
