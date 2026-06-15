package io.github.jmecn.fieldguideexport.icons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IconStackIdsTest {

    @Test
    void stripsNbtAndCount() {
        assertEquals("tfc:dog", IconStackIds.toItemId("tfc:dog{NoAI:1b}"));
        assertEquals("minecraft:stone", IconStackIds.toItemId("minecraft:stone#3"));
    }

    @Test
    void textureIconIsDetected() {
        assertTrue(IconStackIds.isTextureIcon("tfg:textures/gui/foo.png"));
        assertEquals(
                "assets/tfg/textures/gui/foo.png",
                IconStackIds.textureAssetRelativePath("tfg:textures/gui/foo.png"));
    }

    @Test
    void tagIconReturnsNullItemId() {
        assertNull(IconStackIds.toItemId("#forge:ingots"));
    }
}
