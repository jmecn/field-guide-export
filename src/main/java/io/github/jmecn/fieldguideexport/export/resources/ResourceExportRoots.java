package io.github.jmecn.fieldguideexport.export.resources;

/**
 * Top-level resource paths passed to {@link net.minecraft.server.packs.resources.ResourceManager#listResources}.
 * Each entry collects all namespaces under that folder (merged runtime view, including GT/KubeJS dynamic packs).
 */
final class ResourceExportRoots {

    /** {@link net.minecraft.server.packs.PackType#CLIENT_RESOURCES} */
    static final String[] CLIENT = {
            "atlases",
            "blockstates",
            "equipment",
            "font",
            // lang → guide-export/lang/<lang>.json (LangMergerExporter)
            "models",
            // particles — not used by field guide site
            "patchouli_books",
            "post_effect",
            "shaders",
            // sounds — not used by field guide site
            "textures",
            "texts",
            "waypoint_styles",
            "dynamic_assets",
            "dynamic_textures",
            "emi",
            "ponderjs",
            "curios",
            "ftbquests",
            "ftblibrary",
            "kubejs",
            "guideme",
    };

    /** {@link net.minecraft.server.packs.PackType#SERVER_DATA} */
    static final String[] SERVER = {
            "advancements",
            "chat_type",
            "damage_type",
            "dimension",
            "dimension_type",
            "forge",
            "functions",
            "loot_tables",
            "patchouli_books",
            // recipes → data/<ns>/recipes.json (RecipeBundleExporter)
            // structures / worldgen — not used by field guide site
            // tags → data/<ns>/tags/{items,blocks,fluids}.json (TagBundleExporter)
            "trim_pattern",
            "kubejs",
            "tfc",
            "gtceu",
            "firmalife",
            "create",
    };

    private ResourceExportRoots() {}
}
