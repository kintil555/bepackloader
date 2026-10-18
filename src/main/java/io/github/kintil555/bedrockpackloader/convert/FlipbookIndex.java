package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.kintil555.bedrockpackloader.util.JsonIO;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Bedrock animated textures ({@code textures/flipbook_textures.json}) and their Java {@code .mcmeta} equivalents.
 * Both formats stack the frames vertically in one image, so only the metadata has to be translated.
 */
public final class FlipbookIndex {
    private final Map<String, JsonObject> mcmetaByTexture;

    private FlipbookIndex(Map<String, JsonObject> mcmetaByTexture) {
        this.mcmetaByTexture = mcmetaByTexture;
    }

    public static FlipbookIndex empty() {
        return new FlipbookIndex(Map.of());
    }

    /** Parses the file; keys are Bedrock texture paths without extension ({@code textures/blocks/lava_still}). */
    public static FlipbookIndex parse(byte[] json) throws IOException {
        JsonElement root = JsonIO.parse(json);
        if (!root.isJsonArray()) {
            throw new IOException("flipbook_textures.json must be a JSON array");
        }

        Map<String, JsonObject> result = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("flipbook_texture") || !entry.has("ticks_per_frame")) {
                continue;
            }

            JsonObject animation = new JsonObject();
            animation.addProperty("frametime", Math.max(1, entry.get("ticks_per_frame").getAsInt()));
            // Bedrock blends frames unless told otherwise.
            animation.addProperty("interpolate", !entry.has("blend_frames") || entry.get("blend_frames").getAsBoolean());

            if (entry.has("frames") && entry.get("frames").isJsonArray()) {
                JsonArray frames = new JsonArray();
                for (JsonElement frame : entry.getAsJsonArray("frames")) {
                    frames.add(frame);
                }
                animation.add("frames", frames);
            }

            JsonObject mcmeta = new JsonObject();
            mcmeta.add("animation", animation);
            result.put(entry.get("flipbook_texture").getAsString(), mcmeta);
        }
        return new FlipbookIndex(result);
    }

    /** The {@code .mcmeta} contents for a Bedrock texture path (extension ignored), or {@code null} if it is not animated. */
    public @Nullable String mcmetaFor(String bedrockTexturePath) {
        JsonObject mcmeta = this.mcmetaByTexture.get(io.github.kintil555.bedrockpackloader.util.PathNames.stripExtension(bedrockTexturePath));
        return mcmeta == null ? null : mcmeta.toString();
    }

    public int size() {
        return this.mcmetaByTexture.size();
    }
}
