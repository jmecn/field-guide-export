package io.github.jmecn.fieldguideexport.patchouli;

public record AssetSource(String sourceId) {

    public static final AssetSource UNKNOWN = new AssetSource("<unknown>");

    @Override
    public String toString() {
        return sourceId;
    }
}
