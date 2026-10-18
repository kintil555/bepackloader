package io.github.kintil555.bedrockpackloader.mixin.client;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import io.github.kintil555.bedrockpackloader.animation.BedrockDataTickets;
import io.github.kintil555.bedrockpackloader.animation.BedrockEntityAnimatable;
import io.github.kintil555.bedrockpackloader.animation.BedrockEntityBinding;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fills the GeckoLib data of a player's render state when a Bedrock pack replaces the player. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void bedrockpackloader$extractBedrock(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> renderer = BedrockEntityBinding.playerRenderer();
        if (renderer == null) {
            return;
        }
        renderer.extractRenderState(entity, state, partialTicks);
        ((GeoRenderState) (Object) state).addGeckolibData(BedrockDataTickets.ACTIVE, Boolean.TRUE);
    }
}
