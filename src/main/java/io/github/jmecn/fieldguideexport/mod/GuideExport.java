package io.github.jmecn.fieldguideexport.mod;

import io.github.jmecn.fieldguideexport.export.CombinedExportOrchestrator;
import io.github.jmecn.fieldguideexport.export.FieldGuideExportPaths;
import io.github.jmecn.fieldguideexport.export.FieldGuideExportProperties;
import io.github.jmecn.fieldguideexport.export.GuideExportOrchestrator;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/** {@code /fieldguideexport run} and programmatic export entry. */
public final class GuideExport {

    private GuideExport() {}

    public static int run(CommandSourceStack source) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                source.sendFailure(Component.literal("[field-guide-export] client unavailable"));
                return 0;
            }
            Path gameDir = client.gameDirectory.toPath();
            Path guideDir = FieldGuideExportPaths.guideDirectory(gameDir);
            Component message;
            if (FieldGuideExportProperties.exportEmi()) {
                Path exportRoot = FieldGuideExportPaths.resolveExportRoot(gameDir);
                message = CombinedExportOrchestrator.run(exportRoot, gameDir, client).guideMessage();
            } else {
                message = GuideExportOrchestrator.run(guideDir);
            }
            source.sendSystemMessage(message);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[field-guide-export] Export failed: " + e.getMessage()));
            return 0;
        }
    }
}
