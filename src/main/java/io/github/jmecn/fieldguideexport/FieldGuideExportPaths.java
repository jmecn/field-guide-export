package io.github.jmecn.fieldguideexport;
import io.github.jmecn.minecraftwebexport.config.MweConfig;


import java.nio.file.Path;

/**
 * Layout: {@code <exportRoot>/guide-export/} + {@code <exportRoot>/emi/} (EMI via minecraft-web-export).
 *
 * <p>{@code -Dfieldguide.exportFolder=} overrides the guide directory (FGM CI compat).
 * {@code -Dfieldguide.exportRoot=} sets the parent when both trees are written.</p>
 */
public final class FieldGuideExportPaths {

    public static final String GUIDE_SUBDIR = "guide-export";
    public static final String EXPORT_ROOT_PROPERTY = "fieldguide.exportRoot";
    public static final String EXPORT_FOLDER_PROPERTY = "fieldguide.exportFolder";

    private FieldGuideExportPaths() {}

    public static Path resolveExportRoot(Path gameDirectory) {
        String mweOut = MweConfig.exportOutputDir();
        if (!mweOut.isBlank()) {
            return Path.of(mweOut);
        }
        String root = System.getProperty(EXPORT_ROOT_PROPERTY);
        if (root != null && !root.isBlank()) {
            return Path.of(root.trim());
        }
        String folder = System.getProperty(EXPORT_FOLDER_PROPERTY);
        if (folder != null && !folder.isBlank()) {
            Path guide = Path.of(folder.trim());
            Path parent = guide.getParent();
            if (parent != null) {
                return parent;
            }
        }
        return gameDirectory.resolve("export");
    }

    public static Path guideDirectory(Path gameDirectory) {
        String folder = System.getProperty(EXPORT_FOLDER_PROPERTY);
        if (folder != null && !folder.isBlank()) {
            return Path.of(folder.trim());
        }
        return resolveExportRoot(gameDirectory).resolve(GUIDE_SUBDIR);
    }

    public static Path guideDirectoryFromExportRoot(Path exportRoot) {
        String folder = System.getProperty(EXPORT_FOLDER_PROPERTY);
        if (folder != null && !folder.isBlank()) {
            return Path.of(folder.trim());
        }
        return exportRoot.resolve(GUIDE_SUBDIR);
    }
}
