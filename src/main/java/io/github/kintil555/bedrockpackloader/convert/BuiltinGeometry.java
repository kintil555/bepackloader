package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Geometries that Bedrock ships inside the game and that packs reference without defining them
 * ({@code geometry.humanoid.custom} is the default model of {@code minecraft:player}). They use the standard
 * 64x64 skin layout, so the player's own skin texture fits them.
 */
public final class BuiltinGeometry {
    private static final String FORMAT_VERSION = "1.12.0";

    private BuiltinGeometry() {
    }

    /** Geometry identifier to the standalone model document GeckoLib can load. */
    public static Map<String, byte[]> all() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String id : new String[]{"geometry.humanoid", "geometry.humanoid.custom"}) {
            result.put(id, document(humanoid(id, 4)));
        }
        result.put("geometry.humanoid.customSlim", document(humanoid("geometry.humanoid.customSlim", 3)));
        result.put("geometry.cape", document(cape()));
        return result;
    }

    private static byte[] document(JsonObject geometry) {
        JsonArray list = new JsonArray(1);
        list.add(geometry);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);
        root.add("minecraft:geometry", list);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject humanoid(String identifier, int armWidth) {
        JsonArray bones = new JsonArray();
        bones.add(bone("root", null, 0, 0, 0));
        bones.add(bone("waist", "root", 0, 12, 0));
        bones.add(withCube(bone("body", "waist", 0, 24, 0), -4, 12, -2, 8, 12, 4, 16, 16, 0));
        bones.add(withCube(bone("head", "body", 0, 24, 0), -4, 24, -4, 8, 8, 8, 0, 0, 0));
        bones.add(withCube(bone("hat", "head", 0, 24, 0), -4, 24, -4, 8, 8, 8, 32, 0, 0.5));

        double rightArmX = -4 - armWidth;
        bones.add(withCube(bone("rightArm", "body", -5, 22, 0), rightArmX, 12, -2, armWidth, 12, 4, 40, 16, 0));
        bones.add(bone("rightItem", "rightArm", -6, 15, 1));
        bones.add(withCube(bone("leftArm", "body", 5, 22, 0), 4, 12, -2, armWidth, 12, 4, 32, 48, 0));
        bones.add(bone("leftItem", "leftArm", 6, 15, 1));
        bones.add(withCube(bone("rightLeg", "root", -1.9, 12, 0), -3.9, 0, -2, 4, 12, 4, 0, 16, 0));
        bones.add(withCube(bone("leftLeg", "root", 1.9, 12, 0), -0.1, 0, -2, 4, 12, 4, 16, 48, 0));

        bones.add(withCube(bone("jacket", "body", 0, 24, 0), -4, 12, -2, 8, 12, 4, 16, 32, 0.25));
        bones.add(withCube(bone("rightSleeve", "rightArm", -5, 22, 0), rightArmX, 12, -2, armWidth, 12, 4, 40, 32, 0.25));
        bones.add(withCube(bone("leftSleeve", "leftArm", 5, 22, 0), 4, 12, -2, armWidth, 12, 4, 48, 48, 0.25));
        bones.add(withCube(bone("rightPants", "rightLeg", -1.9, 12, 0), -3.9, 0, -2, 4, 12, 4, 0, 32, 0.25));
        bones.add(withCube(bone("leftPants", "leftLeg", 1.9, 12, 0), -0.1, 0, -2, 4, 12, 4, 0, 48, 0.25));
        return geometry(identifier, 64, 64, bones);
    }

    private static JsonObject cape() {
        JsonArray bones = new JsonArray();
        bones.add(withCube(bone("cape", null, 0, 24, 3), -5, 8, 3, 10, 16, 1, 0, 0, 0));
        return geometry("geometry.cape", 64, 32, bones);
    }

    private static JsonObject geometry(String identifier, int textureWidth, int textureHeight, JsonArray bones) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        description.addProperty("visible_bounds_width", 2);
        description.addProperty("visible_bounds_height", 3.5);
        description.add("visible_bounds_offset", vector(0, 1.25, 0));

        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);
        return geometry;
    }

    private static JsonObject bone(String name, String parent, double x, double y, double z) {
        JsonObject bone = new JsonObject();
        bone.addProperty("name", name.toLowerCase(java.util.Locale.ROOT));
        if (parent != null) {
            bone.addProperty("parent", parent.toLowerCase(java.util.Locale.ROOT));
        }
        bone.add("pivot", vector(x, y, z));
        return bone;
    }

    private static JsonObject withCube(JsonObject bone, double ox, double oy, double oz, double sx, double sy, double sz,
                                       int u, int v, double inflate) {
        JsonObject cube = new JsonObject();
        cube.add("origin", vector(ox, oy, oz));
        cube.add("size", vector(sx, sy, sz));
        JsonArray uv = new JsonArray(2);
        uv.add(u);
        uv.add(v);
        cube.add("uv", uv);
        if (inflate != 0) {
            cube.addProperty("inflate", inflate);
        }
        JsonArray cubes = new JsonArray(1);
        cubes.add(cube);
        bone.add("cubes", cubes);
        return bone;
    }

    private static JsonArray vector(double x, double y, double z) {
        JsonArray array = new JsonArray(3);
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
