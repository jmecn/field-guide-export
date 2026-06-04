package io.github.jmecn.fieldguideexport.export.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;
import io.github.jmecn.minecraftwebexport.export.emi.EmiRecipeResolver;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps handbook/Patchouli recipe ids to EMI recipe ids used for scoped export and site EMI mounts.
 */
public final class HandbookRecipeMountResolver {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private HandbookRecipeMountResolver() {
    }

    public static void resolve(BookScanResult scan, Minecraft client) {
        if (scan == null || client == null) {
            return;
        }
        if (!EmiRecipeResolver.isEmiAvailable()) {
            LOGGER.warn("[recipe-mount] EMI unavailable — using handbook recipe ids for export");
            for (String handbookId : scan.getRecipes()) {
                scan.putRecipeMountId(handbookId, handbookId);
            }
            return;
        }
        int remapped = 0;
        for (String handbookId : scan.getRecipes()) {
            EmiRecipe recipe = EmiRecipeResolver.resolve(handbookId);
            if (recipe == null || recipe.getId() == null) {
                scan.putRecipeMountId(handbookId, handbookId);
                continue;
            }
            String emiId = recipe.getId().toString();
            scan.putRecipeMountId(handbookId, emiId);
            if (!handbookId.equals(emiId)) {
                remapped++;
            }
        }
        if (remapped > 0) {
            LOGGER.info("[recipe-mount] {} handbook recipe ids mapped to EMI ids for export", remapped);
        }
    }
}
