package io.github.jmecn.fieldguideexport.export.scan;

import com.google.gson.Gson;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.common.multiblock.AbstractMultiblock;
import vazkii.patchouli.common.multiblock.MultiblockRegistry;
import vazkii.patchouli.common.multiblock.SerializedMultiblock;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves Patchouli {@code multiblock_id} references against the live game — something the
 * CLI cannot do when mods register structures in code (TFC bloomery, firmalife greenhouse, …).
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@link MultiblockRegistry#MULTIBLOCKS} (populated after mods' {@code registerMultiBlocks()}).</li>
 *   <li>Optional JSON under {@code assets/<ns>/patchouli_books/<book>/multiblocks/<path>.json}.</li>
 * </ol>
 *
 * <p>For each structure, unique pattern-mapping characters are turned into blockstate snapshots
 * via {@link IMultiblock#simulate} and {@link BlockStateResolver#resolveFromBlockState}.</p>
 */
public final class PatchouliMultiblockExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new Gson();

    private PatchouliMultiblockExporter() {}

    public static final class ExportedMultiblock {
        public final String id;
        public String source;
        public String error;
        /** Pattern char → blockstate export ({@code ref}, optional {@code override}). */
        public final Map<String, Map<String, Object>> mapping = new LinkedHashMap<>();
        /** De-duplicated blockstates used by this structure (for CLI variant lookup). */
        public final List<Map<String, Object>> blockstates = new ArrayList<>();

        ExportedMultiblock(String id) {
            this.id = id;
        }

        public boolean isOk() {
            return error == null && !mapping.isEmpty();
        }
    }

    public static List<ExportedMultiblock> exportAll(
            Collection<String> multiblockIds,
            Level level,
            ResourceManager resourceManager,
            String bookNamespace,
            String bookId) {
        List<ExportedMultiblock> out = new ArrayList<>();
        for (String rawId : multiblockIds) {
            out.add(exportOne(rawId, level, resourceManager, bookNamespace, bookId));
        }
        return out;
    }

    public static ExportedMultiblock exportOne(
            String rawId,
            Level level,
            ResourceManager resourceManager,
            String bookNamespace,
            String bookId) {
        if (rawId == null || rawId.isBlank()) {
            ExportedMultiblock blank = new ExportedMultiblock("");
            blank.error = "blank_id";
            return blank;
        }

        ResourceLocation loc = ResourceLocation.tryParse(rawId.contains(":") ? rawId : bookNamespace + ":" + rawId);
        if (loc == null) {
            ExportedMultiblock bad = new ExportedMultiblock(rawId);
            bad.error = "invalid_id";
            return bad;
        }
        ExportedMultiblock result = new ExportedMultiblock(loc.toString());
        if (level == null) {
            result.error = "no_level";
            return result;
        }

        IMultiblock multiblock = MultiblockRegistry.MULTIBLOCKS.get(loc);
        if (multiblock != null) {
            result.source = "patchouli_registry";
        } else {
            multiblock = tryLoadJsonMultiblock(loc, resourceManager, bookId);
            if (multiblock != null) {
                result.source = "patchouli_json";
            }
        }

        if (multiblock == null) {
            result.error = "not_in_registry";
            LOGGER.warn("[multiblock] {} not found in Patchouli registry or book JSON", loc);
            return result;
        }

        if (multiblock instanceof AbstractMultiblock abstractMb) {
            abstractMb.setWorld(level);
        }

        try {
            fillFromSimulate(result, multiblock, level);
        } catch (RuntimeException e) {
            result.error = "simulate_failed";
            LOGGER.warn("[multiblock] simulate failed for {}", loc, e);
        }
        return result;
    }

    private static void fillFromSimulate(ExportedMultiblock result, IMultiblock multiblock, Level level) {
        Pair<BlockPos, Collection<IMultiblock.SimulateResult>> sim =
                multiblock.simulate(level, BlockPos.ZERO, Rotation.NONE, true);

        Map<String, BlockState> charToState = new LinkedHashMap<>();
        for (IMultiblock.SimulateResult cell : sim.getSecond()) {
            BlockState displayed = cell.getStateMatcher().getDisplayedState(0);
            Character ch = cell.getCharacter();
            if (ch != null && displayed.isAir() && (ch == ' ' || ch == '0' || ch == '_')) {
                continue;
            }
            if (displayed.isAir()) {
                continue;
            }
            String key;
            if (ch != null) {
                key = String.valueOf(ch);
            } else {
                BlockPos p = cell.getWorldPosition();
                key = "@" + p.getX() + "," + p.getY() + "," + p.getZ();
            }
            charToState.putIfAbsent(key, displayed);
        }

        if (charToState.isEmpty()) {
            result.error = "empty_structure";
            return;
        }

        Set<String> seenRefs = new LinkedHashSet<>();
        for (Map.Entry<String, BlockState> entry : charToState.entrySet()) {
            BlockStateResolver.Resolved resolved = BlockStateResolver.resolveFromBlockState(entry.getValue());
            result.mapping.put(entry.getKey(), BlockStateExportMaps.toMap(resolved));
            if (resolved.ref != null && seenRefs.add(resolved.ref)) {
                result.blockstates.add(BlockStateExportMaps.toMap(resolved));
            }
        }
    }

    @SuppressWarnings("removal")
    private static IMultiblock tryLoadJsonMultiblock(
            ResourceLocation id,
            ResourceManager resourceManager,
            String bookId) {
        if (resourceManager == null) {
            return null;
        }
        String[] paths = {
                "patchouli_books/" + bookId + "/multiblocks/" + id.getPath() + ".json",
                "multiblocks/" + id.getPath() + ".json",
        };
        for (String path : paths) {
            ResourceLocation key = new ResourceLocation(id.getNamespace(), path);
            Optional<Resource> resource = resourceManager.getResource(key);
            if (resource.isEmpty()) {
                continue;
            }
            try (BufferedReader reader = resource.get().openAsReader()) {
                SerializedMultiblock data = GSON.fromJson(reader, SerializedMultiblock.class);
                if (data != null) {
                    return data.toMultiblock();
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("[multiblock] failed to read {}", key, e);
            }
        }
        return null;
    }
}
