package app.soundlife

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** Кэш картинок в памяти (последние 120). */
private val imageCache = LruCache<String, ImageBitmap>(120)

private fun fetchImage(url: String): ImageBitmap? = try {
    imageCache.get(url) ?: run {
        val bytes = URL(url).openStream().use { it.readBytes() }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { imageCache.put(url, it) }
    }
} catch (e: Exception) {
    null
}

/** Картинка из сети; пока грузится или при ошибке — пусто (фон заглушки виден под ней). */
@Composable
internal fun RemoteImage(url: String, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = imageCache.get(url), key1 = url) {
        if (value == null) value = withContext(Dispatchers.IO) { fetchImage(url) }
    }
    val b = bmp
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
