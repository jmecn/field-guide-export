package io.github.jmecn.fieldguideexport.module;
import io.github.jmecn.minecraftwebexport.model.pipeline.Hints;
import io.github.jmecn.minecraftwebexport.pipeline.Module;
import io.github.jmecn.minecraftwebexport.model.pipeline.Scope;
import io.github.jmecn.minecraftwebexport.model.pipeline.Seeds;
import io.github.jmecn.minecraftwebexport.model.pipeline.SeedsBuilder;

import io.github.jmecn.fieldguideexport.FieldGuideExportPaths;
import io.github.jmecn.fieldguideexport.FieldGuideExportLanguages;
import io.github.jmecn.fieldguideexport.GuideExportOrchestrator;
import io.github.jmecn.fieldguideexport.emi.RecipeOverrideResolver;
import io.github.jmecn.fieldguideexport.resources.EntityPreviewExporter;
import io.github.jmecn.fieldguideexport.resources.FieldGuideIconExporter;
import io.github.jmecn.fieldguideexport.resources.IconOgPreviewExporter;
import io.github.jmecn.fieldguideexport.resources.HandbookLangExporter;
import io.github.jmecn.fieldguideexport.scan.BookScanResult;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Supplies Patchouli book scan seeds to minecraft-web-export scoped EMI export.
 */
public final class FieldGuideExportModule implements Module {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

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
    public void beforeEmiExport(Scope scope, Minecraft client) throws IOException {
        Path guideDir = FieldGuideExportPaths.guideDirectoryFromExportRoot(scope.outputRoot());
        clearScanResult();
        GuideExportOrchestrator.run(guideDir);
        RecipeOverrideResolver.resolve(scanResult, client);
        GuideExportOrchestrator.patchRecipeMountIds(guideDir, scanResult);
    }

    @Override
    public Seeds collectSeeds(Scope scope) {
        BookScanResult scan = scanResult;
        if (scan == null) {
            return Seeds.empty();
        }
        SeedsBuilder builder = Seeds.builder();
        for (String handbookRecipeId : scan.getRecipes()) {
            builder.recipeId(scan.getRecipeMountId(handbookRecipeId));
        }
        scan.getItems().forEach(builder::itemId);
        scan.getTags().forEach(builder::tagId);
        return builder.build();
    }

    @Override
    public void exportExtras(Scope scope) throws IOException {
        Path guideDir = FieldGuideExportPaths.guideDirectoryFromExportRoot(scope.outputRoot());
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            LOGGER.warn("exportExtras skipped: Minecraft client unavailable");
            return;
        }

        if (HandbookLangExporter.isEnabled()) {
            HandbookLangExporter.Result lang = HandbookLangExporter.exportHandbookLang(guideDir, client, null, null);
            LOGGER.info("[exportExtras] lang: {} files, {} bytes", lang.languagesWritten(), lang.totalBytes());
        }

        BookScanResult scan = scanResult;
        if (scan != null && !scan.getItems().isEmpty()) {
            Path iconsRoot = guideDir.resolve("assets/icons");
            int sprites = FieldGuideIconExporter.export(
                    iconsRoot,
                    client,
                    scan.getItems(),
                    Map.copyOf(scan.getItemReferenceCounts()));
            LOGGER.info(
                    "[exportExtras] handbook icons: {} sprites at {}",
                    sprites,
                    iconsRoot.toAbsolutePath());

            IconOgPreviewExporter.Result og = IconOgPreviewExporter.export(
                    iconsRoot, guideDir, scan.getEntryIcons());
            GuideExportOrchestrator.patchEntryOgImages(guideDir, og);
        }

        if (scan != null && EntityPreviewExporter.isEnabled() && !scan.getEntityRenderRequests().isEmpty()) {
            EntityPreviewExporter.Result entities = EntityPreviewExporter.export(
                    guideDir, client, scan.getEntityRenderRequests());
            GuideExportOrchestrator.patchEntityRenders(guideDir, entities);
            LOGGER.info(
                    "[exportExtras] entity previews: {}/{} ok, {} bytes",
                    entities.succeeded(),
                    entities.requested(),
                    entities.bytes());
        }
    }

    @Override
    public Hints buildHints(Scope scope) {
        return new Hints(FieldGuideExportLanguages.asList());
    }
}
