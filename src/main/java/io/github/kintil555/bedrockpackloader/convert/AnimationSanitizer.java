package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Rewrites Molang inside bone keyframes that GeckoLib's own parser cannot compile.
 *
 * <p>GeckoLib compiles every keyframe string while baking an animation file and throws on the first one it
 * rejects (characters such as {@code '}, {@code [} or {@code ]}), which drops the whole file. Each such
 * expression is replaced by a variable reference ({@code variable.bpl_N}); the caller decides what the variable
 * evaluates to through {@code variableFor}.</p>
 */
public final class AnimationSanitizer {
    /** The character set GeckoLib accepts in an expression ({@code MathParser.EXPRESSION_FORMAT}). */
    private static final Pattern GECKOLIB_FORMAT = Pattern.compile("^[\\w\\s_+\\-/*%^&|<>=!?:;.,(){}]+$");
    private static final Pattern MOD_CALL = Pattern.compile("\\bmod\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final List<String> CHANNELS = List.of("rotation", "position", "scale");
    private static final Set<String> VALUE_KEYS = Set.of("pre", "post", "vector", "easing_args");

    private final Function<String, String> variableFor;
    private int replaced;

    /** @param variableFor maps a Molang expression to the name of a GeckoLib variable that evaluates it */
    public AnimationSanitizer(Function<String, String> variableFor) {
        this.variableFor = variableFor;
    }

    /** Number of expressions replaced so far. */
    public int replaced() {
        return this.replaced;
    }

    /** True when GeckoLib would not compile {@code expression} correctly. */
    public static boolean needsRewrite(String expression) {
        // GeckoLib 5.5.5 builds math.mod(a, b) with a null modulus and throws, which drops the whole animation file.
        return !GECKOLIB_FORMAT.matcher(expression).matches() || expression.contains("??") || expression.contains("->")
                || MOD_CALL.matcher(expression).find();
    }

    /** Sanitises, in place, every bone channel of every animation in an animation file. */
    public void sanitize(JsonObject file) {
        if (!file.has("animations") || !file.get("animations").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> animation : file.getAsJsonObject("animations").entrySet()) {
            if (animation.getValue().isJsonObject()) {
                sanitizeEffects(animation.getValue().getAsJsonObject());
            }
            if (!animation.getValue().isJsonObject() || !animation.getValue().getAsJsonObject().has("bones")
                    || !animation.getValue().getAsJsonObject().get("bones").isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> bone : animation.getValue().getAsJsonObject().getAsJsonObject("bones").entrySet()) {
                if (bone.getValue().isJsonObject()) {
                    sanitizeBone(bone.getValue().getAsJsonObject());
                }
            }
        }
    }

    /**
     * Bedrock allows several effects, or several timeline statements, at one timestamp (a JSON array); GeckoLib only
     * accepts one per timestamp and drops the whole file otherwise. Extra effects move to a slightly later timestamp,
     * extra statements are joined with {@code ;}.
     */
    private void sanitizeEffects(JsonObject animation) {
        for (String key : List.of("particle_effects", "sound_effects")) {
            if (!animation.has(key) || !animation.get(key).isJsonObject()) {
                continue;
            }
            JsonObject effects = animation.getAsJsonObject(key);
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : effects.entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    result.add(entry.getKey(), entry.getValue());
                    continue;
                }
                this.replaced++;
                double time = parseTime(entry.getKey());
                int index = 0;
                for (JsonElement effect : entry.getValue().getAsJsonArray()) {
                    String name = index == 0 ? entry.getKey() : Double.toString(Math.round((time + index * 0.001) * 10000.0) / 10000.0);
                    if (effect.isJsonObject() && !result.has(name)) {
                        result.add(name, effect);
                        index++;
                    }
                }
            }
            animation.add(key, result);
        }

        if (animation.has("timeline") && animation.get("timeline").isJsonObject()) {
            JsonObject timeline = animation.getAsJsonObject("timeline");
            for (Map.Entry<String, JsonElement> entry : timeline.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    StringBuilder joined = new StringBuilder();
                    for (JsonElement statement : entry.getValue().getAsJsonArray()) {
                        if (statement.isJsonPrimitive()) {
                            joined.append(statement.getAsString().strip());
                            if (!joined.toString().endsWith(";")) {
                                joined.append(';');
                            }
                        }
                    }
                    timeline.addProperty(entry.getKey(), joined.toString());
                    this.replaced++;
                }
            }
        }
    }

    private static double parseTime(String key) {
        try {
            return Double.parseDouble(key);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private void sanitizeBone(JsonObject bone) {
        // Bedrock 1.10+ writes "relative_to": {"rotation": "entity"}; GeckoLib only accepts a string and drops the file otherwise.
        JsonElement relativeTo = bone.get("relative_to");
        if (relativeTo != null && !relativeTo.isJsonPrimitive()) {
            String value = null;
            if (relativeTo.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : relativeTo.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        value = entry.getValue().getAsString();
                        break;
                    }
                }
            }
            if (value == null) {
                bone.remove("relative_to");
            } else {
                bone.addProperty("relative_to", value);
            }
            this.replaced++;
        }
        for (String channel : CHANNELS) {
            JsonElement value = bone.get(channel);
            if (value == null) {
                continue;
            }
            if (value.isJsonObject() && !hasValueKey(value.getAsJsonObject())) {
                // A map of timestamp to keyframe.
                JsonObject keyframes = value.getAsJsonObject();
                for (Map.Entry<String, JsonElement> keyframe : keyframes.entrySet()) {
                    keyframes.add(keyframe.getKey(), convert(keyframe.getValue()));
                }
            } else {
                bone.add(channel, convert(value));
            }
        }
    }

    private static boolean hasValueKey(JsonObject object) {
        for (String key : VALUE_KEYS) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    private JsonElement convert(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String expression = element.getAsString();
            if (!expression.isBlank() && needsRewrite(expression)) {
                this.replaced++;
                return new JsonPrimitive(this.variableFor.apply(expression));
            }
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                // A vector component written as an object (e.g. {"y": "180-this"}) is invalid in Bedrock and makes
                // GeckoLib drop the whole file; it evaluates to 0.
                array.set(i, array.get(i).isJsonObject() ? new JsonPrimitive(0) : convert(array.get(i)));
            }
            return array;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : VALUE_KEYS) {
                if (object.has(key)) {
                    object.add(key, convert(object.get(key)));
                }
            }
        }
        return element;
    }
}
