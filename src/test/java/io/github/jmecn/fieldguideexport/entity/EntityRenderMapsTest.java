package io.github.jmecn.fieldguideexport.entity;

import io.github.jmecn.fieldguideexport.resources.EntityPreviewExporter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EntityRenderMapsTest {

    @Test
    void renderEntryUsesEntityIdAsMapKey() {
        EntityRenderRequest request = new EntityRenderRequest("minecraft:piglin", 0.9f, 0f, -45f);
        EntityPreviewExporter.RenderedEntity rendered = new EntityPreviewExporter.RenderedEntity(
                request,
                "assets/entities/minecraft/piglin.png",
                256,
                256);

        Map<String, Object> entry = EntityRenderMaps.toRenderEntry(rendered);

        assertFalse(entry.containsKey("entity"));
        assertEquals(0.9f, ((Number) entry.get("scale")).floatValue());
        assertEquals("assets/entities/minecraft/piglin.png", entry.get("path"));
    }
}
