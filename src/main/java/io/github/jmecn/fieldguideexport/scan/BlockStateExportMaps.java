package io.github.jmecn.fieldguideexport.scan;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared JSON shape for {@link BlockStateResolver.Resolved} in export files. */
public final class BlockStateExportMaps {

    private BlockStateExportMaps() {}

    public static Map<String, Object> toMap(BlockStateResolver.Resolved r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", r.ref);
        if (r.override != null) {
            m.put("override", r.override);
        }
        if (r.kind != null && !"block".equals(r.kind)) {
            m.put("kind", r.kind);
        }
        if (r.tag != null) {
            m.put("tag", r.tag);
        }
        if (r.unknownProperties != null) {
            m.put("unknownProperties", r.unknownProperties);
        }
        if (r.invalidProperties != null) {
            m.put("invalidProperties", r.invalidProperties);
        }
        if (r.error != null) {
            m.put("error", r.error);
        }
        return m;
    }
}
