package io.github.jmecn.fieldguideexport.export;

/**
 * Controls how much of the merged runtime resource tree is written to {@code guide-export/}.
 *
 * <ul>
 *   <li>{@link #CLOSURE} — only assets/data reachable from the scanned Patchouli book
 *       ({@code meta.json} refs), plus filtered recipes, lang for touched namespaces, and item
 *       icons for referenced items.</li>
 *   <li>{@link #FULL} — mirror all configured {@code assets/} and {@code data/} roots (minus
 *       excluded namespaces and paths like {@code worldgen} / {@code structures}).</li>
 * </ul>
 *
 * <p>System properties (first match wins for mode):</p>
 * <ul>
 *   <li>{@code -Dfieldguide.fullExport=true} → {@link #FULL}</li>
 *   <li>{@code -Dfieldguide.exportMode=full|closure} (default {@code full})</li>
 * </ul>
 */
public enum FieldGuideExportMode {

    CLOSURE,
    FULL;

    public static FieldGuideExportMode current() {
        if (Boolean.getBoolean("fieldguide.fullExport")) {
            return FULL;
        }
        String raw = System.getProperty("fieldguide.exportMode", "full").trim().toLowerCase();
        return switch (raw) {
            case "full" -> FULL;
            case "closure" -> CLOSURE;
            default -> throw new IllegalArgumentException(
                    "fieldguide.exportMode must be 'closure' or 'full', got: " + raw);
        };
    }

    public boolean isClosure() {
        return this == CLOSURE;
    }
}
