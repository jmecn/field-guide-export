package io.github.jmecn.fieldguideexport.export.patchouli;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level Patchouli book metadata loaded from
 * {@code data/<ns>/patchouli_books/<book>/book.json}, plus the categories and entries the
 * loader attaches to it from {@code assets/<ns>/patchouli_books/<book>/<lang>/...}.
 *
 * <p>Only the metadata fields commonly used by TFC's {@code field_guide} are deserialized for
 * now — everything else can be added later without breaking callers (Gson silently ignores
 * unknown JSON fields by default).</p>
 *
 * @see <a href="https://vazkiimods.github.io/Patchouli/docs/reference/book-json/">Book JSON Format</a>
 */
public class Book {

    @SerializedName("name")
    private String name;

    @SerializedName("landing_text")
    private String landingText;

    @SerializedName("subtitle")
    private String subtitle;

    @SerializedName("version")
    private String version;

    @SerializedName("model")
    private String model;

    @SerializedName("index_icon")
    private String indexIcon;

    @SerializedName("creative_tab")
    private String creativeTab;

    @SerializedName("i18n")
    private Boolean i18n;

    @SerializedName("use_resource_pack")
    private Boolean useResourcePack;

    @SerializedName("pamphlet")
    private Boolean pamphlet;

    @SerializedName("macros")
    private Map<String, String> macros;

    /** Book id, e.g. {@code field_guide} (without namespace). Set by the loader. */
    private transient String bookId;

    /** Book namespace, e.g. {@code tfc}. Set by the loader. */
    private transient String namespace;

    /** Language code the entries/categories were loaded for, e.g. {@code en_us}. */
    private transient String language;

    private transient AssetSource assetSource = AssetSource.UNKNOWN;

    private transient final List<BookCategory> categories = new ArrayList<>();
    private transient final Map<String, BookCategory> categoryMap = new LinkedHashMap<>();
    private transient final List<BookEntry> entries = new ArrayList<>();
    private transient final Map<String, BookEntry> entryMap = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public String getLandingText() {
        return landingText;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getVersion() {
        return version;
    }

    public String getModel() {
        return model;
    }

    public String getIndexIcon() {
        return indexIcon;
    }

    public String getCreativeTab() {
        return creativeTab;
    }

    public Boolean getI18n() {
        return i18n;
    }

    public Boolean getUseResourcePack() {
        return useResourcePack;
    }

    public Boolean getPamphlet() {
        return pamphlet;
    }

    public Map<String, String> getMacros() {
        return macros;
    }

    public String getBookId() {
        return bookId;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getLanguage() {
        return language;
    }

    public AssetSource getAssetSource() {
        return assetSource;
    }

    public List<BookCategory> getCategories() {
        return categories;
    }

    public List<BookEntry> getEntries() {
        return entries;
    }

    public BookCategory getCategory(String id) {
        return categoryMap.get(id);
    }

    public BookEntry getEntry(String id) {
        return entryMap.get(id);
    }

    void setBookId(String bookId) {
        this.bookId = bookId;
    }

    void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    void setLanguage(String language) {
        this.language = language;
    }

    void setAssetSource(AssetSource source) {
        this.assetSource = source;
    }

    /** Add a category iff its id is new; returns the live (possibly preexisting) entry. */
    BookCategory addCategory(BookCategory category) {
        BookCategory existing = categoryMap.get(category.getId());
        if (existing != null) {
            return existing;
        }
        categories.add(category);
        categoryMap.put(category.getId(), category);
        return category;
    }

    /**
     * Attach an entry to its category. Skips if the entry id already exists or its category
     * isn't known (caller logs the latter at warn).
     *
     * @return {@code true} if added, {@code false} if skipped
     */
    boolean addEntry(BookEntry entry) {
        if (entryMap.containsKey(entry.getId())) {
            return false;
        }
        BookCategory parent = categoryMap.get(entry.getCategoryId());
        if (parent == null) {
            return false;
        }
        entries.add(entry);
        entryMap.put(entry.getId(), entry);
        parent.addEntry(entry);
        return true;
    }

    /** Stable sort by sortnum then id, for both categories and each category's entries. */
    void sort() {
        categories.sort(BookCategory::compareTo);
        for (BookCategory cat : categories) {
            cat.getEntries().sort(BookEntry::compareTo);
        }
    }
}
