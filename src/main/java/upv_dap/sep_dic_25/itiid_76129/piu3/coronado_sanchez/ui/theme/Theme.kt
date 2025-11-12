package upv_dap.sep_dic_25.itiid_76129.piu3.coronado_sanchez.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF03DAC5),
    tertiary = Color(0xFFFF9800),
    error = Color(0xFFCF6679),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF03DAC5),
    tertiary = Color(0xFFFF9800),
    error = Color(0xFFB00020),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}