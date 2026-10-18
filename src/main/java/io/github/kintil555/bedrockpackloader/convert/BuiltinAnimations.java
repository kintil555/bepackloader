package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Player animations that Bedrock ships inside the game and that packs reference without defining them. Only poses with
 * a fixed value are provided: they are the same constant offsets the Java player model applies.
 */
public final class BuiltinAnimations {
    /** Name of the GeckoLib animation file (without extension) these animations are stored in. */
    public static final String FILE = "builtin_player";

    private static final List<String> ARMS = List.of("rightArm", "rightarm", "leftArm", "leftarm");
    private static final List<String> LEGS = List.of("rightLeg", "rightleg", "leftLeg", "leftleg");

    private BuiltinAnimations() {
    }

    /** Identifiers of the animations in {@link #json(java.util.function.Predicate)}. */
    public static List<String> identifiers() {
        return List.of("animation.player.riding.arms", "animation.player.riding.legs",
                "animation.player.sleeping", "animation.player.look_at_target.ui");
    }

    /** The animation file holding every built-in animation for which {@code wanted} returns true; null when none is wanted. */
    public static byte[] json(java.util.function.Predicate<String> wanted) {
        JsonObject animations = new JsonObject();
        for (String id : identifiers()) {
            if (wanted.test(id)) {
                animations.add(id, animation(id));
            }
        }
        if (animations.size() == 0) {
            return null;
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", animations);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject animation(String id) {
        JsonObject bones = new JsonObject();
        switch (id) {
            case "animation.player.riding.arms" -> {
                for (String arm : ARMS) {
                    bones.add(arm, rotation(-36, 0, 0));
                }
            }
            case "animation.player.riding.legs" -> {
                for (String leg : LEGS) {
                    boolean right = leg.startsWith("right");
                    bones.add(leg, rotation(-81, right ? 18 : -18, right ? 4.5 : -4.5));
                }
            }
            default -> {
                // Sleeping and the UI look-at pose leave the model in its rest pose.
            }
        }

        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.addProperty("animation_length", 1);
        animation.add("bones", bones);
        return animation;
    }

    private static JsonObject rotation(double x, double y, double z) {
        JsonArray vector = new JsonArray(3);
        vector.add(x);
        vector.add(y);
        vector.add(z);
        JsonObject bone = new JsonObject();
        bone.add("rotation", vector);
        return bone;
    }
}
