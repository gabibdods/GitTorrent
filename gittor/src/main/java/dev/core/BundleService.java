package dev.core;

import java.nio.file.*;
import java.io.*;

public class BundleService {
    public void makeBundle(Path repoRoot, Path out) {
        try {
            new ProcessBuilder("git","bundle","create", out.toString(), "--all")
                    .directory(repoRoot.toFile()).inheritIO().start().waitFor();
            System.out.println("Bundle created: " + out);
        } catch (Exception e) { throw new RuntimeException("bundle failed: "+e.getMessage(), e); }
    }
    public void cloneFromBundle(Path bundle, Path dir) {
        try {
            new ProcessBuilder("git","clone", bundle.toString(), dir.toString()).inheritIO().start().waitFor();
            System.out.println("Cloned from bundle: " + bundle + " -> " + dir);
        } catch (Exception e) { throw new RuntimeException("clone bundle failed: "+e.getMessage(), e); }
    }
}
