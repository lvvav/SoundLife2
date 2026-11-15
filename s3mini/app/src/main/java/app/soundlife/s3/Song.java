package app.soundlife.s3;

/** Трек: файл на телефоне или ссылка из онлайн-библиотеки. art — обложка (путь к файлу или ссылка), может быть пустой. */
public class Song {
    public final String title;
    public final String artist;
    public final String path;
    public final String art;

    public Song(String title, String artist, String path, String art) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.art = art;
    }
}
