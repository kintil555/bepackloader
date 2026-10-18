package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import io.github.kintil555.bedrockpackloader.index.AnimationControllerDefinition;
import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import java.util.Optional;

/**
 * The GeckoLib animatable of a Bedrock client entity. It registers one GeckoLib controller per {@code scripts.animate}
 * entry (and one per simultaneously playing animation of a Bedrock controller state); every controller asks the
 * per-instance {@link BedrockEntityRuntime} what to play this frame.
 */
public final class BedrockEntityAnimatable implements GeoReplacedEntity {
    private final ClientEntityDefinition definition;
    private final BedrockAssetIndex index;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this, true);

    public BedrockEntityAnimatable(ClientEntityDefinition definition, BedrockAssetIndex index) {
        this.definition = definition;
        this.index = index;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        BedrockEntityRuntime runtime = new BedrockEntityRuntime(this.definition);

        for (ClientEntityDefinition.Animate entry : this.definition.animate()) {
            String id = this.definition.animations().get(entry.name());
            if (id == null) {
                continue;
            }

            Optional<AnimationControllerDefinition> controller = this.index.controller(id);
            if (controller.isPresent()) {
                ControllerMachine machine = new ControllerMachine(controller.get(), this.definition.animations()::get, BedrockEntityRuntime::compile);
                int slots = ControllerMachine.maxAnimations(controller.get());
                for (int slot = 0; slot < slots; slot++) {
                    int index = slot;
                    registrar.add(new AnimationController<BedrockEntityAnimatable>("bpl_" + entry.name() + "_" + slot, 0,
                            test -> playMachine(runtime, machine, entry, index, test)));
                }
            } else if (this.index.animation(id).isPresent()) {
                registrar.add(new AnimationController<BedrockEntityAnimatable>("bpl_" + entry.name(), 0,
                        test -> playAnimation(runtime, id, entry, test)));
            }
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static PlayState playAnimation(BedrockEntityRuntime runtime, String animationId, ClientEntityDefinition.Animate entry,
                                           AnimationTest<BedrockEntityAnimatable> test) {
        runtime.beginFrame(test.renderState());
        if (!runtime.test(entry.condition())) {
            return PlayState.STOP;
        }
        return test.setAndContinue(RawAnimation.begin().then(animationId, LoopType.DEFAULT));
    }

    private static PlayState playMachine(BedrockEntityRuntime runtime, ControllerMachine machine, ClientEntityDefinition.Animate entry,
                                         int slot, AnimationTest<BedrockEntityAnimatable> test) {
        runtime.beginFrame(test.renderState());
        if (!runtime.test(entry.condition())) {
            return PlayState.STOP;
        }

        ControllerMachine.Step step = machine.step(test.renderState(), runtime.scope());
        String animation = slot < step.animations().size() ? step.animations().get(slot) : null;
        if (animation == null) {
            return PlayState.STOP;
        }
        test.controller().setTransitionTicks(step.blendTicks());
        return test.setAndContinue(RawAnimation.begin().then(animation, LoopType.DEFAULT));
    }
}
