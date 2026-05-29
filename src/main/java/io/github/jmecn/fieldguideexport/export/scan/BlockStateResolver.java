package io.github.jmecn.fieldguideexport.export.scan;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolves Patchouli {@code mapping} strings (a partial blockstate reference such as
 * {@code "minecraft:oak_log[axis=y]"}) against the live block registry to obtain a
 * <b>complete</b> property snapshot.
 *
 * <p>The CLI cannot do this on its own: it only sees the manual's raw string, which is
 * usually a partial state — the rest of the properties are inherited from
 * {@link Block#defaultBlockState()}. By running inside the client we have access to
 * {@link ForgeRegistries#BLOCKS} and the block's {@code StateDefinition}, so we can return:
 *
 * <ul>
 *   <li>{@link Resolved#ref} — the string as it appears in the Patchouli book.</li>
 *   <li>{@link Resolved#override} — canonical runtime blockstate (defaults applied), only when
 *       it differs from {@code ref}; omitted when the book already wrote the full state.</li>
 * </ul>
 *
 * <p>Special-cases:</p>
 * <ul>
 *   <li>{@code "#namespace:path"} → tag reference, not a blockstate. We record it without
 *       attempting to resolve a single state.</li>
 *   <li>Unparseable / unknown blocks / unknown property names → {@link Resolved#error} or
 *       {@link Resolved#unknownProperties}.</li>
 *   <li>{@link Resolved#invalidProperties} — property exists but the value string does not
 *       parse (wrong enum name, etc.). Values that match {@link Block#defaultBlockState()} are
 *       not flagged.</li>
 * </ul>
 */
public final class BlockStateResolver {

    private BlockStateResolver() {}

    /** Result for a single mapping string. Use {@link #isOk()} to gate downstream usage. */
    public static final class Resolved {
        public final String ref;
        public String kind;
        public String block;
        public String tag;
        public Map<String, String> requested = new LinkedHashMap<>();
        public Map<String, String> unknownProperties;
        public Map<String, String> invalidProperties;
        public String error;
        /**
         * Runtime blockstate after applying {@link Block#defaultBlockState()} and any properties
         * from {@code ref}. Omitted when identical to {@link #ref}.
         */
        public String override;

        Resolved(String ref) {
            this.ref = ref;
        }

        public boolean isOk() {
            return error == null;
        }

        public boolean hasOverride() {
            return override != null;
        }
    }

    public static Resolved resolve(String ref) {
        Resolved out = new Resolved(ref);
        if (ref == null || ref.isBlank()) {
            out.error = "blank";
            return out;
        }

        if (ref.startsWith("#")) {
            out.kind = "tag";
            String tagId = ref.substring(1).trim();
            if (tagId.isEmpty() || ResourceLocation.tryParse(tagId) == null) {
                out.error = "invalid_tag";
            } else {
                out.tag = tagId;
            }
            return out;
        }

        if (isAirAlias(ref)) {
            out.kind = "air";
            out.block = "minecraft:air";
            finishBlockState(out, Blocks.AIR.defaultBlockState());
            return out;
        }

        out.kind = "block";
        Parsed parsed = parse(ref);
        if (parsed == null) {
            out.error = "unparseable";
            return out;
        }
        out.block = parsed.blockId.toString();
        out.requested = parsed.overrides;

        if (!ForgeRegistries.BLOCKS.containsKey(parsed.blockId)) {
            out.error = "unknown_block";
            return out;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(parsed.blockId);
        if (block == null) {
            out.error = "unknown_block";
            return out;
        }
        BlockState state = block.defaultBlockState();

        for (Map.Entry<String, String> override : parsed.overrides.entrySet()) {
            Property<?> prop = block.getStateDefinition().getProperty(override.getKey());
            if (prop == null) {
                if (out.unknownProperties == null) out.unknownProperties = new LinkedHashMap<>();
                out.unknownProperties.put(override.getKey(), override.getValue());
                continue;
            }
            Optional<BlockState> next = setPropertyValue(state, prop, override.getValue());
            if (next.isEmpty()) {
                if (out.invalidProperties == null) out.invalidProperties = new LinkedHashMap<>();
                out.invalidProperties.put(override.getKey(), override.getValue());
            } else {
                state = next.get();
            }
        }

        finishBlockState(out, state);
        return out;
    }

    private static boolean isAirAlias(String ref) {
        return "AIR".equalsIgnoreCase(ref.trim());
    }

    private static void finishBlockState(Resolved out, BlockState state) {
        String runtime = formatBlockStateRef(state);
        if (!runtime.equals(out.ref.trim())) {
            out.override = runtime;
        }
    }

    /**
     * Builds a Patchouli-style blockstate string from a live {@link BlockState} (as returned
     * by {@code IStateMatcher#getDisplayedState} during multiblock export).
     */
    public static String formatBlockStateRef(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) {
            return "minecraft:air";
        }
        Map<String, String> props = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            props.put(entry.getKey().getName(), valueName(entry.getKey(), entry.getValue()));
        }
        if (props.isEmpty()) {
            return blockId.toString();
        }
        StringBuilder sb = new StringBuilder(blockId.toString()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    /** Snapshot of an in-world {@link BlockState} without re-parsing a book string. */
    public static Resolved resolveFromBlockState(BlockState state) {
        Resolved out = new Resolved(formatBlockStateRef(state));
        out.kind = "block";
        if (state.isAir()) {
            out.kind = "air";
            return out;
        }
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) {
            out.error = "unknown_block";
            return out;
        }
        out.block = blockId.toString();
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> Optional<BlockState> setPropertyValue(
            BlockState state, Property<?> prop, String raw) {
        Property<T> typed = (Property<T>) prop;
        return typed.getValue(raw).map(v -> state.setValue(typed, v));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String valueName(Property<?> prop, Comparable<?> value) {
        Property<T> typed = (Property<T>) prop;
        return typed.getName((T) value);
    }

    private static Parsed parse(String ref) {
        int lb = ref.indexOf('[');
        String idPart;
        String propsPart;
        if (lb < 0) {
            idPart = ref.trim();
            propsPart = "";
        } else {
            int rb = ref.lastIndexOf(']');
            if (rb < lb) return null;
            idPart = ref.substring(0, lb).trim();
            propsPart = ref.substring(lb + 1, rb).trim();
        }
        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) return null;

        Map<String, String> overrides = new LinkedHashMap<>();
        if (!propsPart.isEmpty()) {
            for (String pair : propsPart.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) return null;
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                if (key.isEmpty() || value.isEmpty()) return null;
                overrides.put(key, value);
            }
        }
        return new Parsed(id, overrides);
    }

    private record Parsed(ResourceLocation blockId, Map<String, String> overrides) {}
}
