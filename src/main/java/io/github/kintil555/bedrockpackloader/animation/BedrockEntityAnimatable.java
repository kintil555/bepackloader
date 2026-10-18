package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.GeckoLibUtil;
import io.github.kintil555.bedrockpackloader.index.RenderControllerDefinition;
import io.github.kintil555.bedrockpackloader.util.PathNames;
import net.minecraft.resources.Identifier;
import io.github.kintil555.bedrockpackloader.index.AnimationControllerDefinition;
import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The GeckoLib animatable of a Bedrock client entity. It registers one GeckoLib controller per {@code scripts.animate}
 * entry (and one per simultaneously playing animation of a Bedrock controller state); every controller asks the
 * per-instance {@link BedrockEntityRuntime} what to play this frame.
 */
public final class BedrockEntityAnimatable implements GeoReplacedEntity {
    private final ClientEntityDefinition definition;
    private final BedrockAssetIndex index;
    private final Map<Integer, BedrockEntityRuntime> runtimes = new ConcurrentHashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this, true);

    public BedrockEntityAnimatable(ClientEntityDefinition definition, BedrockAssetIndex index) {
        this.definition = definition;
        this.index = index;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        for (ClientEntityDefinition.Animate entry : this.definition.animate()) {
            String id = this.definition.animations().get(entry.name());
            if (id == null) {
                continue;
            }

            Optional<AnimationControllerDefinition> controller = this.index.controller(id);
            if (controller.isPresent()) {
                ControllerMachine machine = new ControllerMachine(controller.get(), this.definition.animations()::get, BedrockEntityRuntime::compile, this::timing);
                int slots = ControllerMachine.maxAnimations(controller.get());
                for (int slot = 0; slot < slots; slot++) {
                    int index = slot;
                    registrar.add(new AnimationController<BedrockEntityAnimatable>("bpl_" + entry.name() + "_" + slot, 0,
                            test -> playMachine(runtime(test.renderState()), machine, entry, index, test)));
                }
            } else if (this.index.animation(id).isPresent()) {
                registrar.add(new AnimationController<BedrockEntityAnimatable>("bpl_" + entry.name(), 0,
                        test -> playAnimation(runtime(test.renderState()), id, entry, test)));
            }
        }
    }

    /** The Molang state of the entity that {@code renderState} belongs to, shared by all its controllers. */
    public BedrockEntityRuntime runtime(GeoRenderState renderState) {
        Integer id = renderState.getGeckolibData(BedrockDataTickets.ENTITY_ID);
        return this.runtimes.computeIfAbsent(id == null ? -1 : id, key -> new BedrockEntityRuntime(this.definition));
    }

    /**
     * The models to draw this frame, one per active render controller whose geometry resolves. Returns {@code null}
     * when the entity defines no usable render controller (the caller then draws the default model once); an empty
     * list means every controller is inactive.
     */
    public @Nullable List<RenderPass> renderPasses(GeoRenderState renderState, @Nullable Identifier skin) {
        BedrockEntityRuntime runtime = runtime(renderState);
        List<RenderPass> passes = new ArrayList<>();
        boolean any = false;
        for (ClientEntityDefinition.RenderControllerRef ref : this.definition.renderControllers()) {
            RenderControllerDefinition controller = this.index.renderController(ref.id()).orElse(null);
            if (controller == null) {
                continue;
            }
            String geometryKey = key(runtime.evaluateString(controller.geometry(), controller.arrays()), "geometry.");
            String geometryId = geometryKey == null ? null : this.definition.geometry().get(geometryKey);
            Identifier model = geometryId == null ? null : this.index.geometry(geometryId).orElse(null);
            if (model == null) {
                continue;
            }
            any = true;
            if (!runtime.test(ref.condition())) {
                continue;
            }
            String textureKey = controller.texture() == null ? null : key(runtime.evaluateString(controller.texture(), controller.arrays()), "texture.");
            Identifier texture = null;
            if (textureKey != null && !textureKey.equals("default")) {
                String path = this.definition.textures().get(textureKey);
                texture = path == null ? null : Identifier.fromNamespaceAndPath(this.index.namespace(), PathNames.toIdentifierPath(path) + ".png");
            }
            if (texture == null && skin == null) {
                continue;
            }
            List<RenderPass.Rule> rules = new ArrayList<>();
            for (RenderControllerDefinition.PartRule rule : controller.partVisibility()) {
                rules.add(new RenderPass.Rule(wildcard(rule.pattern()), runtime.test(rule.condition())));
            }
            passes.add(new RenderPass(model, texture, List.copyOf(rules)));
        }
        return any ? passes : null;
    }

    private static Pattern wildcard(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (regex.length() > 0 || pattern.startsWith("*")) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static @Nullable String key(@Nullable String value, String prefix) {
        return value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : null;
    }

    private ControllerMachine.@Nullable Timing timing(String animationId) {
        return this.index.animation(animationId).map(ref -> new ControllerMachine.Timing(ref.length(), ref.loops())).orElse(null);
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
