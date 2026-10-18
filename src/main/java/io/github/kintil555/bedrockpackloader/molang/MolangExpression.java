package io.github.kintil555.bedrockpackloader.molang;

import java.util.function.Consumer;

/** A compiled Bedrock Molang expression, safe to evaluate repeatedly against different scopes. */
public final class MolangExpression {
    public static final MolangExpression ZERO = new MolangExpression("0", new Node.Num(0.0));

    private final String source;
    private final Node root;

    private MolangExpression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    /** @throws MolangException when the expression is not valid Molang */
    public static MolangExpression compile(String source) {
        if (source == null || source.isBlank()) {
            return ZERO;
        }
        return new MolangExpression(source, MolangParser.parse(source));
    }

    /** Compiles, reporting failures to {@code onError} and falling back to the constant {@code 0}. */
    public static MolangExpression compileLenient(String source, Consumer<String> onError) {
        try {
            return compile(source);
        } catch (MolangException ex) {
            onError.accept(ex.getMessage());
            return ZERO;
        }
    }

    public String source() {
        return this.source;
    }

    /** Evaluates to a {@link Double}, a {@link String}, or {@code null} when the result is unset. */
    public Object evaluate(MolangScope scope) {
        scope.clearTemps();
        Node.Frame frame = new Node.Frame(scope);
        Object value = this.root.eval(frame);
        return frame.flow == Node.Frame.RETURN ? frame.result : value;
    }

    public double evaluateNumber(MolangScope scope) {
        return Node.num(evaluate(scope));
    }

    public boolean evaluateBoolean(MolangScope scope) {
        return evaluateNumber(scope) != 0.0;
    }

    @Override
    public String toString() {
        return this.source;
    }
}
