package io.github.jmecn.fieldguideexport.export.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fallback JSON for model parents that exist only as client builtins (no pack file), e.g.
 * {@code minecraft:builtin/generated} or {@code forge:item/default}.
 */
final class SyntheticModelCatalog {

    private static final Map<ResourceLocation, String> MODELS = new LinkedHashMap<>();

    static {
        register("minecraft", "models/builtin/generated.json", """
                {
                  "textures": {
                    "particle": "#layer0"
                  },
                  "gui_light": "front"
                }
                """);
        register("minecraft", "models/builtin/entity.json", """
                {}
                """);
        register("minecraft", "models/block/block.json", """
                {
                  "gui_light": "side",
                  "display": {
                    "gui": { "rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625] },
                    "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25] },
                    "fixed": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5] },
                    "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375] },
                    "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4] },
                    "firstperson_lefthand": { "rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4] }
                  }
                }
                """);
        register("minecraft", "models/block/cube.json", """
                {
                  "parent": "block/block",
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": { "texture": "#down", "cullface": "down" },
                        "up": { "texture": "#up", "cullface": "up" },
                        "north": { "texture": "#north", "cullface": "north" },
                        "south": { "texture": "#south", "cullface": "south" },
                        "west": { "texture": "#west", "cullface": "west" },
                        "east": { "texture": "#east", "cullface": "east" }
                      }
                    }
                  ]
                }
                """);
        register("minecraft", "models/item/generated.json", """
                {
                  "parent": "builtin/generated",
                  "gui_light": "front",
                  "display": {
                    "ground": { "rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5] },
                    "head": { "rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1] },
                    "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55] },
                    "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] },
                    "fixed": { "rotation": [0, 180, 0], "scale": [1, 1, 1] }
                  }
                }
                """);
        register("forge", "models/item/default.json", """
                {
                  "gui_light": "front",
                  "display": {
                    "ground": { "rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5] },
                    "head": { "rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1] },
                    "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55] },
                    "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] },
                    "fixed": { "rotation": [0, 180, 0], "scale": [1, 1, 1] }
                  }
                }
                """);
        register("forge", "models/item/default-tool.json", """
                {
                  "parent": "forge:item/default",
                  "display": {
                    "thirdperson_righthand": { "rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85] },
                    "thirdperson_lefthand": { "rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85] },
                    "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] },
                    "firstperson_lefthand": { "rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] }
                  }
                }
                """);
        register("forge", "models/item/bucket_drip.json", """
                {
                  "parent": "forge:item/default",
                  "textures": {
                    "base": "item/bucket",
                    "fluid": "forge:item/mask/bucket_fluid_drip"
                  }
                }
                """);
        register("forge", "models/block/default.json", """
                {
                  "parent": "minecraft:block/block"
                }
                """);
    }

    private SyntheticModelCatalog() {}

    private static void register(String namespace, String path, String json) {
        MODELS.put(new ResourceLocation(namespace, path), json.strip());
    }

    static boolean isAvailable(ResourceManager rm, ResourceLocation modelFile) {
        return rm.getResource(modelFile).isPresent() || MODELS.containsKey(modelFile);
    }

    static Optional<String> content(ResourceLocation modelFile) {
        return Optional.ofNullable(MODELS.get(modelFile));
    }

    static boolean isSyntheticOnly(ResourceManager rm, ResourceLocation modelFile) {
        return !rm.getResource(modelFile).isPresent() && MODELS.containsKey(modelFile);
    }
}
