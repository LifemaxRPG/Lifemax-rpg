"""Run with Python + Java 21; exercises the updater's actual HTTP methods offline."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/'android-wrapper/native/UpdaterPlugin.java').read_text()
methods=s[s.index('    private HttpURLConnection open('):s.index('    @PluginMethod\n    public void check')]
harness=r'''
import java.net.*;
import java.util.*;
class UpdaterNetworkCheck {
    static final List<Fake> requests=new ArrayList<>();
    static class Fake extends HttpURLConnection {
        Fake(URL u){super(u);requests.add(this);}
        public void connect(){}
        public void disconnect(){}
        public boolean usingProxy(){return false;}
        public int getResponseCode(){return url.getHost().equals("github.com")?302:200;}
        public String getHeaderField(String name){return name.equals("Location")?"https://storage.test/signed?signature=keep-me":null;}
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
            protected URLConnection openConnection(URL u){return new Fake(u);}
        }:null);
        var app=new UpdaterNetworkCheck();
        String base="https://github.com/example/releases/download/latest/version.json";
        String a=freshUrl(base),b=freshUrl(base);
        require(!a.equals(b),"Every check must have a new cache key");
        require(a.startsWith(base+"?lma_check="),"Original manifest URL must be preserved");
        require(freshUrl(base+"?existing=yes").contains("?existing=yes&lma_check="),"Preserve existing query parameters");
        app.open(a);app.open(b);
        require(requests.size()==4,"Both checks must follow the redirect");
        for(Fake c:requests){
            require(!c.getUseCaches(),"Never reuse a cached HTTP response");
            require(!c.getInstanceFollowRedirects(),"Redirects remain explicit");
            if(c.getURL().getHost().equals("github.com"))require(c.getRequestProperty("Cache-Control").contains("no-store"),"Revalidate mutable release URLs");
            else {
                require(c.getURL().getQuery().equals("signature=keep-me"),"Do not rewrite a signed redirect URL");
                require(c.getRequestProperty("Cache-Control")==null,"Do not forward metadata headers to signed storage");
            }
        }
        System.out.println("PASS: fresh check URLs, cache bypass, preserved queries and clean signed redirects");
    }
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'UpdaterNetworkCheck.java';p.write_text(harness+methods+'\n}')
 subprocess.run(['java',str(p)],check=True,timeout=30)
