package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import dev.core.ChainService;
import dev.model.GittorSpec;

@Command(name="chain", description="Apply a .gittor chain spec")
public class ChainApplyCmd implements Runnable {
    @Option(names="--file", defaultValue=".gittor") Path file;
    @Option(names="--parallel", defaultValue="4") int parallel;
    @Option(names="--continue-on-error", defaultValue="false") boolean cont;
    @Override public void run() { new ChainService(parallel, cont).apply(GittorSpec.load(file)); }
}
