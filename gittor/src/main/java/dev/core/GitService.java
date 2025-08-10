package dev.core;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;
import java.nio.file.Path;

public class GitService {
    public void cloneRepo(String url, String branch, int depth, Path dir) {
        try {
            CloneCommand cmd = Git.cloneRepository().setURI(url).setDirectory(dir.toFile());
            if (branch != null && !branch.isBlank()) cmd.setBranch(branch);
            if (depth > 0) cmd.setDepth(depth);
            try (Git g = cmd.call()) {}
            System.out.println("Cloned: " + url + (branch!=null?(" @"+branch):"") + " -> " + dir);
        } catch (Exception e) { throw new RuntimeException("Clone failed: "+e.getMessage(), e); }
    }
}
