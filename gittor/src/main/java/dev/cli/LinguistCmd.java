package dev.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

import dev.analysis.LanguageDetector;
import dev.analysis.LanguageStats;
import dev.core.SecretScanner;

@Command(name="linguist", description="Estimate language percentages (bytes)")
public class LinguistCmd implements Runnable {
    @Option(names="--repo", required=true) Path repo;
    @Option(names="--languages", description="languages.xml overrides (ext→lang)") Path languages;
    @Option(names="--rules", description="Use rules.xml include/deny for consistency") Path rules;
    @Option(names="--json", defaultValue="false") boolean json;

    @Override public void run() {
        var cfg = SecretScanner.loadRules(rules);
        var det = new LanguageDetector(cfg);
        det.loadOverrides(languages);
        LanguageStats.PathFilter filter = det::fileSkipReason;
        var res = new LanguageStats().compute(repo, det, languages, filter);

        var sizer = new dev.analysis.ProjectSizer();
        res.totalProjectBytes = sizer.totalBytes(repo, filter::fileSkipReason);

        if (res.totalProjectBytes > 0 && !res.percentages.isEmpty()) {
            res.percentages.forEach((lang, pct) -> {
                long part = Math.round((pct / 100.0) * res.totalProjectBytes);
                res.partitions.put(lang, part);
            });
        }

        if (json) {
            System.out.println(res.toJson());
        } else {
            System.out.println("Total text bytes: " + res.totalBytes);
            System.out.println("Total data bytes: " + res.totalProjectBytes);
            res.percentages.forEach((k,v) -> System.out.printf(" - %-16s %6.2f%%%n", k, v));
            if (!res.partitions.isEmpty()) {
                System.out.println("Partitions: ");
                res.partitions.forEach((k,v) -> System.out.printf(" - %-16s %d%n", k, v));
            }
            if (!res.skipped.isEmpty()) System.out.println("Skipped: " + res.skipped.size());
        }
    }
}