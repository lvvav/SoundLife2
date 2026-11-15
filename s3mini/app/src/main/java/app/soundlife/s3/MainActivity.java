package app.soundlife.s3;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/** Лёгкая версия SoundLife для старых телефонов (Android 4.1+): музыка, скачанная на телефон. */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF33B5E5;   // голубой Holo
    private static final int BG = 0xFF0E0E0E;
    private static final int BAR = 0xFF1A1A1A;
    private static final int TEXT = 0xFFEEEEEE;
    private static final int MUTED = 0xFF888888;

    private boolean loaded = false;
    private LinearLayout home;
    private LinearLayout musicScreen;
    private SongAdapter adapter;
    private TextView barTitle;
    private TextView barArtist;
    private Button barPlay;

    private final PlayerService.Listener playerListener = new PlayerService.Listener() {
        public void onChanged() {
            if (adapter != null) adapter.notifyDataSetChanged();
            updateBar();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("SoundLife");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(buildHome(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildMusic(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.setListener(playerListener);
        if (adapter != null) adapter.notifyDataSetChanged();
        updateBar();
    }

    @Override
    protected void onPause() {
        PlayerService.setListener(null);
        super.onPause();
    }

    private View buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        home.setBackgroundColor(BG);
        home.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = text("SoundLife", 30, TEXT);
        title.setGravity(Gravity.CENTER);
        home.addView(title);

        TextView hint = text("музыка, которая уже на телефоне", 14, MUTED);
        hint.setGravity(Gravity.CENTER);
        home.addView(hint, wrap(0, dp(6), 0, dp(30)));

        Button mine = button("Моя музыка");
        mine.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMusic();
            }
        });
        home.addView(mine, new LinearLayout.LayoutParams(dp(230), dp(56)));
        return home;
    }

    private View buildMusic() {
        musicScreen = new LinearLayout(this);
        musicScreen.setOrientation(LinearLayout.VERTICAL);
        musicScreen.setBackgroundColor(BG);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0xFF222222));
        list.setDividerHeight(1);
        adapter = new SongAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                send(PlayerService.ACTION_PLAY_AT, position);
            }
        });
        musicScreen.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
        musicScreen.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return musicScreen;
    }

    private void send(String action, int pos) {
        Intent i = new Intent(this, PlayerService.class).setAction(action).putExtra(PlayerService.EXTRA_POS, pos);
        startService(i);
    }

    private void showHome() {
        home.setVisibility(View.VISIBLE);
        musicScreen.setVisibility(View.GONE);
    }

    private void showMusic() {
        if (!loaded) {
            loadSongs();
            loaded = true;
        }
        home.setVisibility(View.GONE);
        musicScreen.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
        updateBar();
    }

    private void loadSongs() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE);
        if (c == null) return;
        int iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int iPath = c.getColumnIndex(MediaStore.Audio.Media.DATA);
        PlayerService.songs.clear();
        while (c.moveToNext()) {
            PlayerService.songs.add(new Song(c.getString(iTitle), c.getString(iArtist), c.getString(iPath)));
        }
        c.close();
        if (PlayerService.songs.isEmpty()) {
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
        if (musicScreen.getVisibility() == View.VISIBLE) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private class SongAdapter extends BaseAdapter {
        public int getCount() { return PlayerService.songs.size(); }
        public Object getItem(int position) { return PlayerService.songs.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView[] lines;
            View row = convertView;
            if (row == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(14), dp(10), dp(14), dp(10));
                TextView t1 = text("", 16, TEXT);
                TextView t2 = text("", 13, MUTED);
                t1.setSingleLine(true);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                t2.setSingleLine(true);
                t2.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t1);
                box.addView(t2);
                lines = new TextView[]{t1, t2};
                box.setTag(lines);
                row = box;
            } else {
                lines = (TextView[]) row.getTag();
            }
            Song s = PlayerService.songs.get(position);
            lines[0].setText(s.title == null ? "Без названия" : s.title);
            lines[0].setTextColor(position == PlayerService.current ? ACCENT : TEXT);
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

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(ACCENT);
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
