package io.github.jmecn.fieldguideexport.export.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks blockstate / model JSON and enqueues dependent models, parents, and textures for closure export.
 *
 * <p>Referenced blockstates are exported in full (all variant entries). Missing builtin parents such as
 * {@code forge:item/default} are materialized via {@link SyntheticModelCatalog}.</p>
 */
@SuppressWarnings("removal")
final class ModelDependencyWalker {

    private ModelDependencyWalker() {}

    static void enqueueBlockstateDependencies(
            ResourceManager rm,
            ResourceLocation blockstateId,
            Resource resource,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            walkBlockstateJson(rm, blockstateId.getNamespace(), JsonParser.parseReader(reader).getAsJsonObject(), pending, written);
        } catch (Exception ignored) {
            // blockstate parse failures are non-fatal; the JSON file itself is still exported
        }
    }

    static void enqueueModelDependencies(
            ResourceManager rm,
            ResourceLocation modelId,
            Resource resource,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            enqueueModelDependenciesFromJson(rm, modelId, JsonParser.parseReader(reader).getAsJsonObject(), pending, written);
        } catch (Exception ignored) {
            // model parse failures are non-fatal
        }
    }

    static void enqueueModelDependenciesFromJson(
            ResourceManager rm,
            ResourceLocation modelId,
            JsonObject root,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        walkModelObject(rm, modelId.getNamespace(), root, pending, written);
    }

    private static void walkBlockstateJson(
            ResourceManager rm,
            String defaultNamespace,
            JsonObject root,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        if (root.has("variants") && root.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
                enqueueVariantList(rm, entry.getValue(), defaultNamespace, pending, written);
            }
        }
        if (root.has("multipart") && root.get("multipart").isJsonArray()) {
            for (JsonElement part : root.getAsJsonArray("multipart")) {
                if (!part.isJsonObject()) {
                    continue;
                }
                JsonObject partObj = part.getAsJsonObject();
                if (partObj.has("apply")) {
                    enqueueVariantList(rm, partObj.get("apply"), defaultNamespace, pending, written);
                }
            }
        }
    }

    private static void walkModelObject(
            ResourceManager rm,
            String defaultNamespace,
            JsonObject root,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        if (root.has("parent") && root.get("parent").isJsonPrimitive()) {
            String parent = root.get("parent").getAsString();
            if (!parent.isBlank() && !parent.startsWith("#")) {
                enqueueModelRef(rm, parent, defaultNamespace, pending, written);
            }
        }

        JsonObject textures = root.has("textures") && root.get("textures").isJsonObject()
                ? root.getAsJsonObject("textures")
                : null;
        if (textures != null) {
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                enqueueTextureRef(entry.getValue(), textures, pending, written, rm);
            }
        }

        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("elements")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject faces = element.getAsJsonObject().has("faces")
                        ? element.getAsJsonObject().getAsJsonObject("faces")
                        : null;
                if (faces == null) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    if (!face.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonElement texture = face.getValue().getAsJsonObject().get("texture");
                    enqueueTextureRef(texture, textures, pending, written, rm);
                }
            }
        }

        if (root.has("base") && root.get("base").isJsonObject()) {
            walkModelObject(rm, defaultNamespace, root.getAsJsonObject("base"), pending, written);
        }
        if (root.has("perspectives") && root.get("perspectives").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("perspectives").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    walkModelObject(rm, defaultNamespace, entry.getValue().getAsJsonObject(), pending, written);
                }
            }
        }
    }

    private static void enqueueVariantList(
            ResourceManager rm,
            JsonElement node,
            String defaultNamespace,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        if (node == null || node.isJsonNull()) {
            return;
        }
        if (node.isJsonObject()) {
            enqueueVariantObject(rm, node.getAsJsonObject(), defaultNamespace, pending, written);
            return;
        }
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                if (child.isJsonObject()) {
                    enqueueVariantObject(rm, child.getAsJsonObject(), defaultNamespace, pending, written);
                }
            }
        }
    }

    private static void enqueueVariantObject(
            ResourceManager rm,
            JsonObject variant,
            String defaultNamespace,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        if (!variant.has("model") || !variant.get("model").isJsonPrimitive()) {
            return;
        }
        enqueueModelRef(rm, variant.get("model").getAsString(), defaultNamespace, pending, written);
    }

    private static void enqueueModelRef(
            ResourceManager rm,
            String modelRef,
            String defaultNamespace,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written) {
        ResourceLocation modelLoc = resolveModelFile(modelRef, defaultNamespace);
        if (modelLoc == null || written.contains(modelLoc) || !SyntheticModelCatalog.isAvailable(rm, modelLoc)) {
            return;
        }
        pending.addLast(modelLoc);
    }

    private static void enqueueTextureRef(
            JsonElement textureNode,
            JsonObject textureMap,
            Deque<ResourceLocation> pending,
            Set<ResourceLocation> written,
            ResourceManager rm) {
        if (textureNode == null || !textureNode.isJsonPrimitive()) {
            return;
        }
        String ref = textureNode.getAsString();
        if (ref.startsWith("#")) {
            if (textureMap != null && textureMap.has(ref.substring(1))) {
                enqueueTextureRef(textureMap.get(ref.substring(1)), textureMap, pending, written, rm);
            }
            return;
        }
        Set<ResourceLocation> tmp = new LinkedHashSet<>();
        ModelDependencyCollector.seedTextureRef(ref, tmp);
        for (ResourceLocation tex : tmp) {
            if (!written.contains(tex) && rm.getResource(tex).isPresent()) {
                pending.addLast(tex);
            }
        }
    }

    static ResourceLocation resolveModelFile(String modelRef, String defaultNamespace) {
        if (modelRef == null || modelRef.isBlank()) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(modelRef);
        if (loc == null) {
            String namespace = namespaceForUnqualifiedModelRef(modelRef, defaultNamespace);
            loc = ResourceLocation.tryParse(namespace + ":" + modelRef);
        }
        if (loc == null) {
            return null;
        }
        String path = loc.getPath();
        if (!path.startsWith("models/")) {
            path = "models/" + path;
        }
        if (!path.endsWith(".json")) {
            path = path + ".json";
        }
        return new ResourceLocation(loc.getNamespace(), path);
    }

    private static String namespaceForUnqualifiedModelRef(String modelRef, String defaultNamespace) {
        if (modelRef.startsWith("block/") || modelRef.startsWith("item/") || modelRef.startsWith("builtin/")) {
            return "minecraft";
        }
        if (modelRef.startsWith("forge/")) {
            return "forge";
        }
        return defaultNamespace;
    }
}
