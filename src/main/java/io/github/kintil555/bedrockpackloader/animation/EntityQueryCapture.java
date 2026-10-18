package io.github.kintil555.bedrockpackloader.animation;

import io.github.kintil555.bedrockpackloader.pack.PackSettings;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Reads the values Molang queries need from a live entity. */
public final class EntityQueryCapture {
    private static final double TICKS_PER_SECOND = 20.0;

    private EntityQueryCapture() {
    }

    public static EntityQuerySnapshot capture(Entity entity, float partialTick, PackSettings settings) {
        Vec3 motion = entity.getDeltaMovement();
        double groundSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * TICKS_PER_SECOND;
        double verticalSpeed = motion.y * TICKS_PER_SECOND;

        double bodyYaw = entity.getYRot();
        boolean baby = false;
        double moveSpeed = 0;
        if (entity instanceof LivingEntity living) {
            bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            baby = living.isBaby();
            moveSpeed = living.walkAnimation.speed(partialTick);
        }

        return new EntityQuerySnapshot(
                entity.onGround(),
                entity.isInWater(),
                entity.isPassenger(),
                baby,
                entity.isCrouching(),
                entity.isSprinting(),
                entity.isSpectator(),
                groundSpeed,
                verticalSpeed,
                bodyYaw,
                entity.getXRot(partialTick),
                entity.getViewYRot(partialTick),
                (entity.tickCount + partialTick) / TICKS_PER_SECOND,
                settings,
                moveSpeed,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.hasCustomName() ? entity.getCustomName().getString() : entity.getName().getString(),
                new LiveEquipment(entity));
    }

    private record LiveEquipment(Entity entity) implements EntityQuerySnapshot.Equipment {
        private static final Map<String, String> TAG_ALIASES = Map.of(
                "minecraft:is_sword", "minecraft:swords",
                "minecraft:is_axe", "minecraft:axes",
                "minecraft:is_pickaxe", "minecraft:pickaxes",
                "minecraft:is_shovel", "minecraft:shovels",
                "minecraft:is_hoe", "minecraft:hoes",
                "minecraft:is_trident", "minecraft:spears",
                "minecraft:is_food", "minecraft:food");

        private @Nullable ItemStack stack(String slot) {
            if (!(this.entity instanceof LivingEntity living)) {
                return null;
            }
            EquipmentSlot equipmentSlot = switch (slot) {
                case "slot.weapon.mainhand" -> EquipmentSlot.MAINHAND;
                case "slot.weapon.offhand" -> EquipmentSlot.OFFHAND;
                case "slot.armor.head" -> EquipmentSlot.HEAD;
                case "slot.armor.chest" -> EquipmentSlot.CHEST;
                case "slot.armor.legs" -> EquipmentSlot.LEGS;
                case "slot.armor.feet" -> EquipmentSlot.FEET;
                default -> null;
            };
            if (equipmentSlot == null) {
                return null;
            }
            ItemStack stack = living.getItemBySlot(equipmentSlot);
            return stack.isEmpty() ? null : stack;
        }

        @Override
        public @Nullable String itemId(String slot) {
            ItemStack stack = stack(slot);
            return stack == null ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        @Override
        public boolean hasTag(String slot, String tag) {
            ItemStack stack = stack(slot);
            if (stack == null) {
                return false;
            }
            String javaTag = TAG_ALIASES.getOrDefault(tag, tag);
            Identifier id = Identifier.tryParse(javaTag);
            if (id == null) {
                return false;
            }
            TagKey<Item> key = TagKey.create(Registries.ITEM, id);
            return stack.is(holder -> holder.is(key));
        }
    }
}
