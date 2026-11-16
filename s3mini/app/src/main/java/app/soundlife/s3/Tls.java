package app.soundlife.s3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * На Android 4.1 TLS 1.2 есть, но по умолчанию выключен. GitHub, Supabase и Яндекс
 * его требуют, поэтому рукопожатие обрывается. Этот класс включает TLS 1.2 для всех
 * HTTPS-запросов приложения (HttpURLConnection и HttpsURLConnection).
 */
final class Tls {

    private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

    private Tls() {}

    static void install() {
        try {
            SSLContext ctx;
            try {
                ctx = SSLContext.getInstance("TLSv1.2");
            } catch (Exception e) {
                ctx = SSLContext.getInstance("TLS");
            }
            ctx.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(new TlsFactory(ctx.getSocketFactory()));
        } catch (Exception ignored) {
            // Если платформа не поддерживает TLS вообще, оставляем системные настройки.
        }
    }

    /** Текст для показа пользователю: для ошибок SSL — понятная фраза, иначе — обычный текст. */
    static String text(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof javax.net.ssl.SSLException) {
                String detail = t.getMessage() == null ? "" : t.getMessage();
                if (detail.length() > 160) detail = detail.substring(0, 160);
                return "Не удалось установить защищённое соединение (TLS). Проверьте интернет и попробуйте ещё раз. Техническая причина: " + detail;
            }
        }
        return e.getMessage();
    }

    private static final class TlsFactory extends SSLSocketFactory {

        private final SSLSocketFactory base;

        TlsFactory(SSLSocketFactory base) {
            this.base = base;
        }

        private static Socket prepare(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                ssl.setEnabledProtocols(PROTOCOLS);
                String[] suites = preferred(ssl.getSupportedCipherSuites());
                if (suites.length > 0) ssl.setEnabledCipherSuites(suites);
            }
            return s;
        }

        /** Современные шифры (ECDHE + AES), которые принимают GitHub и Supabase. Слабые и экспортные отбрасываем. */
        private static String[] preferred(String[] supported) {
            java.util.List<String> out = new java.util.ArrayList<String>();
            for (String c : supported) {
                if (c.contains("ECDHE") && c.contains("AES") && !c.contains("anon") && !c.contains("NULL") && !c.contains("EXPORT")) {
                    out.add(c);
                }
            }
            return out.toArray(new String[out.size()]);
        }

        @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }

        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return prepare(base.createSocket(s, host, port, autoClose));
        }

        @Override public Socket createSocket(String host, int port) throws IOException {
            return prepare(base.createSocket(host, port));
        }

        @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return prepare(base.createSocket(host, port, localHost, localPort));
        }

        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return prepare(base.createSocket(host, port));
        }

        @Override public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return prepare(base.createSocket(host, port, localHost, localPort));
        }
    }
}
