package io.github.jmecn.fieldguideexport.scan;

public final class PatchouliPageTypes {

    public static final String DEFAULT_NAMESPACE = "patchouli";
    public static final String IMPLICIT_TEXT = DEFAULT_NAMESPACE + ":text";

    private PatchouliPageTypes() {}

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
