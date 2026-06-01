package io.github.jmecn.fieldguideexport.export.resources;

import io.github.jmecn.minecraftwebexport.export.emi.ItemIconRendererExporter;
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

/**
 * Writes handbook item icons to {@code guide-export/assets/icons/} using minecraft-web-export's
 * icon atlas builder (which normally targets {@code emi/icons}).
 */
public final class HandbookIconExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private HandbookIconExporter() {}

    public static ItemIconRendererExporter.Result export(
            Path iconsRoot,
            Minecraft client,
            Set<String> onlyItemIds,
            Map<String, Integer> usageWeights) throws IOException {
        Files.createDirectories(iconsRoot);
        Path tempRoot = Files.createTempDirectory("field-guide-export-icons");
        try {
            ItemIconRendererExporter.Result result = ItemIconRendererExporter.export(
                    tempRoot,
                    client,
                    onlyItemIds,
                    null,
                    usageWeights,
                    Map.of());
            Path emiIcons = tempRoot.resolve("emi").resolve("icons");
            if (!Files.isDirectory(emiIcons)) {
                throw new IOException("Expected icon atlas at " + emiIcons);
            }
            if (Files.exists(iconsRoot)) {
                FileUtils.deleteDirectory(iconsRoot.toFile());
            }
            Files.createDirectories(iconsRoot.getParent());
            FileUtils.copyDirectory(emiIcons.toFile(), iconsRoot.toFile());
            LOGGER.info("Copied handbook icon atlas to {}", iconsRoot.toAbsolutePath());
            return result;
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
