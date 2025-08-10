package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import dev.core.GitService;

@Command(name="clone", description="Clone a repository")
public class CloneCmd implements Runnable {
    @Parameters(index="0") String url;
    @Option(names="--branch") String branch;
    @Option(names="--depth", defaultValue="0") int depth;
    @Option(names="--dir") Path dir;
    @Override public void run() { new GitService().cloneRepo(url, branch, depth, dir); }
}
