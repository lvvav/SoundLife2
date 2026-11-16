package app.soundlife.s3;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Лёгкая версия SoundLife для старых телефонов (Android 4.1+): вкладки, поиск, медиатека, лайки и подписки. */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF33B5E5;   // голубой Holo
    private static final int BG = 0xFF0E0E0E;
    private static final int BAR = 0xFF1A1A1A;
    private static final int TEXT = 0xFFEEEEEE;
    private static final int MUTED = 0xFF888888;
    private static final String PREFS = "s3prefs";
    private static final String UNKNOWN_ARTIST = "Неизвестный исполнитель";
    private static final String NO_ALBUM = "Без альбома";

    // Экраны: вкладки HOME / SEARCH / LIBRARY, а также LIST (исполнитель или альбом), LOGIN и FULL (полный плеер)
    private static final int HOME = 0, SEARCH = 1, LIBRARY = 2, LIST = 3, LOGIN = 4, FULL = 5;
    private static final int LIB_ARTISTS = 0, LIB_ALBUMS = 1, LIB_LIKES = 2;

    private final List<Song> localSongs = new ArrayList<Song>();
    private final List<Song> onlineSongs = new ArrayList<Song>();
    private final List<Song> allSongs = new ArrayList<Song>();
    private final TreeMap<String, List<Song>> artistMap = new TreeMap<String, List<Song>>();
    private final TreeMap<String, List<Song>> albumMap = new TreeMap<String, List<Song>>();
    private boolean localLoaded = false;
    private boolean onlineLoaded = false;

    private final View[] screens = new View[6];
    private int screen = HOME;
    private int tab = HOME;
    private int returnTo = HOME;
    private boolean tracking = false;
    private final Handler ticker = new Handler();
    private ItemAdapter visibleAdapter;

    // Главная
    private ListView homeList;
    // Поиск
    private EditText searchField;
    private ListView searchList;
    private TextView searchStatus;
    // Медиатека
    private int libMode = LIB_ARTISTS;
    private ListView libList;
    private final Button[] libButtons = new Button[3];
    private TextView libHint;
    // Страница исполнителя или альбома
    private ListView listView;
    private TextView listTitle;
    private TextView listSub;
    private Button listSubButton;
    private String listSubKey = "";
    private List<Song> listSongs = new ArrayList<Song>();
    // Общие
    private Button accountButton;
    private EditText emailField;
    private EditText passField;
    private TextView loginStatus;
    private LinearLayout chrome;
    private final Button[] navButtons = new Button[3];
    private TextView barTitle;
    private TextView barArtist;
    private Button barPlay;
    // Полный плеер
    private ImageView fullCover;
    private TextView fullTitle;
    private TextView fullArtist;
    private TextView fullTime;
    private TextView fullTotal;
    private SeekBar seek;
    private Button fullPlay;
    private Button fullLike;

    private final Runnable tick = new Runnable() {
        public void run() {
            if (screen == FULL) {
                updateFull();
                ticker.postDelayed(this, 500);
            }
        }
    };

    private final PlayerService.Listener playerListener = new PlayerService.Listener() {
        public void onChanged() {
            updateBar();
            if (screen == FULL) refreshFull();
            if (visibleAdapter != null) visibleAdapter.notifyDataSetChanged();
        }
    };

    /** Строка списка: заголовок раздела, трек, исполнитель или альбом. */
    private static class Item {
        String title;
        String sub = "";
        String art;
        boolean header;
        Song song;
        Runnable click;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Tls.install(this);
        Session.init(this);
        setTitle("SoundLife");

        LinearLayout column = vertical();

        FrameLayout content = new FrameLayout(this);
        screens[HOME] = buildHome();
        screens[SEARCH] = buildSearch();
        screens[LIBRARY] = buildLibrary();
        screens[LIST] = buildList();
        screens[LOGIN] = buildLogin();
        screens[FULL] = buildFull();
        for (View v : screens) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        chrome = vertical();
        chrome.addView(buildMiniBar(), matchWrap());
        chrome.addView(buildNav(), matchWrap());
        column.addView(chrome, matchWrap());

        setContentView(column);
        show(HOME);
        loadLocalAsync();
        loadOnlineAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.setListener(playerListener);
        refreshAccount();
        updateBar();
    }

    @Override
    protected void onPause() {
        PlayerService.setListener(null);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (screen == FULL || screen == LIST) { show(returnTo); return; }
        if (screen != HOME) { show(HOME); return; }
        super.onBackPressed();
    }

    private void show(int which) {
        ticker.removeCallbacks(tick);
        screen = which;
        if (which == HOME || which == SEARCH || which == LIBRARY) tab = which;
        for (int i = 0; i < screens.length; i++) {
            screens[i].setVisibility(i == which ? View.VISIBLE : View.GONE);
        }
        chrome.setVisibility(which == FULL || which == LOGIN ? View.GONE : View.VISIBLE);
        highlightNav();
        if (which == HOME) refreshHome();
        if (which == LIBRARY) refreshLibrary();
        if (which == FULL) {
            refreshFull();
            ticker.post(tick);
        }
    }

    // ---------- Данные ----------

    private static String artistName(Song s) {
        return s.artist == null || s.artist.trim().length() == 0 ? UNKNOWN_ARTIST : s.artist.trim();
    }

    private static String albumName(Song s) {
        return s.album == null || s.album.trim().length() == 0 ? NO_ALBUM : s.album.trim();
    }

    private static String albumKey(Song s) {
        return albumName(s) + "\u0001" + artistName(s);
    }

    private void rebuildIndex() {
        allSongs.clear();
        allSongs.addAll(localSongs);
        allSongs.addAll(onlineSongs);
        artistMap.clear();
        albumMap.clear();
        for (Song s : allSongs) {
            addTo(artistMap, artistName(s), s);
            addTo(albumMap, albumKey(s), s);
        }
    }

    private static void addTo(TreeMap<String, List<Song>> map, String key, Song s) {
        List<Song> list = map.get(key);
        if (list == null) {
            list = new ArrayList<Song>();
            map.put(key, list);
        }
        list.add(s);
    }

    private List<Song> queryLocal() {
        List<Song> out = new ArrayList<Song>();
        ContentResolver cr = getContentResolver();
        HashMap<Long, String> albumArt = new HashMap<Long, String>();
        Cursor a = cr.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART}, null, null, null);
        if (a != null) {
            while (a.moveToNext()) albumArt.put(a.getLong(0), a.getString(1));
            a.close();
        }
        Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE);
        if (c == null) return out;
        int iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int iPath = c.getColumnIndex(MediaStore.Audio.Media.DATA);
        int iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
        int iAlbumName = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        while (c.moveToNext()) {
            String art = albumArt.get(c.getLong(iAlbum));
            out.add(new Song(c.getString(iTitle), c.getString(iArtist), c.getString(iPath), art, c.getString(iAlbumName)));
        }
        c.close();
        return out;
    }

    private void loadLocalAsync() {
        new Thread(new Runnable() {
            public void run() {
                final List<Song> loaded = queryLocal();
                runOnUiThread(new Runnable() {
                    public void run() {
                        localSongs.clear();
                        localSongs.addAll(loaded);
                        localLoaded = true;
                        rebuildIndex();
                        if (screen == HOME) refreshHome();
                        if (localSongs.isEmpty()) Toast.makeText(MainActivity.this, "На телефоне не найдено музыки", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void loadOnlineAsync() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<Song> list = RemoteLib.load();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            onlineSongs.clear();
                            onlineSongs.addAll(list);
                            onlineLoaded = true;
                            rebuildIndex();
                            if (screen == HOME) refreshHome();
                            if (screen == SEARCH) runSearch();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (searchStatus != null) searchStatus.setText("Онлайн-музыка не загрузилась: " + Tls.text(e));
                        }
                    });
                }
            }
        }).start();
    }

    // ---------- Лайки и подписки (хранятся на телефоне) ----------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean isLiked(Song s) {
        return prefs().getStringSet("likes", new HashSet<String>()).contains(s.path);
    }

    private boolean isSub(String key) {
        return prefs().getStringSet("subs", new HashSet<String>()).contains(key);
    }

    private void toggleLike(Song s) {
        toggleIn("likes", s.path);
    }

    private void toggleSub(String key) {
        toggleIn("subs", key);
    }

    private void toggleIn(String name, String value) {
        Set<String> set = new HashSet<String>(prefs().getStringSet(name, new HashSet<String>()));
        if (!set.remove(value)) set.add(value);
        prefs().edit().putStringSet(name, set).commit();
    }

    // ---------- Воспроизведение ----------

    private void send(String action, int pos) {
        Intent i = new Intent(this, PlayerService.class).setAction(action).putExtra(PlayerService.EXTRA_POS, pos);
        startService(i);
    }

    /** Ставит список в очередь и играет с позиции pos; при shuffle очередь перемешивается и играет с начала. */
    private void playList(List<Song> list, int pos, boolean shuffle) {
        if (list.isEmpty()) return;
        List<Song> queue = new ArrayList<Song>(list);
        int start = pos;
        if (shuffle) {
            Collections.shuffle(queue);
            start = 0;
        }
        PlayerService.setQueue(queue);
        send(PlayerService.ACTION_PLAY_AT, start);
    }

    private void seekBy(int deltaMs) {
        int target = PlayerService.position() + deltaMs;
        PlayerService.seek(Math.max(0, Math.min(target, Math.max(0, PlayerService.duration()))));
    }

    // ---------- Строки списков ----------

    private Item header(String title) {
        Item i = new Item();
        i.title = title;
        i.header = true;
        return i;
    }

    private Item songItem(final List<Song> list, final int index) {
        final Song s = list.get(index);
        Item i = new Item();
        i.title = s.title == null ? "Без названия" : s.title;
        i.sub = artistName(s);
        i.art = s.art;
        i.song = s;
        i.click = new Runnable() {
            public void run() { playList(list, index, false); }
        };
        return i;
    }

    private List<Item> songItems(List<Song> list) {
        List<Item> out = new ArrayList<Item>();
        for (int k = 0; k < list.size(); k++) out.add(songItem(list, k));
        return out;
    }

    private Item artistItem(final String name, final List<Song> songs) {
        Item i = new Item();
        i.title = name;
        i.sub = songs.size() + " тр.";
        i.art = songs.get(0).art;
        i.click = new Runnable() {
            public void run() { openList("a:" + name, name, songs.size() + " тр.", songs); }
        };
        return i;
    }

    private Item albumItem(final String key, final List<Song> songs) {
        final Song first = songs.get(0);
        Item i = new Item();
        i.title = albumName(first);
        i.sub = artistName(first);
        i.art = first.art;
        i.click = new Runnable() {
            public void run() { openList("b:" + key, albumName(first), artistName(first), songs); }
        };
        return i;
    }

    private void openList(String subKey, String title, String sub, List<Song> songs) {
        listSubKey = subKey;
        listSongs = songs;
        listTitle.setText(title);
        listSub.setText(sub);
        setVisibleAdapter(listView, new ItemAdapter(songItems(songs)));
        refreshListSubButton();
        returnTo = tab;
        show(LIST);
    }

    private void setVisibleAdapter(ListView lv, ItemAdapter adapter) {
        lv.setAdapter(adapter);
        visibleAdapter = adapter;
    }

    private void refreshListSubButton() {
        listSubButton.setText(isSub(listSubKey) ? "Подписан" : "Подписаться");
    }

    // ---------- Экраны ----------

    private View buildHome() {
        LinearLayout box = vertical();
        LinearLayout head = row();
        head.setPadding(dp(14), dp(10), dp(10), dp(6));
        head.addView(text("SoundLife", 24, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        accountButton = smallButton("Войти", new View.OnClickListener() {
            public void onClick(View v) { openAccount(); }
        });
        head.addView(accountButton);
        box.addView(head, matchWrap());

        LinearLayout quick = row();
        quick.setPadding(dp(10), dp(4), dp(10), dp(8));
        quick.addView(smallButton("Любимые треки", new View.OnClickListener() {
            public void onClick(View v) {
                libMode = LIB_LIKES;
                show(LIBRARY);
            }
        }), new LinearLayout.LayoutParams(0, dp(48), 1f));
        quick.addView(smallButton("Перемешать всё", new View.OnClickListener() {
            public void onClick(View v) { playList(allSongs, 0, true); }
        }), new LinearLayout.LayoutParams(0, dp(48), 1f));
        box.addView(quick, matchWrap());

        homeList = itemList();
        box.addView(homeList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    /** Список строк с обработкой нажатий: у каждой строки своя функция click. */
    private ListView itemList() {
        ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(0xFF222222));
        lv.setDividerHeight(1);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Item it = (Item) parent.getItemAtPosition(position);
                if (it.click != null) it.click.run();
            }
        });
        return lv;
    }

    private void refreshHome() {
        if (homeList == null) return;
        List<Item> items = new ArrayList<Item>();
        if (!localLoaded && !onlineLoaded) items.add(header("Загрузка музыки…"));
        List<Item> artists = new ArrayList<Item>();
        for (Map.Entry<String, List<Song>> e : artistMap.entrySet()) {
            if (artists.size() >= 12) break;
            artists.add(artistItem(e.getKey(), e.getValue()));
        }
        if (!artists.isEmpty()) {
            items.add(header("Исполнители"));
            items.addAll(artists);
        }
        List<Item> albums = new ArrayList<Item>();
        for (Map.Entry<String, List<Song>> e : albumMap.entrySet()) {
            if (albums.size() >= 12) break;
            albums.add(albumItem(e.getKey(), e.getValue()));
        }
        if (!albums.isEmpty()) {
            items.add(header("Альбомы"));
            items.addAll(albums);
        }
        if (!allSongs.isEmpty()) {
            items.add(header("Все треки"));
            items.addAll(songItems(allSongs));
        }
        setVisibleAdapter(homeList, new ItemAdapter(items));
    }

    private View buildSearch() {
        LinearLayout box = vertical();
        searchField = new EditText(this);
        searchField.setHint("Что хотите послушать?");
        searchField.setTextColor(TEXT);
        searchField.setHintTextColor(MUTED);
        searchField.setSingleLine(true);
        searchField.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { runSearch(); }
        });
        box.addView(searchField, matchWrap());

        searchStatus = text("", 13, MUTED);
        searchStatus.setPadding(dp(14), dp(4), dp(14), dp(4));
        box.addView(searchStatus, matchWrap());

        searchList = itemList();
        box.addView(searchList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void runSearch() {
        if (searchList == null) return;
        String q = searchField.getText().toString().trim().toLowerCase();
        List<Item> items = new ArrayList<Item>();
        if (q.length() == 0) {
            searchStatus.setText("Введите название, исполнителя или альбом");
            setVisibleAdapter(searchList, new ItemAdapter(items));
            return;
        }
        searchStatus.setText(onlineLoaded ? "" : "Онлайн-библиотека загружается…");
        List<Item> artists = new ArrayList<Item>();
        for (Map.Entry<String, List<Song>> e : artistMap.entrySet()) {
            if (e.getKey().toLowerCase().contains(q)) artists.add(artistItem(e.getKey(), e.getValue()));
        }
        List<Item> albums = new ArrayList<Item>();
        for (Map.Entry<String, List<Song>> e : albumMap.entrySet()) {
            if (albumName(e.getValue().get(0)).toLowerCase().contains(q)) albums.add(albumItem(e.getKey(), e.getValue()));
        }
        List<Song> matchedSongs = new ArrayList<Song>();
        for (Song s : allSongs) {
            String t = s.title == null ? "" : s.title.toLowerCase();
            if (t.contains(q) || artistName(s).toLowerCase().contains(q)) matchedSongs.add(s);
        }
        if (!artists.isEmpty()) { items.add(header("Исполнители")); items.addAll(artists); }
        if (!albums.isEmpty()) { items.add(header("Альбомы")); items.addAll(albums); }
        if (!matchedSongs.isEmpty()) { items.add(header("Треки")); items.addAll(songItems(matchedSongs)); }
        if (items.isEmpty()) items.add(header("Ничего не найдено"));
        setVisibleAdapter(searchList, new ItemAdapter(items));
    }

    private View buildLibrary() {
        LinearLayout box = vertical();
        LinearLayout tabs = row();
        tabs.setPadding(dp(10), dp(10), dp(10), dp(6));
        String[] names = {"Исполнители", "Альбомы", "Любимые"};
        for (int k = 0; k < names.length; k++) {
            final int mode = k;
            libButtons[k] = smallButton(names[k], new View.OnClickListener() {
                public void onClick(View v) {
                    libMode = mode;
                    refreshLibrary();
                }
            });
            tabs.addView(libButtons[k], new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        box.addView(tabs, matchWrap());

        libHint = text("", 13, MUTED);
        libHint.setPadding(dp(14), dp(4), dp(14), dp(4));
        box.addView(libHint, matchWrap());

        libList = itemList();
        box.addView(libList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void refreshLibrary() {
        if (libList == null) return;
        List<Item> items = new ArrayList<Item>();
        if (libMode == LIB_ARTISTS) {
            libHint.setText("Подписанные исполнители. Подписаться можно на странице исполнителя.");
            for (Map.Entry<String, List<Song>> e : artistMap.entrySet()) {
                if (isSub("a:" + e.getKey())) items.add(artistItem(e.getKey(), e.getValue()));
            }
        } else if (libMode == LIB_ALBUMS) {
            libHint.setText("Подписанные альбомы. Подписаться можно на странице альбома.");
            for (Map.Entry<String, List<Song>> e : albumMap.entrySet()) {
                if (isSub("b:" + e.getKey())) items.add(albumItem(e.getKey(), e.getValue()));
            }
        } else {
            libHint.setText("Треки, которые вы отметили сердечком.");
            List<Song> liked = new ArrayList<Song>();
            for (Song s : allSongs) if (isLiked(s)) liked.add(s);
            items.addAll(songItems(liked));
        }
        if (items.isEmpty()) items.add(header("Пока пусто"));
        setVisibleAdapter(libList, new ItemAdapter(items));
        for (int k = 0; k < libButtons.length; k++) {
            libButtons[k].setTextColor(k == libMode ? Color.WHITE : ACCENT);
        }
    }

    private View buildList() {
        LinearLayout box = vertical();
        LinearLayout top = row();
        top.setPadding(dp(10), dp(8), dp(10), dp(4));
        top.addView(smallButton("Назад", new View.OnClickListener() {
            public void onClick(View v) { show(returnTo); }
        }));
        box.addView(top, matchWrap());

        LinearLayout info = vertical();
        info.setPadding(dp(14), dp(4), dp(14), dp(8));
        listTitle = text("", 22, TEXT);
        listTitle.setSingleLine(true);
        listTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(listTitle, matchWrap());
        listSub = text("", 14, MUTED);
        info.addView(listSub, matchWrap());

        LinearLayout actions = row();
        listSubButton = smallButton("Подписаться", new View.OnClickListener() {
            public void onClick(View v) {
                toggleSub(listSubKey);
                refreshListSubButton();
            }
        });
        actions.addView(listSubButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        actions.addView(smallButton("Играть", new View.OnClickListener() {
            public void onClick(View v) { playList(listSongs, 0, false); }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        actions.addView(smallButton("Перемешать", new View.OnClickListener() {
            public void onClick(View v) { playList(listSongs, 0, true); }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        info.addView(actions, matchWrap());
        box.addView(info, matchWrap());

        listView = itemList();
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private View buildNav() {
        LinearLayout nav = row();
        nav.setBackgroundColor(BAR);
        nav.setPadding(dp(6), dp(4), dp(6), dp(6));
        String[] names = {"Главная", "Поиск", "Медиатека"};
        int[] targets = {HOME, SEARCH, LIBRARY};
        for (int k = 0; k < names.length; k++) {
            final int target = targets[k];
            navButtons[k] = smallButton(names[k], new View.OnClickListener() {
                public void onClick(View v) { show(target); }
            });
            nav.addView(navButtons[k], new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        return nav;
    }

    private void highlightNav() {
        int[] targets = {HOME, SEARCH, LIBRARY};
        for (int k = 0; k < navButtons.length; k++) {
            navButtons[k].setTextColor(targets[k] == tab ? Color.WHITE : MUTED);
        }
    }

    private View buildMiniBar() {
        LinearLayout bar = row();
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(12), dp(6), dp(6), dp(6));

        LinearLayout texts = vertical();
        barTitle = text("Ничего не играет", 15, TEXT);
        barArtist = text("", 12, MUTED);
        for (TextView t : new TextView[]{barTitle, barArtist}) {
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
        }
        texts.addView(barTitle);
        texts.addView(barArtist);
        bar.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bar.addView(smallButton("<<", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_PREV, 0); }
        }));
        barPlay = smallButton(">", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_TOGGLE, 0); }
        });
        bar.addView(barPlay);
        bar.addView(smallButton(">>", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_NEXT, 0); }
        }));
        // Нажатие на полоску (не на кнопки) открывает полный плеер
        bar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openFull(); }
        });
        return bar;
    }

    private void updateBar() {
        if (barTitle == null) return;
        Song s = currentSong();
        if (s != null) {
            barTitle.setText(s.title == null ? "Без названия" : s.title);
            barArtist.setText(s.artist == null ? "" : s.artist);
        } else {
            barTitle.setText("Ничего не играет");
            barArtist.setText("");
        }
        barPlay.setText(PlayerService.isPlaying() ? "||" : ">");
    }

    private View buildLogin() {
        LinearLayout box = vertical();
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        box.addView(text("Вход по почте", 24, TEXT), wrap(0, 0, 0, dp(16)));

        emailField = new EditText(this);
        emailField.setHint("Почта");
        emailField.setTextColor(TEXT);
        emailField.setHintTextColor(MUTED);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(emailField, matchWrap());

        passField = new EditText(this);
        passField.setHint("Пароль (от 6 символов)");
        passField.setTextColor(TEXT);
        passField.setHintTextColor(MUTED);
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(passField, matchWrap());

        LinearLayout buttons = row();
        buttons.addView(bigButton("Войти", new View.OnClickListener() {
            public void onClick(View v) { authenticate(false); }
        }), new LinearLayout.LayoutParams(0, dp(50), 1f));
        buttons.addView(bigButton("Регистрация", new View.OnClickListener() {
            public void onClick(View v) { authenticate(true); }
        }), new LinearLayout.LayoutParams(0, dp(50), 1f));
        box.addView(buttons, wrap(0, dp(16), 0, 0));

        loginStatus = text("", 13, MUTED);
        box.addView(loginStatus, wrap(0, dp(14), 0, 0));

        box.addView(smallButton("Назад", new View.OnClickListener() {
            public void onClick(View v) { show(HOME); }
        }), wrap(0, dp(20), 0, 0));
        return box;
    }

    private void openAccount() {
        if (Session.signedIn()) {
            Session.signOut();
            refreshAccount();
            Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show();
        } else {
            loginStatus.setText("");
            show(LOGIN);
        }
    }

    private void refreshAccount() {
        if (accountButton == null) return;
        accountButton.setText(Session.signedIn() ? "Выйти (" + Session.email() + ")" : "Войти");
    }

    private void authenticate(final boolean signUp) {
        final String mail = emailField.getText().toString().trim();
        final String pass = passField.getText().toString();
        if (mail.length() == 0 || pass.length() < 6) {
            loginStatus.setText("Введите почту и пароль от 6 символов");
            return;
        }
        loginStatus.setText("Подождите…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String note = null;
                    if (signUp) note = Session.signUp(mail, pass); else Session.signIn(mail, pass);
                    final String message = note;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (message != null) {
                                loginStatus.setText(message);
                            } else {
                                passField.setText("");
                                refreshAccount();
                                show(HOME);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { loginStatus.setText(Tls.text(e)); }
                    });
                }
            }
        }).start();
    }

    private View buildFull() {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(10), dp(20), dp(20));

        box.addView(smallButton("Свернуть", new View.OnClickListener() {
            public void onClick(View v) { show(returnTo); }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fullCover = new ImageView(this);
        fullCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fullCover.setBackgroundColor(0xFF222222);
        box.addView(fullCover, new LinearLayout.LayoutParams(dp(220), dp(220)));

        fullTitle = text("", 20, TEXT);
        fullTitle.setGravity(Gravity.CENTER);
        fullTitle.setSingleLine(true);
        fullTitle.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(fullTitle, wrap(0, dp(14), 0, 0));

        fullArtist = text("", 14, MUTED);
        fullArtist.setGravity(Gravity.CENTER);
        fullArtist.setSingleLine(true);
        fullArtist.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(fullArtist, wrap(0, dp(2), 0, 0));

        fullLike = smallButton("♡ Лайк", new View.OnClickListener() {
            public void onClick(View v) {
                Song s = currentSong();
                if (s == null) return;
                toggleLike(s);
                refreshFull();
            }
        });
        box.addView(fullLike, wrap(0, dp(8), 0, 0));

        seek = new SeekBar(this);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) fullTime.setText(fmt(progress));
            }
            public void onStartTrackingTouch(SeekBar sb) { tracking = true; }
            public void onStopTrackingTouch(SeekBar sb) {
                tracking = false;
                PlayerService.seek(sb.getProgress());
            }
        });
        box.addView(seek, wrap(0, dp(18), 0, 0));

        LinearLayout times = row();
        fullTime = text("0:00", 12, MUTED);
        fullTotal = text("0:00", 12, MUTED);
        times.addView(fullTime);
        times.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        times.addView(fullTotal);
        box.addView(times, matchWrap());

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        controls.addView(smallButton("-10", new View.OnClickListener() {
            public void onClick(View v) { seekBy(-10000); }
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        controls.addView(smallButton("<<", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_PREV, 0); }
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        fullPlay = bigButton(">", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_TOGGLE, 0); }
        });
        controls.addView(fullPlay, new LinearLayout.LayoutParams(dp(80), dp(56)));
        controls.addView(smallButton(">>", new View.OnClickListener() {
            public void onClick(View v) { send(PlayerService.ACTION_NEXT, 0); }
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        controls.addView(smallButton("+10", new View.OnClickListener() {
            public void onClick(View v) { seekBy(10000); }
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        box.addView(controls, wrap(0, dp(18), 0, 0));
        return box;
    }

    private void openFull() {
        returnTo = screen;
        show(FULL);
    }

    private Song currentSong() {
        int current = PlayerService.current;
        if (current >= 0 && current < PlayerService.songs.size()) return PlayerService.songs.get(current);
        return null;
    }

    private void refreshFull() {
        if (fullTitle == null) return;
        Song s = currentSong();
        if (s != null) {
            fullTitle.setText(s.title == null ? "Без названия" : s.title);
            fullArtist.setText(s.artist == null ? "" : s.artist);
            Covers.show(fullCover, s.art);
            fullLike.setText(isLiked(s) ? "♥ В любимых" : "♡ Лайк");
        } else {
            fullTitle.setText("Ничего не играет");
            fullArtist.setText("");
            Covers.show(fullCover, null);
            fullLike.setText("♡ Лайк");
        }
        updateFull();
    }

    private void updateFull() {
        int dur = PlayerService.duration();
        int pos = PlayerService.position();
        if (dur > 0) seek.setMax(dur);
        if (!tracking) {
            seek.setProgress(pos);
            fullTime.setText(fmt(pos));
        }
        fullTotal.setText(fmt(dur));
        fullPlay.setText(PlayerService.isPlaying() ? "||" : ">");
    }

    private static String fmt(int ms) {
        int sec = Math.max(0, ms) / 1000;
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    // ---------- Адаптер строк ----------

    private class ItemAdapter extends BaseAdapter {
        private final List<Item> items;

        ItemAdapter(List<Item> items) {
            this.items = items;
        }

        public int getCount() { return items.size(); }
        public Object getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return position; }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int position) { return items.get(position).header ? 1 : 0; }
        public boolean isEnabled(int position) { return !items.get(position).header; }

        public View getView(int position, View convertView, ViewGroup parent) {
            Item it = items.get(position);
            if (it.header) {
                TextView h = text(it.title, 17, TEXT);
                h.setPadding(dp(14), dp(16), dp(14), dp(6));
                return h;
            }
            TextView t1;
            TextView t2;
            ImageView cover;
            View rowView;
            if (convertView != null && convertView.getTag() instanceof Object[]) {
                rowView = convertView;
                Object[] tag = (Object[]) rowView.getTag();
                t1 = (TextView) tag[0];
                t2 = (TextView) tag[1];
                cover = (ImageView) tag[2];
            } else {
                LinearLayout box = row();
                box.setPadding(dp(10), dp(8), dp(14), dp(8));
                cover = new ImageView(MainActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackgroundColor(0xFF222222);
                box.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout texts = vertical();
                texts.setPadding(dp(12), 0, 0, 0);
                t1 = text("", 16, TEXT);
                t2 = text("", 13, MUTED);
                for (TextView t : new TextView[]{t1, t2}) {
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                }
                texts.addView(t1);
                texts.addView(t2);
                box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                box.setTag(new Object[]{t1, t2, cover});
                rowView = box;
            }
            Covers.show(cover, it.art);
            int cur = PlayerService.current;
            boolean playingThis = it.song != null && cur >= 0 && cur < PlayerService.songs.size()
                    && PlayerService.songs.get(cur).path.equals(it.song.path);
            t1.setText(it.title);
            t1.setTextColor(playingThis ? ACCENT : TEXT);
            t2.setText(it.sub == null ? "" : it.sub);
            return rowView;
        }
    }

    // ---------- Виджеты ----------

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button bigButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(ACCENT);
        b.setOnClickListener(click);
        return b;
    }

    private Button smallButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(ACCENT);
        b.setBackgroundColor(0x00000000);
        b.setMinWidth(dp(44));
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout.LayoutParams wrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(left, top, right, bottom);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
