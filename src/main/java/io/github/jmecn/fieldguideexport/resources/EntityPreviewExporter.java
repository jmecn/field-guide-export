package io.github.jmecn.fieldguideexport.resources;

import io.github.jmecn.fieldguideexport.entity.EntityPreviewRenderer;
import io.github.jmecn.fieldguideexport.entity.EntityRenderPaths;
import io.github.jmecn.fieldguideexport.entity.EntityRenderRequest;
import io.github.jmecn.fieldguideexport.render.OffScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports {@code patchouli:entity} page previews to {@code assets/entities/} and returns
 * entries for {@code meta.json} {@code entityRenders} map (key = entity id).
 *
 * <p>Composition aligns with Patchouli {@code PageEntity}; frame size defaults to 256×256.</p>
 */
public final class EntityPreviewExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide-export");

    public static final int FRAME_SIZE = 256;

    private EntityPreviewExporter() {}

    public record RenderedEntity(
            EntityRenderRequest request,
            String path,
            int width,
            int height) {}

    public record FailedEntity(EntityRenderRequest request, String error) {}

    public record Result(
            int requested,
            int succeeded,
            int failed,
            long bytes,
            List<RenderedEntity> renders,
            List<FailedEntity> failures) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipEntityExport");
    }

    public static Result export(Path guideDir, Minecraft client, Iterable<EntityRenderRequest> requests)
            throws IOException {
        if (guideDir == null || client == null) {
            return new Result(0, 0, 0, 0, List.of(), List.of());
        }

        Set<EntityRenderRequest> unique = new LinkedHashSet<>();
        for (EntityRenderRequest request : requests) {
            unique.add(request);
        }
        if (unique.isEmpty()) {
            return new Result(0, 0, 0, 0, List.of(), List.of());
        }

        List<RenderedEntity> renders = new ArrayList<>();
        List<FailedEntity> failures = new ArrayList<>();
        long bytes = 0;

        for (EntityRenderRequest request : unique) {
            String relativePath = EntityRenderPaths.relativePngPath(request);
            Path outputFile = guideDir.resolve(relativePath);
            try {
                renderOne(client, request, outputFile);
                long size = Files.size(outputFile);
                bytes += size;
                renders.add(new RenderedEntity(request, relativePath, FRAME_SIZE, FRAME_SIZE));
            } catch (Exception e) {
                LOGGER.warn("[entity-export] failed for {}: {}", request.entity(), e.toString());
                failures.add(new FailedEntity(request, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        LOGGER.info(
                "[entity-export] {} requested, {} ok, {} failed, {} bytes",
                unique.size(),
                renders.size(),
                failures.size(),
                bytes);
        return new Result(unique.size(), renders.size(), failures.size(), bytes, List.copyOf(renders), List.copyOf(failures));
    }

    private static void renderOne(Minecraft client, EntityRenderRequest request, Path outputFile) throws IOException {
        EntityPreviewRenderer.EntityComposition composition = EntityPreviewRenderer.compose(client, request);
        Files.createDirectories(outputFile.getParent());
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        GuiGraphics graphics = new GuiGraphics(client, bufferSource);
        try (OffScreenRenderer framebuffer = new OffScreenRenderer(FRAME_SIZE, FRAME_SIZE)) {
            framebuffer.setupGuiEntityRendering(FRAME_SIZE, FRAME_SIZE);
            byte[] png = framebuffer.captureAsPng(() -> {
                EntityPreviewRenderer.renderExported(client, graphics, composition, FRAME_SIZE);
                graphics.flush();
                bufferSource.endBatch();
            });
            Files.write(outputFile, png);
        }
    }
}
