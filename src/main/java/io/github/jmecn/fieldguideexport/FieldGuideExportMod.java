package io.github.jmecn.fieldguideexport;
import io.github.jmecn.minecraftwebexport.pipeline.ModuleRegistry;

import io.github.jmecn.fieldguideexport.module.FieldGuideExportModule;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FieldGuideExportMod.MOD_ID)
public final class FieldGuideExportMod {

    public static final String MOD_ID = "field_guide_export";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public FieldGuideExportMod() {
        ModuleRegistry.register(FieldGuideExportModule.getInstance());
        LOGGER.info("Field Guide Export initialized — /fieldguideexport run; CI: minecraftWebExport.export.enabled=true");
    }
}
