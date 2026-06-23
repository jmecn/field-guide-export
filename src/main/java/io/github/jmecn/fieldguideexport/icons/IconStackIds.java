package io.github.jmecn.fieldguideexport.icons;

import java.util.ArrayList;
import java.util.List;

public final class IconStackIds {

    private IconStackIds() {}

    /** Splits Patchouli comma-separated item strings (respects braces/quotes). */
    public static String[] splitSerializedStacks(String ingredientSerialized) {
        if (ingredientSerialized == null || ingredientSerialized.isBlank()) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        int lastIndex = 0;
        int braces = 0;
        Character insideString = null;
        for (int i = 0; i < ingredientSerialized.length(); i++) {
            switch (ingredientSerialized.charAt(i)) {
                case '{' -> {
                    if (insideString == null) {
                        braces++;
                    }
                }
                case '}' -> {
                    if (insideString == null) {
                        braces--;
                    }
                }
                case '\'' -> insideString = insideString == null ? '\'' : null;
                case '"' -> insideString = insideString == null ? '"' : null;
                case ',' -> {
                    if (braces <= 0 && insideString == null) {
                        result.add(ingredientSerialized.substring(lastIndex, i));
                        lastIndex = i + 1;
                    }
                }
                default -> { }
            }
        }
        result.add(ingredientSerialized.substring(lastIndex));
        return result.toArray(String[]::new);
    }

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
