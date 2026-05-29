package io.github.jmecn.fieldguideexport.export.scan;

/**
 * Normalizes Patchouli page {@code type} strings the same way the mod does when loading pages:
 * types without a {@code namespace:} prefix get {@code patchouli:} prepended
 * (e.g. {@code text} → {@code patchouli:text}). Addon types such as {@code tfc:multimultiblock}
 * are left unchanged.
 */
public final class PatchouliPageTypes {

    public static final String DEFAULT_NAMESPACE = "patchouli";
    public static final String IMPLICIT_TEXT = DEFAULT_NAMESPACE + ":text";

    private PatchouliPageTypes() {}

    /**
     * @param raw {@code type} from page JSON, or {@code null} when the page is a bare string
     *            (implicit text page)
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return IMPLICIT_TEXT;
        }
        String type = raw.trim();
        if (type.indexOf(':') < 0) {
            return DEFAULT_NAMESPACE + ":" + type;
        }
        return type;
    }
}
