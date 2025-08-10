package dev;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import dev.cli.*;

@Command(
    name = "gittor",
    description = "GitTorrent cli",
    mixinStandardHelpOptions = true,
    subcommands = {
            BundleCmd.class,
            ChainApplyCmd.class,
            CloneCmd.class,
            EraseHistoryCmd.class,
            FormatCmd.class,
            LinguistCmd.class,
            P2PCmd.class,
            RewriteHistoryCmd.class,
            SecretsScanCmd.class,
            StripMetaCmd.class
    }
)
public class Main implements Runnable {
    @Override public void run() {
        CommandLine.usage(this, System.out);
    }
    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }
}
