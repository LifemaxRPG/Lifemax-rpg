package com.lifemax.rpg.app.fooddata;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * The USDA branded-foods file the Food database card downloads from GitHub: where it comes from, how it is checked and how
 * the page reads it back in pieces. Plain Java with no Android types, so lifemax_modular_v73/tests/food-data-file.test.py
 * runs it offline. The downloading itself goes through DownloadRules (GitHub-only hosts, size cap, resume).
 *
 * The release "usda-foods" is built by tools/build_usda_condensed.py (workflow "Publish USDA foods"). The page never sends
 * an address: both are fixed here.
 */
public final class FoodDataFile {
    public static final String MANIFEST_URL =
            "https://github.com/LifemaxRPG/Lifemax-rpg/releases/download/usda-foods/usda-branded-foods.json";
    public static final String FILE_URL =
            "https://github.com/LifemaxRPG/Lifemax-rpg/releases/download/usda-foods/usda-branded-foods.jsonl.gz";
    public static final String FILE_NAME = "usda-branded-foods.jsonl.gz";
    /** The most the page may ask for in one read. */
    public static final int MAX_CHUNK = 1024 * 1024;

    private FoodDataFile() {}

    /** One piece of the staged file; [done] when it reaches the end. */
    public static final class Chunk {
        public final byte[] data;
        public final boolean done;

        Chunk(byte[] data, boolean done) {
            this.data = data;
            this.done = done;
        }
    }

    /** Checks what the manifest claims before anything is downloaded against it. Returns the lower-case fingerprint. */
    public static String checkManifest(long foods, long bytes, String sha256, long maxBytes) throws IOException {
        String sha = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        if (foods <= 0 || bytes <= 0 || !sha.matches("[0-9a-f]{64}"))
            throw new IOException("The food list on GitHub is not ready. Try again later.");
        if (bytes > maxBytes) throw new IOException("The food list on GitHub is larger than this app accepts.");
        return sha;
    }

    public static String sha256(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Whether the file has the size and SHA-256 the manifest names. A file that does not is deleted. */
    public static void verify(File file, long bytes, String sha256) throws IOException {
        if (file.length() != bytes || !sha256(file).equalsIgnoreCase(sha256)) {
            file.delete();
            throw new IOException("The downloaded file did not match its checksum. Try again.");
        }
    }

    /** [length] bytes from [offset] (fewer at the end of the file). */
    public static Chunk read(File file, long offset, long length) throws IOException {
        if (!file.isFile()) throw new IOException("There is no downloaded food list to read.");
        if (length <= 0 || length > MAX_CHUNK) throw new IOException("A read may ask for 1 to " + MAX_CHUNK + " bytes.");
        long size = file.length();
        if (offset < 0 || offset > size) throw new IOException("That position is outside the downloaded file.");
        int n = (int) Math.min(length, size - offset);
        byte[] data = new byte[n];
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            f.seek(offset);
            f.readFully(data);
        }
        return new Chunk(data, offset + n >= size);
    }
}
