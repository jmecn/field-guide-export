package io.github.jmecn.fieldguideexport.export.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRenderPathsTest {

    @Test
    void plainEntityUsesFlatPath() {
        EntityRenderRequest request = new EntityRenderRequest("minecraft:piglin", 1f, 0f, -45f);
        assertEquals("assets/entities/minecraft/piglin.png", EntityRenderPaths.relativePngPath(request));
    }

    @Test
    void nbtEntityUsesHashedSubpath() {
        EntityRenderRequest a = new EntityRenderRequest("tfc:dog{NoAI:1b}", 0.7f, 0f, -45f);
        EntityRenderRequest b = new EntityRenderRequest("tfc:dog{NoAI:1b,Other:2b}", 0.7f, 0f, -45f);

        String pathA = EntityRenderPaths.relativePngPath(a);
        String pathB = EntityRenderPaths.relativePngPath(b);

        assertTrue(pathA.startsWith("assets/entities/tfc/dog/"));
        assertTrue(pathA.endsWith(".png"));
        assertNotEquals(pathA, pathB);
    }
}
