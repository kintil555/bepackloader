package io.github.kintil555.bedrockpackloader.molang;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Mutable evaluation state: {@code v.*} (persistent), {@code t.*} (temporary, cleared per top-level evaluation)
 * and {@code c.*} (context values set by the host). The keyword {@code this} (current value of an animation
 * channel) reads the context value named {@code "this"}; the host sets it before evaluating a keyframe.
 */
public final class MolangScope {
    private final MolangContext context;
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> temps = new HashMap<>();
    private final Map<String, Object> contextValues = new HashMap<>();
    private final Map<String, Object[]> arrays = new HashMap<>();
    private final Set<String> reportedQueries = new HashSet<>();
    private final Random random;

    public MolangScope(MolangContext context) {
        this(context, new Random());
    }

    public MolangScope(MolangContext context, Random random) {
        this.context = context == null ? MolangContext.EMPTY : context;
        this.random = random;
    }

    public MolangContext context() {
        return this.context;
    }

    public Random random() {
        return this.random;
    }

    public Object variable(String name) {
        return this.variables.get(name);
    }

    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }

    public Map<String, Object> variables() {
        return this.variables;
    }

    public void setContextValue(String name, Object value) {
        this.contextValues.put(name, value);
    }

    Object read(char kind, String name) {
        return switch (kind) {
            case 'v' -> this.variables.get(name);
            case 't' -> this.temps.get(name);
            default -> this.contextValues.get(name);
        };
    }

    void write(char kind, String name, Object value) {
        switch (kind) {
            case 'v' -> this.variables.put(name, value);
            case 't' -> this.temps.put(name, value);
            default -> this.contextValues.put(name, value);
        }
    }

    Object[] localArray(String name) {
        return this.arrays.get(name);
    }

    void writeArray(String name, int index, Object value) {
        Object[] array = this.arrays.get(name);
        if (array == null || array.length <= index) {
            Object[] grown = new Object[Math.min(Math.max(index + 1, 8), 1024)];
            if (array != null) {
                System.arraycopy(array, 0, grown, 0, array.length);
            }
            this.arrays.put(name, array = grown);
        }
        if (index < array.length) {
            array[index] = value;
        }
    }

    void clearTemps() {
        this.temps.clear();
    }

    void reportUnsupported(String query) {
        if (this.reportedQueries.add(query)) {
            this.context.unsupportedQuery(query);
        }
    }
}
