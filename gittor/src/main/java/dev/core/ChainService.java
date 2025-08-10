package dev.core;

import dev.model.GittorSpec;
import dev.util.Locking;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.nio.file.*;

public class ChainService {
    private final int parallelism;
    private final boolean continueOnError;
    private final GitService git = new GitService();

    public ChainService(int parallelism, boolean continueOnError) {
        this.parallelism = parallelism;
        this.continueOnError = continueOnError;
    }

    public void apply(GittorSpec spec) {
        var graph = buildGraph(spec.repositories);
        var layers = levelize(graph);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        for (List<RepoNode> layer : layers) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (RepoNode n : layer) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        process(n.repo, spec);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        pool.shutdown();
    }

    private void process(GittorSpec.Repository r, GittorSpec spec) throws IOException {
        Path dir = r.dirPath(spec.defaults.baseDir);
        try (Locking ignored = Locking.acquire(dir.getParent() != null ? dir.getParent() : Path.of("."), r.name)) {
            Files.createDirectories(dir.getParent() != null ? dir.getParent() : Path.of("."));
            if (r.bundle != null && !r.bundle.isBlank()) {
                new BundleService().cloneFromBundle(Path.of(r.bundle), dir);
            } else if (r.magnet != null && !r.magnet.isBlank()) {
                new P2PService().fetchMagnet(r.magnet, dir); // may require external jars
            } else {
                git.cloneRepo(r.url, r.branch != null ? r.branch : spec.defaults.branch,
                        r.depth != null ? r.depth : spec.defaults.depth, dir);
            }
            if (r.post != null) {
                if (Boolean.TRUE.equals(r.post.scanSecrets)) {
                    var res = new SecretScanner().scanWorkingTree(dir);
                    System.out.println(res.pretty());
                }
                if (r.post.rewrite != null) {
                    new HistoryRewriter().rewrite(dir,
                        r.post.rewrite.deleteFiles.toArray(new String[0]),
                        r.post.rewrite.deletePaths.toArray(new String[0]),
                        r.post.rewrite.stripBlobsOver,
                        r.post.rewrite.replaceText.stream().map(t -> t.find+"=>"+t.replace).toArray(String[]::new),
                        false, null, Boolean.TRUE.equals(r.post.rewrite.forcePush), true);
                }
            }
        }
    }

    private Map<String, RepoNode> buildGraph(List<GittorSpec.Repository> repos) {
        Map<String, RepoNode> nodes = new LinkedHashMap<>();
        for (var r : repos) nodes.put(r.name, new RepoNode(r));
        for (var r : repos) {
            var node = nodes.get(r.name);
            for (String dep : r.dependsOn) node.deps.add(nodes.get(dep));
        }
        return nodes;
    }

    private List<List<RepoNode>> levelize(Map<String, RepoNode> nodes) {
        Map<RepoNode,Integer> indeg = new HashMap<>();
        for (RepoNode n : nodes.values()) {
            indeg.putIfAbsent(n, 0);
            for (RepoNode d : n.deps) indeg.put(d, indeg.getOrDefault(d,0)+1);
        }
        List<List<RepoNode>> layers = new ArrayList<>();
        List<RepoNode> zero = indeg.entrySet().stream().filter(e->e.getValue()==0).map(Map.Entry::getKey).collect(Collectors.toList());
        while(!zero.isEmpty()) {
            layers.add(zero);
            List<RepoNode> next = new ArrayList<>();
            for (RepoNode z : zero) for (RepoNode n : nodes.values()) if (n.deps.contains(z)) {
                indeg.put(n, indeg.get(n)-1); if (indeg.get(n)==0) next.add(n);
            }
            zero = next;
        }
        return layers;
    }

    static class RepoNode {
        final GittorSpec.Repository repo; final List<RepoNode> deps = new ArrayList<>();
        RepoNode(GittorSpec.Repository r){ this.repo=r; }
    }
}
