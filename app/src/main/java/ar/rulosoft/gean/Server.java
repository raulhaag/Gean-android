package ar.rulosoft.gean;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;

public class Server implements HttpHandler {
    private HttpServer server;
    private Activity parent;
    private String wpath = "./www";
    private boolean running;
    long totalSise, actualServed;
    static Server instance = null;
    public synchronized static Server getInstance(){
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }
    private Server() {

    }

    static InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
        Enumeration en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface i = (NetworkInterface) en.nextElement();
            for (Enumeration en2 = i.getInetAddresses(); en2.hasMoreElements(); ) {
                InetAddress addr = (InetAddress) en2.nextElement();
                if (!addr.isLoopbackAddress()) {
                    if (addr instanceof Inet4Address) {
                        if (preferIPv6) {
                            continue;
                        }
                        return addr;
                    }
                    if (addr instanceof Inet6Address) {
                        if (preferIpv4) {
                            continue;
                        }
                        return addr;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        URI uri = he.getRequestURI();
        String path = uri.toString();
        String[] fpath = uri.toString().split("/");
        String response = "";
        if("get".equals(fpath[1]) || "rget".equals(fpath[1]) || "post".equals(fpath[1]) || "rpost".equals(fpath[1])){
            HashMap<String,String> headers = new HashMap<>();
            if(fpath.length > 3){
                headers = InetTools.jsonToHM(InetTools.dec(fpath[3]));
            }
            HashMap<String, String> data = new HashMap<>();
            if (fpath.length > 4){
                data = InetTools.jsonToHM(InetTools.dec(fpath[4]));
            }
            if("get".equals(fpath[1])) {
                response = InetTools.get(InetTools.dec(fpath[2]), headers);
            }else if("post".equals(fpath[1])){
                response = InetTools.post(InetTools.dec(fpath[2]), headers, data);
            }else if("rpost".equals(fpath[1])){
                response = InetTools.rpost(InetTools.dec(fpath[2]), headers, data);
            }else if("rget".equals(fpath[1])){
                response = InetTools.rget(InetTools.dec(fpath[2]), headers);
            }
        }
        if("info".equals(fpath[1])) {
            response = "{\"host\":\"android\", \"version\":\""+ Updates.version + "\"}";
        }else if("view".equals(fpath[1])){
            if(parent != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(InetTools.dec(fpath[2])), "video/*");
                parent.startActivity(intent);
                response = "ok";
            }else{
                response = "error";
            }
        }

        File rfile = new File(Updates.path, wpath + path);
        if(response == "") {
            if (!rfile.exists()) {
                response = "404 (Not Found)\n";
            } else {
                if (path.endsWith(".js")) {
                    he.getResponseHeaders().add("Content-Type", "application/javascript");
                }
                OutputStream os = he.getResponseBody();
                long cfl = rfile.length();

                he.sendResponseHeaders(200, cfl);
                long afs = 0;
                InputStream fs = new FileInputStream(rfile);
                final byte[] buffer = new byte[0x1000];
                int count = 0;
                while ((count = fs.read(buffer)) >= 0) {
                    try {
                        os.write(buffer, 0, count);
                    } catch (Exception e) {
                        System.err.println("Connection lost... " + afs);
                        actualServed -= afs;
                        afs = 0;
                        break;
                    }
                    afs += count;
                    actualServed += count;
                }
                fs.close();
                os.close();
                return;
            }
        }
        he.sendResponseHeaders(200, response.length());
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes(),0, response.length());
        os.close();
        if("shutdown".equals(fpath[1])){
            server.stop(0);
        }
    }
    public void start(Activity parent) throws IOException {
        this.parent = parent;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080),0);
        server.createContext("/", this);
        server.start();
    }
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
