package io.github.jmecn.fieldguideexport.export.patchouli;

/**
 * Where a particular Patchouli JSON came from inside Forge's ResourceManager.
 *
 * <p>In a loaded modpack a single resource location like {@code tfc:patchouli_books/field_guide/en_us/categories/getting_started}
 * may be provided by multiple {@code PackResources} (the TFC mod jar, the modpack's
 * {@code kubejs/assets/} folder, a Mod Director download, an applied resource pack, ...).
 * ResourceManager already picks the highest-priority one for us; this record just keeps the
 * pack id so we can log overrides and trace surprising values back to their source.</p>
 *
 * <p>The {@code sourceId} is what {@code Resource#sourcePackId()} returns — typically the file
 * name of the pack ({@code mod/tfc}, {@code file/Modpack-Modern}, {@code kubejs}, ...).</p>
 */
public record AssetSource(String sourceId) {

    public static final AssetSource UNKNOWN = new AssetSource("<unknown>");

    @Override
    public String toString() {
        return sourceId;
    }
}
