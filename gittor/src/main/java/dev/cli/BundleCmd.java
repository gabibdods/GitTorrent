package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import dev.core.BundleService;

@Command(name="bundle", description="Bundle operations")
public class BundleCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--out", required=true) Path out;
    @Override public void run() { new BundleService().makeBundle(repo, out); }
}
