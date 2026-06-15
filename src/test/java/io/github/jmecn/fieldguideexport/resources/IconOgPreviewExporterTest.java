package io.github.jmecn.fieldguideexport.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IconOgPreviewExporterTest {

  @Test
  void exportsCroppedOgPreview(@TempDir Path root) throws IOException {
    Path iconsRoot = root.resolve("assets/icons");
    Files.createDirectories(iconsRoot);

    BufferedImage atlas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 8; y < 40; y++) {
      for (int x = 8; x < 40; x++) {
        atlas.setRGB(x, y, 0xFFFF0000);
      }
    }
    ImageIO.write(atlas, "png", iconsRoot.resolve("atlas-000.png").toFile());

    String indexJson =
        """
        {
          "cellSize": 32,
          "items": {
            "minecraft:piglin": { "page": 0, "x": 8, "y": 8 }
          }
        }
        """;
    Files.writeString(iconsRoot.resolve("index.json"), indexJson);

    IconOgPreviewExporter.Result result =
        IconOgPreviewExporter.export(
            iconsRoot,
            root,
            Map.of("beneath/piglins", "minecraft:piglin"));

    assertEquals(1, result.succeeded());
    assertEquals(
        "assets/icons/og/beneath/piglins.png",
        result.paths().get("beneath/piglins"));

    BufferedImage og = ImageIO.read(iconsRoot.resolve("og/beneath/piglins.png").toFile());
    assertEquals(IconOgPreviewExporter.OG_SIZE, og.getWidth());
    assertEquals(IconOgPreviewExporter.OG_SIZE, og.getHeight());
    assertNotEquals(0, og.getRGB(128, 128));
  }
}
