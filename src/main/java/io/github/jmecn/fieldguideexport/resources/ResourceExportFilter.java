package io.github.jmecn.fieldguideexport.resources;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResourceExportFilter {

    private static final Set<String> DEFAULT_EXCLUDED = Set.of("additionalplacements");

    private ResourceExportFilter() {}

    public static Set<String> excludedNamespaces() {
        String extra = System.getProperty("fieldguide.exportExcludedNamespaces", "").trim();
        if (extra.isEmpty()) {
            return DEFAULT_EXCLUDED;
        }
        var merged = DEFAULT_EXCLUDED.stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Arrays.stream(extra.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(merged::add);
        return Set.copyOf(merged);
    }

    public static boolean isExcluded(ResourceLocation id) {
        return excludedNamespaces().contains(id.getNamespace());
    }
}
