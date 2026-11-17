package app.soundlife.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

private val darkScheme = darkColorScheme(
    primary = Acc, onPrimary = Bg, background = Bg, surface = Bg,
    surfaceVariant = Panel, onBackground = Color.White, onSurface = Color.White,
    secondary = Acc, onSecondary = Bg, outline = PanelHi,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkScheme) { AdminApp() } }
    }
}
