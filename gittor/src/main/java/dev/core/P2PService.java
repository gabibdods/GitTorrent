package dev.core;

import java.nio.file.Path;

public class P2PService {
    public void seedBundle(Path bundlePath) {
        System.out.println("[stub] Seeding bundle via BitTorrent: " + bundlePath);
    }
    public void fetchMagnet(String magnet, Path targetDir) {
        System.out.println("[stub] Fetching magnet: " + magnet + " into " + targetDir);
    }
}
