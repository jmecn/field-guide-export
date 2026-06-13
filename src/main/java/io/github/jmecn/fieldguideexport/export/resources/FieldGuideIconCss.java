package io.github.jmecn.fieldguideexport.export.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renames MWE {@code .icon-atlas} selectors so field-guide sprites do not collide with EMI. */
public final class FieldGuideIconCss {

    public static final String FIELD_GUIDE_ICON_CSS_CLASS = "field-guide-icon-atlas";

    private FieldGuideIconCss() {}

    public static void rewriteExportedCss(Path iconsRoot) throws IOException {
        Path css = iconsRoot.resolve("icons.css");
        if (!Files.isRegularFile(css)) {
            return;
        }
        String content = Files.readString(css);
        if (!content.contains(".icon-atlas")) {
            return;
        }
        content = content.replace(".icon-atlas {", "." + FIELD_GUIDE_ICON_CSS_CLASS + " {");
        content = content.replace(".icon-atlas[", "." + FIELD_GUIDE_ICON_CSS_CLASS + "[");
        Files.writeString(css, content);
    }
}
