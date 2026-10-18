package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.animation.state.ControllerState;
import com.geckolib.loading.math.MathParser;
import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Exposes Bedrock Molang expressions to GeckoLib as {@code variable.bpl_N} variables. GeckoLib evaluates the
 * variable while sampling a keyframe; the value comes from this package's Molang evaluator, so strings, {@code ??},
 * arrays and the other constructs GeckoLib's parser rejects keep working.
 */
public final class MolangVariableBridge {
    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Function<ControllerState, MolangScope> DEFAULT_SCOPES = state -> new MolangScope(MolangContext.EMPTY);

    private static volatile Function<ControllerState, MolangScope> scopes = DEFAULT_SCOPES;

    private MolangVariableBridge() {
    }

    /**
     * Sets how a scope (queries and variables of the animated entity) is built from GeckoLib's controller state.
     * Without a provider every query evaluates to 0.
     */
    public static void setScopeProvider(Function<ControllerState, MolangScope> provider) {
        scopes = provider;
    }

    /** Returns the variable name that evaluates {@code expression}, creating and registering it on first use. */
    public static String variableFor(String expression) {
        return NAMES.computeIfAbsent(expression, MolangVariableBridge::register);
    }

    private static String register(String source) {
        String name = "variable.bpl_" + COUNTER.getAndIncrement();
        MolangExpression expression = MolangExpression.compileLenient(source,
                message -> BedrockPackLoaderMod.LOGGER.warn("Molang '{}' is not valid, using 0: {}", source, message));
        MathParser.getVariableFor(name).set(state -> expression.evaluateNumber(scopes.apply(state)));
        return name;
    }
}
