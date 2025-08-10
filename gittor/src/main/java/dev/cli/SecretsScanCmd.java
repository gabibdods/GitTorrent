package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import dev.core.SecretScanner;

@Command(name="secrets", description="Secret scanning")
public class SecretsScanCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--rules") Path rules;
    @Option(names="--install-hook", defaultValue="false") boolean installHook;
    @Option(names="--json", defaultValue="false") boolean json;
    @Override public void run() {
        if (installHook) {
            SecretScanner.installPreCommitHook(repo);
            return;
        }
        var cfg = SecretScanner.loadRules(rules);
        var res = new SecretScanner(cfg).scanWorkingTree(repo);
        System.out.println(json ? res.toJson() : res.pretty());
    }
}
