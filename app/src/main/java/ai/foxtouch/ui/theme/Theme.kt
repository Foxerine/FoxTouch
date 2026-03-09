package ai.foxtouch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FoxOrange = Color(0xFFFF6D00)
private val FoxOrangeLight = Color(0xFFFFAB40)

private val DarkColorScheme = darkColorScheme(
    primary = FoxOrange,
    secondary = FoxOrangeLight,
    tertiary = Color(0xFFFFD180),
)

private val LightColorScheme = lightColorScheme(
    primary = FoxOrange,
    secondary = FoxOrangeLight,
    tertiary = Color(0xFFFF9100),
)

@Composable
fun FoxTouchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
