package io.github.jmecn.fieldguideexport.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jmecn.fieldguideexport.export.patchouli.Book;
import io.github.jmecn.fieldguideexport.export.patchouli.BookCategory;
import io.github.jmecn.fieldguideexport.export.patchouli.BookEntry;
import io.github.jmecn.fieldguideexport.export.patchouli.PatchouliBookLoader;
import io.github.jmecn.fieldguideexport.export.resources.ClosureResourceExporter;
import io.github.jmecn.fieldguideexport.export.resources.ExportDirectoryStats;
import io.github.jmecn.fieldguideexport.export.resources.RuntimeResourceExporter;
import io.github.jmecn.fieldguideexport.export.scan.BlockStateExportMaps;
import io.github.jmecn.fieldguideexport.export.scan.BlockStateResolver;
import io.github.jmecn.fieldguideexport.export.module.FieldGuideExportModule;
import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;
import io.github.jmecn.fieldguideexport.export.scan.BookScanner;
import io.github.jmecn.fieldguideexport.export.scan.PatchouliMultiblockExporter;
import io.github.jmecn.fieldguideexport.support.FieldGuidePageSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 guide-export pipeline: Patchouli book scan, {@code manifest.json} / {@code meta.json},
 * and full or closure {@code assets/} + {@code data/}.
 */
public final class GuideExportOrchestrator {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuideExportOrchestrator() {}

    /**
     * Updates {@code refs.recipeMountIds} after {@link io.github.jmecn.fieldguideexport.export.emi.HandbookRecipeMountResolver}
     * runs (meta is written earlier in {@link #run} before EMI is available).
     */
    public static void patchRecipeMountIds(Path guideDir, BookScanResult scan) {
        if (guideDir == null || scan == null) {
            return;
        }
        Path metaFile = guideDir.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            LOGGER.warn("[recipe-mount] meta.json missing — cannot patch recipeMountIds");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = GSON.fromJson(Files.readString(metaFile), Map.class);
            if (meta == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> refs = (Map<String, Object>) meta.computeIfAbsent("refs", k -> new LinkedHashMap<>());
            Map<String, String> mounts = scan.getRecipeMountIds();
            refs.put("recipeMountIds", mounts);
            Files.writeString(metaFile, GSON.toJson(meta));
            long remapped = mounts.entrySet().stream()
                    .filter(e -> !e.getKey().equals(e.getValue()))
                    .count();
            LOGGER.info(
                    "[recipe-mount] patched meta.json: {} mount ids ({} handbook→EMI)",
                    mounts.size(),
                    remapped);
        } catch (IOException e) {
            LOGGER.error("[recipe-mount] failed to patch {}", metaFile.toAbsolutePath(), e);
        }
    }

    public static Component run(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        FieldGuideExportMode exportMode = FieldGuideExportMode.current();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("status", "export");
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("exportMode", exportMode.name().toLowerCase());
        manifest.put("exporter", "field-guide-export");

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            manifest.put("error", "Minecraft.getInstance() returned null");
            return writeManifest(outputDir, manifest);
        }

        BookScanResult scanResult = null;
        Book book = null;
        BlockStateResolution blockstates = null;
        List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs = null;

        try {
            book = PatchouliBookLoader.forTfcFieldGuide(client).load();
            manifest.put("book", summarizeBook(book));

            scanResult = BookScanner.scan(book);
            FieldGuideExportModule.getInstance().setScanResult(scanResult);
            blockstates = resolveBlockstates(scanResult);
            multiblockDefs = PatchouliMultiblockExporter.exportAll(
                    scanResult.getMultiblocks(),
                    client.level,
                    client.getResourceManager(),
                    book.getNamespace(),
                    book.getBookId());

            Map<String, Object> stats = new LinkedHashMap<>(scanResult.toStatsMap());
            applyBlockstateStats(stats, blockstates);
            applyMultiblockStats(stats, multiblockDefs);
            applyMissingStats(stats, collectMissing(blockstates, multiblockDefs));
            manifest.put("stats", stats);
            logScanSummary(book, scanResult, blockstates, multiblockDefs);
        } catch (Throwable t) {
            LOGGER.error("patchouli book load failed", t);
            manifest.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        RuntimeResourceExporter.Result fullResources = null;
        ClosureResourceExporter.Result closureResources = null;

        if (exportMode.isClosure()) {
            LOGGER.info("[export] mode=closure (book-referenced assets/data)");
        } else {
            LOGGER.info("[export] mode=full (merged assets/ + data/ trees)");
        }

        try {
            if (!exportMode.isClosure()) {
                fullResources = RuntimeResourceExporter.export(outputDir, client);
            }
        } catch (Throwable t) {
            LOGGER.error("asset/data export failed", t);
            manifest.put("resourceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            if (exportMode.isClosure() && scanResult != null && book != null) {
                closureResources = ClosureResourceExporter.export(
                        outputDir,
                        client,
                        book,
                        scanResult,
                        blockstates != null ? blockstates.entries : List.of(),
                        multiblockDefs,
                        Set.of());
            }
        } catch (Throwable t) {
            LOGGER.error("closure resource export failed", t);
            manifest.put("resourceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (fullResources != null) {
            manifest.put("resources", resourceStats(fullResources.assetFiles(), fullResources.dataFiles(),
                    fullResources.assetBytes(), fullResources.dataBytes(),
                    fullResources.assetFailures(), fullResources.dataFailures(),
                    fullResources.serverSkipped()));
        }
        if (closureResources != null) {
            Map<String, Object> resourceStats = resourceStats(
                    closureResources.assetFiles(),
                    closureResources.dataFiles(),
                    closureResources.assetBytes(),
                    closureResources.dataBytes(),
                    closureResources.failures(),
                    0,
                    closureResources.serverSkipped());
            resourceStats.put("closureSeeded", closureResources.seededLocations());
            resourceStats.put("closureWritten", closureResources.writtenLocations());
            manifest.put("resources", resourceStats);
        }

        try {
            ExportDirectoryStats.Summary size = ExportDirectoryStats.summarize(outputDir);
            manifest.put("exportSize", ExportDirectoryStats.toMap(size));
            LOGGER.info("[export] total on disk: {} files, {} bytes under {}",
                    size.fileCount(), size.totalBytes(), outputDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("[export] could not summarize export directory size", e);
        }

        Component result = writeManifest(outputDir, manifest);
        if (scanResult != null) {
            writeMeta(outputDir, scanResult, blockstates, multiblockDefs, fullResources, closureResources);
        }
        return result;
    }

    private static Map<String, Object> resourceStats(
            int assetFiles,
            int dataFiles,
            long assetBytes,
            long dataBytes,
            int assetFailures,
            int dataFailures,
            boolean serverSkipped) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assetFiles", assetFiles);
        stats.put("dataFiles", dataFiles);
        stats.put("assetBytes", assetBytes);
        stats.put("dataBytes", dataBytes);
        stats.put("assetFailures", assetFailures);
        stats.put("dataFailures", dataFailures);
        stats.put("serverSkipped", serverSkipped);
        return stats;
    }

    private static BlockStateResolution resolveBlockstates(BookScanResult scan) {
        BlockStateResolution res = new BlockStateResolution();
        for (String ref : scan.getBlockstateRefs()) {
            BlockStateResolver.Resolved r = BlockStateResolver.resolve(ref);
            res.entries.add(r);
            if ("tag".equals(r.kind)) {
                if (r.error == null) {
                    res.tagCount++;
                } else {
                    res.failedCount++;
                }
            } else if (r.isOk()) {
                res.resolvedCount++;
                if (r.hasOverride()) {
                    res.enrichedCount++;
                }
            } else {
                res.failedCount++;
            }
        }
        return res;
    }

    private static void applyBlockstateStats(Map<String, Object> stats, BlockStateResolution blockstates) {
        if (blockstates == null) {
            return;
        }
        stats.put("blockstateResolved", blockstates.resolvedCount);
        stats.put("blockstateEnriched", blockstates.enrichedCount);
        stats.put("blockstateTags", blockstates.tagCount);
        stats.put("blockstateFailed", blockstates.failedCount);
    }

    private static void applyMultiblockStats(
            Map<String, Object> stats,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs) {
        if (multiblockDefs == null) {
            return;
        }
        int ok = 0;
        int fail = 0;
        for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
            if (mb.isOk()) {
                ok++;
            } else {
                fail++;
            }
        }
        stats.put("multiblockResolved", ok);
        stats.put("multiblockFailed", fail);
    }

    private static void logScanSummary(
            Book book,
            BookScanResult scan,
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs) {
        LOGGER.info(
                "[scan] {}:{} ({}): {} pages | {} recipes | {} items | {} tags",
                book.getNamespace(),
                book.getBookId(),
                book.getLanguage(),
                scan.getPageCount(),
                scan.getRecipes().size(),
                scan.getItems().size(),
                scan.getTags().size());
        if (blockstates != null) {
            LOGGER.info(
                    "[scan] blockstates: {} resolved, {} failed",
                    blockstates.resolvedCount,
                    blockstates.failedCount);
        }
        if (multiblockDefs != null) {
            int ok = 0;
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) {
                    ok++;
                }
            }
            LOGGER.info("[scan] multiblocks: {} / {} resolved", ok, multiblockDefs.size());
        }
    }

    private static Map<String, Object> summarizeBook(Book book) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("namespace", book.getNamespace());
        summary.put("bookId", book.getBookId());
        summary.put("language", book.getLanguage());
        summary.put("name", book.getName());
        summary.put("categoryCount", book.getCategories().size());
        summary.put("entryCount", book.getEntries().size());

        List<Map<String, Object>> categories = new ArrayList<>();
        for (BookCategory cat : book.getCategories()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", cat.getId());
            c.put("name", cat.getName());
            c.put("entryCount", cat.getEntries().size());
            List<Map<String, Object>> entries = new ArrayList<>();
            for (BookEntry e : cat.getEntries()) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("id", e.getId());
                em.put("name", e.getName());
                em.put("pageCount", e.getPages().size());
                entries.add(em);
            }
            c.put("entries", entries);
            categories.add(c);
        }
        summary.put("categories", categories);
        return summary;
    }

    private static Map<String, Object> collectMissing(
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs) {
        List<Map<String, Object>> missingBlockstates = new ArrayList<>();
        List<Map<String, Object>> missingMultiblocks = new ArrayList<>();

        if (blockstates != null) {
            for (BlockStateResolver.Resolved r : blockstates.entries) {
                if (r.isOk()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ref", r.ref);
                m.put("error", r.error != null ? r.error : "unknown");
                if (r.kind != null) {
                    m.put("kind", r.kind);
                }
                missingBlockstates.add(m);
            }
        }
        if (multiblockDefs != null) {
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mb.id);
                m.put("error", mb.error != null ? mb.error : "unknown");
                if (mb.source != null) {
                    m.put("source", mb.source);
                }
                missingMultiblocks.add(m);
            }
        }

        if (missingBlockstates.isEmpty() && missingMultiblocks.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> missingStats = new LinkedHashMap<>();
        missingStats.put("blockstates", missingBlockstates.size());
        missingStats.put("multiblocks", missingMultiblocks.size());
        missingStats.put("total", missingBlockstates.size() + missingMultiblocks.size());
        out.put("stats", missingStats);
        if (!missingBlockstates.isEmpty()) {
            out.put("blockstates", missingBlockstates);
        }
        if (!missingMultiblocks.isEmpty()) {
            out.put("multiblocks", missingMultiblocks);
        }
        return out;
    }

    private static void applyMissingStats(Map<String, Object> stats, Map<String, Object> missing) {
        if (missing.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> missingStats = (Map<String, Object>) missing.get("stats");
        stats.put("missingBlockstates", missingStats.get("blockstates"));
        stats.put("missingMultiblocks", missingStats.get("multiblocks"));
        stats.put("missingTotal", missingStats.get("total"));
    }

    private static Component writeManifest(Path outputDir, Map<String, Object> manifest) throws IOException {
        Path manifestFile = outputDir.resolve("manifest.json");
        Files.writeString(manifestFile, GSON.toJson(manifest));
        return Component.literal("[field-guide-export] wrote " + manifestFile.toAbsolutePath());
    }

    private static void writeMeta(
            Path outputDir,
            BookScanResult scan,
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs,
            RuntimeResourceExporter.Result fullResources,
            ClosureResourceExporter.Result closureResources) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", "1.3");
        meta.put("scannedAt", Instant.now().toString());

        Map<String, Object> stats = new LinkedHashMap<>(scan.toStatsMap());
        applyBlockstateStats(stats, blockstates);
        applyMultiblockStats(stats, multiblockDefs);
        if (fullResources != null) {
            stats.put("assetFiles", fullResources.assetFiles());
            stats.put("dataFiles", fullResources.dataFiles());
            stats.put("assetBytes", fullResources.assetBytes());
            stats.put("dataBytes", fullResources.dataBytes());
            stats.put("assetExportFailures", fullResources.assetFailures());
            stats.put("dataExportFailures", fullResources.dataFailures());
            stats.put("dataExportSkipped", fullResources.serverSkipped());
        }
        if (closureResources != null) {
            stats.put("assetFiles", closureResources.assetFiles());
            stats.put("dataFiles", closureResources.dataFiles());
            stats.put("assetBytes", closureResources.assetBytes());
            stats.put("dataBytes", closureResources.dataBytes());
            stats.put("closureSeeded", closureResources.seededLocations());
            stats.put("closureWritten", closureResources.writtenLocations());
            stats.put("dataExportSkipped", closureResources.serverSkipped());
        }
        meta.put("stats", stats);
        meta.put("pageTypeSupport", buildPageTypeSupport(scan));

        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("recipes", scan.getRecipes());
        refs.put("recipeMountIds", scan.getRecipeMountIds());
        refs.put("recipesByPageType", scan.getRecipesByPageType());
        refs.put("items", scan.getItems());
        refs.put("tags", scan.getTags());
        refs.put("textures", scan.getTextures());
        refs.put("entities", scan.getEntities());
        refs.put("multiblocks", scan.getMultiblocks());
        refs.put("models", scan.getModels());
        refs.put("blockstateRefs", scan.getBlockstateRefs());
        meta.put("refs", refs);

        if (blockstates != null) {
            List<Map<String, Object>> bsList = new ArrayList<>();
            for (BlockStateResolver.Resolved r : blockstates.entries) {
                bsList.add(BlockStateExportMaps.toMap(r));
            }
            meta.put("blockstates", bsList);
        }

        if (multiblockDefs != null && !multiblockDefs.isEmpty()) {
            List<Map<String, Object>> mbList = new ArrayList<>();
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mb.id);
                if (mb.source != null) {
                    m.put("source", mb.source);
                }
                if (mb.error != null) {
                    m.put("error", mb.error);
                }
                if (!mb.mapping.isEmpty()) {
                    m.put("mapping", mb.mapping);
                }
                if (!mb.blockstates.isEmpty()) {
                    m.put("blockstates", mb.blockstates);
                }
                mbList.add(m);
            }
            meta.put("multiblockDefs", mbList);
        }

        Map<String, Object> missing = collectMissing(blockstates, multiblockDefs);
        if (!missing.isEmpty()) {
            meta.put("missing", missing);
            applyMissingStats(stats, missing);
        }

        Path metaFile = outputDir.resolve("meta.json");
        try {
            Files.writeString(metaFile, GSON.toJson(meta));
            LOGGER.info("wrote {}", metaFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("failed to write {}", metaFile.toAbsolutePath(), e);
        }
    }

    private static Map<String, Object> buildPageTypeSupport(BookScanResult scan) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("catalog", FieldGuidePageSupport.exportCatalog());

        Map<String, Integer> seen = scan.getPagesByType();
        List<Map<String, Object>> inBook = new ArrayList<>();
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            if (!e.getKey().startsWith("tfc:")) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", e.getKey());
            row.put("count", e.getValue());
            row.put("tier", FieldGuidePageSupport.tierOf(e.getKey()).name().toLowerCase());
            inBook.add(row);
        }
        out.put("tfcPagesInBook", inBook);
        return out;
    }

    private static final class BlockStateResolution {
        final List<BlockStateResolver.Resolved> entries = new ArrayList<>();
        int resolvedCount;
        int enrichedCount;
        int tagCount;
        int failedCount;
    }
}
