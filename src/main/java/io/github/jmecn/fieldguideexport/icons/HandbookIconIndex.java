package io.github.jmecn.fieldguideexport.icons;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Reads {@code assets/icons/index.json} produced by field-guide icon export. */
public final class HandbookIconIndex {

    private static final Gson GSON = new Gson();
    private static final TypeToken<Map<String, Object>> MAP_TYPE = new TypeToken<>() {};

    private final int cellSize;
    private final Map<String, SpritePlacement> items;

    private HandbookIconIndex(int cellSize, Map<String, SpritePlacement> items) {
        this.cellSize = cellSize;
        this.items = items;
    }

    public static HandbookIconIndex load(Path iconsRoot) throws IOException {
        Path indexFile = iconsRoot.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return new HandbookIconIndex(32, Map.of());
        }
        Map<String, Object> root = GSON.fromJson(Files.readString(indexFile), MAP_TYPE.getType());
        if (root == null) {
            return new HandbookIconIndex(32, Map.of());
        }
        int cellSize = intValue(root.get("cellSize"), 32);
        @SuppressWarnings("unchecked")
        Map<String, Object> itemsMap = (Map<String, Object>) root.get("items");
        if (itemsMap == null) {
            return new HandbookIconIndex(cellSize, Map.of());
        }
        Map<String, SpritePlacement> parsed = new TreeMap<>();
        for (Map.Entry<String, Object> entry : itemsMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> row) {
                parsed.put(entry.getKey(), SpritePlacement.fromMap(row, cellSize));
            }
        }
        return new HandbookIconIndex(cellSize, Collections.unmodifiableMap(parsed));
    }

    public Optional<SpritePlacement> findItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(itemId));
    }

    public Path atlasFile(Path iconsRoot, SpritePlacement placement) {
        return iconsRoot.resolve("atlas-%03d.png".formatted(placement.page()));
    }

    public record SpritePlacement(int cellSize, int page, int x, int y) {
        static SpritePlacement fromMap(Map<?, ?> row, int defaultCellSize) {
            return new SpritePlacement(
                    defaultCellSize,
                    intValue(row.get("page"), 0),
                    intValue(row.get("x"), 0),
                    intValue(row.get("y"), 0));
        }
    }

    private static int intValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }
}
