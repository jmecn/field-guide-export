package io.github.jmecn.fieldguideexport.export.scan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Aggregated counts and de-duplicated reference sets produced by a {@link BookScanner} pass
 * over a Patchouli book. Intended to be serialized to {@code meta.json} so consumers (CLI
 * htmlrender, dev diagnostics) get a stable inventory of everything the book reaches into:
 * recipes, items, textures, entities, multiblocks, models.
 *
 * <p>This is not the schema for "what each page renders" — it's an inventory of {@code
 * domain:id} strings the book *references*, plus per–page-type and per-recipe-type counts.
 * Useful both as a sanity check (e.g. spot pages whose recipe id silently went missing
 * after a modpack update) and as the seed set for any future closure walker that mirrors
 * {@link io.github.jmecn.fieldguideexport.export.resources.RuntimeResourceExporter} writes the full
 * merged {@code assets/} + {@code data/} tree.</p>
 */
public final class BookScanResult {

    /** Total pages visited (including pages with no parseable raw JSON). */
    private int pageCount;

    /** Pages broken down by {@code page.type}; key = patchouli/tfc type id, value = count. */
    private final Map<String, Integer> pagesByType = new TreeMap<>();

    /** Pages whose raw JSON could not be inspected (Gson-only path, no JsonObject attached). */
    private int pagesWithoutRaw;

    /** Recipe ids referenced anywhere in the book (page.recipe / recipes / recipe2). */
    private final Set<String> recipes = new TreeSet<>();

    /**
     * Handbook recipe id → EMI recipe id used for scoped export and site {@code data-recipe-id}.
     * Populated by {@link io.github.jmecn.fieldguideexport.export.emi.HandbookRecipeMountResolver}.
     */
    private final Map<String, String> recipeMountIds = new TreeMap<>();

    /**
     * Recipe ids grouped by the page type that referenced them. Lets the consumer answer
     * "which recipe ids does a {@code patchouli:crafting} page actually point at"
     * without scanning twice.
     */
    private final Map<String, Set<String>> recipesByPageType = new TreeMap<>();

    /**
     * Item ids referenced anywhere in the book (entry/category/spotlight icons; page items
     * fields). NBT / count suffixes are stripped so the set is {@code namespace:path}
     * level.
     */
    private final Set<String> items = new TreeSet<>();

    /** Per-item reference counts from book pages (icons, spotlight items, etc.). */
    private final Map<String, Integer> itemReferenceCounts = new TreeMap<>();

    /** Tag ids ({@code namespace:path}, no {@code #} prefix) from spotlight/pages/recipe-like fields. */
    private final Set<String> tags = new TreeSet<>();

    /**
     * Texture references — values ending in {@code .png} found in book/category/entry icons
     * and {@code patchouli:image} page {@code images[]}.
     */
    private final Set<String> textures = new TreeSet<>();

    /** Entity ids referenced by {@code patchouli:entity} pages. */
    private final Set<String> entities = new TreeSet<>();

    /**
     * Multiblock ids referenced by {@code patchouli:multiblock} (when {@code multiblock} is
     * a string id rather than an inline definition) and {@code tfc:multimultiblock}.
     */
    private final Set<String> multiblocks = new TreeSet<>();

    /** Book-level model id (e.g. {@code patchouli:book_brown}). */
    private final Set<String> models = new TreeSet<>();

    /**
     * Raw blockstate-reference strings as they appear in {@code multiblock.mapping} dictionaries
     * across every page (patchouli's {@code mapping} key on {@code multiblock} pages, and TFC's
     * {@code multimultiblock} variants). Examples:
     *
     * <ul>
     *   <li>{@code "minecraft:furnace"} — block id only, defaultState wins.</li>
     *   <li>{@code "minecraft:oak_log[axis=y]"} — partial state, defaultState fills the rest.</li>
     * </ul>
     *
     * <p>This set is the <b>input</b> to {@code BlockStateResolver}; the resolved variants
     * with full property maps belong in a separate field that the writer fills.</p>
     */
    private final Set<String> blockstateRefs = new TreeSet<>();

    public int getPageCount() {
        return pageCount;
    }

    public Map<String, Integer> getPagesByType() {
        return pagesByType;
    }

    public int getPagesWithoutRaw() {
        return pagesWithoutRaw;
    }

    public Set<String> getRecipes() {
        return recipes;
    }

    /** Handbook recipe id → EMI mount/export recipe id (immutable copy). */
    public Map<String, String> getRecipeMountIds() {
        return Map.copyOf(recipeMountIds);
    }

    /**
     * EMI recipe id to export or mount; defaults to {@code handbookRecipeId} when unset.
     */
    public String getRecipeMountId(String handbookRecipeId) {
        if (handbookRecipeId == null || handbookRecipeId.isBlank()) {
            return handbookRecipeId;
        }
        return recipeMountIds.getOrDefault(handbookRecipeId, handbookRecipeId);
    }

    public void putRecipeMountId(String handbookRecipeId, String mountRecipeId) {
        if (handbookRecipeId == null || handbookRecipeId.isBlank()) {
            return;
        }
        String mount = mountRecipeId == null || mountRecipeId.isBlank() ? handbookRecipeId : mountRecipeId;
        recipeMountIds.put(handbookRecipeId, mount);
    }

    public Map<String, Set<String>> getRecipesByPageType() {
        return recipesByPageType;
    }

    public Set<String> getItems() {
        return items;
    }

    /** How often each item id appears in the book (for icon atlas ordering). */
    public Map<String, Integer> getItemReferenceCounts() {
        return itemReferenceCounts;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Set<String> getTextures() {
        return textures;
    }

    public Set<String> getEntities() {
        return entities;
    }

    public Set<String> getMultiblocks() {
        return multiblocks;
    }

    public Set<String> getModels() {
        return models;
    }

    public Set<String> getBlockstateRefs() {
        return blockstateRefs;
    }

    void addBlockstateRef(String ref) {
        if (ref != null && !ref.isBlank()) {
            blockstateRefs.add(ref);
        }
    }

    void incrementPageCount() {
        pageCount++;
    }

    void incrementPageType(String type) {
        pagesByType.merge(type, 1, Integer::sum);
    }

    void incrementPagesWithoutRaw() {
        pagesWithoutRaw++;
    }

    void addRecipe(String pageType, String recipeId) {
        recipes.add(recipeId);
        recipesByPageType.computeIfAbsent(pageType, k -> new TreeSet<>()).add(recipeId);
    }

    void addItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        items.add(itemId);
        itemReferenceCounts.merge(itemId, 1, Integer::sum);
    }

    void addTag(String tagId) {
        if (tagId != null && !tagId.isBlank()) {
            String normalized = tagId.startsWith("#") ? tagId.substring(1) : tagId;
            if (normalized.indexOf(':') > 0) {
                tags.add(normalized);
            }
        }
    }

    void addTexture(String texture) {
        textures.add(texture);
    }

    void addEntity(String entityId) {
        entities.add(entityId);
    }

    void addMultiblock(String multiblockId) {
        multiblocks.add(multiblockId);
    }

    void addModel(String modelId) {
        models.add(modelId);
    }

    /**
     * Returns a stat-only view (no full reference lists) suitable for chat / log lines.
     * Order matches what {@code BookScanner#print} writes.
     */
    public Map<String, Object> toStatsMap() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pages", pageCount);
        stats.put("pagesByType", pagesByType);
        if (pagesWithoutRaw > 0) {
            stats.put("pagesWithoutRaw", pagesWithoutRaw);
        }
        stats.put("recipes", recipes.size());
        Map<String, Integer> recipeCountsByPageType = new TreeMap<>();
        for (Map.Entry<String, Set<String>> e : recipesByPageType.entrySet()) {
            recipeCountsByPageType.put(e.getKey(), e.getValue().size());
        }
        stats.put("recipesByPageType", recipeCountsByPageType);
        stats.put("items", items.size());
        stats.put("tags", tags.size());
        stats.put("textures", textures.size());
        stats.put("entities", entities.size());
        stats.put("multiblocks", multiblocks.size());
        stats.put("models", models.size());
        stats.put("blockstateRefs", blockstateRefs.size());
        return stats;
    }
}
