package io.github.jmecn.fieldguideexport.export.patchouli;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * One page inside a {@link BookEntry}.
 *
 * <p>This first-pass POJO only deserializes the few common fields the loader needs to log
 * stats and dispatch later (type, flag, advancement, anchor). The raw JSON is kept on
 * {@link #raw} so subsequent page-type-specific code (text, crafting, multiblock, ...) can
 * read its specific fields without forcing the loader to know every Patchouli page type up
 * front. This mirrors {@code cli/.../patchouli/BookPage} but without lombok.</p>
 *
 * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/page-types/">Default Page types</a>
 */
public class BookPage {

    /**
     * Fully qualified page type, e.g. {@code patchouli:text}, {@code patchouli:crafting},
     * {@code tfc:heat_recipe}, {@code tfc:multimultiblock}.
     */
    @SerializedName("type")
    private String type;

    /** Optional config flag expression — page is hidden when the flag is false. */
    @SerializedName("flag")
    private String flag;

    /** Optional advancement gate — page only shows once the advancement is completed. */
    @SerializedName("advancement")
    private String advancement;

    /** Optional anchor for internal links of the form {@code $(l:entry#anchor)}. */
    @SerializedName("anchor")
    private String anchor;

    /** The original JSON object backing this page, attached by the loader after deserialization. */
    private transient JsonObject raw;

    public String getType() {
        return type;
    }

    public String getFlag() {
        return flag;
    }

    public String getAdvancement() {
        return advancement;
    }

    public String getAnchor() {
        return anchor;
    }

    public JsonObject getRaw() {
        return raw;
    }

    void setRaw(JsonObject raw) {
        this.raw = raw;
    }
}
