package io.github.jmecn.fieldguideexport.export;

/** In-game / manual export toggles ({@code -Dfieldguide.*}). CI uses minecraft-web-export properties. */
public final class FieldGuideExportProperties {

    public static final String EXPORT_EMI_PROPERTY = "fieldguide.exportEmi";

    private FieldGuideExportProperties() {}

    /** When false, {@code /fieldguideexport run} writes guide only (no scoped EMI). */
    public static boolean exportEmi() {
        return !"false".equalsIgnoreCase(System.getProperty(EXPORT_EMI_PROPERTY, "true").trim());
    }
}
