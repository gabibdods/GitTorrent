package dev.format;

import java.nio.file.*;
import java.util.*;

public class FormatterConfig {
    public String eol = "lf";
    public boolean trimTrailing = true;
    public boolean ensureFinalNewline = true;
    public int collapseBlankLinesTo = 2;
    public boolean detab = true;
    public int tabWidth = 2;
    public Integer wrapMarkdownAt = 0;

    public Map<String, Map<String,String>> overrides = new HashMap<>();

    public static FormatterConfig load(Path xml) {
        FormatterConfig cfg = new FormatterConfig();
        if (xml == null) return cfg;
        try {
            var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(xml.toFile());
            var root = doc.getDocumentElement();
            java.util.function.BiConsumer<String, java.util.function.Consumer<String>> opt = (tag, setter) -> {
                var n = root.getElementsByTagName(tag);
                if (n.getLength()>0) setter.accept(n.item(0).getTextContent().trim());
            };
            opt.accept("eol", v -> cfg.eol = v.toLowerCase());
            opt.accept("trimTrailing", v -> cfg.trimTrailing = Boolean.parseBoolean(v));
            opt.accept("ensureFinalNewline", v -> cfg.ensureFinalNewline = Boolean.parseBoolean(v));
            opt.accept("collapseBlankLinesTo", v -> cfg.collapseBlankLinesTo = Integer.parseInt(v));
            opt.accept("detab", v -> cfg.detab = Boolean.parseBoolean(v));
            opt.accept("tabWidth", v -> cfg.tabWidth = Integer.parseInt(v));
            opt.accept("wrapMarkdownAt", v -> cfg.wrapMarkdownAt = Integer.parseInt(v));

            var langs = root.getElementsByTagName("language");
            for (int i=0;i<langs.getLength();i++) {
                var e = (org.w3c.dom.Element) langs.item(i);
                String key = e.getAttribute("name");
                Map<String,String> kv = new HashMap<>();
                var children = e.getChildNodes();
                for (int j=0;j<children.getLength();j++) {
                    if (children.item(j) instanceof org.w3c.dom.Element ce) {
                        kv.put(ce.getTagName(), ce.getTextContent().trim());
                    }
                }
                cfg.overrides.put(key, kv);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load .gittorfmt.xml: " + e.getMessage(), e);
        }
        return cfg;
    }

    public Map<String,String> forKey(String key) {
        return overrides.getOrDefault(key, Map.of());
    }
}