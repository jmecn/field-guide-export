package io.github.jmecn.fieldguideexport.export.entity;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRenderRequestTest {

    @Test
    void fromPageJsonUsesPatchouliDefaults() {
        JsonObject raw = new JsonObject();
        raw.addProperty("entity", "minecraft:piglin");

        EntityRenderRequest request = EntityRenderRequest.fromPageJson(raw);
        assertNotNull(request);
        assertEquals("minecraft:piglin", request.entity());
        assertEquals(EntityRenderRequest.DEFAULT_SCALE, request.scale());
        assertEquals(EntityRenderRequest.DEFAULT_OFFSET, request.offset());
        assertEquals(EntityRenderRequest.DEFAULT_ROTATION, request.defaultRotation());
    }

    @Test
    void fromPageJsonReadsCompositionFields() {
        JsonObject raw = new JsonObject();
        raw.addProperty("entity", "tfc:dog{NoAI:1b}");
        raw.addProperty("scale", 0.7);
        raw.addProperty("offset", 2.5);
        raw.addProperty("default_rotation", 30);

        EntityRenderRequest request = EntityRenderRequest.fromPageJson(raw);
        assertNotNull(request);
        assertEquals(0.7f, request.scale());
        assertEquals(2.5f, request.offset());
        assertEquals(30f, request.defaultRotation());
    }

    @Test
    void fromPageJsonReturnsNullWithoutEntity() {
        assertNull(EntityRenderRequest.fromPageJson(new JsonObject()));
    }
}
