package app.soundlife

import android.net.Uri
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.LibraryMusic as LibraryMusicOutlined
import androidx.compose.material.icons.outlined.Search as SearchOutlined
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.MediaRecorder
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player as Exo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Group
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Call
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.paint
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.GridView
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.NewReleases

// ───────────────────────── данные ─────────────────────────

private sealed interface LibState {
    data object Loading : LibState
    data class Ready(val tracks: List<Track>) : LibState
    data class Error(val msg: String) : LibState
}

/** Экран поверх вкладки (страница артиста, альбома, плейлиста или любимых). */
private sealed interface Detail {
    data class Artist(val name: String) : Detail
    data class Album(val name: String) : Detail
    data class Playlist(val name: String) : Detail
    data class YmAlbumPage(val id: String, val title: String, val artist: String, val img: String) : Detail
    data class YmArtistPage(val id: String, val name: String, val img: String) : Detail
    data object Liked : Detail
    data object Wish : Detail
    data class User(val uid: String, val name: String) : Detail
    data object Inbox : Detail
    data class Collab(val id: String, val name: String, val owner: String) : Detail
    data class Thread(val uid: String, val name: String) : Detail
    data class PubPl(val id: String, val name: String, val tracks: List<String>) : Detail
}

private class AlbumInfo(val name: String, val artist: String, val tracks: List<Track>) {
    val img: String = tracks.firstOrNull { it.img.isNotBlank() }?.img.orEmpty()
}

/** Каталог: треки, сгруппированные по артистам и альбомам (альбом = папка в music/). */
private class Catalog(val tracks: List<Track>) {
    val artists: List<Pair<String, List<Track>>> =
        tracks.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    val albums: List<AlbumInfo> =
        tracks.filter { it.album.isNotBlank() }.groupBy { it.album }.map { (name, ts) ->
            val main = ts.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: ""
            AlbumInfo(name, main, ts)
        }.sortedBy { it.name.lowercase() }

    fun artist(name: String): List<Track> = artists.firstOrNull { it.first == name }?.second ?: emptyList()
    fun album(name: String): AlbumInfo? = albums.firstOrNull { it.name == name }
}

/** Треки плейлиста в порядке добавления. Удалённые из каталога пропускаются. */
private fun playlistTracks(cat: Catalog, name: String): List<Track> =
    Playlists.lists[name].orEmpty().mapNotNull { p -> cat.tracks.firstOrNull { it.path == p } }

internal fun Track.toItem() = MediaItem.Builder()
    .setMediaId(path)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setArtworkUri(if (img.isNotBlank()) Uri.parse(img) else null).build()
    ).build()

internal fun MediaController.playTracks(list: List<Track>, index: Int, shuffle: Boolean? = null) {
    if (list.isEmpty()) return
    if (shuffle != null) shuffleModeEnabled = shuffle
    setMediaItems(list.map { it.toItem() }, index.coerceIn(0, list.size - 1), 0L)
    prepare(); play()
}

private fun plural(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    val w = if (m100 in 11..14) "треков" else when (m10) { 1 -> "трек"; 2, 3, 4 -> "трека"; else -> "треков" }
    return "$n $w"
}

private enum class NavTab(val label: String, val on: ImageVector, val off: ImageVector) {
    Home("Главная", Icons.Filled.Home, Icons.Outlined.HomeOutlined),
    Search("Поиск", Icons.Filled.Search, Icons.Outlined.SearchOutlined),
    Library("Медиатека", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusicOutlined),
}

// ───────────────────────── каркас приложения ─────────────────────────

@Composable
fun SoundLifeApp(controller: MediaController?) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<LibState>(LibState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var tabIx by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showFull by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val readyTracks = (state as? LibState.Ready)?.tracks
    LaunchedEffect(readyTracks) { Jam.setTracks(readyTracks.orEmpty()) }
    LaunchedEffect(controller) { Jam.attach(controller) }

    LaunchedEffect(reload) {
        state = LibState.Loading
        state = try {
            LibState.Ready(withContext(Dispatchers.IO) { Library.load(ctx) })
        } catch (e: Exception) {
            LibState.Error(e.message ?: "Нет сети")
        }
    }

    val ps = rememberPlayerState(controller)
    BackHandler(enabled = detail != null) { detail = null }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Panel) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(Profile.nick.ifEmpty { "SoundLife" }, color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (Auth.signedIn) "Посмотреть профиль" else "Вы не вошли", color = Mut, fontSize = 13.sp)
                }
                listOf(
                    Triple("Профиль", Icons.Filled.Person, { Auth.userId()?.let { detail = Detail.User(it, Profile.nick) } }),
                    Triple("Сообщения", Icons.AutoMirrored.Filled.Send, { detail = Detail.Inbox }),
                    Triple("Любимые треки", Icons.Filled.Favorite, { detail = Detail.Liked }),
                    Triple("Настройки", Icons.Filled.Settings, { showSettings = true }),
                ).forEach { (label, icon, act) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        icon = { Icon(icon, null) },
                        selected = false,
                        onClick = { drawerScope.launch { drawer.close() }; act() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            Column {
                androidx.compose.animation.AnimatedVisibility(
                    visible = ps.mediaId != null && controller != null,
                    enter = androidx.compose.animation.slideInVertically(tween(320)) { it } + androidx.compose.animation.fadeIn(tween(320)),
                    exit = androidx.compose.animation.slideOutVertically(tween(260)) { it } + androidx.compose.animation.fadeOut(tween(200)),
                ) { if (controller != null) MiniPlayer(ps, controller) { showFull = true } }
                NavigationBar(containerColor = Bg, tonalElevation = 0.dp) {
                    NavTab.entries.forEach { t ->
                        val sel = tabIx == t.ordinal
                        NavigationBarItem(
                            selected = sel,
                            onClick = { tabIx = t.ordinal; detail = null },
                            icon = {
                                val iconScale by animateFloatAsState(if (sel) 1.12f else 1f, label = "navIcon")
                                Icon(if (sel) t.on else t.off, t.label, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                            },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Acc, selectedTextColor = Acc,
                                unselectedIconColor = Mut, unselectedTextColor = Mut,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            when (val s = state) {
                LibState.Loading -> SkeletonList()
                is LibState.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Не удалось загрузить: ${s.msg}", color = Mut)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { reload++ }, colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = OnAcc)) { Text("Повторить") }
                }
                is LibState.Ready -> {
                    val cat = remember(s) { Catalog(s.tracks) }
                    AnimatedContent(
                        targetState = detail,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }) togetherWith fadeOut(tween(160))
                        },
                        label = "screen",
                    ) { d ->
                    if (d != null) {
                        if (d is Detail.Thread) {
                            ThreadScreen(d, onBack = { detail = Detail.Inbox }, tracks = cat.tracks, ps = ps, controller = controller)
                        } else if (d is Detail.Inbox) {
                            InboxScreen(onBack = { detail = null }, onOpen = { detail = it })
                        } else if (d is Detail.Collab) {
                            CollabScreen(d, cat, ps, controller, onBack = { detail = null })
                        } else if (d is Detail.User) {
                            UserScreen(d, onBack = { detail = null }, onOpen = { detail = it })
                        } else if (d is Detail.YmAlbumPage || d is Detail.YmArtistPage) {
                            YmDetailScreen(d, ps, controller, onBack = { detail = null }, onOpen = { detail = it })
                        } else {
                            DetailScreen(d, cat, ps, controller, onBack = { detail = null }, onOpen = { detail = it })
                        }
                    } else when (NavTab.entries[tabIx]) {
                        NavTab.Home -> HomeScreen(cat, ps, controller, onOpen = { detail = it }, onSettings = { showSettings = true }, onMenu = { drawerScope.launch { drawer.open() } })
                        NavTab.Search -> SearchScreen(cat, ps, controller, onOpen = { detail = it })
                        NavTab.Library -> LibraryScreen(cat, controller, onOpen = { detail = it }, onSettings = { showSettings = true })
                    }
                    }
                }
            }
        }
    }
    }
    if (showFull && controller != null) FullPlayer(ps, controller) { showFull = false }
    if (showSettings) SettingsHub { showSettings = false }
    AddToPlaylistDialog()
}

// ───────────────────────── Главная ─────────────────────────

private class Quick(val title: String, val seed: String, val icon: ImageVector?, val round: Boolean, val onClick: () -> Unit)

@Composable
private fun HomeScreen(cat: Catalog, ps: PlayerUi, c: MediaController?, onOpen: (Detail) -> Unit, onSettings: () -> Unit, onMenu: () -> Unit = {}) {
    val subA = Subs.artists
    val subB = Subs.albums
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val hello = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val artists = remember(cat, subA) { cat.artists.sortedByDescending { it.first in subA } }
    val albums = remember(cat, subB) { cat.albums.sortedByDescending { it.name in subB } }

    val quick = buildList {
        add(Quick("Любимые треки", "liked", Icons.Filled.Favorite, false) { onOpen(Detail.Liked) })
        add(Quick("Перемешать всё", "shuffle", Icons.Filled.Shuffle, false) {
            c?.playTracks(cat.tracks, Random.nextInt(cat.tracks.size.coerceAtLeast(1)), true)
        })
        cat.artists.filter { it.first in subA }.take(2).forEach { a ->
            add(Quick(a.first, a.first, null, true) { onOpen(Detail.Artist(a.first)) })
        }
        cat.albums.filter { it.name in subB }.take(2).forEach { a ->
            add(Quick(a.name, a.name, null, false) { onOpen(Detail.Album(a.name)) })
        }
    }

    var showStats by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    if (showRecent) RecentDialog(cat.tracks, c) { showRecent = false }
    if (showStats) StatsDialog { showStats = false }
    var showAllTracks by remember { mutableStateOf(false) }
    var homeTab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hello, color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Меню", tint = Mut) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки", tint = Mut) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(listOf("Все", "Артисты", "Альбомы", "Треки")) { i, label ->
                    FilterChip(
                        selected = homeTab == i, onClick = { homeTab = i }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Hover, labelColor = TextC, selectedContainerColor = Acc, selectedLabelColor = OnAcc),
                        border = null,
                    )
                }
            }
        }
        if (homeTab == 0) item {
            val hrs = remember { String.format(java.util.Locale.US, "%.1f", Stats.totalHours()) }
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(ShapeCard)
                    .background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFF107A3))))
                    .clickable { showStats = true }.padding(16.dp),
            ) {
                Column {
                    Text("ИТОГИ", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("Вы слушали $hrs ч", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Нажмите, чтобы открыть статистику", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { q -> QuickTile(q, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(ShapeCard).background(Panel).padding(16.dp)) {
                Text("ДЛЯ ВАС", color = Mut, fontSize = 12.sp)
                Text("Моя волна", color = TextC, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Перемешанные треки из вашей библиотеки", color = Mut, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { c?.playTracks(cat.tracks, Random.nextInt(cat.tracks.size.coerceAtLeast(1)), true) }) {
                    Text("Слушать", color = Acc, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            val week = remember { (Stats.dailySec(7).sumOf { it.second } / 60).toInt() }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(ShapeCard).background(Panel)
                    .clickable { showStats = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ТВОЯ МУЗЫКА · 7 ДНЕЙ", color = Mut, fontSize = 12.sp)
                    Text("$week мин", color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Нажмите, чтобы увидеть итоги", color = Mut, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { showRecent = true }) { Text("Недавние", color = Acc) }
            }
        }
        if (artists.isNotEmpty() && (homeTab == 0 || homeTab == 1)) {
            item { SectionTitle("Артисты") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artists, key = { it.first }) { a -> ArtistCard(a.first, a.second.firstOrNull { it.img.isNotBlank() }?.img.orEmpty()) { onOpen(Detail.Artist(a.first)) } }
                }
            }
        }
        if (albums.isNotEmpty() && (homeTab == 0 || homeTab == 2)) {
            item { SectionTitle("Альбомы") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.name }) { a -> AlbumCard(a) { onOpen(Detail.Album(a.name)) } }
                }
            }
        }
        if (homeTab == 0 || homeTab == 3) item { SectionTitle("Все треки") }
        if (homeTab == 0 || homeTab == 3) item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showAllTracks = !showAllTracks }) {
                    Text(if (showAllTracks) "Свернуть" else "Показать все · ${cat.tracks.size}", color = Acc)
                }
            }
        }
        if (homeTab == 0 || homeTab == 3) itemsIndexed(if (showAllTracks) cat.tracks else cat.tracks.take(20), key = { _, t -> t.path }) { i, t ->
            TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(cat.tracks, i) }
        }
    }
}

@Composable
private fun QuickTile(q: Quick, modifier: Modifier) {
    Row(
        modifier.height(56.dp).clip(ShapeSmall).background(Hover).clickable(onClick = q.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(q.seed, 56.dp, q.round, q.icon, square = true)
        Text(
            q.title, color = TextC, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 10.dp))
}

@Composable
private fun ArtistCard(name: String, img: String, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clip(ShapeSmall).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(name, 104.dp, round = true, img = img)
        Spacer(Modifier.height(6.dp))
        Text(name, color = TextC, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AlbumCard(a: AlbumInfo, onClick: () -> Unit) {
    Column(Modifier.width(130.dp).clip(ShapeSmall).clickable(onClick = onClick)) {
        Cover(a.name, 130.dp, img = a.img)
        Spacer(Modifier.height(6.dp))
        Text(a.name, color = TextC, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.artist, color = Mut, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── Поиск ─────────────────────────

@Composable
private fun SearchScreen(cat: Catalog, ps: PlayerUi, c: MediaController?, onOpen: (Detail) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var srcTab by rememberSaveable { mutableIntStateOf(0) }
    val showL = srcTab == 0 || srcTab == 1
    val showI = srcTab == 0 || srcTab == 2
    val showY = srcTab == 0 || srcTab == 3
    val query = q.trim().lowercase()
    /** Результаты iTunes (30-секундные отрывки). Запрос после паузы 450 мс, от 3 символов. */
    var itRes by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(query) {
        itRes = emptyList()
        if (query.length < 3) return@LaunchedEffect
        delay(450)
        itRes = try { withContext(Dispatchers.IO) { ITunes.search(query) } } catch (e: Exception) { emptyList() }
    }
    /** Яндекс Музыка: треки, альбомы и исполнители. Только для владельца с входом; иначе тихо пусто. */
    var ymRes by remember { mutableStateOf<List<Track>>(emptyList()) }
    var ymAl by remember { mutableStateOf<List<YmAlbum>>(emptyList()) }
    var ymAr by remember { mutableStateOf<List<YmArtist>>(emptyList()) }
    LaunchedEffect(query) {
        ymRes = emptyList(); ymAl = emptyList(); ymAr = emptyList()
        if (query.length < 3 || !Auth.signedIn) return@LaunchedEffect
        delay(450)
        try {
            val r = withContext(Dispatchers.IO) { Ym.search(query) }
            ymRes = r.tracks.map { Ym.asTrack(it) }
            ymAl = r.albums
            ymAr = r.artists
        } catch (e: Exception) {
            // Яндекс недоступен или нет доступа: раздел просто не показывается
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Поиск", color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Треки, артисты, альбомы", color = Mut) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Mut) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Очистить", tint = Mut) } },
            shape = ShapeCard,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Acc, unfocusedBorderColor = Line,
                focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                cursorColor = Acc, focusedTextColor = TextC, unfocusedTextColor = TextC,
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        )
        if (query.isEmpty()) {
            Text("Ищите по названию трека, артисту или альбому", color = Mut, modifier = Modifier.padding(horizontal = 16.dp))
            return@Column
        }
        val ar = remember(cat, query) { cat.artists.filter { it.first.lowercase().contains(query) } }
        val al = remember(cat, query) { cat.albums.filter { "${it.name} ${it.artist}".lowercase().contains(query) } }
        val tr = remember(cat, query) { searchFiltered(cat.tracks, query) { "${it.artist} ${it.title} ${it.album}" } }
        if (ar.isEmpty() && al.isEmpty() && tr.isEmpty() && itRes.isEmpty() && ymRes.isEmpty() && ymAl.isEmpty() && ymAr.isEmpty()) {
            Text("Ничего не найдено", color = Mut, modifier = Modifier.padding(horizontal = 16.dp))
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("Все", "Медиатека", "iTunes", "Яндекс")) { i, label ->
                FilterChip(
                    selected = srcTab == i, onClick = { srcTab = i }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Hover, labelColor = TextC, selectedContainerColor = Acc, selectedLabelColor = OnAcc),
                    border = null,
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (ar.isNotEmpty() && showL) {
                item { SectionTitle("Артисты") }
                items(ar, key = { "a:" + it.first }) { a ->
                    LibRow(a.first, "Артист · ${plural(a.second.size)}", a.first, round = true, img = a.second.firstOrNull { it.img.isNotBlank() }?.img.orEmpty()) { onOpen(Detail.Artist(a.first)) }
                }
            }
            if (al.isNotEmpty() && showL) {
                item { SectionTitle("Альбомы") }
                items(al, key = { "b:" + it.name }) { a ->
                    LibRow(a.name, "Альбом · ${a.artist}", a.name, img = a.img) { onOpen(Detail.Album(a.name)) }
                }
            }
            if (tr.isNotEmpty() && showL) {
                item { SectionTitle("Треки") }
                itemsIndexed(tr, key = { _, t -> "t:" + t.path }) { i, t ->
                    TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(tr, i) }
                }
            }
            if (ymAr.isNotEmpty() && showY) {
                item { SectionTitle("Яндекс · исполнители") }
                items(ymAr, key = { "yar:" + it.id }) { a ->
                    LibRow(a.name, "Исполнитель · Яндекс Музыка", a.name, round = true, img = a.img) { onOpen(Detail.YmArtistPage(a.id, a.name, a.img)) }
                }
            }
            if (ymAl.isNotEmpty() && showY) {
                item { SectionTitle("Яндекс · альбомы") }
                items(ymAl, key = { "yal:" + it.id }) { a ->
                    LibRow(a.title, "Альбом · ${a.artist}" + (if (a.year.isNotBlank()) " · ${a.year}" else ""), a.title, img = a.img) {
                        onOpen(Detail.YmAlbumPage(a.id, a.title, a.artist, a.img))
                    }
                }
            }
            if (ymRes.isNotEmpty() && showY) {
                item { SectionTitle("Яндекс Музыка · полные треки") }
                itemsIndexed(ymRes, key = { _, t -> t.path }) { i, t ->
                    TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(ymRes, i) }
                }
            }
            if (itRes.isNotEmpty() && showI) {
                item { SectionTitle("iTunes · отрывки по 30 секунд") }
                itemsIndexed(itRes, key = { _, t -> t.path }) { i, t ->
                    TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(itRes, i) }
                }
            }
        }
    }
}

// ───────────────────────── Медиатека ─────────────────────────

@Composable
private fun LibraryScreen(cat: Catalog, c: MediaController?, onOpen: (Detail) -> Unit, onSettings: () -> Unit) {
    var showNewest by remember { mutableStateOf(false) }
    if (showNewest) NewestDialog(cat.tracks, c) { showNewest = false }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 всё, 1 плейлисты, 2 альбомы, 3 артисты
    var grid by rememberSaveable { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var findNick by remember { mutableStateOf(false) }
    var collabs by remember { mutableStateOf<List<Collab.Pl>>(emptyList()) }
    var newCollab by remember { mutableStateOf(false) }
    var newCollabIn by remember { mutableStateOf("") }
    var collabMsg by remember { mutableStateOf("") }
    var findIn by remember { mutableStateOf("") }
    var findMsg by remember { mutableStateOf("") }
    val lsc = rememberCoroutineScope()
    LaunchedEffect(Auth.signedIn) {
        collabs = if (Auth.signedIn) withContext(Dispatchers.IO) { runCatching { Collab.list() }.getOrDefault(emptyList()) } else emptyList()
    }
    val subA = Subs.artists
    val subB = Subs.albums
    val names = Playlists.lists.keys.sorted()
    val favCount = Favorites.ids.count { id -> cat.tracks.any { it.path == id } }
    val myArtists = cat.artists.filter { it.first in subA }
    val myAlbums = cat.albums.filter { it.name in subB }
    val showPl = filter == 0 || filter == 1
    val showAl = filter == 0 || filter == 2
    val showAr = filter == 0 || filter == 3
    val empty = (!showPl || names.isEmpty()) && (!showAl || myAlbums.isEmpty()) && (!showAr || myArtists.isEmpty()) && Subs.ym.isEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Медиатека", color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, "Вид", tint = Mut) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки", tint = Mut) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(listOf("Всё", "Плейлисты", "Альбомы", "Артисты")) { i, label ->
                FilterChip(
                    selected = filter == i, onClick = { filter = i }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Hover, labelColor = TextC,
                        selectedContainerColor = Acc, selectedLabelColor = OnAcc,
                    ),
                    border = null,
                )
            }
        }
        if (filter == 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradTile("Любимые", plural(favCount), listOf(Color(0xFFB721FF), Color(0xFF21D4FD)), Icons.Filled.Favorite, Modifier.weight(1f)) { onOpen(Detail.Liked) }
                GradTile("Хочу послушать", plural(Wishes.list.size), listOf(Color(0xFFFF8A00), Color(0xFFE52E71)), Icons.Filled.Add, Modifier.weight(1f)) { onOpen(Detail.Wish) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                GradTile("Новое в хранилище", "Последние 50", listOf(Color(0xFF11998E), Color(0xFF38EF7D)), Icons.Filled.NewReleases, Modifier.fillMaxWidth()) { showNewest = true }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (filter == 0) {
                item { LibRow("Любимые треки", plural(favCount), "liked", icon = Icons.Filled.Favorite) { onOpen(Detail.Liked) } }
                if (Wishes.list.isNotEmpty()) {
                    item { LibRow("Хочу послушать", plural(Wishes.list.size), "wish", icon = Icons.Filled.Add) { onOpen(Detail.Wish) } }
                }
            }
            if (filter == 0 && Auth.signedIn) {
                item { LibRow("Сообщения", "Диалоги", "chat", icon = Icons.AutoMirrored.Filled.Send) { onOpen(Detail.Inbox) } }
                item { LibRow("Новый совместный плейлист", "Со списком друзей", "newcollab", icon = Icons.Filled.Add) { newCollab = true } }
                items(collabs, key = { "cl:${it.id}" }) { p ->
                    LibRow(p.name, if (p.owner == Auth.userId()) "Совместный · вы владелец" else "Совместный", "cl:${p.id}", icon = Icons.Filled.Group) {
                        onOpen(Detail.Collab(p.id, p.name, p.owner))
                    }
                }
            }
            if (filter == 0) {
                item { LibRow("Найти человека", "По нику", "findperson", icon = Icons.Filled.Search) { findNick = true } }
                items(Follows.users, key = { "fu:$it" }) { uid ->
                    val p = Follows.people[uid]
                    LibRow(p?.nick?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Пользователь", "Подписка", "fu:$uid", round = true) {
                        onOpen(Detail.User(uid, p?.nick.orEmpty()))
                    }
                }
                items(Follows.playlists, key = { "fp:$it" }) { id ->
                    Follows.pubs[id]?.let { pl ->
                        LibRow(pl.name, "Публичный плейлист", "fp:$id", icon = Icons.Filled.LibraryMusic) { onOpen(Detail.PubPl(pl.id, pl.name, pl.tracks)) }
                    }
                }
            }
            if (showPl) {
                item { LibRow("Новый плейлист", "Создать", "new", icon = Icons.Filled.Add) { showNew = true } }
                items(names, key = { "p:$it" }) { n ->
                    LibRow(n, "Плейлист · ${plural(playlistTracks(cat, n).size)}", n, icon = Icons.Filled.LibraryMusic) { onOpen(Detail.Playlist(n)) }
                }
            }
            if (showAl) items(Subs.ym.filter { it.kind == "album" }, key = { "yalb:" + it.id }) { s ->
                LibRow(s.title, "Альбом · ${s.sub}", s.title, img = s.img) { onOpen(Detail.YmAlbumPage(s.id, s.title, s.sub, s.img)) }
            }
            if (showAl && grid) item { AlbumGrid(myAlbums) { onOpen(Detail.Album(it.name)) } }
            if (showAl && !grid) items(myAlbums, key = { "b:" + it.name }) { a ->
                LibRow(a.name, "Альбом · ${a.artist}", a.name, img = a.img) { onOpen(Detail.Album(a.name)) }
            }
            if (showAr) items(Subs.ym.filter { it.kind == "artist" }, key = { "yar:" + it.id }) { s ->
                LibRow(s.title, "Исполнитель · Яндекс Музыка", s.title, round = true, img = s.img) { onOpen(Detail.YmArtistPage(s.id, s.title, s.img)) }
            }
            if (showAr) items(myArtists, key = { "a:" + it.first }) { a ->
                LibRow(a.first, "Артист", a.first, round = true, img = a.second.firstOrNull { it.img.isNotBlank() }?.img.orEmpty()) { onOpen(Detail.Artist(a.first)) }
            }
            if (empty) item {
                Text(
                    "Подписывайтесь на артистов и альбомы на их страницах — они появятся здесь. Плейлисты создаются кнопкой выше.",
                    color = Mut, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (newCollab) AlertDialog(
        onDismissRequest = { newCollab = false },
        title = { Text("Новый совместный плейлист") },
        text = {
            Column {
                OutlinedTextField(
                    value = newCollabIn, onValueChange = { newCollabIn = it }, singleLine = true,
                    placeholder = { Text("Название", color = Mut) }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                if (collabMsg.isNotEmpty()) Text(collabMsg, color = Mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = newCollabIn.trim()
                if (name.isEmpty()) return@TextButton
                lsc.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Collab.create(name, Auth.userId().orEmpty()) } }
                    r.onSuccess {
                        newCollab = false
                        newCollabIn = ""
                        collabMsg = ""
                        collabs = withContext(Dispatchers.IO) { runCatching { Collab.list() }.getOrDefault(emptyList()) }
                    }.onFailure { collabMsg = "Не удалось создать. Проверьте связь" }
                }
            }) { Text("Создать", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { newCollab = false }) { Text("Отмена", color = Mut) } },
    )

    if (findNick) AlertDialog(
        onDismissRequest = { findNick = false },
        title = { Text("Найти человека") },
        text = {
            Column {
                OutlinedTextField(
                    value = findIn, onValueChange = { findIn = it }, singleLine = true,
                    placeholder = { Text("ник без @", color = Mut) }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                        focusedTextColor = TextC, unfocusedTextColor = TextC,
                    ),
                )
                if (findMsg.isNotEmpty()) Text(findMsg, color = Mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                lsc.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Social.findByNick(findIn) } }
                    r.onSuccess { p ->
                        if (p == null) findMsg = "Такого ника нет" else {
                            findNick = false
                            findMsg = ""
                            findIn = ""
                            onOpen(Detail.User(p.uid, p.nick))
                        }
                    }.onFailure { findMsg = "Нет связи с сервером" }
                }
            }) { Text("Найти", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { findNick = false }) { Text("Отмена", color = Mut) } },
    )

    if (showNew) AlertDialog(
        onDismissRequest = { showNew = false },
        title = { Text("Новый плейлист") },
        text = {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, singleLine = true,
                placeholder = { Text("Название", color = Mut) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (Playlists.create(newName)) { showNew = false; newName = "" } }) { Text("Создать", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { showNew = false }) { Text("Отмена", color = Mut) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = TextC,
    )
}

// ───────────────────────── Страница артиста / альбома / плейлиста / любимого ─────────────────────────

private class Info(
    val title: String, val sub: String, val tracks: List<Track>, val seed: String,
    val round: Boolean, val icon: ImageVector?, val followed: Boolean?, val onFollow: () -> Unit,
)

@Composable
private fun DetailScreen(d: Detail, cat: Catalog, ps: PlayerUi, c: MediaController?, onBack: () -> Unit, onOpen: (Detail) -> Unit = {}) {
    val favs = Favorites.ids
    val dctx = LocalContext.current
    val dscope = rememberCoroutineScope()
    // Публичный плейлист: при открытии отправляем текущие треки
    LaunchedEffect(d) {
        if (d is Detail.Playlist && PubPlaylists.isPublic(d.name)) {
            withContext(Dispatchers.IO) { runCatching { PubPlaylists.push(d.name, Playlists.lists[d.name].orEmpty()) } }
        }
    }
    val info = when (d) {
        Detail.Liked -> {
            val ts = cat.tracks.filter { it.path in favs }
            Info("Любимые треки", plural(ts.size), ts, "liked", false, Icons.Filled.Favorite, null) {}
        }
        is Detail.PubPl -> {
            val ts = d.tracks.mapNotNull { p -> cat.tracks.firstOrNull { it.path == p } }
            Info(d.name, "Публичный плейлист · ${plural(ts.size)}", ts, "pub:${d.id}", false, Icons.Filled.LibraryMusic, Follows.isPl(d.id)) { Follows.togglePl(d.id) }
        }
        Detail.Wish -> {
            // Если трек уже есть в библиотеке сайта, играем полную версию
            val ts = Wishes.list.map { w -> cat.tracks.firstOrNull { wishKey(it.artist + " " + it.title) == wishKey(w.a + " " + w.t) } ?: Wishes.toTrack(w) }
            val onSite = ts.count { !it.preview }
            Info("Хочу послушать", "${plural(ts.size)} · на сайте: $onSite", ts, "wish", false, Icons.Filled.Add, null) {}
        }
        is Detail.Artist -> {
            val ts = cat.artist(d.name)
            Info(d.name, "Артист · ${plural(ts.size)}", ts, d.name, true, null, d.name in Subs.artists) { Subs.toggleArtist(d.name) }
        }
        is Detail.Album -> {
            val a = cat.album(d.name)
            val ts = a?.tracks ?: emptyList()
            Info(d.name, "Альбом · ${a?.artist ?: ""} · ${plural(ts.size)}", ts, d.name, false, null, d.name in Subs.albums) { Subs.toggleAlbum(d.name) }
        }
        is Detail.Playlist -> {
            val ts = playlistTracks(cat, d.name)
            Info(d.name, "Плейлист · ${plural(ts.size)}", ts, d.name, false, Icons.Filled.LibraryMusic, null) {}
        }
        else -> Info("", "", emptyList(), "", false, null, null) {}
    }
    val artistAlbums = if (d is Detail.Artist) cat.albums.filter { al -> al.tracks.any { it.artist == d.name } } else emptyList()

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
                }
                Cover(info.seed, 180.dp, info.round, info.icon, img = info.tracks.firstOrNull { it.img.isNotBlank() }?.img.orEmpty())
                Spacer(Modifier.height(16.dp))
                Text(info.title, color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(info.sub, color = Mut, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (info.followed != null) {
                        OutlinedButton(
                            onClick = info.onFollow,
                            border = BorderStroke(1.dp, if (info.followed) Acc else Line),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (info.followed) Acc else TextC),
                        ) { Text(if (info.followed) "Вы подписаны" else "Подписаться") }
                    }
                    if (d is Detail.Playlist) {
                        val pub = PubPlaylists.isPublic(d.name)
                        TextButton(onClick = {
                            val keys = Playlists.lists[d.name].orEmpty()
                            dscope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { PubPlaylists.set(d.name, !pub, keys) } }
                                if (r.isFailure) Toast.makeText(dctx, "Не удалось. Проверьте SQL для публичных плейлистов и связь", Toast.LENGTH_LONG).show()
                            }
                        }) { Text(if (pub) "Публичный" else "Сделать публичным", color = Acc) }
                        TextButton(onClick = { Playlists.delete(d.name); onBack() }) { Text("Удалить", color = Mut) }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { c?.playTracks(info.tracks, Random.nextInt(info.tracks.size.coerceAtLeast(1)), true) }) {
                        Icon(Icons.Filled.Shuffle, "Перемешать", tint = Mut)
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Acc).clickable { c?.playTracks(info.tracks, 0, false) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, "Играть", tint = OnAcc, modifier = Modifier.size(30.dp)) }
                }
            }
        }
        if (artistAlbums.isNotEmpty()) {
            item { SectionTitle("Альбомы") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artistAlbums, key = { it.name }) { a -> AlbumCard(a) { onOpen(Detail.Album(a.name)) } }
                }
            }
        }
        if (info.tracks.isEmpty()) {
            item {
                Text(
                    if (d is Detail.Playlist) "В плейлисте пока пусто. Добавляйте треки кнопкой ⋮ рядом с треком."
                    else "Здесь пока пусто",
                    color = Mut, modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item { SectionTitle("Треки") }
            itemsIndexed(info.tracks, key = { _, t -> t.path }) { i, t ->
                TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(info.tracks, i) }
            }
        }
    }
}

// ───────────────────────── общие элементы ─────────────────────────

/** Обложка-заглушка: градиент от названия + иконка или первая буква. Реальные обложки — следующим шагом. */
@Composable
internal fun Cover(seed: String, size: Dp, round: Boolean = false, icon: ImageVector? = null, square: Boolean = false, img: String = "") {
    val hue = remember(seed) { ((seed.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat() }
    val shape = when {
        round -> CircleShape
        square -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(if (size > 80.dp) 10.dp else 6.dp)
    }
    val brush = Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.22f)))
    Box(Modifier.size(size).clip(shape).background(brush), contentAlignment = Alignment.Center) {
        if (img.isNotBlank()) {
            RemoteImage(img, Modifier.fillMaxSize())
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.45f))
        } else {
            Text(seed.trim().take(1).uppercase(), color = Color.White.copy(alpha = 0.9f), fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewestDialog(tracks: List<Track>, c: MediaController?, onClose: () -> Unit) {
    val list = remember(tracks) { tracks.takeLast(50).reversed() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
                Text("Новое в хранилище", color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(list, key = { _, t -> "n:" + t.path }) { i, t ->
                    TrackRow(t, playing = false) { c?.playTracks(list, i) }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(list: List<AlbumInfo>, onOpen: (AlbumInfo) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        list.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { a ->
                    Column(Modifier.weight(1f).clickable { onOpen(a) }) {
                        Cover(a.name, 170.dp, square = true, img = a.img)
                        Spacer(Modifier.height(6.dp))
                        Text(a.name, color = TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(a.artist, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradTile(title: String, sub: String, colors: List<Color>, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(96.dp).clip(ShapeCard).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(12.dp),
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun LibRow(title: String, sub: String, seed: String, round: Boolean = false, icon: ImageVector? = null, img: String = "", onClick: () -> Unit) =
    Appear { LibRowInner(title, sub, seed, round, icon, img, onClick) }

@Composable
private fun LibRowInner(title: String, sub: String, seed: String, round: Boolean = false, icon: ImageVector? = null, img: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(seed, 52.dp, round, icon, img = img)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(sub, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit) =
    Appear { TrackRowInner(t, playing, onClick) }

@Composable
private fun TrackRowInner(t: Track, playing: Boolean, onClick: () -> Unit) {
    val rowSrc = remember { MutableInteractionSource() }
    val rowPressed by rowSrc.collectIsPressedAsState()
    val rowScale by animateFloatAsState(if (rowPressed) 0.98f else 1f, label = "rowPress")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = rowScale; scaleY = rowScale }
            .clickable(interactionSource = rowSrc, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(t.album.ifBlank { t.artist }, 48.dp, img = t.img)
            if (playing) {
                val eq = rememberInfiniteTransition(label = "eq")
                val eqScale by eq.animateFloat(
                    initialValue = 0.85f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "eqs",
                )
                Icon(Icons.Filled.GraphicEq, null, tint = Acc,
                    modifier = Modifier.size(24.dp).graphicsLayer { scaleX = eqScale; scaleY = eqScale })
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val titleColor by animateColorAsState(if (playing) Acc else TextC, label = "titleColor")
            Text(t.title, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(t.artist, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        }
        if (t.preview) {
            val wished = Wishes.has(t.path.removePrefix("pv:"))
            IconButton(onClick = { Wishes.toggle(t) }) {
                Icon(if (wished) Icons.Filled.Check else Icons.Filled.Add, "Хочу этот трек", tint = if (wished) Acc else Mut)
            }
        }
        if (!t.preview && !t.path.startsWith("ym:")) {
            LikeButton(t.path)
            IconButton(onClick = { Playlists.pendingTrack = t.path }) { Icon(Icons.Filled.MoreVert, "В плейлист", tint = Mut) }
        }
    }
}

// ───────────────────────── Профиль человека ─────────────────────────

@Composable
private fun UserScreen(d: Detail.User, onBack: () -> Unit, onOpen: (Detail) -> Unit) {
    var person by remember(d) { mutableStateOf<Social.Person?>(null) }
    var lists by remember(d) { mutableStateOf<List<Social.PubPl>>(emptyList()) }
    var loading by remember(d) { mutableStateOf(true) }
    var error by remember(d) { mutableStateOf("") }

    LaunchedEffect(d.uid) {
        loading = true
        error = ""
        val r = withContext(Dispatchers.IO) { runCatching { Social.person(d.uid) to Social.playlistsOf(d.uid) } }
        r.onSuccess { (p, pls) -> person = p; lists = pls }
            .onFailure { error = it.message ?: "Не удалось загрузить" }
        loading = false
    }

    val me = Auth.userId() == d.uid
    val followed = Follows.isUser(d.uid)
    val nick = person?.nick?.takeIf { it.isNotEmpty() } ?: d.name.takeIf { it.isNotEmpty() }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarCircle(person?.avatar.orEmpty(), 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(nick?.let { "@$it" } ?: "Пользователь", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!loading) Text("${lists.size} плейлистов · ${lists.sumOf { it.tracks.size }} треков", color = Mut, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                if (me) {
                    Text("Это вы", color = Mut, fontSize = 14.sp)
                } else {
                    OutlinedButton(
                        onClick = { Follows.toggleUser(d.uid) },
                        border = BorderStroke(1.dp, if (followed) Acc else Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (followed) Acc else TextC),
                    ) { Text(if (followed) "Вы подписаны" else "Подписаться") }
                    TextButton(onClick = { onOpen(Detail.Thread(d.uid, nick.orEmpty())) }) { Text("Написать", color = Acc) }
                }
            }
        }
        if (error.isNotEmpty()) item { Text(error, color = Mut, modifier = Modifier.padding(16.dp)) }
        if (!loading && error.isEmpty() && lists.isEmpty()) {
            item { Text("Публичных плейлистов пока нет", color = Mut, modifier = Modifier.padding(16.dp)) }
        }
        items(lists, key = { "upl:${it.id}" }) { p ->
            LibRow(p.name, "Публичный плейлист · ${plural(p.tracks.size)}", "upl:${p.id}", icon = Icons.Filled.LibraryMusic) {
                onOpen(Detail.PubPl(p.id, p.name, p.tracks))
            }
        }
    }
}

// ───────────────────────── Яндекс: альбом и исполнитель ─────────────────────────

@Composable
private fun YmDetailScreen(d: Detail, ps: PlayerUi, c: MediaController?, onBack: () -> Unit, onOpen: (Detail) -> Unit) {
    var tracks by remember(d) { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember(d) { mutableStateOf<List<YmAlbum>>(emptyList()) }
    var loading by remember(d) { mutableStateOf(true) }
    var error by remember(d) { mutableStateOf("") }

    LaunchedEffect(d) {
        loading = true
        error = ""
        val res = withContext(Dispatchers.IO) {
            runCatching {
                when (d) {
                    is Detail.YmAlbumPage -> Pair(Ym.album(d.id).second.map { Ym.asTrack(it) }, emptyList<YmAlbum>())
                    is Detail.YmArtistPage -> Ym.artist(d.id).let { (_, ts, als) -> Pair(ts.map { Ym.asTrack(it) }, als) }
                    else -> Pair(emptyList<Track>(), emptyList<YmAlbum>())
                }
            }
        }
        res.onSuccess { (t, a) -> tracks = t; albums = a }
            .onFailure { error = it.message ?: "Не удалось загрузить" }
        loading = false
    }

    val title = when (d) {
        is Detail.YmAlbumPage -> d.title
        is Detail.YmArtistPage -> d.name
        else -> ""
    }
    val sub = when (d) {
        is Detail.YmAlbumPage -> "Альбом · ${d.artist}"
        else -> "Исполнитель · Яндекс Музыка"
    }
    val id = when (d) {
        is Detail.YmAlbumPage -> d.id
        is Detail.YmArtistPage -> d.id
        else -> ""
    }
    val kind = if (d is Detail.YmArtistPage) "artist" else "album"
    val img = when (d) {
        is Detail.YmAlbumPage -> d.img
        is Detail.YmArtistPage -> d.img
        else -> ""
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
                }
                Cover(title, 180.dp, round = d is Detail.YmArtistPage, img = img)
                Spacer(Modifier.height(16.dp))
                Text(title, color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub, color = Mut, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val subbed = Subs.isYm(kind, id)
                    OutlinedButton(
                        onClick = {
                            Subs.toggleYm(YmSub(kind, id, title, if (d is Detail.YmAlbumPage) d.artist else "", img))
                        },
                        border = BorderStroke(1.dp, if (subbed) Acc else Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (subbed) Acc else TextC),
                    ) { Text(if (subbed) "Вы подписаны" else "Подписаться") }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Acc)
                            .clickable { if (tracks.isNotEmpty()) c?.playTracks(tracks, 0, false) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, "Играть", tint = OnAcc, modifier = Modifier.size(30.dp)) }
                }
            }
        }
        if (loading) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Acc)
            }
        }
        if (error.isNotEmpty()) item { Text(error, color = Mut, modifier = Modifier.padding(16.dp)) }
        if (albums.isNotEmpty()) {
            item { SectionTitle("Альбомы") }
            items(albums, key = { "ya:" + it.id }) { a ->
                LibRow(a.title, "Альбом · ${a.artist}" + (if (a.year.isNotBlank()) " · ${a.year}" else ""), a.title, img = a.img) {
                    onOpen(Detail.YmAlbumPage(a.id, a.title, a.artist, a.img))
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { SectionTitle("Треки") }
            itemsIndexed(tracks, key = { _, t -> t.path }) { i, t ->
                TrackRow(t, playing = ps.mediaId == t.path) { c?.playTracks(tracks, i) }
            }
        }
    }
}


// ───────────────────────── Сообщения ─────────────────────────

@Composable
private fun ChatTop(title: String, onBack: () -> Unit, onCall: (() -> Unit)? = null, onVideo: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
        Text(title, color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (onCall != null) IconButton(onClick = onCall) { Icon(Icons.Filled.Call, "Позвонить", tint = Mut) }
        if (onVideo != null) IconButton(onClick = onVideo) { Icon(Icons.Filled.Videocam, "Видеозвонок", tint = Mut) }
    }
}

@Composable
private fun InboxScreen(onBack: () -> Unit, onOpen: (Detail) -> Unit) {
    var dialogs by remember { mutableStateOf<List<Chat.Dialog>>(emptyList()) }
    var names by remember { mutableStateOf<Map<String, Social.Person>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val d = Chat.inbox()
                    d to Social.peopleByIds(d.map { it.peer }).associateBy { p -> p.uid }
                }
            }
            r.onSuccess { (d, n) -> dialogs = d; names = n; error = "" }
                .onFailure { error = it.message ?: "Не удалось загрузить" }
            loaded = true
            delay(15_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ChatTop("Сообщения", onBack)
        if (error.isNotEmpty()) Text(error, color = Mut, modifier = Modifier.padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (loaded && dialogs.isEmpty() && error.isEmpty()) {
                item { Text("Пока нет сообщений. Напишите тому, кто в подписках, или найдите человека по нику.", color = Mut, modifier = Modifier.padding(16.dp)) }
            }
            items(dialogs, key = { it.peer }) { d ->
                val p = names[d.peer]
                val who = p?.nick?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Без ника"
                val last = d.last?.let { m -> if (m.body.isNotBlank()) m.body else if (m.track.isNotBlank()) "Вложение" else "" } ?: "Нет сообщений"
                val sub = if (d.unread > 0) "$last · новых: ${d.unread}" else last
                LibRow(who, sub, "chat:${d.peer}", round = true) { onOpen(Detail.Thread(d.peer, p?.nick.orEmpty())) }
            }
        }
    }
}

/** Вложения, которые уже не нужно показывать текстом: подпись по умолчанию от сайта или приложения. */
private val CHAT_PLACEHOLDERS = setOf("🎤 Голосовое сообщение", "📷 Фото", "🎬 Видео", "⭕ Видеокружок")

@Composable
private fun ThreadScreen(
    d: Detail.Thread, onBack: () -> Unit, tracks: List<Track>, ps: PlayerUi, controller: MediaController?,
) {
    val me = Auth.userId().orEmpty()
    val ctx = LocalContext.current
    var nick by remember(d) { mutableStateOf(d.name) }
    var msgs by remember(d) { mutableStateOf<List<Chat.Msg>>(emptyList()) }
    var reacts by remember(d) { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var selected by remember(d) { mutableStateOf<String?>(null) }
    var text by remember(d) { mutableStateOf("") }
    var error by remember(d) { mutableStateOf("") }
    var rec by remember(d) { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember(d) { mutableStateOf<File?>(null) }
    var recStart by remember(d) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    // Имя собеседника — один раз
    LaunchedEffect(d.uid) {
        val p = withContext(Dispatchers.IO) { runCatching { Social.person(d.uid) }.getOrNull() }
        if (p != null && p.nick.isNotEmpty()) nick = p.nick
    }

    // Переписка обновляется каждые 4 секунды, пока экран открыт
    LaunchedEffect(d.uid) {
        while (true) {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val m = Chat.thread(d.uid)
                    if (m.any { it.recipient == me && it.readAt == null }) Chat.markRead(d.uid)
                    m to Chat.reactions(m.map { it.id })
                }
            }
            r.onSuccess { (m, rx) -> msgs = m; reacts = rx; error = "" }
                .onFailure { error = it.message ?: "Не удалось загрузить" }
            delay(4_000)
        }
    }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1)
    }

    // Запись не должна остаться включённой при выходе с экрана
    DisposableEffect(d) {
        onDispose { runCatching { rec?.release() } }
    }

    fun react(id: String, e: String) {
        val remove = reacts[id]?.get(me) == e
        val base = (reacts[id] ?: emptyMap()).toMutableMap()
        if (remove) base.remove(me) else base[me] = e
        reacts = reacts + (id to base)
        selected = null
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { Chat.setReaction(id, e, remove) } }
        }
    }

    fun sendAttachment(kind: String, body: String, name: String, bytes: ByteArray, mime: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val tr = Chat.upload(kind, name, bytes, mime)
                    Chat.send(d.uid, body, tr)
                }
            }
            r.onSuccess { m -> msgs = msgs + m; error = "" }
                .onFailure { error = it.message ?: "Не отправлено" }
        }
    }

    fun sendTrack(path: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Chat.send(d.uid, "", path) } }
            r.onSuccess { m -> msgs = msgs + m; error = "" }
                .onFailure { error = "Не отправлено" }
        }
    }

    fun startRec() {
        val f = File(ctx.cacheDir, "voice-rec.m4a")
        @Suppress("DEPRECATION")
        val r = MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            recFile = f
            recStart = System.currentTimeMillis()
        } catch (e: Exception) {
            r.release()
            error = "Не удалось начать запись"
        }
    }

    fun stopRec() {
        val r = rec ?: return
        val f = recFile
        rec = null
        val sec = (System.currentTimeMillis() - recStart) / 1000
        try {
            r.stop()
        } catch (e: Exception) {
            r.release()
            error = "Слишком короткое сообщение"
            return
        }
        r.release()
        if (f == null || sec < 1) {
            error = "Слишком короткое сообщение"
            return
        }
        sendAttachment("voice", "🎤 Голосовое сообщение", "v${System.currentTimeMillis()}.m4a", f.readBytes(), "audio/mp4")
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else error = "Нет доступа к микрофону"
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val caption = text.trim()
            val r = withContext(Dispatchers.IO) { runCatching { readMedia(ctx, uri) } }
            r.onSuccess { (bytes, mime, ext) ->
                text = ""
                val isPhoto = mime.startsWith("image/")
                val body = caption.ifEmpty { if (isPhoto) "📷 Фото" else "🎬 Видео" }
                sendAttachment(
                    "media", body, "m${System.currentTimeMillis()}.$ext", bytes, mime,
                )
            }.onFailure { error = it.message ?: "Не удалось открыть файл" }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
        focusedTextColor = TextC, unfocusedTextColor = TextC,
    )
    val curTrack = ps.mediaId?.takeIf { it.startsWith(BuildConfig.GH_FOLDER + "/") }
    var showCircle by remember(d) { mutableStateOf(false) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.CAMERA] == true) showCircle = true else error = "Нет доступа к камере"
    }

    Column(Modifier.fillMaxSize()) {
        if (showCircle) Dialog(
            onDismissRequest = { showCircle = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CircleRecorder(onFinished = { f ->
                showCircle = false
                if (f != null && f.length() > 0) {
                    val bytes = f.readBytes()
                    if (bytes.size > 25 * 1024 * 1024) error = "Кружок слишком большой"
                    else sendAttachment("circle", "⭕ Видеокружок", "c${System.currentTimeMillis()}.mp4", bytes, "video/mp4")
                }
            })
        }
        ChatTop(if (nick.isNotEmpty()) "@$nick" else "Без ника", onBack, onCall = { Calls.start(d.uid, false, nick) }, onVideo = { Calls.start(d.uid, true, nick) })
        if (error.isNotEmpty()) Text(error, color = Mut, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(msgs, key = { it.id }) { m ->
                ChatBubble(
                    m = m,
                    mine = m.sender == me,
                    rx = reacts[m.id] ?: emptyMap(),
                    selected = selected == m.id,
                    onTap = { selected = if (selected == m.id) null else m.id },
                    onReact = { e -> react(m.id, e) },
                    tracks = tracks,
                    controller = controller,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pickFile.launch("*/*") }) { Icon(Icons.Filled.AttachFile, "Фото или видео", tint = Mut) }
            IconButton(onClick = {
                if (rec != null) stopRec()
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }) {
                Icon(if (rec != null) Icons.Filled.Stop else Icons.Filled.Mic, "Голосовое", tint = if (rec != null) Acc else Mut)
            }
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCircle = true
                else camPerm.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }) { Icon(Icons.Filled.Videocam, "Видеокружок", tint = Mut) }
            IconButton(enabled = curTrack != null, onClick = { curTrack?.let { sendTrack(it) } }) {
                Icon(Icons.Filled.MusicNote, "Отправить текущий трек", tint = if (curTrack != null) Mut else Line)
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, maxLines = 4,
                placeholder = { Text("Сообщение", color = Mut) }, modifier = Modifier.weight(1f), colors = fieldColors,
            )
            val sendSrc = remember { MutableInteractionSource() }
            val sendPressed by sendSrc.collectIsPressedAsState()
            val sendScale by animateFloatAsState(if (sendPressed) 0.85f else 1f, label = "send")
            IconButton(
                enabled = text.isNotBlank(),
                interactionSource = sendSrc,
                onClick = {
                    val t = text.trim()
                    text = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Chat.send(d.uid, t) } }
                        r.onSuccess { m -> msgs = msgs + m; error = "" }
                            .onFailure { error = "Не отправлено"; text = t }
                    }
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, "Отправить",
                    tint = if (text.isNotBlank()) Acc else Mut,
                    modifier = Modifier.graphicsLayer { scaleX = sendScale; scaleY = sendScale },
                )
            }
        }
    }
}

/** Фото, видео или файл для отправки: (байты, mime, расширение). Фото сжимаются до 1600 px, как на сайте. */
private fun readMedia(ctx: Context, uri: Uri): Triple<ByteArray, String, String> {
    val mime = ctx.contentResolver.getType(uri) ?: ""
    return when {
        mime.startsWith("image/") -> {
            val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                ?: throw RuntimeException("Не удалось открыть фото")
            val k = minOf(1f, 1600f / maxOf(src.width, src.height))
            val bmp = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            Triple(out.toByteArray(), "image/jpeg", "jpg")
        }
        mime.startsWith("video/") -> {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (bytes.size > 25 * 1024 * 1024) throw RuntimeException("Видео больше 25 МБ. Выберите покороче")
            val ext = when {
                mime.contains("quicktime") -> "mov"
                mime.contains("webm") -> "webm"
                else -> "mp4"
            }
            Triple(bytes, mime, ext)
        }
        else -> throw RuntimeException("Можно отправлять только фото и видео")
    }
}

@Composable
private fun ChatBubble(
    m: Chat.Msg, mine: Boolean, rx: Map<String, String>, selected: Boolean,
    onTap: () -> Unit, onReact: (String) -> Unit, tracks: List<Track>, controller: MediaController?,
) = Appear { ChatBubbleInner(m, mine, rx, selected, onTap, onReact, tracks, controller) }

@Composable
private fun ChatBubbleInner(
    m: Chat.Msg, mine: Boolean, rx: Map<String, String>, selected: Boolean,
    onTap: () -> Unit, onReact: (String) -> Unit, tracks: List<Track>, controller: MediaController?,
) {
    val ctx = LocalContext.current
    val kind = m.track.substringBefore('/', "")
    val url = if (kind == "voice" || kind == "media" || kind == "circle") Chat.url(m.track) else ""
    val fg = if (mine) OnAcc else TextC
    val caption = if (m.body.isNotBlank() && m.body !in CHAT_PLACEHOLDERS) m.body else ""
    val isTrack = m.track.startsWith(BuildConfig.GH_FOLDER + "/")
    val isText = kind.isEmpty() && !isTrack || kind == "call" || kind == "jam"

    fun open(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier.clip(ShapeCard).background(if (mine) Acc else Hover)
                .clickable(onClick = onTap).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when {
                kind == "voice" -> VoiceBubble(url, fg)
                kind == "media" && m.track.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") ->
                    PhotoBubble(url) { open(url) }
                kind == "media" -> FileLine("🎬 Видео", fg) { open(url) }
                kind == "circle" -> FileLine("⭕ Видеокружок", fg) { open(url) }
                isTrack -> {
                    val t = tracks.firstOrNull { it.path == m.track }
                    Row(
                        Modifier.clickable(enabled = t != null) { t?.let { controller?.playTracks(listOf(it), 0) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = fg, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t?.let { "${it.title} · ${it.artist}" } ?: "Трек из библиотеки",
                            color = fg, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                isText -> Text(m.body, color = fg, fontSize = 15.sp)
            }
            if (caption.isNotEmpty() && !isText) {
                Text(caption, color = fg, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        val counts = rx.values.groupingBy { it }.eachCount()
        if (counts.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp)) {
                counts.forEach { (e, c) ->
                    Text(if (c > 1) "$e $c" else e, color = TextC, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
        if (selected) {
            Row(Modifier.padding(top = 2.dp)) {
                Chat.EMOJI.forEach { e ->
                    Text(e, fontSize = 22.sp, modifier = Modifier.clickable { onReact(e) }.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun FileLine(label: String, fg: Color, onClick: () -> Unit) {
    Text(label, color = fg, fontSize = 15.sp, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable(onClick = onClick))
}

/** Голосовое: кнопка «играть/пауза» (ExoPlayer, уже есть в проекте). */
@Composable
private fun VoiceBubble(url: String, fg: Color) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    var playing by remember(url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : Exo.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(36.dp)) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Слушать", tint = fg)
        }
        Text("Голосовое", color = fg, fontSize = 14.sp)
    }
}

/** Фото: загружается по ссылке, нажатие открывает в браузере. */
@Composable
private fun PhotoBubble(url: String, onOpen: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    val b = bmp
    Box(
        Modifier.size(220.dp).clip(ShapeCard).background(Line).clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("Загружаем фото…", color = Mut, fontSize = 13.sp)
    }
}

// ───────────────────────── Совместный плейлист ─────────────────────────

private fun wishKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

@Composable
private fun CollabScreen(d: Detail.Collab, cat: Catalog, ps: PlayerUi, c: MediaController?, onBack: () -> Unit) {
    val me = Auth.userId().orEmpty()
    val owner = d.owner == me
    val scope = rememberCoroutineScope()
    var name by remember(d) { mutableStateOf(d.name) }
    var keys by remember(d) { mutableStateOf<List<String>>(emptyList()) }
    var members by remember(d) { mutableStateOf<List<String>>(emptyList()) }
    var people by remember(d) { mutableStateOf<Map<String, Social.Person>>(emptyMap()) }
    var loaded by remember(d) { mutableStateOf(false) }
    var error by remember(d) { mutableStateOf("") }
    var showMembers by remember(d) { mutableStateOf(false) }
    var showRename by remember(d) { mutableStateOf(false) }
    var confirmGone by remember(d) { mutableStateOf(false) }
    var renameIn by remember(d) { mutableStateOf("") }
    var nickIn by remember(d) { mutableStateOf("") }
    var memberMsg by remember(d) { mutableStateOf("") }

    val byPath = cat.tracks.associateBy { it.path }
    val curTrack = ps.mediaId?.takeIf { it.startsWith(BuildConfig.GH_FOLDER + "/") }

    fun reload() {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val k = Collab.tracks(listOf(d.id))[d.id].orEmpty()
                    val m = Collab.members(d.id)
                    Triple(k, m, Social.peopleByIds((m + d.owner).distinct()))
                }
            }
            r.onSuccess { (k, m, p) ->
                keys = k
                members = m
                people = p.associateBy { it.uid }
                loaded = true
                error = ""
            }.onFailure {
                loaded = true
                error = it.message ?: "Не удалось загрузить"
            }
        }
    }

    fun work(action: () -> Unit, done: () -> Unit = {}) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(action) }
            r.onSuccess { done() }.onFailure { error = it.message ?: "Не удалось. Проверьте связь" }
        }
    }

    LaunchedEffect(d.id) {
        while (true) {
            reload()
            delay(15_000)
        }
    }

    fun nickOf(uid: String): String = if (uid == me) "вы" else people[uid]?.nick?.takeIf { it.isNotEmpty() } ?: "без ника"

    Column(Modifier.fillMaxSize()) {
        ChatTop(name, onBack)
        Text(
            if (owner) "Совместный · вы владелец" else "Совместный · участник",
            color = Mut, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (curTrack != null) {
                TextButton(onClick = { work({ Collab.addTrack(d.id, curTrack) }, ::reload) }) { Text("+ текущий", color = Acc) }
            }
            TextButton(onClick = { memberMsg = ""; showMembers = true }) { Text("Участники", color = Acc) }
            if (owner) TextButton(onClick = { renameIn = name; showRename = true }) { Text("Название", color = Acc) }
            TextButton(onClick = { confirmGone = true }) { Text(if (owner) "Удалить" else "Выйти", color = Mut) }
        }
        if (error.isNotEmpty()) Text(error, color = Mut, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (loaded && keys.isEmpty()) {
                item { Text("В плейлисте пока нет треков. Добавьте текущий трек кнопкой выше.", color = Mut, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(keys, key = { _, k -> k }) { i, k ->
                val t = byPath[k]
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = t != null) {
                        val list = keys.mapNotNull { byPath[it] }
                        c?.playTracks(list, list.indexOfFirst { it.path == k }.coerceAtLeast(0))
                    }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t?.title ?: k.substringAfterLast('/'), color = if (t != null) TextC else Mut,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                        )
                        Text(t?.artist ?: "Нет в библиотеке", color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    IconButton(onClick = { work({ Collab.removeTrack(d.id, k) }, ::reload) }) {
                        Icon(Icons.Filled.Close, "Убрать", tint = Mut)
                    }
                }
            }
        }
    }

    if (showMembers) AlertDialog(
        onDismissRequest = { showMembers = false },
        title = { Text("Участники") },
        text = {
            Column {
                Text("Владелец: @${nickOf(d.owner)}", color = TextC, fontSize = 14.sp)
                members.forEach { uid ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${nickOf(uid)}", color = TextC, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (owner) IconButton(onClick = { work({ Collab.removeMember(d.id, uid) }, ::reload) }) {
                            Icon(Icons.Filled.Close, "Убрать", tint = Mut)
                        }
                    }
                }
                if (owner) {
                    OutlinedTextField(
                        value = nickIn, onValueChange = { nickIn = it }, singleLine = true,
                        placeholder = { Text("ник друга", color = Mut) }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                            focusedTextColor = TextC, unfocusedTextColor = TextC,
                        ),
                    )
                    TextButton(onClick = {
                        val n = nickIn.trim().lowercase().removePrefix("@")
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching {
                                    val p = Social.findByNick(n) ?: throw RuntimeException("Такого ника нет")
                                    if (p.uid == me) throw RuntimeException("Это вы")
                                    Collab.addMember(d.id, p.uid)
                                    p
                                }
                            }
                            r.onSuccess { p ->
                                nickIn = ""
                                memberMsg = "@${p.nick} добавлен(а)"
                                reload()
                            }.onFailure { memberMsg = it.message ?: "Не удалось добавить" }
                        }
                    }) { Text("Добавить", color = Acc) }
                    if (memberMsg.isNotEmpty()) Text(memberMsg, color = Mut, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMembers = false }) { Text("Готово", color = Acc) } },
    )

    if (showRename) AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text("Название") },
        text = {
            OutlinedTextField(
                value = renameIn, onValueChange = { renameIn = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Acc, unfocusedBorderColor = Line, cursorColor = Acc,
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = renameIn.trim()
                if (n.isEmpty()) return@TextButton
                showRename = false
                work({ Collab.rename(d.id, n) }) { name = n }
            }) { Text("Сохранить", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { showRename = false }) { Text("Отмена", color = Mut) } },
    )

    if (confirmGone) AlertDialog(
        onDismissRequest = { confirmGone = false },
        title = { Text(if (owner) "Удалить плейлист?" else "Выйти из плейлиста?") },
        text = { Text(if (owner) "Плейлист пропадёт у всех участников." else "Вы больше не увидите этот плейлист.", color = Mut) },
        confirmButton = {
            TextButton(onClick = {
                confirmGone = false
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { if (owner) Collab.delete(d.id) else Collab.leave(d.id) } }
                    r.onSuccess { onBack() }.onFailure { error = "Не удалось. Проверьте связь и попробуйте ещё раз" }
                }
            }) { Text(if (owner) "Удалить" else "Выйти", color = Acc) }
        },
        dismissButton = { TextButton(onClick = { confirmGone = false }) { Text("Отмена", color = Mut) } },
    )
}
