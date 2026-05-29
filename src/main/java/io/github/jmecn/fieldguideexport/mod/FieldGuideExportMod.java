package io.github.jmecn.fieldguideexport.mod;

import io.github.jmecn.fieldguideexport.export.ci.FieldGuideExportCiDriver;
import io.github.jmecn.fieldguideexport.export.ci.FieldGuideExportCiProperties;
import io.github.jmecn.fieldguideexport.export.module.FieldGuideExportModule;
import io.github.jmecn.minecraftwebexport.export.module.ExportModuleRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Forge entrypoint for Field Guide {@code guide-export/} + scoped EMI integration. */
@Mod(FieldGuideExportMod.MOD_ID)
public final class FieldGuideExportMod {

    public static final String MOD_ID = "field_guide_export";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public FieldGuideExportMod() {
        ExportModuleRegistry.register(FieldGuideExportModule.getInstance());
        LOGGER.info("Field Guide Export initialized — /fieldguideexport run or fieldguide.runExportAndExit");
        if (FMLEnvironment.dist == Dist.CLIENT && FieldGuideExportCiProperties.runExportAndExit()) {
            new FieldGuideExportCiDriver(FMLPaths.GAMEDIR.get()).register();
        }
    }
}
