package io.github.jmecn.fieldguideexport.export.patchouli;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * One Patchouli category, loaded from
 * {@code assets/<ns>/patchouli_books/<book>/<lang>/categories/<id>.json}.
 *
 * <p>The {@link #id} field is path-derived (not part of the JSON itself); the loader fills
 * it after deserialization.</p>
 */
public class BookCategory implements Comparable<BookCategory> {

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("icon")
    private String icon;

    @SerializedName("parent")
    private String parent;

    @SerializedName("flag")
    private String flag;

    @SerializedName("sortnum")
    private int sort = 0;

    @SerializedName("secret")
    private boolean secret = false;

    /** Path-derived id (no namespace, no extension), e.g. {@code getting_started}. */
    private transient String id;
    private transient AssetSource assetSource = AssetSource.UNKNOWN;
    private transient final List<BookEntry> entries = new ArrayList<>();

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public String getParent() {
        return parent;
    }

    public String getFlag() {
        return flag;
    }

    public int getSort() {
        return sort;
    }

    public boolean isSecret() {
        return secret;
    }

    public String getId() {
        return id;
    }

    public AssetSource getAssetSource() {
        return assetSource;
    }

    public List<BookEntry> getEntries() {
        return entries;
    }

    void setId(String id) {
        this.id = id;
    }

    void setAssetSource(AssetSource source) {
        this.assetSource = source;
    }

    void addEntry(BookEntry entry) {
        entries.add(entry);
    }

    @Override
    public String toString() {
        return id + "@" + assetSource;
    }

    @Override
    public int compareTo(BookCategory other) {
        if (this.sort != other.sort) {
            return Integer.compare(this.sort, other.sort);
        }
        return this.id.compareTo(other.id);
    }
}
