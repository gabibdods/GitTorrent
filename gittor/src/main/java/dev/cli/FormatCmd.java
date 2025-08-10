package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

import dev.core.SecretScanner;
import dev.analysis.LanguageDetector;
import dev.format.*;

@Command(name="format", description="Apply repository formatting per .gittorfmt.xml")
public class FormatCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--config", description="Path to .gittorfmt") Path cfgPath;
    @Option(names="--write", defaultValue="false", description="Write changes to disk (otherwise dry-run)") boolean write;
    @Option(names="--json", defaultValue="false") boolean json;
    @Option(names="--rules", description="Use rules.xml filters for include/deny consistency") Path rules;

    @Override public void run() {
        FormatterConfig cfg = FormatterConfig.load(cfgPath);
        var sec = SecretScanner.loadRules(rules);
        var det = new LanguageDetector();

        var res = new FileFormatter().formatTree(
                repo, cfg,
                p -> {
                    try {
                        byte[] data = java.nio.file.Files.readAllBytes(p);
                        return det.detect(p, data);
                    } catch (Exception e) { return "Other"; }
                },
                p -> {
                    String norm = p.toString().replace('\\','/');
                    for (String d : sec.denyPaths) if (norm.contains(d)) return "deny: "+d;
                    String name = p.getFileName().toString();
                    String ext; int dot = name.lastIndexOf('.');
                    if (dot > 0) ext = name.substring(dot+1).toLowerCase();
                    else if (dot == 0) ext = name.substring(1).toLowerCase(); else ext = name.toLowerCase();
                    boolean allowed = sec.includeExtensions.contains(ext) || sec.includeExtensions.contains(name) || sec.includeExtensions.contains("." + ext);
                    return allowed ? null : "ext not included";
                },
                write
        );

        if (json) {
            var om = new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            try { System.out.println(om.writeValueAsString(res)); } catch(Exception ignored){}
        } else {
            System.out.printf("Files considered: %d, rewritten: %d, bytesΔ: %+d%n",
                    res.filesConsidered, res.filesRewritten, res.bytesDelta);
            for (var c : res.changes) {
                System.out.printf("  - %s (%d -> %d) %s%n", c.path, c.originalBytes, c.newBytes, c.note);
            }
            if (!write) System.out.println("(dry-run; use --write to apply)");
        }
    }
}