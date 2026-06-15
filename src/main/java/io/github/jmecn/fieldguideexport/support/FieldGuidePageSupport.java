package io.github.jmecn.fieldguideexport.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FieldGuidePageSupport {

    public enum Tier {
        FULL,
        PARTIAL,
        TEMPLATE
    }

    private static final Map<String, Tier> TFC_MODPACK_PAGES = new LinkedHashMap<>();

    static {
        TFC_MODPACK_PAGES.put("tfc:anvil_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:drying_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:glassworking_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:heat_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:instant_barrel_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:knapping_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:loom_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:multimultiblock", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:quern_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:rock_knapping_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:sealed_barrel_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:sns/better_anvil_recipe", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:table", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:table_small", Tier.PARTIAL);
        TFC_MODPACK_PAGES.put("tfc:welding_recipe", Tier.PARTIAL);
    }

    private FieldGuidePageSupport() {}

    public static Tier tierOf(String pageType) {
        return TFC_MODPACK_PAGES.getOrDefault(pageType, Tier.TEMPLATE);
    }

    public static Set<String> knownTfcModpackTypes() {
        return TFC_MODPACK_PAGES.keySet();
    }

    public static Map<String, Object> exportCatalog() {
        Map<String, java.util.List<String>> byTier = new LinkedHashMap<>();
        for (Tier t : Tier.values()) {
            byTier.put(t.name().toLowerCase(), new java.util.ArrayList<>());
        }
        for (Map.Entry<String, Tier> e : TFC_MODPACK_PAGES.entrySet()) {
            byTier.get(e.getValue().name().toLowerCase()).add(e.getKey());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byTier", byTier);
        out.put("types", new LinkedHashMap<>(TFC_MODPACK_PAGES));
        return out;
    }
}
