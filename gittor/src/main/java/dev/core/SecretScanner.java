package dev.core;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

public class SecretScanner {

    public static class Rule {
        public String name;
        public String pattern;
        public Rule(){} public Rule(String n, String p){
            name=n;
            pattern=p;
        }
    }
    public static class Config {
        public List<String> includeExtensions = new ArrayList<>(List.of("txt","md","java","xml","yml","yaml","json","properties","env",".gitignore",".gitattributes"));
        public List<String> denyPaths = new ArrayList<>(List.of("node_modules/",".git/","build/","dist/","target/",".idea/"));
        public List<String> allowPatterns = new ArrayList<>();
        public double entropyThreshold = 3.5;
        public List<Rule> rules = defaultRules();

        static List<Rule> defaultRules() {
            return List.of(
                    new Rule("AWS Access Key ID", "AKIA[0-9A-Z]{16}"),
                    new Rule("AWS Secret", "(?i)aws_secret_access_key\s*[:=]\s*([A-Za-z0-9/+=]{40})"),
                    new Rule("GCP JSON key", "\"type\"\\s*:\\s*\"service_account\""),
                    new Rule("Azure ServiceBus", "Endpoint=sb://.*?\\.servicebus\\.windows\\.net/;SharedAccessKeyName=.*?;SharedAccessKey=[A-Za-z0-9+/=]+"),
                    new Rule("Stripe Live Secret", "sk_live_[0-9a-zA-Z]{24}"),
                    new Rule("Twilio API Key", "SK[0-9a-fA-F]{32}"),
                    new Rule("GitHub PAT", "ghp_[A-Za-z0-9]{36}"),
                    new Rule("Slack token", "xox[baprs]-[A-Za-z0-9-]{10,48}"),
                    new Rule("JWT", "[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+"),
                    new Rule("Private Key", "-----BEGIN (?:RSA|DSA|EC|OPENSSH|PGP) PRIVATE KEY-----")
            );
        }
    }
    public static class Finding {
        public String path;
        public String rule;
        public String sample;
        public Finding(){} public Finding(String p,String r,String s){
            path=p;
            rule=r;
            sample=s;
        }
    }
    public static class Result {
        public List<Finding> findings = new ArrayList<>();
        public long filesScanned=0;

        public boolean repoSkipped = false;
        public String repoSkipReason = null;
        public List<Skipped> skippedPaths = new ArrayList<>();
        public Map<String, Long> skippedCounts = new HashMap<>();

        public String toJson(){
            try {
                return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this);
            } catch(Exception e) {
                return "{ error }";
            }
        }
        public String pretty(){
            StringBuilder sb = new StringBuilder();
            sb.append("Files scanned: ").append(filesScanned).append('\n');
            sb.append("Findings: ").append(findings.size()).append('\n');
            if (repoSkipped) {
                sb.append("Skipped repos: ").append(repoSkipReason).append('\n');
            } else if (!skippedPaths.isEmpty()) {
                sb.append("Skipped entries: ").append(skippedPaths.size()).append('\n');
            }
            for (Finding f: findings) sb.append(" - ").append(f.path).append(" :: ").append(f.rule).append('\n');
            return sb.toString();
        }
    }
    public static class Skipped {
        public String path;
        public String reason;
        public Skipped() {};
        public Skipped(String path, String reason) { this.path = path; this.reason = reason; }
    }

    public Config config = new Config();
    public SecretScanner() {}
    public SecretScanner(Config cfg){ this.config = cfg; }

    public static Config loadRules(Path rulesXml) {
        if (rulesXml == null) return new Config();
        try {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rulesXml.toFile());
            doc.getDocumentElement().normalize();
            Config cfg = new Config();
            var rules = doc.getElementsByTagName("rule");
            List<Rule> rs = new ArrayList<>();
            for (int i=0;i<rules.getLength();i++) {
                Element e = (Element) rules.item(i);
                rs.add(new Rule(e.getAttribute("name"), e.getTextContent().trim()));
            }
            if (!rs.isEmpty()) cfg.rules = rs;
            var allow = doc.getElementsByTagName("allow");
            if (allow.getLength()>0) cfg.allowPatterns = getTexts((Element)allow.item(0),"pattern");
            var deny = doc.getElementsByTagName("deny");
            if (deny.getLength()>0) cfg.denyPaths = getTexts((Element)deny.item(0),"path");
            var exts = doc.getElementsByTagName("extensions");
            if (exts.getLength()>0) cfg.includeExtensions = getTexts((Element)exts.item(0),"ext");
            var entropy = doc.getElementsByTagName("entropy");
            if (entropy.getLength()>0) {
                String th = ((Element)entropy.item(0)).getAttribute("threshold");
                if (!th.isBlank()) cfg.entropyThreshold = Double.parseDouble(th);
            }
            return cfg;
        } catch (Exception e) { throw new RuntimeException("rules.xml parse failed: "+e.getMessage(), e); }
    }

    private static List<String> getTexts(Element parent, String tag) {
        List<String> out = new ArrayList<>(); var list = parent.getElementsByTagName(tag);
        for (int i=0;i<list.getLength();i++) { String v = list.item(i).getTextContent().trim(); if (!v.isBlank()) out.add(v); }
        return out;
    }

    public Result scanWorkingTree(Path root) {
        Result r = new Result();

        String rskip = repoSkipReason(root);
        if (rskip != null) {
            r.repoSkipped = true;
            r.repoSkipReason = rskip;
            r.skippedPaths.add(new Skipped(root.toString(), rskip));
            r.skippedCounts.put("deny", r.skippedCounts.getOrDefault("deny", 0L) + 1L);
            return r;
        }

        try {
            Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                String reason = fileSkipReason(p);
                if (reason != null) {
                    r.skippedPaths.add(new Skipped(p.toString(), reason));
                    if (reason.startsWith("Matched deny rule:")) {
                        r.skippedCounts.put("deny", r.skippedCounts.getOrDefault("deny", 0L) + 1L);
                    } else if (reason.startsWith("Extension not included:")) {
                        r.skippedCounts.put("ext", r.skippedCounts.getOrDefault("ext", 0L) + 1L);
                    }
                }
                scanFile(p, r);
            });
        } catch (IOException e) { throw new RuntimeException(e); }
        return r;
    }

    private String repoSkipReason(Path repoRoot) {
        String norm = repoRoot.toString().replace('\\','/') + (repoRoot.toString().replace('\\','/').endsWith("/") ? "" : "/");
        for (String d : config.denyPaths) if (norm.contains(d)) return "Matched deny rule: " + d;
        return null;
    }

    private String fileSkipReason(Path p) {
        String norm = p.toString().replace('\\','/');
        for (String d : config.denyPaths) if (norm.contains(d)) return "Matched deny rule: " + d;

        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext;
        if (dot > 0) {
            ext = name.substring(dot + 1).toLowerCase();
        } else if (dot == 0) {
            ext = name.substring(1).toLowerCase();
        } else {
            ext = name.toLowerCase();
        }

        boolean allowed = config.includeExtensions.contains(ext) || config.includeExtensions.contains(name) || config.includeExtensions.contains("." + ext);
        return !allowed ? "Extension not included: " + (dot == 0 ? "." + ext : ext) : null;
    }

    private void scanFile(Path p, Result r) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(p);

            int limit = Math.min(data.length, 4096); for (int i=0;i<limit;i++) if (data[i]==0) {
                r.skippedPaths.add(new Skipped(p.toString(), "NUL byte detected in binary file"));
                r.skippedCounts.put("binary", r.skippedCounts.getOrDefault("binary", 0L) + 1L);
                return;
            }

            String s = new String(data);
            r.filesScanned++;

            for (String allow : config.allowPatterns) if (s.contains(allow)) {
                r.skippedPaths.add(new Skipped(p.toString(), "Allowlist pattern matched: " + allow));
                r.skippedCounts.put("allow", r.skippedCounts.getOrDefault("allow", 0L) + 1L);
                return;
            }

            for (Rule rule : config.rules) {
                Pattern pat = Pattern.compile(rule.pattern);
                var m = pat.matcher(s);
                while (m.find()) r.findings.add(new Finding(p.toString(), rule.name, m.group()));
            }

            for (String tok : s.split("[^A-Za-z0-9+/=_-]+")) {
                if (tok.length() >= 20 && shannon(tok) >= config.entropyThreshold) r.findings.add(new Finding(p.toString(), "entropy>="+config.entropyThreshold, tok));
            }

        } catch (IOException e) {
            r.skippedPaths.add(new Skipped(p.toString(), "IO error: " + e.getMessage()));
            r.skippedCounts.put("io", r.skippedCounts.getOrDefault("io", 0L) + 1L);
        }
    }

    private double shannon(String s) {
        int[] counts = new int[256]; int n=0;
        for (char c: s.toCharArray()) if (c<256) { counts[c]++; n++; }
        double H=0.0; for (int c: counts) if (c!=0) { double p=(double)c/n; H -= p*(Math.log(p)/Math.log(2)); }
        return H;
    }

    public static void installPreCommitHook(Path repoRoot) {
        Path hooks = repoRoot.resolve(".git/hooks");
        try {
            Files.createDirectories(hooks);
            Path hook = hooks.resolve("pre-commit");
            String script = """
                    #!/bin/sh
                    if [ -z "$GITTOR_CLASSPATH" ]; then echo 'Set GITTOR_CLASSPATH to your jars'; exit 1; fi
                    exec java -cp "$GITTOR_CLASSPATH" dev.you.gittor.Main secrets scan --repo . --json
                    """;
            Files.writeString(hook, script);
            hook.toFile().setExecutable(true, false);
            System.out.println("Installed pre-commit hook (uses $GITTOR_CLASSPATH).");
        } catch (IOException e) { throw new RuntimeException("hook install failed: "+e.getMessage(), e); }
    }
}
