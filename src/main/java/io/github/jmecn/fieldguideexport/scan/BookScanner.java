package io.github.jmecn.fieldguideexport.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jmecn.fieldguideexport.entity.EntityRenderRequest;
import io.github.jmecn.fieldguideexport.icons.IconStackIds;
import io.github.jmecn.fieldguideexport.patchouli.Book;
import io.github.jmecn.fieldguideexport.patchouli.BookCategory;
import io.github.jmecn.fieldguideexport.patchouli.BookEntry;
import io.github.jmecn.fieldguideexport.patchouli.BookPage;

import java.util.Map;

public final class BookScanner {

    private static final String[] RECIPE_FIELDS_SINGLE = {"recipe", "recipe2", "recipe3", "recipe4"};

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
            result.addEntryIcon(entry.getId(), entry.getIcon());
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
            scanSpotlightItem(result, raw.get("item"));
        }

        if ("patchouli:entity".equals(type)) {
            EntityRenderRequest request = EntityRenderRequest.fromPageJson(raw);
            if (request != null) {
                result.addEntityRenderRequest(request);
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

    private static void scanSpotlightItem(BookScanResult result, JsonElement itemEl) {
        if (itemEl == null || itemEl.isJsonNull()) {
            return;
        }
        if (itemEl.isJsonPrimitive()) {
            addItemStackRef(result, itemEl.getAsString());
            return;
        }
        if (itemEl.isJsonObject()) {
            JsonObject obj = itemEl.getAsJsonObject();
            JsonElement tag = obj.get("tag");
            if (tag != null && tag.isJsonPrimitive()) {
                result.addTag(tag.getAsString());
            }
            JsonElement item = obj.get("item");
            if (item != null && item.isJsonPrimitive()) {
                addItemStackRef(result, item.getAsString());
            }
            return;
        }
        if (itemEl.isJsonArray()) {
            for (JsonElement entry : itemEl.getAsJsonArray()) {
                scanSpotlightItem(result, entry);
            }
        }
    }

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

    private static void addItemStackRef(BookScanResult result, String itemStackString) {
        if (itemStackString == null || itemStackString.isBlank()) {
            return;
        }
        for (String part : IconStackIds.splitSerializedStacks(itemStackString)) {
            addSingleItemStackRef(result, part);
        }
    }

    private static void addSingleItemStackRef(BookScanResult result, String itemStackString) {
        String trimmed = itemStackString.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.startsWith("tag:")) {
            result.addTag("#" + trimmed.substring(4).trim());
            return;
        }
        int hash = trimmed.indexOf('#');
        int brace = trimmed.indexOf('{');
        int cut = trimmed.length();
        if (hash >= 0) cut = Math.min(cut, hash);
        if (brace >= 0) cut = Math.min(cut, brace);
        String id = trimmed.substring(0, cut).trim();
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
