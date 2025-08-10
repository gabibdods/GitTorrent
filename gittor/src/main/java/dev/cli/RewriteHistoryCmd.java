package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import dev.core.HistoryRewriter;

@Command(name="rewrite", description="Rewrite history")
public class RewriteHistoryCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--delete-files", split=",") String[] deleteFiles;
    @Option(names="--delete-paths", split=",") String[] deletePaths;
    @Option(names="--strip-blobs-over") String stripOver;
    @Option(names="--replace-text", split=",") String[] replaceText;
    @Option(names="--bfg-parity", defaultValue="false") boolean bfgParity;
    @Option(names="--bfg-jar") Path bfgJar;
    @Option(names="--force-push", defaultValue="false") boolean forcePush;
    @Option(names="--dry-run", defaultValue="false") boolean dryRun;
    @Override public void run() { new HistoryRewriter().rewrite(repo, deleteFiles, deletePaths, stripOver, replaceText, bfgParity, bfgJar, forcePush, dryRun); }
}
