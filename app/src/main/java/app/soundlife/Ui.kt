package app.soundlife

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut

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

    /** argb — фон сайта, по умолчанию тёмно-серый #0d0d11. */
    fun set(_input: Long) {
        val argb = if (_input == 0L) 0xFF0D0D11L else _input // фон как на сайте (#0d0d11), по умолчанию
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
    val glassShape = RoundedCornerShape(24.dp)
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(glassShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), glassShape),
    ) {
        val target = if (ps.duration > 0) (ps.position.toFloat() / ps.duration).coerceIn(0f, 1f) else 0f
        val progress by androidx.compose.animation.core.animateFloatAsState(target, label = "miniProgress")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Acc, trackColor = Color(0x22FFFFFF),
        )
        var swipe by remember { mutableFloatStateOf(0f) }
        Row(
            Modifier.clickable(onClick = onOpen)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipe = 0f },
                        onDragEnd = { if (swipe > 120f) c.seekToPrevious() else if (swipe < -120f) c.seekToNext() },
                    ) { _, d -> swipe += d }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) { Cover(ps.title.ifEmpty { "?" }, 44.dp, square = true, img = ps.artUrl) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = ps.title, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "miniTitle") { tt ->
                    Text(tt, color = TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                Text(ps.artist, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            IconButton(onClick = { ps.mediaId?.let { Favorites.toggle(it) } }) {
                Icon(if (Favorites.has(ps.mediaId)) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline, "В любимое", tint = if (Favorites.has(ps.mediaId)) Acc else TextC)
            }
            IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                AnimatedContent(targetState = ps.isPlaying, transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f)) }, label = "miniPlay") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/играть", tint = Acc)
                }
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
    val pop = remember { Animatable(1f) }
    val wasLiked = remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !wasLiked.value) {
            pop.animateTo(1.35f, tween(120))
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
        wasLiked.value = liked
    }
    IconButton(onClick = { mediaId?.let { Favorites.toggle(it) } }, enabled = mediaId != null) {
        Icon(
            if (liked) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline, "Лайк",
            tint = if (liked) Acc else TextC,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}


/** Скругления как на сайте: маленькие элементы 10, карточки 14, кнопки и плашки полностью круглые. */
val ShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
val ShapeCard = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
val ShapePill = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)


/** Плавное появление содержимого: затухание и подъём на 260 мс, один раз при показе элемента. */
@Composable
fun Appear(content: @Composable () -> Unit) {
    if (!Settings.anim) { content(); return }
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 },
    ) { content() }
}

/** Цвет ошибок и отказа, как на сайте (--danger). */
val Danger = Color(0xFFFF6B6B)

/** Заглушка загрузки: серые строки мягко мерцают, как на сайте (shim). */
@Composable
fun SkeletonList() {
    val t = rememberInfiniteTransition(label = "shim")
    val a by t.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    val bar = Hover.copy(alpha = a)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(7) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(ShapeSmall).background(bar))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(ShapePill).background(bar))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(ShapePill).background(bar))
                }
            }
        }
    }
}
