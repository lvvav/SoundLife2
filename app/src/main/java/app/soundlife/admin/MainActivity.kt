package app.soundlife.admin

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Acc = Color(0xFFC6FF3D)
private val Bg = Color(0xFF0D0D11)
private val Panel = Color(0xFF17171E)
private val Mut = Color(0xFF9A9AA6)
private val AUDIO = Regex("\\.(mp3|flac|wav|m4a|ogg)$", RegexOption.IGNORE_CASE)

private val darkScheme = darkColorScheme(
    primary = Acc, onPrimary = Color(0xFF0D0D11), background = Bg, surface = Bg,
    surfaceVariant = Panel, onBackground = Color.White, onSurface = Color.White,
)

/** Настройки входа: токен и хранилище в SharedPreferences этого приложения. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("admin", Context.MODE_PRIVATE)
    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) { p.edit().putString("token", v).apply() }
    var repo: String
        get() = p.getString("repo", BuildConfig.GH_REPO) ?: BuildConfig.GH_REPO
        set(v) { p.edit().putString("repo", v).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkScheme) { AdminApp() } }
    }
}

@Composable
fun AdminApp() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var token by remember { mutableStateOf(prefs.token) }
    var repo by remember { mutableStateOf(prefs.repo) }
    var tab by remember { mutableIntStateOf(0) }

    if (token.isEmpty()) {
        LoginScreen { t, r ->
            prefs.token = t; prefs.repo = r
            token = t; repo = r
        }
        return
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Треки" to 0, "Загрузка" to 1, "Настройки" to 2).forEach { (label, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(if (i == 0) "♪" else if (i == 1) "⬆" else "⚙", fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Acc.copy(alpha = 0.2f), selectedIconColor = Acc, selectedTextColor = Acc),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TracksScreen(token, repo)
                1 -> UploadScreen(token, repo)
                else -> SettingsScreen(token, repo) {
                    prefs.token = ""
                    token = ""
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onDone: (token: String, repo: String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf(BuildConfig.GH_REPO) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Админка SoundLife", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Acc)
        Spacer(Modifier.height(6.dp))
        Text("Вход по токену GitHub с правом «Contents: Read and write».", color = Mut, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(repo, { repo = it.trim() }, label = { Text("Хранилище (владелец/репозиторий)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(token, { token = it.trim() }, label = { Text("Токен GitHub") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp)
        Button(
            onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { Gh.tree(token, repo, BuildConfig.GH_BRANCH) }
                        onDone(token, repo)
                    } catch (e: Exception) {
                        error = e.message ?: "Не удалось войти"
                    } finally { busy = false }
                }
            },
            enabled = token.isNotEmpty() && repo.contains('/') && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Color(0xFF0D0D11)),
        ) { Text(if (busy) "Проверяю…" else "Войти", fontWeight = FontWeight.Bold) }
    }
}

/** Треки: список аудиофайлов папки music, сгруппирован по жанру (папке). */
@Composable
fun TracksScreen(token: String, repo: String) {
    var items by remember { mutableStateOf<List<Node>?>(null) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        error = ""
        try {
            items = withContext(Dispatchers.IO) {
                Gh.tree(token, repo, BuildConfig.GH_BRANCH)
                    .filter { it.path.startsWith(BuildConfig.GH_FOLDER + "/") && AUDIO.containsMatchIn(it.path) }
            }
        } catch (e: Exception) { error = e.message ?: "Ошибка загрузки" }
    }
    val shown = items.orEmpty().filter { it.path.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Треки", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск по названию или папке") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B))
            TextButton(onClick = { reload++ }) { Text("Повторить") }
        }
        Text("Всего: ${items?.size ?: 0}", color = Mut, fontSize = 14.sp)
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.path }) { n ->
                val parts = n.path.split('/')
                val genre = if (parts.size > 2) parts[1] else "Без жанра"
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(parts.last().substringBeforeLast('.'), fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("$genre · ${n.size / 1024} КБ", color = Mut, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Загрузка: выбираем файлы с телефона, задаём жанр, отправляем одним коммитом. */
@Composable
fun UploadScreen(token: String, repo: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var genre by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked = it }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Загрузка", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Button(
            onClick = { picker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
        ) { Text("Выбрать файлы (${picked.size})") }
        OutlinedTextField(genre, { genre = it.trim() }, label = { Text("Жанр (папка, можно пусто)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            if (picked.isEmpty()) "Файлы не выбраны" else picked.joinToString("\n") { it.lastPathSegment?.substringAfterLast('/') ?: "файл" },
            color = Mut, fontSize = 14.sp,
        )
        if (status.isNotEmpty()) Text(status, color = Acc)
        Button(
            onClick = {
                busy = true; status = "Загружаю…"
                scope.launch {
                    try {
                        val files = withContext(Dispatchers.IO) {
                            picked.associate { uri ->
                                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "track"
                                val path = listOfNotNull(BuildConfig.GH_FOLDER, genre.ifBlank { null }, name).joinToString("/")
                                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать $name")
                                path to Gh.blob(token, repo, bytes)
                            }
                        }
                        withContext(Dispatchers.IO) { Gh.commit(token, repo, BuildConfig.GH_BRANCH, files, "Загрузка треков: ${files.size}") }
                        status = "Готово: загружено ${files.size}"
                        picked = emptyList()
                    } catch (e: Exception) {
                        status = e.message ?: "Ошибка загрузки"
                    } finally { busy = false }
                }
            },
            enabled = picked.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Color(0xFF0D0D11)),
        ) { Text(if (busy) "Загружаю…" else "Загрузить на сайт", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SettingsScreen(token: String, repo: String, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Хранилище: $repo", color = Mut)
        Text("Ветка: ${BuildConfig.GH_BRANCH}", color = Mut)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color(0xFFFF6B6B)),
        ) { Text("Выйти и удалить токен") }
    }
}
