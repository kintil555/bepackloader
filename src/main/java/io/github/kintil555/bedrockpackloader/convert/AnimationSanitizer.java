package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
    /**
     * GeckoLib answers these queries by casting its animatable to {@code Entity}; ours is a shared singleton, so
     * evaluating one throws a ClassCastException on the render thread. Expressions using them are routed through the
     * own evaluator, which reads the real entity.
     */
    private static final Pattern ENTITY_QUERY = Pattern.compile("\\b(?:q|query)\\.(?:main_hand_item_max_duration|main_hand_item_use_duration|is_in_contact_with_water|item_max_use_duration|rider_body_x_rotation|rider_body_y_rotation|rider_head_x_rotation|rider_head_y_rotation|distance_from_camera|remaining_durability|is_in_water_or_rain|cardinal_facing_2d|invulnerable_ticks|movement_direction|get_actor_info_id|limb_swing_amount|has_player_rider|is_wall_climbing|body_x_rotation|body_y_rotation|cardinal_facing|equipment_count|head_x_rotation|head_y_rotation|is_fire_immune|max_durability|sleep_rotation|vertical_speed|has_collision|has_head_gear|is_using_item|ground_speed|is_breathing|is_enchanted|is_invisible|is_on_ground|is_sprinting|is_stackable|block_state|death_ticks|has_gravity|is_in_water|is_sleeping|is_sneaking|is_swimming|is_in_lava|is_leashed|is_on_fire|is_saddled|limb_swing|max_health|can_climb|has_owner|has_rider|hurt_time|is_moving|is_riding|is_silent|yaw_speed|blocking|can_swim|can_walk|is_alive|is_angry|can_fly|is_baby|health|scale)\\b", Pattern.CASE_INSENSITIVE);
    /** {@code this} (the channel's current value) is not understood by GeckoLib's parser. */
    private static final Pattern THIS_KEYWORD = Pattern.compile("\\bthis\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_CALL = Pattern.compile("\\bmod\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final List<String> CHANNELS = List.of("rotation", "position", "scale");
    private static final Set<String> VALUE_KEYS = Set.of("pre", "post", "vector", "easing_args");

    private final Function<String, String> variableFor;
    private final Predicate<String> rejected;
    private int replaced;

    /** @param variableFor maps a Molang expression to the name of a GeckoLib variable that evaluates it */
    public AnimationSanitizer(Function<String, String> variableFor) {
        this(variableFor, expression -> false);
    }

    /**
     * @param rejected extra check, typically GeckoLib's own parser, for expressions {@link #needsRewrite} cannot
     *                 recognise statically; results are cached per expression
     */
    public AnimationSanitizer(Function<String, String> variableFor, Predicate<String> rejected) {
        this.variableFor = variableFor;
        Map<String, Boolean> cache = new HashMap<>();
        this.rejected = expression -> cache.computeIfAbsent(expression, rejected::test);
    }

    /** Number of expressions replaced so far. */
    public int replaced() {
        return this.replaced;
    }

    /** True when GeckoLib would not compile {@code expression} correctly. */
    public static boolean needsRewrite(String expression) {
        // GeckoLib 5.5.5 builds math.mod(a, b) with a null modulus and throws, which drops the whole animation file.
        return !GECKOLIB_FORMAT.matcher(expression).matches() || expression.contains("??") || expression.contains("->")
                || MOD_CALL.matcher(expression).find() || THIS_KEYWORD.matcher(expression).find()
                || ENTITY_QUERY.matcher(expression).find();
    }

    /** Sanitises, in place, every bone channel of every animation in an animation file. */
    public void sanitize(JsonObject file) {
        if (!file.has("animations") || !file.get("animations").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> animation : file.getAsJsonObject("animations").entrySet()) {
            if (animation.getValue().isJsonObject()) {
                sanitizeEffects(animation.getValue().getAsJsonObject());
                sanitizeAnimationLevel(animation.getValue().getAsJsonObject());
            }
            if (!animation.getValue().isJsonObject() || !animation.getValue().getAsJsonObject().has("bones")
                    || !animation.getValue().getAsJsonObject().get("bones").isJsonObject()) {
                continue;
            }
            lowerCaseBones(animation.getValue().getAsJsonObject());
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

    /**
     * {@code anim_time_update} and {@code blend_weight} are evaluated by GeckoLib too. A blend weight GeckoLib cannot
     * evaluate goes through the own evaluator; an unusable time update is dropped (the animation then plays at its
     * normal speed), because the own evaluator cannot see GeckoLib's animation clock.
     */
    private void sanitizeAnimationLevel(JsonObject animation) {
        JsonElement timeUpdate = animation.get("anim_time_update");
        if (timeUpdate != null && timeUpdate.isJsonPrimitive() && timeUpdate.getAsJsonPrimitive().isString()
                && (needsRewrite(timeUpdate.getAsString()) || this.rejected.test(timeUpdate.getAsString()))) {
            animation.remove("anim_time_update");
            this.replaced++;
        }
        JsonElement blend = animation.get("blend_weight");
        if (blend != null && blend.isJsonPrimitive() && blend.getAsJsonPrimitive().isString()
                && (needsRewrite(blend.getAsString()) || this.rejected.test(blend.getAsString()))) {
            animation.addProperty("blend_weight", this.variableFor.apply(blend.getAsString()));
            this.replaced++;
        }
    }

    /** Bone names are case-insensitive in Bedrock; geometries are written in lower case, so animations must be too. */
    private void lowerCaseBones(JsonObject animation) {
        JsonObject bones = animation.getAsJsonObject("bones");
        boolean changed = false;
        for (String name : bones.keySet()) {
            if (!name.equals(name.toLowerCase(java.util.Locale.ROOT))) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return;
        }
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
            String lower = bone.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!result.has(lower)) {
                result.add(lower, bone.getValue());
            }
        }
        animation.add("bones", result);
        this.replaced++;
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
            if (!expression.isBlank() && (needsRewrite(expression) || this.rejected.test(expression))) {
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
