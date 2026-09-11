package bsh;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UnaryExpressionTest {

    // Initial value, increment, decrement, unary plus, minus, and complement.
    private static final Object[][] NUMBERS = {
        { (byte) 7, (byte) 8, (byte) 6, 7, -7, ~7 },
        { (short) 7, (short) 8, (short) 6, 7, -7, ~7 },
        { 'A', 'B', '@', 65, -65, ~65 },
        { 7, 8, 6, 7, -7, ~7 },
        { 7L, 8L, 6L, 7L, -7L, ~7L },
        { 7.25f, 8.25f, 6.25f, 7.25f, -7.25f, null },
        { 7.25d, 8.25d, 6.25d, 7.25d, -7.25d, null }
    };

    @Test
    public void boxed_numeric_operators() throws Exception {
        assertOperators(NUMBERS, false);
    }

    @Test
    public void primitive_numeric_operators() throws Exception {
        assertOperators(NUMBERS, true);
    }

    @Test
    public void big_number_operators() throws Exception {
        BigInteger integer = new BigInteger("123456789012345678901234567890");
        BigDecimal decimal = new BigDecimal("123456789012345678901234567890.125");
        Object[][] numbers = {
            { integer, integer.add(BigInteger.ONE), integer.subtract(BigInteger.ONE),
                integer, integer.negate(), integer.not() },
            { decimal, decimal.add(BigDecimal.ONE), decimal.subtract(BigDecimal.ONE),
                decimal, decimal.negate(), decimal.negate() }
        };
        assertOperators(numbers, false);
        assertOperators(numbers, true);
    }

    @Test
    public void big_decimal_scale_normalization() throws Exception {
        BigDecimal initial = new BigDecimal("2");
        assertUnary(initial, "+value", new BigDecimal("2.0"), initial, true);
        assertUnary(initial, "++value", new BigDecimal("3.0"),
                new BigDecimal("3.0"), false);
        assertUnary(initial, "value++", initial, new BigDecimal("3.0"), false);
        BigDecimal negative = new BigDecimal("-2.125");
        assertUnary(negative, "~value", negative, negative, true);
    }

    @Test
    public void big_number_literals_remain_primitives() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("integer = -123456789012345678901234567890W;"
                + "decimal = -123456789012345678901234567890.125w;");
        assertValue(new BigInteger("-123456789012345678901234567890"),
                interpreter.getNameSpace().getVariable("integer"), true);
        assertValue(new BigDecimal("-123456789012345678901234567890.125"),
                interpreter.getNameSpace().getVariable("decimal"), true);
    }

    @Test
    public void small_integral_overflow() throws Exception {
        Object[][] limits = {
            { Byte.MAX_VALUE, Byte.MIN_VALUE },
            { Short.MAX_VALUE, Short.MIN_VALUE },
            { Character.MAX_VALUE, Character.MIN_VALUE }
        };
        for (Object[] limit : limits) {
            assertUnary(limit[0], "++value", limit[1], limit[1], false);
            assertUnary(limit[0], "value++", limit[0], limit[1], false);
            assertUnary(limit[1], "--value", limit[0], limit[0], false);
            assertUnary(limit[1], "value--", limit[1], limit[0], false);
        }
    }

    @Test
    public void typed_boxed_variables() throws Exception {
        for (Object[] row : NUMBERS) {
            Interpreter interpreter = new Interpreter();
            interpreter.set("initial", row[0]);
            interpreter.eval(row[0].getClass().getSimpleName()
                    + " value = initial; result = ++value;");
            assertValue(row[1], interpreter.getNameSpace().getVariable("result"), false);
            assertValue(row[1], interpreter.getNameSpace().getVariable("value"), false);
            interpreter.eval("result = value--;");
            assertValue(row[1], interpreter.getNameSpace().getVariable("result"), false);
            assertValue(row[0], interpreter.getNameSpace().getVariable("value"), false);
        }
    }

    @Test
    public void issue_762() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("Integer i = new Integer(0); result = ++i;");
        assertValue(1, interpreter.getNameSpace().getVariable("result"), false);
        interpreter.eval("result = --i;");
        assertValue(0, interpreter.getNameSpace().getVariable("result"), false);
        assertValue(0, interpreter.getNameSpace().getVariable("i"), false);
    }

    @Test
    public void boxed_array_elements() throws Exception {
        for (Object[] row : NUMBERS) {
            Object values = Array.newInstance(row[0].getClass(), 1);
            Array.set(values, 0, row[0]);
            Interpreter interpreter = new Interpreter();
            interpreter.set("values", values);
            interpreter.eval("result = ++values[0];");
            assertValue(row[1], interpreter.getNameSpace().getVariable("result"), false);
            assertValue(row[1], Array.get(values, 0), false);
        }
    }

    @Test
    public void field_receiver_is_evaluated_once() throws Exception {
        Operand operand = new Operand();
        Integer original = operand.value;
        Interpreter interpreter = new Interpreter();
        interpreter.set("operand", operand);
        interpreter.eval("result = operand.receiver().value++;");
        assertSame(original, interpreter.getNameSpace().getVariable("result"));
        assertValue(1001, operand.value, false);
        assertEquals(1, operand.receiverCalls);
    }

    @Test
    public void array_index_is_evaluated_once() throws Exception {
        Operand operand = new Operand();
        Interpreter interpreter = new Interpreter();
        interpreter.set("operand", operand);
        interpreter.eval("result = ++operand.values[operand.index()];");
        assertValue(1001, interpreter.getNameSpace().getVariable("result"), false);
        assertValue(1001, operand.values[0], false);
        assertEquals(1, operand.indexCalls);
    }

    @Test
    public void boxed_method_result_is_evaluated_once() throws Exception {
        Operand operand = new Operand();
        Interpreter interpreter = new Interpreter();
        interpreter.set("operand", operand);
        interpreter.eval("result = -operand.boxedValue();");
        assertValue(-1000, interpreter.getNameSpace().getVariable("result"), true);
        assertEquals(1, operand.methodCalls);
    }

    @Test
    public void scripted_boxed_field() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("class Holder { Integer value = 1000; }"
                + "holder = new Holder(); result = ++holder.value;");
        assertValue(1001, interpreter.getNameSpace().getVariable("result"), false);
        assertEquals(1001, interpreter.eval("holder.value;"));
    }

    @Test
    public void boolean_negation() throws Exception {
        assertUnary(Boolean.TRUE, "!value", Boolean.FALSE, Boolean.TRUE, true);
        assertUnary(Boolean.FALSE, "!value", Boolean.TRUE, Boolean.FALSE, true);
        assertUnary(Primitive.TRUE, "!value", Boolean.FALSE, Boolean.TRUE, true);
        assertUnary(Primitive.FALSE, "!value", Boolean.TRUE, Boolean.FALSE, true);
    }

    @Test
    public void unsupported_objects_are_still_rejected() throws Exception {
        for (Object value : new Object[] { "7", new Object(), new AtomicInteger(7) }) {
            Interpreter interpreter = new Interpreter();
            interpreter.set("value", value);
            assertError(interpreter, "++value;", "inappropriate for object");
            assertSame(value, interpreter.getNameSpace().getVariable("value"));
        }
    }

    @Test
    public void null_void_and_invalid_boolean_operations() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("Integer value = null;");
        assertError(interpreter, "++value;", "illegal use of null");
        assertSame(Primitive.NULL, interpreter.getNameSpace().getVariable("value"));
        assertError(interpreter, "-undefinedUnaryOperand;", "illegal use of undefined");
        interpreter = new Interpreter();
        interpreter.set("value", Boolean.TRUE);
        assertError(interpreter, "++value;", "inappropriate for boolean");
        assertValue(Boolean.TRUE, interpreter.getNameSpace().getVariable("value"), false);
    }

    @Test
    public void final_boxed_variable_is_not_modified() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("final Integer value = 7;");
        assertError(interpreter, "++value;", "final variable");
        assertValue(7, interpreter.getNameSpace().getVariable("value"), false);
    }

    private static void assertOperators(Object[][] numbers, boolean primitive)
            throws Exception {
        for (Object[] row : numbers) {
            Object initial = primitive ? Primitive.wrap(row[0],
                    Primitive.unboxType(row[0].getClass())) : row[0];
            assertUnary(initial, "++value", row[1], row[1], primitive);
            assertUnary(initial, "value++", row[0], row[1], primitive);
            assertUnary(initial, "--value", row[2], row[2], primitive);
            assertUnary(initial, "value--", row[0], row[2], primitive);
            assertUnary(initial, "+value", row[3], row[0], true);
            assertUnary(initial, "-value", row[4], row[0], true);
            if (row[5] != null)
                assertUnary(initial, "~value", row[5], row[0], true);
        }
    }

    private static void assertUnary(Object initial, String expression, Object expected,
            Object stored, boolean primitiveResult) throws Exception {
        for (boolean strict : new boolean[] { false, true }) {
            Interpreter interpreter = new Interpreter();
            interpreter.setStrictJava(strict);
            interpreter.set("value", initial);
            interpreter.set("result", Primitive.NULL);
            interpreter.eval("result = " + expression + ";");
            Object result = interpreter.getNameSpace().getVariable("result");
            assertValue(expected, result, primitiveResult);
            Object actualStored = interpreter.getNameSpace().getVariable("value");
            assertValue(stored, actualStored, initial instanceof Primitive);
            if (expression.endsWith("++") || expression.endsWith("--"))
                assertSame(initial, result);
            else if (expression.startsWith("++") || expression.startsWith("--"))
                assertSame(actualStored, result);
        }
    }

    private static void assertValue(Object expected, Object actual, boolean primitive) {
        assertEquals("Primitive representation", primitive, actual instanceof Primitive);
        Object value = Primitive.unwrap(actual);
        assertEquals("Value type", expected.getClass(), value.getClass());
        assertEquals(expected, value);
    }

    private static void assertError(Interpreter interpreter, String script, String message)
            throws Exception {
        try {
            interpreter.eval(script);
            fail("Expected an error for " + script);
        } catch (EvalError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
        }
    }

    public static class Operand {
        public Integer value = 1000;
        public Integer[] values = { 1000 };
        public int receiverCalls;
        public int indexCalls;
        public int methodCalls;

        public Operand receiver() { receiverCalls++; return this; }
        public int index() { indexCalls++; return 0; }
        public Integer boxedValue() { methodCalls++; return value; }
    }
}
