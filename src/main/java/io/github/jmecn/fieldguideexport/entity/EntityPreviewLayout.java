package io.github.jmecn.fieldguideexport.entity;

import net.minecraft.world.entity.Entity;

public final class EntityPreviewLayout {

    public static final float BOOK_VIEWPORT_WIDTH = 116f;
    
    public static final float BOOK_CENTER_X = 58f;
    
    public static final float BOOK_CENTER_Y = 60f;
    
    public static final float WIKI_DEFAULT_ZOOM = 100f;
    
    public static final float PATCHOULI_SIZE_SHRINK = 0.8f;
    
    public static final float ZOOM_MAX = 300f;
    public static final float ZOOM_MIN = 16f;

    private EntityPreviewLayout() {}

    public static float scaledCenterX(int frameSize) {
        return BOOK_CENTER_X * frameSize / BOOK_VIEWPORT_WIDTH;
    }

    public static float scaledCenterY(int frameSize) {
        return BOOK_CENTER_Y * frameSize / 120f;
    }

    public static float entitySize(Entity entity) {
        return Math.max(1f, Math.max(entity.getBbWidth(), entity.getBbHeight()));
    }

    public static float adaptiveZoomScale(Entity entity, float pageScale, int frameSize) {
        return adaptiveZoomScale(entity.getBbWidth(), entity.getBbHeight(), pageScale, frameSize);
    }

    static float adaptiveZoomScale(float bbWidth, float bbHeight, float pageScale, int frameSize) {
        float entitySize = Math.max(1f, Math.max(bbWidth, bbHeight));
        float patchouliScale = WIKI_DEFAULT_ZOOM / entitySize * PATCHOULI_SIZE_SHRINK * pageScale;
        float frameFactor = frameSize / BOOK_VIEWPORT_WIDTH;
        float zoom = patchouliScale * frameFactor;
        return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoom));
    }

    public static float patchouliVerticalOffset(Entity entity, float extraOffset) {
        float size = entitySize(entity);
        return Math.max(entity.getBbHeight(), size) * 0.5f + extraOffset;
    }

    public static float centerY(Entity entity, float pageScale, float extraOffset, int frameSize) {
        float zoom = adaptiveZoomScale(entity, pageScale, frameSize);
        return scaledCenterY(frameSize) + patchouliVerticalOffset(entity, extraOffset) * zoom;
    }

    public static float wikiYawDegrees(float defaultRotationDegrees) {
        return -defaultRotationDegrees;
    }
}
