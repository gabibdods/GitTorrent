package dev.analysis;

import java.nio.file.*;
import java.io.IOException;
import java.util.function.Function;

public class ProjectSizer {

    public long totalBytes(Path root, Function<Path, String> fileSkipReason) {
        final long[] total = {0L};
        try {
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        String reason = fileSkipReason.apply(p);
                        if (reason != null) return;
                        try {
                            total[0] += Files.size(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return total[0];
    }
}