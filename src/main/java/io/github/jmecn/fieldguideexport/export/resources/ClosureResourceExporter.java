package io.github.jmecn.fieldguideexport.export.resources;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.jmecn.fieldguideexport.export.patchouli.Book;
import io.github.jmecn.fieldguideexport.export.scan.BlockStateResolver;
import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;
import io.github.jmecn.fieldguideexport.export.scan.PatchouliMultiblockExporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes only runtime resources reachable from a {@link BookScanResult} (Patchouli book closure).
 */
@SuppressWarnings("removal")
public final class ClosureResourceExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private ClosureResourceExporter() {}

    public record Result(
            int assetFiles,
            int dataFiles,
            long assetBytes,
            long dataBytes,
            int failures,
            boolean serverSkipped,
            int seededLocations,
            int writtenLocations) {}

    public static Result export(
            Path outputDir,
            Minecraft client,
            Book book,
            BookScanResult scan,
            List<BlockStateResolver.Resolved> blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs,
            Set<String> extraBlockIds) throws IOException {
        Path assetsRoot = outputDir.resolve("assets");
        Path dataRoot = outputDir.resolve("data");

        ResourceManager clientRm = client.getResourceManager();
        Set<String> excluded = ResourceExportFilter.excludedNamespaces();

        Set<ResourceLocation> queue = new LinkedHashSet<>();
        seedPatchouliBooks(clientRm, book.getNamespace(), queue);
        for (String texture : scan.getTextures()) {
            ModelDependencyCollector.seedTextureRef(texture, queue);
        }
        for (String item : scan.getItems()) {
            ModelDependencyCollector.seedItem(clientRm, item, queue);
        }
        for (String model : scan.getModels()) {
            ModelDependencyCollector.seedModelId(clientRm, model, queue);
        }
        if (extraBlockIds != null) {
            for (String blockId : extraBlockIds) {
                ModelDependencyCollector.seedBlockId(clientRm, blockId, queue);
            }
        }
        if (blockstates != null) {
            for (BlockStateResolver.Resolved r : blockstates) {
                if (r.block != null) {
                    ModelDependencyCollector.seedBlockId(clientRm, r.block, queue);
                } else if (r.ref != null && !r.ref.startsWith("#")) {
                    ModelDependencyCollector.seedBlockId(clientRm, r.ref, queue);
                }
            }
        }
        if (multiblockDefs != null) {
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                for (Map<String, Object> state : mb.blockstates) {
                    seedBlockstateMap(clientRm, state, queue);
                }
                for (Map<String, Object> mapped : mb.mapping.values()) {
                    seedBlockstateMap(clientRm, mapped, queue);
                }
            }
        }

        int seeded = queue.size();
        ExportCounters assets = writeClosure(clientRm, assetsRoot, queue, excluded);

        ExportCounters data = new ExportCounters();
        boolean serverSkipped = false;
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn("[closure] no integrated server — skipping data/ closure export");
            serverSkipped = true;
        } else {
            ResourceManager serverRm = server.getResourceManager();
            Set<ResourceLocation> dataQueue = new LinkedHashSet<>();
            seedPatchouliBooks(serverRm, book.getNamespace(), dataQueue);
            for (BlockStateResolver.Resolved r : blockstates != null ? blockstates : List.<BlockStateResolver.Resolved>of()) {
                if (r.block != null) {
                    ModelDependencyCollector.seedBlockId(serverRm, r.block, dataQueue);
                } else if (r.ref != null && !r.ref.startsWith("#")) {
                    ModelDependencyCollector.seedBlockId(serverRm, r.ref, dataQueue);
                }
            }
            if (extraBlockIds != null) {
                for (String blockId : extraBlockIds) {
                    ModelDependencyCollector.seedBlockId(serverRm, blockId, dataQueue);
                }
            }
            if (multiblockDefs != null) {
                for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                    for (Map<String, Object> state : mb.blockstates) {
                        seedBlockstateMap(serverRm, state, dataQueue);
                    }
                }
            }
            data = writeClosure(serverRm, dataRoot, dataQueue, excluded);
            seeded += dataQueue.size();
        }

        LOGGER.info("[closure] seeded {} locations, wrote {} asset + {} data files ({} + {} bytes, {} failures)",
                seeded, assets.files, data.files, assets.bytes, data.bytes, assets.failures + data.failures);

        return new Result(
                assets.files, data.files,
                assets.bytes, data.bytes,
                assets.failures + data.failures,
                serverSkipped,
                seeded,
                assets.written + data.written);
    }

    private static void seedBlockstateMap(
            ResourceManager rm, Map<String, Object> stateMap, Set<ResourceLocation> queue) {
        if (stateMap == null) {
            return;
        }
        Object ref = stateMap.get("ref");
        if (ref instanceof String s) {
            ModelDependencyCollector.seedBlockId(rm, s, queue);
        }
        Object override = stateMap.get("override");
        if (override instanceof String s) {
            ModelDependencyCollector.seedBlockId(rm, s, queue);
        }
    }

    private static void seedPatchouliBooks(ResourceManager rm, String namespace, Set<ResourceLocation> queue) {
        Map<ResourceLocation, Resource> hits = rm.listResources("patchouli_books", loc ->
                namespace.equals(loc.getNamespace()));
        queue.addAll(hits.keySet());
    }

    private static ExportCounters writeClosure(
            ResourceManager rm,
            Path typeRoot,
            Set<ResourceLocation> seeds,
            Set<String> excludedNamespaces) throws IOException {
        ExportCounters counters = new ExportCounters();
        Set<ResourceLocation> written = new HashSet<>();
        Deque<ResourceLocation> pending = new ArrayDeque<>(seeds);

        while (!pending.isEmpty()) {
            ResourceLocation id = pending.removeFirst();
            if (!written.add(id)) {
                continue;
            }
            if (excludedNamespaces.contains(id.getNamespace())) {
                continue;
            }
            try {
                var opt = rm.getResource(id);
                if (opt.isPresent()) {
                    counters.bytes += ResourceFileWriter.write(typeRoot, id, opt.get());
                    counters.files++;
                    counters.written++;
                    enqueueParsedDependencies(rm, id, opt.get(), pending, written);
                } else if (id.getPath().endsWith(".json") && id.getPath().startsWith("models/")) {
                    var synthetic = SyntheticModelCatalog.content(id);
                    if (synthetic.isPresent()) {
                        counters.bytes += ResourceFileWriter.writeUtf8(typeRoot, id, synthetic.get());
                        counters.files++;
                        counters.written++;
                        if (SyntheticModelCatalog.isSyntheticOnly(rm, id)) {
                            LOGGER.debug("[closure] synthesized model {}", id);
                        }
                        var root = com.google.gson.JsonParser.parseString(synthetic.get()).getAsJsonObject();
                        ModelDependencyWalker.enqueueModelDependenciesFromJson(rm, id, root, pending, written);
                    }
                }
            } catch (IOException e) {
                counters.failures++;
                LOGGER.warn("[closure] failed to write {}: {}", id, e.getMessage());
            }
        }
        return counters;
    }

    private static void enqueueParsedDependencies(
            ResourceManager rm,
            ResourceLocation id,
            Resource resource,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        if (id.getPath().endsWith(".json") && id.getPath().startsWith("blockstates/")) {
            ModelDependencyWalker.enqueueBlockstateDependencies(rm, id, resource, pending, written);
        } else if (id.getPath().endsWith(".json") && id.getPath().startsWith("models/")) {
            ModelDependencyWalker.enqueueModelDependencies(rm, id, resource, pending, written);
        }
    }

    private static final class ExportCounters {
        int files;
        long bytes;
        int failures;
        int written;
    }
}
