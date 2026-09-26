package io.github.jmecn.fieldguideexport;

import java.util.Locale;

public final class FieldGuideExportProperties {

    public static final String EXPORT_EMI_PROPERTY = "fieldguide.exportEmi";
    public static final String BOOK_NAMESPACE_PROPERTY = "fieldguide.bookNamespace";
    public static final String BOOK_ID_PROPERTY = "fieldguide.bookId";

    private static final String DEFAULT_BOOK_NAMESPACE = "tfc";
    private static final String DEFAULT_BOOK_ID = "field_guide";

    private FieldGuideExportProperties() {}

    public static boolean exportEmi() {
        return !"false".equalsIgnoreCase(System.getProperty(EXPORT_EMI_PROPERTY, "true").trim());
    }

    public static String bookNamespace() {
        return resolveBookPart(BOOK_NAMESPACE_PROPERTY, "BOOK_NAMESPACE", DEFAULT_BOOK_NAMESPACE);
    }

    public static String bookId() {
        return resolveBookPart(BOOK_ID_PROPERTY, "BOOK_ID", DEFAULT_BOOK_ID);
    }

    private static String resolveBookPart(String property, String envVar, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
