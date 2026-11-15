package app.soundlife.admin

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ошибка GitHub с кодом ответа и текстом. */
class GhError(val code: Int, msg: String) : Exception(msg)

/** Один файл хранилища. */
data class Node(val path: String, val sha: String, val size: Long)

/** Работа с GitHub REST API через HttpURLConnection (без лишних библиотек). */
object Gh {
    private fun call(token: String, method: String, url: String, body: JSONObject? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val m = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
            throw GhError(code, when (code) {
                401 -> "Токен неверный или просрочен. Войдите заново."
                403 -> "Нет доступа или лимит запросов исчерпан. Проверьте права токена: Contents — Read and write."
                404 -> "Хранилище не найдено или у токена нет к нему доступа."
                409 -> "Хранилище пустое: создайте в нём любой файл (например README) и повторите."
                else -> m.ifBlank { "Ошибка GitHub ($code)" }
            })
        }
        return text
    }

    /** Имя пользователя токена (проверка входа). */
    fun login(token: String): String =
        JSONObject(call(token, "GET", "https://api.github.com/user")).optString("login")

    /** Все файлы ветки (рекурсивно). */
    fun tree(token: String, repo: String, branch: String): List<Node> {
        val j = JSONObject(call(token, "GET", "https://api.github.com/repos/$repo/git/trees/$branch?recursive=1"))
        val arr: JSONArray = j.optJSONArray("tree") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "blob" }
            .map { Node(it.getString("path"), it.getString("sha"), it.optLong("size")) }
    }

    /** Загружает бинарный файл как blob и возвращает его sha. */
    fun blob(token: String, repo: String, bytes: ByteArray): String {
        val body = JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64")
        return JSONObject(call(token, "POST", "https://api.github.com/repos/$repo/git/blobs", body)).getString("sha")
    }

    /** Один коммит: добавляет/заменяет файлы (path → sha blob). */
    fun commit(token: String, repo: String, branch: String, files: Map<String, String>, message: String) {
        val base = "https://api.github.com/repos/$repo"
        val head = JSONObject(call(token, "GET", "$base/git/ref/heads/$branch")).getJSONObject("object").getString("sha")
        val baseTree = JSONObject(call(token, "GET", "$base/git/commits/$head")).getJSONObject("tree").getString("sha")
        val tree = JSONArray()
        files.forEach { (p, sha) -> tree.put(JSONObject().put("path", p).put("mode", "100644").put("type", "blob").put("sha", sha)) }
        val nt = JSONObject(call(token, "POST", "$base/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree))).getString("sha")
        val nc = JSONObject(call(token, "POST", "$base/git/commits",
            JSONObject().put("message", message).put("tree", nt).put("parents", JSONArray().put(head)))).getString("sha")
        call(token, "PATCH", "$base/git/refs/heads/$branch", JSONObject().put("sha", nc))
    }
}
