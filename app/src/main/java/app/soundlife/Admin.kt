package app.soundlife

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Админ-панель: тот же сервер, что и на сайте (функция admin-album). Вход: почта/ник, пароль и секретное слово. */
object AdminApi {
    private val http = OkHttpClient()
    private var token = ""
    val loggedIn: Boolean get() = token.isNotEmpty()

    private fun call(body: JSONObject, authed: Boolean): JSONObject {
        val req = Request.Builder()
            .url(BuildConfig.SUPA_URL.trimEnd('/') + "/functions/v1/admin-album")
            .header("apikey", BuildConfig.SUPA_KEY)
            .header("authorization", "Bearer " + BuildConfig.SUPA_KEY)
            .apply { if (authed) header("x-admin-token", token) }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val j = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
            if (!r.isSuccessful) error(
                when (j.optString("error")) {
                    "denied" -> "Неверная почта, пароль или секретное слово"
                    "auth" -> "Войдите заново"
                    else -> j.optString("error").ifBlank { "ошибка ${r.code}" }
                },
            )
            return j
        }
    }

    fun login(user: String, pass: String, word: String) {
        val j = call(JSONObject().put("action", "login").put("user", user).put("pass", pass).put("word", word), false)
        token = j.getString("token")
    }

    fun saveTrack(path: String, d: Map<String, String>) {
        val data = JSONObject()
        d.forEach { (k, v) -> if (v.isNotBlank()) data.put(k, v.trim()) }
        call(
            JSONObject().put("action", "save").put(
                "items",
                JSONArray().put(JSONObject().put("key", path).put("d", data)),
            ),
            true,
        )
    }

    fun saveCover(albumKey: String, jpegBase64: String) {
        call(JSONObject().put("action", "cover").put("k", albumKey).put("img", "data:image/jpeg;base64,$jpegBase64"), true)
    }
}

@Composable
fun AdminDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(AdminApi.loggedIn) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(loggedIn) {
        if (loggedIn && tracks.isEmpty()) {
            tracks = withContext(Dispatchers.IO) { runCatching { Library.load(ctx) }.getOrDefault(emptyList()) }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Админ-панель", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Закрыть", color = Mut) }
            }
            if (!loggedIn) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text("Почта или ник") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pass, onValueChange = { pass = it }, singleLine = true, label = { Text("Пароль") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = word, onValueChange = { word = it }, singleLine = true, label = { Text("Секретное слово") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !busy && user.isNotBlank() && pass.isNotBlank() && word.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true; msg = ""
                            try {
                                withContext(Dispatchers.IO) { AdminApi.login(user.trim(), pass, word) }
                                loggedIn = true
                            } catch (e: Exception) {
                                msg = e.message ?: "Не получилось войти"
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = OnAcc),
                ) { Text(if (busy) "Вхожу…" else "Войти") }
                if (msg.isNotEmpty()) Text(msg, color = Danger, fontSize = 13.sp)
            } else {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Поиск трека") }, modifier = Modifier.fillMaxWidth())
                if (msg.isNotEmpty()) Text(msg, color = Mut, fontSize = 13.sp)
                val shown = remember(tracks, query) {
                    val q = query.trim().lowercase()
                    tracks.filter { q.isEmpty() || "${it.artist} ${it.title} ${it.album}".lowercase().contains(q) }.take(300)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.path }) { t ->
                        Column(Modifier.fillMaxWidth().clickable { selected = t; msg = "" }.padding(vertical = 10.dp)) {
                            Text(t.title, color = TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text("${t.artist} · ${t.album}", color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    selected?.let { t ->
        EditTrackDialog(
            track = t,
            onClose = { selected = null },
            onSaved = { m -> msg = m },
        )
    }
}

@Composable
private fun EditTrackDialog(track: Track, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember(track.path) { mutableStateOf(track.title) }
    var a by remember(track.path) { mutableStateOf(track.artist) }
    var al by remember(track.path) { mutableStateOf(track.album) }
    var aa by remember(track.path) { mutableStateOf(track.albumArtist) }
    var y by remember(track.path) { mutableStateOf(track.year) }
    var n by remember(track.path) { mutableStateOf(if (track.num > 0) track.num.toString() else "") }
    var d by remember(track.path) { mutableStateOf(if (track.disc > 0) track.disc.toString() else "") }
    var busy by remember { mutableStateOf(false) }
    val albumKey = Overrides.albumKey(aa.ifBlank { a }, al)

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                    val scaled = if (maxOf(bmp.width, bmp.height) > 800) {
                        val k = 800f / maxOf(bmp.width, bmp.height)
                        Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
                    } else bmp
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    AdminApi.saveCover(albumKey, android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP))
                }
                onSaved("Обложка альбома сохранена")
            } catch (e: Exception) {
                onSaved(e.message ?: "Не получилось сохранить обложку")
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Трек") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true, label = { Text("Название") })
                OutlinedTextField(value = a, onValueChange = { a = it }, singleLine = true, label = { Text("Артист") })
                OutlinedTextField(value = al, onValueChange = { al = it }, singleLine = true, label = { Text("Альбом") })
                OutlinedTextField(value = aa, onValueChange = { aa = it }, singleLine = true, label = { Text("Артист альбома") })
                OutlinedTextField(value = y, onValueChange = { y = it }, singleLine = true, label = { Text("Год") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = n, onValueChange = { n = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Номер") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = d, onValueChange = { d = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Диск") }, modifier = Modifier.weight(1f))
                }
                TextButton(enabled = !busy, onClick = { pickCover.launch("image/*") }) { Text("Обложка альбома…", color = Acc) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) {
                            AdminApi.saveTrack(track.path, mapOf(
                                "t" to t, "a" to a, "al" to al, "aa" to aa, "y" to y, "n" to n, "d" to d,
                            ))
                        }
                        onSaved("Сохранено. Обновите медиатеку, чтобы увидеть изменения")
                        onClose()
                    } catch (e: Exception) {
                        onSaved(e.message ?: "Не получилось сохранить")
                    }
                    busy = false
                }
            }) { Text("Сохранить", color = Acc) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена", color = Mut) } },
    )
}
