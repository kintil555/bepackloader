package io.github.kintil555.bedrockpackloader.index;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The client-side description of an entity ({@code minecraft:client_entity}): which geometry, textures and
 * animations it uses and which animations the scripts run each frame.
 */
public record ClientEntityDefinition(
        String identifier,
        Map<String, String> geometry,
        Map<String, String> textures,
        Map<String, String> animations,
        List<RenderControllerRef> renderControllers,
        List<Animate> animate,
        List<String> preAnimation,
        List<String> initialize,
        @Nullable String scale,
        boolean enableAttachables
) {
    /** An entry of {@code scripts.animate}: an animation or controller short name, optionally gated by a Molang condition. */
    public record Animate(String name, @Nullable String condition) {
    }

    /** An entry of {@code render_controllers}, optionally gated by a Molang condition. */
    public record RenderControllerRef(String id, @Nullable String condition) {
    }

    /**
     * Parses a client entity file.
     *
     * @return the definition, or {@code null} if the file contains no {@code minecraft:client_entity}
     */
    public static @Nullable ClientEntityDefinition parse(JsonObject root) {
        JsonElement entity = root.get("minecraft:client_entity");
        if (entity == null || !entity.isJsonObject() || !entity.getAsJsonObject().has("description")) {
            return null;
        }

        JsonObject description = entity.getAsJsonObject().getAsJsonObject("description");
        if (!description.has("identifier")) {
            return null;
        }

        JsonObject scripts = description.has("scripts") && description.get("scripts").isJsonObject()
                ? description.getAsJsonObject("scripts") : new JsonObject();

        return new ClientEntityDefinition(
                description.get("identifier").getAsString(),
                stringMap(description, "geometry"),
                stringMap(description, "textures"),
                stringMap(description, "animations"),
                renderControllers(description),
                animate(scripts),
                stringList(scripts, "pre_animation"),
                stringList(scripts, "initialize"),
                scripts.has("scale") && scripts.get("scale").isJsonPrimitive() ? scripts.get("scale").getAsString() : null,
                description.has("enable_attachables") && description.get("enable_attachables").isJsonPrimitive()
                        && description.get("enable_attachables").getAsBoolean());
    }

    private static Map<String, String> stringMap(JsonObject parent, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : parent.getAsJsonObject(key).entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> stringList(JsonObject parent, String key) {
        List<String> result = new ArrayList<>();
        if (parent.has(key) && parent.get(key).isJsonArray()) {
            for (JsonElement element : parent.getAsJsonArray(key)) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Animate> animate(JsonObject scripts) {
        List<Animate> result = new ArrayList<>();
        if (scripts.has("animate") && scripts.get("animate").isJsonArray()) {
            for (JsonElement element : scripts.getAsJsonArray("animate")) {
                if (element.isJsonPrimitive()) {
                    result.add(new Animate(element.getAsString(), null));
                } else if (element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        result.add(new Animate(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<RenderControllerRef> renderControllers(JsonObject description) {
        List<RenderControllerRef> result = new ArrayList<>();
        if (description.has("render_controllers") && description.get("render_controllers").isJsonArray()) {
            for (JsonElement element : description.getAsJsonArray("render_controllers")) {
                if (element.isJsonPrimitive()) {
                    result.add(new RenderControllerRef(element.getAsString(), null));
                } else if (element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        result.add(new RenderControllerRef(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
