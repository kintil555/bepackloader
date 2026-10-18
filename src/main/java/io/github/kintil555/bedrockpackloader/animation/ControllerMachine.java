package io.github.kintil555.bedrockpackloader.animation;

import io.github.kintil555.bedrockpackloader.index.AnimationControllerDefinition;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Runs one Bedrock animation controller (a state machine) for one entity. Once per frame the transitions of the
 * current state are tested in order; the first whose Molang condition is true switches the state.
 */
public final class ControllerMachine {
    /** The result of a frame: animation ids of the state ({@code null} for a name that does not resolve) and the cross-fade in ticks. */
    public record Step(List<@Nullable String> animations, boolean stateChanged, int blendTicks) {
    }

    private final AnimationControllerDefinition definition;
    private final Function<String, @Nullable String> animationIds;
    private final Function<String, MolangExpression> compiler;

    private String state;
    private boolean started;
    private @Nullable Object lastFrame;
    private @Nullable Step lastStep;

    /**
     * @param animationIds maps an animation short name used in the controller to a full animation id
     * @param compiler     compiles (and caches) a Molang source
     */
    public ControllerMachine(AnimationControllerDefinition definition, Function<String, @Nullable String> animationIds,
                             Function<String, MolangExpression> compiler) {
        this.definition = definition;
        this.animationIds = animationIds;
        this.compiler = compiler;
        this.state = definition.states().containsKey(definition.initialState()) || definition.states().isEmpty()
                ? definition.initialState() : definition.states().keySet().iterator().next();
    }

    /** The largest number of animations any state plays at once. */
    public static int maxAnimations(AnimationControllerDefinition definition) {
        int max = 0;
        for (AnimationControllerDefinition.State state : definition.states().values()) {
            max = Math.max(max, state.animations().size());
        }
        return max;
    }

    public String state() {
        return this.state;
    }

    /** Advances the machine for {@code frame}; calling it again with the same frame object returns the same step. */
    public Step step(Object frame, MolangScope scope) {
        Step cached = this.lastStep;
        if (cached != null && frame == this.lastFrame) {
            return cached;
        }

        AnimationControllerDefinition.State current = this.definition.states().get(this.state);
        boolean changed = false;
        if (!this.started) {
            this.started = true;
            if (current != null) {
                run(current.onEntry(), scope);
            }
        } else if (current != null) {
            for (AnimationControllerDefinition.Transition transition : current.transitions()) {
                if (this.definition.states().containsKey(transition.target())
                        && this.compiler.apply(transition.condition()).evaluateBoolean(scope)) {
                    run(current.onExit(), scope);
                    this.state = transition.target();
                    current = this.definition.states().get(this.state);
                    run(current.onEntry(), scope);
                    changed = true;
                    break;
                }
            }
        }

        List<@Nullable String> animations = new ArrayList<>();
        int blendTicks = 0;
        if (current != null) {
            for (AnimationControllerDefinition.StateAnimation animation : current.animations()) {
                animations.add(this.animationIds.apply(animation.name()));
            }
            blendTicks = Math.round(current.blendTransition() * 20.0F);
        }

        Step step = new Step(Collections.unmodifiableList(animations), changed, blendTicks);
        this.lastFrame = frame;
        this.lastStep = step;
        return step;
    }

    private void run(List<String> scripts, MolangScope scope) {
        for (String script : scripts) {
            this.compiler.apply(script).evaluate(scope);
        }
    }
}
