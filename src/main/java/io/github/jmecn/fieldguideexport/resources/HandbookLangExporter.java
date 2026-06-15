package io.github.jmecn.fieldguideexport.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.jmecn.fieldguideexport.FieldGuideExportLanguages;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class HandbookLangExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HandbookLangExporter() {}

    public record Result(
            int languagesWritten,
            long totalBytes,
            int duplicateKeyWarnings,
            int filteredKeysRequested,
            int keysSkipped,
            int keysPerLanguage) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipLangExport");
    }

    public static Result export(Path outputDir, Minecraft client) throws IOException {
        return export(outputDir, client, null, null);
    }

    public static Result exportHandbookLang(Path outputDir, Minecraft client) throws IOException {
        return exportHandbookLang(outputDir, client, null, null);
    }

    public static Result exportHandbookLang(Path outputDir, Minecraft client, Set<String> onlyNamespaces, Set<String> onlyKeys)
            throws IOException {
        return exportTo(outputDir.resolve("lang"), client, onlyNamespaces, onlyKeys);
    }

    public static Result export(Path outputDir, Minecraft client, Set<String> onlyNamespaces, Set<String> onlyKeys)
            throws IOException {
        return exportHandbookLang(outputDir, client, onlyNamespaces, onlyKeys);
    }

    public static Result exportTo(Path langRoot, Minecraft client, Set<String> onlyNamespaces, Set<String> onlyKeys)
            throws IOException {
        Files.createDirectories(langRoot);

        Set<String> languages = FieldGuideExportLanguages.resolve();
        if (languages == null) {
            languages = client.getLanguageManager().getLanguages().keySet();
        }

        int langWritten = 0;
        long totalBytes = 0;
        int dupWarnings = 0;
        int keysSkipped = 0;
        int keysPerLanguage = 0;
        String mode = onlyKeys == null ? "full" : "filtered";

        for (String langCode : languages) {
            String langFile = langCode + ".json";
            Map<String, String> merged = new TreeMap<>();
            Map<ResourceLocation, Resource> hits = collectLangHits(client, langFile, onlyNamespaces);

            if (hits.isEmpty()) {
                LOGGER.warn("[lang] {} — no cmd lang files matched (namespaces={})", langCode,
                        onlyNamespaces == null ? "all" : onlyNamespaces);
                logLangPathProbe(client, langFile);
            }

            for (Map.Entry<ResourceLocation, Resource> hit : hits.entrySet()) {
                try (var reader = new InputStreamReader(hit.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    for (var entry : obj.entrySet()) {
                        String key = entry.getKey();
                        if (onlyKeys != null && !onlyKeys.contains(key)) {
                            keysSkipped++;
                            continue;
                        }
                        String value = entry.getValue().getAsString();
                        if (merged.containsKey(key)) {
                            dupWarnings++;
                            if (dupWarnings <= 20) {
                                LOGGER.warn("[lang] duplicate key '{}' from {}", key, hit.getKey());
                            }
                        }
                        merged.put(key, value);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[lang] failed to read {}: {}", hit.getKey(), e.getMessage());
                }
            }

            if (merged.isEmpty()) {
                LOGGER.warn("[lang] {} — 0 keys after merge ({}, {} cmd files read)", langCode, mode, hits.size());
                continue;
            }

            Path out = langRoot.resolve(langFile);
            String json = GSON.toJson(merged);
            Files.writeString(out, json);
            langWritten++;
            totalBytes += json.length();
            keysPerLanguage = merged.size();
            LOGGER.info("[lang] {} — {} keys from {} cmd files ({})", langCode, merged.size(), hits.size(), mode);
        }

        if (onlyKeys != null) {
            LOGGER.info("[lang] filtered key filter: {} requested, ~{} keys per language file, {} entries skipped while scanning",
                    onlyKeys.size(), keysPerLanguage, keysSkipped);
        }

        return new Result(
                langWritten,
                totalBytes,
                dupWarnings,
                onlyKeys != null ? onlyKeys.size() : 0,
                keysSkipped,
                keysPerLanguage);
    }

    private static boolean matchesLangPath(ResourceLocation loc, String langFile) {
        String path = loc.getPath();
        return path.equals(langFile) || path.equals("lang/" + langFile) || path.endsWith("/" + langFile);
    }

    private static Map<ResourceLocation, Resource> collectLangHits(
            Minecraft client,
            String langFile,
            Set<String> onlyNamespaces) {
        Predicate<ResourceLocation> filter = loc -> matchesLangPath(loc, langFile)
                && !ResourceExportFilter.isExcluded(loc)
                && (onlyNamespaces == null || onlyNamespaces.contains(loc.getNamespace()));

        Map<ResourceLocation, Resource> hits = new LinkedHashMap<>();
        mergeLangHits(hits, client.getResourceManager(), filter);
        var server = client.getSingleplayerServer();
        if (server != null) {
            mergeLangHits(hits, server.getResourceManager(), filter);
        }
        return hits;
    }

    private static void mergeLangHits(
            Map<ResourceLocation, Resource> into,
            ResourceManager rm,
            Predicate<ResourceLocation> filter) {
        for (var entry : rm.listResources("lang", filter).entrySet()) {
            into.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static void logLangPathProbe(Minecraft client, String langFile) {
        int shown = 0;
        StringBuilder sample = new StringBuilder();
        for (ResourceLocation loc : client.getResourceManager().listResources("lang", l -> matchesLangPath(l, langFile)).keySet()) {
            if (shown++ >= 5) {
                break;
            }
            if (shown > 1) {
                sample.append(", ");
            }
            sample.append(loc);
        }
        if (shown > 0) {
            LOGGER.warn("[lang] client has {} lang file(s) for {} but none passed namespace filter; sample: {}",
                    shown, langFile, sample);
        } else {
            LOGGER.warn("[lang] client ResourceManager has no resources under lang/ for {} (assets not loaded?)", langFile);
        }
    }
}
