package io.github.jmecn.fieldguideexport.export.resources;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summarizes on-disk size of {@code guide-export/} for manifest stats. */
public final class ExportDirectoryStats {

    private ExportDirectoryStats() {}

    public record Summary(long fileCount, long totalBytes) {}

    public static Summary summarize(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return new Summary(0, 0);
        }
        var acc = new long[2];
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                acc[0]++;
                acc[1] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return new Summary(acc[0], acc[1]);
    }

    public static Map<String, Object> toMap(Summary summary) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exportFileCount", summary.fileCount());
        m.put("exportTotalBytes", summary.totalBytes());
        return m;
    }
}
