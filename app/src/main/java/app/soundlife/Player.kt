package app.soundlife

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

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
        val pctx = LocalContext.current
        val album = c.mediaMetadata.albumTitle?.toString().orEmpty()
        var speed by remember { mutableStateOf(1f) }
        var sleepMin by remember { mutableStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(sleepMin) {
            if (sleepMin > 0) {
                kotlinx.coroutines.delay(sleepMin * 60_000L)
                c.pause()
                sleepMin = 0
            }
        }
        val upcoming = remember(ps.mediaId) {
            (1..5).mapNotNull { k ->
                val i = c.currentMediaItemIndex + k
                if (i < c.mediaItemCount) c.getMediaItemAt(i).mediaMetadata.let { m ->
                    Triple(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), m.artworkUri?.toString().orEmpty())
                } else null
            }
        }
        Column(
            Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
        ) {
            // Верхняя строка: свернуть | «Из альбома» + название | меню
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Свернуть", tint = TextC) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ИЗ АЛЬБОМА", color = Mut, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(album.ifBlank { "Сейчас играет" }, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { ps.mediaId?.let { Playlists.pendingTrack = it } }, enabled = ps.mediaId != null) {
                    Icon(Icons.Filled.MoreVert, "Меню трека", tint = TextC)
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                if (showLy) {
                    Box(Modifier.fillMaxWidth().height(420.dp).clip(ShapeCard).background(Panel).padding(16.dp)) {
                        LyricsView(lyrs, ps.position, Modifier.fillMaxSize(), onSeek = { c.seekTo(it) })
                    }
                    TextButton(onClick = { showLy = false }) { Text("Обложка", color = Acc) }
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val side = maxWidth
                        val breath = rememberInfiniteTransition(label = "breathe")
                        val breathScale by breath.animateFloat(
                            initialValue = 0.98f, targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "scale",
                        )
                        val sc = if (ps.isPlaying) breathScale else 1f
                        Box(Modifier.size(side).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(16.dp))) {
                            Cover(ps.title.ifBlank { "SoundLife" }, side, square = true, img = ps.artUrl)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = ps.title.ifBlank { "Ничего не играет" },
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                            label = "playerTitle",
                        ) { tt ->
                            Text(tt, color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(ps.artist, color = Mut, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LikeButton(ps.mediaId)
                }

                Spacer(Modifier.height(14.dp))
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

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { c.shuffleModeEnabled = !c.shuffleModeEnabled }) {
                        Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (ps.shuffle) Acc else Mut)
                    }
                    IconButton(onClick = { c.seekToPrevious() }) {
                        Icon(Icons.Filled.SkipPrevious, "Назад", tint = TextC, modifier = Modifier.size(40.dp))
                    }
                    val pressSrc = remember { MutableInteractionSource() }
                    val pressed by pressSrc.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "press")
                    Box(
                        Modifier.size(78.dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                            .clip(CircleShape).background(TextC)
                            .clickable(interactionSource = pressSrc, indication = null) { if (c.isPlaying) c.pause() else c.play() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Пауза/играть", tint = Bg, modifier = Modifier.size(40.dp),
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

                // Кнопки: скорость, таймер сна, скачать, поделиться
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                            c.setPlaybackSpeed(speed)
                        },
                        shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextC),
                    ) { Text(if (speed == 1f) "1×" else "${speed}×", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { sleepMin = when (sleepMin) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 } },
                        shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextC),
                    ) { Text(if (sleepMin == 0) "Таймер сна" else "Сон через $sleepMin мин", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = {
                            val id = ps.mediaId
                            val repo = BuildConfig.GH_REPOS.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() }
                            if (id != null && repo != null) {
                                val req = android.app.DownloadManager.Request(android.net.Uri.parse(Library.rawUrl(repo, id)))
                                    .setTitle(ps.title)
                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "SoundLife/" + id.substringAfterLast('/'))
                                (pctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
                                android.widget.Toast.makeText(pctx, "Скачивание начато", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextC),
                    ) { Text("Скачать", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = {
                            val text = "${ps.artist} — ${ps.title}"
                            pctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться"))
                        },
                        shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextC),
                    ) { Text("Поделиться", fontSize = 13.sp) }
                }

                // Текст песни
                if (!showLy) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth().clip(ShapeCard).background(Panel).padding(16.dp)) {
                        Text("Текст", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LyricsView(lyrs, ps.position, Modifier.fillMaxWidth().height(120.dp), onSeek = { c.seekTo(it) })
                        TextButton(onClick = { showLy = true }) { Text("Показать текст", color = Acc) }
                    }
                }

                // О треке
                Spacer(Modifier.height(22.dp))
                Text("О ТРЕКЕ", color = Mut, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val fileName = ps.mediaId?.substringAfterLast('/').orEmpty()
                listOf(
                    "Название" to ps.title,
                    "Исполнитель" to ps.artist,
                    "Альбом" to album,
                    "Длительность" to fmt(ps.duration),
                    "Формат" to fileName.substringAfterLast('.', "").uppercase(),
                    "Файл" to fileName,
                ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, color = Mut, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(v, color = TextC, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = Line)
                }

                // Далее
                if (upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("ДАЛЕЕ", color = Mut, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    upcoming.forEach { (tt, aa, art) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(tt, 48.dp, img = art)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tt, color = TextC, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(aa, color = Mut, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
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
private fun LyricsView(state: Pair<Boolean, Lyrics.Result?>, pos: Long, modifier: Modifier, onSeek: (Long) -> Unit = {}) {
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
                            modifier = Modifier.clickable { onSeek(line.first) }.padding(vertical = 6.dp),
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
            Spacer(Modifier.height(12.dp))
            Text("АККАУНТ", color = Mut, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                var showUpd by remember { mutableStateOf(false) }
                var showInfo by remember { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Text("ИНФОРМАЦИЯ", color = Mut, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row {
                    TextButton(onClick = { showUpd = true }) { Text("Обновления", color = Acc) }
                    TextButton(onClick = { showInfo = true }) { Text("Информация", color = Acc) }
                }
                if (showUpd) UpdatesDialog { showUpd = false }
                if (showInfo) InfoDialog { showInfo = false }
                var showJoin by remember { mutableStateOf(false) }
                var joinCode by remember { mutableStateOf("") }
                if (Auth.signedIn) {
                    Spacer(Modifier.height(12.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("СЛУШАТЬ ВМЕСТЕ", color = Mut, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
