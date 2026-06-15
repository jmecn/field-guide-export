package io.github.jmecn.fieldguideexport.icons;

/** Normalizes Patchouli icon / item-stack strings to registry ids. */
public final class IconStackIds {

    private IconStackIds() {}

    /**
     * @return registry id, {@code null} for blank, tag-only ({@code #tag}), or unparseable input
     */
    public static String toItemId(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        if (icon.endsWith(".png")) {
            return null;
        }
        int hash = icon.indexOf('#');
        int brace = icon.indexOf('{');
        int cut = icon.length();
        if (hash >= 0) {
            cut = Math.min(cut, hash);
        }
        if (brace >= 0) {
            cut = Math.min(cut, brace);
        }
        String id = icon.substring(0, cut).trim();
        if (id.isEmpty() || id.startsWith("#")) {
            return null;
        }
        int comma = id.indexOf(',');
        if (comma >= 0) {
            id = id.substring(0, comma).trim();
        }
        return id.isEmpty() ? null : id;
    }

    public static boolean isTextureIcon(String icon) {
        return icon != null && icon.endsWith(".png");
    }

    /** {@code namespace:path/to.png} → {@code assets/namespace/path/to.png} under guide-export. */
    public static String textureAssetRelativePath(String icon) {
        if (!isTextureIcon(icon)) {
            return null;
        }
        int colon = icon.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return "assets/" + icon.substring(0, colon) + "/" + icon.substring(colon + 1);
    }
}
