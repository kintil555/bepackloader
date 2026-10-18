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
        return !GECKOLIB_FORMAT.matcher(expression).matches() || expression.contains("??") || expression.contains("->");
    }

    /** Sanitises, in place, every bone channel of every animation in an animation file. */
    public void sanitize(JsonObject file) {
        if (!file.has("animations") || !file.get("animations").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> animation : file.getAsJsonObject("animations").entrySet()) {
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

    private void sanitizeBone(JsonObject bone) {
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
                array.set(i, convert(array.get(i)));
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
