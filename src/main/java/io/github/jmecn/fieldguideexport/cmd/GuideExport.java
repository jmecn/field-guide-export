package io.github.jmecn.fieldguideexport.cmd;

import io.github.jmecn.fieldguideexport.FieldGuideExportPipeline;
import io.github.jmecn.fieldguideexport.FieldGuideExportPaths;
import io.github.jmecn.fieldguideexport.FieldGuideExportProperties;
import io.github.jmecn.fieldguideexport.GuideExportOrchestrator;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class GuideExport {

    private GuideExport() {}

    public static int run(CommandSourceStack source) {
        try {
            Minecraft client = Minecraft.getInstance();
            Path gameDir = client.gameDirectory.toPath();
            Path guideDir = FieldGuideExportPaths.guideDirectory(gameDir);
            Component message;
            if (FieldGuideExportProperties.exportEmi()) {
                Path exportRoot = FieldGuideExportPaths.resolveExportRoot(gameDir);
                message = FieldGuideExportPipeline.run(exportRoot, gameDir, client).guideMessage();
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
