package io.github.jmecn.fieldguideexport;
import io.github.jmecn.minecraftwebexport.pipeline.Pipeline;
import io.github.jmecn.minecraftwebexport.model.pipeline.ExportResult;

import io.github.jmecn.fieldguideexport.module.FieldGuideExportModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class FieldGuideExportPipeline {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private FieldGuideExportPipeline() {}

    public record CombinedExportResult(Component guideMessage, ExportResult emiResult) {}

    public static CombinedExportResult run(Path exportRoot, Path gameDirectory, Minecraft client)
            throws IOException {
        Objects.requireNonNull(exportRoot, "exportRoot");
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Objects.requireNonNull(client, "client");

        ensureScopedEmiDefaults();
        LOGGER.info("[export] combined → {} (guide-export/ + emi/)", exportRoot.toAbsolutePath());

        ExportResult emiResult = Pipeline.run(exportRoot, gameDirectory, client);
        Component guideMessage = Component.literal(
                "[field-guide-export] guide + EMI → " + exportRoot.toAbsolutePath());
        LOGGER.info(
                "[export] finished: recipes={}/{}, items={}",
                emiResult.recipesWritten(),
                emiResult.recipesRequested(),
                emiResult.itemIndexCount());
        return new CombinedExportResult(guideMessage, emiResult);
    }

    private static void ensureScopedEmiDefaults() {
        System.setProperty("minecraftWebExport.exportMode", "scoped");
    }
}
