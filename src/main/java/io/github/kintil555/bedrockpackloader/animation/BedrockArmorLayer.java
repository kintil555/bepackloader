package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.object.VanillaModelModifier;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.service.GeckoLibClient;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.function.Function;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws worn armor, elytra and head items on the Bedrock player model. The vanilla armor layer is skipped once the
 * GeckoLib renderer takes over, and GeckoLib's own armor layer only accepts living-entity animatables, so this layer
 * reads the equipment from the {@link Avatar} itself and places each vanilla armor part on the matching bone of the
 * Bedrock geometry ({@code head}, {@code body}, {@code rightArm}, {@code leftArm}, {@code rightLeg}, {@code leftLeg}).
 * Bones missing from the model are ignored.
 */
public final class BedrockArmorLayer<R extends GeoRenderState> extends GeoRenderLayer<BedrockEntityAnimatable, Avatar, R> {
    private record Part(String bone, EquipmentSlot slot, Function<HumanoidModel<?>, ModelPart> part,
                        boolean bodyOfSlot, double sx, double sy, double sz) {
    }

    private static final Part[] PARTS = {
            new Part("head", EquipmentSlot.HEAD, model -> model.head, false, 8, 8, 8),
            new Part("body", EquipmentSlot.CHEST, model -> model.body, true, 8, 12, 4),
            new Part("rightArm", EquipmentSlot.CHEST, model -> model.rightArm, false, 4, 12, 4),
            new Part("leftArm", EquipmentSlot.CHEST, model -> model.leftArm, false, 4, 12, 4),
            new Part("rightLeg", EquipmentSlot.LEGS, model -> model.rightLeg, false, 4, 12, 4),
            new Part("leftLeg", EquipmentSlot.LEGS, model -> model.leftLeg, false, 4, 12, 4),
            new Part("rightLeg", EquipmentSlot.FEET, model -> model.rightLeg, false, 4, 12, 4),
            new Part("leftLeg", EquipmentSlot.FEET, model -> model.leftLeg, false, 4, 12, 4)
    };

    private final EquipmentLayerRenderer equipmentRenderer;
    private final EquipmentAssetManager equipmentAssets;
    private final ItemModelResolver itemModelResolver;

    public BedrockArmorLayer(EntityRendererProvider.Context context,
                             GeoRenderer<BedrockEntityAnimatable, Avatar, R> renderer) {
        super(renderer);
        this.equipmentRenderer = context.getEquipmentRenderer();
        this.equipmentAssets = context.getEquipmentAssets();
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void addRenderData(BedrockEntityAnimatable animatable, @Nullable Avatar avatar, R renderState,
                              float partialTick) {
        if (avatar == null) {
            return;
        }
        EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.put(EquipmentSlot.HEAD, avatar.getItemBySlot(EquipmentSlot.HEAD));
        equipment.put(EquipmentSlot.CHEST, avatar.getItemBySlot(EquipmentSlot.CHEST));
        equipment.put(EquipmentSlot.LEGS, avatar.getItemBySlot(EquipmentSlot.LEGS));
        equipment.put(EquipmentSlot.FEET, avatar.getItemBySlot(EquipmentSlot.FEET));
        renderState.addGeckolibData(DataTickets.EQUIPMENT_BY_SLOT, equipment);
        renderState.addGeckolibData(DataTickets.ELYTRA_ROTATION, new Vec3(
                avatar.elytraAnimationState.getRotX(partialTick),
                avatar.elytraAnimationState.getRotY(partialTick),
                avatar.elytraAnimationState.getRotZ(partialTick)));
        renderState.addGeckolibData(DataTickets.IS_CROUCHING, avatar.isCrouching());
    }

    @Override
    public void submitRenderTask(RenderPassInfo<R> info, SubmitNodeCollector collector) {
        EnumMap<EquipmentSlot, ItemStack> equipment = info.renderState()
                .getOrDefaultGeckolibData(DataTickets.EQUIPMENT_BY_SLOT, new EnumMap<>(EquipmentSlot.class));
        if (equipment.isEmpty()) {
            return;
        }
        BakedGeoModel model = info.model();
        for (Part part : PARTS) {
            ItemStack stack = equipment.getOrDefault(part.slot(), ItemStack.EMPTY);
            GeoBone bone = stack.isEmpty() ? null : findBone(model, part.bone());
            if (bone == null) {
                continue;
            }
            info.poseStack().pushPose();
            info.renderPosed(() -> submitPart(info, collector, part, bone, stack));
            info.poseStack().popPose();
        }
    }

    private static @Nullable GeoBone findBone(BakedGeoModel model, String wanted) {
        for (String name : model.boneLookup().get().keySet()) {
            if (name.equalsIgnoreCase(wanted)) {
                return model.getBone(name).orElse(null);
            }
        }
        return null;
    }

    private void submitPart(RenderPassInfo<R> info, SubmitNodeCollector collector, Part part, GeoBone bone,
                            ItemStack stack) {
        if (HumanoidArmorLayer.shouldRender(stack, part.slot())) {
            submitEquippable(info, collector, part, bone, stack);
        } else if (part.slot() == EquipmentSlot.HEAD) {
            submitHeadItem(info, collector, bone, stack);
        }
    }

    private void submitHeadItem(RenderPassInfo<R> info, SubmitNodeCollector collector, GeoBone bone, ItemStack stack) {
        ItemStackRenderState itemState = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.HEAD, null, null, 0);
        PoseStack poseStack = info.poseStack();
        poseStack.pushPose();
        com.geckolib.util.RenderUtil.transformToBone(poseStack, bone);
        poseStack.translate(0.0F, 0.25F, 0.0F);
        poseStack.scale(0.625F, 0.625F, 0.625F);
        itemState.submit(poseStack, collector, info.packedLight(), OverlayTexture.NO_OVERLAY,
                ((EntityRenderState) info.renderState()).outlineColor);
        poseStack.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void submitEquippable(RenderPassInfo<R> info, SubmitNodeCollector collector, Part part, GeoBone bone,
                                  ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }
        ResourceKey<EquipmentAsset> assetId = equippable.assetId().get();
        boolean wings = part.slot() == EquipmentSlot.CHEST
                && !this.equipmentAssets.get(assetId).getLayers(LayerType.WINGS).isEmpty();
        if (wings && !part.bodyOfSlot()) {
            return;
        }
        ArmorModelSet<HumanoidModel<?>> set = GeckoLibClient.HUMANOID_ARMOR_MODEL.get();
        HumanoidModel humanoid = set.get(part.slot());
        ModelPart modelPart = part.part().apply(humanoid);
        if (!wings && modelPart.isEmpty()) {
            return;
        }
        Vec3 scale = wings ? new Vec3(1.0, 1.0, 1.0) : scaleFor(bone, part);
        PoseStack poseStack = info.poseStack();
        poseStack.pushPose();
        com.geckolib.util.RenderUtil.transformToBone(poseStack, bone);
        bone.translateAwayFromPivotPoint(poseStack);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);

        HumanoidRenderState clean = new HumanoidRenderState();
        Model<?> drawn;
        LayerType layerType;
        if (wings) {
            ElytraModel elytra = GeckoLibClient.GENERIC_ELYTRA_MODEL.get();
            Vec3 rotation = info.renderState().getOrDefaultGeckolibData(DataTickets.ELYTRA_ROTATION, Vec3.ZERO);
            clean.isCrouching = info.renderState().getOrDefaultGeckolibData(DataTickets.IS_CROUCHING, false);
            clean.elytraRotX = (float) rotation.x;
            clean.elytraRotY = (float) rotation.y;
            clean.elytraRotZ = (float) rotation.z;
            poseStack.translate(0.0F, -1.5F, 0.125F);
            drawn = elytra;
            layerType = LayerType.WINGS;
        } else {
            ModelPart[] all = {humanoid.head, humanoid.body, humanoid.leftArm, humanoid.rightArm,
                    humanoid.leftLeg, humanoid.rightLeg};
            VanillaModelModifier.addModifierToState((GeoRenderState) (Object) clean, humanoid,
                    VanillaModelModifier.of(m -> {
                        for (ModelPart other : all) {
                            other.skipDraw = other != modelPart;
                        }
                        modelPart.resetPose();
                        modelPart.setPos(
                                -(bone.pivotX() - (bone.pivotX() * (float) scale.x - bone.pivotX()) / (float) scale.x),
                                -(bone.pivotY() - (bone.pivotY() * (float) scale.y - bone.pivotY()) / (float) scale.y),
                                bone.pivotZ() - (bone.pivotZ() * (float) scale.z - bone.pivotZ()) / (float) scale.z);
                    }, m -> {
                        for (ModelPart other : all) {
                            other.skipDraw = false;
                        }
                    }));
            drawn = humanoid;
            layerType = part.slot() == EquipmentSlot.LEGS ? LayerType.HUMANOID_LEGGINGS : LayerType.HUMANOID;
        }
        this.equipmentRenderer.renderLayers(layerType, assetId, (Model) drawn, clean, stack, poseStack, collector,
                info.packedLight(), ((EntityRenderState) info.renderState()).outlineColor);
        poseStack.popPose();
    }

    /** Scales the vanilla armor part to the size of the Bedrock bone it replaces. */
    private static Vec3 scaleFor(GeoBone bone, Part part) {
        if (bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length != 0) {
            return new Vec3(cuboid.cubes[0].size().x() / part.sx(), cuboid.cubes[0].size().y() / part.sy(),
                    cuboid.cubes[0].size().z() / part.sz());
        }
        return new Vec3(1.0, 1.0, 1.0);
    }
}
