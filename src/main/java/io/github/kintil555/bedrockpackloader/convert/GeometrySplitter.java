package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GeckoLib bakes only the first geometry of a file, while Bedrock files routinely hold many. This splits a
 * geometry file into one single-geometry document per identifier and maps unsupported format versions to the
 * newest one GeckoLib understands.
 */
public final class GeometrySplitter {
    private static final int[][] SUPPORTED = {{1, 12, 0}, {1, 14, 0}, {1, 16, 0}, {1, 19, 30}, {1, 21, 0}};

    /** One geometry as a standalone model document. */
    public record Part(String identifier, String formatVersion, byte[] json, boolean versionChanged) {
    }

    private GeometrySplitter() {
    }

    public static List<Part> split(JsonObject root) {
        List<Part> parts = new ArrayList<>();
        if (!root.has("minecraft:geometry") || !root.get("minecraft:geometry").isJsonArray()) {
            return parts;
        }

        String originalVersion = root.has("format_version") ? root.get("format_version").getAsString() : "1.12.0";
        String version = normaliseVersion(originalVersion);

        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        for (JsonElement element : geometries) {
            Optional<String> identifier = identifierOf(element);
            if (identifier.isEmpty()) {
                continue;
            }

            repairBones(element.getAsJsonObject());
            JsonArray single = new JsonArray(1);
            single.add(element);
            JsonObject document = new JsonObject();
            document.addProperty("format_version", version);
            document.add("minecraft:geometry", single);
            parts.add(new Part(identifier.get(), version, document.toString().getBytes(StandardCharsets.UTF_8),
                    !version.equals(originalVersion)));
        }
        return parts;
    }

    /**
     * Makes a geometry loadable by GeckoLib, which is stricter than Bedrock: a bone whose parent is not defined in the
     * same geometry (attachment models extend bones of another model) becomes a top-level bone, and cubes without
     * {@code uv} get the origin of the texture.
     */
    static void repairBones(JsonObject geometry) {
        if (!geometry.has("bones") || !geometry.get("bones").isJsonArray()) {
            return;
        }
        JsonArray bones = geometry.getAsJsonArray("bones");
        // Bedrock matches bone names case-insensitively (packs animate "rightleg" on a "rightLeg" bone); GeckoLib does
        // not, so every geometry and animation uses lower-case names.
        for (JsonElement bone : bones) {
            if (bone.isJsonObject()) {
                for (String key : new String[]{"name", "parent"}) {
                    JsonObject object = bone.getAsJsonObject();
                    if (object.has(key) && object.get(key).isJsonPrimitive()) {
                        object.addProperty(key, object.get(key).getAsString().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }
        Set<String> names = new HashSet<>();
        for (JsonElement bone : bones) {
            if (bone.isJsonObject() && bone.getAsJsonObject().has("name")) {
                names.add(bone.getAsJsonObject().get("name").getAsString());
            }
        }
        for (JsonElement element : bones) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject bone = element.getAsJsonObject();
            if (bone.has("parent") && !names.contains(bone.get("parent").getAsString())) {
                bone.remove("parent");
            }
            if (bone.has("cubes") && bone.get("cubes").isJsonArray()) {
                for (JsonElement cube : bone.getAsJsonArray("cubes")) {
                    if (cube.isJsonObject() && !cube.getAsJsonObject().has("uv")) {
                        JsonArray uv = new JsonArray(2);
                        uv.add(0);
                        uv.add(0);
                        cube.getAsJsonObject().add("uv", uv);
                    }
                }
            }
        }
    }

    private static Optional<String> identifierOf(JsonElement geometry) {
        if (!geometry.isJsonObject() || !geometry.getAsJsonObject().has("description")) {
            return Optional.empty();
        }
        JsonElement description = geometry.getAsJsonObject().get("description");
        if (!description.isJsonObject() || !description.getAsJsonObject().has("identifier")) {
            return Optional.empty();
        }
        return Optional.of(description.getAsJsonObject().get("identifier").getAsString());
    }

    /** Returns the version unchanged when supported, otherwise the highest supported version not above it. */
    public static String normaliseVersion(String version) {
        int[] parsed = parse(version);
        if (parsed == null) {
            return version;
        }

        int[] best = null;
        for (int[] candidate : SUPPORTED) {
            if (compare(candidate, parsed) == 0) {
                return version;
            }
            if (compare(candidate, parsed) < 0) {
                best = candidate;
            }
        }
        return best == null ? version : best[0] + "." + best[1] + "." + best[2];
    }

    private static int[] parse(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts.length == 3 ? Integer.parseInt(parts[2]) : 0};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
