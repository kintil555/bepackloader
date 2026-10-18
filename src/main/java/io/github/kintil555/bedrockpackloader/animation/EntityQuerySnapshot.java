package io.github.kintil555.bedrockpackloader.animation;

import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.pack.PackSettings;

/**
 * The entity values Molang queries read, captured once per rendered frame. Speeds are blocks per second,
 * rotations degrees, {@code lifeTime} seconds.
 */
public record EntityQuerySnapshot(
        boolean onGround,
        boolean inWater,
        boolean riding,
        boolean baby,
        boolean sneaking,
        boolean sprinting,
        boolean spectator,
        double groundSpeed,
        double verticalSpeed,
        double bodyYRotation,
        double headXRotation,
        double headYRotation,
        double lifeTime,
        PackSettings settings
) implements MolangContext {
    @Override
    public Object query(String name, Object[] args) {
        return switch (name) {
            case "is_on_ground" -> bool(this.onGround);
            case "is_in_water" -> bool(this.inWater);
            case "is_riding" -> bool(this.riding);
            case "is_baby" -> bool(this.baby);
            case "is_sneaking" -> bool(this.sneaking);
            case "is_sprinting" -> bool(this.sprinting);
            case "is_spectator" -> bool(this.spectator);
            case "is_in_ui" -> 0.0;
            case "ground_speed" -> this.groundSpeed;
            case "vertical_speed" -> this.verticalSpeed;
            case "body_y_rotation" -> this.bodyYRotation;
            case "head_x_rotation" -> this.headXRotation;
            case "head_y_rotation" -> this.headYRotation;
            case "life_time" -> this.lifeTime;
            case "is_pack_setting_enabled" -> args.length >= 1 && args[0] instanceof String id
                    ? bool(this.settings.isEnabled(id)) : null;
            case "is_pack_setting_selected" -> args.length >= 2 && args[0] instanceof String id && args[1] instanceof String option
                    ? bool(this.settings.isSelected(id, option)) : null;
            default -> null;
        };
    }

    private static Double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }
}
