package app.soundlife.admin

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Экраны приложения. Вкладки (Home, Tracks, Upload, Settings) сбрасывают стек, остальные добавляются в него. */
sealed class Page {
    object Home : Page()
    object Tracks : Page()
    object Upload : Page()
    object Settings : Page()
    object Albums : Page()
    object Artists : Page()
    object Wishes : Page()
    data class AlbumView(val key: String) : Page()
    data class ArtistView(val key: String) : Page()
    data class Edit(val paths: List<String>) : Page()
    data class EditArtist(val key: String) : Page()
}

private val TABS = listOf(Page.Home, Page.Tracks, Page.Upload, Page.Settings)

@Composable
fun AdminApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var token by remember { mutableStateOf(prefs.token) }
    var repo by remember { mutableStateOf(prefs.repo) }
    if (token.isEmpty()) {
        LoginScreen { t, r ->
            prefs.token = t; prefs.repo = r
            token = t; repo = r
        }
        return
    }

    val session = Session(token, repo, BuildConfig.GH_BRANCH, BuildConfig.GH_FOLDER)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val selected = remember { mutableStateListOf<String>() }
    var lib by remember { mutableStateOf<Library?>(null) }
    var loadError by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var stack by remember { mutableStateOf<List<Page>>(listOf(Page.Home)) }

    Player.repo = repo
    Player.branch = BuildConfig.GH_BRANCH
    Player.onChange = { tick++ }

    LaunchedEffect(reload, token, repo) {
        loadError = ""
        try {
            lib = withContext(Dispatchers.IO) { Library.build(token, repo, BuildConfig.GH_BRANCH, BuildConfig.GH_FOLDER) }
        } catch (e: Exception) {
            loadError = e.message ?: "Не удалось загрузить библиотеку"
        }
    }

    val page = stack.last()
    fun go(p: Page) {
        stack = if (p in TABS) listOf(p) else stack + p
        selected.clear()
    }
    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1) else stack = listOf(Page.Home)
    }
    fun say(msg: String) {
        scope.launch { snack.showSnackbar(msg) }
    }

    BackHandler(enabled = full || stack.size > 1 || selected.isNotEmpty()) {
        when {
            full -> full = false
            selected.isNotEmpty() -> selected.clear()
            else -> back()
        }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column {
                if (!full && Player.current != null) {
                    MiniPlayer(repo, BuildConfig.GH_BRANCH, tick, onOpen = { full = true })
                }
                if (!full) {
                    NavigationBar(containerColor = Panel) {
                        val labels = listOf("Главная", "Треки", "Загрузка", "Настройки")
                        val icons = listOf(Icons.Default.Home, Icons.Default.MusicNote, Icons.Default.Upload, Icons.Default.Settings)
                        TABS.forEachIndexed { i, tab ->
                            NavigationBarItem(
                                selected = stack.size == 1 && page == tab,
                                onClick = { go(tab) },
                                icon = { Icon(icons[i], null) },
                                label = { Text(labels[i]) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Acc.copy(alpha = 0.2f),
                                    selectedIconColor = Acc,
                                    selectedTextColor = Acc,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val current = lib
            when {
                full -> FullPlayer(session, tick) { full = false }
                current == null -> {
                    if (loadError.isNotEmpty()) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(loadError, color = Bad, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { reload++ }) { Text("Повторить") }
                        }
                    } else Empty("Загружаю библиотеку…")
                }
                else -> ScreenHost(
                    page, current, session, selected, ::go, ::back, ::say, { reload++ },
                    playingPath = tick.let { Player.current?.path },
                    onLogout = { prefs.token = ""; token = "" },
                )
            }
        }
    }
}

@Composable
private fun ScreenHost(
    page: Page,
    lib: Library,
    s: Session,
    selected: SnapshotStateList<String>,
    go: (Page) -> Unit,
    back: () -> Unit,
    say: (String) -> Unit,
    reloadLib: () -> Unit,
    playingPath: String?,
    onLogout: () -> Unit,
) {
    val branch = s.branch
    val scope = rememberCoroutineScope()
    when (page) {
        Page.Home -> HomeScreen(
            lib, s.repo, branch,
            onTracks = { go(Page.Tracks) },
            onAlbums = { go(Page.Albums) },
            onArtists = { go(Page.Artists) },
            onWishes = { go(Page.Wishes) },
            onAlbum = { go(Page.AlbumView(it.key)) },
            onArtist = { go(Page.ArtistView(it.key)) },
            onShuffle = { Player.play(lib.tracks, 0, shuffle = true) },
        )
        Page.Tracks -> TracksScreen(
            lib, s.repo, branch, selected,
            onEdit = { list -> go(Page.Edit(list.map { it.path })) },
            onPlay = { list, i -> Player.play(list, i) },
            playingPath = playingPath,
        )
        Page.Albums -> AlbumsScreen(lib, s.repo, branch) { go(Page.AlbumView(it.key)) }
        Page.Artists -> ArtistsScreen(
            lib, s.repo, branch,
            onOpen = { go(Page.ArtistView(it.key)) },
            onMerge = { cards, name ->
                scope.launch {
                    try {
                        val msg = withContext(Dispatchers.IO) { Save.setArtist(s, lib, cards.flatMap { it.tracks }, name) }
                        say(msg); reloadLib()
                    } catch (e: Exception) { say(e.message ?: "Не удалось объединить") }
                }
            },
        )
        Page.Wishes -> WishesScreen(s, lib) { msg -> say(msg); reloadLib() }
        Page.Upload -> UploadScreen(s) { msg -> say(msg); reloadLib() }
        Page.Settings -> SettingsScreen(s, onLogout)
        is Page.AlbumView -> {
            val a = lib.albums.firstOrNull { it.key == page.key }
            if (a == null) back() else CollectionScreen(
                title = a.title, sub = a.artist, cover = a.cover, round = false,
                tracks = a.tracks, repo = s.repo, branch = branch, playingPath = playingPath,
                onBack = back,
                onPlay = { list, i -> Player.play(list, i) },
                onShuffle = { Player.play(a.tracks, 0, shuffle = true) },
                onEdit = { go(Page.Edit(a.tracks.map { it.path })) },
            )
        }
        is Page.ArtistView -> {
            val a = lib.artists.firstOrNull { it.key == page.key }
            if (a == null) back() else CollectionScreen(
                title = a.name, sub = "${a.tracks.size} тр.", cover = a.img, round = true,
                tracks = a.tracks, repo = s.repo, branch = branch, playingPath = playingPath,
                onBack = back,
                onPlay = { list, i -> Player.play(list, i) },
                onShuffle = { Player.play(a.tracks, 0, shuffle = true) },
                onEdit = { go(Page.Edit(a.tracks.map { it.path })) },
                extra = {
                    OutlinedButton(onClick = { go(Page.EditArtist(a.key)) }) { Text("Карточка артиста") }
                },
            )
        }
        is Page.Edit -> {
            val targets = lib.tracks.filter { it.path in page.paths }
            if (targets.isEmpty()) back() else TrackEditorScreen(s, lib, targets) { msg ->
                if (msg != null) { say(msg); reloadLib(); selected.clear(); back() } else back()
            }
        }
        is Page.EditArtist -> {
            val card = lib.artists.firstOrNull { it.key == page.key }
            if (card == null) back() else ArtistEditorScreen(s, lib, card) { msg ->
                if (msg != null) { say(msg); reloadLib(); back() } else back()
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
        if (error.isNotEmpty()) Text(error, color = Bad, fontSize = 14.sp)
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
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
        ) { Text(if (busy) "Проверяю…" else "Войти", fontWeight = FontWeight.Bold) }
    }
}
