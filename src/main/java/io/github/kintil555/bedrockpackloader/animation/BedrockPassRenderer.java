package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Draws one {@link RenderPass} with GeckoLib. GeckoLib makes {@code EntityRenderState} a {@code GeoRenderState} with
 * a mixin, so the generic bounds cannot be satisfied at compile time and the call goes through raw types.
 */
public final class BedrockPassRenderer {
    private BedrockPassRenderer() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void draw(GeoReplacedEntityRenderer<?, ?, ?> renderer, EntityRenderState state, PoseStack poseStack,
                            SubmitNodeCollector collector, CameraRenderState camera, RenderPass pass) {
        RenderPassInfo.BoneUpdater visibility = (info, bones) -> {
            for (String bone : ((RenderPassInfo<?>) info).model().boneLookup().get().keySet()) {
                if (!pass.isVisible(bone)) {
                    bones.ifPresent(bone, snapshot -> snapshot.skipRender(true));
                }
            }
        };
        ((GeoRenderer) renderer).performRenderPass((GeoRenderState) (Object) state, poseStack, collector, camera, List.of(visibility));
    }
}
