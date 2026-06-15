package io.github.jmecn.fieldguideexport.resources;

import io.github.jmecn.fieldguideexport.icons.HandbookIconIndex;
import io.github.jmecn.fieldguideexport.icons.IconStackIds;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IconOgPreviewExporter {

    private static final Logger LOGGER = Logger.getLogger("fieldguide-export");

    public static final int OG_SIZE = 256;
    public static final String MISSING_ITEM_ID = "fieldguide:missing_icon";
    private static final String OG_RELATIVE_PREFIX = "assets/icons/og/";

    private IconOgPreviewExporter() {}

    public record Result(int requested, int succeeded, int failed, Map<String, String> paths) {}

    public static Result export(Path iconsRoot, Path guideExportRoot, Map<String, String> entryIcons) throws IOException {
        if (entryIcons == null || entryIcons.isEmpty()) {
            return new Result(0, 0, 0, Map.of());
        }
        HandbookIconIndex index = HandbookIconIndex.load(iconsRoot);
        Path ogRoot = iconsRoot.resolve("og");
        if (Files.exists(ogRoot)) {
            deleteTree(ogRoot);
        }
        Files.createDirectories(ogRoot);

        Map<String, String> paths = new LinkedHashMap<>();
        int failed = 0;
        for (Map.Entry<String, String> entry : entryIcons.entrySet()) {
            String entryId = entry.getKey();
            String icon = entry.getValue();
            Path output = ogRoot.resolve(entryId + ".png");
            try {
                Files.createDirectories(output.getParent());
                BufferedImage image = renderPreview(iconsRoot, guideExportRoot, index, icon);
                if (image == null) {
                    failed++;
                    continue;
                }
                ImageIO.write(image, "png", output.toFile());
                paths.put(entryId, OG_RELATIVE_PREFIX + entryId + ".png");
            } catch (Exception e) {
                failed++;
                LOGGER.log(Level.WARNING, "[og-icons] failed for entry {0} (icon {1}): {2}", new Object[] {entryId, icon, e.toString()});
            }
        }
        LOGGER.info(String.format(
                "[og-icons] %d entries, %d ok, %d failed under %s",
                entryIcons.size(),
                paths.size(),
                failed,
                ogRoot.toAbsolutePath()));
        return new Result(entryIcons.size(), paths.size(), failed, Map.copyOf(paths));
    }

    private static BufferedImage renderPreview(
            Path iconsRoot,
            Path guideExportRoot,
            HandbookIconIndex index,
            String icon) throws IOException {
        if (IconStackIds.isTextureIcon(icon)) {
            String rel = IconStackIds.textureAssetRelativePath(icon);
            if (rel == null) {
                return null;
            }
            Path texture = guideExportRoot.resolve(rel);
            if (!Files.isRegularFile(texture)) {
                LOGGER.fine("[og-icons] missing texture " + texture);
                return null;
            }
            return scaleToOg(ImageIO.read(texture.toFile()));
        }
        String itemId = IconStackIds.toItemId(icon);
        if (itemId == null) {
            return null;
        }
        HandbookIconIndex.SpritePlacement placement =
                index.findItem(itemId).or(() -> index.findItem(MISSING_ITEM_ID)).orElse(null);
        if (placement == null) {
            return null;
        }
        Path atlas = index.atlasFile(iconsRoot, placement);
        if (!Files.isRegularFile(atlas)) {
            return null;
        }
        BufferedImage sheet = ImageIO.read(atlas.toFile());
        int size = placement.cellSize();
        BufferedImage sprite = sheet.getSubimage(placement.x(), placement.y(), size, size);
        return scaleToOg(sprite);
    }

    static BufferedImage scaleToOg(BufferedImage source) {
        int og = OG_SIZE;
        int crop = Math.min(source.getWidth(), source.getHeight());
        int sx = Math.max(0, (source.getWidth() - crop) / 2);
        int sy = Math.max(0, (source.getHeight() - crop) / 2);

        BufferedImage out = new BufferedImage(og, og, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, og, og, sx, sy, sx + crop, sy + crop, null);
        graphics.dispose();
        return out;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
