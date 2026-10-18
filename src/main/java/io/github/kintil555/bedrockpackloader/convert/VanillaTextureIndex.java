package io.github.kintil555.bedrockpackloader.convert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The texture files shipped with Minecraft 26.2 (paths relative to {@code assets/minecraft/textures/}) and their
 * pixel sizes. Bedrock textures are only emitted when a matching vanilla texture exists <em>and</em> has a
 * compatible layout, so the generated pack never contains orphaned or mis-mapped files.
 */
public final class VanillaTextureIndex {
    private static final String RESOURCE = "/assets/bedrockpackloader/mappings/vanilla_textures.txt";

    /** Pixel size of a texture. */
    public record Size(int width, int height) {
    }

    private final Map<String, Size> sizes;
    private final Map<String, List<String>> entityByFileName;

    public VanillaTextureIndex(Map<String, Size> sizes) {
        this.sizes = Map.copyOf(sizes);

        Map<String, List<String>> byFileName = new HashMap<>();
        for (String path : this.sizes.keySet()) {
            if (path.startsWith("entity/")) {
                String file = path.substring(path.lastIndexOf('/') + 1);
                byFileName.computeIfAbsent(file, key -> new ArrayList<>()).add(path);
            }
        }
        this.entityByFileName = byFileName;
    }

    /** Loads the index bundled with the mod. Each line is {@code path width height}. */
    public static VanillaTextureIndex loadBundled() {
        try (InputStream stream = VanillaTextureIndex.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled resource " + RESOURCE);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static VanillaTextureIndex parse(BufferedReader reader) throws IOException {
        Map<String, Size> loaded = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.strip().split(" ");
            if (parts.length != 3) {
                throw new IOException("Malformed vanilla texture line: " + line);
            }
            loaded.put(parts[0], new Size(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        return new VanillaTextureIndex(loaded);
    }

    /** @param path path below {@code textures/}, including the {@code .png} extension */
    public boolean contains(String path) {
        return this.sizes.containsKey(path);
    }

    public Size sizeOf(String path) {
        return this.sizes.get(path);
    }

    /** Entity textures whose file name equals {@code fileName}, e.g. {@code creeper.png}. */
    public List<String> entityTexturesNamed(String fileName) {
        return this.entityByFileName.getOrDefault(fileName, List.of());
    }

    /**
     * Whether a texture of the given size can replace the vanilla texture {@code path} without breaking UV layouts.
     * That holds when it is the same size, a uniform integer upscale of it, or - for animated strips of square
     * frames - an integer upscale of the frame size.
     */
    public boolean isCompatible(String path, int width, int height) {
        Size vanilla = this.sizes.get(path);
        if (vanilla == null || width <= 0 || height <= 0) {
            return false;
        }
        if (width % vanilla.width() != 0) {
            return false;
        }
        int scale = width / vanilla.width();
        if (height == vanilla.height() * scale) {
            return true;
        }
        boolean vanillaStrip = vanilla.height() > vanilla.width() && vanilla.height() % vanilla.width() == 0;
        boolean bedrockStrip = height >= width && height % width == 0;
        return vanillaStrip && bedrockStrip;
    }

    public int size() {
        return this.sizes.size();
    }
}
