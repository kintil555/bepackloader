package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Draws the main-hand and off-hand items on the Bedrock player model. The vanilla layers are skipped when the
 * GeckoLib renderer takes over, so without this layer held items would disappear. Bedrock player geometries expose
 * the attachment points as the bones {@code rightItem} and {@code leftItem}; bones missing from the model are ignored.
 */
public final class BedrockHeldItemLayer<R extends GeoRenderState> extends BlockAndItemGeoLayer<BedrockEntityAnimatable, Avatar, R> {
    private static final String RIGHT_BONE = "rightItem";
    private static final String LEFT_BONE = "leftItem";

    public BedrockHeldItemLayer(EntityRendererProvider.Context context,
                                GeoRenderer<BedrockEntityAnimatable, Avatar, R> renderer) {
        super(context, renderer);
    }

    @Override
    protected List<RenderData> getRelevantBones(BedrockEntityAnimatable animatable, @Nullable Avatar avatar,
                                                R renderState, float partialTick) {
        List<RenderData> bones = new ArrayList<>(2);
        if (avatar == null) {
            return bones;
        }
        addHand(bones, avatar, HumanoidArm.RIGHT, RIGHT_BONE);
        addHand(bones, avatar, HumanoidArm.LEFT, LEFT_BONE);
        return bones;
    }

    private void addHand(List<RenderData> bones, Avatar avatar, HumanoidArm arm, String boneName) {
        boolean mainHand = arm == avatar.getMainArm();
        EquipmentSlot slot = mainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemDisplayContext context = arm == HumanoidArm.RIGHT
                ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        ItemStack stack = avatar.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return;
        }
        bones.add(RenderData.item(boneName, context,
                RenderUtil.createRenderStateForItem(stack, this.itemModelResolver, context, avatar)));
    }

    @Override
    public void addRenderData(BedrockEntityAnimatable animatable, @Nullable Avatar avatar, R renderState,
                              float partialTick) {
        List<RenderData> contents = getRelevantBones(animatable, avatar, renderState, partialTick);
        if (!contents.isEmpty()) {
            renderState.addGeckolibData(CONTENTS, contents);
        }
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<R> info,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        BakedGeoModel model = info.model();
        List<RenderData> all = info.renderState().getOrDefaultGeckolibData(CONTENTS, List.of());
        if (all.isEmpty()) {
            return;
        }
        List<RenderData> present = new ArrayList<>(all.size());
        for (RenderData data : all) {
            String actual = resolveBone(model, data.boneName());
            if (actual != null) {
                present.add(new RenderData(actual, data.displayContext(), data.modelState()));
            }
        }
        if (present.isEmpty()) {
            return;
        }
        info.renderState().addGeckolibData(CONTENTS, present);
        super.addPerBoneRender(info, consumer);
        info.renderState().addGeckolibData(CONTENTS, all);
    }

    /** Bedrock bone names are case-insensitive; returns the name as spelled in the model, or null when absent. */
    private static @Nullable String resolveBone(BakedGeoModel model, String wanted) {
        for (String name : model.boneLookup().get().keySet()) {
            if (name.equalsIgnoreCase(wanted)) {
                return name;
            }
        }
        return null;
    }

    @Override
    protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState stackState,
                                         ItemDisplayContext displayContext, R renderState,
                                         SubmitNodeCollector renderTasks, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.XN.rotationDegrees(90.0F));
        poseStack.translate(0.0F, 0.125F, -0.0625F);
        super.submitItemStackRender(poseStack, bone, stackState, displayContext, renderState, renderTasks, packedLight);
        poseStack.popPose();
    }
}
