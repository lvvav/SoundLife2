package app.soundlife

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Палитра сайта
val Bg = Color(0xFF0D0D11)
val Panel = Color(0xFF121217)
val Hover = Color(0xFF18181F)
val Line = Color(0xFF23232C)
val TextC = Color(0xFFECECF1)
val Mut = Color(0xFF8B8B99)
val Acc = Color(0xFFC6FF3D)
val OnAcc = Color(0xFF11160A)

@Composable
fun SoundLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Acc, onPrimary = OnAcc, background = Bg, onBackground = TextC,
            surface = Panel, onSurface = TextC, surfaceVariant = Hover, onSurfaceVariant = Mut,
            outline = Line,
        ),
        content = content,
    )
}

private sealed interface LibState {
    data object Loading : LibState
    data class Ready(val tracks: List<Track>) : LibState
    data class Error(val msg: String) : LibState
}

private fun fmt(ms: Long): String {
    if (ms < 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun Track.toItem() = MediaItem.Builder()
    .setMediaId(path)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
    ).build()

@Composable
fun SoundLifeApp(controller: MediaController?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf<LibState>(LibState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showFull by remember { mutableStateOf(false) }
    var onlyFav by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        state = LibState.Loading
        state = try {
            LibState.Ready(withContext(Dispatchers.IO) { Library.load(ctx) })
        } catch (e: Exception) {
            LibState.Error(e.message ?: "Нет сети")
        }
    }

    val ps = rememberPlayerState(controller)

    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets.safeDrawing) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SoundLife", color = Acc, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onlyFav = !onlyFav }) {
                    Icon(if (onlyFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Любимое", tint = if (onlyFav) Acc else Mut)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Настройки", tint = Mut) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Поиск по трекам и артистам", color = Mut) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Mut) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Acc, unfocusedBorderColor = Line,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                ),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    LibState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Acc)
                    is LibState.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Не удалось загрузить: ${s.msg}", color = Mut)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { reload++ }, colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = OnAcc)) { Text("Повторить") }
                    }
                    is LibState.Ready -> {
                        val q = query.trim().lowercase()
                        val favs = Favorites.ids
                        val list = remember(s, q, onlyFav, favs) {
                            s.tracks.filter {
                                (!onlyFav || it.path in favs) &&
                                    (q.isEmpty() || "${it.artist} ${it.title} ${it.album}".lowercase().contains(q))
                            }
                        }
                        if (list.isEmpty()) Text(if (onlyFav && q.isEmpty()) "Лайкнутых треков пока нет" else "Ничего не найдено", color = Mut, modifier = Modifier.align(Alignment.Center))
                        else LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                            itemsIndexed(list, key = { _, t -> t.path }) { i, t ->
                                TrackRow(t, playing = ps.mediaId == t.path) {
                                    controller?.run {
                                        setMediaItems(list.map { it.toItem() }, i, 0L)
                                        prepare(); play()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (ps.mediaId != null && controller != null) {
                MiniPlayer(ps, controller) { showFull = true }
            }
        }
    }
    if (showFull && controller != null) FullPlayer(ps, controller) { showFull = false }
    if (showSettings) SettingsDialog { showSettings = false }
}

@Composable
private fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Hover),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Default.GraphicEq else Icons.Default.MusicNote, null, tint = if (playing) Acc else Mut) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = if (playing) Acc else TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(t.artist, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MiniPlayer(ps: PlayerUi, c: MediaController, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        LinearProgressIndicator(
            progress = { if (ps.duration > 0) (ps.position.toFloat() / ps.duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Acc, trackColor = Line,
        )
        Row(Modifier.clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ps.title, color = TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(ps.artist, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            LikeButton(ps.mediaId)
            IconButton(onClick = { c.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Назад", tint = TextC) }
            IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                Icon(if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/играть", tint = Acc)
            }
            IconButton(onClick = { c.seekToNext() }) { Icon(Icons.Default.SkipNext, "Далее", tint = TextC) }
        }
    }
}

@Composable
private fun FullPlayer(ps: PlayerUi, c: MediaController, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Свернуть", tint = TextC) }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Hover),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.MusicNote, null, tint = Acc, modifier = Modifier.size(72.dp)) }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ps.title, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(ps.artist, color = Mut, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LikeButton(ps.mediaId)
            }
            Spacer(Modifier.height(16.dp))

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
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.shuffleModeEnabled = !c.shuffleModeEnabled }) {
                    Icon(Icons.Default.Shuffle, "Перемешать", tint = if (ps.shuffle) Acc else Mut)
                }
                IconButton(onClick = { c.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Назад", tint = TextC, modifier = Modifier.size(36.dp)) }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Acc)
                        .clickable { if (c.isPlaying) c.pause() else c.play() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/играть", tint = OnAcc, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { c.seekToNext() }) { Icon(Icons.Default.SkipNext, "Далее", tint = TextC, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = {
                    c.repeatMode = when (c.repeatMode) {
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                        else -> Player.REPEAT_MODE_ALL
                    }
                }) {
                    Icon(if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Повтор",
                        tint = if (ps.repeat == Player.REPEAT_MODE_OFF) Mut else Acc)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Состояние плеера для интерфейса. */
class PlayerUi {
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_ALL)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
}

@Composable
fun rememberPlayerState(c: MediaController?): PlayerUi {
    val ui = remember { PlayerUi() }
    DisposableEffect(c) {
        if (c == null) return@DisposableEffect onDispose {}
        fun sync() {
            ui.mediaId = c.currentMediaItem?.mediaId
            ui.title = c.mediaMetadata.title?.toString() ?: ""
            ui.artist = c.mediaMetadata.artist?.toString() ?: ""
            ui.isPlaying = c.isPlaying
            ui.shuffle = c.shuffleModeEnabled
            ui.repeat = c.repeatMode
            ui.duration = c.duration.takeIf { it > 0 } ?: 0L
        }
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        c.addListener(l); sync()
        onDispose { c.removeListener(l) }
    }
    LaunchedEffect(c) {
        while (c != null) { ui.position = c.currentPosition; delay(500) }
    }
    return ui
}

/** Сердечко (иконка, не смайлик). Состояние общее с шторкой и экраном блокировки. */
@Composable
fun LikeButton(mediaId: String?) {
    val liked = Favorites.has(mediaId)
    IconButton(onClick = { mediaId?.let { Favorites.toggle(it) } }, enabled = mediaId != null) {
        Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Лайк", tint = if (liked) Acc else Mut)
    }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) {
            Text("Настройки", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
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
                    checked = Settings.mix, onCheckedChange = { Settings.setMix(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Acc, checkedThumbColor = OnAcc),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Готово", color = Acc) }
        }
    }
}
