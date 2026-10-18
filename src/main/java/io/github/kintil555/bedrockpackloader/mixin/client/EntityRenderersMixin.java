package io.github.kintil555.bedrockpackloader.mixin.client;

import io.github.kintil555.bedrockpackloader.animation.BedrockEntityBinding;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the renderer context used to build the player renderers. */
@Mixin(EntityRenderers.class)
public abstract class EntityRenderersMixin {
    @Inject(method = "createAvatarRenderers", at = @At("HEAD"))
    private static void bedrockpackloader$captureContext(EntityRendererProvider.Context context, CallbackInfoReturnable<?> cir) {
        BedrockEntityBinding.captureContext(context);
    }
}
