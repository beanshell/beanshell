package bsh;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public strictfp class FloatArithmeticTest {

    private static final String[] OPERATORS = { "+", "-", "*", "/", "%" };

    @Test
    public void issue_767_and_method_selection() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertFloat(2f, interpreter.eval("float a = 1; float b = 2; a * b;"));
        assertEquals(false, interpreter.eval("Float.isInfinite(1f * 2f);"));
        assertEquals(false, interpreter.eval("Float.isInfinite(1f * 2);"));
        interpreter.eval("choose(float x) { return \"float\"; }"
                + "choose(double x) { return \"double\"; }");
        assertEquals("float", interpreter.eval("choose(1f * 2f);"));
        assertEquals("float", interpreter.eval("choose(1f * 2);"));
        assertEquals("double", interpreter.eval("choose(1f * 2d);"));
    }

    @Test
    public void float_and_integral_operands_use_float_in_both_orders() throws Exception {
        Object[] integers = { (byte) 7, (short) 7, 'A', 16777217, 16777217L,
                Long.MAX_VALUE };
        for (boolean strict : new boolean[] { false, true }) {
            for (boolean boxed : new boolean[] { false, true }) {
                Interpreter interpreter = new Interpreter();
                interpreter.setStrictJava(strict);
                for (Object integer : integers) {
                    float promoted = integer instanceof Character ? (Character) integer
                            : ((Number) integer).floatValue();
                    interpreter.set("value", boxed ? (Object) Float.valueOf(1f)
                            : new Primitive(1f));
                    interpreter.set("integer", boxed ? integer : Primitive.wrap(integer,
                            Primitive.unboxType(integer.getClass())));
                    for (String operator : OPERATORS) {
                        assertFloat(javaResult(1f, promoted, operator),
                                interpreter.eval("value " + operator + " integer;"));
                        assertFloat(javaResult(promoted, 1f, operator),
                                interpreter.eval("integer " + operator + " value;"));
                    }
                }
            }
        }
    }

    @Test
    public void promotion_happens_before_arithmetic() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertFloat(16777216f + 1, interpreter.eval("16777216f + 1;"));
        assertFloat(1f / 16777217, interpreter.eval("1f / 16777217;"));
        // Computing in double and narrowing afterward changes this result.
        assertTrue(Float.floatToIntBits(1f / 16777217)
                != Float.floatToIntBits((float) (1d / 16777217)));
        assertEquals(16777216f == 16777217,
                interpreter.eval("16777216f == 16777217;"));
        assertEquals(16777216f < 16777217,
                interpreter.eval("16777216f < 16777217;"));
    }

    @Test
    public void arithmetic_matches_java_at_float_boundaries() throws Exception {
        float[] values = { Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -16f, -1f,
                -0.1f, -Float.MIN_NORMAL, -Float.MIN_VALUE, -0f, 0f, Float.MIN_VALUE,
                Float.MIN_NORMAL, 0.1f, 1f, 16f, Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NaN };
        for (boolean strict : new boolean[] { false, true }) {
            for (boolean boxed : new boolean[] { false, true }) {
                Interpreter interpreter = new Interpreter();
                interpreter.setStrictJava(strict);
                for (float lhs : values) {
                    for (float rhs : values) {
                        interpreter.set("lhs", boxed ? (Object) Float.valueOf(lhs)
                                : new Primitive(lhs));
                        interpreter.set("rhs", boxed ? (Object) Float.valueOf(rhs)
                                : new Primitive(rhs));
                        for (String operator : OPERATORS)
                            assertFloat(javaResult(lhs, rhs, operator),
                                    interpreter.eval("lhs " + operator + " rhs;"));
                    }
                }
            }
        }
    }

    @Test
    public void negative_multiplication_preserves_rounding_and_zero_sign() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertFloat(-0.1f * 0.1f, interpreter.eval("-0.1f * 0.1f;"));
        assertFloat(-1f * 0f, interpreter.eval("-1f * 0f;"));
        assertFloat(0f * -1f, interpreter.eval("0f * -1f;"));
    }

    @Test
    public void overflow_and_underflow_keep_float_results() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertFloat(Float.MAX_VALUE * 2f, interpreter.eval("Float.MAX_VALUE * 2f;"));
        assertFloat(Float.MAX_VALUE * -2f, interpreter.eval("Float.MAX_VALUE * -2f;"));
        assertFloat(Float.MIN_VALUE / 2f, interpreter.eval("Float.MIN_VALUE / 2f;"));
        assertFloat(1f / 0f, interpreter.eval("1f / 0f;"));
        assertFloat(0f / 0f, interpreter.eval("0f / 0f;"));
    }

    @Test
    public void double_operands_and_results_remain_double() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals(Double.valueOf(1f + 2d), interpreter.eval("1f + 2d;"));
        assertEquals(Double.valueOf(2d + 1f), interpreter.eval("2d + 1f;"));
        assertEquals(Double.valueOf(1d + 2d), interpreter.eval("1d + 2d;"));
    }

    @Test
    public void compound_assignment_rounds_each_float_operation() throws Exception {
        Interpreter interpreter = new Interpreter();
        float value = 1f;
        value /= 16777217;
        assertFloat(value, interpreter.eval("float value = 1f; value /= 16777217; value;"));
        value *= -2;
        assertFloat(value, interpreter.eval("value *= -2; value;"));
    }

    @Test
    public void long_compound_assignment_uses_float_promotion() throws Exception {
        Interpreter interpreter = new Interpreter();
        long value = 9223370937343148032L;
        interpreter.eval("long value = 9223370937343148032L;");
        for (int count = 0; count < 2; count++) {
            value += 274877923328f;
            assertEquals(Long.valueOf(value),
                    interpreter.eval("value += 274877923328f; value;"));
        }
        assertEquals(9223370937343148032L, value);
    }

    @Test
    public void big_number_and_power_extensions_are_preserved() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals(Double.valueOf(3d), interpreter.eval("1f + 2W;"));
        assertEquals(Double.valueOf(3d), interpreter.eval("2W + 1f;"));
        assertEquals(new BigDecimal("3.0"), interpreter.eval("1f + 2.0w;"));
        assertEquals(new BigDecimal("3.0"), interpreter.eval("2.0w + 1f;"));
        assertEquals(Double.valueOf(Math.pow(2f, 3f)), interpreter.eval("2f ** 3f;"));
        assertEquals(Double.valueOf(Math.pow(2f, 3)), interpreter.eval("2f ** 3;"));
    }

    @Test
    public void shifts_still_reject_float_operands() throws Exception {
        Interpreter interpreter = new Interpreter();
        for (String operator : new String[] { "<<", ">>", ">>>" }) {
            try {
                interpreter.eval("1f " + operator + " 1;");
                fail("Expected float shift to fail");
            } catch (EvalError e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Can't shift"));
            }
        }
    }

    private static float javaResult(float lhs, float rhs, String operator) {
        switch (operator) {
            case "+": return lhs + rhs;
            case "-": return lhs - rhs;
            case "*": return lhs * rhs;
            case "/": return lhs / rhs;
            case "%": return lhs % rhs;
            default: throw new AssertionError(operator);
        }
    }

    private static void assertFloat(float expected, Object actual) {
        assertEquals("Result type", Float.class, actual.getClass());
        // Canonicalize NaNs, but preserve the distinction between signed zeros.
        assertEquals("Float result", Float.floatToIntBits(expected),
                Float.floatToIntBits((Float) actual));
    }
}
