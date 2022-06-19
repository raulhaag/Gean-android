package ar.rulosoft.gean;

import android.util.Base64;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Objects;

import okhttp3.FormBody;
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

    public static String dec(String _in){
        try {
            return new String(Base64.decode(_in.replace("_","/").getBytes(), Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "{}";
    }

    public static void download(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();
        long contentLength = body.contentLength();
        BufferedSource source = body.source();

        BufferedSink sink = Okio.buffer(Okio.sink(destFile));
        Buffer sinkBuffer = sink.buffer();

        long totalBytesRead = 0;
        int bufferSize = 8 * 1024;
        for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
            sink.emit();
            totalBytesRead += bytesRead;
            int progress = (int) ((totalBytesRead * 100) / contentLength);
        }
        sink.flush();
        sink.close();
        source.close();
    }
}
