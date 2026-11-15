package app.soundlife.s3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Обложки: маленькие картинки, кэш в памяти (на слабом телефоне важно не переполнить память). */
final class Covers {

    static final int SIZE = 96;
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Covers() {}

    static Bitmap cached(String key) {
        return key == null ? null : cache.get(key);
    }

    /** Показывает обложку в ImageView. Для ссылок грузит в фоне; если строка уже сменилась, результат не ставит. */
    static void show(ImageView view, String key) {
        view.setTag(key);
        view.setImageBitmap(null);
        if (key == null) return;
        Bitmap b = cache.get(key);
        if (b != null) {
            view.setImageBitmap(b);
            return;
        }
        new Loader(view, key).execute();
    }

    private static class Loader extends AsyncTask<Void, Void, Bitmap> {
        private final ImageView view;
        private final String key;

        Loader(ImageView view, String key) {
            this.view = view;
            this.key = key;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                byte[] data = key.startsWith("http") ? download(key) : fileBytes(key);
                return decode(data);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) return;
            cache.put(key, b);
            if (key.equals(view.getTag())) view.setImageBitmap(b);
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    private static byte[] fileBytes(String path) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /** Декодирует картинку уменьшенной, чтобы не тратить память. */
    static Bitmap decode(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > SIZE * 2) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }
}
