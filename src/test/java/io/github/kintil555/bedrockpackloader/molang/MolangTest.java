package io.github.kintil555.bedrockpackloader.molang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MolangTest {
    private static final MolangContext CONTEXT = new MolangContext() {
        @Override
        public Object query(String name, Object[] args) {
            return switch (name) {
                case "is_on_ground" -> 1.0;
                case "ground_speed" -> 2.5;
                case "is_item_name_any" -> {
                    for (int i = 1; i < args.length; i++) {
                        if (args[0] instanceof String slot && args[i] instanceof String item && item.equals("minecraft:bow")
                                && slot.equals("slot.weapon.mainhand")) {
                            yield 1.0;
                        }
                    }
                    yield 0.0;
                }
                case "get_equipped_item_name" -> "minecraft:bow";
                default -> null;
            };
        }

        @Override
        public List<Object> array(String name) {
            return name.equals("skins") ? List.of("texture.a", "texture.b", "texture.c") : null;
        }
    };

    private static double num(String source) {
        return MolangExpression.compile(source).evaluateNumber(new MolangScope(CONTEXT, new Random(1)));
    }

    @Test
    void arithmeticAndPrecedence() {
        assertEquals(7.0, num("1 + 2 * 3"), 1e-9);
        assertEquals(9.0, num("(1 + 2) * 3"), 1e-9);
        assertEquals(-4.0, num("-(1 + 3)"), 1e-9);
        assertEquals(0.0, num("5 / 0"), 1e-9);
        assertEquals(1.0, num("1 < 2 && 2 <= 2 && 3 > 2 && 3 >= 3"), 1e-9);
        assertEquals(1.0, num("!0"), 1e-9);
    }

    @Test
    void mathUsesDegrees() {
        assertEquals(1.0, num("math.sin(90)"), 1e-9);
        assertEquals(-1.0, num("math.cos(180)"), 1e-9);
        assertEquals(45.0, num("math.atan(1)"), 1e-9);
        assertEquals(3.0, num("math.clamp(10, 0, 3)"), 1e-9);
        assertEquals(0.5, num("math.hermite_blend(0.5)"), 1e-9);
        assertEquals(10.0, num("math.lerp(0, 20, 0.5)"), 1e-9);
        assertEquals(-170.0, num("math.min_angle(190)"), 1e-9);
        assertEquals(Math.PI, num("math.pi"), 1e-9);
    }

    @Test
    void easingEndpointsAreExact() {
        for (String name : List.of("sine", "quad", "cubic", "quart", "quint", "expo", "circ", "back", "elastic", "bounce")) {
            for (String mode : List.of("ease_in_", "ease_out_", "ease_in_out_")) {
                assertEquals(10.0, num("math." + mode + name + "(10, 20, 0)"), 1e-6, mode + name + " at 0");
                assertEquals(20.0, num("math." + mode + name + "(10, 20, 1)"), 1e-6, mode + name + " at 1");
            }
        }
    }

    @Test
    void queriesAndStrings() {
        assertEquals(1.0, num("q.is_on_ground"), 1e-9);
        assertEquals(1.0, num("query.is_on_ground"), 1e-9);
        assertEquals(5.0, num("q.ground_speed * 2"), 1e-9);
        assertEquals(1.0, num("q.is_item_name_any('slot.weapon.mainhand', 0, 'minecraft:bow')"), 1e-9);
        assertEquals(1.0, num("q.get_equipped_item_name == 'minecraft:bow'"), 1e-9);
        assertEquals(0.0, num("q.get_equipped_item_name == 'minecraft:stick'"), 1e-9);
        assertEquals(1.0, num("Q.IS_ON_GROUND"), 1e-9, "identifiers are case-insensitive");
    }

    @Test
    void unsupportedQueryEvaluatesToZeroAndIsReportedOnce() {
        List<String> reported = new ArrayList<>();
        MolangScope scope = new MolangScope(new MolangContext() {
            @Override
            public Object query(String name, Object[] args) {
                return null;
            }

            @Override
            public void unsupportedQuery(String name) {
                reported.add(name);
            }
        });
        MolangExpression expr = MolangExpression.compile("q.nope + q.nope");
        assertEquals(0.0, expr.evaluateNumber(scope), 1e-9);
        expr.evaluate(scope);
        assertEquals(List.of("nope"), reported);
    }

    @Test
    void nullCoalescing() {
        assertEquals(7.0, num("v.unset ?? 7"), 1e-9);
        assertEquals(0.0, num("v.unset"), 1e-9);
        MolangScope scope = new MolangScope(CONTEXT);
        scope.setVariable("set", 3.0);
        assertEquals(3.0, MolangExpression.compile("v.set ?? 7").evaluateNumber(scope), 1e-9);
        assertEquals(2.0, num("(v.a ?? v.b) ?? 2"), 1e-9);
    }

    @Test
    void ternaries() {
        assertEquals(1.0, num("1 ? 1 : 2"), 1e-9);
        assertEquals(2.0, num("0 ? 1 : 2"), 1e-9);
        assertEquals(0.0, num("0 ? 5"), 1e-9);
        assertEquals(3.0, num("0 ? 1 : 1 ? 3 : 4"), 1e-9);
    }

    @Test
    void assignmentsPersistVariablesButNotTemps() {
        MolangScope scope = new MolangScope(CONTEXT);
        MolangExpression.compile("v.x = 4; t.y = v.x * 2; v.z = t.y + 1;").evaluate(scope);
        assertEquals(4.0, scope.variable("x"));
        assertEquals(9.0, scope.variable("z"));
        assertEquals(0.0, MolangExpression.compile("t.y").evaluateNumber(scope), 1e-9);
    }

    @Test
    void returnAndLoops() {
        assertEquals(6.0, num("t.s = 0; loop(4, { t.s = t.s + 1; t.s == 3 ? { }; }); return 6;"), 1e-9);
        assertEquals(10.0, num("t.s = 0; t.i = 0; loop(100, { t.i = t.i + 1; t.s = t.s + t.i; (t.i >= 4) ? break; }); return t.s;"), 1e-9);
        assertEquals(0.0, num("t.a = 1; t.b = 2;"), 1e-9, "no return in a multi-statement expression yields 0");
        assertEquals(5.0, num("t.n = 0; loop(3, { t.n = t.n + 1; (t.n == 2) ? return 5; }); return 9;"), 1e-9);
    }

    @Test
    void arraysClampTheIndex() {
        MolangScope scope = new MolangScope(CONTEXT);
        assertEquals("texture.b", MolangExpression.compile("array.skins[1]").evaluate(scope));
        assertEquals("texture.c", MolangExpression.compile("array.skins[99]").evaluate(scope));
        assertEquals("texture.a", MolangExpression.compile("array.skins[-3]").evaluate(scope));
    }

    @Test
    void resourceReferencesAreStrings() {
        MolangScope scope = new MolangScope(CONTEXT);
        assertEquals("texture.default", MolangExpression.compile("Texture.Default").evaluate(scope));
        assertEquals("geometry.cape", MolangExpression.compile("q.is_on_ground ? Geometry.cape : Geometry.default").evaluate(scope));
    }

    @Test
    void entityReferenceArrowEvaluatesToZero() {
        assertEquals(0.0, num("q.is_on_ground->v.something"), 1e-9);
    }

    @Test
    void invalidExpressionsThrowAndLenientFallsBackToZero() {
        assertThrows(MolangException.class, () -> MolangExpression.compile("1 +"));
        assertThrows(MolangException.class, () -> MolangExpression.compile("math.nope(1)"));
        assertThrows(MolangException.class, () -> MolangExpression.compile("'unterminated"));
        assertThrows(MolangException.class, () -> MolangExpression.compile("5 = 3"));
        List<String> errors = new ArrayList<>();
        MolangExpression lenient = MolangExpression.compileLenient("1 +", errors::add);
        assertEquals(0.0, lenient.evaluateNumber(new MolangScope(CONTEXT)), 1e-9);
        assertTrue(errors.size() == 1 && errors.get(0).contains("1 +"));
    }

    @Test
    void trailingFloatSuffixAndLeadingDot() {
        assertEquals(1.5, num("1.5f"), 1e-9);
        assertEquals(0.25, num(".25"), 1e-9);
    }

    @Test
    void scientificNotationStandaloneBlocksAndThis() {
        assertEquals(1.0E-4, num("1.0E-4"), 1e-12);
        assertEquals(250.0, num("2.5e2"), 1e-9);
        assertEquals(3.0, num("{ t.a = 3; }; return t.a;"), 1e-9);
        MolangScope scope = new MolangScope(CONTEXT);
        scope.setContextValue("this", 10.0);
        assertEquals(8.0, MolangExpression.compile("this - 2").evaluateNumber(scope), 1e-9);
    }
}
