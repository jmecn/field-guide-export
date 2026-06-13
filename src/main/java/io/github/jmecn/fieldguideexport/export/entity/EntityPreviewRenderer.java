package io.github.jmecn.fieldguideexport.export.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import vazkii.patchouli.common.util.EntityUtil;

import java.util.function.Function;

/**
 * Off-screen entity export: Patchouli entity loading + WikiZoomer draw path
 * ({@link EntityWikiZoomerDraw}).
 */
public final class EntityPreviewRenderer {

    private EntityPreviewRenderer() {}

    public static EntityComposition compose(Minecraft client, EntityRenderRequest request) {
        Level level = client.level;
        if (level == null) {
            throw new IllegalStateException("client.level is null");
        }
        Function<Level, Entity> creator = EntityUtil.loadEntity(request.entity());
        Entity entity = creator.apply(level);
        prepareEntity(entity);
        return new EntityComposition(entity, request.scale(), request.offset(), request.defaultRotation());
    }

    public static void renderExported(
            Minecraft client,
            GuiGraphics graphics,
            EntityComposition composition,
            int frameSize) {
        Entity entity = composition.entity();
        float centerX = EntityPreviewLayout.scaledCenterX(frameSize);
        float centerY = EntityPreviewLayout.centerY(
                entity, composition.pageScale(), composition.extraOffset(), frameSize);
        float zoom = EntityPreviewLayout.adaptiveZoomScale(entity, composition.pageScale(), frameSize);
        float yaw = EntityPreviewLayout.wikiYawDegrees(composition.defaultRotation());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 10f);
        EntityWikiZoomerDraw.draw(graphics, entity, zoom, yaw, EntityWikiZoomerDraw.PITCH_DEGREES);
        graphics.pose().popPose();
    }

    private static void prepareEntity(Entity entity) {
        entity.setOldPosAndRot();
        entity.setDeltaMovement(0, 0, 0);
    }

    public record EntityComposition(
            Entity entity,
            float pageScale,
            float extraOffset,
            float defaultRotation) {}
}
