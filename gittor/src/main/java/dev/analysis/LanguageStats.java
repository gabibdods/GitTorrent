package dev.analysis;

import java.nio.file.*;
import java.io.*;
import java.util.*;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LanguageStats {
    public static class Result {
        public Map<String,Long> bytesByLanguage = new LinkedHashMap<>();
        public long totalBytes = 0;
        public Map<String,Double> percentages = new LinkedHashMap<>();
        public long totalProjectBytes = 0;
        public Map<String, Long> partitions = new LinkedHashMap<>();
        public List<String> skipped = new ArrayList<>();
        public String toJson() {
            try {
                ObjectMapper om = new ObjectMapper();
                DefaultPrettyPrinter pp = new DefaultPrettyPrinter()
                        .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                        .withArrayIndenter(new DefaultIndenter("  ", "\n"));
                return om.writer(pp).writeValueAsString(this) + System.lineSeparator();
            } catch(Exception e) {
                return "{}" + System.lineSeparator();
            }
        }
    }

    public interface PathFilter {
        String fileSkipReason(Path p);
    }

    public Result compute(Path root, LanguageDetector detector, Path langOverrides, PathFilter filter) {
        if (langOverrides != null) detector.loadOverrides(langOverrides);
        Result r = new Result();
        try {
            Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                String reason = (filter != null) ? filter.fileSkipReason(p) : null;
                if (reason != null) { r.skipped.add(p.toString()+" :: "+reason); return; }
                try {
                    byte[] data = Files.readAllBytes(p);
                    if (LanguageDetector.looksBinary(data)) { r.skipped.add(p +" :: binary"); return; }
                    String lang = detector.detect(p, data);
                    long sz = data.length;
                    r.bytesByLanguage.merge(lang, sz, Long::sum);
                    r.totalBytes += sz;
                } catch (IOException io) {
                    r.skipped.add(p +" :: IO error: "+io.getMessage());
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (r.totalBytes > 0) {
            r.bytesByLanguage.entrySet().stream().sorted((a,b) -> Long.compare(b.getValue(), a.getValue())).forEach(e -> r.percentages.put(e.getKey(), (e.getValue()*100.0)/r.totalBytes));
        }
        return r;
    }
}
