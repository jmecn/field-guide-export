package io.github.jmecn.fieldguideexport.scan;

import io.github.jmecn.fieldguideexport.emi.RecipeOverrideResolver;
import io.github.jmecn.fieldguideexport.entity.EntityRenderRequest;
import io.github.jmecn.fieldguideexport.resources.ReferencedResourceExporter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class BookScanResult {

    private int pageCount;

    private final Map<String, Integer> pagesByType = new TreeMap<>();

    private int pagesWithoutRaw;

    private final Set<String> recipes = new TreeSet<>();

    private final Map<String, String> recipeMountIds = new TreeMap<>();

    private final Map<String, Set<String>> recipesByPageType = new TreeMap<>();

    private final Set<String> items = new TreeSet<>();

    private final Map<String, Integer> itemReferenceCounts = new TreeMap<>();

    private final Map<String, String> entryIcons = new TreeMap<>();

    private final Set<String> tags = new TreeSet<>();

    private final Set<String> textures = new TreeSet<>();

    private final Set<String> entities = new TreeSet<>();

    private final LinkedHashMap<EntityRenderRequest, EntityRenderRequest> entityRenderRequests = new LinkedHashMap<>();

    private final Set<String> multiblocks = new TreeSet<>();

    private final Set<String> models = new TreeSet<>();

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

    public Map<String, String> getRecipeMountIds() {
        return Map.copyOf(recipeMountIds);
    }

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

    public Map<String, Integer> getItemReferenceCounts() {
        return itemReferenceCounts;
    }

    public Map<String, String> getEntryIcons() {
        return entryIcons;
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

    public List<EntityRenderRequest> getEntityRenderRequests() {
        return List.copyOf(entityRenderRequests.keySet());
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

    void addEntryIcon(String entryId, String icon) {
        if (entryId == null || entryId.isBlank() || icon == null || icon.isBlank()) {
            return;
        }
        entryIcons.put(entryId, icon);
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

    void addEntityRenderRequest(EntityRenderRequest request) {
        entityRenderRequests.putIfAbsent(request, request);
        entities.add(request.entity());
    }

    void addMultiblock(String multiblockId) {
        multiblocks.add(multiblockId);
    }

    void addModel(String modelId) {
        models.add(modelId);
    }

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
        stats.put("entityRenderRequests", entityRenderRequests.size());
        stats.put("multiblocks", multiblocks.size());
        stats.put("models", models.size());
        stats.put("blockstateRefs", blockstateRefs.size());
        return stats;
    }
}
