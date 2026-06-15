package io.github.jmecn.fieldguideexport.entity;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Entity preview draw path adapted from WikiZoomer {@code GuiEntityZoomer.drawEntityOnScreen}
 * (Alexthe666 / wiki-zoomer cmd in this repo).
 */
public final class EntityWikiZoomerDraw {

    public static final float PITCH_DEGREES = 30f;

    private static final int PACKED_LIGHT = 15728880;

    private EntityWikiZoomerDraw() {}

    public static void draw(
            GuiGraphics graphics,
            Entity entity,
            float zoomScale,
            float yawDegrees,
            float pitchDegrees) {
        graphics.pose().pushPose();
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(180f));
        graphics.pose().scale(zoomScale, zoomScale, zoomScale);
        entity.setOnGround(false);
        RenderSystem.applyModelViewMatrix();

        Quaternionf pitch = Axis.XP.rotationDegrees(pitchDegrees);
        Quaternionf yaw = Axis.YP.rotationDegrees(yawDegrees);
        Quaternionf combined = Axis.ZP.rotationDegrees(0f);
        combined.mul(pitch);
        graphics.pose().mulPose(combined);
        graphics.pose().mulPose(yaw);

        Vector3f light0 = Util.make(new Vector3f(-0.2f, 0f, 1f), Vector3f::normalize);
        Vector3f light1 = Util.make(new Vector3f(-0.2f, -1f, 0f), Vector3f::normalize);
        RenderSystem.setShaderLights(light0, light1);

        Minecraft client = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        pitch.conjugate();
        dispatcher.overrideCameraOrientation(pitch);
        dispatcher.setRenderShadow(false);

        resetPose(entity);
        float partialTicks = client.getFrameTime();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(
                entity,
                0,
                0,
                0,
                0,
                partialTicks,
                graphics.pose(),
                buffers,
                PACKED_LIGHT));
        buffers.endBatch();
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }

    private static void resetPose(Entity entity) {
        entity.setYRot(0f);
        entity.setXRot(0f);
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 0f;
            living.yHeadRotO = 0f;
            living.yHeadRot = 0f;
            living.oAttackAnim = living.attackAnim = 0f;
        }
    }
}
