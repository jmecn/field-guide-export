package io.github.jmecn.fieldguideexport.export.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jmecn.fieldguideexport.export.patchouli.Book;
import io.github.jmecn.fieldguideexport.export.patchouli.BookCategory;
import io.github.jmecn.fieldguideexport.export.patchouli.BookEntry;
import io.github.jmecn.fieldguideexport.export.patchouli.BookPage;

import java.util.Map;
import java.util.Set;

/**
 * Scans a fully loaded {@link Book} for every {@code namespace:id} reference its pages /
 * entries / categories make: recipes (per page type), items, textures, entities,
 * multiblocks, and book-level models.
 *
 * <p>This is a <b>shallow</b> scanner — it only inspects field names we already know to
 * carry references in vanilla Patchouli pages and the TFC page types this modpack uses.
 * The scanner deliberately does not deep-walk arbitrary string values: page text contains
 * {@code $(...)} formatting macros and entity tooltips, which produce too many false
 * positives.</p>
 *
 * <p>Field map (per page {@code type}):</p>
 *
 * <ul>
 *   <li>{@code patchouli:crafting / smelting / smoking / blasting / campfire_cooking /
 *       smithing / stonecutting}: {@code recipe}, {@code recipe2} (single string each).</li>
 *   <li>{@code patchouli:spotlight}: {@code item} (single string OR array of strings).</li>
 *   <li>{@code patchouli:entity}: {@code entity} (single string).</li>
 *   <li>{@code patchouli:multiblock}: {@code multiblock_id} or {@code multiblock} when the
 *       latter is a string (an inline object is skipped here).</li>
 *   <li>{@code patchouli:image}: {@code images[]}.</li>
 *   <li>TFC: {@code recipe} (one), {@code recipes[]} (knapping variants).</li>
 *   <li>{@code tfc:multimultiblock}: {@code multiblocks[]} — each element is either a
 *       string id (external multiblock) or an inline object with its own {@code mapping}
 *       (crop growth stages, tree stages, etc.). Both forms are scanned.</li>
 *   <li>Any page type: {@code mapping} dictionary (top-level or nested under
 *       {@code multiblock}) — string values are collected into
 *       {@link BookScanResult#getBlockstateRefs()} for later resolution against the registry.</li>
 * </ul>
 *
 * <p>Item icons on book / category / entry level are split: values ending in {@code .png}
 * go to {@link BookScanResult#getTextures()}; everything else is parsed as an
 * itemstack-string (NBT and count suffixes stripped) and goes to
 * {@link BookScanResult#getItems()}.</p>
 */
public final class BookScanner {

    /** Recipe-string fields used by vanilla / TFC / SNS anvil-style pages. */
    private static final String[] RECIPE_FIELDS_SINGLE = {"recipe", "recipe2", "recipe3", "recipe4"};

    /** Recipe-array fields used by TFC knapping pages (4 recipes shown side by side). */
    private static final String[] RECIPE_FIELDS_ARRAY = {"recipes"};

    private BookScanner() {}

    public static BookScanResult scan(Book book) {
        BookScanResult result = new BookScanResult();

        if (book.getModel() != null && !book.getModel().isBlank()) {
            result.addModel(book.getModel());
        }
        addIconRef(result, book.getIndexIcon());

        for (BookCategory cat : book.getCategories()) {
            addIconRef(result, cat.getIcon());
        }

        for (BookEntry entry : book.getEntries()) {
            addIconRef(result, entry.getIcon());
            for (BookPage page : entry.getPages()) {
                result.incrementPageCount();
                String type = effectiveType(page);
                result.incrementPageType(type);

                JsonObject raw = page.getRaw();
                if (raw == null) {
                    result.incrementPagesWithoutRaw();
                    continue;
                }
                scanPage(result, type, raw);
            }
        }
        return result;
    }

    private static String effectiveType(BookPage page) {
        return PatchouliPageTypes.normalize(page.getType());
    }

    private static void scanPage(BookScanResult result, String type, JsonObject raw) {
        for (String field : RECIPE_FIELDS_SINGLE) {
            String id = optString(raw, field);
            if (id != null) {
                result.addRecipe(type, id);
            }
        }
        for (String field : RECIPE_FIELDS_ARRAY) {
            addStringArray(raw, field, id -> result.addRecipe(type, id));
        }

        if ("patchouli:spotlight".equals(type)) {
            String tag = optString(raw, "tag");
            if (tag != null) {
                result.addTag(tag);
            }
            String single = optString(raw, "item");
            if (single != null) {
                addItemStackRef(result, single);
            } else {
                addStringArray(raw, "item", v -> addItemStackRef(result, v));
            }
        }

        if ("patchouli:entity".equals(type)) {
            String entity = optString(raw, "entity");
            if (entity != null) {
                result.addEntity(entity);
            }
        }

        if ("patchouli:multiblock".equals(type)) {
            String mb = optString(raw, "multiblock_id");
            if (mb == null) {
                JsonElement inline = raw.get("multiblock");
                if (inline != null && inline.isJsonPrimitive()) {
                    mb = inline.getAsString();
                }
            }
            if (mb != null && !mb.isBlank()) {
                result.addMultiblock(mb);
            }
        }

        if ("patchouli:image".equals(type)) {
            addStringArray(raw, "images", result::addTexture);
        }

        collectMappingFromObject(result, raw);
        JsonElement inlineMb = raw.get("multiblock");
        if (inlineMb != null && inlineMb.isJsonObject()) {
            collectMappingFromObject(result, inlineMb.getAsJsonObject());
        }
        collectMultiblocksArray(result, raw);
    }

    /**
     * TFC {@code tfc:multimultiblock} (and any page using the same field) stores an array where
     * each entry is either:
     *
     * <ul>
     *   <li>a string — external {@code multiblock_id} reference;</li>
     *   <li>an object — inline Patchouli multiblock with {@code pattern} + {@code mapping}
     *       (used for crop/tree growth stages: {@code age=0}, {@code age=1}, …).</li>
     * </ul>
     */
    private static void collectMultiblocksArray(BookScanResult result, JsonObject page) {
        JsonElement el = page.get("multiblocks");
        if (el == null || !el.isJsonArray()) {
            return;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                String id = item.getAsString();
                if (id != null && !id.isBlank()) {
                    result.addMultiblock(id);
                }
            } else if (item.isJsonObject()) {
                collectMappingFromObject(result, item.getAsJsonObject());
            }
        }
    }

    /**
     * Collects every string value found in this object's {@code mapping} dictionary into the
     * blockstate-reference bucket.
     *
     * <p>Patchouli's {@code multiblock} pages embed a {@code mapping: { "char": "ns:block[k=v]" }}
     * dictionary; this method is type-agnostic because TFC and other addons use the same idiom
     * on custom page types. Non-object {@code mapping} fields are ignored.</p>
     */
    private static void collectMappingFromObject(BookScanResult result, JsonObject obj) {
        JsonElement mappingEl = obj.get("mapping");
        if (mappingEl == null || !mappingEl.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : mappingEl.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                result.addBlockstateRef(value.getAsString());
            }
        }
    }

    /**
     * Patchouli icons can be:
     * <ul>
     *   <li>An ItemStack string like {@code tfc:firepit} or {@code minecraft:diamond_sword{NBT}}.</li>
     *   <li>A resource path to a square texture, identifiable by the {@code .png} suffix.</li>
     * </ul>
     */
    private static void addIconRef(BookScanResult result, String icon) {
        if (icon == null || icon.isBlank()) {
            return;
        }
        if (icon.endsWith(".png")) {
            result.addTexture(icon);
        } else {
            addItemStackRef(result, icon);
        }
    }

    /**
     * Strips {@code #count} and {@code {nbt...}} suffixes so the resulting set is at
     * {@code namespace:path} granularity (matches what the registry uses).
     */
    private static void addItemStackRef(BookScanResult result, String itemStackString) {
        if (itemStackString == null || itemStackString.isBlank()) {
            return;
        }
        int hash = itemStackString.indexOf('#');
        int brace = itemStackString.indexOf('{');
        int cut = itemStackString.length();
        if (hash >= 0) cut = Math.min(cut, hash);
        if (brace >= 0) cut = Math.min(cut, brace);
        String id = itemStackString.substring(0, cut).trim();
        if (!id.isEmpty()) {
            if (id.startsWith("#")) {
                result.addTag(id);
            } else {
                result.addItem(id);
            }
        }
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        String value = el.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static void addStringArray(JsonObject obj, String key, java.util.function.Consumer<String> sink) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) {
            return;
        }
        JsonArray array = el.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive()) {
                continue;
            }
            String value = item.getAsString();
            if (value != null && !value.isBlank()) {
                sink.accept(value);
            }
        }
    }
}
