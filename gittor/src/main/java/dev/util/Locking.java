package dev.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;

public class Locking implements AutoCloseable {
    private final FileChannel ch;
    private final FileLock lock;
    private Locking(FileChannel c, FileLock l) { this.ch=c; this.lock=l; }
    public static Locking acquire(Path dir, String name) {
        try {
            Files.createDirectories(dir);
            Path f = dir.resolve(name + ".lock");
            FileChannel c = FileChannel.open(f, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock l = c.lock();
            return new Locking(c,l);
        } catch (IOException e) { throw new RuntimeException("lock failed: "+e.getMessage(), e); }
    }
    @Override public void close() {
        try { if (lock!=null) lock.release(); if (ch!=null) ch.close(); } catch (IOException ignored) {}
    }
}
