package ar.rulosoft.gean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {
    private Activity parent;
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
        ArrayList<String> setCookie = new ArrayList<>();

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
                response = InetTools.get(InetTools.dec(fpath[2]), headers, setCookie);
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
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(InetTools.dec(fpath[2])), "video/*");
                parent.startActivity(intent);
                response = "ok";
            } else {
                response = "error";
            }
        }else if ("play".equals(fpath[1])) {
            if (parent != null) {
                Intent intent = new Intent(parent, PlayActivity.class);
                intent.setDataAndType(Uri.parse(InetTools.dec(fpath[2])), "video/*");
                parent.startActivity(intent);
                response = "ok";
            } else {
                response = "error";
            }
        }

        String wpath = "./www";
        File rfile = new File(Updates.path, wpath + path);

        if (response.equals("")) {
            if (!rfile.exists()) {
                response = "404 (Not Found)\n";
            } else {
                if(rfile.getAbsolutePath().endsWith("sources.js")){
                    rfile = new File(Updates.path + "/temp/sources.js");
                }
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
        NanoHTTPD.Response nr = newFixedLengthResponse(Response.Status.OK, "application/json", response);
        for(String h: setCookie){
            nr.addHeader("gean_Set-Cookie", h);
        }
        return nr;
    }

    public static void generateSourceList() {
        String regex = "class\\s+(\\S+)[\\s|\\S]+?this.name\\s*=\\s*([\\S+]+)\\s*;";
        String sourcesDir = Updates.path + "/www/js/sources/";
        File sourcesDirectory = new File(sourcesDir);
        File[] sources = sourcesDirectory.listFiles();

        if (sources != null) {
            StringBuilder initial = new StringBuilder();
            StringBuilder imports = new StringBuilder();

            for (File file : sources) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    StringBuilder fileContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        fileContent.append(line).append("\n");
                    }
                    String content = fileContent.toString();

                    if (content.contains("this.name")) {
                        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
                        Matcher matcher = pattern.matcher(content);

                        if (matcher.find()) {
                            if (matcher.groupCount() > 0 && !matcher.group(1).substring(0, 2).equals("NO")) {
                                imports.append("import { ").append(matcher.group(1)).append(" } from \"./").append(file.getName()).append("\";\n");
                                initial.append(matcher.group(2)).append(": new ").append(matcher.group(1)).append(",\n");
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            String sOut = imports + "\nexport function openInNewTab(url) {\n"+
            "window.open(url, '_blank').focus();\n"+
            "}\n";
            sOut += "let servers = {" + initial + "};\n";
            sOut += "export function getSource(name) {return servers[name];}\n" +
                    "\n" +
                    "export function getResponse(name, callback, error_callback) {\n" +
                    "    if(servers[name]){\n" +
                    "        return servers[name].getFrontPage(callback, error_callback);\n" +
                    "    }\n" +
                    "    return servers[\"jkanime\"].getFrontPage(callback, error_callback);\n" +
                    "}\n" +
                    "\n" +
                    "export function getLinks(path, callback, error_callback) {\n" +
                    "}\n" +
                    "\n" +
                    "export function getSourceList(){\n" +
                    "    return Object.keys(servers);\n" +
                    "}";

            try {
                new File(Updates.path + "/temp").mkdirs();
            }catch (Exception ignored){

            }
            try (FileWriter writer = new FileWriter(Updates.path + "/temp/sources.js")) {
                writer.write(sOut);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void startServer(Activity parent) throws IOException {
        this.parent = parent;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    public void stopServer() {
        stop();
    }
}
