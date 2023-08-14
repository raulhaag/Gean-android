package ar.rulosoft.gean;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.text.UStringsKt;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class InetTools {

    static CacheInfo cacheInfo = new CacheInfo();
    static OkHttpClient client = new OkHttpClient();
    public static String get(String url, HashMap<String, String> headers) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        try (Response response = client.newCall(request.build()).execute()) {
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static void get(String url, HashMap<String, String> headers, HttpExchange he) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        try (Response response = client.newCall(request.build()).execute()) {
            double length = Double.parseDouble(Objects.requireNonNull(response.header("Content-Length", "1")));
            he.sendResponseHeaders(200, (long) length);
            OutputStream os = he.getResponseBody();
            byte[] data = new byte[8192];
            InputStream is = response.body().byteStream();
            BufferedInputStream input = new BufferedInputStream(is);
            int count = 0;
            while ((count = input.read(data)) != -1) {
                os.write(data, 0, count);
                os.flush();
            }
            os.flush();
            os.close();
            input.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String rget(String url, HashMap<String, String> headers) {
        Request.Builder request = new Request.Builder().url(url);
        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }
        try (Response response = client.newCall(request.build()).execute()) {
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
        try (Response response = client.newCall(request.post(formBody.build()).build()).execute()) {
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
        try (Response response = client.newCall(request.post(formBody.build()).build()).execute()) {
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

    public static void cacheDownloader(String url, File destFile, HashMap<String, String> headers) throws IOException {
        cacheInfo.cacheLink = url;
        cacheInfo.cacheProgress = 0;
        Headers.Builder headersOk = new Headers.Builder();
        for(String k: headers.keySet()){
            headersOk.add(k, (String) Objects.requireNonNull(headers.get(k)));
        }
        Request request = new Request.Builder().url(url).headers(headersOk.build()).build();
        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();
        cacheInfo.cacheTotalLength = body.contentLength();
        BufferedSource source = body.source();
        BufferedSink sink = Okio.buffer(Okio.sink(destFile));
        Buffer sinkBuffer = sink.buffer();
        long totalBytesRead = 0;
        int bufferSize = 8192;
        cacheInfo.cacheStatus = 1;
        for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
            sink.emit();
            totalBytesRead += bytesRead;
            //int progress = (int) ((totalBytesRead * 100) / contentLength);
            sink.flush();
            cacheInfo.cacheProgress = totalBytesRead;
            if(cacheInfo.cacheStop){
                break;
            }
        }
        sink.flush();
        sink.close();
        source.close();
        cacheInfo.cacheStatus = 2;
    }
    public static void returnCache(@NonNull HttpExchange he) throws IOException {
        com.sun.net.httpserver.Headers headers = he.getRequestHeaders();
        long start = 0;
        int rcode = 200;
        int maxWait = 10; //max wait 30 seconds for start download
        while(cacheInfo.cacheProgress == 0){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                //ignore
            }
            maxWait--;
            if(maxWait == 0){
                he.sendResponseHeaders(500,0);
                OutputStream os = he.getResponseBody();
                os.flush();
                os.close();
            }
        }
        long end = cacheInfo.cacheTotalLength;

        if(headers.containsKey("Range")) {
            long[] range = parseRangeHeader(Objects.requireNonNull(headers.get("Range")).get(0), start, end);
            start = range[0];
            end = range[1];
            rcode = 206;
        }
        he.sendResponseHeaders(rcode, end - start + 1);
        he.getResponseHeaders().set("Content-Type", "video/mp4");
        he.getResponseHeaders().set("Connection", "keep-alive");
        if(rcode == 206) he.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + cacheInfo.cacheTotalLength);
        OutputStream os = he.getResponseBody();
        FileInputStream fis = new FileInputStream(new File(Updates.path, "cache.mp4"));
        byte[] data = new byte[4096];
        long cp = start;
        int readed = 0;
        while (cp >= cacheInfo.cacheProgress){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        fis.skip(cp);
        try {
            while (cp < end) {
                if (cacheInfo.cacheProgress > cp) {
                    readed = fis.read(data);
                    os.write(data,0, readed);
                    os.flush();
                    cp += readed;
                  //  if (readed < data.length) {
                  //      break;
                   // }
                } else {
                    if (cacheInfo.cacheStatus < 0) {
                        break;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (cacheInfo.cacheStop) {
                    break;
                }
            }
        }catch (Exception e){
            Log.e("CACHE READ ERROR", e.getMessage());
            e.printStackTrace();
        }
        os.flush();
        os.close();
        fis.close();
        he.close();
    }

    public static void returnCache1(HttpExchange exchange) throws IOException {
        int maxWait = 10; //max wait 30 seconds for start download
        while(cacheInfo.cacheProgress == 0){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                //ignore
            }
            maxWait--;
            if(maxWait == 0){
                exchange.sendResponseHeaders(505,-1);
                OutputStream os = exchange.getResponseBody();
                os.flush();
                os.close();
                exchange.close();
                return;
            }
        }
        // Obtener el rango de bytes solicitado por el cliente
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeValue = rangeHeader.substring(6);
            String[] ranges = rangeValue.split("-");
            long start = Long.parseLong(ranges[0]);
            long end = cacheInfo.cacheTotalLength - 1;
            if (ranges.length == 2) {
                end = Long.parseLong(ranges[1]);
            }

            // Configurar la respuesta parcial con el rango solicitado
            exchange.sendResponseHeaders(206, end - start + 1);
            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + cacheInfo.cacheTotalLength);

            // Enviar los bytes correspondientes al rango desde el archivo local
            try (OutputStream responseBody = exchange.getResponseBody();
                 FileInputStream fileInputStream = new FileInputStream(Updates.path + "/cache.mp4")) {
                fileInputStream.skip(start);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long bytesRemaining = end - start + 1;
                long cp = 0;
                while (true){
                    if((cp + buffer.length)>= cacheInfo.cacheProgress && (cacheInfo.cacheTotalLength - cacheInfo.cacheProgress > buffer.length)){
                        Thread.sleep(250);
                        continue;
                    }
                    if((bytesRead = fileInputStream.read(buffer, 0, (int) Math.min(bytesRemaining, buffer.length))) != -1 && bytesRemaining > 0) {
                        responseBody.write(buffer, 0, bytesRead);
                        bytesRemaining -= bytesRead;
                        cp += bytesRead;
                    }else{
                        break;
                    }
                }
                responseBody.flush();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Si no se especifica el encabezado "Range", enviar el archivo completo
            exchange.sendResponseHeaders(200, cacheInfo.cacheTotalLength);
            try (OutputStream responseBody = exchange.getResponseBody();
                 FileInputStream fileInputStream = new FileInputStream(Updates.path + "/cache.mp4")) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long cp = 0;

                while (true) {
                    if((cp + buffer.length)>= cacheInfo.cacheProgress && (cacheInfo.cacheTotalLength - cacheInfo.cacheProgress > buffer.length)){
                        Thread.sleep(250);
                        continue;
                    }
                    if((bytesRead = fileInputStream.read(buffer)) != -1){
                        responseBody.write(buffer, 0, bytesRead);
                        cp += bytesRead;
                    }else{
                        break;
                    }
                }
                responseBody.flush();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        exchange.close();
    }

    public static void cache(String url, HashMap<String, String> headers, HttpExchange he) throws IOException {
        url = dec(url);
        if(!cacheInfo.cacheLink.equals(url)){
            if(cacheInfo.cacheThread != null){
                cacheInfo.cacheStop = true;
                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            cacheInfo.cacheLink = url;
            cacheInfo.cacheThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        cacheDownloader(cacheInfo.cacheLink, new File(Updates.path, "cache.mp4"), headers);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            cacheInfo.cacheStop = false;
            cacheInfo.cacheThread.start();
        }
        returnCache(he);
    }

    public static String dec(String _in){
        try {
            return new String(Base64.decode(_in.replace("_","/").getBytes(), Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "{}";
    }

    public static void download(String url, File destFile) throws IOException {
        cacheInfo.cacheLink = url;
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
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
    }
}
