package com.novelreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

data class ReaderSurface(
    val bg: String,
    val text: String,
    val accent: String,
    val link: String
)

private const val ContainerTint = 0.16f

private val LightError = Color(0xFFBA1A1A)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val DarkError = Color(0xFFFFB4AB)
private val DarkErrorContainer = Color(0xFF93000A)

private val TintedOn = Color(0xFF1A1A1A)

fun contrastOn(color: Color): Color = when {
    contrastRatio(color, TintedOn) >= MinContrast -> TintedOn
    contrastRatio(color, Color.White) >= MinContrast -> Color.White
    else -> Color.Black
}

fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

const val MinContrast = 4.5f

fun parseAccentHex(value: String?): Color? {
    val trimmed = value?.trim() ?: return null
    if (!trimmed.matches(Regex("^#[0-9a-fA-F]{6}$"))) return null
    val rgb = trimmed.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

fun hexString(color: Color): String {
    val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(r, g, b)
}

data class Hsv(val hue: Float, val saturation: Float, val value: Float)

fun hsvOf(color: Color): Hsv {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        max == color.green -> 60f * (((color.blue - color.red) / delta) + 2f)
        else -> 60f * (((color.red - color.green) / delta) + 4f)
    }
    return Hsv(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = if (max == 0f) 0f else delta / max,
        value = max
    )
}

private const val AccentTargetContrast = 5f

private fun maxAchievableContrast(background: Color): Float =
    maxOf(contrastRatio(Color.Black, background), contrastRatio(Color.White, background))

fun accentHexFor(hue: Float, saturationPercent: Int, background: Color): String {
    val saturation = (saturationPercent / 100f).coerceIn(0f, 1f)
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val backgroundIsDark = background.luminance() < 0.4f
    val target = minOf(AccentTargetContrast, maxAchievableContrast(background))
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        val enough = contrastRatio(Color.hsl(normalizedHue, saturation, mid), background) >= target
        if (backgroundIsDark) {
            if (enough) high = mid else low = mid
        } else {
            if (enough) low = mid else high = mid
        }
    }
    return hexString(Color.hsl(normalizedHue, saturation, if (backgroundIsDark) high else low))
}

private fun containerContent(container: Color, preferred: Color): Color =
    if (contrastRatio(container, preferred) >= MinContrast) preferred else contrastOn(container)

fun ColorScheme.withAccent(accentHex: String?): ColorScheme {
    val accent = parseAccentHex(accentHex) ?: return this
    val container = lerp(background, accent, ContainerTint)
    return copy(
        primary = accent,
        onPrimary = contrastOn(accent),
        primaryContainer = container,
        onPrimaryContainer = containerContent(container, accent)
    )
}

private fun appLightScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onBackground: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color
) = lightColorScheme(
    primary = primary,
    onPrimary = contrastOn(primary),
    primaryContainer = lerp(background, primary, ContainerTint),
    onPrimaryContainer = containerContent(lerp(background, primary, ContainerTint), primary),
    secondary = secondary,
    onSecondary = contrastOn(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant,
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = onBackground.copy(alpha = 0.7f),
    error = LightError,
    errorContainer = LightErrorContainer
)

private fun appDarkScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onBackground: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color
) = darkColorScheme(
    primary = primary,
    onPrimary = contrastOn(primary),
    primaryContainer = lerp(surface, primary, ContainerTint),
    onPrimaryContainer = containerContent(lerp(surface, primary, ContainerTint), primary),
    secondary = secondary,
    onSecondary = contrastOn(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant,
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = onBackground.copy(alpha = 0.7f),
    error = DarkError,
    errorContainer = DarkErrorContainer
)

enum class AppPalette(
    val id: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val readerLight: ReaderSurface,
    val readerDark: ReaderSurface
) {
    INDIGO(
        id = "indigo",
        light = appLightScheme(
            primary = Color(0xFF1A237E),
            secondary = Color(0xFFFF6F00),
            background = Color(0xFFF5F0E8),
            surface = Color(0xFFFFF8F0),
            surfaceVariant = Color(0xFFE8E0D0),
            onBackground = Color(0xFF333333),
            secondaryContainer = Color(0xFFFFE0B2),
            onSecondaryContainer = Color(0xFF3E2723)
        ),
        dark = appDarkScheme(
            primary = Color(0xFF90CAF9),
            secondary = Color(0xFFFFAB40),
            background = Color(0xFF1A1A2E),
            surface = Color(0xFF16213E),
            surfaceVariant = Color(0xFF16213E),
            onBackground = Color(0xFFE0E0E0),
            secondaryContainer = Color(0xFF3D1C00),
            onSecondaryContainer = Color(0xFFFFAB40)
        ),
        readerLight = ReaderSurface("#f5f0e8", "#333333", "#1a237e", "#1565c0"),
        readerDark = ReaderSurface("#0a0a0f", "#e0e0e0", "#90caf9", "#64b5f6")
    ),
    PAPEL(
        id = "papel",
        light = appLightScheme(
            primary = Color(0xFF6D4C41),
            secondary = Color(0xFF8D6E63),
            background = Color(0xFFF4E4C1),
            surface = Color(0xFFFBF0DA),
            surfaceVariant = Color(0xFFE8D5AC),
            onBackground = Color(0xFF4E3B2E),
            secondaryContainer = Color(0xFFEADBC0),
            onSecondaryContainer = Color(0xFF3E2C22)
        ),
        dark = appDarkScheme(
            primary = Color(0xFFD7B899),
            secondary = Color(0xFFC9A27E),
            background = Color(0xFF241E17),
            surface = Color(0xFF2E271E),
            surfaceVariant = Color(0xFF3A3126),
            onBackground = Color(0xFFE8DCC8),
            secondaryContainer = Color(0xFF3A3126),
            onSecondaryContainer = Color(0xFFD7B899)
        ),
        readerLight = ReaderSurface("#f4e4c1", "#5b4636", "#8d6e63", "#6d4c41"),
        readerDark = ReaderSurface("#241e17", "#e0d3bd", "#d7b899", "#d7b899")
    ),
    GRAFITE(
        id = "grafite",
        light = appLightScheme(
            primary = Color(0xFF37474F),
            secondary = Color(0xFF546E7A),
            background = Color(0xFFECECEC),
            surface = Color(0xFFF7F7F7),
            surfaceVariant = Color(0xFFDCDCDC),
            onBackground = Color(0xFF2B2B2B),
            secondaryContainer = Color(0xFFD6E1E6),
            onSecondaryContainer = Color(0xFF263238)
        ),
        dark = appDarkScheme(
            primary = Color(0xFF90A4AE),
            secondary = Color(0xFFB0BEC5),
            background = Color(0xFF141414),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF2A2A2A),
            onBackground = Color(0xFFE0E0E0),
            secondaryContainer = Color(0xFF263238),
            onSecondaryContainer = Color(0xFF90A4AE)
        ),
        readerLight = ReaderSurface("#ececec", "#2b2b2b", "#37474f", "#1565c0"),
        readerDark = ReaderSurface("#2d2d2d", "#d0d0d0", "#90a4ae", "#81d4fa")
    ),
    FLORESTA(
        id = "floresta",
        light = appLightScheme(
            primary = Color(0xFF2E5D3A),
            secondary = Color(0xFF4C7A34),
            background = Color(0xFFEAF3EA),
            surface = Color(0xFFF4FAF4),
            surfaceVariant = Color(0xFFD3E5D5),
            onBackground = Color(0xFF24332A),
            secondaryContainer = Color(0xFFD8E8CE),
            onSecondaryContainer = Color(0xFF24332A)
        ),
        dark = appDarkScheme(
            primary = Color(0xFF8FD3A0),
            secondary = Color(0xFFA8D48B),
            background = Color(0xFF101A14),
            surface = Color(0xFF16241C),
            surfaceVariant = Color(0xFF1F3126),
            onBackground = Color(0xFFDCEBE0),
            secondaryContainer = Color(0xFF1F3126),
            onSecondaryContainer = Color(0xFF8FD3A0)
        ),
        readerLight = ReaderSurface("#eaf3ea", "#24332a", "#2e5d3a", "#1b5e20"),
        readerDark = ReaderSurface("#101a14", "#cfe3d4", "#8fd3a0", "#a8d48b")
    ),
    AMEIXA(
        id = "ameixa",
        light = appLightScheme(
            primary = Color(0xFF6A1B5A),
            secondary = Color(0xFFA6438A),
            background = Color(0xFFF7ECF3),
            surface = Color(0xFFFDF5FA),
            surfaceVariant = Color(0xFFEBD3E3),
            onBackground = Color(0xFF3A2233),
            secondaryContainer = Color(0xFFF0D6E4),
            onSecondaryContainer = Color(0xFF4A2140)
        ),
        dark = appDarkScheme(
            primary = Color(0xFFCF9FE0),
            secondary = Color(0xFFE0B0C8),
            background = Color(0xFF1B1020),
            surface = Color(0xFF241629),
            surfaceVariant = Color(0xFF2F1D36),
            onBackground = Color(0xFFEADCEF),
            secondaryContainer = Color(0xFF2F1D36),
            onSecondaryContainer = Color(0xFFCF9FE0)
        ),
        readerLight = ReaderSurface("#f7ecf3", "#3a2233", "#6a1b5a", "#8e24aa"),
        readerDark = ReaderSurface("#1b1020", "#e5d6ec", "#cf9fe0", "#e0b0c8")
    ),
    AMOLED(
        id = "amoled",
        light = appDarkScheme(
            primary = Color(0xFFE8E8E8),
            secondary = Color(0xFFBDBDBD),
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF000000),
            onBackground = Color(0xFFE8E8E8),
            secondaryContainer = Color(0xFF1A1A1A),
            onSecondaryContainer = Color(0xFFE8E8E8)
        ),
        dark = appDarkScheme(
            primary = Color(0xFFE8E8E8),
            secondary = Color(0xFFBDBDBD),
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF000000),
            onBackground = Color(0xFFE8E8E8),
            secondaryContainer = Color(0xFF1A1A1A),
            onSecondaryContainer = Color(0xFFE8E8E8)
        ),
        readerLight = ReaderSurface("#000000", "#e8e8e8", "#e8e8e8", "#bdbdbd"),
        readerDark = ReaderSurface("#000000", "#e8e8e8", "#e8e8e8", "#bdbdbd")
    );

    fun readerSurface(dark: Boolean): ReaderSurface = if (dark) readerDark else readerLight

    companion object {
        val DEFAULT = INDIGO
        private val byId = entries.associateBy { it.id }

        fun fromId(value: String?): AppPalette =
            byId[value?.trim()?.lowercase()] ?: DEFAULT
    }
}
