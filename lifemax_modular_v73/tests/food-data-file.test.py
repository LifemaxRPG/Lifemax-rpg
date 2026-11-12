"""Run with Python + Java 21; exercises FoodDataFile.java (native/src/.../app/fooddata, used by FoodDataPlugin) offline: reading the
staged file in chunks across its boundaries, the SHA-256 and size check, the manifest check, and the fixed GitHub addresses, plus
the plugin's source rules (single flight, fixed URLs, cap, progress, KeepAwake) and the FOOD_MAX_BYTES cap in DownloadRules."""
from pathlib import Path
import re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
native = root / 'android-wrapper/native'
rules = (native / 'src/main/java/com/lifemax/rpg/app/update/DownloadRules.java').read_text()
src = (native / 'src/main/java/com/lifemax/rpg/app/fooddata/FoodDataFile.java').read_text()
harness = r'''
package com.lifemax.rpg.app.fooddata;
import com.lifemax.rpg.app.update.DownloadRules;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
public class FoodDataFileCheck {
    static int failures = 0;
    static void require(boolean value, String message) {
        if (value) System.out.println("PASS " + message); else { failures++; System.out.println("FAIL " + message); }
    }
    static String why(Callable c) { try { c.run(); return null; } catch (IOException e) { return e.getMessage(); } }
    interface Callable { void run() throws IOException; }
    static String hex(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    public static void main(String[] args) throws Exception {
        byte[] body = new byte[2_500_000];
        new Random(3).nextBytes(body);
        File f = File.createTempFile("foods", ".jsonl.gz");
        Files.write(f.toPath(), body);
        String sha = hex(body);

        // chunks across file boundaries reassemble the file byte for byte
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        long off = 0; int reads = 0; boolean done = false;
        while (!done) {
            FoodDataFile.Chunk c = FoodDataFile.read(f, off, FoodDataFile.MAX_CHUNK);
            all.write(c.data); off += c.data.length; done = c.done; reads++;
            if (!done) require(c.data.length == FoodDataFile.MAX_CHUNK, "a chunk before the end is full length");
        }
        require(reads == 3 && Arrays.equals(all.toByteArray(), body), "2.5 MB comes back in three reads, byte for byte");
        FoodDataFile.Chunk tail = FoodDataFile.read(f, body.length - 10, 1000);
        require(tail.data.length == 10 && tail.done && Arrays.equals(tail.data, Arrays.copyOfRange(body, body.length - 10, body.length)), "a read that runs past the end is cut short and says done");
        FoodDataFile.Chunk exact = FoodDataFile.read(f, 0, body.length > FoodDataFile.MAX_CHUNK ? FoodDataFile.MAX_CHUNK : body.length);
        require(!exact.done, "a read that stops short of the end is not done");
        FoodDataFile.Chunk end = FoodDataFile.read(f, body.length, 5);
        require(end.data.length == 0 && end.done, "a read at the very end is empty and done");
        FoodDataFile.Chunk mid = FoodDataFile.read(f, 1_048_575, 3);
        require(Arrays.equals(mid.data, Arrays.copyOfRange(body, 1_048_575, 1_048_578)), "a read across a 1 MiB boundary is exact");
        // refusals
        require(FoodDataFile.MAX_CHUNK == 1 << 20, "the largest read is 1 MiB");
        require(why(() -> FoodDataFile.read(f, 0, FoodDataFile.MAX_CHUNK + 1)) != null, "a read over 1 MiB is refused");
        require(why(() -> FoodDataFile.read(f, 0, 0)) != null && why(() -> FoodDataFile.read(f, 0, -5)) != null, "an empty or negative length is refused");
        require(why(() -> FoodDataFile.read(f, -1, 10)) != null && why(() -> FoodDataFile.read(f, body.length + 1, 10)) != null, "a position outside the file is refused");
        File none = new File(f.getParentFile(), "not-staged-" + System.nanoTime());
        require(String.valueOf(why(() -> FoodDataFile.read(none, 0, 10))).contains("no downloaded"), "no staged file is refused in plain words");

        // verification
        require(FoodDataFile.sha256(f).equals(sha), "the SHA-256 matches an independent hash");
        FoodDataFile.verify(f, body.length, sha.toUpperCase());
        require(f.exists(), "a file that matches is kept (upper-case fingerprints are fine)");
        String bad = why(() -> FoodDataFile.verify(f, body.length, "0".repeat(64)));
        require(bad != null && bad.contains("checksum") && !f.exists(), "a wrong fingerprint deletes the file and says so");
        Files.write(f.toPath(), body);
        bad = why(() -> FoodDataFile.verify(f, body.length + 1, sha));
        require(bad != null && !f.exists(), "a wrong size deletes the file");
        Files.write(f.toPath(), Arrays.copyOf(body, body.length - 1));
        bad = why(() -> FoodDataFile.verify(f, body.length, sha));
        require(bad != null && !f.exists(), "a truncated file is refused");

        // the manifest's claims
        long cap = DownloadRules.FOOD_MAX_BYTES;
        require(FoodDataFile.checkManifest(432_470, 17_756_641, sha.toUpperCase(), cap).equals(sha), "a good manifest passes and the fingerprint is lower-cased");
        require(why(() -> FoodDataFile.checkManifest(0, 100, sha, cap)) != null, "no foods is refused");
        require(why(() -> FoodDataFile.checkManifest(5, 0, sha, cap)) != null, "no bytes is refused");
        require(why(() -> FoodDataFile.checkManifest(5, 100, "abc", cap)) != null && why(() -> FoodDataFile.checkManifest(5, 100, null, cap)) != null, "a missing or short fingerprint is refused");
        require(why(() -> FoodDataFile.checkManifest(5, cap + 1, sha, cap)) != null, "a size over the cap is refused");
        require(cap == 120L * 1024 * 1024 && cap >= 4 * 17_756_641L, "the cap is 120 MB, room for several times today's file");

        // the addresses are fixed, on github.com, and satisfy the download rules
        require(DownloadRules.isStart(FoodDataFile.MANIFEST_URL) && DownloadRules.isStart(FoodDataFile.FILE_URL), "both addresses start at github.com over https");
        require(FoodDataFile.FILE_URL.endsWith("/usda-foods/usda-branded-foods.jsonl.gz") && FoodDataFile.MANIFEST_URL.endsWith("/usda-foods/usda-branded-foods.json"), "they name the usda-foods release files");
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
        System.out.println("PASS: food data file chunks, checksum and size verification, caps");
    }
}
'''
with tempfile.TemporaryDirectory() as d:
    base = Path(d) / 'src/com/lifemax/rpg/app'
    (base / 'update').mkdir(parents=True); (base / 'fooddata').mkdir()
    (base / 'update/DownloadRules.java').write_text(rules)
    (base / 'fooddata/FoodDataFile.java').write_text(src)
    (base / 'fooddata/FoodDataFileCheck.java').write_text(harness)
    out = Path(d) / 'out'
    subprocess.run(['javac', '-d', str(out), *map(str, [base / 'update/DownloadRules.java', base / 'fooddata/FoodDataFile.java', base / 'fooddata/FoodDataFileCheck.java'])], check=True, timeout=120)
    subprocess.run(['java', '-cp', str(out), 'com.lifemax.rpg.app.fooddata.FoodDataFileCheck'], check=True, timeout=60)

# the plugin goes through the rules and keeps the page away from addresses
plugin = (native / 'FoodDataPlugin.java').read_text()
main = (native / 'MainActivity.java').read_text()
fails = []
def check(cond, msg):
    print(('PASS ' if cond else 'FAIL ') + msg)
    if not cond: fails.append(msg)
check(plugin.count('DownloadRules.open(') == 1 and plugin.count('DownloadRules.fetch(') == 1 and 'openConnection' not in plugin, 'FoodDataPlugin downloads the manifest and the file only through DownloadRules')
check('call.getString' not in plugin and 'getString("url")' not in plugin and 'FoodDataFile.FILE_URL' in plugin and 'FoodDataFile.MANIFEST_URL' in plugin, 'the page cannot supply an address')
check(re.search(r'AtomicBoolean DOWNLOADING = new AtomicBoolean\(false\)', plugin) and 'compareAndSet(false, true)' in plugin, 'one download at a time')
check('DownloadRules.FOOD_MAX_BYTES' in plugin and 'KeepAwake.start' in plugin and 'awake.release()' in plugin and 'notifyListeners("progress"' in plugin, 'size cap, screen kept awake, progress events')
check('FoodDataFile.verify(' in plugin and plugin.count('file.delete()') >= 2, 'the file is verified, and deleted on any failure')
check('registerPlugin(FoodDataPlugin.class)' in main, 'FoodDataPlugin is registered in MainActivity')
check(all(f'public void {m}(' in plugin for m in ['check', 'download', 'read', 'discard']), 'check, download, read and discard exist')
if fails:
    raise SystemExit(f'{len(fails)} failed')
