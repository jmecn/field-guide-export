package io.github.jmecn.fieldguideexport.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a single pack resource into {@code assets/} or {@code data/} mirror layout. */
final class ResourceFileWriter {

    private ResourceFileWriter() {}

    static long write(Path typeRoot, ResourceLocation id, Resource resource) throws IOException {
        Path outFile = typeRoot.resolve(id.getNamespace()).resolve(id.getPath());
        Files.createDirectories(outFile.getParent());
        try (InputStream in = resource.open()) {
            long bytes = Files.copy(in, outFile, StandardCopyOption.REPLACE_EXISTING);
            return bytes > 0 ? bytes : Files.size(outFile);
        }
    }

    static long writeUtf8(Path typeRoot, ResourceLocation id, String content) throws IOException {
        Path outFile = typeRoot.resolve(id.getNamespace()).resolve(id.getPath());
        Files.createDirectories(outFile.getParent());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(outFile, bytes);
        return bytes.length;
    }
}
