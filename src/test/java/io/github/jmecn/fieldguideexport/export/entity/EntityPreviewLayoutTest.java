package io.github.jmecn.fieldguideexport.export.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPreviewLayoutTest {

    @Test
    void scaledCentersMatchPatchouliViewport() {
        assertEquals(128f, EntityPreviewLayout.scaledCenterX(256), 0.5f);
        assertEquals(128f, EntityPreviewLayout.scaledCenterY(256), 0.5f);
    }

    @Test
    void wikiYawMapsPatchouliDefaultRotation() {
        assertEquals(45f, EntityPreviewLayout.wikiYawDegrees(-45f), 0.5f);
    }

    @Test
    void adaptiveZoomEnlargesSmallMobsAndShrinksLargeOnes() {
        float small = EntityPreviewLayout.adaptiveZoomScale(0.25f, 0.2f, 1f, 256);
        float medium = EntityPreviewLayout.adaptiveZoomScale(0.85f, 1.4f, 1f, 256);
        float large = EntityPreviewLayout.adaptiveZoomScale(2f, 2.5f, 1f, 256);
        assertTrue(small > medium, "small mob should zoom in more");
        assertTrue(medium > large, "large mob should zoom out");
    }

    @Test
    void adaptiveZoomScalesWithPatchouliPageScale() {
        float base = EntityPreviewLayout.adaptiveZoomScale(0.85f, 1.4f, 1f, 256);
        float doubled = EntityPreviewLayout.adaptiveZoomScale(0.85f, 1.4f, 2f, 256);
        assertEquals(base * 2f, doubled, 0.5f);
    }
}
