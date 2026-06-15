package io.github.jmecn.fieldguideexport.resources;

import io.github.jmecn.minecraftwebexport.emi.tag.TagExpander;
import io.github.jmecn.minecraftwebexport.model.tag.TagContents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jmecn.fieldguideexport.scan.BlockStateResolver;
import io.github.jmecn.fieldguideexport.scan.BookScanResult;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes {@code index/tag-members.json} for offline site / CLI tag expansion
 * ({@code BlockstateRefResolver}, {@code ExportModelLoader#loadBlockTag}).
 */
public final class HandbookTagIndexExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HandbookTagIndexExporter() {}

    public record Result(
            int tagsRequested,
            int itemTagEntries,
            int blockTagEntries,
            int fluidTagEntries,
            int totalRegistryRefs,
            long bytes) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipTagMembersExport");
    }

    public static Result export(
            Path guideDir,
            MinecraftServer server,
            BookScanResult scan,
            List<BlockStateResolver.Resolved> blockstates) throws IOException {
        if (guideDir == null || server == null || scan == null) {
            return new Result(0, 0, 0, 0, 0, 0);
        }

        Set<String> tagIds = collectTagIds(scan, blockstates);
        if (tagIds.isEmpty()) {
            return new Result(0, 0, 0, 0, 0, 0);
        }

        Map<String, List<String>> items = new LinkedHashMap<>();
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        Map<String, List<String>> fluids = new LinkedHashMap<>();
        int registryRefs = 0;

        for (String tagId : tagIds) {
            TagContents contents = TagExpander.expandTagContents(server, tagId);
            if (!contents.items().isEmpty()) {
                items.put(tagId, sortedList(contents.items()));
                registryRefs += contents.items().size();
            }
            if (!contents.blocks().isEmpty()) {
                blocks.put(tagId, sortedList(contents.blocks()));
                registryRefs += contents.blocks().size();
            }
            if (!contents.fluids().isEmpty()) {
                fluids.put(tagId, sortedList(contents.fluids()));
                registryRefs += contents.fluids().size();
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        if (!items.isEmpty()) {
            root.put("items", items);
        }
        if (!blocks.isEmpty()) {
            root.put("blocks", blocks);
        }
        if (!fluids.isEmpty()) {
            root.put("fluids", fluids);
        }

        Path indexFile = guideDir.resolve("index/tag-members.json");
        Files.createDirectories(indexFile.getParent());
        String json = GSON.toJson(root);
        Files.writeString(indexFile, json);

        LOGGER.info(
                "[tag-index] {} tags -> {} item, {} block, {} fluid entries ({} registry refs, {} bytes)",
                tagIds.size(),
                items.size(),
                blocks.size(),
                fluids.size(),
                registryRefs,
                json.length());

        return new Result(tagIds.size(), items.size(), blocks.size(), fluids.size(), registryRefs, json.length());
    }

    static Set<String> collectTagIds(BookScanResult scan, List<BlockStateResolver.Resolved> blockstates) {
        Set<String> tagIds = new TreeSet<>();
        tagIds.addAll(scan.getTags());
        for (String ref : scan.getBlockstateRefs()) {
            if (ref != null && ref.startsWith("#")) {
                String tagId = ref.substring(1).trim();
                if (!tagId.isEmpty()) {
                    tagIds.add(tagId);
                }
            }
        }
        if (blockstates != null) {
            for (BlockStateResolver.Resolved r : blockstates) {
                if ("tag".equals(r.kind) && r.tag != null && !r.tag.isBlank()) {
                    tagIds.add(r.tag);
                }
            }
        }
        return tagIds;
    }

    private static List<String> sortedList(Set<String> values) {
        return new ArrayList<>(new TreeSet<>(values));
    }
}
