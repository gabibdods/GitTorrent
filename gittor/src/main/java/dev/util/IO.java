package dev.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IO {
    public static void copyTree(Path src, Path dst) {
        try {
            Files.walk(src).forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    Path out = dst.resolve(rel);
                    if (Files.isDirectory(p)) Files.createDirectories(out);
                    else {
                        Files.createDirectories(out.getParent());
                        Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static String ts() { return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()); }
}
