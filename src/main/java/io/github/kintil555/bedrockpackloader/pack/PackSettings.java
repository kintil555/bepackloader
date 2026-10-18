package io.github.kintil555.bedrockpackloader.pack;

import java.util.HashMap;
import java.util.Map;

/** Values of a Bedrock pack's settings, answering {@code q.is_pack_setting_enabled} and {@code q.is_pack_setting_selected}. */
public final class PackSettings {
    public static final PackSettings NONE = new PackSettings(Map.of());

    private final Map<String, String> values;

    public PackSettings(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** The settings with the defaults declared in the manifest. */
    public static PackSettings fromManifest(BedrockManifest manifest) {
        Map<String, String> values = new HashMap<>();
        for (BedrockManifest.Setting setting : manifest.settings()) {
            values.put(setting.name(), setting.defaultValue());
        }
        return new PackSettings(values);
    }

    /** True for a toggle that is on. */
    public boolean isEnabled(String name) {
        return "true".equals(this.values.get(name));
    }

    /** True when the dropdown {@code name} currently has the option {@code option}. */
    public boolean isSelected(String name, String option) {
        return option.equals(this.values.get(name));
    }
}
