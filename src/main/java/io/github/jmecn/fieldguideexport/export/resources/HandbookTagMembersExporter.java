package io.github.jmecn.fieldguideexport.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jmecn.fieldguideexport.export.scan.BlockStateResolver;
import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;
import io.github.jmecn.minecraftwebexport.export.emi.TagClosureExpander;
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
public final class HandbookTagMembersExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HandbookTagMembersExporter() {}

    public record Result(
            int tagsRequested,
            int itemTagEntries,
            int blockTagEntries,
            int fluidTagEntries,
            int totalMemberRefs,
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
        int memberRefs = 0;

        for (String tagId : tagIds) {
            TagClosureExpander.TagMembers members = TagClosureExpander.expandTagMembers(server, tagId);
            if (!members.items().isEmpty()) {
                items.put(tagId, sortedList(members.items()));
                memberRefs += members.items().size();
            }
            if (!members.blocks().isEmpty()) {
                blocks.put(tagId, sortedList(members.blocks()));
                memberRefs += members.blocks().size();
            }
            if (!members.fluids().isEmpty()) {
                fluids.put(tagId, sortedList(members.fluids()));
                memberRefs += members.fluids().size();
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
                "[tag-members] {} tags -> {} item, {} block, {} fluid entries ({} member refs, {} bytes)",
                tagIds.size(),
                items.size(),
                blocks.size(),
                fluids.size(),
                memberRefs,
                json.length());

        return new Result(tagIds.size(), items.size(), blocks.size(), fluids.size(), memberRefs, json.length());
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
