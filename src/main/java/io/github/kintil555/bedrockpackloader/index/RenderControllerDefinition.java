package io.github.kintil555.bedrockpackloader.index;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A Bedrock render controller reduced to what the Java renderer can use: the Molang expression that picks the
 * geometry, the one that picks the texture, and the arrays those expressions index. Materials, part visibility and
 * colours are not supported.
 */
public record RenderControllerDefinition(String id, String geometry, @Nullable String texture, Map<String, List<Object>> arrays,
                                         List<PartRule> partVisibility) {
    /** A {@code part_visibility} entry: bone name pattern ({@code *} wildcards) and a Molang condition ({@code "1"}/{@code "0"} for literals). */
    public record PartRule(String pattern, String condition) {
    }

    /** Parses all controllers of a {@code render_controllers} file; entries without geometry are skipped. */
    public static List<RenderControllerDefinition> parseFile(JsonObject root) {
        List<RenderControllerDefinition> result = new ArrayList<>();
        if (!root.has("render_controllers") || !root.get("render_controllers").isJsonObject()) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("render_controllers").entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject controller = entry.getValue().getAsJsonObject();
            if (!controller.has("geometry") || !controller.get("geometry").isJsonPrimitive()) {
                continue;
            }
            String texture = null;
            if (controller.has("textures") && controller.get("textures").isJsonArray()
                    && !controller.getAsJsonArray("textures").isEmpty() && controller.getAsJsonArray("textures").get(0).isJsonPrimitive()) {
                texture = controller.getAsJsonArray("textures").get(0).getAsString();
            }
            result.add(new RenderControllerDefinition(entry.getKey(), controller.get("geometry").getAsString(), texture, arrays(controller),
                    partVisibility(controller)));
        }
        return result;
    }

    private static List<PartRule> partVisibility(JsonObject controller) {
        List<PartRule> rules = new ArrayList<>();
        if (controller.has("part_visibility") && controller.get("part_visibility").isJsonArray()) {
            for (JsonElement element : controller.getAsJsonArray("part_visibility")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> rule : element.getAsJsonObject().entrySet()) {
                    JsonElement value = rule.getValue();
                    if (!value.isJsonPrimitive()) {
                        continue;
                    }
                    String condition = value.getAsJsonPrimitive().isBoolean() ? (value.getAsBoolean() ? "1" : "0") : value.getAsString();
                    rules.add(new PartRule(rule.getKey().toLowerCase(Locale.ROOT), condition));
                }
            }
        }
        return List.copyOf(rules);
    }

    private static Map<String, List<Object>> arrays(JsonObject controller) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        if (!controller.has("arrays") || !controller.get("arrays").isJsonObject()) {
            return result;
        }
        for (Map.Entry<String, JsonElement> group : controller.getAsJsonObject("arrays").entrySet()) {
            if (!group.getValue().isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> array : group.getValue().getAsJsonObject().entrySet()) {
                if (!array.getValue().isJsonArray()) {
                    continue;
                }
                List<Object> values = new ArrayList<>();
                for (JsonElement element : array.getValue().getAsJsonArray()) {
                    values.add(element.isJsonPrimitive() ? element.getAsString().toLowerCase(Locale.ROOT) : "");
                }
                String name = array.getKey().toLowerCase(Locale.ROOT);
                result.put(name.startsWith("array.") ? name.substring("array.".length()) : name, List.copyOf(values));
            }
        }
        return Map.copyOf(result);
    }
}
