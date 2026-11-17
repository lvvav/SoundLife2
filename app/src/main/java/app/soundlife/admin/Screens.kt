package app.soundlife.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Главная: большие кнопки разделов, ряды альбомов и артистов. */
@Composable
fun HomeScreen(lib: Library, repo: String, branch: String, onTracks: () -> Unit, onAlbums: () -> Unit, onArtists: () -> Unit, onWishes: () -> Unit, onAlbum: (Album) -> Unit, onArtist: (ArtistCard) -> Unit, onShuffle: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Title("SoundLife", "Админка сайта") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigTile("Треки", "${lib.tracks.size} файлов", Icons.Default.MusicNote, onTracks)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigTile("Альбомы", "${lib.albums.size}", Icons.Default.Album, onAlbums, Modifier.weight(1f))
                    BigTile("Артисты", "${lib.artists.size}", Icons.Default.Person, onArtists, Modifier.weight(1f))
                }
                BigTile("Хотят послушать", "Заявки и решения", Icons.Default.Favorite, onWishes)
                Button(
                    onClick = onShuffle,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Acc),
                    shape = RoundedCornerShape(14.dp),
                ) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Перемешать всё", fontWeight = FontWeight.Bold) }
            }
        }
        if (lib.albums.isNotEmpty()) {
            item { SectionHeader("Альбомы", onAlbums) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(lib.albums.take(12)) { a ->
                        GridCard(a.title, a.artist, a.cover?.let { Raw.url(repo, branch, it) }, round = false) { onAlbum(a) }
                    }
                }
            }
        }
        if (lib.artists.isNotEmpty()) {
            item { SectionHeader("Артисты", onArtists) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(lib.artists.take(12)) { a ->
                        GridCard(a.name, "${a.tracks.size} тр.", a.img?.let { Raw.url(repo, branch, it) }, round = true) { onArtist(a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, null, tint = Acc, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = Mut, fontSize = 13.sp)
    }
}

@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onMore) { Text("Все", color = Acc) }
    }
}

/** Треки: поиск, нажатие играет, долгое нажатие выбирает несколько файлов для правки. */
@Composable
fun TracksScreen(lib: Library, repo: String, branch: String, selected: SnapshotStateList<String>, onEdit: (List<Track>) -> Unit, onPlay: (List<Track>, Int) -> Unit, playingPath: String?) {
    var query by remember { mutableStateOf("") }
    val shown = remember(lib, query) {
        lib.tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    }
    Column(Modifier.fillMaxSize()) {
        Title("Треки", "${shown.size} из ${lib.tracks.size}")
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Поиск по названию, исполнителю, альбому") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Выбрано: ${selected.size}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected.clear() }) { Text("Снять", color = Mut) }
                Button(
                    onClick = { onEdit(lib.tracks.filter { it.path in selected }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
                ) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Изменить", fontWeight = FontWeight.Bold) }
            }
        }
        if (shown.isEmpty()) {
            Empty("Треков нет. Загрузите файлы во вкладке «Загрузка».")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                itemsIndexed(shown, key = { _, t -> t.path }) { i, t ->
                    TrackRow(
                        t, repo, branch,
                        playing = t.path == playingPath,
                        selectable = selected.isNotEmpty(),
                        selected = t.path in selected,
                        onClick = {
                            if (selected.isNotEmpty()) toggle(selected, t.path) else onPlay(shown, i)
                        },
                        onLongClick = { toggle(selected, t.path) },
                    )
                }
            }
        }
    }
}

private fun toggle(sel: SnapshotStateList<String>, path: String) {
    if (path in sel) sel.remove(path) else sel.add(path)
}

/** Альбомы: сетка с обложками. */
@Composable
fun AlbumsScreen(lib: Library, repo: String, branch: String, onOpen: (Album) -> Unit) {
    if (lib.albums.isEmpty()) { Empty("Альбомов пока нет"); return }
    Column(Modifier.fillMaxSize()) {
        Title("Альбомы", "${lib.albums.size}")
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
            items(lib.albums) { a ->
                GridCard(a.title, a.artist, a.cover?.let { Raw.url(repo, branch, it) }, round = false) { onOpen(a) }
            }
        }
    }
}

/** Артисты: круглые карточки с фото. */
@Composable
fun ArtistsScreen(lib: Library, repo: String, branch: String, onOpen: (ArtistCard) -> Unit) {
    if (lib.artists.isEmpty()) { Empty("Артистов пока нет"); return }
    Column(Modifier.fillMaxSize()) {
        Title("Артисты", "${lib.artists.size}")
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
            items(lib.artists) { a ->
                GridCard(a.name, "${a.tracks.size} тр.", a.img?.let { Raw.url(repo, branch, it) }, round = true) { onOpen(a) }
            }
        }
    }
}

/** Экран альбома или артиста: шапка, кнопки «Играть», «Перемешать», «Изменить», список треков. */
@Composable
fun CollectionScreen(
    title: String,
    sub: String,
    cover: String?,
    round: Boolean,
    tracks: List<Track>,
    repo: String,
    branch: String,
    playingPath: String?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: () -> Unit,
    onEdit: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, top = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(cover?.let { Raw.url(repo, branch, it) }, 200, round = round, placeholder = title.firstOrNull()?.uppercase() ?: "♪")
                Spacer(Modifier.height(14.dp))
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(sub, color = Mut, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(tracks, 0) }, colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg)) {
                        Icon(Icons.Default.PlayArrow, null); Text("Играть", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, null); Text("Перемешать") }
                    if (onEdit != null) OutlinedButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Text("Изменить") }
                }
                extra()
                Spacer(Modifier.height(12.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.path }) { i, t ->
            TrackRow(t, repo, branch, playing = t.path == playingPath, onClick = { onPlay(tracks, i) })
        }
    }
}
