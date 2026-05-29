package io.github.jmecn.fieldguideexport.mod;

import io.github.jmecn.fieldguideexport.export.module.FieldGuideExportModule;
import io.github.jmecn.minecraftwebexport.export.module.ExportModuleRegistry;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Forge entrypoint: registers {@link FieldGuideExportModule}; CI uses minecraft-web-export {@code runExportAndExit}. */
@Mod(FieldGuideExportMod.MOD_ID)
public final class FieldGuideExportMod {

    public static final String MOD_ID = "field_guide_export";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public FieldGuideExportMod() {
        ExportModuleRegistry.register(FieldGuideExportModule.getInstance());
        LOGGER.info("Field Guide Export initialized — /fieldguideexport run; CI: minecraftWebExport.runExportAndExit");
    }
}
