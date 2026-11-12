package app.soundlife

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Вход на компьютере по QR: телефон подтверждает код, как на сайте (функция clever-function, действие approve). */
object QrLogin {
    /** sid из ссылки с QR-кода: адрес сайта с #qr=<sid>. null — это не код входа. */
    fun sidFrom(text: String): String? =
        Regex("[#&]qr=([a-z0-9]{20,64})").find(text)?.groupValues?.get(1)

    /** Подтвердить вход на другом устройстве. Вызывать в фоне. Бросает исключение с понятным текстом. */
    fun approve(sid: String) {
        val tk = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")
        val c = URL("${BuildConfig.SUPA_URL}/functions/v1/clever-function").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("apikey", BuildConfig.SUPA_KEY)
        c.setRequestProperty("Authorization", "Bearer $tk")
        c.outputStream.use { it.write(JSONObject().put("action", "approve").put("sid", sid).toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val text = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        val j = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        if (!ok || !j.optBoolean("ok")) {
            throw RuntimeException(
                if (j.optString("status") == "expired") "Код устарел. Обновите код на компьютере"
                else "Не получилось подтвердить вход. Обновите код на компьютере",
            )
        }
    }
}
