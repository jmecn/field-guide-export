package io.github.jmecn.fieldguideexport.export.module;

import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;
import io.github.jmecn.minecraftwebexport.export.module.ExportHints;
import io.github.jmecn.minecraftwebexport.export.module.ExportModule;
import io.github.jmecn.minecraftwebexport.export.module.ExportScope;
import io.github.jmecn.minecraftwebexport.export.module.ExportSeeds;

import java.util.List;
import java.util.Map;

/**
 * Supplies Patchouli book scan seeds to minecraft-web-export scoped EMI export.
 */
public final class FieldGuideExportModule implements ExportModule {

    public static final String MODULE_ID = "field_guide_export";

    private static final FieldGuideExportModule INSTANCE = new FieldGuideExportModule();

    private volatile BookScanResult scanResult;

    private FieldGuideExportModule() {}

    public static FieldGuideExportModule getInstance() {
        return INSTANCE;
    }

    public void setScanResult(BookScanResult scan) {
        this.scanResult = scan;
    }

    public void clearScanResult() {
        this.scanResult = null;
    }

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public ExportSeeds collectSeeds(ExportScope scope) {
        BookScanResult scan = scanResult;
        if (scan == null) {
            return ExportSeeds.empty();
        }
        ExportSeeds.Builder builder = ExportSeeds.builder();
        scan.getRecipes().forEach(builder::recipeId);
        scan.getItems().forEach(builder::itemId);
        scan.getTags().forEach(builder::tagId);
        scan.getEntities().forEach(builder::entityId);
        for (String texture : scan.getTextures()) {
            builder.textureId(texture);
            toAssetPath(texture).ifPresent(builder::resourcePath);
        }
        for (String model : scan.getModels()) {
            toAssetPath(model).ifPresent(builder::resourcePath);
        }
        for (String ref : scan.getBlockstateRefs()) {
            blockIdFromRef(ref).ifPresent(builder::blockId);
        }
        return builder.build();
    }

    @Override
    public ExportHints buildHints(ExportScope scope, ExportSeeds mergedSeeds) {
        BookScanResult scan = scanResult;
        if (scan == null) {
            return ExportHints.defaults();
        }
        return new ExportHints(
                Map.copyOf(scan.getItemReferenceCounts()),
                Map.of(),
                List.of("tfc", "firmalife", "minecraft"),
                false);
    }

    private static java.util.Optional<String> blockIdFromRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return java.util.Optional.empty();
        }
        int bracket = ref.indexOf('[');
        String id = bracket > 0 ? ref.substring(0, bracket) : ref;
        if (id.indexOf(':') > 0) {
            return java.util.Optional.of(id.trim());
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String> toAssetPath(String ref) {
        if (ref == null || ref.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = ref.trim();
        if (trimmed.startsWith("assets/") || trimmed.startsWith("data/")) {
            return java.util.Optional.of(trimmed);
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return java.util.Optional.empty();
        }
        String namespace = trimmed.substring(0, colon);
        String path = trimmed.substring(colon + 1);
        if (path.endsWith(".png") || path.endsWith(".json")) {
            String kind = path.endsWith(".json") && !path.contains("textures/") ? "models" : "textures";
            if (path.startsWith("textures/") || path.startsWith("models/")) {
                return java.util.Optional.of("assets/" + namespace + "/" + path);
            }
            return java.util.Optional.of("assets/" + namespace + "/" + kind + "/" + path);
        }
        return java.util.Optional.empty();
    }
}
