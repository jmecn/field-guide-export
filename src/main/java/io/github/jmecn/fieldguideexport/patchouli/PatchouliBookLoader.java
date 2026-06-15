package io.github.jmecn.fieldguideexport.patchouli;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PatchouliBookLoader {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new Gson();

    public static final String DEFAULT_LANGUAGE = "en_us";

    private final Minecraft client;
    private final MinecraftServer server;
    private final String namespace;
    private final String bookId;
    private final String language;

    public PatchouliBookLoader(Minecraft client, MinecraftServer server,
                               String namespace, String bookId, String language) {
        this.client = client;
        this.server = server;
        this.namespace = namespace;
        this.bookId = bookId;
        this.language = language;
    }

    public static PatchouliBookLoader forTfcFieldGuide(Minecraft client) {
        return new PatchouliBookLoader(client, client.getSingleplayerServer(),
                "tfc", "field_guide", DEFAULT_LANGUAGE);
    }

    public Book load() {
        Book book = loadBookMetadata();
        book.setNamespace(namespace);
        book.setBookId(bookId);
        book.setLanguage(language);

        int categoryCount = loadCategories(book);
        int entryAdded = loadEntries(book);
        book.sort();

        LOGGER.info("loaded patchouli book {}:{} ({} lang) — {} categories, {} entries (added {})",
                namespace, bookId, language,
                book.getCategories().size(),
                book.getEntries().size(),
                entryAdded);

        if (LOGGER.isDebugEnabled()) {
            for (BookCategory cat : book.getCategories()) {
                LOGGER.debug("  [{}] {} <{}> — {} entries (source={})",
                        cat.getSort(), cat.getId(), cat.getName(),
                        cat.getEntries().size(), cat.getAssetSource());
            }
        }
        return book;
    }

    @SuppressWarnings("removal") 
                                  
    private Book loadBookMetadata() {
        String bookJsonPath = "patchouli_books/" + bookId + "/book.json";
        ResourceLocation bookKey = new ResourceLocation(namespace, bookJsonPath);

        if (server == null) {
            LOGGER.warn("no integrated server available; cannot read data/{}/{} — using empty Book metadata",
                    namespace, bookJsonPath);
            return new Book();
        }

        ResourceManager dataResources = server.getResourceManager();
        Optional<Resource> resource = dataResources.getResource(bookKey);
        if (resource.isEmpty()) {
            LOGGER.warn("book.json not found: data/{}/{}; using empty Book metadata",
                    namespace, bookJsonPath);
            return new Book();
        }

        Resource res = resource.get();
        try (BufferedReader reader = res.openAsReader()) {
            Book book = GSON.fromJson(reader, Book.class);
            if (book == null) {
                book = new Book();
            }
            book.setAssetSource(new AssetSource(res.sourcePackId()));
            return book;
        } catch (IOException e) {
            LOGGER.error("failed to read book.json (data/{}/{})", namespace, bookJsonPath, e);
            return new Book();
        }
    }

    private int loadCategories(Book book) {
        String dir = "patchouli_books/" + bookId + "/" + language + "/categories";
        Map<ResourceLocation, Resource> hits = client.getResourceManager()
                .listResources(dir, loc -> loc.getNamespace().equals(namespace)
                        && loc.getPath().endsWith(".json"));

        int added = 0;
        for (Map.Entry<ResourceLocation, Resource> hit : hits.entrySet()) {
            ResourceLocation loc = hit.getKey();
            Resource res = hit.getValue();

            String relativeId = stripExtension(loc.getPath().substring(dir.length() + 1));
            try (BufferedReader reader = res.openAsReader()) {
                BookCategory cat = GSON.fromJson(reader, BookCategory.class);
                if (cat == null) {
                    LOGGER.warn("category {} parsed as null (empty JSON?); skipping", loc);
                    continue;
                }
                cat.setId(relativeId);
                cat.setAssetSource(new AssetSource(res.sourcePackId()));

                BookCategory live = book.addCategory(cat);
                if (live != cat) {
                    LOGGER.debug("category {} already present (kept first); duplicate from {}",
                            relativeId, res.sourcePackId());
                } else {
                    added++;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("failed to read category {} ({})", loc, res.sourcePackId(), e);
            }
        }
        return added;
    }

    private int loadEntries(Book book) {
        String dir = "patchouli_books/" + bookId + "/" + language + "/entries";
        Map<ResourceLocation, Resource> hits = client.getResourceManager()
                .listResources(dir, loc -> loc.getNamespace().equals(namespace)
                        && loc.getPath().endsWith(".json"));

        int added = 0;
        List<BookEntry> orphans = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> hit : hits.entrySet()) {
            ResourceLocation loc = hit.getKey();
            Resource res = hit.getValue();

            String relativeId = stripExtension(loc.getPath().substring(dir.length() + 1));
            BookEntry entry;
            try (BufferedReader reader = res.openAsReader()) {
                entry = GSON.fromJson(reader, BookEntry.class);
                if (entry == null) {
                    LOGGER.warn("entry {} parsed as null (empty JSON?); skipping", loc);
                    continue;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("failed to read entry {} ({})", loc, res.sourcePackId(), e);
                continue;
            }
            entry.ensurePagesNonNull();
            entry.setAssetSource(new AssetSource(res.sourcePackId()));
            entry.setIdsFromPath(relativeId);

            attachRawPageJson(entry, res, loc);

            if (book.addEntry(entry)) {
                added++;
            } else if (book.getEntry(entry.getId()) == null) {
                orphans.add(entry);
            }
        }
        if (!orphans.isEmpty()) {
            LOGGER.warn("{} entries reference unknown categories; first few: {}",
                    orphans.size(), orphans.subList(0, Math.min(5, orphans.size())));
        }
        return added;
    }

    private void attachRawPageJson(BookEntry entry, Resource res, ResourceLocation loc) {
        if (entry.getPages().isEmpty()) {
            return;
        }
        try (BufferedReader reader = res.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("pages")) {
                return;
            }
            var rawPages = root.getAsJsonArray("pages");
            int n = Math.min(rawPages.size(), entry.getPages().size());
            for (int i = 0; i < n; i++) {
                if (rawPages.get(i).isJsonObject()) {
                    entry.getPages().get(i).setRaw(rawPages.get(i).getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("failed to attach raw page json for {}", loc, e);
        }
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }
}
