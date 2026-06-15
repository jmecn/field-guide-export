package io.github.jmecn.fieldguideexport.patchouli;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BookEntry implements Comparable<BookEntry> {

    @SerializedName("name")
    private String name = "";

    @SerializedName("category")
    private String category = "";

    @SerializedName("icon")
    private String icon = "";

    @SerializedName("pages")
    private List<BookPage> pages = new ArrayList<>();

    @SerializedName("advancement")
    private String advancement = "";

    @SerializedName("flag")
    private String flag = "";

    @SerializedName("priority")
    private Boolean priority = false;

    @SerializedName("secret")
    private Boolean secret = false;

    @SerializedName("sortnum")
    private int sort = 0;

    @SerializedName("turnin")
    private String turnin;

    @SerializedName("extra_recipe_mappings")
    private Map<String, Integer> extraRecipeMappings;

    @SerializedName("entry_color")
    private String entryColor;

    private transient String id;
    
    private transient String relId;
    
    private transient String categoryId;

    private transient AssetSource assetSource = AssetSource.UNKNOWN;

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public List<BookPage> getPages() {
        return pages;
    }

    public String getAdvancement() {
        return advancement;
    }

    public String getFlag() {
        return flag;
    }

    public Boolean getPriority() {
        return priority;
    }

    public Boolean getSecret() {
        return secret;
    }

    public int getSort() {
        return sort;
    }

    public String getTurnin() {
        return turnin;
    }

    public Map<String, Integer> getExtraRecipeMappings() {
        return extraRecipeMappings;
    }

    public String getEntryColor() {
        return entryColor;
    }

    public String getId() {
        return id;
    }

    public String getRelId() {
        return relId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public AssetSource getAssetSource() {
        return assetSource;
    }

    void setAssetSource(AssetSource source) {
        this.assetSource = source;
    }

    void setIdsFromPath(String entryPathInsideEntriesDir) {
        this.id = entryPathInsideEntriesDir;
        int slash = entryPathInsideEntriesDir.indexOf('/');
        this.relId = slash >= 0 ? entryPathInsideEntriesDir.substring(slash + 1) : entryPathInsideEntriesDir;

        int colon = this.category.indexOf(':');
        this.categoryId = colon > 0 ? this.category.substring(colon + 1) : this.category;
    }

    void ensurePagesNonNull() {
        if (pages == null) {
            pages = new ArrayList<>();
        }
    }

    @Override
    public String toString() {
        return id + "@" + assetSource;
    }

    @Override
    public int compareTo(BookEntry other) {
        if (this.sort != other.sort) {
            return Integer.compare(this.sort, other.sort);
        }
        return this.id.compareTo(other.id);
    }
}
