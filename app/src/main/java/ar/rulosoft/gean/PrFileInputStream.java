package ar.rulosoft.gean;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Objects;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public class PrFileInputStream extends InputStream {
    InputStream inputStream;
    Response response;
    Request request;
    Headers rHeader;
    public PrFileInputStream(String link, HashMap<String,String> headers) throws IOException {
        Headers.Builder headersOk = new Headers.Builder();
        for(String k: headers.keySet()){
            headersOk.add(k, (String) Objects.requireNonNull(headers.get(k)));
        }
        request = new Request.Builder()
                .url(link).headers(headersOk.build())
                .build();
        response = InetTools.client().newCall(request).execute();
        rHeader = response.headers();
        inputStream = response.body().byteStream();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return inputStream.read(b);
    }
    public Headers getResponseHeaders() {
        return rHeader;
    }
    public int rcode(){
        return response.code();
    }
    public String getContentType(){
        return rHeader.get("Content-Type");
    }
}
