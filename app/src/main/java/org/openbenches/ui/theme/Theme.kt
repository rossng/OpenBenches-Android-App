package org.openbenches.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// The website has a single light look, so the app does too rather than following dynamic colour.
private val OpenBenchesColors = lightColorScheme(
    primary = PlaqueGold,
    onPrimary = SoftBlack,
    primaryContainer = PlaqueGold,
    onPrimaryContainer = SoftBlack,
    secondary = SkyBlue,
    onSecondary = SoftBlack,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = SoftBlack,
    tertiary = GrassGreen,
    onTertiary = SoftWhite,
    background = SoftWhite,
    onBackground = SoftBlack,
    surface = SoftWhite,
    onSurface = SoftBlack,
    surfaceVariant = Color(0xFFE2E6E8),
    onSurfaceVariant = Color(0xFF3E4448),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SoftWhite,
    surfaceContainer = SoftWhite,
    surfaceContainerHigh = SoftWhite,
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = BorderBlack,
    outlineVariant = Color(0x33191E20),
    error = Color(0xFFB3261E),
)

private val OpenBenchesShapes = Shapes(
    small = RoundedCornerShape(4.dp),   // .button: border-radius .2em
    medium = RoundedCornerShape(16.dp), // --radius: 1rem
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun OpenBenchesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenBenchesColors,
        typography = Typography,
        shapes = OpenBenchesShapes,
        content = content,
    )
}
