package app.soundlife

import android.Manifest
import android.content.Intent
import android.widget.Toast
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тёмные системные панели независимо от темы телефона (иначе на светлой теме иконки статус-бара не видно)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Favorites.init(this); Subs.init(this); Playlists.init(this); Settings.init(this); Auth.init(this); Wishes.init(this); Profile.init(this); Follows.init(this); PubPlaylists.init(this); Sync.schedule(); Notifier.createChannel(this); Notifier.schedule(this); Calls.init(this); Jam.init(this); startCalls()
        handleLogin(intent)
        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { SoundLifeTheme { Box(Modifier.fillMaxSize()) { SoundLifeApp(rememberController()); CallOverlay() } } }
    }

    /** Фоновое соединение для входящих звонков: только когда вошли в аккаунт. */
    private fun startCalls() {
        if (Auth.userId() != null) startForegroundService(Intent(this, CallService::class.java))
    }

    // launchMode = singleTask: возврат из браузера приходит сюда же
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLogin(intent)
    }

    /** Возврат из Яндекса или Google: soundlife://auth#access_token=... */
    private fun handleLogin(i: Intent?) {
        val u = i?.data ?: return
        if (u.scheme != "soundlife" || u.host != "auth") return
        val frag = u.encodedFragment ?: return
        i?.data = null // чтобы не обрабатывать второй раз
        Thread {
            val msg = try {
                Auth.finishLogin(frag)
                runOnUiThread { startCalls() }
                "Вы вошли в аккаунт"
            } catch (e: Exception) {
                e.message ?: "Не удалось войти"
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }
}

@Composable
fun rememberController(): MediaController? {
    val ctx = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            controller = try { future.get() } catch (e: Exception) { null }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(future) }
    }
    return controller
}
