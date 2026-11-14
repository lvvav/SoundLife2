package app.soundlife

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
        value = imageCache.get(url)
        if (value == null) value = withContext(Dispatchers.IO) { fetchImage(url) }
    }
    val b = bmp
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}


/** Средний цвет обложки (ARGB), для фона плеера и темы. Null, если картинки нет. */
internal fun coverAverage(url: String): Long? {
    if (url.isBlank()) return null
    val bmp = fetchImage(url) ?: return null
    val small = android.graphics.Bitmap.createScaledBitmap(bmp.asAndroidBitmap(), 16, 16, true)
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (x in 0 until 16) for (y in 0 until 16) {
        val p = small.getPixel(x, y)
        r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); n++
    }
    return 0xFF000000L or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
}

/** Насыщенный вариант цвета обложки для акцента: не тусклый, яркий. */
internal fun vivid(argb: Long): Long {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt(), hsv)
    hsv[1] = maxOf(hsv[1], 0.75f)
    hsv[2] = maxOf(hsv[2], 0.95f)
    return 0xFF000000L or (android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFL)
}
