package io.github.jmecn.fieldguideexport.patchouli;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class BookPage {

    @SerializedName("type")
    private String type;

    @SerializedName("flag")
    private String flag;

    @SerializedName("advancement")
    private String advancement;

    @SerializedName("anchor")
    private String anchor;

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
