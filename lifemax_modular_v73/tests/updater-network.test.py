"""Run with Python + Java 21; exercises the over-the-air download rules offline (DownloadRules.java in native/src/.../app/update,
used by UpdaterPlugin for version.json and the APK and by LiveWebPlugin for the web bundle): fresh check URLs, cache bypass,
clean signed redirects, GitHub-only hosts on every hop (backlog SEC-1) and the size caps (SEC-5)."""
from pathlib import Path
import re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
native = root / 'android-wrapper/native'
rules = (native / 'src/main/java/com/lifemax/rpg/app/update/DownloadRules.java').read_text()
harness = r'''
package com.lifemax.rpg.app.update;
import java.io.*;
import java.net.*;
import java.util.*;
public class DownloadRulesCheck {
    static final List<Fake> requests = new ArrayList<>();
    static final Map<String, String> redirects = new HashMap<>();   // host, or host + path, -> Location it answers with
    static byte[] body = new byte[0];
    static long declared = -1;
    static class Fake extends HttpURLConnection {
        Fake(URL u) { super(u); requests.add(this); }
        public void connect() {}
        public void disconnect() {}
        public boolean usingProxy() { return false; }
        String loc() { String k = url.getHost() + url.getPath(); return redirects.containsKey(k) ? redirects.get(k) : redirects.get(url.getHost()); }
        public int getResponseCode() { return loc() != null ? 302 : 200; }
        public String getHeaderField(String name) { return name.equals("Location") ? loc() : null; }
        public long getContentLengthLong() { return declared; }
        public InputStream getInputStream() { return new ByteArrayInputStream(body); }
    }
    static int failures = 0;
    static void require(boolean value, String message) {
        if (value) System.out.println("PASS " + message); else { failures++; System.out.println("FAIL " + message); }
    }
    static String refused(String url) {
        try { DownloadRules.open(url); return null; } catch (IOException e) { return e.getMessage(); }
    }
    static List<String> hosts() { List<String> h = new ArrayList<>(); for (Fake c : requests) h.add(c.getURL().getHost()); return h; }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            protected URLConnection openConnection(URL u) { return new Fake(u); }
        } : null);
        String base = "https://github.com/LifemaxRPG/Lifemax-rpg/releases/download/latest/";
        String signed = "https://release-assets.githubusercontent.com/github-production-release-asset/1/abc?sp=r&sig=keep-me";

        String a = DownloadRules.freshUrl(base + "version.json"), b = DownloadRules.freshUrl(base + "version.json");
        require(!a.equals(b), "every check gets a new cache key");
        require(a.startsWith(base + "version.json?lma_check="), "the original manifest URL is preserved");
        require(DownloadRules.freshUrl(base + "x?existing=yes").contains("?existing=yes&lma_check="), "existing query parameters are kept");

        // the real path: github.com answers with one redirect to signed storage, which serves the file
        redirects.put("github.com", signed);
        DownloadRules.open(a); DownloadRules.open(b);
        require(requests.size() == 4, "both checks follow the redirect to release-assets.githubusercontent.com");
        for (Fake c : requests) {
            require(!c.getUseCaches(), "no cached HTTP response is reused (" + c.getURL().getHost() + ")");
            require(!c.getInstanceFollowRedirects(), "redirects stay explicit, so each hop is checked first");
            if (c.getURL().getHost().equals("github.com"))
                require(c.getRequestProperty("Cache-Control").contains("no-store"), "the mutable release URL is revalidated");
            else {
                require(c.getURL().getQuery().equals("sp=r&sig=keep-me"), "the signed redirect URL is not rewritten");
                require(c.getRequestProperty("Cache-Control") == null, "no metadata headers go to signed storage");
            }
        }
        for (String storage : new String[] {"https://objects.githubusercontent.com/github-production-release-asset-2e65be/1?x=1",
                "https://RELEASE-ASSETS.githubusercontent.com/a"}) {
            requests.clear(); redirects.put("github.com", storage);
            require(refused(base + "app.html") == null && requests.size() == 2, "GitHub storage is allowed: " + storage);
        }
        // a renamed repository: github.com first redirects (relative) to the new name on github.com, then to storage
        requests.clear(); redirects.clear();
        redirects.put("github.com/LifemaxRPG/Lifemax-rpg/releases/download/latest/app.html", "/LifemaxRPG/New-name/releases/download/latest/app.html");
        redirects.put("github.com/LifemaxRPG/New-name/releases/download/latest/app.html", signed);
        require(refused(base + "app.html") == null && hosts().equals(List.of("github.com", "github.com", "release-assets.githubusercontent.com")),
                "a redirect within github.com, then to storage, is followed");

        // hostile redirects are refused before anything is sent to them
        String[] bad = {"https://evil.example/app.html", "http://release-assets.githubusercontent.com/a",
                "https://githubusercontent.com.evil.example/a", "https://evilgithubusercontent.com/a",
                "https://release-assets.githubusercontent.com:8443/a", "https://user@release-assets.githubusercontent.com/a",
                "ftp://release-assets.githubusercontent.com/a"};
        for (String to : bad) {
            requests.clear(); redirects.clear(); redirects.put("github.com", to);
            String why = refused(base + "app.html");
            require(why != null && why.contains("not GitHub") && hosts().equals(List.of("github.com")),
                    "a redirect to " + to + " is refused without contacting it (" + why + ")");
        }
        require(String.valueOf(refused(base + "app.html")).indexOf("keep-me") < 0, "an error never quotes a signed query");

        // the first request must be github.com itself
        String[] badStart = {"https://example.com/app.html", "http://github.com/x", "https://github.com.evil.example/x",
                "https://github.com@evil.example/x", "https://github.com:8443/x", "https://objects.githubusercontent.com/x",
                "https://raw.githubusercontent.com/x", "file:///data/x", "not a url", ""};
        for (String s : badStart) {
            requests.clear(); redirects.clear();
            require(!DownloadRules.isStart(s) && refused(s) != null && requests.isEmpty(), "a download may not start at '" + s + "'");
        }
        require(!DownloadRules.isStart((String) null), "a missing URL is not a start");
        require(DownloadRules.isStart(base + "app.html") && DownloadRules.isStart("https://GitHub.com/a/b"), "github.com is a start");

        // loops end
        requests.clear(); redirects.clear(); redirects.put("github.com", "https://github.com/again");
        require(String.valueOf(refused(base + "app.html")).contains("too many redirects") && requests.size() == 6, "a redirect loop stops after six hops");

        // size caps
        redirects.clear();
        body = new byte[1000]; declared = 1000;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> pct = new ArrayList<>();
        long n = DownloadRules.copy(DownloadRules.open(base + "app.html"), out, 1000, pct::add);
        require(n == 1000 && out.size() == 1000 && pct.get(pct.size() - 1) == 100, "a body within the cap is copied whole with progress to 100");
        declared = 5_000_000_000L; out.reset();
        try { DownloadRules.copy(DownloadRules.open(base + "app.html"), out, 1000, null); require(false, "a declared size over the cap is refused"); }
        catch (IOException e) { require(out.size() == 0 && e.getMessage().contains("too large"), "a declared size over the cap is refused before reading"); }
        declared = -1; out.reset(); pct.clear();
        try { DownloadRules.copy(DownloadRules.open(base + "app.html"), out, 999, pct::add); require(false, "an undeclared body over the cap is refused"); }
        catch (IOException e) { require(out.size() <= 999, "an undeclared body over the cap is refused while counting"); }
        require(DownloadRules.WEB_MAX_BYTES >= 4 * 13_802_259L, "the web cap leaves room for four times today's app.html");
        require(DownloadRules.APK_MAX_BYTES >= 4 * 28_637_916L, "the APK cap leaves room for four times today's APK");
        require(DownloadRules.MANIFEST_MAX_BYTES >= 100 * 1024, "the manifest cap leaves room for a much larger version.json");
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
        System.out.println("PASS: GitHub-only download rules, fresh check URLs, clean signed redirects and size caps");
    }
}
'''
with tempfile.TemporaryDirectory() as d:
    pkg = Path(d) / 'src/com/lifemax/rpg/app/update'; pkg.mkdir(parents=True)
    (pkg / 'DownloadRules.java').write_text(rules)
    (pkg / 'DownloadRulesCheck.java').write_text(harness)
    out = Path(d) / 'out'
    subprocess.run(['javac', '-d', str(out), str(pkg / 'DownloadRules.java'), str(pkg / 'DownloadRulesCheck.java')], check=True, timeout=120)
    subprocess.run(['java', '-cp', str(out), 'com.lifemax.rpg.app.update.DownloadRulesCheck'], check=True, timeout=60)

# the code that downloads goes through the rules, and the published URLs satisfy them
updater = (native / 'UpdaterPlugin.java').read_text()
live = (native / 'LiveWebPlugin.java').read_text()
wf = (root / '.github/workflows/build-android.yml').read_text()
fails = []
def check(cond, msg):
    print(('PASS ' if cond else 'FAIL ') + msg)
    if not cond: fails.append(msg)
check(updater.count('DownloadRules.open(') == 2 and 'openConnection' not in updater, 'UpdaterPlugin downloads version.json and the APK only through DownloadRules.open')
check(live.count('DownloadRules.open(') == 1 and 'openConnection' not in live and 'setInstanceFollowRedirects(true)' not in live, 'LiveWebPlugin downloads the web bundle only through DownloadRules.open (no automatic redirects)')
check('DownloadRules.isStart(url)' in updater and 'DownloadRules.isStart(url)' in live, 'both plugins refuse a non-GitHub address before starting')
check('WEB_MAX_BYTES' in live and 'APK_MAX_BYTES' in updater and 'MANIFEST_MAX_BYTES' in updater, 'every download has its size cap')
check(re.search(r'AtomicBoolean \w+ = new AtomicBoolean\(false\)', updater) and re.search(r'AtomicBoolean \w+ = new AtomicBoolean\(false\)', live), 'install and fetch each have an in-progress guard')
check('did not match its fingerprint' in live and 'LiveWeb.sha256(tmp)' in live, 'the web bundle is still checked against its SHA-256')
m = re.search(r'VERSION_URL =\s*"([^"]+)"', updater)
check(bool(m) and m.group(1).startswith('https://github.com/'), 'the version.json address is on github.com')
bases = re.findall(r'BASE="([^"]+)"', wf)
check(bool(bases) and all(b.startswith('https://github.com/$GITHUB_REPOSITORY/releases/download/') for b in bases), 'CI publishes every download address on github.com releases')
if fails:
    raise SystemExit(f'{len(fails)} failed')
