package io.github.jmecn.fieldguideexport;

public final class FieldGuideExportProperties {

    public static final String EXPORT_EMI_PROPERTY = "fieldguide.exportEmi";

    private FieldGuideExportProperties() {}

    public static boolean exportEmi() {
        return !"false".equalsIgnoreCase(System.getProperty(EXPORT_EMI_PROPERTY, "true").trim());
    }
}
