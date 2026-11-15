package app.soundlife.s3;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONObject;

/** Сетевые запросы: Supabase (вход по почте) и GitHub (онлайн-музыка). */
final class Net {

    private Net() {}

    /** Запрос к Supabase Auth. Возвращает текст ответа или бросает исключение с понятным текстом. */
    static String supa(String path, String body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.SUPA_URL + path).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("apikey", BuildConfig.SUPA_KEY);
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        OutputStream out = c.getOutputStream();
        out.write(body.getBytes("UTF-8"));
        out.close();
        return read(c);
    }

    /** GET-запрос (GitHub API и файлы из репозитория). */
    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        return read(c);
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = in == null ? "" : new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        if (code < 200 || code >= 300) throw new Exception(message(text, code));
        return text;
    }

    private static String message(String text, int code) {
        try {
            JSONObject j = new JSONObject(text);
            String raw = j.optString("error_description", j.optString("msg", j.optString("error", "")));
            if (raw.contains("Invalid login")) return "Неверная почта или пароль";
            if (raw.contains("already registered")) return "Такая почта уже зарегистрирована";
            if (raw.contains("Password should")) return "Пароль слишком короткий (нужно от 6 символов)";
            if (raw.length() > 0) return raw;
        } catch (Exception ignored) { }
        return "Ошибка сети или сервера (код " + code + ")";
    }
}
