package io.github.jmecn.fieldguideexport.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL12;

import java.io.IOException;

/**
 * Off-screen framebuffer capture for field-guide entity previews.
 * Entity pages use {@link #setupGuiEntityRendering(int, int)} (256×256 GUI ortho), not
 * {@code setupItemRendering()} (16×16 item slot), or exported PNGs render fully transparent.
 */
public final class OffScreenRenderer implements AutoCloseable {

    private final NativeImage nativeImage;
    private final TextureTarget frameBuffer;
    private final int width;
    private final int height;

    public OffScreenRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        RenderSystem.viewport(0, 0, width, height);
        nativeImage = new NativeImage(width, height, true);
        frameBuffer = new TextureTarget(width, height, true, true);
        frameBuffer.setClearColor(0, 0, 0, 0);
        frameBuffer.clear(true);
    }

    @Override
    public void close() {
        nativeImage.close();
        frameBuffer.destroyBuffers();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            var window = minecraft.getWindow();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    public byte[] captureAsPng(Runnable runnable) {
        renderToBuffer(runnable);
        try {
            return nativeImage.asByteArray();
        } catch (IOException e) {
            throw new RuntimeException("failed to encode image as PNG", e);
        }
    }

    /**
     * Patchouli {@code PageEntity} / inventory-style entity preview projection for the given
     * framebuffer size (typically 256×256).
     */
    public void setupGuiEntityRendering(int logicalWidth, int logicalHeight) {
        setupOrtho(logicalWidth, logicalHeight);
        Lighting.setupForEntityInInventory();
        FogRenderer.setupNoFog();
    }

    private void setupOrtho(float logicalW, float logicalH) {
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0f, logicalW, logicalH, 0.0f, 1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.setIdentity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
    }

    private void renderToBuffer(Runnable runnable) {
        frameBuffer.bindWrite(true);
        GlStateManager._clear(GL12.GL_COLOR_BUFFER_BIT | GL12.GL_DEPTH_BUFFER_BIT, false);
        runnable.run();
        frameBuffer.unbindWrite();

        frameBuffer.bindRead();
        nativeImage.downloadTexture(0, false);
        nativeImage.flipY();
        frameBuffer.unbindRead();
    }
}
