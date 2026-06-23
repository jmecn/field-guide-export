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

    @Test
    void splitSerializedStacksRespectsCommasAndBraces() {
        assertArrayEquals(
                new String[]{"tfc:ceramic/jug", "tfc:silica_glass_bottle"},
                IconStackIds.splitSerializedStacks("tfc:ceramic/jug,tfc:silica_glass_bottle"));
        assertArrayEquals(
                new String[]{"minecraft:stick{CustomModelData:1}", "minecraft:stone"},
                IconStackIds.splitSerializedStacks("minecraft:stick{CustomModelData:1},minecraft:stone"));
    }
}
