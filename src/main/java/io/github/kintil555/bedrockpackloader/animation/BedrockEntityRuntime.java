package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.renderer.base.GeoRenderState;
import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
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

    private static final class FrameContext implements MolangContext {
        private volatile @Nullable EntityQuerySnapshot snapshot;

        @Override
        public @Nullable Object query(String name, Object[] args) {
            EntityQuerySnapshot current = this.snapshot;
            return current == null ? null : current.query(name, args);
        }

        @Override
        public void unsupportedQuery(String name) {
            if (REPORTED_QUERIES.add(name)) {
                BedrockPackLoaderMod.LOGGER.warn("Molang query '{}' is not supported yet and evaluates to 0", name);
            }
        }
    }
}
