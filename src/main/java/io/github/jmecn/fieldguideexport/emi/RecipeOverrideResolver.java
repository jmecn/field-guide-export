package io.github.jmecn.fieldguideexport.emi;
import io.github.jmecn.minecraftwebexport.emi.recipe.Resolver;

import dev.emi.emi.api.recipe.EmiRecipe;
import io.github.jmecn.fieldguideexport.scan.BookScanResult;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RecipeOverrideResolver {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private RecipeOverrideResolver() {
    }

    public static void resolve(BookScanResult scan, Minecraft client) {
        if (scan == null || client == null) {
            return;
        }
        if (!Resolver.isEmiAvailable()) {
            LOGGER.warn("[recipe-mount] EMI unavailable — using handbook recipe ids for export");
            for (String handbookId : scan.getRecipes()) {
                scan.putRecipeMountId(handbookId, handbookId);
            }
            return;
        }
        int remapped = 0;
        for (String handbookId : scan.getRecipes()) {
            EmiRecipe recipe = Resolver.resolve(handbookId);
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
