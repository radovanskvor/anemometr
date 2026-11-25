package cz.skvor.windmeter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.FlowStatus

/** Colors with a meaning in ventilation work, kept apart from the Material roles. */
@Immutable
data class AppColors(
    val supply: Color,
    val exhaust: Color,
    val ok: Color,
    val warning: Color,
    val bad: Color,
    val muted: Color,
    val cardBorder: Color,
) {
    fun of(direction: AirDirection) = if (direction == AirDirection.Supply) supply else exhaust

    fun of(status: FlowStatus) = when (status) {
        FlowStatus.NotMeasured -> muted
        FlowStatus.Ok -> ok
        FlowStatus.Warning -> warning
        FlowStatus.Bad -> bad
    }
}

// Supply red and exhaust blue follow the colors of the NIBE ventilation drawings.
private val DarkAppColors = AppColors(
    supply = Color(0xFFFF7A66),
    exhaust = Color(0xFF5AA9FF),
    ok = Color(0xFF3DDC97),
    warning = Color(0xFFFFC04D),
    bad = Color(0xFFFF5C72),
    muted = Color(0xFF6B7A90),
    cardBorder = Color(0xFF223047),
)

private val LightAppColors = AppColors(
    supply = Color(0xFFD9442E),
    exhaust = Color(0xFF1F6FD1),
    ok = Color(0xFF14935E),
    warning = Color(0xFFC77A00),
    bad = Color(0xFFD3243F),
    muted = Color(0xFF8592A6),
    cardBorder = Color(0xFFDDE4EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FE0D0),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0F3B3A),
    onPrimaryContainer = Color(0xFFB6F5EC),
    secondary = Color(0xFF9FB3CC),
    onSecondary = Color(0xFF0B1422),
    secondaryContainer = Color(0xFF1B2738),
    onSecondaryContainer = Color(0xFFD6E2F2),
    tertiary = Color(0xFFFFC04D),
    background = Color(0xFF0A111C),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0A111C),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF16202E),
    onSurfaceVariant = Color(0xFF93A3B8),
    surfaceContainerLowest = Color(0xFF070C14),
    surfaceContainerLow = Color(0xFF0E1622),
    surfaceContainer = Color(0xFF121B28),
    surfaceContainerHigh = Color(0xFF172231),
    surfaceContainerHighest = Color(0xFF1D2A3B),
    outline = Color(0xFF3A4A60),
    outlineVariant = Color(0xFF223047),
    error = Color(0xFFFF5C72),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2EC),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF52627A),
    secondaryContainer = Color(0xFFE3E9F2),
    onSecondaryContainer = Color(0xFF162131),
    tertiary = Color(0xFFC77A00),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF111A27),
    surface = Color(0xFFF4F7FB),
    onSurface = Color(0xFF111A27),
    surfaceVariant = Color(0xFFE6ECF4),
    onSurfaceVariant = Color(0xFF5B6B80),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F8),
    surfaceContainerHighest = Color(0xFFE6ECF4),
    outline = Color(0xFFB3BFCE),
    outlineVariant = Color(0xFFDDE4EE),
    error = Color(0xFFD3243F),
)

private val tabular = "tnum"

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 76.sp, letterSpacing = (-2).sp, fontFeatureSettings = tabular),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, fontSize = 52.sp, letterSpacing = (-1).sp, fontFeatureSettings = tabular),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = tabular),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = tabular),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tabular),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

/** Large numeric readouts; tabular figures stop the digits from jumping as values change. */
val NumberStyle = TextStyle(fontFeatureSettings = tabular)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

@Composable
fun WindMeterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}
