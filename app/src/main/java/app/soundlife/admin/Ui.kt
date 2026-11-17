package app.soundlife.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Acc = Color(0xFFC6FF3D)
val Bg = Color(0xFF0D0D11)
val Panel = Color(0xFF17171E)
val PanelHi = Color(0xFF22222C)
val Mut = Color(0xFF9A9AA6)
val Bad = Color(0xFFFF6B6B)

/** Обложка с кэшем. Если картинки нет, показывает значок на цветном фоне. */
@Composable
fun Cover(url: String?, size: Int, round: Boolean = false, placeholder: String = "♪") {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching { Covers.load(url)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier.size(size.dp).clip(shape).background(PanelHi),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(placeholder, color = Mut, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Строка трека: обложка, название, исполнитель и значок выбора. */
@Composable
fun TrackRow(
    t: Track,
    repo: String,
    branch: String,
    playing: Boolean,
    selectable: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val cover = t.cover?.let { Raw.url(repo, branch, it) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PanelHi else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                fontWeight = FontWeight.SemiBold,
                color = if (playing) Acc else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = Mut, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (playing) Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Acc)
        if (selectable) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Acc else Mut,
            )
        }
    }
}

/** Заголовок раздела крупным шрифтом. */
@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        if (sub != null) Text(sub, color = Mut, fontSize = 14.sp)
    }
}

/** Плитка для сетки альбомов и артистов. */
@Composable
fun GridCard(title: String, sub: String, cover: String?, round: Boolean, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) PanelHi else Color.Transparent).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(cover, 150, round = round, placeholder = title.firstOrNull()?.uppercase() ?: "♪")
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(sub, color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Мини-плеер над нижней панелью: нажатие открывает полный плеер. */
@Composable
fun MiniPlayer(repo: String, branch: String, tick: Int, onOpen: () -> Unit) {
    val t = Player.current ?: return
    val cover = t.cover?.let { Raw.url(repo, branch, it) }
    Row(
        Modifier.fillMaxWidth().background(PanelHi).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 44)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { Player.prev(); }) { Icon(Icons.Default.SkipPrevious, null) }
        IconButton(onClick = { Player.toggle() }) {
            Icon(if (Player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Acc)
        }
        IconButton(onClick = { Player.next() }) { Icon(Icons.Default.SkipNext, null) }
    }
}

/** Небольшой пустой экран с текстом. */
@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Mut, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
