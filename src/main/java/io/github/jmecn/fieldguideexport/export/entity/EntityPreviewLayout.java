package io.github.jmecn.fieldguideexport.export.entity;

import net.minecraft.world.entity.Entity;

/**
 * Layout math for off-screen entity export. Draw path from WikiZoomer; zoom/offset from Patchouli
 * {@code PageEntity.loadEntity} so small and large mobs fill the frame similarly.
 */
public final class EntityPreviewLayout {

    /** Patchouli entity viewport width in book GUI pixels. */
    public static final float BOOK_VIEWPORT_WIDTH = 116f;
    /** Patchouli entity anchor X in book GUI pixels. */
    public static final float BOOK_CENTER_X = 58f;
    /** Patchouli entity anchor Y in book GUI pixels. */
    public static final float BOOK_CENTER_Y = 60f;
    /** WikiZoomer default zoom slider; also Patchouli renderScale numerator. */
    public static final float WIKI_DEFAULT_ZOOM = 100f;
    /** Patchouli {@code renderScale} shrink factor. */
    public static final float PATCHOULI_SIZE_SHRINK = 0.8f;
    /** Match WikiZoomer slider max. */
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

    /**
     * Patchouli {@code renderScale = 100 / entitySize * 0.8 * pageScale}, scaled from book viewport
     * to export frame. Large mobs get a smaller multiplier; small mobs get a larger one.
     */
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

    /** Patchouli vertical anchor offset in model space (before zoom). */
    public static float patchouliVerticalOffset(Entity entity, float extraOffset) {
        float size = entitySize(entity);
        return Math.max(entity.getBbHeight(), size) * 0.5f + extraOffset;
    }

    /** Screen Y: book center plus Patchouli offset converted through adaptive zoom. */
    public static float centerY(Entity entity, float pageScale, float extraOffset, int frameSize) {
        float zoom = adaptiveZoomScale(entity, pageScale, frameSize);
        return scaledCenterY(frameSize) + patchouliVerticalOffset(entity, extraOffset) * zoom;
    }

    /**
     * Patchouli {@code default_rotation} is YP degrees on the pose; WikiZoomer GUI uses YP 45
     * when Patchouli default is -45.
     */
    public static float wikiYawDegrees(float defaultRotationDegrees) {
        return -defaultRotationDegrees;
    }
}
