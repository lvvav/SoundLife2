package app.soundlife.s3;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Воспроизведение в фоне: играет, даже когда экран выключен или приложение свернуто. */
public class PlayerService extends Service {

    static final String ACTION_PLAY_AT = "app.soundlife.s3.PLAY_AT";
    static final String ACTION_TOGGLE = "app.soundlife.s3.TOGGLE";
    static final String ACTION_NEXT = "app.soundlife.s3.NEXT";
    static final String ACTION_PREV = "app.soundlife.s3.PREV";
    static final String ACTION_STOP = "app.soundlife.s3.STOP";
    static final String EXTRA_POS = "pos";
    private static final int NOTE_ID = 1;

    /** Плейлист и состояние видны и экрану, и уведомлению. */
    static final List<Song> songs = new ArrayList<Song>();
    static int current = -1;
    private static MediaPlayer player;
    private static Listener listener;

    public interface Listener {
        void onChanged();
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY_AT.equals(action)) {
            playAt(intent.getIntExtra(EXTRA_POS, 0));
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            relative(1);
        } else if (ACTION_PREV.equals(action)) {
            relative(-1);
        } else if (ACTION_STOP.equals(action)) {
            stopAll();
        }
        return START_NOT_STICKY;
    }

    private void playAt(int pos) {
        if (pos < 0 || pos >= songs.size()) return;
        release();
        Song s = songs.get(pos);
        try {
            player = new MediaPlayer();
            player.setDataSource(s.path);
            player.prepare();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    relative(1);
                }
            });
            player.start();
            current = pos;
        } catch (Exception e) {
            release();
            current = -1;
            Toast.makeText(this, "Не получилось открыть файл", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void toggle() {
        if (player == null) {
            if (!songs.isEmpty()) playAt(0);
            return;
        }
        if (player.isPlaying()) player.pause(); else player.start();
        refresh();
    }

    private void relative(int delta) {
        int next = current + delta;
        if (next < 0 || next >= songs.size()) {
            stopAll();
            return;
        }
        playAt(next);
    }

    private void stopAll() {
        release();
        current = -1;
        stopForeground(true);
        refresh();
        stopSelf();
    }

    private void release() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    /** Обновляет уведомление и экран после каждого изменения. */
    private void refresh() {
        if (current >= 0 && current < songs.size()) {
            startForeground(NOTE_ID, buildNotification());
        }
        if (listener != null) listener.onChanged();
    }

    private Notification buildNotification() {
        Song s = songs.get(current);
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(s.title == null ? "Без названия" : s.title)
                .setContentText(s.artist == null ? "" : s.artist)
                .setOngoing(isPlaying())
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0));
        b.addAction(android.R.drawable.ic_media_previous, "Назад", action(ACTION_PREV, 2));
        b.addAction(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying() ? "Пауза" : "Играть", action(ACTION_TOGGLE, 3));
        b.addAction(android.R.drawable.ic_media_next, "Далее", action(ACTION_NEXT, 4));
        return b.build();
    }

    private PendingIntent action(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, PlayerService.class).setAction(action), 0);
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }
}
