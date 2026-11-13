package app.soundlife

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Главная — сам сайт SoundLife: тот же код, те же треки, обложки и правки. */
private const val SITE_URL = "https://lvvav.github.io/wavelab/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(SITE_URL)
            }
        },
    )
}
