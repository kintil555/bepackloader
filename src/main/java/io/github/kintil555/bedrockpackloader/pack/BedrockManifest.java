package io.github.kintil555.bedrockpackloader.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.kintil555.bedrockpackloader.util.JsonIO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The parts of a Bedrock {@code manifest.json} this mod cares about. */
public record BedrockManifest(
        String name,
        String description,
        String uuid,
        String version,
        boolean resourcePack,
        List<Subpack> subpacks,
        List<Setting> settings
) {
    /** A selectable variant of the pack, stored in {@code subpacks/<folderName>/}. */
    public record Subpack(String folderName, String name) {
    }

    /** A pack setting ({@code settings} in the manifest) with its default value as text: {@code true}/{@code false} for toggles, the option name for dropdowns. */
    public record Setting(String name, String defaultValue) {
    }

    public static BedrockManifest parse(byte[] json) throws IOException {
        JsonObject root = JsonIO.parseObject(json);
        if (root == null) {
            throw new IOException("manifest.json is not a JSON object");
        }

        JsonObject header = object(root, "header");
        if (header == null) {
            throw new IOException("manifest.json has no header");
        }

        String name = string(header, "name", "Bedrock pack");
        String description = string(header, "description", "");
        String uuid = string(header, "uuid", "");
        String version = versionString(header.get("version"));

        boolean resourcePack = false;
        JsonArray modules = root.has("modules") && root.get("modules").isJsonArray() ? root.getAsJsonArray("modules") : new JsonArray();
        for (JsonElement module : modules) {
            if (module.isJsonObject() && "resources".equals(string(module.getAsJsonObject(), "type", ""))) {
                resourcePack = true;
            }
        }

        List<Subpack> subpacks = new ArrayList<>();
        if (root.has("subpacks") && root.get("subpacks").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("subpacks")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject subpack = element.getAsJsonObject();
                String folder = string(subpack, "folder_name", "");
                if (!folder.isEmpty()) {
                    subpacks.add(new Subpack(folder, string(subpack, "name", folder)));
                }
            }
        }

        List<Setting> settings = new ArrayList<>();
        if (root.has("settings") && root.get("settings").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("settings")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject setting = element.getAsJsonObject();
                String settingName = string(setting, "name", "");
                if (!settingName.isEmpty()) {
                    settings.add(new Setting(settingName, string(setting, "default", "")));
                }
            }
        }

        return new BedrockManifest(name, description, uuid, version, resourcePack, List.copyOf(subpacks), List.copyOf(settings));
    }

    private static @Nullable JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    /** Versions are either {@code [1, 2, 3]} or, in format 3 manifests, a semver string. */
    private static String versionString(@Nullable JsonElement element) {
        if (element == null) {
            return "0.0.0";
        }
        if (element.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement part : element.getAsJsonArray()) {
                parts.add(part.getAsString());
            }
            return String.join(".", parts);
        }
        return element.isJsonPrimitive() ? element.getAsString() : "0.0.0";
    }
}
