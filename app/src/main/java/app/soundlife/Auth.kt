package app.soundlife

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Вход в аккаунт SoundLife: тот же проект Supabase, что у сайта. Прямые запросы к REST, без SDK. */
object Auth {
    private const val K_ACCESS = "sb_access"
    private const val K_REFRESH = "sb_refresh"
    private const val K_EXP = "sb_exp"
    private const val K_EMAIL = "sb_email"
    private const val K_UID = "sb_uid"

    /** Адрес, на который браузер вернёт пользователя в приложение (зарегистрировать в Supabase и в Яндексе). */
    const val REDIRECT = "soundlife://auth"

    /** Почта вошедшего пользователя; пусто = не вошли. */
    var account by mutableStateOf("")
        private set
    val signedIn: Boolean get() = account.isNotEmpty()

    private var appCtx: Context? = null
    private var access = ""
    private var refresh = ""
    private var exp = 0L
    private var uid = ""

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = prefs(ctx)
        access = p.getString(K_ACCESS, "") ?: ""
        refresh = p.getString(K_REFRESH, "") ?: ""
        exp = p.getLong(K_EXP, 0L)
        uid = p.getString(K_UID, "") ?: ""
        account = if (refresh.isNotEmpty()) p.getString(K_EMAIL, "") ?: "" else ""
    }

    /** Действующий токен (обновляет его при необходимости). null — не вошли. */
    @Synchronized
    fun token(): String? {
        if (refresh.isEmpty()) return null
        if (System.currentTimeMillis() / 1000 < exp - 60) return access
        return try {
            request("grant_type=refresh_token", JSONObject().put("refresh_token", refresh), account)
            access
        } catch (e: Exception) {
            null
        }
    }

    /** Вход по почте и паролю. Бросает исключение с понятным текстом. */
    @Synchronized
    fun signIn(email: String, password: String) {
        val mail = email.trim()
        request("grant_type=password", JSONObject().put("email", mail).put("password", password), mail)
        Sync.schedule()
        runCatching { Wishes.pull() }
        runCatching { Profile.load() }
        runCatching { Follows.pull() }
    }

    /** Ссылка входа через Google (Supabase OAuth). Вернётся в приложение по REDIRECT. */
    fun googleUrl(): String =
        "${BuildConfig.SUPA_URL}/auth/v1/authorize?provider=google&redirect_to=$REDIRECT"

    /**
     * Возврат из браузера: разбирает часть адреса после # (access_token и т.д.).
     * Вызывать в фоновом потоке. Бросает исключение с понятным текстом.
     */
    @Synchronized
    fun finishLogin(fragment: String) {
        val p = fragment.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
        }.toMap()
        val tok = p["access_token"].orEmpty()
        if (tok.isEmpty()) throw RuntimeException("Вход отменён")

        // Сессия Supabase уже есть в адресе (access_token, refresh_token), нужен только пользователь
        val user = call("/auth/v1/user", null, tok, "GET")
        val session = JSONObject()
            .put("access_token", tok)
            .put("refresh_token", p["refresh_token"].orEmpty())
            .put("expires_in", p["expires_in"]?.toLongOrNull() ?: 3600L)
            .put("user", user)
        store(session, "")
        Sync.schedule()
        runCatching { Wishes.pull() }
        runCatching { Profile.load() }
        runCatching { Follows.pull() }
    }

    @Synchronized
    fun signOut() {
        access = ""
        refresh = ""
        exp = 0L
        uid = ""
        appCtx?.let { prefs(it).edit().remove(K_ACCESS).remove(K_REFRESH).remove(K_EXP).remove(K_EMAIL).remove(K_UID).apply() }
        account = ""
        Profile.clear()
        Follows.clear()
    }

    private fun request(grant: String, body: JSONObject, mail: String) {
        val j = call("/auth/v1/token?$grant", body, BuildConfig.SUPA_KEY)
        store(j, mail)
    }

    /** Запрос к Supabase. Возвращает JSON ответа или бросает исключение с понятным текстом. */
    private fun call(path: String, body: JSONObject?, bearer: String, method: String = "POST"): JSONObject {
        val c = URL("${BuildConfig.SUPA_URL}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("apikey", BuildConfig.SUPA_KEY)
        c.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val ok = c.responseCode in 200..299
        val text = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        val j = JSONObject(text)
        if (!ok) throw RuntimeException(authMessage(j))
        return j
    }

    /** Запоминает сессию (access/refresh/exp/почта/id) на устройстве. */
    private fun store(j: JSONObject, mail: String) {
        access = j.getString("access_token")
        refresh = j.getString("refresh_token")
        exp = System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600)
        val who = j.optJSONObject("user")?.optString("email").orEmpty().ifBlank { mail }
        account = who
        uid = j.optJSONObject("user")?.optString("id").orEmpty().ifBlank { uid }
        appCtx?.let {
            prefs(it).edit()
                .putString(K_ACCESS, access)
                .putString(K_REFRESH, refresh)
                .putLong(K_EXP, exp)
                .putString(K_EMAIL, who)
                .putString(K_UID, uid)
                .apply()
        }
    }

    /** Id пользователя для таблицы user_data; null — не вошли. */
    fun userId(): String? = uid.ifEmpty { null }

    private fun authMessage(j: JSONObject): String = when (j.optString("error_code", j.optString("error"))) {
        "invalid_credentials", "invalid_grant" -> "Неверная почта или пароль"
        "email_not_confirmed" -> "Почта не подтверждена. Подтвердите её по письму"
        else -> j.optString("error_description", j.optString("msg", "Не удалось войти"))
    }
}
