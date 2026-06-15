package io.github.jmecn.fieldguideexport.entity;

import io.github.jmecn.fieldguideexport.resources.EntityPreviewExporter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EntityRenderMaps {

    private EntityRenderMaps() {}

    public static Map<String, Object> toRenderEntry(EntityPreviewExporter.RenderedEntity rendered) {
        Map<String, Object> row = new LinkedHashMap<>();
        EntityRenderRequest req = rendered.request();
        row.put("scale", req.scale());
        row.put("offset", req.offset());
        row.put("defaultRotation", req.defaultRotation());
        row.put("path", rendered.path());
        row.put("width", rendered.width());
        row.put("height", rendered.height());
        return row;
    }

    public static Map<String, Object> toMissingMap(EntityRenderRequest request, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entity", request.entity());
        row.put("scale", request.scale());
        row.put("offset", request.offset());
        row.put("defaultRotation", request.defaultRotation());
        row.put("error", error);
        return row;
    }
}
