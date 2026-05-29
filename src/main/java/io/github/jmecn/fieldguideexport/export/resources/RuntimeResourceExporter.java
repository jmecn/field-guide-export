package io.github.jmecn.fieldguideexport.export.resources;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Writes the merged runtime {@link ResourceManager} view to {@code guide-export/assets/} and
 * {@code guide-export/data/} using Minecraft's on-disk layout.
 *
 * <p>Includes programmatic packs (e.g. GregTech {@code GTDynamicResourcePack}, KubeJS virtual packs)
 * because they register with the same {@code ResourceManager} the client uses in-game.</p>
 */
public final class RuntimeResourceExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private RuntimeResourceExporter() {}

    public record Result(
            int assetFiles,
            int dataFiles,
            long assetBytes,
            long dataBytes,
            int assetFailures,
            int dataFailures,
            boolean serverSkipped) {}

    public static Result export(Path outputDir, Minecraft client) {
        Path assetsRoot = outputDir.resolve("assets");
        Path dataRoot = outputDir.resolve("data");

        ResourceManager clientRm = client.getResourceManager();
        Set<String> excluded = ResourceExportFilter.excludedNamespaces();
        if (!excluded.isEmpty()) {
            LOGGER.info("[resources] excluded namespaces: {}", excluded);
        }

        ExportCounters assets = exportManager(clientRm, assetsRoot, ResourceExportRoots.CLIENT, new HashSet<>(), excluded);
        ExportCounters data = new ExportCounters();

        MinecraftServer server = client.getSingleplayerServer();
        boolean serverSkipped = false;
        if (server == null) {
            LOGGER.warn("[resources] no integrated server — skipping data/ export");
            serverSkipped = true;
        } else {
            data = exportManager(server.getResourceManager(), dataRoot, ResourceExportRoots.SERVER, new HashSet<>(), excluded);
        }

        LOGGER.info("[resources] wrote {} asset files ({} bytes, {} failures), {} data files ({} bytes, {} failures)",
                assets.files, assets.bytes, assets.failures,
                data.files, data.bytes, data.failures);

        return new Result(
                assets.files, data.files,
                assets.bytes, data.bytes,
                assets.failures, data.failures,
                serverSkipped);
    }

    private static final class ExportCounters {
        int files;
        long bytes;
        int failures;
    }

    private static ExportCounters exportManager(
            ResourceManager manager,
            Path typeRoot,
            String[] topLevelPaths,
            Set<ResourceLocation> written,
            Set<String> excludedNamespaces) {
        ExportCounters counters = new ExportCounters();
        Map<String, Integer> perRoot = new LinkedHashMap<>();
        int skippedExcluded = 0;

        for (String rootPath : topLevelPaths) {
            int rootCount = 0;
            Map<ResourceLocation, Resource> hits = manager.listResources(rootPath, loc -> true);
            for (Map.Entry<ResourceLocation, Resource> hit : hits.entrySet()) {
                ResourceLocation id = hit.getKey();
                if (excludedNamespaces.contains(id.getNamespace())) {
                    skippedExcluded++;
                    continue;
                }
                if (!written.add(id)) {
                    continue;
                }
                try {
                    long bytes = ResourceFileWriter.write(typeRoot, id, hit.getValue());
                    counters.files++;
                    counters.bytes += bytes;
                    rootCount++;
                } catch (IOException e) {
                    counters.failures++;
                    LOGGER.warn("[resources] failed to write {}: {}", id, e.getMessage());
                }
            }
            if (rootCount > 0) {
                perRoot.put(rootPath, rootCount);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            perRoot.forEach((root, count) -> LOGGER.debug("[resources]   {} → {} files", root, count));
        }
        if (skippedExcluded > 0) {
            LOGGER.info("[resources] skipped {} resources in excluded namespaces under {}", skippedExcluded, typeRoot);
        }

        return counters;
    }

}
