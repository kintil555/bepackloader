package io.github.kintil555.bedrockpackloader.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Recursive-descent parser for Bedrock Molang (strings, {@code ??}, ternaries, loops, {@code return}). */
final class MolangParser {
    private enum Type { NUMBER, STRING, IDENT, OP, END }

    private record Token(Type type, String text, double number) {
    }

    private final List<Token> tokens;
    private final String source;
    private int pos;

    private MolangParser(String source) {
        this.source = source;
        this.tokens = lex(source);
    }

    static Node parse(String source) {
        MolangParser parser = new MolangParser(source);
        List<Node> statements = parser.statements(false);
        if (parser.peek().type() != Type.END) {
            throw parser.error("Unexpected '" + parser.peek().text() + "'");
        }
        return new Node.Program(statements.toArray(new Node[0]));
    }

    private MolangException error(String message) {
        return new MolangException(message + " in Molang expression: " + this.source);
    }

    // ---------------------------------------------------------------- lexer

    private static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    i++;
                }
                if (i + 1 < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int j = i + 1;
                    if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                        j++;
                    }
                    if (j < n && Character.isDigit(s.charAt(j))) {
                        while (j < n && Character.isDigit(s.charAt(j))) {
                            j++;
                        }
                        i = j;
                    }
                }
                String text = s.substring(start, i);
                try {
                    out.add(new Token(Type.NUMBER, text, Double.parseDouble(text)));
                } catch (NumberFormatException ex) {
                    throw new MolangException("Bad number '" + text + "' in Molang expression: " + s);
                }
                if (i < n && (s.charAt(i) == 'f' || s.charAt(i) == 'F')) {
                    i++;
                }
            } else if (c == '\'') {
                int end = s.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new MolangException("Unterminated string in Molang expression: " + s);
                }
                out.add(new Token(Type.STRING, s.substring(i + 1, end), 0));
                i = end + 1;
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) {
                    i++;
                }
                out.add(new Token(Type.IDENT, s.substring(start, i).toLowerCase(Locale.ROOT), 0));
            } else {
                String two = i + 1 < n ? s.substring(i, i + 2) : "";
                if (two.equals("??") || two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")
                        || two.equals("&&") || two.equals("||") || two.equals("->")) {
                    out.add(new Token(Type.OP, two, 0));
                    i += 2;
                } else if ("+-*/()[]{},;?:<>!=".indexOf(c) >= 0) {
                    out.add(new Token(Type.OP, String.valueOf(c), 0));
                    i++;
                } else {
                    throw new MolangException("Unexpected character '" + c + "' in Molang expression: " + s);
                }
            }
        }
        out.add(new Token(Type.END, "<end>", 0));
        return out;
    }

    // --------------------------------------------------------------- helpers

    private Token peek() {
        return this.tokens.get(this.pos);
    }

    private Token next() {
        return this.tokens.get(this.pos++);
    }

    private boolean isOp(String op) {
        Token t = peek();
        return t.type() == Type.OP && t.text().equals(op);
    }

    private boolean accept(String op) {
        if (isOp(op)) {
            this.pos++;
            return true;
        }
        return false;
    }

    private void expect(String op) {
        if (!accept(op)) {
            throw error("Expected '" + op + "' but found '" + peek().text() + "'");
        }
    }

    // ------------------------------------------------------------ statements

    private List<Node> statements(boolean inBlock) {
        List<Node> result = new ArrayList<>();
        while (true) {
            while (accept(";")) {
                // skip empty statements
            }
            if (peek().type() == Type.END || (inBlock && isOp("}"))) {
                return result;
            }
            result.add(statement());
            if (!accept(";") && peek().type() != Type.END && !(inBlock && isOp("}"))) {
                throw error("Expected ';' but found '" + peek().text() + "'");
            }
        }
    }

    private Node.Block block() {
        expect("{");
        List<Node> body = statements(true);
        expect("}");
        return new Node.Block(body.toArray(new Node[0]));
    }

    private Node statement() {
        if (isOp("{")) {
            return block();
        }
        Token t = peek();
        if (t.type() == Type.IDENT) {
            switch (t.text()) {
                case "return": {
                    this.pos++;
                    boolean empty = peek().type() == Type.END || isOp(";") || isOp("}");
                    return new Node.Return(empty ? null : expression());
                }
                case "break":
                    this.pos++;
                    return new Node.Jump(Node.Frame.BREAK);
                case "continue":
                    this.pos++;
                    return new Node.Jump(Node.Frame.CONTINUE);
                case "loop": {
                    this.pos++;
                    expect("(");
                    Node count = expression();
                    expect(",");
                    Node.Block body = block();
                    expect(")");
                    return new Node.Loop(count, body);
                }
                case "for_each":
                    throw error("for_each is not supported");
                default:
                    break;
            }
        }
        return expression();
    }

    // ----------------------------------------------------------- expressions

    private Node expression() {
        Node left = ternary();
        if (isOp("=")) {
            if (!(left instanceof Node.Ref) && !(left instanceof Node.ArrayRef)) {
                throw error("Invalid assignment target");
            }
            this.pos++;
            return new Node.Assign(left, expression());
        }
        return left;
    }

    private Node ternary() {
        Node condition = nullish();
        if (accept("?")) {
            Node then = branch();
            Node otherwise = accept(":") ? branch() : null;
            return new Node.Ternary(condition, then, otherwise);
        }
        return condition;
    }

    /** A ternary branch: an expression, a {@code { ... }} block, or {@code return}/{@code break}/{@code continue}. */
    private Node branch() {
        if (isOp("{")) {
            return block();
        }
        Token t = peek();
        if (t.type() == Type.IDENT && (t.text().equals("return") || t.text().equals("break") || t.text().equals("continue"))) {
            return statement();
        }
        return ternary();
    }

    private Node nullish() {
        Node left = or();
        while (accept("??")) {
            left = new Node.Binary("??", left, or());
        }
        return left;
    }

    private Node or() {
        Node left = and();
        while (accept("||")) {
            left = new Node.Binary("||", left, and());
        }
        return left;
    }

    private Node and() {
        Node left = equality();
        while (accept("&&")) {
            left = new Node.Binary("&&", left, equality());
        }
        return left;
    }

    private Node equality() {
        Node left = relational();
        while (isOp("==") || isOp("!=")) {
            String op = next().text();
            left = new Node.Binary(op, left, relational());
        }
        return left;
    }

    private Node relational() {
        Node left = additive();
        while (isOp("<") || isOp("<=") || isOp(">") || isOp(">=")) {
            String op = next().text();
            left = new Node.Binary(op, left, additive());
        }
        return left;
    }

    private Node additive() {
        Node left = multiplicative();
        while (isOp("+") || isOp("-")) {
            String op = next().text();
            left = new Node.Binary(op, left, multiplicative());
        }
        return left;
    }

    private Node multiplicative() {
        Node left = unary();
        while (isOp("*") || isOp("/")) {
            String op = next().text();
            left = new Node.Binary(op, left, unary());
        }
        return left;
    }

    private Node unary() {
        if (accept("-")) {
            return new Node.Unary('-', unary());
        }
        if (accept("+")) {
            return unary();
        }
        if (accept("!")) {
            return new Node.Unary('!', unary());
        }
        return postfix();
    }

    /** {@code a->b} reads a value from another entity, which cannot be resolved; it evaluates to 0. */
    private Node postfix() {
        Node node = primary();
        while (accept("->")) {
            primary();
            node = new Node.Unsupported("->");
        }
        return node;
    }

    private Node primary() {
        Token t = next();
        switch (t.type()) {
            case NUMBER:
                return new Node.Num(t.number());
            case STRING:
                return new Node.Str(t.text());
            case IDENT:
                return identifier(t.text());
            case OP:
                if (t.text().equals("(")) {
                    Node inner = expression();
                    expect(")");
                    return inner;
                }
                break;
            default:
                break;
        }
        throw error("Unexpected '" + t.text() + "'");
    }

    private Node[] arguments() {
        List<Node> args = new ArrayList<>();
        if (!isOp(")")) {
            do {
                args.add(expression());
            } while (accept(","));
        }
        expect(")");
        return args.toArray(new Node[0]);
    }

    private Node identifier(String name) {
        switch (name) {
            case "true":
                return new Node.Num(1.0);
            case "false":
                return new Node.Num(0.0);
            case "math.pi":
                return new Node.Num(Math.PI);
            case "this":
                return new Node.Ref('c', "this");
            default:
                break;
        }
        int dot = name.indexOf('.');
        if (dot < 0) {
            throw error("Unknown identifier '" + name + "'");
        }
        String prefix = name.substring(0, dot);
        String rest = name.substring(dot + 1);
        boolean call = accept("(");
        switch (prefix) {
            case "math": {
                MolangMath.Fn fn = MolangMath.find(rest);
                if (fn == null || !call) {
                    throw error("Unknown math function '" + name + "'");
                }
                return new Node.MathCall(fn, arguments());
            }
            case "q", "query":
                return new Node.Query(rest, call ? arguments() : new Node[0]);
            case "v", "variable":
                return reference('v', rest, call);
            case "t", "temp":
                return reference('t', rest, call);
            case "c", "context":
                return reference('c', rest, call);
            case "array": {
                if (call || !accept("[")) {
                    throw error("Array '" + name + "' needs an index");
                }
                Node index = expression();
                expect("]");
                return new Node.ArrayRef(rest, index);
            }
            case "geometry", "texture", "material":
                if (call) {
                    throw error("'" + name + "' is not callable");
                }
                return new Node.Str(name);
            default:
                throw error("Unknown identifier '" + name + "'");
        }
    }

    private Node reference(char kind, String rest, boolean call) {
        if (call) {
            throw error("'" + rest + "' is not callable");
        }
        return new Node.Ref(kind, rest);
    }
}
