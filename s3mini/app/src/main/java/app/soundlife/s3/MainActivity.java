package app.soundlife.s3;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Лёгкая версия SoundLife для старых телефонов (Android 4.1+): своя музыка, онлайн-музыка, вход по почте. */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF33B5E5;   // голубой Holo
    private static final int BG = 0xFF0E0E0E;
    private static final int BAR = 0xFF1A1A1A;
    private static final int TEXT = 0xFFEEEEEE;
    private static final int MUTED = 0xFF888888;

    private static final int HOME = 0, LOCAL = 1, ONLINE = 2, LOGIN = 3;

    private final List<Song> localSongs = new ArrayList<Song>();
    private final List<Song> onlineSongs = new ArrayList<Song>();
    private List<Song> shown = localSongs;
    private boolean localLoaded = false;
    private boolean onlineLoaded = false;
    private int screen = HOME;

    private View[] screens = new View[4];
    private SongAdapter adapter;
    private TextView onlineStatus;
    private TextView loginStatus;
    private EditText emailField;
    private EditText passField;
    private TextView barTitle;
    private TextView barArtist;
    private Button barPlay;
    private Button accountButton;

    private final PlayerService.Listener playerListener = new PlayerService.Listener() {
        public void onChanged() {
            if (adapter != null) adapter.notifyDataSetChanged();
            updateBar();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Session.init(this);
        setTitle("SoundLife");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        screens[HOME] = buildHome();
        screens[LOCAL] = buildSongs(true);
        screens[ONLINE] = buildSongs(false);
        screens[LOGIN] = buildLogin();
        for (View v : screens) {
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);
        show(HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.setListener(playerListener);
        refreshAccount();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateBar();
    }

    @Override
    protected void onPause() {
        PlayerService.setListener(null);
        super.onPause();
    }

    private void show(int which) {
        screen = which;
        for (int i = 0; i < screens.length; i++) {
            screens[i].setVisibility(i == which ? View.VISIBLE : View.GONE);
        }
    }

    private View buildHome() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        home.setBackgroundColor(BG);
        home.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = text("SoundLife", 30, TEXT);
        title.setGravity(Gravity.CENTER);
        home.addView(title);

        TextView hint = text("музыка для старого телефона", 14, MUTED);
        hint.setGravity(Gravity.CENTER);
        home.addView(hint, wrap(0, dp(6), 0, dp(30)));

        home.addView(bigButton("Моя музыка", new View.OnClickListener() {
            public void onClick(View v) { openLocal(); }
        }), new LinearLayout.LayoutParams(dp(230), dp(56)));
        home.addView(bigButton("Онлайн-музыка", new View.OnClickListener() {
            public void onClick(View v) { openOnline(); }
        }), wrap(0, dp(14), 0, 0));

        accountButton = smallButton("Войти", new View.OnClickListener() {
            public void onClick(View v) { openAccount(); }
        });
        home.addView(accountButton, wrap(0, dp(20), 0, 0));
        return home;
    }

    private View buildSongs(final boolean local) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);

        if (!local) {
            onlineStatus = text("Загрузка…", 13, MUTED);
            onlineStatus.setPadding(dp(14), dp(10), dp(14), dp(6));
            box.addView(onlineStatus);
        }

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0xFF222222));
        list.setDividerHeight(1);
        adapter = new SongAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                PlayerService.setQueue(shown);
                send(PlayerService.ACTION_PLAY_AT, position);
            }
        });
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(12), dp(6), dp(6), dp(6));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
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
        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View buildLogin() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = text("Вход по почте", 24, TEXT);
        box.addView(title, wrap(0, 0, 0, dp(16)));

        emailField = new EditText(this);
        emailField.setHint("Почта");
        emailField.setTextColor(TEXT);
        emailField.setHintTextColor(MUTED);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(emailField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        passField = new EditText(this);
        passField.setHint("Пароль (от 6 символов)");
        passField.setTextColor(TEXT);
        passField.setHintTextColor(MUTED);
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(passField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(bigButton("Войти", new View.OnClickListener() {
            public void onClick(View v) { authenticate(false); }
        }), new LinearLayout.LayoutParams(0, dp(50), 1f));
        buttons.addView(bigButton("Регистрация", new View.OnClickListener() {
            public void onClick(View v) { authenticate(true); }
        }), new LinearLayout.LayoutParams(0, dp(50), 1f));
        box.addView(buttons, wrap(0, dp(16), 0, 0));

        loginStatus = text("", 13, MUTED);
        box.addView(loginStatus, wrap(0, dp(14), 0, 0));

        Button back = smallButton("Назад", new View.OnClickListener() {
            public void onClick(View v) { show(HOME); }
        });
        box.addView(back, wrap(0, dp(20), 0, 0));
        return box;
    }

    private void openLocal() {
        if (!localLoaded) {
            loadLocal();
            localLoaded = true;
        }
        shown = localSongs;
        show(LOCAL);
        adapter.notifyDataSetChanged();
        updateBar();
    }

    private void openOnline() {
        shown = onlineSongs;
        show(ONLINE);
        adapter.notifyDataSetChanged();
        updateBar();
        if (onlineLoaded) return;
        onlineStatus.setText("Загрузка…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<Song> list = RemoteLib.load();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            onlineSongs.clear();
                            onlineSongs.addAll(list);
                            onlineLoaded = true;
                            onlineStatus.setText(list.isEmpty() ? "В онлайн-библиотеке пока пусто" : "");
                            if (screen == ONLINE) adapter.notifyDataSetChanged();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { onlineStatus.setText("Не удалось загрузить: " + e.getMessage()); }
                    });
                }
            }
        }).start();
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
                        public void run() { loginStatus.setText(e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void send(String action, int pos) {
        Intent i = new Intent(this, PlayerService.class).setAction(action).putExtra(PlayerService.EXTRA_POS, pos);
        startService(i);
    }

    private void loadLocal() {
        ContentResolver cr = getContentResolver();
        // Обложки альбомов: id альбома -> путь к картинке
        java.util.HashMap<Long, String> albumArt = new java.util.HashMap<Long, String>();
        Cursor a = cr.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART}, null, null, null);
        if (a != null) {
            while (a.moveToNext()) albumArt.put(a.getLong(0), a.getString(1));
            a.close();
        }
        Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE);
        if (c == null) return;
        int iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int iPath = c.getColumnIndex(MediaStore.Audio.Media.DATA);
        int iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
        while (c.moveToNext()) {
            String art = albumArt.get(c.getLong(iAlbum));
            localSongs.add(new Song(c.getString(iTitle), c.getString(iArtist), c.getString(iPath), art));
        }
        c.close();
        if (localSongs.isEmpty()) {
            Toast.makeText(this, "На телефоне не найдено музыки", Toast.LENGTH_LONG).show();
        }
    }

    private void updateBar() {
        if (barTitle == null) return;
        int current = PlayerService.current;
        if (current >= 0 && current < PlayerService.songs.size()) {
            Song s = PlayerService.songs.get(current);
            barTitle.setText(s.title == null ? "Без названия" : s.title);
            barArtist.setText(s.artist == null ? "" : s.artist);
        } else {
            barTitle.setText("Ничего не играет");
            barArtist.setText("");
        }
        barPlay.setText(PlayerService.isPlaying() ? "||" : ">");
    }

    @Override
    public void onBackPressed() {
        if (screen != HOME) {
            show(HOME);
        } else {
            super.onBackPressed();
        }
    }

    private class SongAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int position) { return shown.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView[] lines;
            ImageView cover;
            View row = convertView;
            if (row == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setPadding(dp(10), dp(8), dp(14), dp(8));
                cover = new ImageView(MainActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackgroundColor(0xFF222222);
                box.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(12), 0, 0, 0);
                TextView t1 = text("", 16, TEXT);
                TextView t2 = text("", 13, MUTED);
                t1.setSingleLine(true);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                t2.setSingleLine(true);
                t2.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(t1);
                texts.addView(t2);
                box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                lines = new TextView[]{t1, t2};
                box.setTag(new Object[]{lines, cover});
                row = box;
            } else {
                Object[] tag = (Object[]) row.getTag();
                lines = (TextView[]) tag[0];
                cover = (ImageView) tag[1];
            }
            Song s = shown.get(position);
            Covers.show(cover, s.art);
            int cur = PlayerService.current;
            boolean playingThis = cur >= 0 && cur < PlayerService.songs.size()
                    && PlayerService.songs.get(cur).path.equals(s.path);
            lines[0].setText(s.title == null ? "Без названия" : s.title);
            lines[0].setTextColor(playingThis ? ACCENT : TEXT);
            lines[1].setText(s.artist == null ? "" : s.artist);
            return row;
        }
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
