package io.github.jmecn.fieldguideexport.scan;

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
import vazkii.patchouli.common.multiblock.DenseMultiblock;
import vazkii.patchouli.common.multiblock.MultiblockRegistry;
import vazkii.patchouli.common.multiblock.SerializedMultiblock;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class PatchouliMultiblockExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new Gson();

    private PatchouliMultiblockExporter() {}

    public static final class ExportedMultiblock {
        public final String id;
        public String source;
        public String error;
        
        public final Map<String, Map<String, Object>> mapping = new LinkedHashMap<>();
        
        public final List<Map<String, Object>> blockstates = new ArrayList<>();
        
        public final List<List<String>> pattern = new ArrayList<>();

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
        SerializedMultiblock serialized = null;
        if (multiblock != null) {
            result.source = "patchouli_registry";
        } else {
            serialized = tryLoadSerializedMultiblock(loc, resourceManager, bookId);
            if (serialized != null) {
                multiblock = serialized.toMultiblock();
                result.source = "patchouli_json";
                copyPatternFromSerialized(serialized, result);
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
            if (result.pattern.isEmpty()) {
                copyPatternFromDense(multiblock, result);
            }
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

        if (result.pattern.isEmpty()) {
            buildPatternFromSimulate(sim, result);
        }
    }

    private static void copyPatternFromSerialized(SerializedMultiblock serialized, ExportedMultiblock result) {
        try {
            Field field = SerializedMultiblock.class.getDeclaredField("densePattern");
            field.setAccessible(true);
            String[][] dense = (String[][]) field.get(serialized);
            if (dense != null) {
                copyDensePattern(dense, result);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[multiblock] could not read SerializedMultiblock.pattern for {}", result.id, e);
        }
    }

    private static void copyPatternFromDense(IMultiblock multiblock, ExportedMultiblock result) {
        if (!(multiblock instanceof DenseMultiblock)) {
            return;
        }
        try {
            Field field = DenseMultiblock.class.getDeclaredField("pattern");
            field.setAccessible(true);
            String[][] dense = (String[][]) field.get(multiblock);
            if (dense != null) {
                copyDensePattern(dense, result);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[multiblock] could not read DenseMultiblock.pattern for {}", result.id, e);
        }
    }

    private static void copyDensePattern(String[][] dense, ExportedMultiblock result) {
        result.pattern.clear();
        for (String[] layer : dense) {
            result.pattern.add(Arrays.asList(layer));
        }
    }

    private static void buildPatternFromSimulate(
            Pair<BlockPos, Collection<IMultiblock.SimulateResult>> sim,
            ExportedMultiblock result) {
        BlockPos origin = sim.getFirst();
        TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, Character>>> grid = new TreeMap<>();
        for (IMultiblock.SimulateResult cell : sim.getSecond()) {
            Character ch = cell.getCharacter();
            if (ch == null) {
                continue;
            }
            BlockPos rel = cell.getWorldPosition().subtract(origin);
            grid.computeIfAbsent(rel.getY(), y -> new TreeMap<>())
                    .computeIfAbsent(rel.getZ(), z -> new TreeMap<>())
                    .put(rel.getX(), ch);
        }
        if (grid.isEmpty()) {
            return;
        }
        int minY = grid.firstKey();
        int maxY = grid.lastKey();
        int minZ = grid.values().stream().mapToInt(TreeMap::firstKey).min().orElse(0);
        int maxZ = grid.values().stream().mapToInt(TreeMap::lastKey).max().orElse(0);
        int minX = grid.values().stream()
                .flatMap(zMap -> zMap.values().stream())
                .mapToInt(TreeMap::firstKey)
                .min()
                .orElse(0);
        int maxX = grid.values().stream()
                .flatMap(zMap -> zMap.values().stream())
                .mapToInt(TreeMap::lastKey)
                .max()
                .orElse(0);

        result.pattern.clear();
        for (int y = minY; y <= maxY; y++) {
            List<String> layer = new ArrayList<>();
            TreeMap<Integer, TreeMap<Integer, Character>> zMap =
                    grid.getOrDefault(y, new TreeMap<>());
            for (int z = minZ; z <= maxZ; z++) {
                StringBuilder row = new StringBuilder();
                TreeMap<Integer, Character> xMap = zMap.getOrDefault(z, new TreeMap<>());
                for (int x = minX; x <= maxX; x++) {
                    Character c = xMap.get(x);
                    row.append(c != null ? c : ' ');
                }
                layer.add(row.toString());
            }
            result.pattern.add(layer);
        }
    }

    @SuppressWarnings("removal")
    private static SerializedMultiblock tryLoadSerializedMultiblock(
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
                return GSON.fromJson(reader, SerializedMultiblock.class);
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("[multiblock] failed to read {}", key, e);
            }
        }
        return null;
    }
}
