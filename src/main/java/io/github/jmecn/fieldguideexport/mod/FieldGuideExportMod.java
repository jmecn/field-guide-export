package io.github.jmecn.fieldguideexport.mod;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge entrypoint for the Field Guide export mod.
 *
 * <p>Phase 0 scaffold — guide-export orchestration and Patchouli scanning land in Phase 1.</p>
 */
@Mod(FieldGuideExportMod.MOD_ID)
public final class FieldGuideExportMod {

    public static final String MOD_ID = "field_guide_export";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public FieldGuideExportMod() {
        LOGGER.info("Field Guide Export mod initialized (phase 0 scaffold)");
    }
}
