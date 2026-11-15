package app.soundlife

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.delay
import java.util.Calendar

/** Данные «Итогов года» за текущий год. */
internal class YearData(
    val year: Int,
    val hours: Double,
    val days: Int,
    val tracks: Int,
    val artists: Int,
    val topArtist: String,
    val topTracks: List<Pair<Track, Double>>,
    val topAlbum: String,
    val topAlbumArt: String,
    val avgMinPerDay: Int,
)

internal fun buildYear(cat: Catalog, year: Int): YearData {
    val secBy = Stats.yearTrackSeconds(year)
    val tracks = secBy.mapNotNull { (k, v) -> cat.tracks.firstOrNull { it.path == k }?.let { it to v } }
        .sortedByDescending { it.second }
    val byArtist = HashMap<String, Double>()
    val byAlbum = HashMap<String, Double>()
    for ((t, v) in tracks) {
        byArtist[t.artist] = (byArtist[t.artist] ?: 0.0) + v
        if (t.album.isNotBlank()) byAlbum[t.album] = (byAlbum[t.album] ?: 0.0) + v
    }
    val total = secBy.values.sum()
    val days = Stats.yearActiveDays(year)
    val topArtist = byArtist.maxByOrNull { it.value }?.key.orEmpty()
    val topAlbum = byAlbum.maxByOrNull { it.value }?.key.orEmpty()
    val art = cat.tracks.firstOrNull { it.album == topAlbum && it.img.isNotBlank() }?.img.orEmpty()
    return YearData(
        year = year,
        hours = total / 3600.0,
        days = days,
        tracks = tracks.size,
        artists = byArtist.size,
        topArtist = topArtist,
        topTracks = tracks.take(5),
        topAlbum = topAlbum,
        topAlbumArt = art,
        avgMinPerDay = if (days > 0) (total / 60 / days).toInt() else 0,
    )
}

/** Итоги открываются за неделю до Нового года. */
internal fun yearRecapOpen(year: Int): Boolean {
    val now = Calendar.getInstance()
    val open = Calendar.getInstance().apply { set(year, Calendar.DECEMBER, 25, 0, 0, 0) }
    return now.timeInMillis >= open.timeInMillis
}

/** Карточка на главной. Нажатие: до 25 декабря спрашивает, ждать или смотреть. */
@Composable
internal fun YearRecapCard(cat: Catalog) {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    var ask by remember { mutableStateOf(false) }
    var gift by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6A2BD9), Color(0xFFE0457B))))
            .clickable { if (yearRecapOpen(year)) gift = true else ask = true }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("ИТОГИ $year", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Твой год в музыке", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Icon(Icons.Filled.Share, null, tint = Color.Transparent, modifier = Modifier.size(1.dp))
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Итоги откроются 25 декабря") },
            text = { Text("Можно подождать сюрприза или посмотреть уже сейчас, пока итоги неполные.") },
            confirmButton = { TextButton(onClick = { ask = false; gift = true }) { Text("Посмотреть", color = Acc) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Подождать", color = Mut) } },
            containerColor = Panel, titleContentColor = TextC, textContentColor = TextC,
        )
    }
    if (gift) {
        Dialog(onDismissRequest = { gift = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            YearRecapFlow(cat, year) { gift = false }
        }
    }
}

/** Подарок: нажми 3 раза, коробка открывается, дальше истории. */
@Composable
private fun YearRecapFlow(cat: Catalog, year: Int, onClose: () -> Unit) {
    val data = remember(cat, year) { buildYear(cat, year) }
    var taps by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val opened = taps >= 3
    val wiggle = rememberInfiniteTransition(label = "gift")
    val angle by wiggle.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "wiggle",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A0F4F), Color(0xFF0E0E0E))))) {
        AnimatedVisibility(visible = !opened, exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.4f)) {
            Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(180.dp).rotate(if (taps == 0) 0f else angle)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFF7AA8), Color(0xFF8A5CFF))))
                        .clickable {
                            taps++
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎁", fontSize = 72.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("Нажми на подарок ${3 - taps} раза", color = Mut, fontSize = 15.sp)
            }
        }
        AnimatedVisibility(visible = opened, enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.9f)) {
            YearStory(data, onClose)
        }
        Box(Modifier.statusBarsPadding().padding(12.dp).clickable(onClick = onClose)) {
            Icon(Icons.Filled.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

private val SLIDE_BG = listOf(
    listOf(Color(0xFF2A0F4F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF0F3B4F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF4F1F0F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF1F4F2A), Color(0xFF0E0E0E)),
    listOf(Color(0xFF4F0F2E), Color(0xFF0E0E0E)),
    listOf(Color(0xFF0F2A4F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF3D3D0F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF4F2A0F), Color(0xFF0E0E0E)),
    listOf(Color(0xFF2A0F4F), Color(0xFF6A2BD9)),
)

/** Истории: тап по левой половине — назад, по правой — дальше. */
@Composable
private fun YearStory(d: YearData, onClose: () -> Unit) {
    val n = SLIDE_BG.size
    var idx by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(SLIDE_BG[idx]))
            .pointerInput(idx) {
                detectTapGestures(onTap = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (off.x < size.width / 2) {
                        if (idx > 0) idx--
                    } else {
                        if (idx < n - 1) idx++ else onClose()
                    }
                })
            },
    ) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until n) {
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(if (i <= idx) 1f else 0f).fillMaxSize().background(Color.White))
                }
            }
        }
        Crossfade(targetState = idx, animationSpec = tween(450), label = "slide") { i ->
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Slide(i, d, ctx)
            }
        }
    }
}

/** Один слайд: число с анимацией появления или список. */
@Composable
private fun Slide(i: Int, d: YearData, ctx: android.content.Context) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); go = true }
    val hours by animateFloatAsState(if (go) d.hours.toFloat() else 0f, tween(1600), label = "hours")
    val days by animateFloatAsState(if (go) d.days.toFloat() else 0f, tween(1400), label = "days")
    val artists by animateFloatAsState(if (go) d.artists.toFloat() else 0f, tween(1400), label = "artists")
    val tracks by animateFloatAsState(if (go) d.tracks.toFloat() else 0f, tween(1400), label = "tracks")
    val avg by animateFloatAsState(if (go) d.avgMinPerDay.toFloat() else 0f, tween(1400), label = "avg")
    when (i) {
        0 -> Center("Твой ${d.year}", "В музыке ты провёл", "%.0f".format(hours) + " ч", "Вот что в нём было")
        1 -> Center("Музыка была с тобой", "Дней с прослушкой", "%.0f".format(days), "В среднем ${avg.toInt()} мин в день")
        2 -> Center("Исполнитель года", d.topArtist.ifBlank { "—" }, "", "Больше всего ты слушал его")
        3 -> TopList(d)
        4 -> Center("Альбом года", d.topAlbum.ifBlank { "—" }, "", "Его ты переслушивал чаще всего")
        5 -> Center("Исполнителей", "%.0f".format(artists), "", "Ты открыл для себя столько")
        6 -> Center("Треков", "%.0f".format(tracks), "", "Разных треков за год")
        7 -> Center("Итог", "Слушай дальше", "", "Спасибо, что был с нами в ${d.year}")
        else -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Поделись", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    val text = "Мой ${d.year} в SoundLife: ${"%.0f".format(d.hours)} ч музыки, топ-исполнитель ${d.topArtist}"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться"))
                }) { Text("Отправить", color = Acc, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun Center(title: String, big: String, bigExtra: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(big + if (bigExtra.isNotEmpty()) " $bigExtra" else "", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(sub, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TopList(d: YearData) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Топ-5 треков", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        d.topTracks.forEachIndexed { i, (t, _) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
