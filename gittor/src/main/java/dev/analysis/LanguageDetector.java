package dev.analysis;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import dev.core.SecretScanner;

public class LanguageDetector {
    private final Map<String, String> extToLang = new HashMap<>(Map.ofEntries(
            Map.entry("java","Java"), Map.entry("kt","Kotlin"), Map.entry("scala","Scala"),
            Map.entry("js","JavaScript"), Map.entry("ts","TypeScript"), Map.entry("tsx","TypeScript"), Map.entry("jsx","JavaScript"),
            Map.entry("py","Python"), Map.entry("rb","Ruby"), Map.entry("go","Go"), Map.entry("rs","Rust"),
            Map.entry("c","C"), Map.entry("cc","C++"), Map.entry("cpp","C++"), Map.entry("h","C/C++ Header"), Map.entry("hpp","C++ Header"),
            Map.entry("php","PHP"), Map.entry("swift","Swift"), Map.entry("m","Objective-C"), Map.entry("mm","Objective-C++"),
            Map.entry("cs","C#"), Map.entry("sh","Shell"), Map.entry("bash","Shell"), Map.entry("ps1","PowerShell"),
            Map.entry("yaml","YAML"), Map.entry("yml","YAML"), Map.entry("json","JSON"), Map.entry("xml","XML"),
            Map.entry("md","Markdown"), Map.entry("txt","Text"), Map.entry("ini","INI"), Map.entry("toml","TOML"),
            Map.entry("gradle","Gradle"), Map.entry("groovy","Groovy"), Map.entry("dart","Dart"), Map.entry("vue","Vue"),
            Map.entry("svelte","Svelte"), Map.entry("scss","SCSS"), Map.entry("css","CSS"), Map.entry("html","HTML")
    ));

    private static final List<Map.Entry<Pattern, String>> SHEBANG = List.of(
            Map.entry(Pattern.compile("^#!.*\\bpython(\\d+)?\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "Python"),
            Map.entry(Pattern.compile("^#!.*\\bnode\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "JavaScript"),
            Map.entry(Pattern.compile("^#!.*\\b(sh|bash|zsh)\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "Shell"),
            Map.entry(Pattern.compile("^#!.*\\bpowershell\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "PowerShell"),
            Map.entry(Pattern.compile("^#!.*\\bperl\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "Perl"),
            Map.entry(Pattern.compile("^#!.*\\bruby\\b.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), "Ruby")
    );

    private final SecretScanner.Config cfg;
    private List<Map.Entry<Pattern,String>> customShebang = null;

    public LanguageDetector() { this.cfg = null; }
    public LanguageDetector(SecretScanner.Config cfg) {
        this.cfg = cfg;
    }

    public void loadOverrides(Path languagesXml) {
        if (languagesXml == null) return;
        try {
            var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(languagesXml.toFile());
            doc.getDocumentElement().normalize();

            var mapNodes = doc.getElementsByTagName("map");
            for (int i=0; i<mapNodes.getLength(); i++) {
                var e = (org.w3c.dom.Element) mapNodes.item(i);
                String ext  = e.getAttribute("ext").toLowerCase();
                String lang = e.getAttribute("lang");
                if (!ext.isBlank() && !lang.isBlank()) {
                    extToLang.put(ext, lang);
                }
            }

            var sbNodes = doc.getElementsByTagName("shebang");
            if (sbNodes.getLength() > 0) {
                List<Map.Entry<Pattern,String>> merged = new ArrayList<>(SHEBANG);
                for (int i=0; i<sbNodes.getLength(); i++) {
                    var e = (org.w3c.dom.Element) sbNodes.item(i);
                    String pat  = e.getAttribute("pattern");
                    String lang = e.getAttribute("lang");
                    if (!pat.isBlank() && !lang.isBlank()) {
                        merged.add(Map.entry(Pattern.compile(pat, Pattern.CASE_INSENSITIVE|Pattern.MULTILINE), lang));
                    }
                }
                customShebang = merged;
            }
        } catch (Exception e) {
            throw new RuntimeException("languages.xml parse failed: " + e.getMessage(), e);
        }
    }


    public static boolean looksBinary(byte[] data) {
        int limit = Math.min(data.length, 4096);
        for (int i=0;i<limit;i++) if (data[i]==0) return true;
        return false;
    }

    public String detect (Path file, byte[] content) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot == 0 && name.length() > 1) {
            String ext = name.substring(1).toLowerCase();
            String lang = extToLang.get(ext);
            if (lang != null) return lang;
        } else if (dot > 0) {
            String ext = name.substring(dot+1).toLowerCase();
            String lang = extToLang.get(ext);
            if (lang != null) return lang;
        }
        String head = new String(content, 0, Math.min(content.length, 512));
        List<Map.Entry<Pattern,String>> sb = (customShebang != null) ? customShebang : SHEBANG;
        for (var e : sb) {
            if (e.getKey().matcher(head).find()) return e.getValue();
        }
        for (var e : SHEBANG) {
            if (e.getKey().matcher(head).find()) return e.getValue();
        }
        return "Other";
    }

    public String fileSkipReason(Path p) {
        if (cfg == null) return null;
        String norm = p.toString().replace('\\','/');
        for (String d : cfg.denyPaths) if (norm.contains(d)) return "deny: " + d;
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext;
        if (dot > 0) ext = name.substring(dot + 1).toLowerCase();
        else if (dot == 0) ext = name.substring(1).toLowerCase();
        else ext = name.toLowerCase();
        boolean allowed = cfg.includeExtensions.contains(ext) || cfg.includeExtensions.contains(name) || cfg.includeExtensions.contains("." + ext);
        return allowed ? null : "ext not included: " + (dot == 0 ? "." + ext : ext);
    }
}
