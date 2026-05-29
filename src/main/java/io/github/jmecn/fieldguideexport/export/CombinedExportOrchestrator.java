package io.github.jmecn.fieldguideexport.export;

import io.github.jmecn.minecraftwebexport.export.RuntimeExportEntrypoint;
import io.github.jmecn.minecraftwebexport.export.module.ExportCoordinator;
import io.github.jmecn.minecraftwebexport.export.module.ExportResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * In-game combined export: {@link ExportCoordinator} runs {@code guide-export/} via
 * {@link FieldGuideExportModule#beforeEmiExport} then scoped EMI under {@code emi/}.
 */
public final class CombinedExportOrchestrator {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private CombinedExportOrchestrator() {}

    public record CombinedExportResult(Component guideMessage, ExportResult emiResult) {}

    public static CombinedExportResult run(Path exportRoot, Path gameDirectory, Minecraft client)
            throws IOException {
        Objects.requireNonNull(exportRoot, "exportRoot");
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Objects.requireNonNull(client, "client");

        ensureScopedEmiDefaults();
        LOGGER.info("[export] combined → {} (guide-export/ + emi/)", exportRoot.toAbsolutePath());

        ExportResult emiResult = new ExportCoordinator().run(exportRoot, gameDirectory, client);
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
        if (System.getProperty("minecraftWebExport.exportMode") == null) {
            System.setProperty("minecraftWebExport.exportMode", "scoped");
        }
        if (System.getProperty(RuntimeExportEntrypoint.ENABLE_PROPERTY) == null) {
            System.setProperty(RuntimeExportEntrypoint.ENABLE_PROPERTY, "true");
        }
    }
}
