package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import dev.core.HistoryRewriter;

@Command(name="erase", description="Erase history with an orphan root commit")
public class EraseHistoryCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--branch") String branch;
    @Option(names="--force", defaultValue="false") boolean force;
    @Override public void run() { new HistoryRewriter().eraseHistory(repo, branch, force); }
}
