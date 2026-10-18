package io.github.kintil555.bedrockpackloader.animation;

import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.pack.PackSettings;
import org.jspecify.annotations.Nullable;

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
        PackSettings settings,
        double moveSpeed,
        double x,
        double y,
        double z,
        @Nullable String name,
        Equipment equipment
) implements MolangContext {
    /** Answers the equipment queries without exposing Minecraft types to this record. */
    public interface Equipment {
        Equipment NONE = new Equipment() {
            @Override
            public @Nullable String itemId(String slot) {
                return null;
            }

            @Override
            public boolean hasTag(String slot, String tag) {
                return false;
            }
        };

        /** The registry id of the item in a Bedrock slot ({@code slot.weapon.mainhand}, ...), or null when empty. */
        @Nullable String itemId(String slot);

        /** Whether the item in the slot has a tag; {@code tag} is a Bedrock or Java tag id. */
        boolean hasTag(String slot, String tag);
    }

    /** Snapshot without movement speed, position, name or equipment (tests and simple callers). */
    public EntityQuerySnapshot(boolean onGround, boolean inWater, boolean riding, boolean baby, boolean sneaking, boolean sprinting,
                               boolean spectator, double groundSpeed, double verticalSpeed, double bodyYRotation,
                               double headXRotation, double headYRotation, double lifeTime, PackSettings settings) {
        this(onGround, inWater, riding, baby, sneaking, sprinting, spectator, groundSpeed, verticalSpeed, bodyYRotation,
                headXRotation, headYRotation, lifeTime, settings, 0, 0, 0, 0, null, Equipment.NONE);
    }

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
            case "is_in_ui", "has_target", "is_attached", "is_riding_on_ground" -> 0.0;
            case "modified_move_speed" -> this.moveSpeed;
            case "graphics_mode_is_any" -> bool(anyString(args, "fancy"));
            case "position" -> args.length >= 1 && args[0] instanceof Double axis
                    ? (axis.intValue() == 0 ? this.x : axis.intValue() == 1 ? this.y : this.z) : null;
            case "is_name_any" -> bool(this.name != null && anyString(args, this.name));
            case "is_item_name_any" -> bool(itemNameAny(args));
            case "equipped_item_any_tag" -> bool(itemTagAny(args));
            case "target_x_rotation", "target_y_rotation" -> 0.0;
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

    private static boolean anyString(Object[] args, String value) {
        for (Object arg : args) {
            if (arg instanceof String text && text.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean itemNameAny(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof String slot)) {
            return false;
        }
        String id = this.equipment.itemId(slot);
        if (id == null) {
            return false;
        }
        for (int i = 1; i < args.length; i++) {
            if (args[i] instanceof String name && (name.equals(id) || name.equals(id.replace("minecraft:", "")))) {
                return true;
            }
        }
        return false;
    }

    private boolean itemTagAny(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof String slot)) {
            return false;
        }
        for (int i = 1; i < args.length; i++) {
            if (args[i] instanceof String tag && this.equipment.hasTag(slot, tag)) {
                return true;
            }
        }
        return false;
    }

    private static Double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }
}
