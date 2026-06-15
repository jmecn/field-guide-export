package io.github.jmecn.fieldguideexport.resources;

import io.github.jmecn.minecraftwebexport.emi.icon.ItemIconWriter;
import io.github.jmecn.minecraftwebexport.io.ExportWriteQueue;

import net.minecraft.client.Minecraft;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public final class FieldGuideIconExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private FieldGuideIconExporter() {}

    public static int export(
            Path iconsRoot,
            Minecraft client,
            Set<String> onlyItemIds,
            Map<String, Integer> usageWeights) throws IOException {
        Files.createDirectories(iconsRoot);
        Path tempRoot = Files.createTempDirectory("field-guide-export-icons");
        try {
            int sprites;
            
            try (ExportWriteQueue writes = ExportWriteQueue.create(tempRoot, false)) {
                sprites = ItemIconWriter.export(
                        tempRoot, client, onlyItemIds, Set.of(), usageWeights, Map.of(), writes);
                writes.awaitIdle();
            }
            Path emiIcons = tempRoot.resolve("emi").resolve("icons");
            if (!Files.isDirectory(emiIcons)) {
                throw new IOException("Expected icon atlas at " + emiIcons);
            }
            if (Files.exists(iconsRoot)) {
                FileUtils.deleteDirectory(iconsRoot.toFile());
            }
            Files.createDirectories(iconsRoot.getParent());
            FileUtils.copyDirectory(emiIcons.toFile(), iconsRoot.toFile());
            FieldGuideIconCss.rewriteExportedCss(iconsRoot);
            LOGGER.info("Copied field-guide icon atlas to {}", iconsRoot.toAbsolutePath());
            return sprites;
        } finally {
            deleteRecursive(tempRoot);
        }
    }

    private static void deleteRecursive(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Could not clean temp dir {}", root, e);
        }
    }
}
