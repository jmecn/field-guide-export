package io.github.jmecn.fieldguideexport.entity;

import com.google.gson.JsonObject;

import java.util.Objects;

public record EntityRenderRequest(
        String entity,
        float scale,
        float offset,
        float defaultRotation) {

    public static final float DEFAULT_SCALE = 1f;
    public static final float DEFAULT_OFFSET = 0f;
    public static final float DEFAULT_ROTATION = -45f;

    public EntityRenderRequest {
        Objects.requireNonNull(entity, "entity");
        if (entity.isBlank()) {
            throw new IllegalArgumentException("entity must not be blank");
        }
    }

    public static EntityRenderRequest fromPageJson(JsonObject raw) {
        if (raw == null) {
            return null;
        }
        String entity = optString(raw, "entity");
        if (entity == null) {
            return null;
        }
        float scale = optFloat(raw, "scale", DEFAULT_SCALE);
        float offset = optFloat(raw, "offset", DEFAULT_OFFSET);
        float rotation = optFloat(raw, "default_rotation", DEFAULT_ROTATION);
        return new EntityRenderRequest(entity, scale, offset, rotation);
    }

    public String registryId() {
        int brace = entity.indexOf('{');
        return brace > 0 ? entity.substring(0, brace) : entity;
    }

    private static String optString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static float optFloat(JsonObject obj, String key, float defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsFloat();
    }
}
