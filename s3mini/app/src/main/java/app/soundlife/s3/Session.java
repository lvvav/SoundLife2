package app.soundlife.s3;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Вход по почте и паролю (Supabase). Токен хранится на телефоне и обновляется сам. */
final class Session {

    private static final String PREFS = "s3session";
    private static Context app;
    private static String token;
    private static String refresh;
    private static String email;
    private static long expires;

    private Session() {}

    static void init(Context c) {
        app = c.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, 0);
        token = p.getString("t", null);
        refresh = p.getString("r", null);
        email = p.getString("e", null);
        expires = p.getLong("x", 0);
    }

    static boolean signedIn() { return token != null; }

    static String email() { return email; }

    static void signOut() {
        token = null;
        refresh = null;
        email = null;
        app.getSharedPreferences(PREFS, 0).edit().clear().commit();
    }

    /** Действующий токен; при необходимости обновляет его. Вызывать в фоне. */
    static synchronized String bearer() throws Exception {
        if (token == null) throw new Exception("Нужен вход по почте");
        if (System.currentTimeMillis() > expires - 60000 && refresh != null) {
            JSONObject body = new JSONObject().put("refresh_token", refresh);
            save(Net.supa("/auth/v1/token?grant_type=refresh_token", body.toString(), BuildConfig.SUPA_KEY), email);
        }
        return token;
    }

    /** Вход. Вызывать в фоне. */
    static void signIn(String mail, String pass) throws Exception {
        JSONObject body = new JSONObject().put("email", mail).put("password", pass);
        save(Net.supa("/auth/v1/token?grant_type=password", body.toString(), BuildConfig.SUPA_KEY), mail);
    }

    /** Регистрация. Возвращает текст для пользователя, если нужно подтвердить почту. Вызывать в фоне. */
    static String signUp(String mail, String pass) throws Exception {
        JSONObject body = new JSONObject().put("email", mail).put("password", pass);
        String text = Net.supa("/auth/v1/signup", body.toString(), BuildConfig.SUPA_KEY);
        if (new JSONObject(text).has("access_token")) {
            save(text, mail);
            return null;
        }
        return "Письмо отправлено. Подтвердите почту и войдите";
    }

    private static void save(String text, String mail) throws Exception {
        JSONObject j = new JSONObject(text);
        token = j.getString("access_token");
        refresh = j.optString("refresh_token", refresh);
        email = mail;
        expires = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000L;
        app.getSharedPreferences(PREFS, 0).edit()
                .putString("t", token).putString("r", refresh).putString("e", email).putLong("x", expires).commit();
    }
}
