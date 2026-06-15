package io.github.jmecn.fieldguideexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jmecn.fieldguideexport.emi.RecipeOverrideResolver;
import io.github.jmecn.fieldguideexport.patchouli.Book;
import io.github.jmecn.fieldguideexport.patchouli.BookCategory;
import io.github.jmecn.fieldguideexport.patchouli.BookEntry;
import io.github.jmecn.fieldguideexport.patchouli.PatchouliBookLoader;
import io.github.jmecn.fieldguideexport.entity.EntityRenderMaps;
import io.github.jmecn.fieldguideexport.resources.ReferencedResourceExporter;
import io.github.jmecn.fieldguideexport.resources.EntityPreviewExporter;
import io.github.jmecn.fieldguideexport.resources.IconOgPreviewExporter;
import io.github.jmecn.fieldguideexport.resources.ExportDirectoryStats;
import io.github.jmecn.fieldguideexport.resources.HandbookTagIndexExporter;
import io.github.jmecn.fieldguideexport.scan.BlockStateExportMaps;
import io.github.jmecn.fieldguideexport.scan.BlockStateResolver;
import io.github.jmecn.fieldguideexport.module.FieldGuideExportModule;
import io.github.jmecn.fieldguideexport.scan.BookScanResult;
import io.github.jmecn.fieldguideexport.scan.BookScanner;
import io.github.jmecn.fieldguideexport.scan.PatchouliMultiblockExporter;
import io.github.jmecn.fieldguideexport.support.FieldGuidePageSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
 * Guide-export pipeline: Patchouli book scan, {@code manifest.json} / {@code meta.json},
 * and book-referenced {@code assets/} + {@code data/} resources.
 */
public final class GuideExportOrchestrator {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuideExportOrchestrator() {}

    /**
     * Updates {@code refs.recipeMountIds} after {@link RecipeOverrideResolver}
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

    /**
     * Patches {@code meta.json} after {@link io.github.jmecn.fieldguideexport.resources.EntityPreviewExporter}
     * runs in {@code exportExtras} (meta is written earlier in {@link #run}).
     */
    public static void patchEntityRenders(Path guideDir, EntityPreviewExporter.Result result) {
        if (guideDir == null || result == null || result.requested() == 0) {
            return;
        }
        Path metaFile = guideDir.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            LOGGER.warn("[entity-export] meta.json missing — cannot patch entityRenders");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = GSON.fromJson(Files.readString(metaFile), Map.class);
            if (meta == null) {
                return;
            }
            meta.put("schemaVersion", "1.4");

            Map<String, Object> renders = new LinkedHashMap<>();
            for (EntityPreviewExporter.RenderedEntity row : result.renders()) {
                String entityId = row.request().entity();
                if (renders.containsKey(entityId)) {
                    LOGGER.warn("[entity-export] duplicate entityRenders key {}, overwriting", entityId);
                }
                renders.put(entityId, EntityRenderMaps.toRenderEntry(row));
            }
            if (!renders.isEmpty()) {
                meta.put("entityRenders", renders);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) meta.computeIfAbsent("stats", k -> new LinkedHashMap<>());
            stats.put("entityRenderRequested", result.requested());
            stats.put("entityRenderSucceeded", result.succeeded());
            stats.put("entityRenderFailed", result.failed());
            stats.put("entityRenderBytes", result.bytes());

            if (!result.failures().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> missing = (Map<String, Object>) meta.computeIfAbsent("missing", k -> new LinkedHashMap<>());
                List<Map<String, Object>> missingEntities = new ArrayList<>();
                for (EntityPreviewExporter.FailedEntity failure : result.failures()) {
                    missingEntities.add(EntityRenderMaps.toMissingMap(failure.request(), failure.error()));
                }
                missing.put("entities", missingEntities);
                @SuppressWarnings("unchecked")
                Map<String, Object> missingStats = (Map<String, Object>) missing.computeIfAbsent("stats", k -> new LinkedHashMap<>());
                missingStats.put("entities", missingEntities.size());
                int total = 0;
                Object blockstates = missingStats.get("blockstates");
                Object multiblocks = missingStats.get("multiblocks");
                if (blockstates instanceof Number n) {
                    total += n.intValue();
                }
                if (multiblocks instanceof Number n) {
                    total += n.intValue();
                }
                total += missingEntities.size();
                missingStats.put("total", total);
                stats.put("missingEntities", missingEntities.size());
            }

            Files.writeString(metaFile, GSON.toJson(meta));
            LOGGER.info(
                    "[entity-export] patched meta.json: {} renders, {} missing",
                    result.succeeded(),
                    result.failed());
        } catch (IOException e) {
            LOGGER.error("[entity-export] failed to patch {}", metaFile.toAbsolutePath(), e);
        }
    }

    /** Patches {@code meta.json} with per-entry OG preview paths after {@link IconOgPreviewExporter}. */
    public static void patchEntryOgImages(Path guideDir, IconOgPreviewExporter.Result result) {
        if (guideDir == null || result == null || result.paths().isEmpty()) {
            return;
        }
        Path metaFile = guideDir.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            LOGGER.warn("[og-icons] meta.json missing — cannot patch entryOgImages");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = GSON.fromJson(Files.readString(metaFile), Map.class);
            if (meta == null) {
                return;
            }
            meta.put("entryOgImages", new LinkedHashMap<>(result.paths()));
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) meta.computeIfAbsent("stats", k -> new LinkedHashMap<>());
            stats.put("entryOgImageRequested", result.requested());
            stats.put("entryOgImageSucceeded", result.succeeded());
            stats.put("entryOgImageFailed", result.failed());
            Files.writeString(metaFile, GSON.toJson(meta));
            LOGGER.info("[og-icons] patched meta.json: {} entry OG images", result.succeeded());
        } catch (IOException e) {
            LOGGER.error("[og-icons] failed to patch {}", metaFile.toAbsolutePath(), e);
        }
    }

    public static Component run(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("status", "export");
        manifest.put("exportedAt", Instant.now().toString());
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

        ReferencedResourceExporter.Result resources = null;
        LOGGER.info("[export] writing book-referenced assets/data resources");

        try {
            if (scanResult != null) {
                resources = ReferencedResourceExporter.export(
                        outputDir,
                        client,
                        book,
                        scanResult,
                        blockstates != null ? blockstates.entries : List.of(),
                        multiblockDefs,
                        Set.of());
            } else {
                LOGGER.warn("[export] skipping resource export — book scan unavailable");
            }
        } catch (Throwable t) {
            LOGGER.error("resource export failed", t);
            manifest.put("resourceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (resources != null) {
            Map<String, Object> resourceStats = resourceStats(
                    resources.assetFiles(),
                    resources.dataFiles(),
                    resources.assetBytes(),
                    resources.dataBytes(),
                    resources.failures(),
                    0,
                    resources.serverSkipped());
            resourceStats.put("referencedSeeded", resources.seededLocations());
            resourceStats.put("referencedWritten", resources.writtenLocations());
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

        if (scanResult != null) {
            writeMeta(outputDir, scanResult, blockstates, multiblockDefs, resources);
            exportTagIndex(outputDir, client, scanResult, blockstates, manifest);
        }

        Component result = writeManifest(outputDir, manifest);
        return result;
    }

    private static void exportTagIndex(
            Path outputDir,
            Minecraft client,
            BookScanResult scanResult,
            BlockStateResolution blockstates,
            Map<String, Object> manifest) {
        if (!HandbookTagIndexExporter.isEnabled()) {
            return;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn("[tag-index] skipped: integrated server unavailable");
            return;
        }
        try {
            HandbookTagIndexExporter.Result tags = HandbookTagIndexExporter.export(
                    outputDir,
                    server,
                    scanResult,
                    blockstates != null ? blockstates.entries : List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) manifest.computeIfAbsent("stats", k -> new LinkedHashMap<>());
            stats.put("tagIndexTags", tags.tagsRequested());
            stats.put("tagIndexBlockTags", tags.blockTagEntries());
            stats.put("tagIndexItemTags", tags.itemTagEntries());
            stats.put("tagIndexFluidTags", tags.fluidTagEntries());
            stats.put("tagIndexRegistryRefs", tags.totalRegistryRefs());
            stats.put("tagIndexBytes", tags.bytes());
        } catch (IOException e) {
            LOGGER.error("[tag-index] export failed", e);
            manifest.put("tagIndexError", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
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
            ReferencedResourceExporter.Result resources) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", "1.3");
        meta.put("scannedAt", Instant.now().toString());

        Map<String, Object> stats = new LinkedHashMap<>(scan.toStatsMap());
        applyBlockstateStats(stats, blockstates);
        applyMultiblockStats(stats, multiblockDefs);
        if (resources != null) {
            stats.put("assetFiles", resources.assetFiles());
            stats.put("dataFiles", resources.dataFiles());
            stats.put("assetBytes", resources.assetBytes());
            stats.put("dataBytes", resources.dataBytes());
            stats.put("referencedSeeded", resources.seededLocations());
            stats.put("referencedWritten", resources.writtenLocations());
            stats.put("dataExportSkipped", resources.serverSkipped());
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
                if (!mb.pattern.isEmpty()) {
                    m.put("pattern", mb.pattern);
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
