package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.renderer.base.GeoRenderState;
import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** The Molang state of one animated entity instance: its scope, the current frame's values and the entity scripts. */
public final class BedrockEntityRuntime {
    private static final Map<String, MolangExpression> EXPRESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED_QUERIES = ConcurrentHashMap.newKeySet();

    private final ClientEntityDefinition definition;
    private final FrameContext context = new FrameContext();
    private final MolangScope scope = new MolangScope(this.context);

    private @Nullable Object lastFrame;
    private boolean initialized;

    public BedrockEntityRuntime(ClientEntityDefinition definition) {
        this.definition = definition;
        // Variables the Bedrock engine provides; the world render is always the third-person one.
        this.scope.setVariable("is_first_person", 0.0);
        this.scope.setVariable("map_face_icon", 0.0);
        this.scope.setVariable("is_paperdoll", 0.0);
    }

    /** Evaluates {@code source} with {@code arrays} visible as {@code array.*} and returns its string result, or null. */
    public @Nullable String evaluateString(String source, Map<String, List<Object>> arrays) {
        this.context.arrays = arrays;
        try {
            return compile(source).evaluate(this.scope) instanceof String text ? text : null;
        } finally {
            this.context.arrays = Map.of();
        }
    }

    /** Compiles a Molang source once for the whole game; invalid sources become the constant 0. */
    public static MolangExpression compile(String source) {
        return EXPRESSIONS.computeIfAbsent(source, key -> MolangExpression.compileLenient(key,
                message -> BedrockPackLoaderMod.LOGGER.warn("Molang '{}' is not valid, using 0: {}", key, message)));
    }

    public MolangScope scope() {
        return this.scope;
    }

    /**
     * Prepares the entity for the frame of {@code renderState}: refreshes the queried values, publishes the scope to
     * GeckoLib and runs the entity's {@code initialize} (once) and {@code pre_animation} scripts. Idempotent per frame.
     */
    public void beginFrame(GeoRenderState renderState) {
        if (renderState == this.lastFrame) {
            return;
        }
        this.lastFrame = renderState;

        EntityQuerySnapshot snapshot = renderState.getGeckolibData(BedrockDataTickets.SNAPSHOT);
        if (snapshot != null) {
            this.context.snapshot = snapshot;
        }
        renderState.addGeckolibData(BedrockDataTickets.SCOPE, this.scope);

        if (!this.initialized) {
            this.initialized = true;
            for (String script : this.definition.initialize()) {
                compile(script).evaluate(this.scope);
            }
        }
        for (String script : this.definition.preAnimation()) {
            compile(script).evaluate(this.scope);
        }
    }

    /** True when {@code condition} is absent or evaluates to true. */
    public boolean test(@Nullable String condition) {
        return condition == null || compile(condition).evaluateBoolean(this.scope);
    }

    private static final class FrameContext implements MolangContext, ControllerTimes {
        private volatile @Nullable EntityQuerySnapshot snapshot;
        private volatile Map<String, List<Object>> arrays = Map.of();
        private volatile double stateTime;
        private volatile double animTime;
        private volatile boolean anyFinished;
        private volatile boolean allFinished;

        @Override
        public void setControllerTimes(double stateTime, double animTime, boolean anyAnimationFinished, boolean allAnimationsFinished) {
            this.stateTime = stateTime;
            this.animTime = animTime;
            this.anyFinished = anyAnimationFinished;
            this.allFinished = allAnimationsFinished;
        }

        @Override
        public @Nullable Object query(String name, Object[] args) {
            switch (name) {
                case "state_time" -> {
                    return this.stateTime;
                }
                case "anim_time" -> {
                    return this.animTime;
                }
                case "any_animation_finished" -> {
                    return this.anyFinished ? 1.0 : 0.0;
                }
                case "all_animations_finished" -> {
                    return this.allFinished ? 1.0 : 0.0;
                }
                default -> {
                }
            }
            EntityQuerySnapshot current = this.snapshot;
            return current == null ? null : current.query(name, args);
        }

        @Override
        public @Nullable List<Object> array(String name) {
            return this.arrays.get(name);
        }

        @Override
        public void unsupportedQuery(String name) {
            if (REPORTED_QUERIES.add(name)) {
                BedrockPackLoaderMod.LOGGER.warn("Molang query '{}' is not supported yet and evaluates to 0", name);
            }
        }
    }
}
