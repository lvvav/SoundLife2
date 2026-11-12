package app.soundlife

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant

/** Профиль: ник и аватарка. Таблица profiles, как на сайте (аватарка — картинка 160×160 в data-адресе). */
object Profile {
    var nick by mutableStateOf("")
        private set
    var avatar by mutableStateOf("")
        private set
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = prefs(ctx)
        nick = p.getString("pf_nick", "") ?: ""
        avatar = p.getString("pf_avatar", "") ?: ""
    }

    /** При выходе: профиль чужого аккаунта не показываем. */
    fun clear() {
        set("", "")
    }

    /** Загрузка с аккаунта. Вызывать в фоне. */
    fun load() {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/profiles?select=nick,avatar&user_id=eq.$uid", tk, null, null))
        val o = arr.optJSONObject(0)
        set(o?.str("nick").orEmpty(), o?.str("avatar").orEmpty())
    }

    /** Новый ник: латиница, цифры, _ и точка, 3–20 знаков (как на сайте). Бросает исключение с текстом. */
    fun setNick(raw: String) {
        val n = raw.trim().lowercase().removePrefix("@")
        if (!Regex("^[a-z0-9_.]{3,20}$").matches(n)) {
            throw RuntimeException("Ник: 3–20 знаков, только a–z, 0–9, _ и точка")
        }
        save(n, avatar)
    }

    /** Новая аватарка: квадрат по центру, 160×160, JPEG. Вызывать в фоне. */
    fun setAvatarFrom(bytes: ByteArray) {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw RuntimeException("Не удалось открыть картинку")
        val s = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val small = Bitmap.createScaledBitmap(square, 160, 160, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        save(nick, "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    /** Аватарка из data-адреса в картинку; null — пусто или не картинка. */
    fun decode(s: String): Bitmap? {
        if (!s.startsWith("data:image/")) return null
        val bytes = Base64.decode(s.substringAfter(","), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun save(n: String, av: String) {
        val uid = Auth.userId() ?: throw RuntimeException("Сначала войдите в аккаунт")
        val tk = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")
        val row = JSONObject()
            .put("user_id", uid)
            .put("nick", if (n.isEmpty()) JSONObject.NULL else n)
            .put("avatar", av)
            .put("updated_at", Instant.now().toString())
        try {
            Sync.rest("POST", "/rest/v1/profiles?on_conflict=user_id", tk, JSONArray().put(row).toString(), "resolution=merge-duplicates,return=minimal")
        } catch (e: Exception) {
            throw RuntimeException(if (e.message.orEmpty().contains("23505")) "Этот ник уже занят" else "Не удалось сохранить")
        }
        set(n, av)
    }

    private fun set(n: String, av: String) {
        nick = n
        avatar = av
        appCtx?.let { prefs(it).edit().putString("pf_nick", n).putString("pf_avatar", av).apply() }
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)
}
