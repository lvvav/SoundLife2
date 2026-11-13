package app.soundlife

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Обновление прямо из приложения: версия берётся из последней сборки на GitHub, APK ставит системный установщик. */
object Updater {
    private const val BASE = "https://github.com/lvvav/SoundLife2/releases/download/apk-latest/"
    private val http = OkHttpClient()

    class Info(val code: Int, val name: String)

    /** Новая версия или null, если уже последняя. */
    suspend fun check(): Info? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(BASE + "version.json").build()).execute().use { r ->
            if (!r.isSuccessful) error("сервер ответил ${r.code}")
            val o = JSONObject(r.body!!.string())
            val code = o.getInt("versionCode")
            if (code > BuildConfig.VERSION_CODE) Info(code, o.optString("versionName")) else null
        }
    }

    suspend fun download(ctx: Context): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val f = File(dir, "update.apk")
        http.newCall(Request.Builder().url(BASE + "app-debug.apk").build()).execute().use { r ->
            if (!r.isSuccessful) error("сервер ответил ${r.code}")
            r.body!!.byteStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        f
    }

    fun install(ctx: Context, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            // Разрешите установку из приложения SoundLife, потом нажмите «Обновить» ещё раз
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

object UpdateState {
    var available by mutableStateOf<Updater.Info?>(null)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
}

/** Проверка при запуске: молча, без ошибок в интерфейсе. */
@Composable
fun UpdateHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { UpdateState.available = Updater.check() } catch (_: Exception) {}
    }
    val info = UpdateState.available ?: return
    AlertDialog(
        onDismissRequest = { if (!UpdateState.busy) UpdateState.available = null },
        title = { Text("Доступна версия ${info.name}") },
        text = {
            Text(
                if (UpdateState.status.isNotEmpty()) UpdateState.status
                else "Скачать и установить обновление, не выходя из приложения?",
            )
        },
        confirmButton = {
            TextButton(enabled = !UpdateState.busy, onClick = {
                scope.launch {
                    UpdateState.busy = true
                    UpdateState.status = "Скачиваю…"
                    try {
                        val f = Updater.download(ctx)
                        UpdateState.available = null
                        Updater.install(ctx, f)
                    } catch (e: Exception) {
                        UpdateState.status = "Не получилось: ${e.message}"
                    }
                    UpdateState.busy = false
                    UpdateState.status = ""
                }
            }) { Text("Обновить", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { UpdateState.available = null }) { Text("Позже", color = Mut) } },
    )
}

/** Ручная проверка из настроек. */
suspend fun checkForUpdateNow(ctx: Context) {
    try {
        val i = Updater.check()
        if (i == null) Toast.makeText(ctx, "У вас последняя версия", Toast.LENGTH_SHORT).show()
        else UpdateState.available = i
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось проверить: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
