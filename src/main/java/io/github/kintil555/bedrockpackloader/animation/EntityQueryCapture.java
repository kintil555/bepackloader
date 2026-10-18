package io.github.kintil555.bedrockpackloader.animation;

import io.github.kintil555.bedrockpackloader.pack.PackSettings;
import net.minecraft.util.Mth;
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
        if (entity instanceof LivingEntity living) {
            bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            baby = living.isBaby();
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
                settings);
    }
}
