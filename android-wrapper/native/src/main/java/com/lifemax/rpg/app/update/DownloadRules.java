package com.lifemax.rpg.app.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

/**
 * Where over-the-air downloads may come from and how large they may be (backlog SEC-1 and SEC-5).
 *
 * CI publishes version.json, app.html and LifeMax-RPG.apk to the GitHub release "latest"
 * (.github/workflows/build-android.yml), so every URL in version.json is
 * https://github.com/OWNER/REPO/releases/download/latest/FILE. github.com answers with one redirect to signed storage on
 * release-assets.githubusercontent.com (objects.githubusercontent.com before 2025), which serves the file.
 * The first request must therefore go to github.com, and every redirect must stay on github.com or a
 * *.githubusercontent.com host, always over https. That is deliberately wider than the storage hosts GitHub uses today, so
 * a move to another one never stops updates; the integrity of a download rests on the SHA-256 check of the web bundle in
 * LiveWebPlugin and on Android's signature check of the APK.
 *
 * Plain Java with no Android types, so tests/updater-network.test.py runs it against a fake network.
 */
public final class DownloadRules {
    /** version.json is under 1 KB. */
    public static final long MANIFEST_MAX_BYTES = 256L * 1024;
    /** app.html is about 14 MB (build 277). */
    public static final long WEB_MAX_BYTES = 80L * 1024 * 1024;
    /** The APK is about 29 MB (build 277). */
    public static final long APK_MAX_BYTES = 300L * 1024 * 1024;

    private static final int MAX_HOPS = 6;

    /** Receives the download progress in percent, or -1 when the size is unknown. */
    public interface Progress {
        void percent(int pct);
    }

    private DownloadRules() {}

    /** The first request: github.com itself, over https. */
    private static boolean isStart(URL u) {
        return isHttps(u) && "github.com".equals(host(u));
    }

    public static boolean isStart(String url) {
        try {
            return isStart(new URL(url));
        } catch (MalformedURLException | RuntimeException e) {
            return false;
        }
    }

    /** A redirect target: github.com again, or GitHub's file storage under githubusercontent.com, over https. */
    private static boolean isHop(URL u) {
        if (!isHttps(u)) return false;
        String h = host(u);
        return "github.com".equals(h) || h.endsWith(".githubusercontent.com");
    }

    private static boolean isHttps(URL u) {
        return u != null
                && "https".equalsIgnoreCase(u.getProtocol())
                && u.getUserInfo() == null
                && (u.getPort() == -1 || u.getPort() == 443);
    }

    private static String host(URL u) {
        String h = u.getHost();
        return h == null ? "" : h.toLowerCase(Locale.ROOT);
    }

    /** Scheme and host only: a signed storage address carries a token in its query that must not end up in a message. */
    private static String where(URL u) {
        return u == null ? "nowhere" : u.getProtocol() + "://" + u.getHost();
    }

    // The release tag is reused. A unique URL prevents Android and GitHub's
    // redirect caches from answering a manual check with an older manifest.
    public static String freshUrl(String url) {
        return url + (url.contains("?") ? "&" : "?") + "lma_check=" + UUID.randomUUID();
    }

    /*
     * Follows redirects by hand so each hop is checked before it is contacted, and each hop is a fresh, plain request:
     * GitHub sends release downloads to a signed storage address that rejects extra headers (it answers "bad request").
     */
    public static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!isStart(u)) throw new IOException("updates are only downloaded from https://github.com, not " + where(u));
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            if (hop == 0) {
                c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                c.setRequestProperty("Pragma", "no-cache");
            }
            c.setRequestProperty("User-Agent", "LifeMax-Updater");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) throw new IOException("redirect without an address");
                u = new URL(u, loc);
                if (!isHop(u)) throw new IOException("the download was redirected to " + where(u) + ", which is not GitHub");
                continue;
            }
            return c;
        }
        throw new IOException("too many redirects");
    }

    /** Copies a response body, refusing one larger than max bytes, whether declared up front or counted. Returns its size. */
    public static long copy(HttpURLConnection c, OutputStream out, long max, Progress progress) throws IOException {
        long total = c.getContentLengthLong();
        if (total > max) throw new IOException(tooBig(max));
        long got = 0;
        int last = -2;
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                got += n;
                if (got > max) throw new IOException(tooBig(max));
                out.write(buf, 0, n);
                int pct = total > 0 ? (int) (got * 100 / total) : -1;
                if (progress != null && pct != last) progress.percent(pct);
                last = pct;
            }
        }
        return got;
    }

    private static String tooBig(long max) {
        return "the download is over " + (max >> 20) + " MB, too large to be a LifeMax build";
    }
}
