package io.github.jmecn.fieldguideexport.export.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bundle-relative PNG paths for exported entity previews.
 */
public final class EntityRenderPaths {

    private EntityRenderPaths() {}

    /**
     * {@code assets/entities/<namespace>/<path>.png} for plain ids;
     * when NBT is present, {@code assets/entities/<namespace>/<path>/<hash>.png}
     * so variants with different NBT do not overwrite each other.
     */
    public static String relativePngPath(EntityRenderRequest request) {
        String registryId = request.registryId();
        int colon = registryId.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("Invalid entity id: " + request.entity());
        }
        String namespace = registryId.substring(0, colon);
        String path = registryId.substring(colon + 1);
        if (request.entity().indexOf('{') < 0) {
            return "assets/entities/" + namespace + "/" + path + ".png";
        }
        return "assets/entities/" + namespace + "/" + path + "/" + shortHash(request.entity()) + ".png";
    }

    static String shortHash(String entity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(entity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(entity.hashCode());
        }
    }
}
