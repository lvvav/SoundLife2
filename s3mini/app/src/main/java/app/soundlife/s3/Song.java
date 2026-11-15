package app.soundlife.s3;

/** Трек с телефона. */
public class Song {
    public final String title;
    public final String artist;
    public final String path;

    public Song(String title, String artist, String path) {
        this.title = title;
        this.artist = artist;
        this.path = path;
    }
}
