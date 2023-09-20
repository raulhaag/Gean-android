package ar.rulosoft.gean;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class CacheFileInputStream extends FileInputStream {
    InetTools.CacheInfo info;
    FileInputStream file;

    long pos = 0;
    public CacheFileInputStream(String name) throws FileNotFoundException {
        super(name);
    }

    public CacheFileInputStream(File file) throws FileNotFoundException {
        super(file);
    }

    public CacheFileInputStream(FileDescriptor fdObj) {
        super(fdObj);
    }

    public CacheFileInputStream(InetTools.CacheInfo info, String name, long startRange, long endRange) throws FileNotFoundException {
        super(name);
        this.info = info;
        this.file = new FileInputStream(new File(name));
        try {
            while(startRange > info.cacheProgress){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            this.skip(startRange);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long skip(long n) throws IOException{
        pos += n;
        return this.file.skip(n);
    }



    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        while((pos + off  + len) > info.cacheProgress){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        pos += len;
        return this.file.read(b, off, len);
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        return (read(b, 0, 1) != -1) ? b[0] & 0xff : -1;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    protected void finalize() throws IOException {
        super.finalize();
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        pos = 0;
        this.file.reset();
    }

    @Override
    public void close() throws IOException {
        super.close();
        //this.file.close();
    }

}
