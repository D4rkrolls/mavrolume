package camera.mavrolume.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Mavrolume uses a dark camera interface.
 *
 * In Compose, a Theme is like a React context provider for design tokens.
 * MaterialTheme wraps your whole UI tree and provides colors, typography, shapes
 * to all child composables — exactly like ThemeProvider in styled-components
 * or MUI's ThemeProvider.
 */
private val MavrolumeColorScheme = darkColorScheme(
    primary = FilmAccent,
    onPrimary = FilmWhite,
    secondary = FilmAccentMuted,
    background = FilmBlack,
    surface = FilmDarkGray,
    surfaceVariant = FilmMediumGray,
    onBackground = FilmWhite,
    onSurface = FilmWhite,
    onSurfaceVariant = FilmLightGray,
)

@Composable
fun MavrolumeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MavrolumeColorScheme,
        typography = MavrolumeTypography,
        content = content,
    )
}
