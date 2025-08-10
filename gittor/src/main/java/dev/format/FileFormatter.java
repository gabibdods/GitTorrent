package dev.format;

import java.nio.file.*;
import java.io.*;
import java.util.*;

public class FileFormatter {
    public static class Change {
        public String path;
        public long originalBytes;
        public long newBytes;
        public String note;
        public Change(String p, long o, long n, String note) {
            this.path=p;
            this.originalBytes=o;
            this.newBytes=n;
            this.note=note;
        }
    }

    public static class Result {
        public List<Change> changes = new ArrayList<>();
        public long filesConsidered=0, filesRewritten=0, bytesDelta=0;
    }

    public byte[] format(byte[] in, FormatterConfig cfg, String langOrExt) {
        String s = new String(in);

        Map<String,String> ov = cfg.forKey(langOrExt);
        String eol = ov.getOrDefault("eol", cfg.eol);
        boolean trim = Boolean.parseBoolean(ov.getOrDefault("trimTrailing", String.valueOf(cfg.trimTrailing)));
        boolean finalNl = Boolean.parseBoolean(ov.getOrDefault("ensureFinalNewline", String.valueOf(cfg.ensureFinalNewline)));
        int collapse = Integer.parseInt(ov.getOrDefault("collapseBlankLinesTo", String.valueOf(cfg.collapseBlankLinesTo)));
        boolean detab = Boolean.parseBoolean(ov.getOrDefault("detab", String.valueOf(cfg.detab)));
        int tabw = Integer.parseInt(ov.getOrDefault("tabWidth", String.valueOf(cfg.tabWidth)));
        int wrapMd = Integer.parseInt(ov.getOrDefault("wrapMarkdownAt", String.valueOf(cfg.wrapMarkdownAt)));

        String lineSep = eol.equals("crlf") ? "\r\n" : "\n";

        s = s.replace("\r\n", "\n").replace("\r", "\n");

        if (detab) s = s.replace("\t", " ".repeat(Math.max(1, tabw)));

        if (trim) {
            String[] lines = s.split("\n",-1);
            for (int i=0;i<lines.length;i++) lines[i] = lines[i].replaceAll("[ \\t]+$", "");
            s = String.join("\n", lines);
        }

        if (collapse >= 0) {
            s = s.replaceAll("(?m)^(?:\\s*\\n){"+(collapse+1)+",}", "\n".repeat(Math.max(1, collapse)));
        }

        if (wrapMd > 0 && ("Markdown".equalsIgnoreCase(langOrExt) || "md".equalsIgnoreCase(langOrExt) || "txt".equalsIgnoreCase(langOrExt))) {
            StringBuilder out = new StringBuilder();
            for (String line : s.split("\n",-1)) {
                if (line.length() <= wrapMd || line.startsWith("#") || line.startsWith("```")) { out.append(line).append("\n"); continue; }
                int idx = 0;
                while (idx < line.length()) {
                    int end = Math.min(idx + wrapMd, line.length());
                    // try to break on space
                    int space = line.lastIndexOf(' ', end);
                    if (space <= idx) space = end;
                    out.append(line, idx, space).append("\n");
                    idx = (space < line.length() && line.charAt(space)==' ')? space+1 : space;
                }
            }
            s = out.toString();
        }

        if (finalNl && !s.endsWith("\n")) s += "\n";

        s = s.replace("\n", lineSep);
        return s.getBytes();
    }

    public Result formatTree(Path root, FormatterConfig cfg,
                             java.util.function.Function<Path,String> langKey,
                             java.util.function.Function<Path,String> skipReason,
                             boolean write) {
        Result r = new Result();
        try {
            Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                String reason = skipReason.apply(p);
                if (reason != null) return;
                try {
                    byte[] in = Files.readAllBytes(p);

                    if (dev.analysis.LanguageDetector.looksBinary(in)) return;
                    r.filesConsidered++;
                    String key = langKey.apply(p); // language or ext
                    byte[] out = format(in, cfg, key);
                    if (!Arrays.equals(in, out)) {
                        r.filesRewritten++;
                        r.bytesDelta += (out.length - in.length);
                        if (write) Files.write(p, out);
                        r.changes.add(new Change(p.toString(), in.length, out.length, "formatted"));
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) { throw new RuntimeException(e); }
        return r;
    }
}