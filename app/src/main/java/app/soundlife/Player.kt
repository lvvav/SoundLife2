package app.soundlife

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.widget.Toast
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

/** Экран плеера в стиле Spotify: большая обложка, под ней название, ползунок и кнопки. */
@Composable
internal fun FullPlayer(ps: PlayerUi, c: MediaController, onClose: () -> Unit) {
    var showLy by remember { mutableStateOf(false) }
    // (загружено, результат): грузим при смене трека
    val lyrs by produceState<Pair<Boolean, Lyrics.Result?>>(false to null, ps.mediaId) {
        value = false to null
        val id = ps.mediaId ?: return@produceState
        val r = withContext(Dispatchers.IO) { Lyrics.get(id, ps.title, ps.artist, ps.duration / 1000) }
        value = true to r
    }
    // Популярность трека (Last.fm); грузим при смене трека
    val lfm by produceState<Lastfm.Info?>(null, ps.mediaId) {
        value = null
        val id = ps.mediaId ?: return@produceState
        value = withContext(Dispatchers.IO) { Lastfm.get(ps.artist, ps.title) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Свернуть", tint = TextC) }
                Text(
                    "Сейчас играет", color = Mut, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showLy = !showLy }) { Text(if (showLy) "Обложка" else "Текст", color = Acc) }
                IconButton(onClick = { ps.mediaId?.let { Playlists.pendingTrack = it } }, enabled = ps.mediaId != null) {
                    Icon(Icons.Filled.PlaylistAdd, "В плейлист", tint = TextC)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (showLy) {
                LyricsView(lyrs, ps.position, Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, 360.dp)
                    val breath = rememberInfiniteTransition(label = "breathe")
                    val breathScale by breath.animateFloat(
                        initialValue = 0.97f, targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "scale",
                    )
                    val sc = if (ps.isPlaying) breathScale else 1f
                    Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }) {
                        Cover(ps.title.ifBlank { "SoundLife" }, side, square = false, img = ps.artUrl)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ps.title.ifBlank { "Ничего не играет" }, color = TextC, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(ps.artist, color = Mut, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    lfm?.let { i ->
                        Text("Слушателей в мире: ${Lastfm.fmt(i.listeners)}", color = Mut, fontSize = 12.sp, maxLines = 1)
                        if (i.tags.isNotEmpty()) {
                            Text(i.tags.joinToString(", "), color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                LikeButton(ps.mediaId)
            }

            Spacer(Modifier.height(12.dp))
            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = ps.duration.coerceAtLeast(1L).toFloat()
            Slider(
                value = drag ?: ps.position.toFloat().coerceIn(0f, dur),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { c.seekTo(it.toLong()) }; drag = null },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(thumbColor = Acc, activeTrackColor = Acc, inactiveTrackColor = Line),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((drag ?: ps.position.toFloat()).toLong()), color = Mut, fontSize = 12.sp)
                Text(fmt(ps.duration), color = Mut, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.shuffleModeEnabled = !c.shuffleModeEnabled }) {
                    Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (ps.shuffle) Acc else Mut)
                }
                IconButton(onClick = { c.seekToPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, "Назад", tint = TextC, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Acc)
                        .clickable { if (c.isPlaying) c.pause() else c.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Пауза/играть", tint = OnAcc, modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { c.seekToNext() }) {
                    Icon(Icons.Filled.SkipNext, "Далее", tint = TextC, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = {
                    c.repeatMode = when (c.repeatMode) {
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                        else -> Player.REPEAT_MODE_ALL
                    }
                }) {
                    Icon(
                        if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Повтор", tint = if (ps.repeat == Player.REPEAT_MODE_OFF) Mut else Acc,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Аватарка круглая; без картинки — значок. */
@Composable
internal fun AvatarCircle(data: String, size: Dp) {
    val bmp = remember(data) { Profile.decode(data)?.asImageBitmap() }
    Box(Modifier.size(size).clip(CircleShape).background(Hover), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize())
        else Icon(Icons.Filled.Person, null, tint = Mut, modifier = Modifier.size(size * 0.55f))
    }
}

/** Текст песни: с таймкодами подсвечивается текущая строка; без них — обычный текст. */
@Composable
private fun LyricsView(state: Pair<Boolean, Lyrics.Result?>, pos: Long, modifier: Modifier) {
    val (loaded, r) = state
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            !loaded -> Text("Загружаем текст…", color = Mut, fontSize = 15.sp)
            r == null -> Text("Текст не найден", color = Mut, fontSize = 15.sp)
            r.instrumental -> Text("Инструментальный трек", color = Mut, fontSize = 15.sp)
            r.lines.isNotEmpty() -> {
                val cur = r.lines.indexOfLast { it.first <= pos }
                val list = rememberLazyListState()
                LaunchedEffect(cur) { if (cur >= 0) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
                    itemsIndexed(r.lines) { i, line ->
                        Text(
                            line.second.ifBlank { "♪" }, textAlign = TextAlign.Center, fontSize = 20.sp,
                            fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == cur) TextC else Mut,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
            else -> Text(
                r.plain, color = TextC, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
    }
}

/** Настройки: цвет акцента и «поверх других приложений». */
@Composable
internal fun SettingsDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(ShapeCard).background(Panel).padding(20.dp)) {
            Text("Настройки", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            var em by remember { mutableStateOf("") }
            var pw by remember { mutableStateOf("") }
            var msg by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                focusedTextColor = TextC, unfocusedTextColor = TextC,
            )
            Spacer(Modifier.height(16.dp))
            Text("Аккаунт SoundLife", color = Mut, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (Auth.signedIn) {
                val ctx = LocalContext.current
                var showNick by remember { mutableStateOf(false) }
                var nickIn by remember { mutableStateOf("") }
                var pfMsg by remember { mutableStateOf("") }
                val pickAva = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Profile.setAvatarFrom(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                            }
                            pfMsg = ""
                        } catch (e: Exception) {
                            pfMsg = e.message ?: "Не удалось сохранить аватарку"
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(Profile.avatar, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (Profile.nick.isNotEmpty()) "@${Profile.nick}" else "Ник не выбран",
                            color = TextC, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(Auth.account, color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { Auth.signOut() }) { Text("Выйти", color = Acc) }
                }
                Row {
                    TextButton(onClick = { nickIn = Profile.nick; showNick = true }) {
                        Text(if (Profile.nick.isEmpty()) "Придумать ник" else "Сменить ник", color = Acc)
                    }
                    TextButton(onClick = { pickAva.launch("image/*") }) { Text("Аватарка", color = Acc) }
                }
                if (pfMsg.isNotEmpty()) Text(pfMsg, color = Mut, fontSize = 12.sp)
                var showJoin by remember { mutableStateOf(false) }
                var joinCode by remember { mutableStateOf("") }
                if (Auth.signedIn) {
                    Spacer(Modifier.height(12.dp))
                    Text("Слушать вместе", color = TextC, fontWeight = FontWeight.Bold)
                    if (Jam.code.isEmpty()) {
                        Row {
                            TextButton(onClick = { Jam.create() }) { Text("Создать комнату", color = Acc) }
                            TextButton(onClick = { joinCode = ""; showJoin = true }) { Text("Войти по коду", color = Acc) }
                        }
                    } else {
                        Text("Комната ${Jam.code.uppercase()}", color = Mut, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Jam.inviteText()), "Позвать друга"))
                            }) { Text("Позвать друга", color = Acc) }
                            TextButton(onClick = { Jam.leave() }) { Text("Выйти из комнаты", color = Acc) }
                        }
                    }
                }
                if (showJoin) AlertDialog(
                    onDismissRequest = { showJoin = false },
                    title = { Text("Код комнаты") },
                    text = {
                        OutlinedTextField(
                            value = joinCode, onValueChange = { joinCode = it.take(8) },
                            singleLine = true, placeholder = { Text("например, k3m9xq") },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showJoin = false; Jam.join(joinCode) }) { Text("Войти", color = Acc) }
                    },
                    dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Отмена", color = Mut) } },
                )
                var qrSid by remember { mutableStateOf<String?>(null) }
                val qrScan = rememberLauncherForActivityResult(ScanContract()) { res ->
                    QrLogin.sidFrom(res.contents.orEmpty())?.let { qrSid = it }
                }
                TextButton(onClick = {
                    qrScan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Наведите камеру на QR-код на компьютере").setBeepEnabled(false))
                }) { Text("Войти на компьютере по QR", color = Acc) }
                qrSid?.let { sid ->
                    AlertDialog(
                        onDismissRequest = { qrSid = null },
                        title = { Text("Войти на другом устройстве?") },
                        text = { Text("Подтвердите, только если сами открыли QR-код на своём компьютере.", color = Mut) },
                        confirmButton = {
                            TextButton(onClick = {
                                qrSid = null
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { runCatching { QrLogin.approve(sid) } }
                                    Toast.makeText(ctx, if (r.isSuccess) "Готово: устройство вошло в ваш аккаунт" else (r.exceptionOrNull()?.message ?: "Не получилось"), Toast.LENGTH_LONG).show()
                                }
                            }) { Text("Войти", color = Acc) }
                        },
                        dismissButton = { TextButton(onClick = { qrSid = null }) { Text("Отмена", color = Mut) } },
                    )
                }
                if (showNick) AlertDialog(
                    onDismissRequest = { showNick = false },
                    title = { Text("Ник") },
                    text = {
                        OutlinedTextField(
                            value = nickIn, onValueChange = { nickIn = it }, singleLine = true,
                            placeholder = { Text("латиница, цифры, _ и точка", color = Mut) },
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { Profile.saveNick(nickIn) }
                                    pfMsg = ""
                                } catch (e: Exception) {
                                    pfMsg = e.message ?: "Не удалось сохранить"
                                }
                                showNick = false
                            }
                        }) { Text("Сохранить", color = Acc) }
                    },
                    dismissButton = { TextButton(onClick = { showNick = false }) { Text("Отмена", color = Mut) } },
                )
            } else {
                // Вход через браузер: вернётся в приложение по soundlife://auth
                val ctx = LocalContext.current
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Auth.googleUrl()))) }) { Text("Войти через Google", color = Acc) }
                OutlinedTextField(
                    value = em, onValueChange = { em = it }, singleLine = true,
                    placeholder = { Text("Почта", color = Mut) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("Пароль", color = Mut) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                )
                if (msg.isNotEmpty()) Text(msg, color = Mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(
                    enabled = !busy && em.isNotBlank() && pw.isNotBlank(),
                    onClick = {
                        busy = true
                        msg = ""
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { Auth.signIn(em, pw) }
                                pw = ""
                            } catch (e: Exception) {
                                msg = e.message ?: "Не удалось войти"
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Входим…" else "Войти", color = Acc) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Прослушано: ${"%.1f".format(Stats.totalHours())} ч", color = Mut, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Цвет акцента", color = Mut, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ACCENTS.forEachIndexed { i, (argb, _) ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(argb)).clickable { Settings.setAccent(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (Settings.accentIx == i) Icon(Icons.Filled.Check, null, tint = Color.Black)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Играть поверх других приложений", color = TextC, fontSize = 15.sp)
                    Text(
                        "Музыка не прерывается, когда в TikTok и других приложениях включается звук. Выключено: обычное поведение, чужой звук ставит музыку на паузу.",
                        color = Mut, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = Settings.mix, onCheckedChange = { Settings.changeMix(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Acc, checkedThumbColor = OnAcc),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Готово", color = Acc) }
        }
    }
}

/** Диалог «В плейлист»: отмечает плейлисты, в которых уже есть трек, и создаёт новый. */
@Composable
internal fun AddToPlaylistDialog() {
    var newName by remember { mutableStateOf("") }
    val path = Playlists.pendingTrack
    if (path == null) return
    val lists = Playlists.lists

    Dialog(onDismissRequest = { Playlists.pendingTrack = null }) {
        Column(Modifier.clip(ShapeCard).background(Panel).padding(20.dp)) {
            Text("В плейлист", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (lists.isEmpty()) {
                Text("Плейлистов пока нет. Создайте первый ниже.", color = Mut, fontSize = 13.sp)
            }
            lists.keys.sorted().forEach { name ->
                val has = path in lists[name].orEmpty()
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (has) Playlists.remove(name, path) else Playlists.add(name, path) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = TextC, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (has) Icon(Icons.Filled.Check, "Уже есть", tint = Acc)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, singleLine = true,
                placeholder = { Text("Название нового плейлиста", color = Mut) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    if (Playlists.create(newName)) {
                        Playlists.add(newName.trim(), path)
                        newName = ""
                    }
                }) { Text("Создать и добавить", color = Acc) }
                TextButton(onClick = { Playlists.pendingTrack = null }) { Text("Готово", color = Acc) }
            }
        }
    }
}
