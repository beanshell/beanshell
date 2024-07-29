package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class CastTest {

    @Test
    public void test_cast_to_float() throws Exception {
        Object f = TestUtil.eval("(float) 5");
        assertTrue(f instanceof Float);
        assertEquals(5f, f);
    }

    @Test
    public void test_cast_to_int_1() throws Exception {
        Object i = TestUtil.eval("(int) 0.5");
        assertTrue(i instanceof Integer);
        assertEquals(0, i);
    }

    @Test
    public void test_cast_to_int_2() throws Exception {
        Object i = TestUtil.eval("(int) 0.3");
        assertTrue(i instanceof Integer);
        assertEquals(0, i);
    }

    @Test
    public void test_cast_to_int_3() throws Exception {
        Object i = TestUtil.eval("(int) 0.9");
        assertTrue(i instanceof Integer);
        assertEquals(0, i);
    }

    @Test
    public void test_cast_to_double() throws Exception {
        Object d = TestUtil.eval("(double) 1");
        assertTrue(d instanceof Double);
        assertEquals(1.0d, d);
    }

    @Test
    public void test_cast_null_to_string_1() throws Exception {
        Object str = TestUtil.eval("String s = null; return s;");
        assertNull(str);
    }

    @Test
    public void test_cast_null_to_string_2() throws Exception {
        Object str = TestUtil.eval("(String) null");
        assertNull(str);
    }

    @Test
    public void test_cast_null_to_object() throws Exception {
        Object obj = TestUtil.eval("(Object) null");
        assertNull(obj);
    }

    @Test
    public void test_cast_null_to_int() throws Exception {
        Object i = TestUtil.eval("(int) null");
        assertNotNull(i);
    }

    @Test
    public void test_cast_null_to_primitive_default_values() throws Exception {
        assertEquals((byte) 0, TestUtil.eval("(byte) null"));
        assertEquals((short) 0, TestUtil.eval("(short) null"));
        assertEquals((int) 0, TestUtil.eval("(int) null"));
        assertEquals(0L, TestUtil.eval("(long) null"));
        assertEquals(0.0F, TestUtil.eval("(float) null"));
        assertEquals(0.0D, TestUtil.eval("(double) null"));
        assertEquals(false, TestUtil.eval("(boolean) null"));
        assertEquals('\000', TestUtil.eval("(char) null"));
    }

    @Test
    public void test_cast_char_to_int() throws Exception {
        Object i = TestUtil.eval("(int) 'a'");
        assertEquals(97, i);
    }

    @Test
    public void test_cast_not_defined_variable() {
        EvalError error1 = assertThrows(EvalError.class, () -> TestUtil.eval("(int) xyz"));
        assertTrue(error1.getMessage().contains("Cannot cast void value to int"));

        EvalError error2 = assertThrows(EvalError.class, () -> TestUtil.eval("(Object) xyz"));
        assertTrue(error2.getMessage().contains("Cannot cast void value to Object"));
    }

    @Test
    public void test_cast_primitives_to_object_types_get_boxed() throws Exception {
        assertTrue((boolean) TestUtil.eval("((Object) 5o) instanceof Byte"));
        assertTrue((boolean) TestUtil.eval("((Object) 5s) instanceof Short"));
        assertTrue((boolean) TestUtil.eval("((Object) 5i) instanceof Integer"));
        assertTrue((boolean) TestUtil.eval("((Object) 5l) instanceof Long"));
        assertTrue((boolean) TestUtil.eval("((Object) 5f) instanceof Float"));
        assertTrue((boolean) TestUtil.eval("((Object) 5d) instanceof Double"));
        assertTrue((boolean) TestUtil.eval("((Object) '5') instanceof Character"));
        assertTrue((boolean) TestUtil.eval("((Object) true) instanceof Boolean"));
    }

    @Test
    public void test_cast_boxed_types_to_primitives() throws Throwable {
        try {
            TestUtil.eval(
                "import org.junit.Assert;",
                "Assert.assertEquals('a', (char) Character.valueOf('a'));",
                "Assert.assertEquals(1o, (byte) Byte.valueOf(1o));",
                "Assert.assertEquals(2s, (short) Short.valueOf(2s));",
                "Assert.assertEquals(3i, (int) Integer.valueOf(3i));",
                "Assert.assertEquals(4l, (long) Long.valueOf(4l));",
                "Assert.assertEquals(5.0f, (float) Float.valueOf(5f), 1);",
                "Assert.assertEquals(6.0d, (double) Double.valueOf(6d), 1);",
                "Assert.assertEquals(true, (boolean) Boolean.TRUE);"
            );
        } catch (TargetError e) {
            throw e.getTarget();
        }
    }

    @Test
    public void test_cast_boolean_to_numbers() throws Exception {
        // with 'false'
        assertEquals('\000', TestUtil.eval("(char) false"));
        assertEquals((byte) 0, TestUtil.eval("(byte) false"));
        assertEquals((short) 0, TestUtil.eval("(short) false"));
        assertEquals((int) 0, TestUtil.eval("(int) false"));
        assertEquals(0L, TestUtil.eval("(long) false"));
        assertEquals(0.0F, TestUtil.eval("(float) false"));
        assertEquals(0.0D, TestUtil.eval("(double) false"));

        // with 'true'
        assertEquals('\001', TestUtil.eval("(char) true"));
        assertEquals((byte) 1, TestUtil.eval("(byte) true"));
        assertEquals((short) 1, TestUtil.eval("(short) true"));
        assertEquals((int) 1, TestUtil.eval("(int) true"));
        assertEquals(1L, TestUtil.eval("(long) true"));
        assertEquals(1.0F, TestUtil.eval("(float) true"));
        assertEquals(1.0D, TestUtil.eval("(double) true"));
    }

    @Test
    public void test_cast_string_to_numbers() throws Exception {
        assertEquals('a', TestUtil.eval("(char) \"97\""));
        assertEquals((int) 1, TestUtil.eval("(int) \"1.0\""));
        assertEquals((byte) 3, TestUtil.eval("(byte) \"3\""));
        assertEquals((short) 2, TestUtil.eval("(short) \"2\""));
        assertEquals((int) 1, TestUtil.eval("(int) \"1.0\""));
        assertEquals(0L, TestUtil.eval("(long) \"0\""));
        assertEquals(-1.0F, TestUtil.eval("(float) \"-1\""));
        assertEquals(-2.0D, TestUtil.eval("(double) \"-2.0\""));
    }

    @Test
    public void test_cast_to_boolean() throws Exception {
        // 0, null, or empty string is false
        assertEquals(false, TestUtil.eval("(boolean) false"));
        assertEquals(false, TestUtil.eval("(boolean) '\000'"));
        assertEquals(false, TestUtil.eval("(boolean) 0o"));
        assertEquals(false, TestUtil.eval("(boolean) 0s"));
        assertEquals(false, TestUtil.eval("(boolean) 0i"));
        assertEquals(false, TestUtil.eval("(boolean) 0l"));
        assertEquals(false, TestUtil.eval("(boolean) 0.0f"));
        assertEquals(false, TestUtil.eval("(boolean) 0.0d"));
        assertEquals(false, TestUtil.eval("(boolean) \"\""));
        assertEquals(false, TestUtil.eval("(boolean) null"));

        // not 0, not null, or not empty string is true
        assertEquals(true, TestUtil.eval("(boolean) true"));
        assertEquals(true, TestUtil.eval("(boolean) ' '"));
        assertEquals(true, TestUtil.eval("(boolean) 3o"));
        assertEquals(true, TestUtil.eval("(boolean) 2s"));
        assertEquals(true, TestUtil.eval("(boolean) 1i"));
        assertEquals(true, TestUtil.eval("(boolean) -1l"));
        assertEquals(true, TestUtil.eval("(boolean) -2.0f"));
        assertEquals(true, TestUtil.eval("(boolean) -3.0d"));
        assertEquals(true, TestUtil.eval("(boolean) \"not empty\""));
        assertEquals(true, TestUtil.eval("(boolean) new Object()"));
        assertEquals(true, TestUtil.eval("(boolean) Class.class"));
    }

    @Test
    public void test_typed_variable_assignment() throws Throwable {
        Interpreter bsh = new Interpreter();

        bsh.eval("byte b;");
        assertEquals((byte) 0, bsh.eval("b"));
        assertEquals((byte) 0, bsh.eval("b = null"));
        assertEquals((byte) 0, bsh.eval("b = '\000'"));
        assertEquals((byte) 0, bsh.eval("b = false"));
        assertEquals((byte) 1, bsh.eval("b = true"));
        assertEquals((byte) 123, bsh.eval("b = \"123\""));

        bsh.eval("Byte B;");
        assertNull(bsh.eval("B"));
        assertNull(bsh.eval("B = null"));
        assertEquals(Byte.valueOf("0"), bsh.eval("B = '\000'"));
        assertEquals(Byte.valueOf("0"), bsh.eval("B = false"));
        assertEquals(Byte.valueOf("1"), bsh.eval("B = true"));
        assertEquals(Byte.valueOf("123"), bsh.eval("B = \"123\""));

        bsh.eval("short s;");
        assertEquals((short) 0, bsh.eval("s"));
        assertEquals((short) 0, bsh.eval("s = null"));
        assertEquals((short) 0, bsh.eval("s = '\000'"));
        assertEquals((short) 0, bsh.eval("s = false"));
        assertEquals((short) 1, bsh.eval("s = true"));
        assertEquals((short) 123, bsh.eval("s = \"123\""));

        bsh.eval("Short S;");
        assertNull(bsh.eval("S"));
        assertNull(bsh.eval("S = null"));
        assertEquals(Short.valueOf("0"), bsh.eval("S = '\000'"));
        assertEquals(Short.valueOf("0"), bsh.eval("S = false"));
        assertEquals(Short.valueOf("1"), bsh.eval("S = true"));
        assertEquals(Short.valueOf("123"), bsh.eval("S = \"123\""));

        bsh.eval("int i;");
        assertEquals((int) 0, bsh.eval("i"));
        assertEquals((int) 0, bsh.eval("i = null"));
        assertEquals((int) 0, bsh.eval("i = '\000'"));
        assertEquals((int) 0, bsh.eval("i = false"));
        assertEquals((int) 1, bsh.eval("i = true"));
        assertEquals((int) 123, bsh.eval("i = \"123\""));

        bsh.eval("Integer I;");
        assertNull(bsh.eval("I"));
        assertNull(bsh.eval("I = null"));
        assertEquals(Integer.valueOf("0"), bsh.eval("I = '\000'"));
        assertEquals(Integer.valueOf("0"), bsh.eval("I = false"));
        assertEquals(Integer.valueOf("1"), bsh.eval("I = true"));
        assertEquals(Integer.valueOf("123"), bsh.eval("I = \"123\""));

        bsh.eval("long l;");
        assertEquals((long) 0, bsh.eval("l"));
        assertEquals((long) 0, bsh.eval("l = null"));
        assertEquals((long) 0, bsh.eval("l = '\000'"));
        assertEquals((long) 0, bsh.eval("l = false"));
        assertEquals((long) 1, bsh.eval("l = true"));
        assertEquals((long) 123, bsh.eval("l = \"123\""));

        bsh.eval("Long L;");
        assertNull(bsh.eval("L"));
        assertNull(bsh.eval("L = null"));
        assertEquals(Long.valueOf("0"), bsh.eval("L = '\000'"));
        assertEquals(Long.valueOf("0"), bsh.eval("L = false"));
        assertEquals(Long.valueOf("1"), bsh.eval("L = true"));
        assertEquals(Long.valueOf("123"), bsh.eval("L = \"123\""));

        bsh.eval("float f;");
        assertEquals((float) 0, bsh.eval("f"));
        assertEquals((float) 0, bsh.eval("f = null"));
        assertEquals((float) 0, bsh.eval("f = '\000'"));
        assertEquals((float) 0, bsh.eval("f = false"));
        assertEquals((float) 1, bsh.eval("f = true"));
        assertEquals((float) 123, bsh.eval("f = \"123\""));

        bsh.eval("Float F;");
        assertNull(bsh.eval("F"));
        assertNull(bsh.eval("F = null"));
        assertEquals(Float.valueOf("0"), bsh.eval("F = '\000'"));
        assertEquals(Float.valueOf("0"), bsh.eval("F = false"));
        assertEquals(Float.valueOf("1"), bsh.eval("F = true"));
        assertEquals(Float.valueOf("123"), bsh.eval("F = \"123\""));

        bsh.eval("double d;");
        assertEquals((double) 0, bsh.eval("d"));
        assertEquals((double) 0, bsh.eval("d = null"));
        assertEquals((double) 0, bsh.eval("d = '\000'"));
        assertEquals((double) 0, bsh.eval("d = false"));
        assertEquals((double) 1, bsh.eval("d = true"));
        assertEquals((double) 123, bsh.eval("d = \"123\""));

        bsh.eval("Double D;");
        assertNull(bsh.eval("D"));
        assertNull(bsh.eval("D = null"));
        assertEquals(Double.valueOf("0"), bsh.eval("D = '\000'"));
        assertEquals(Double.valueOf("0"), bsh.eval("D = false"));
        assertEquals(Double.valueOf("1"), bsh.eval("D = true"));
        assertEquals(Double.valueOf("123"), bsh.eval("D = \"123\""));

        bsh.eval("boolean bb;");
        assertEquals(false, bsh.eval("bb"));
        assertEquals(false, bsh.eval("bb = null"));
        assertEquals(false, bsh.eval("bb = 0"));
        assertEquals(false, bsh.eval("bb = \"\""));
        assertEquals(false, bsh.eval("bb = '\000'"));
        assertEquals(false, bsh.eval("bb = false"));
        assertEquals(true, bsh.eval("bb = Boolean.TYPE"));
        assertEquals(true, bsh.eval("bb = 123"));
        assertEquals(true, bsh.eval("bb = \"tick\""));
        assertEquals(true, bsh.eval("bb = 'a'"));
        assertEquals(true, bsh.eval("bb = true"));

        bsh.eval("Boolean BB;");
        assertNull(bsh.eval("BB"));
        assertNull(bsh.eval("BB = null"));
        assertEquals(false, bsh.eval("BB = 0"));
        assertEquals(false, bsh.eval("BB = \"\""));
        assertEquals(false, bsh.eval("BB = '\000'"));
        assertEquals(false, bsh.eval("BB = false"));
        assertEquals(true, bsh.eval("BB = Boolean.class"));
        assertEquals(true, bsh.eval("BB = 123"));
        assertEquals(true, bsh.eval("BB = \"tick\""));
        assertEquals(true, bsh.eval("BB = 'a'"));
        assertEquals(true, bsh.eval("BB = true"));
    }

    @Test
    public void test_cast_non_parseable_strigs_to_number() throws Throwable {
        EvalError error1 = assertThrows(EvalError.class, () -> TestUtil.eval("(int) \"foo\""));
        assertTrue(error1.getMessage().contains("Cannot cast String with value \"foo\" to int"));

        EvalError error2 = assertThrows(EvalError.class, () -> TestUtil.eval("(short) \"\""));
        assertTrue(error2.getMessage().contains("cannot cast string \"\" to number"));

        EvalError error3 = assertThrows(EvalError.class, () -> TestUtil.eval("(Integer) \"foo\""));
        assertTrue(error3.getMessage().contains("Cannot cast String with value \"foo\" to Integer"));
    }

    @Test
    public void test_invalid_cast() throws Throwable {
        TargetError error = assertThrows(TargetError.class, () -> TestUtil.eval("(Integer) \"foo\""));
        assertTrue(error.getTarget() instanceof ClassCastException);
    }

    @Test
    public void test_cast_array_1() throws Exception {
        Object result = TestUtil.eval("(long[]) new byte[] { 14, 10, 45, 2 }");
        assertTrue(result instanceof long[]);

        long[] array = (long[]) result;
        assertEquals(4, array.length);
        assertEquals(14, array[0]);
        assertEquals(10, array[1]);
        assertEquals(45, array[2]);
        assertEquals(2, array[3]);
    }

    @Test
    public void test_cast_array_2() throws Exception {
        Object result = TestUtil.eval("(List<Integer>[]) new List[] { null, new ArrayList(), Arrays.asList(10, 43, 76) }");
        assertTrue(result instanceof List[]);

        List<Integer>[] array = (List<Integer>[]) result;
        assertEquals(3, array.length);
        assertEquals(null, array[0]);
        assertEquals(new ArrayList<>(), array[1]);
        assertEquals(Arrays.asList(10, 43, 76), array[2]);
    }

    @Test
    public void test_cast_matrix() throws Exception {
        Object result = TestUtil.eval("(byte[][]) new int[][] { { 14, 10 }, { 45, 2, 34 } }");
        assertTrue(result instanceof byte[][]);

        byte[][] array = (byte[][]) result;
        assertEquals(2, array.length);
        assertEquals((byte) 14, array[0][0]);
        assertEquals((byte) 10, array[0][1]);
        assertEquals((byte) 45, array[1][0]);
        assertEquals((byte) 2, array[1][1]);
        assertEquals((byte) 34, array[1][2]);
    }
}
