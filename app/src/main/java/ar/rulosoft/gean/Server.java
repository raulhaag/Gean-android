package ar.rulosoft.gean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executors;
import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {
    private Activity parent;
    private String wpath = "./www";
    private boolean running;
    long totalSize, actualServed;
    static Server instance = null;

    public synchronized static Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    private Server() {
        super(8080);
    }

    static InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface i = en.nextElement();
            for (Enumeration<InetAddress> en2 = i.getInetAddresses(); en2.hasMoreElements(); ) {
                InetAddress addr = en2.nextElement();
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
    public Response serve(IHTTPSession session) {
        Uri uri = Uri.parse(session.getUri());
        String path = uri.toString();
        String[] fpath = uri.toString().split("/");
        String response = "";

        if ("get".equals(fpath[1]) || "rget".equals(fpath[1]) || "post".equals(fpath[1]) || "rpost".equals(fpath[1]) || "file".equals(fpath[1]) || "cache".equals(fpath[1])) {
            HashMap<String, String> headers = new HashMap<>();
            if (fpath.length > 3) {
                headers = InetTools.jsonToHM(InetTools.dec(fpath[3]));
            }
            HashMap<String, String> data = new HashMap<>();
            if (fpath.length > 4) {
                data = InetTools.jsonToHM(InetTools.dec(fpath[4]));
            }

            if ("get".equals(fpath[1])) {
                response = InetTools.get(InetTools.dec(fpath[2]), headers);
            } else if ("post".equals(fpath[1])) {
                response = InetTools.post(InetTools.dec(fpath[2]), headers, data);
            } else if ("rpost".equals(fpath[1])) {
                response = InetTools.rpost(InetTools.dec(fpath[2]), headers, data);
            } else if ("rget".equals(fpath[1])) {
                response = InetTools.rget(InetTools.dec(fpath[2]), headers);
            } else if ("file".equals(fpath[1])) {
                try {
                    return InetTools.file(InetTools.dec(fpath[2]), headers);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if ("cache".equals(fpath[1])) {
                return InetTools.cache(session, fpath[2], headers);
            }
        }

        if ("info".equals(fpath[1])) {
            response = "{\"host\":\"android\", \"version\":\"" + Updates.version + "\"}";
        } else if ("view".equals(fpath[1])) {
            if (parent != null) {
                //Intent intent = new Intent(Intent.ACTION_VIEW);
                Intent intent = new Intent(parent, PlayActivity.class);
                intent.setDataAndType(Uri.parse(InetTools.dec(fpath[2])), "video/*");
                parent.startActivity(intent);
                response = "ok";
            } else {
                response = "error";
            }
        }

        File rfile = new File(Updates.path, wpath + path);

        if (response.equals("")) {
            if (!rfile.exists()) {
                response = "404 (Not Found)\n";
            } else {
                String extension = MimeTypeMap.getFileExtensionFromUrl(rfile.toString());
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if(mimeType == null){
                    if(Objects.equals(extension, "js")){
                        mimeType = "text/javascript";
                    }
                }
                try {
                    FileInputStream fs = new FileInputStream(rfile);
                    return newFixedLengthResponse(Response.Status.OK, mimeType, fs, fs.available());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", response);
    }

    public void startServer(Activity parent) throws IOException {
        this.parent = parent;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    public void stopServer() {
        stop();
    }
}
