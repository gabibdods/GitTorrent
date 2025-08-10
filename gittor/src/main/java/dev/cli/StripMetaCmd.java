package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import dev.core.SecretScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.pdfbox.pdmodel.PDDocument;

@Command(name = "stripmeta", description = "Remove all metadata from a file.")
public class StripMetaCmd implements Runnable {

    @Option(names="--input", description="Input file (single-file mode)")
    Path input;

    @Option(names="--output", description="Output file (single-file mode). If omitted, overwrites input.")
    Path output;

    @Option(names="--repo", description="Repository root (repo mode)")
    Path repo;

    @Option(names="--rules", description="rules.xml to honor include/deny filters (repo mode)")
    Path rules;

    @Option(names="--write", defaultValue="false", description="Actually write changes (repo mode). Otherwise dry-run.")
    boolean write;

    @Option(names="--json", defaultValue="false", description="Emit JSON result")
    boolean json;

    public static class FileResult {
        public String path;
        public boolean changed;
        public String type;
        public String note;
        public long originalBytes;
        public long newBytes;
    }

    public static class Result {
        public int processed=0, changed=0, skipped=0, errors=0;
        public List<FileResult> files = new ArrayList<>();
    }

    @Override public void run() {
        try {
            if (input != null) {
                Path in = input;
                Path out = (output != null) ? output : input;
                var fr = stripOne(in, out, /*writeAlways=*/true);
                if (json) printJson(singleResult(fr)); else printSingle(fr);
                return;
            }

            if (repo == null) throw new IllegalArgumentException("Specify --input (single file) or --repo (repo mode).");

            var cfg = SecretScanner.loadRules(rules);
            Result res = new Result();

            Files.walk(repo).filter(Files::isRegularFile).forEach(p -> {
                String reason = fileSkipReason(cfg, p);
                if (reason != null) {
                    res.skipped++;
                    var fr = new FileResult();
                    fr.path = p.toString();
                    fr.type = "skipped";
                    fr.note = reason;
                    res.files.add(fr);
                    return;
                }

                try {
                    String type = sniffTypeByExt(p);
                    if (type.equals("unknown") || type.equals("text")) {
                        res.processed++;
                        var fr = new FileResult();
                        fr.path = p.toString();
                        fr.type = type;
                        fr.note = "no embedded metadata expected";
                        fr.changed = false;
                        fr.originalBytes = Files.size(p);
                        fr.newBytes = fr.originalBytes;
                        res.files.add(fr);
                        return;
                    }

                    var tmpOut = write ? p : null;
                    var fr = stripOne(p, tmpOut, write);
                    res.processed++;
                    if (fr.changed) res.changed++;
                    res.files.add(fr);
                } catch (Exception ex) {
                    res.errors++;
                    var fr = new FileResult();
                    fr.path = p.toString();
                    fr.type = "error";
                    fr.note = ex.getMessage();
                    res.files.add(fr);
                }
            });

            if (json) {
                printJson(res);
            } else {
                System.out.printf("Processed: %d, changed: %d, skipped: %d, errors: %d%n",
                        res.processed, res.changed, res.skipped, res.errors);
                if (!write) System.out.println("(dry-run; use --write to apply)");
            }
        } catch (Exception e) {
            throw new RuntimeException("stripmeta failed: " + e.getMessage(), e);
        }
    }

    private Result singleResult(FileResult fr) {
        Result r = new Result();
        r.processed = 1;
        r.changed = fr.changed ? 1 : 0;
        r.files.add(fr);
        return r;
    }

    private void printJson(Result r) {
        try {
            var om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(om.writeValueAsString(r));
        } catch (Exception ignored) {}
    }

    private void printSingle(FileResult fr) throws IOException {
        System.out.printf("%s: %s (%s) %s [%d -> %d]%n",
                fr.changed ? "CHANGED" : "OK",
                fr.path, fr.type, fr.note == null ? "" : fr.note,
                fr.originalBytes, fr.newBytes);
    }

    private static String fileSkipReason(SecretScanner.Config cfg, Path p) {
        String norm = p.toString().replace('\\','/');
        for (String d : cfg.denyPaths) if (norm.contains(d)) return "deny: " + d;
        String name = p.getFileName().toString();
        String ext; int dot = name.lastIndexOf('.');
        if (dot > 0) ext = name.substring(dot+1).toLowerCase();
        else if (dot == 0) ext = name.substring(1).toLowerCase(); else ext = name.toLowerCase();
        boolean allowed = cfg.includeExtensions.contains(ext)
                || cfg.includeExtensions.contains(name)
                || cfg.includeExtensions.contains("." + ext);
        return allowed ? null : "ext not included";
    }

    private static String sniffTypeByExt(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".pdf")) return "pdf";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "jpeg";
        if (n.endsWith(".png")) return "png";
        if (n.endsWith(".tif") || n.endsWith(".tiff")) return "tiff";
        if (n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")) return "image";
        if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".java") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".yml") || n.endsWith(".yaml"))
            return "text";
        return "unknown";
    }

    private FileResult stripOne(Path in, Path outOrNull, boolean writeMode) throws IOException {
        var fr = new FileResult();
        fr.path = in.toString();
        fr.originalBytes = Files.size(in);
        String t = sniffTypeByExt(in);
        fr.type = t;

        Path out = (outOrNull != null) ? outOrNull : in;

        boolean processed = false;
        switch (t) {
            case "pdf" -> processed = stripPdf(in, out, writeMode);
            case "jpeg" -> processed = stripImage(in, out, "jpg", writeMode);
            case "png", "tiff", "image" -> processed = stripImage(in, out, "png", writeMode);
            default -> {}
        }

        if (!processed) {
            fr.changed = false;
            fr.note = "no-op";
            fr.newBytes = fr.originalBytes;
            return fr;
        }

        fr.newBytes = Files.size(out);
        fr.changed = fr.newBytes != fr.originalBytes || !in.equals(out);
        fr.note = "metadata removed";
        return fr;
    }

    // ---- Concrete strippers ----

    private boolean stripPdf(Path in, Path out, boolean writeMode) {
        try (var is = Files.newInputStream(in); PDDocument doc = PDDocument.load(is)) {
            if (doc.getDocumentInformation() != null) {
                doc.getDocumentInformation().getCOSObject().clear();
            }
            doc.setAllSecurityToBeRemoved(true);
            if (writeMode) {
                doc.save(out.toFile());
            } else {
                Path tmp = Files.createTempFile("gittor-stripmeta-", ".pdf");
                doc.save(tmp.toFile());
                Files.deleteIfExists(tmp);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean stripImage(Path in, Path out, String format, boolean writeMode) {
        try (var is = Files.newInputStream(in)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) return false;
            if (writeMode) {
                ImageIO.write(img, format, out.toFile());
            } else {
                Path tmp = Files.createTempFile("gittor-stripmeta-", "."+format);
                ImageIO.write(img, format, tmp.toFile());
                Files.deleteIfExists(tmp);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}