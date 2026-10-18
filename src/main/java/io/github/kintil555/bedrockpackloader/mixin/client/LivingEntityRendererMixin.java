package io.github.kintil555.bedrockpackloader.mixin.client;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kintil555.bedrockpackloader.animation.BedrockDataTickets;
import io.github.kintil555.bedrockpackloader.animation.BedrockEntityAnimatable;
import io.github.kintil555.bedrockpackloader.animation.BedrockEntityBinding;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla player submit with the GeckoLib one for render states the Bedrock binding extracted. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void bedrockpackloader$submitBedrock(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                                 CameraRenderState camera, CallbackInfo ci) {
        if (!((Object) this instanceof AvatarRenderer<?>) || !(state instanceof AvatarRenderState avatarState)) {
            return;
        }
        if (!((GeoRenderState) (Object) state).hasGeckolibData(BedrockDataTickets.ACTIVE)) {
            return;
        }
        GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> renderer = BedrockEntityBinding.playerRenderer();
        if (renderer == null) {
            return;
        }
        renderer.submit(avatarState, poseStack, collector, camera);
        ci.cancel();
    }
}
