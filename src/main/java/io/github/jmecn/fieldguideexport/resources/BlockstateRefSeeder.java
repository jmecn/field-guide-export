package io.github.jmecn.fieldguideexport.resources;

import io.github.jmecn.fieldguideexport.scan.BlockStateResolver;
import io.github.jmecn.minecraftwebexport.emi.tag.TagExpander;
import io.github.jmecn.minecraftwebexport.model.tag.TagContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class BlockstateRefSeeder {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private BlockstateRefSeeder() {}

    static void seedResolved(
            ResourceManager rm,
            BlockStateResolver.Resolved resolved,
            Set<net.minecraft.resources.ResourceLocation> queue,
            MinecraftServer server) {
        if (resolved == null) {
            return;
        }
        if (resolved.block != null) {
            ModelDependencyCollector.seedBlockId(rm, resolved.block, queue);
            return;
        }
        if ("tag".equals(resolved.kind) && resolved.tag != null && !resolved.tag.isBlank()) {
            seedTagRef(rm, resolved.tag, queue, server);
            return;
        }
        if (resolved.ref != null) {
            seedRef(rm, resolved.ref, queue, server);
        }
    }

    static void seedRef(
            ResourceManager rm,
            String ref,
            Set<net.minecraft.resources.ResourceLocation> queue,
            MinecraftServer server) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        String trimmed = ref.trim();
        if ("AIR".equalsIgnoreCase(trimmed) || "minecraft:air".equalsIgnoreCase(trimmed)) {
            return;
        }
        if (trimmed.startsWith("#")) {
            seedTagRef(rm, trimmed.substring(1), queue, server);
            return;
        }
        ModelDependencyCollector.seedBlockId(rm, trimmed, queue);
    }

    static void seedBlockstateMap(
            ResourceManager rm,
            Map<String, Object> stateMap,
            Set<net.minecraft.resources.ResourceLocation> queue,
            MinecraftServer server) {
        if (stateMap == null) {
            return;
        }
        Object ref = stateMap.get("ref");
        if (ref instanceof String s) {
            seedRef(rm, s, queue, server);
        }
        Object override = stateMap.get("override");
        if (override instanceof String s) {
            seedRef(rm, s, queue, server);
        }
    }

    private static void seedTagRef(
            ResourceManager rm,
            String tagId,
            Set<net.minecraft.resources.ResourceLocation> queue,
            MinecraftServer server) {
        if (tagId == null || tagId.isBlank()) {
            return;
        }
        if (server == null) {
            LOGGER.warn("[resources] cannot expand block tag {} without integrated server", tagId);
            return;
        }
        TagContents contents = TagExpander.expandTagContents(server, tagId);
        String first = pickFirstBlockMember(contents.blocks());
        if (first == null) {
            LOGGER.debug("[resources] block tag {} has no members", tagId);
            return;
        }
        ModelDependencyCollector.seedBlockId(rm, first, queue);
    }

    static String pickFirstBlockMember(Set<String> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        return new TreeSet<>(blocks).iterator().next();
    }
}
