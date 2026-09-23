package tj.kod.assistant

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val KotNeonGreen = Color(0xFF35F39A)
val KotNeonCyan = Color(0xFF27DFFF)
val KotNeonBlue = Color(0xFF557CFF)
val KotInk = Color(0xFF05080D)
val KotPanel = Color(0xFF0B1118)
val KotPanelRaised = Color(0xFF101823)
val KotMuted = Color(0xFF8FA2B5)

private val KotDarkColors = darkColorScheme(
    primary = KotNeonGreen,
    onPrimary = Color(0xFF002817),
    primaryContainer = Color(0xFF0A2A1D),
    onPrimaryContainer = Color(0xFFD1FFE5),
    secondary = KotNeonCyan,
    onSecondary = Color(0xFF001F27),
    secondaryContainer = Color(0xFF08232A),
    onSecondaryContainer = Color(0xFFD0F8FF),
    tertiary = KotNeonBlue,
    onTertiary = Color.White,
    background = KotInk,
    onBackground = Color(0xFFF4F8FC),
    surface = KotPanel,
    onSurface = Color(0xFFF4F8FC),
    surfaceVariant = KotPanelRaised,
    onSurfaceVariant = Color(0xFFB5C3D0),
    outline = Color(0xFF243240),
    outlineVariant = Color(0xFF17222D),
    error = Color(0xFFFF6B78),
    onError = Color(0xFF330008),
)

private val KotShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KotTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KotDarkColors,
        typography = Typography(),
        shapes = KotShapes,
        content = content,
    )
}
