package app.soundlife.s3;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Онлайн-музыка: файлы из папки music/ репозитория GitHub. */
final class RemoteLib {

    private static final String REPO = "lvvav/wavelab";
    private static final String BRANCH = "HEAD";
    private static final String FOLDER = "music/";

    private RemoteLib() {}

    static List<Song> load() throws Exception {
        String json = Net.get("https://api.github.com/repos/" + REPO + "/git/trees/" + BRANCH + "?recursive=1");
        JSONArray tree = new JSONObject(json).optJSONArray("tree");
        List<Song> songs = new ArrayList<Song>();
        if (tree == null) return songs;
        for (int i = 0; i < tree.length(); i++) {
            JSONObject o = tree.getJSONObject(i);
            String path = o.optString("path");
            if (!"blob".equals(o.optString("type")) || !path.startsWith(FOLDER)) continue;
            String lower = path.toLowerCase();
            if (!(lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg"))) continue;
            String name = path.substring(path.lastIndexOf('/') + 1);
            String title = name.substring(0, name.lastIndexOf('.'));
            songs.add(new Song(title, "", url(path)));
        }
        return songs;
    }

    private static String url(String path) throws Exception {
        StringBuilder sb = new StringBuilder("https://raw.githubusercontent.com/" + REPO + "/" + BRANCH);
        for (String seg : path.split("/")) {
            sb.append('/').append(URLEncoder.encode(seg, "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }
}
