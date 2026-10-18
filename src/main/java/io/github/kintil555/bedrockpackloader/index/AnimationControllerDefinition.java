package io.github.kintil555.bedrockpackloader.index;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A Bedrock animation controller: a state machine whose states play animations and whose transitions are
 * guarded by Molang expressions.
 */
public record AnimationControllerDefinition(String id, String initialState, Map<String, State> states) {
    /** One state of the machine. {@code blendTransition} is the cross-fade duration in seconds. */
    public record State(
            List<StateAnimation> animations,
            List<Transition> transitions,
            float blendTransition,
            boolean blendViaShortestPath,
            List<String> onEntry,
            List<String> onExit
    ) {
    }

    /** An animation played by a state, with an optional Molang expression giving its blend weight. */
    public record StateAnimation(String name, @Nullable String blendExpression) {
    }

    /** A transition to {@code target} taken when the Molang {@code condition} is truthy. */
    public record Transition(String target, String condition) {
    }

    /** Parses all controllers of a {@code animation_controllers} file. */
    public static List<AnimationControllerDefinition> parseFile(JsonObject root) {
        List<AnimationControllerDefinition> result = new ArrayList<>();
        if (!root.has("animation_controllers") || !root.get("animation_controllers").isJsonObject()) {
            return result;
        }

        for (Map.Entry<String, JsonElement> controller : root.getAsJsonObject("animation_controllers").entrySet()) {
            if (controller.getValue().isJsonObject()) {
                result.add(parseController(controller.getKey(), controller.getValue().getAsJsonObject()));
            }
        }
        return result;
    }

    private static AnimationControllerDefinition parseController(String id, JsonObject controller) {
        Map<String, State> states = new LinkedHashMap<>();
        if (controller.has("states") && controller.get("states").isJsonObject()) {
            for (Map.Entry<String, JsonElement> state : controller.getAsJsonObject("states").entrySet()) {
                if (state.getValue().isJsonObject()) {
                    states.put(state.getKey(), parseState(state.getValue().getAsJsonObject()));
                }
            }
        }

        String initial = controller.has("initial_state") && controller.get("initial_state").isJsonPrimitive()
                ? controller.get("initial_state").getAsString() : "default";
        return new AnimationControllerDefinition(id, initial, Map.copyOf(states));
    }

    private static State parseState(JsonObject state) {
        List<StateAnimation> animations = new ArrayList<>();
        if (state.has("animations") && state.get("animations").isJsonArray()) {
            for (JsonElement element : state.getAsJsonArray("animations")) {
                if (element.isJsonPrimitive()) {
                    animations.add(new StateAnimation(element.getAsString(), null));
                } else if (element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        animations.add(new StateAnimation(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
        }

        List<Transition> transitions = new ArrayList<>();
        if (state.has("transitions") && state.get("transitions").isJsonArray()) {
            for (JsonElement element : state.getAsJsonArray("transitions")) {
                if (element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        transitions.add(new Transition(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
        }

        return new State(
                List.copyOf(animations),
                List.copyOf(transitions),
                blendTransition(state.get("blend_transition")),
                state.has("blend_via_shortest_path") && state.get("blend_via_shortest_path").getAsBoolean(),
                stringList(state, "on_entry"),
                stringList(state, "on_exit"));
    }

    /** {@code blend_transition} is a duration, or a curve of time -> weight whose last key is the duration. */
    private static float blendTransition(@Nullable JsonElement element) {
        if (element == null) {
            return 0.0F;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsFloat();
        }

        float duration = 0.0F;
        if (element.isJsonObject()) {
            for (String key : element.getAsJsonObject().keySet()) {
                try {
                    duration = Math.max(duration, Float.parseFloat(key));
                } catch (NumberFormatException ignored) {
                    // Non-numeric keys are not part of the curve.
                }
            }
        }
        return duration;
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
}
