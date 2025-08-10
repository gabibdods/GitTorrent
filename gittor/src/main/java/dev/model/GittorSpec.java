package dev.model;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.*;
import java.util.*;
import java.nio.file.*;

@XmlRootElement(name="gittor")
@XmlAccessorType(XmlAccessType.FIELD)
public class GittorSpec {
    public String version;
    public Defaults defaults = new Defaults();
    @XmlElementWrapper(name="repositories") @XmlElement(name="repo")
    public List<Repository> repositories = new ArrayList<>();

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Defaults {
        public String branch = "main";
        public int depth = 0;
        public String protocol = "https";
        public String baseDir = "workspace";
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Repository {
        @XmlAttribute public String name;
        public String url;
        public String branch;
        public Integer depth;
        public String baseDir;
        @XmlElementWrapper(name="depends_on") @XmlElement(name="name")
        public List<String> dependsOn = new ArrayList<>();
        public Post post;
        // Optional alternative sources for v1.5
        public String bundle;       // path to git bundle
        public String magnet;       // magnet link for torrent

        public Path dirPath(String defaultBase) {
            String dirName = (name != null && !name.isBlank()) ? name : "repo";
            String base = (baseDir != null && !baseDir.isBlank()) ? baseDir : defaultBase;
            return Paths.get(base).resolve(dirName);
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Post {
        @XmlElement(name="scan-secrets") public Boolean scanSecrets;
        @XmlElement(name="rewrite-history") public RewriteHistory rewrite;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RewriteHistory {
        @XmlElement(name="delete-files") public List<String> deleteFiles = new ArrayList<>();
        @XmlElement(name="delete-paths") public List<String> deletePaths = new ArrayList<>();
        @XmlElement(name="strip-blobs-over") public String stripBlobsOver;
        @XmlElement(name="replace-text") public List<TextReplace> replaceText = new ArrayList<>();
        @XmlElement(name="force-push") public Boolean forcePush;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TextReplace {
        @XmlAttribute public String find;
        @XmlAttribute public String replace;
    }

    public static GittorSpec load(Path path) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(GittorSpec.class);
            Unmarshaller um = ctx.createUnmarshaller();
            return (GittorSpec) um.unmarshal(path.toFile());
        } catch (Exception e) { throw new RuntimeException("load .gittor failed: "+e.getMessage(), e); }
    }
}
