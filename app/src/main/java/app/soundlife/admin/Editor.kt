package app.soundlife.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Читает картинку с телефона и сжимает до 700 px (как на сайте). */
fun readCover(ctx: Context, uri: Uri): ByteArray? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / sample > 1400 || opts.outHeight / sample > 1400) sample *= 2
    val real = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) } ?: return null
    val side = minOf(bmp.width, bmp.height)
    val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(square, minOf(700, side), minOf(700, side), true)
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 88, bos)
    return bos.toByteArray()
}

/**
 * Правка одного или нескольких треков. В режиме нескольких поля пустые — значит не меняются,
 * название и текст песни доступны только для одного трека.
 */
@Composable
fun TrackEditorScreen(s: Session, lib: Library, targets: List<Track>, onDone: (String?) -> Unit) {
    val single = targets.size == 1
    val first = targets.first()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var artist by remember { mutableStateOf(if (single) first.artist else "") }
    var title by remember { mutableStateOf(if (single) first.title else "") }
    var album by remember { mutableStateOf(if (single) first.album else "") }
    var genre by remember { mutableStateOf(if (single) first.genre else "") }
    var year by remember { mutableStateOf(if (single) first.year else "") }
    var number by remember { mutableStateOf(if (single) first.number else "") }
    var disc by remember { mutableStateOf(if (single) first.disc else "") }
    var lyrics by remember { mutableStateOf(if (single) lib.lyricsFor(first) else "") }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var removeCover by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                cover = withContext(Dispatchers.IO) { runCatching { readCover(ctx, uri) }.getOrNull() }
                removeCover = false
                if (cover == null) error = "Не удалось прочитать картинку."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (single) "Трек" else "Треков: ${targets.size}",
            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (single) first.path.substringAfterLast('/') else "Пустые поля не меняются",
            color = Mut, fontSize = 13.sp,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(
                when {
                    cover != null -> null
                    removeCover -> null
                    else -> first.cover?.let { Raw.url(s.repo, s.branch, it) }
                },
                72,
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text(if (cover != null) "Фото выбрано" else "Обложка")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { cover = null; removeCover = true }) { Text("Убрать", color = Bad) }
        }

        OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { if (!single) Text("Не менять") })
        if (single) OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(album, { album = it }, label = { Text("Альбом") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { if (!single) Text("Не менять") })
        OutlinedTextField(genre, { genre = it.trim() }, label = { Text("Жанр (папка)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { if (!single) Text("Не менять") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(year, { year = it }, label = { Text("Год") }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { if (!single) Text("—") })
            OutlinedTextField(number, { number = it }, label = { Text("Трек №") }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { if (!single) Text("—") })
            OutlinedTextField(disc, { disc = it }, label = { Text("Диск") }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { if (!single) Text("—") })
        }
        if (single) {
            OutlinedTextField(
                lyrics, { lyrics = it },
                label = { Text("Текст песни") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }

        if (error.isNotEmpty()) Text(error, color = Bad)
        Button(
            onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        val msg = withContext(Dispatchers.IO) {
                            Save.tracks(
                                s, lib, targets,
                                TrackForm(
                                    artist = artist, title = title, album = album, genre = genre,
                                    year = year, number = number, disc = disc,
                                    lyrics = if (single) lyrics else null,
                                    cover = cover, removeCover = removeCover,
                                ),
                                single,
                            )
                        }
                        onDone(msg)
                    } catch (e: Exception) {
                        error = e.message ?: "Не удалось сохранить"
                    } finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
        ) { Text(if (busy) "Сохраняю…" else "Сохранить", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = Mut) }
    }
}

/** Карточка артиста: имя, описание, фото. */
@Composable
fun ArtistEditorScreen(s: Session, lib: Library, card: ArtistCard, onDone: (String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(card.name) }
    var bio by remember { mutableStateOf(card.bio.orEmpty()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) { runCatching { readCover(ctx, uri) }.getOrNull() }
            removePhoto = false
            if (photo == null) error = "Не удалось прочитать картинку."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Артист", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(
                if (removePhoto || photo != null) null else card.img?.let { Raw.url(s.repo, s.branch, it) },
                96, round = true, placeholder = card.name.firstOrNull()?.uppercase() ?: "?",
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Фото") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { photo = null; removePhoto = true }) { Text("Убрать", color = Bad) }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Имя на сайте") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth().height(160.dp))
        Text("Треков: ${card.tracks.size}", color = Mut, fontSize = 13.sp)
        if (error.isNotEmpty()) Text(error, color = Bad)
        Button(
            onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        val msg = withContext(Dispatchers.IO) {
                            Save.artistCard(s, lib, card.key, card.name, name, bio, photo, removePhoto)
                        }
                        onDone(msg)
                    } catch (e: Exception) {
                        error = e.message ?: "Не удалось сохранить"
                    } finally { busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = Bg),
        ) { Text(if (busy) "Сохраняю…" else "Сохранить", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = Mut) }
    }
}
