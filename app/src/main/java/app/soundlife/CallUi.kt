package app.soundlife

import android.Manifest
import android.annotation.SuppressLint
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

/** Экран звонка поверх всего приложения. Показывается, пока Calls.st не IDLE. */
@Composable
fun CallOverlay() {
    val st = Calls.st
    if (st == Calls.St.IDLE) return
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(st) {
        if (st == Calls.St.RING || st == Calls.St.IN) {
            perm.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D14))) {
        if (st == Calls.St.LIVE) {
            key(Calls.room) { VdoView(Calls.room, Calls.pw, Calls.video) }
            Button(
                onClick = { Calls.hang() },
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) { Text("Завершить") }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (st == Calls.St.IN) "Входящий звонок" else "Вызов…", color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text(Calls.peerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(56.dp))
                if (st == Calls.St.IN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        Button(
                            onClick = { Calls.decline() },
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        ) { Text("Отклонить") }
                        Button(
                            onClick = { Calls.answer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EB86B)),
                        ) { Text("Ответить") }
                    }
                } else {
                    Button(
                        onClick = { Calls.hang() },
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) { Text("Отменить") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VdoView(room: String, pw: String, video: Boolean) {
    val ctx = LocalContext.current
    val url = "https://vdo.ninja/?room=" + URLEncoder.encode(room, "UTF-8") +
        "&password=" + URLEncoder.encode(pw, "UTF-8") +
        "&webcam&autostart&cleanish&label=" + URLEncoder.encode(Profile.nick, "UTF-8") +
        (if (video) "" else "&videodevice=0")
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(request.resources)
                    }
                }
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
    )
}
