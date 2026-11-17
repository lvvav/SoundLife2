package app.soundlife.admin

import java.net.HttpURLConnection
import java.net.URL

/**
 * Чтение ID3v2 из MP3 так же, как сайт: запрашивается только начало файла (Range),
 * разбираются кадры TPE1 (исполнитель), TIT2 (название), TALB (альбом), TRCK, TPOS, TDRC/TYER.
 */
object Tags {
    data class Info(
        val artist: String = "",
        val title: String = "",
        val album: String = "",
        val track: String = "",
        val disc: String = "",
        val year: String = "",
    )

    private const val MAX_TAG = 8_000_000

    fun read(url: String): Info? {
        val head = get(url, 0, 9) ?: return null
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return null
        val ver = head[3].toInt()
        if (ver != 3 && ver != 4) return null
        val size = synchsafe(head, 6)
        if (size <= 0 || size > MAX_TAG) return null
        val b = get(url, 0, size + 9) ?: return null

        val frames = HashMap<String, String>()
        var p = 10
        // расширенный заголовок, если он есть
        if (head[5].toInt() and 0x40 != 0) {
            p += if (ver == 4) synchsafe(b, 10) else u32(b, 10) + 4
        }
        val end = minOf(size + 10, b.size)
        while (p + 10 <= end) {
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            if (id[0].code == 0) break
            val fs = if (ver == 4) synchsafe(b, p + 4) else u32(b, p + 4)
            if (fs <= 0 || p + 10 + fs > b.size) break
            if (id[0] == 'T' && !frames.containsKey(id)) {
                frames[id] = decode(b, p + 10, fs)
            }
            p += 10 + fs
        }
        return Info(
            artist = frames["TPE1"].orEmpty(),
            title = frames["TIT2"].orEmpty(),
            album = frames["TALB"].orEmpty(),
            track = frames["TRCK"].orEmpty().substringBefore('/').trim(),
            disc = frames["TPOS"].orEmpty().substringBefore('/').trim(),
            year = (frames["TDRC"] ?: frames["TYER"]).orEmpty().take(4),
        )
    }

    private fun get(url: String, from: Int, to: Int): ByteArray? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=$from-$to")
        }
        val code = c.responseCode
        if (code != 200 && code != 206) return null
        // Если сервер отдал весь файл (200), читаем только нужный кусок и закрываем соединение
        val want = to - from + 1
        return c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < want) {
                val n = input.read(buf, 0, minOf(buf.size, want - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    /** Текст кадра: первый байт — кодировка (0 Latin-1, 1 UTF-16 с BOM, 2 UTF-16BE, 3 UTF-8). */
    private fun decode(b: ByteArray, o: Int, len: Int): String {
        if (len < 2) return ""
        val enc = b[o].toInt()
        val data = b.copyOfRange(o + 1, o + len)
        val charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> if (data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) Charsets.UTF_16BE
                 else if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) Charsets.UTF_16LE
                 else Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
        val text = String(data, charset)
        return text.replace("\u0000", "").replace("﻿", "").trim()
    }
}
