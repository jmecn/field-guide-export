package io.github.jmecn.fieldguideexport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class FieldGuideExportLanguages {

    private static final Logger LOGGER = LogManager.getLogger(FieldGuideExportLanguages.class);
    private static final String PROPERTY = "fieldguide.exportLanguages";
    private static final String FALLBACK = "en_us";

    private FieldGuideExportLanguages() {
    }

    public static Set<String> resolve() {
        String raw = System.getProperty(PROPERTY, "").trim();
        if (raw.isEmpty()) {
            LOGGER.warn(
                    "{} unset — exporting {} only; Field-Guide-Modern CI should set this from Language enum",
                    PROPERTY,
                    FALLBACK);
            return Set.of(FALLBACK);
        }
        if ("*".equals(raw)) {
            return null;
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static List<String> asList() {
        Set<String> languages = resolve();
        if (languages == null) {
            return List.of();
        }
        return languages.stream().sorted().toList();
    }
}
