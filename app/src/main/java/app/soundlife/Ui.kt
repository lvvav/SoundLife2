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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.pow
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Палитра: по умолчанию AMOLED-чёрный. Если на сайте выбран другой фон, цвета пересчитываются
 * так же, как applyTheme на сайте (светлый или тёмный фон, текст контрастный).
 */
object Palette {
    var bg by mutableStateOf(Color(0xFF0D0D11)); private set
    var panel by mutableStateOf(Color(0xFF121217)); private set
    var hover by mutableStateOf(Color(0xFF18181F)); private set
    var line by mutableStateOf(Color(0xFF23232C)); private set
    var text by mutableStateOf(Color(0xFFECECF1)); private set
    var mut by mutableStateOf(Color(0xFF8B8B99)); private set
    var dark by mutableStateOf(true); private set

    /** argb — фон сайта, например 0xFF000000. */
    fun set(argb: Long) {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val isDark = luminance(r, g, b) < 0.4f
        val w = if (isDark) 255f else 0f
        val tr = if (isDark) 0xEC else 0x15
        val tg = if (isDark) 0xEC else 0x15
        val tb = if (isDark) 0xF1 else 0x1A
        fun m(a: Float, c: Float, t: Float) = a + (c - a) * t
        fun col(x: Float, y: Float, z: Float) = Color(x / 255f, y / 255f, z / 255f)
        val br = r.toFloat(); val bgn = g.toFloat(); val bb = b.toFloat()
        bg = col(br, bgn, bb)
        panel = col(m(br, w, .04f), m(bgn, w, .04f), m(bb, w, .04f))
        hover = col(m(br, w, .08f), m(bgn, w, .08f), m(bb, w, .08f))
        val lt = if (isDark) .14f else .13f
        line = col(m(br, w, lt), m(bgn, w, lt), m(bb, w, lt))
        text = col(tr.toFloat(), tg.toFloat(), tb.toFloat())
        mut = col(m(tr.toFloat(), br, .5f), m(tg.toFloat(), bgn, .5f), m(tb.toFloat(), bb, .5f))
        dark = isDark
    }

    private fun luminance(r: Int, g: Int, b: Int): Float {
        fun lin(v: Int): Float {
            val x = v / 255f
            return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }
}

val Bg: Color get() = Palette.bg
val Panel: Color get() = Palette.panel
val Hover: Color get() = Palette.hover
val Line: Color get() = Palette.line
val TextC: Color get() = Palette.text
val Mut: Color get() = Palette.mut
/** Акцент берётся из настроек (меняется в диалоге). */
val Acc: Color get() = Color(Settings.accentArgb)
val OnAcc = Color(0xFF11160A)

@Composable
fun SoundLifeTheme(content: @Composable () -> Unit) {
    val scheme = if (Palette.dark) {
        darkColorScheme(
            primary = Acc, onPrimary = OnAcc, background = Bg, onBackground = TextC,
            surface = Panel, onSurface = TextC, surfaceVariant = Hover, onSurfaceVariant = Mut, outline = Line,
        )
    } else {
        lightColorScheme(
            primary = Acc, onPrimary = OnAcc, background = Bg, onBackground = TextC,
            surface = Panel, onSurface = TextC, surfaceVariant = Hover, onSurfaceVariant = Mut, outline = Line,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

internal fun fmt(ms: Long): String {
    if (ms < 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun MiniPlayer(ps: PlayerUi, c: MediaController, onOpen: () -> Unit) {
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

/** Состояние плеера для интерфейса. */
class PlayerUi {
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artUrl by mutableStateOf("")
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
            ui.artUrl = c.mediaMetadata.artworkUri?.toString() ?: ""
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


/** Скругления как на сайте: маленькие элементы 10, карточки 14, кнопки и плашки полностью круглые. */
val ShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
val ShapeCard = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
val ShapePill = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)


/** Плавное появление содержимого: затухание и подъём на 260 мс, один раз при показе элемента. */
@Composable
fun Appear(content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 },
    ) { content() }
}
