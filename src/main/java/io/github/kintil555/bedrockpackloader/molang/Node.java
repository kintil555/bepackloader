package io.github.kintil555.bedrockpackloader.molang;

import java.util.List;

/** Compiled Molang syntax tree. Values are {@link Double}, {@link String} or {@code null} (unset). */
sealed interface Node {
    /** Mutable state of one top-level evaluation. */
    final class Frame {
        static final int NONE = 0;
        static final int BREAK = 1;
        static final int CONTINUE = 2;
        static final int RETURN = 3;

        final MolangScope scope;
        int flow = NONE;
        Object result;

        Frame(MolangScope scope) {
            this.scope = scope;
        }
    }

    int MAX_LOOP = 1024;

    Object eval(Frame frame);

    static double num(Object value) {
        return value instanceof Double d ? d : 0.0;
    }

    static Double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }

    record Num(Double value) implements Node {
        @Override
        public Object eval(Frame frame) {
            return this.value;
        }
    }

    record Str(String value) implements Node {
        @Override
        public Object eval(Frame frame) {
            return this.value;
        }
    }

    /** Variable ({@code v}), temp ({@code t}) or context ({@code c}) read. */
    record Ref(char kind, String name) implements Node {
        @Override
        public Object eval(Frame frame) {
            return frame.scope.read(this.kind, this.name);
        }
    }

    record Query(String name, Node[] args) implements Node {
        @Override
        public Object eval(Frame frame) {
            Object[] values = new Object[this.args.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = this.args[i].eval(frame);
            }
            Object result = frame.scope.context().query(this.name, values);
            if (result == null) {
                frame.scope.reportUnsupported(this.name);
                return 0.0;
            }
            return result;
        }
    }

    record MathCall(MolangMath.Fn fn, Node[] args) implements Node {
        @Override
        public Object eval(Frame frame) {
            double[] values = new double[this.args.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = num(this.args[i].eval(frame));
            }
            double result = this.fn.apply(values, frame.scope.random());
            return Double.isNaN(result) || Double.isInfinite(result) ? 0.0 : result;
        }
    }

    /** {@code array.name[index]}; the index is floored and clamped to the array bounds. */
    record ArrayRef(String name, Node index) implements Node {
        @Override
        public Object eval(Frame frame) {
            int i = (int) Math.floor(num(this.index.eval(frame)));
            List<Object> external = frame.scope.context().array(this.name);
            if (external != null) {
                return external.isEmpty() ? null : external.get(Math.max(0, Math.min(i, external.size() - 1)));
            }
            Object[] local = frame.scope.localArray(this.name);
            return local == null || local.length == 0 ? null : local[Math.max(0, Math.min(i, local.length - 1))];
        }
    }

    record Unary(char op, Node operand) implements Node {
        @Override
        public Object eval(Frame frame) {
            double v = num(this.operand.eval(frame));
            return this.op == '!' ? bool(v == 0.0) : (Object) (-v);
        }
    }

    record Binary(String op, Node left, Node right) implements Node {
        @Override
        public Object eval(Frame frame) {
            switch (this.op) {
                case "&&": {
                    return bool(num(this.left.eval(frame)) != 0.0 && num(this.right.eval(frame)) != 0.0);
                }
                case "||": {
                    return bool(num(this.left.eval(frame)) != 0.0 || num(this.right.eval(frame)) != 0.0);
                }
                case "??": {
                    Object l = this.left.eval(frame);
                    return l != null ? l : this.right.eval(frame);
                }
                default:
                    break;
            }
            Object l = this.left.eval(frame);
            Object r = this.right.eval(frame);
            if (this.op.equals("==") || this.op.equals("!=")) {
                boolean equal = l instanceof String || r instanceof String ? java.util.Objects.equals(l, r) : num(l) == num(r);
                return bool(this.op.equals("==") == equal);
            }
            double a = num(l);
            double b = num(r);
            return switch (this.op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b == 0.0 ? 0.0 : a / b;
                case "<" -> bool(a < b);
                case "<=" -> bool(a <= b);
                case ">" -> bool(a > b);
                case ">=" -> bool(a >= b);
                default -> 0.0;
            };
        }
    }

    /** {@code cond ? a : b}; {@code otherwise} is {@code null} for the binary form {@code cond ? a}. */
    record Ternary(Node condition, Node then, Node otherwise) implements Node {
        @Override
        public Object eval(Frame frame) {
            if (num(this.condition.eval(frame)) != 0.0) {
                return this.then.eval(frame);
            }
            return this.otherwise == null ? 0.0 : this.otherwise.eval(frame);
        }
    }

    /** Assignment to a {@link Ref} or {@link ArrayRef}; evaluates to {@code null}. */
    record Assign(Node target, Node value) implements Node {
        @Override
        public Object eval(Frame frame) {
            Object v = this.value.eval(frame);
            if (this.target instanceof Ref ref) {
                frame.scope.write(ref.kind(), ref.name(), v);
            } else if (this.target instanceof ArrayRef ref) {
                frame.scope.writeArray(ref.name(), (int) Math.max(0, Math.floor(num(ref.index().eval(frame)))), v);
            }
            return null;
        }
    }

    /** Syntax that parses but cannot be evaluated (e.g. {@code ->}); evaluates to 0. */
    record Unsupported(String what) implements Node {
        @Override
        public Object eval(Frame frame) {
            return 0.0;
        }
    }

    record Return(Node value) implements Node {
        @Override
        public Object eval(Frame frame) {
            frame.result = this.value == null ? 0.0 : this.value.eval(frame);
            frame.flow = Frame.RETURN;
            return null;
        }
    }

    record Jump(int flow) implements Node {
        @Override
        public Object eval(Frame frame) {
            frame.flow = this.flow;
            return null;
        }
    }

    record Block(Node[] statements) implements Node {
        @Override
        public Object eval(Frame frame) {
            Object last = null;
            for (Node statement : this.statements) {
                last = statement.eval(frame);
                if (frame.flow != Frame.NONE) {
                    return null;
                }
            }
            return last;
        }
    }

    /**
     * Top-level statement list. A single plain expression yields its own value; several statements yield
     * the value of {@code return} (or 0 when none is executed), as in Bedrock.
     */
    record Program(Node[] statements) implements Node {
        @Override
        public Object eval(Frame frame) {
            Object last = null;
            for (Node statement : this.statements) {
                last = statement.eval(frame);
                if (frame.flow != Frame.NONE) {
                    return null;
                }
            }
            return this.statements.length == 1 ? last : (Object) 0.0;
        }
    }

    record Loop(Node count, Block body) implements Node {
        @Override
        public Object eval(Frame frame) {
            int n = (int) Math.min(MAX_LOOP, Math.max(0, Math.floor(num(this.count.eval(frame)))));
            for (int i = 0; i < n; i++) {
                this.body.eval(frame);
                if (frame.flow == Frame.BREAK) {
                    frame.flow = Frame.NONE;
                    break;
                }
                if (frame.flow == Frame.CONTINUE) {
                    frame.flow = Frame.NONE;
                } else if (frame.flow == Frame.RETURN) {
                    return null;
                }
            }
            return null;
        }
    }
}
