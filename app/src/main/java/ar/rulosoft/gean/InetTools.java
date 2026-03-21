package ar.rulosoft.gean;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import android.os.Build;
import android.util.Base64;
import android.util.Pair;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
public class InetTools {
    public static final String USER_AGENT = "Mozilla/5.0 (X11; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
    private static String lastM3U8BaseServer = "";
    private static String lastM3U8BaseHeaders = "";
    public static CacheInfo cacheInfo = new CacheInfo();
    static OkHttpClient mClient = null;


    public static OkHttpClient client(){
        if(mClient == null){
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }};
            final SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException(e);
            }

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            builder.retryOnConnectionFailure(true);
            //builder.dns(new GeanDns());
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            mClient = builder.build();
        }
        return mClient;
    }

    public static String get(String url, HashMap<String, String> headers, ArrayList<String> setCookie) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        if (!headers.containsKey("User-Agent")) {
            request.addHeader("User-Agent", USER_AGENT);
        }
        try (Response response = client().newCall(request.build()).execute()) {
            setCookie.addAll(response.headers("set-cookie"));
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static Pair<String, Response> getResponse(String url, HashMap<String, String> headers, ArrayList<String> setCookie) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        if (!headers.containsKey("User-Agent")) {
            request.addHeader("User-Agent", USER_AGENT);
        }
        try (Response response = client().newCall(request.build()).execute()) {
            setCookie.addAll(response.headers("set-cookie"));
            return new Pair<>(response.body().string(), response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String eliminarPathRelativos(String url) {
        try {
            url = url.substring(0, 8) + url.substring(8).replace("//", "/");
            URI uri = new URI(url);
            URI uriAbsoluta = uri.normalize();
            return uriAbsoluta.toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static String transform(String content, String baseUrl, String headers) {
        String[] contentLines = content.split("\n");
        ArrayList<String> newLines = new ArrayList<>();
        for (String line : contentLines) {
            line = line.strip();
            if(line.isEmpty()) continue;
            if(line.startsWith("#")){
                if(line.contains("URI=")){
                    final Pattern pattern = Pattern.compile("URI=\"(.*?)\"", Pattern.MULTILINE);
                    final Matcher matcher = pattern.matcher(line);
                    if(matcher.find()){
                        String url = matcher.group(1);
                        String ptype = "file";
                        if(url.contains(".m3u8")){
                            ptype = "m3u8";
                        }
                        String full = baseUrl + url;
                        if (url.startsWith("http")){
                            full = url;
                        }
                        String encoded = "http://127.0.0.1:8080/"+ ptype + "/" + encode(full) + headers;
                        line = line.replaceAll("URI=\"(.*?)\"", "URI=\"" + encoded + "\"");
                    }
                }
                newLines.add(line);
                continue;
            }
            String full = baseUrl + line;
            if (line.startsWith("http")){
                full = line;
            }
            String ptype = "file";
            if((!newLines.isEmpty() && newLines.get(newLines.size() - 1).startsWith("#EXT-X-STREAM-INF"))
            || line.split("\\?")[0].contains(".m3u8")) {
                ptype = "m3u8";
            }
            newLines.add("http://127.0.0.1:8080/"+ ptype + "/" + encode(full) + headers);

        }
        return String.join("\n", newLines);
    }

    public static String getParentPath(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            return url;
        }
        return url.substring(0, lastSlashIndex + 1);
    }

    public static NanoHTTPD.Response m3u8(String[] path, HashMap<String, String> headers, ArrayList<String> setCookie) {
        String url = "";
        String rdata = "";
        Response response = null;
        lastM3U8BaseServer = getParentPath(dec(path[2]));
        if(path.length > 3 && path[3].contains(".key")){
            url = lastM3U8BaseServer + path[3];
            Pair<String, Response> p = getResponse(url, headers, setCookie);
            rdata = p.first;
            response = p.second;
        }else{
            if (path.length == 4){
                lastM3U8BaseHeaders = "/" + path[3];
            }else{
                lastM3U8BaseHeaders = "";
            }
            url = dec(path[2]);
            Pair<String, Response> p = getResponse(url, headers, setCookie);
            rdata = p.first;
            response = p.second;
            rdata = transform(rdata, lastM3U8BaseServer, lastM3U8BaseHeaders);
        }
        String mime = response.header("Content-Type", "text/plain");
        NanoHTTPD.Response nanoResponse = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, rdata);
        nanoResponse.addHeader("Content-Length", "" + rdata.length()); // Set content length manually if needed
        nanoResponse.addHeader("Access-Control-Allow-Origin", "*"); // Example header
        nanoResponse.addHeader("Access-Control-Expose-Headers", "*"); // Example header
        nanoResponse.addHeader("Access-Control-Allow-Headers", "*"); // Example header
        nanoResponse.addHeader("Cache-Control", "no-cache"); // Example header
        nanoResponse.addHeader("Content-Type", mime);

        return nanoResponse;
    }


    public static String rget(String url, HashMap<String, String> headers) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        if (!headers.containsKey("User-Agent")) {
            request.addHeader("User-Agent", USER_AGENT);
        }
        try (Response response = client().newCall(request.build()).execute()) {
            return response.request().url().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String post(String url, HashMap<String,String> headers, HashMap<String, String> data){
        FormBody.Builder formBody = new FormBody.Builder();
        for(String k: data.keySet()){
            formBody.add(k, data.get(k));
        }
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        if (!headers.containsKey("User-Agent")) {
            request.addHeader("User-Agent", USER_AGENT);
        }
        try (Response response = client().newCall(request.post(formBody.build()).build()).execute()) {
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String rpost(String url, HashMap<String,String> headers, HashMap<String, String> data){
        FormBody.Builder formBody = new FormBody.Builder();
        for(String k: data.keySet()){
            formBody.add(k, data.get(k));
        }
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        if (!headers.containsKey("User-Agent")) {
            request.addHeader("User-Agent", USER_AGENT);
        }
        try (Response response = client().newCall(request.post(formBody.build()).build()).execute()) {
            return response.request().url().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static HashMap<String,String> jsonToHM(String jsonstr){
        HashMap<String,String> response = new HashMap<>();
        try {
            JsonObject obj = JsonParser.object().from(jsonstr);
            for (String key:obj.keySet()){
                response.put(key, obj.get(key).toString());
            }
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        return response;
    }

    // Define constants to avoid magic strings and improve maintainability
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String RANGE_HEADER = "range";
    private static final String BYTES_PREFIX = "bytes=";

    public static NanoHTTPD.Response file(final String url, final Map<String, String> headers) throws IOException {
        // Ensure User-Agent is present without modifying the original headers map
        final Map<String, String> requestHeaders = new HashMap<>(headers);
        if(!headers.containsKey(USER_AGENT_HEADER)){
            headers.put(USER_AGENT_HEADER, USER_AGENT);
        }
        final PrFileInputStream inputStream = new PrFileInputStream(url, requestHeaders);
        final int responseCode = inputStream.rcode();

        // Handle unsuccessful status codes first (Guard Clause)
        if (responseCode != 200 && responseCode != 206) {
            // It's better to return a proper status code than a string body for errors.
            // For example, map the upstream error code to a NanoHTTPD status.
            // This is a simplified example.
            return newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Unsupported response from upstream: " + responseCode
            );
        }

        try {
            final Headers responseHeaders = inputStream.getResponseHeaders();
            final String contentLengthStr = responseHeaders.get("Content-Length");
            if (contentLengthStr == null) {
                // Or handle as a chunked response if appropriate
                throw new IOException("Content-Length header is missing from the upstream response.");
            }
            final long totalLength = Long.parseLong(contentLengthStr);

            final NanoHTTPD.Response.Status status = (responseCode == 200) ?
                    NanoHTTPD.Response.Status.OK : NanoHTTPD.Response.Status.PARTIAL_CONTENT;

            final String contentType = inputStream.getContentType() != null ?
                    inputStream.getContentType() : "application/octet-stream";

            // Create a single response object and configure it
            final NanoHTTPD.Response response = newFixedLengthResponse(status, contentType, inputStream, totalLength);
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Connection", "keep-alive");

            // Let NanoHTTPD handle the range request logic
            // This is the most idiomatic way and avoids manual parsing.
            // NanoHTTPD's `serveFile` internally handles range requests correctly when you
            // provide the full stream and its total length.
            return response;

        } catch (NumberFormatException e) {
            // This handles cases where "Content-Length" is not a valid number
            return newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Invalid Content-Length from upstream."
            );
        }
    }

    public static void cacheDownloader(String url, File destFile, HashMap<String, String> headers) throws IOException {
        int currentProgress = 0;
        int lastInformedProgress = 0;
        Headers.Builder headersOk = new Headers.Builder();
        for(String k: headers.keySet()){
            headersOk.add(k, (String) Objects.requireNonNull(headers.get(k)));
        }
        if(headersOk.get("User-Agent") == null){
            headersOk.add("User-Agent", USER_AGENT);
        }
        if (cacheInfo.cacheProgress != 0){
            headersOk.add("Range", "bytes="+ cacheInfo.cacheProgress + "-");
        }
        Request request = new Request.Builder().url(url).headers(headersOk.build()).build();
        Response response = client().newCall(request).execute();
        ResponseBody body = response.body();
        cacheInfo.contentType = response.header("Content-Type");
        if (cacheInfo.cacheProgress == 0){
            cacheInfo.cacheTotalLength = body.contentLength();
        }
        if(response.code() != 206){
            cacheInfo.cacheProgress = 0;
        }
        BufferedSource source = body.source();
        BufferedSink sink = Okio.buffer(Okio.sink(destFile, response.code() == 206));
        Buffer sinkBuffer = sink.getBuffer();
        long totalBytesRead = 0;
        int bufferSize = 8192;
        cacheInfo.cacheStatus = 1;
        cacheInfo.cacheStop = false;
        for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
            sink.emit();
            totalBytesRead += bytesRead;
            currentProgress = (int) ((totalBytesRead * 100) / cacheInfo.cacheTotalLength);
            sink.flush();
            if(currentProgress > lastInformedProgress){
                lastInformedProgress = currentProgress;
                cacheInfo.setProgress(currentProgress);
            }
            cacheInfo.cacheProgress = totalBytesRead;
            if(cacheInfo.cacheStop){
                break;
            }
        }
        sink.flush();
        sink.close();
        source.close();
        cacheInfo.cacheStatus = 2;
        //Log.d("chache", "---------------------------------_terminado__________________________________");
    }

    public static NanoHTTPD.Response returnCache(NanoHTTPD.IHTTPSession session) {
        Map<String,String> headers = session.getHeaders();
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", USER_AGENT);
        }

        long start = 0;
        int maxWait = 10; // max wait 30 seconds for start download
        while (cacheInfo.cacheProgress == 0) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // ignore
            }
            maxWait--;
            if (maxWait == 0) {
                return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "");
            }
        }
        long end = cacheInfo.cacheTotalLength;
        NanoHTTPD.Response response;
        if (cacheInfo.contentType != null && cacheInfo.contentType.length() > 2) {
            response = newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT, cacheInfo.contentType, "");
        } else {
            response = newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT, "application/octet-stream", "");
        }
        response.addHeader("Connection", "keep-alive");

        String rangeHeader = headers.get("range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeValue = rangeHeader.substring(6);
            String[] ranges = rangeValue.split("-");
            long startRange = Long.parseLong(ranges[0]);
            long endRange = cacheInfo.cacheTotalLength - 1;
            if (ranges.length == 2) {
                endRange = Long.parseLong(ranges[1]);
            }

            response.setStatus(NanoHTTPD.Response.Status.PARTIAL_CONTENT);
            response.addHeader("Content-Range", "bytes " + startRange + "-" + endRange + "/" + cacheInfo.cacheTotalLength);
            response.addHeader("Content-Length", String.valueOf(endRange - startRange + 1));
            response.addHeader("Accept-Ranges", "bytes");

            // Enviar los bytes correspondientes al rango desde el archivo local
            try (InputStream inputStream = new CacheFileInputStream(cacheInfo, Updates.path +"/cache.mp4", startRange, endRange)) {
                response.setData(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            response.addHeader("Content-Length", String.valueOf(cacheInfo.cacheTotalLength));
            try (InputStream inputStream = new CacheFileInputStream(cacheInfo, Updates.path +"/cache.mp4", 0, cacheInfo.cacheTotalLength)) {
                response.setData(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return response;
    }

    public static NanoHTTPD.Response cache(NanoHTTPD.IHTTPSession session, String url, HashMap<String, String> headers) {
        url = dec(url);

        if(!cacheInfo.cacheLink.equals(url)){
            if(cacheInfo.cacheThread != null){
                cacheInfo.cacheStop = true;
            }
            cacheInfo.cacheLink = url;
            cacheInfo.cacheProgress = 0;
            cacheInfo.cacheStatus = 0;
            cacheInfo.cacheThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    int errorCount = 0;
                    while ((errorCount < 10) && !cacheInfo.cacheStop && (cacheInfo.cacheStatus != 2)) {
                        try {
                            cacheDownloader(cacheInfo.cacheLink, new File(Updates.path, "cache.mp4"), headers);
                        } catch (IOException e) {
                            e.printStackTrace();
                            errorCount++;
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            });
            cacheInfo.cacheStop = false;
            cacheInfo.cacheThread.start();
        }
        return returnCache(session);
    }

    public static String dec(String _in){
        try {
            return new String(Base64.decode(_in.replace("_","/").getBytes(), Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "{}";
    }

    public static String encode(String data) {
        return new String(Base64.encode(data.getBytes(), Base64.DEFAULT)).replace( "/", "_").replace("\n", "");
    }

    public static void download(String url, File destFile) throws IOException {
        cacheInfo.cacheLink = url;
        Request request = new Request.Builder().url(url).build();
        Response response = client().newCall(request).execute();
        ResponseBody body = response.body();
        long contentLength = body.contentLength();
        BufferedSource source = body.source();
        BufferedSink sink = Okio.buffer(Okio.sink(destFile));
        Buffer sinkBuffer = sink.buffer();
        long totalBytesRead = 0;
        int bufferSize = 8 * 1024;
        cacheInfo.cacheStatus = 1;
        for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
            sink.emit();
            totalBytesRead += bytesRead;
            //int progress = (int) ((totalBytesRead * 100) / contentLength);
            sink.flush();
            cacheInfo.cacheProgress = totalBytesRead;
        }
        sink.flush();
        sink.close();
        source.close();
        cacheInfo.cacheStatus = 2;
    }
    public static long[] parseRangeHeader(String rangeHeader, long defaultStart, long defaultEnd) {
        long[] range = new long[2];
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            range[0] = defaultStart;
            range[1] = defaultEnd;
            return range;
        }
        Pattern pattern = Pattern.compile("bytes=(\\d*)-(\\d*)");
        Matcher matcher = pattern.matcher(rangeHeader);
        if (matcher.find()) {
            String startValue = matcher.group(1);
            String endValue = matcher.group(2);
            long start = startValue.isEmpty() ? defaultStart : Long.parseLong(startValue);
            long end = endValue.isEmpty() ? defaultEnd : Long.parseLong(endValue);
            range[0] = start;
            range[1] = end;
        } else {
            range[0] = defaultStart;
            range[1] = defaultEnd;
        }
        return range;
    }

    public static class CacheInfo{
        long cacheProgress = 0;
        long cacheTotalLength = 0;
        short cacheStatus = 0; //| 0 waiting | 1 running | 2 finished | -1 error |
        Thread cacheThread = null;
        boolean cacheStop = false;
        String cacheLink = "";
        String contentType = "";
        private int cacheProgressPs = 0;

        CacheListener cacheListener = null;
        void setProgress(int cacheProgress){
            if((cacheListener != null) && (cacheProgressPs != cacheProgress)){
                cacheListener.onCacheProgressUpdate(cacheProgress);
            }
        }
        void setCacheListener(CacheListener newCacheListener){
            cacheListener = newCacheListener;
        }

        public void deleteFile() {
            try {
                Thread.sleep(1000);
                File file = new File(Updates.path, "cache.mp4");
                if(file.exists()){
                    file.delete();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class GeanDns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<InetAddress> list = new ArrayList<>();
            if (hostname.toLowerCase().contains("raw.githubusercontent.com")) {//Raw.githubusercontent.com
                list.add(InetAddress.getByAddress(hostname, new byte[]{(byte) 151, (byte) 101, (byte) 4, (byte) 133}));
            } else if (hostname.toLowerCase().contains(".animeonline.ninja")) {
                list.add(InetAddress.getByAddress(hostname, new byte[]{(byte) 38, (byte) 62, (byte) 224, (byte) 77}));
            } else {
                list = SYSTEM.lookup(hostname);
            }
            return list;
        }
    }

    public static interface CacheListener{
        void onCacheProgressUpdate(int progress);
    }
}
