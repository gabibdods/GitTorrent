package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import dev.core.P2PService;

@Command(name="p2p", description="P2P operations (stubs)")
public class P2PCmd implements Runnable {
    @Option(names="--bundle") Path bundle;
    @Option(names="--magnet") String magnet;
    @Option(names="--out") Path out;
    @Override public void run() {
        P2PService s = new P2PService();
        if (bundle != null) s.seedBundle(bundle);
        if (magnet != null && out != null) s.fetchMagnet(magnet, out);
        if (bundle == null && (magnet == null || out == null)) System.out.println("Specify either --bundle or --magnet + --out");
    }
}
