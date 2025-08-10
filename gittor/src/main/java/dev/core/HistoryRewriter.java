package dev.core;

import dev.util.IO;
import dev.util.Prompt;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.dircache.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class HistoryRewriter {

    public static class Plan {
        public int commitsVisited=0, commitsRewritten=0; public long blobsRemoved=0, bytesSaved=0; public boolean wouldForcePush=false;
        public String toJson(){ return String.format("{\n  \"commitsVisited\": %d,\n  \"commitsRewritten\": %d,\n  \"blobsRemoved\": %d,\n  \"bytesSaved\": %d,\n  \"wouldForcePush\": %s\n}", commitsVisited, commitsRewritten, blobsRemoved, bytesSaved, wouldForcePush); }
        public String pretty(){ return "Visited="+commitsVisited+", rewritten="+commitsRewritten+", blobsRemoved="+blobsRemoved+", bytesSaved="+bytesSaved; }
    }

    public void rewrite(Path repoPath, String[] deleteFiles, String[] deletePaths, String stripOver, String[] replaceText, boolean bfgParity, Path bfgJar, boolean forcePush, boolean dryRun) {
        if (bfgParity) { runBfg(repoPath, deleteFiles, deletePaths, stripOver, replaceText, bfgJar, dryRun); return; }
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            Plan plan = new Plan();
            long sizeLimit = parseSize(stripOver);
            Set<String> delFileGlobs = new HashSet<>(); if (deleteFiles!=null) delFileGlobs.addAll(Arrays.asList(deleteFiles));
            Set<String> delPaths = new HashSet<>(); if (deletePaths!=null) delPaths.addAll(Arrays.asList(deletePaths));
            Map<String,String> replacements = parseReplace(replaceText);

            Path backup = repoPath.resolve(".gittor").resolve("backup-"+IO.ts());
            if (!dryRun) { IO.copyTree(repoPath, backup); System.out.println("Backup: " + backup); }
            else System.out.println("[dry-run] would create backup at: " + backup);

            Map<ObjectId,ObjectId> commitMap = new HashMap<>();
            try (RevWalk walk = new RevWalk(repo)) {
                ObjectId head = repo.resolve("HEAD");
                if (head == null) throw new RuntimeException("No HEAD");
                RevCommit start = walk.parseCommit(head);
                walk.markStart(start);
                for (RevCommit c : walk) {
                    plan.commitsVisited++;
                    ObjectId newTree = filterTree(repo, c.getTree(), delFileGlobs, delPaths, sizeLimit, replacements, plan, dryRun);
                    if (!newTree.equals(c.getTree().getId())) {
                        ObjectId[] newParents = new ObjectId[c.getParentCount()];
                        for (int i=0;i<c.getParentCount();i++) {
                            ObjectId p = c.getParent(i);
                            newParents[i] = commitMap.getOrDefault(p, p);
                        }
                        ObjectId newCommit = buildCommit(repo, newTree, newParents, c, dryRun);
                        commitMap.put(c.getId(), newCommit); plan.commitsRewritten++;
                    } else commitMap.put(c.getId(), c.getId());
                }
            }

            if (!dryRun) {
                for (Ref ref : repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                    ObjectId oldId = ref.getObjectId();
                    ObjectId newId = commitMap.getOrDefault(oldId, oldId);
                    if (!newId.equals(oldId)) {
                        RefUpdate ru = repo.updateRef(ref.getName());
                        ru.setNewObjectId(newId); ru.setForceUpdate(true); ru.update();
                    }
                }
            } else {
                System.out.println("[dry-run] would update branch refs where rewritten");
            }

            plan.wouldForcePush = forcePush;
            System.out.println("Rewrite plan: " + (dryRun ? plan.toJson() : plan.pretty()));
            if (forcePush && !dryRun) {
                if (!Prompt.confirm("Proceed with FORCE PUSH of rewritten refs?", false)) {
                    System.out.println("Aborted by user."); return;
                }
                System.out.println("Local refs updated. Push with: git push --force-with-lease");
            }
        } catch (Exception e) { throw new RuntimeException("Rewrite failed: "+e.getMessage(), e); }
    }

    private ObjectId buildCommit(Repository repo, ObjectId newTree, ObjectId[] parents, RevCommit old, boolean dry) throws IOException {
        if (dry) return old.getId();
        try (ObjectInserter oi = repo.newObjectInserter()) {
            CommitBuilder cb = new CommitBuilder();
            cb.setTreeId(newTree); cb.setParentIds(parents);
            cb.setAuthor(old.getAuthorIdent()); cb.setCommitter(old.getCommitterIdent()); cb.setMessage(old.getFullMessage());
            ObjectId id = oi.insert(cb); oi.flush(); return id;
        }
    }

    private ObjectId filterTree(Repository repo, RevTree tree, Set<String> delFileGlobs, Set<String> delPathSet, long sizeLimit, Map<String,String> replacements, Plan plan, boolean dry) throws IOException {
        try (TreeWalk tw = new TreeWalk(repo)) {
            tw.addTree(tree); tw.setRecursive(false);
            DirCache newIdx = DirCache.newInCore(); DirCacheBuilder b = newIdx.builder();
            filterRecursive(repo, tw, "", delFileGlobs, delPathSet, sizeLimit, replacements, plan, b, dry);
            b.finish(); try (ObjectInserter oi = repo.newObjectInserter()) {
                ObjectId newTree = newIdx.writeTree(oi); if (!dry) oi.flush(); return newTree;
            }
        }
    }

    private void filterRecursive(Repository repo, TreeWalk tw, String prefix, Set<String> delFileGlobs, Set<String> delPathSet, long sizeLimit, Map<String,String> replacements, Plan plan, DirCacheBuilder b, boolean dry) throws IOException {
        while (tw.next()) {
            String name = tw.getNameString();
            String full = prefix.isEmpty()? name : prefix + "/" + name;
            if (tw.isSubtree()) {
                tw.enterSubtree();
                boolean dropDir = delPathSet.stream().anyMatch(p -> match(full, p));
                if (dropDir) { skipSubtree(tw); continue; }
                CanonicalTreeParser p = new CanonicalTreeParser(null, repo.newObjectReader(), tw.getObjectId(0));
                TreeWalk sub = new TreeWalk(repo); sub.addTree(p); sub.setRecursive(false);
                filterRecursive(repo, sub, full, delFileGlobs, delPathSet, sizeLimit, replacements, plan, b, dry);
                continue;
            }
            ObjectId blobId = tw.getObjectId(0);
            boolean drop = delFileGlobs.stream().anyMatch(g -> match(name, g)) || delPathSet.stream().anyMatch(p -> match(full, p));
            byte[] data = null;
            if (!drop || sizeLimit>0 || !replacements.isEmpty()) {
                try (ObjectReader or = repo.newObjectReader()) { data = or.open(blobId, Constants.OBJ_BLOB).getBytes(); }
            }
            if (sizeLimit > 0 && data.length > sizeLimit) { drop=true; plan.blobsRemoved++; plan.bytesSaved += data.length; }
            if (drop) continue;
            if (!replacements.isEmpty() && looksText(data)) {
                String s = new String(data); String out = s;
                for (var e : replacements.entrySet()) out = out.replaceAll(e.getKey(), e.getValue());
                if (!out.equals(s)) { data = out.getBytes(); if (out.length() < s.length()) plan.bytesSaved += (s.length()-out.length()); }
            }
            DirCacheEntry dce = new DirCacheEntry(full);
            dce.setFileMode(tw.getFileMode(0));
            if (!dry) {
                try (ObjectInserter oi = repo.newObjectInserter()) {
                    ObjectId nb = oi.insert(Constants.OBJ_BLOB, data); oi.flush(); dce.setObjectId(nb);
                }
            } else dce.setObjectId(blobId);
            b.add(dce);
        }
    }

    private boolean looksText(byte[] data){ int limit=Math.min(data.length,4096); for (int i=0;i<limit;i++) if (data[i]==0) return false; return true; }
    private void skipSubtree(TreeWalk tw) throws IOException { int d=tw.getDepth(); while (tw.next() && tw.getDepth()>d){} }
    private boolean match(String s, String glob){ String rx=glob.replace(".", "\\.").replace("*",".*").replace("?","."); return s.matches(rx); }

    private long parseSize(String s){ if (s==null||s.isBlank()) return -1; String u=s.trim().toUpperCase(); long m=1; if(u.endsWith("KB")){m=1024;u=u.substring(0,u.length()-2);} else if(u.endsWith("MB")){} return parseSize2(s); }
    private long parseSize2(String s){ try{ String u=s.trim().toUpperCase(); long mult=1; if(u.endsWith("KB")){mult=1024; u=u.substring(0,u.length()-2);} else if(u.endsWith("MB")){mult=1024*1024; u=u.substring(0,u.length()-2);} else if(u.endsWith("GB")){mult=1024L*1024*1024; u=u.substring(0,u.length()-2);} return Long.parseLong(u)*mult; }catch(Exception e){ return -1; } }

    private Map<String,String> parseReplace(String[] arr){ Map<String,String> m=new HashMap<>(); if(arr==null) return m; for(String kv: arr){ int i=kv.indexOf("=>"); if(i>0) m.put(kv.substring(0,i), kv.substring(i+2)); } return m; }

    private void runBfg(Path repo, String[] deleteFiles, String[] deletePaths, String stripOver, String[] replaceText, Path bfgJar, boolean dryRun) {
        if (bfgJar == null) throw new IllegalArgumentException("--bfg-jar is required for --bfg-parity");
        try {
            var cmd = new java.util.ArrayList<String>();
            cmd.addAll(java.util.List.of("java","-Xmx2g","-jar", bfgJar.toString()));
            if (stripOver!=null) cmd.addAll(java.util.List.of("--strip-blobs-bigger-than", stripOver));
            if (deleteFiles!=null) for(String g: deleteFiles) cmd.addAll(java.util.List.of("--delete-files", g));
            if (deletePaths!=null) for(String p: deletePaths) cmd.addAll(java.util.List.of("--delete-folders", p));
            cmd.add(repo.toAbsolutePath().toString());
            System.out.println("Running: "+String.join(" ", cmd));
            if (!dryRun) new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } catch (Exception e) { throw new RuntimeException("BFG run failed: "+e.getMessage(), e); }
    }

    // Erase history: create an orphan commit from current tree on branch
    public void eraseHistory(Path repoPath, String branch, boolean force) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId tree = repo.resolve("HEAD^{tree}");
            if (tree == null) throw new RuntimeException("Cannot resolve HEAD^{tree}");
            try (ObjectInserter oi = repo.newObjectInserter()) {
                CommitBuilder cb = new CommitBuilder();
                cb.setTreeId(tree);
                cb.setAuthor(new PersonIdent(repo));
                cb.setCommitter(new PersonIdent(repo));
                cb.setMessage("Reset history to current snapshot");
                ObjectId root = oi.insert(cb); oi.flush();
                String br = (branch==null||branch.isBlank())? "main" : branch;
                RefUpdate ru = repo.updateRef("refs/heads/"+br);
                ru.setNewObjectId(root); ru.setForceUpdate(force); ru.update();
                System.out.println("Created orphan root at "+br+" -> "+root.name());
            }
        } catch (Exception e) { throw new RuntimeException("Erase history failed: "+e.getMessage(), e); }
    }
}
