package io.github.jmecn.fieldguideexport.export;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Language codes for {@code guide-export/lang/}. Keep in sync with
 * {@code io.github.jmecn.fieldguideexport.localization.Language} in {@code :core}.
 */
public final class FieldGuideExportLanguages {

    /** Same order as {@code Language} enum in core. */
    public static final List<String> SUPPORTED = List.of(
            "en_us",
            "de_de",
            "es_es",
            "fr_fr",
            "hu_hu",
            "ja_jp",
            "ko_kr",
            "pl_pl",
            "pt_br",
            "ru_ru",
            "sv_se",
            "tr_tr",
            "uk_ua",
            "zh_cn",
            "zh_hk",
            "zh_tw");

    private FieldGuideExportLanguages() {}

    public static Set<String> resolve() {
        String raw = System.getProperty("fieldguide.exportLanguages", "").trim();
        if (raw.isEmpty()) {
            return Set.copyOf(SUPPORTED);
        }
        if ("*".equals(raw)) {
            return null; // caller uses all MC languages
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
