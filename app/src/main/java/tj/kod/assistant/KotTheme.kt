package tj.kod.assistant

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KotDarkColors = darkColorScheme(
    primary = Color(0xFF39F58A),
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF0D2A1A),
    onPrimaryContainer = Color(0xFFC0FFD8),
    secondary = Color(0xFF4E8CFF),
    onSecondary = Color(0xFF07172F),
    secondaryContainer = Color(0xFF102441),
    onSecondaryContainer = Color(0xFFD7E5FF),
    background = Color(0xFF080B0D),
    onBackground = Color(0xFFF2F6F4),
    surface = Color(0xFF0D1114),
    onSurface = Color(0xFFF2F6F4),
    surfaceVariant = Color(0xFF141A1E),
    onSurfaceVariant = Color(0xFFB8C2BD),
    outline = Color(0xFF34413B),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
)

private val KotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
