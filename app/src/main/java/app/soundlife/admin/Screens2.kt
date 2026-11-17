package app.soundlife.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «Хотят послушать»: заявки, решения «принять» и «отклонить» сохраняются одним коммитом. */
@Composable
fun WishesScreen(s: Session, lib: Library, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var wishes by remember { mutableStateOf<List<Wish>?>(null) }
    var error by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("new") }
    var decisions by remember { mutableStateOf(mapOf<String, String?>()) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = ""
        try {
            wishes = withContext(Dispatchers.IO) { Wishes.load() }
        } catch (e: Exception) { error = e.message ?: "Ошибка загрузки" }
    }

    val onSiteKeys = remember(lib) {
        lib.tracks.map { (it.artist + " " + it.title).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }.toHashSet()
    }
    fun status(w: Wish): String {
        if (w.key in onSiteKeys) return "site"
        val saved = (lib.meta.optJSONObject("@wishes")?.optJSONObject(w.key))?.optString("s")
        val s2 = if (decisions.containsKey(w.key)) decisions[w.key] else saved
        return when (s2) { "ok" -> "ok"; "no" -> "no"; else -> "new" }
    }
    val list = wishes.orEmpty().filter { w ->
        filter == "all" || status(w) == filter
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Title("Хотят послушать", "Новых: ${wishes.orEmpty().count { status(it) == "new" }}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("new" to "Новые", "ok" to "Принятые", "no" to "Отклонённые", "site" to "На сайте", "all" to "Все").forEach { (k, label) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (error.isNotEmpty()) {
            Text(error, color = Bad)
            TextButton(onClick = { reload++ }) { Text("Повторить") }
        }
        if (wishes == null && error.isEmpty()) Empty("Загружаю…")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.key }) { w ->
                val st = status(w)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${w.n}", color = Acc, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.title.ifBlank { "Без названия" }, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(listOf(w.artist, w.album).filter { it.isNotBlank() }.joinToString(" · "), color = Mut, fontSize = 13.sp, maxLines = 1)
                    }
                    if (st == "site") {
                        Text("на сайте", color = Mut, fontSize = 12.sp)
                    } else {
                        IconButton(onClick = { decisions = decisions + (w.key to "ok") }) {
                            Icon(Icons.Default.Check, null, tint = if (st == "ok") Acc else Mut)
                        }
                        IconButton(onClick = { decisions = decisions + (w.key to "no") }) {
                            Icon(Icons.Default.Close, null, tint = if (st == "no") Bad else Mut)
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val msg = withContext(Dispatchers.IO) { Save.wishes(s, lib, decisions) }
                        decisions = emptyMap()
                        onMessage(msg)
                    } catch (e: Exception) { onMessage(e.message ?: "Не удалось сохранить") }
                    finally { busy = false }
                }
            },
            enabled = decisions.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
        ) { Text(if (busy) "Сохраняю…" else "Сохранить решения (${decisions.size})", fontWeight = FontWeight.Bold) }
    }
}

/** Загрузка: несколько файлов сразу, одна папка жанра, один коммит. */
@Composable
fun UploadScreen(s: Session, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var genre by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked = it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Загрузка", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Button(
            onClick = { picker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Выбрать файлы (${picked.size})") }
        OutlinedTextField(genre, { genre = it.trim() }, label = { Text("Жанр (папка, можно пусто)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            if (picked.isEmpty()) "Файлы не выбраны" else picked.joinToString("\n") { it.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "файл" },
            color = Mut, fontSize = 14.sp,
        )
        if (status.isNotEmpty()) Text(status, color = Acc)
        Button(
            onClick = {
                busy = true; status = "Загружаю…"
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            val files = picked.map { uri ->
                                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "track.mp3"
                                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать $name")
                                name to bytes
                            }
                            Save.upload(s, files, genre)
                        }
                        picked = emptyList()
                        status = ""
                        onDone("Загружено: $count")
                    } catch (e: Exception) {
                        status = e.message ?: "Ошибка загрузки"
                    } finally { busy = false }
                }
            },
            enabled = picked.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
        ) { Text(if (busy) "Загружаю…" else "Загрузить на сайт", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SettingsScreen(s: Session, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Updater.Latest?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Хранилище: ${s.repo}", color = Mut)
        Text("Ветка: ${s.branch}", color = Mut)
        Text("Папка с музыкой: ${s.folder}", color = Mut)
        Text("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT.take(7)})", color = Mut)

        Spacer(Modifier.height(8.dp))
        Text("Обновление", fontWeight = FontWeight.Bold)
        if (status.isNotEmpty()) Text(status, color = Acc)
        Button(
            onClick = {
                busy = true; status = "Проверяю…"; found = null; file = null
                scope.launch {
                    try {
                        val latest = withContext(Dispatchers.IO) { Updater.check() }
                        if (latest == null) status = "Установлена последняя версия."
                        else { found = latest; status = "Есть новая сборка. Скачать и установить?" }
                    } catch (e: Exception) { status = e.message ?: "Ошибка проверки" }
                    finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Acc),
        ) { Text(if (busy) "Работаю…" else "Проверить обновление", fontWeight = FontWeight.Bold) }
        val latest = found
        if (latest != null) {
            Button(
                onClick = {
                    busy = true; status = "Скачиваю… 0%"
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) {
                                Updater.download(ctx, latest) { pct -> scope.launch { status = "Скачиваю… $pct%" } }
                            }
                            file = f
                            status = "Скачано. Нажмите «Установить»."
                        } catch (e: Exception) { status = "Не удалось скачать: " + (e.message ?: e.javaClass.simpleName) }
                        finally { busy = false }
                    }
                },
                enabled = !busy && file == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
            ) { Text("Скачать обновление", fontWeight = FontWeight.Bold) }
        }
        val f = file
        if (f != null) {
            Button(
                onClick = {
                    try { Updater.install(ctx, f) } catch (e: Exception) { status = e.message ?: "Не удалось открыть установщик" }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
            ) { Text("Установить", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Bad),
        ) { Text("Выйти и удалить токен") }
    }
}

/** Полный плеер: обложка, название, перемотка, управление, лайк-заглушка не нужна в админке. */
@Composable
fun FullPlayer(s: Session, tick: Int, onClose: () -> Unit) {
    val t = Player.current ?: run { onClose(); return }
    var pos by remember { mutableIntStateOf(Player.position) }
    var dur by remember { mutableIntStateOf(Player.duration) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(t.path) {
        while (true) {
            if (!dragging) { pos = Player.position; dur = Player.duration }
            kotlinx.coroutines.delay(400)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Default.KeyboardArrowDown, null) }
        Cover(t.cover?.let { Raw.url(s.repo, s.branch, it) }, 260)
        Spacer(Modifier.height(20.dp))
        Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(t.artist, color = Mut, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = pos.toFloat(),
            onValueChange = { pos = it.toInt(); dragging = true },
            onValueChangeFinished = { Player.seek(pos); dragging = false },
            valueRange = 0f..(if (dur > 0) dur.toFloat() else 1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Mut, fontSize = 12.sp)
            Text(fmt(dur), color = Mut, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = { Player.seekBy(-10_000); pos = Player.position }) { Icon(Icons.Default.Replay10, null) }
            IconButton(onClick = { Player.prev() }) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(36.dp)) }
            FilledIconButton(
                onClick = { Player.toggle() },
                modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Acc, contentColor = Bg),
            ) { Icon(if (Player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { Player.next() }) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { Player.seekBy(10_000); pos = Player.position }) { Icon(Icons.Default.Forward10, null) }
        }
    }
}

private fun fmt(ms: Int): String {
    val sec = maxOf(0, ms) / 1000
    return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
}
