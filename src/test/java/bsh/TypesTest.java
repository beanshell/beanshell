/** Copyright 2022 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package bsh;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import mypackage.MyClass;

public class TypesTest {

    /**
     * Test cast primitive to wrapper type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_to_wrapper_type() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(1, Integer.TYPE), Integer.class, Types.CAST);

        Assert.assertEquals(Integer.valueOf(1), casted);
    }

    /**
     * Test cast primitive type to primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_primitive_to_primitive_type() throws Exception {
        Primitive casted = Primitive.castPrimitive(
            Long.TYPE, Double.TYPE, (Primitive) Primitive.wrap(1.00D, Double.TYPE), false, Types.CAST);

        Assert.assertEquals(Long.valueOf(1), casted.getValue());
    }

    /**
     * Test cast check only primitive type to primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_check_only_primitive_to_primitive_type() throws Exception {
        Assert.assertEquals(Types.VALID_CAST, Primitive.castPrimitive(
            Long.TYPE, Integer.TYPE, (Primitive) Primitive.wrap(1, Integer.TYPE), true, Types.CAST));
    }


    /**
     * Test assign check only primitive type to primitive
     * @throws Exception in case of failure
     */
    @Test
    public void assign_check_only_primitive_to_primitive_type() throws Exception {
        Assert.assertEquals(Types.VALID_CAST, Primitive.castPrimitive(
            Long.TYPE, Integer.TYPE, (Primitive) Primitive.wrap(1, Integer.TYPE), true, Types.ASSIGNMENT));
    }

    /**
     * Test checx only primitive void cast invalid
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_void() throws Exception {
        Assert.assertEquals(Types.INVALID_CAST, Primitive.castPrimitive(
            Integer.class, Integer.TYPE, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test checx only primitive void cast invalid from void type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_void() throws Exception {
        Assert.assertEquals(Types.INVALID_CAST, Primitive.castPrimitive(
            Integer.TYPE, Void.TYPE, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test checx only primitive void cast invalid from boolean type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_boolean() throws Exception {
        Assert.assertEquals(Types.INVALID_CAST, Primitive.castPrimitive(
            Integer.TYPE, Boolean.TYPE, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test checx only primitive void cast valid from boolean type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_check_type_primitive_from_boolean() throws Exception {
        Assert.assertEquals(Types.VALID_CAST, Primitive.castPrimitive(
            Boolean.TYPE, Boolean.TYPE, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test checx only primitive void cast valid from integer type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_check_type_primitive_from_integer() throws Exception {
        Assert.assertEquals(Types.INVALID_CAST, Primitive.castPrimitive(
            Integer.class, Integer.TYPE, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test primitive cast invalid from integer type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_integer() throws Exception {
        Throwable e = Assert.assertThrows(UtilTargetError.class,
            () -> Primitive.castPrimitive(Integer.class, Integer.TYPE,
                Primitive.VOID,false, Types.CAST));
        assertThat(e.getMessage(), containsString("Cannot cast primitive value to object type"));
    }

    /**
     * Test primitive cast invalid from integer to string type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_integer_to_string() throws Exception {
        Throwable e = Assert.assertThrows(UtilEvalError.class,
            () -> Primitive.castPrimitive(String.class, Integer.TYPE,
                Primitive.VOID,false, Types.ASSIGNMENT));
        assertThat(e.getMessage(), containsString("Cannot assign primitive value to object type"));
    }

    /**
     * Test checx only primitive void cast valid from null type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_null() throws Exception {
        Assert.assertEquals(Types.VALID_CAST, Primitive.castPrimitive(
            Integer.TYPE, null, Primitive.VOID, true, Types.CAST));
    }

    /**
     * Test checx only primitive void cast invalid assign from null type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_type_primitive_from_null_assign() throws Exception {
        Assert.assertEquals(Types.INVALID_CAST, Primitive.castPrimitive(
            Integer.TYPE, null, Primitive.VOID, true, Types.ASSIGNMENT));
    }

    /**
     * Cast strict long to byte
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_byte() throws Exception {
        Assert.assertEquals(Byte.MAX_VALUE,
            Primitive.castNumberStrictJava(Byte.class, (long)Byte.MAX_VALUE));
    }

    /**
     * Cast strict long to short
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_short() throws Exception {
        Assert.assertEquals(Short.MAX_VALUE,
            Primitive.castNumberStrictJava(Short.class, (long)Short.MAX_VALUE));
    }

    /**
     * Cast strict long to char
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_char() throws Exception {
        Assert.assertEquals((char) Integer.MAX_VALUE,
            Primitive.castNumberStrictJava(Character.class, (long)Integer.MAX_VALUE));
    }

    /**
     * Cast strict long to int
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_int() throws Exception {
        Assert.assertEquals(Integer.MAX_VALUE,
            Primitive.castNumberStrictJava(Integer.class, (long)Integer.MAX_VALUE));
    }

    /**
     * Cast strict long to long
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_long() throws Exception {
        Assert.assertEquals(Long.MAX_VALUE,
            Primitive.castNumberStrictJava(Long.class, Long.MAX_VALUE));
    }

    /**
     * Cast strict double to float
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_float() throws Exception {
        Assert.assertEquals(Float.MAX_VALUE,
            Primitive.castNumberStrictJava(Float.class, (double)Float.MAX_VALUE));
    }

    /**
     * Cast strict double to double
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_double() throws Exception {
        Assert.assertEquals(Double.MAX_VALUE,
            Primitive.castNumberStrictJava(Double.class, Double.MAX_VALUE));
    }

    /**
     * Cast strict double to big decimal
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_big_decimal() throws Exception {
        Assert.assertEquals(BigDecimal.ZERO.setScale(1),
            Primitive.castNumberStrictJava(BigDecimal.class, (double)0.00));
    }
    /**
     * Cast strict long to byte primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_byte_primitive() throws Exception {
        Assert.assertEquals(Byte.MAX_VALUE,
            Primitive.castNumberStrictJava(Byte.TYPE, (long)Byte.MAX_VALUE));
    }

    /**
     * Cast strict long to short primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_short_primitive() throws Exception {
        Assert.assertEquals(Short.MAX_VALUE,
            Primitive.castNumberStrictJava(Short.TYPE, (long)Short.MAX_VALUE));
    }

    /**
     * Cast strict long to char primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_char_primitive() throws Exception {
        Assert.assertEquals((char) Integer.MAX_VALUE,
            Primitive.castNumberStrictJava(Character.TYPE, (long)Integer.MAX_VALUE));
    }

    /**
     * Cast strict long to int primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_int_primitive() throws Exception {
        Assert.assertEquals(Integer.MAX_VALUE,
            Primitive.castNumberStrictJava(Integer.TYPE, (long)Integer.MAX_VALUE));
    }

    /**
     * Cast strict int to long primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_long_primitive() throws Exception {
        Assert.assertEquals((long)Integer.MAX_VALUE,
            Primitive.castNumberStrictJava(Long.TYPE, (long)Integer.MAX_VALUE));
    }

    /**
     * Cast strict double to float primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_float_primitive() throws Exception {
        Assert.assertEquals(Float.MAX_VALUE,
            Primitive.castNumberStrictJava(Float.TYPE, (double)Float.MAX_VALUE));
    }

    /**
     * Cast strict double to double primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_double_primitive() throws Exception {
        Assert.assertEquals(Double.MAX_VALUE,
            Primitive.castNumberStrictJava(Double.TYPE, Double.MAX_VALUE));
    }

    /**
     * Cast number double to long
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_long_number() throws Exception {
        Assert.assertEquals(0L, Primitive.castNumber(Long.class, (double)0.0));
    }

    /**
     * Cast number double to long primitive
     * @throws Exception in case of failure
     */
    @Test
    public void cast_strict_long_number_primitive() throws Exception {
        Assert.assertEquals(0L, Primitive.castNumber(Long.TYPE, (double)0.00));
    }

    /**
     * Test primitive null type is null
     * @throws Exception in case of failure
     */
    @Test
    public void get_type_primitive_null() throws Exception {
        Assert.assertNull(Primitive.NULL.getType());
    }

    /**
     * Test primitive unwrap null is null
     * @throws Exception in case of failure
     */
    @Test
    public void unwrap_primitive_null() throws Exception {
        Assert.assertNull(Primitive.unwrap((Object[])null));
    }

    /**
     * Test primitive unwrap void is exception
     * @throws Exception in case of failure
     */
    @Test
    public void unwrap_primitive_void() throws Exception {
        Throwable e = Assert.assertThrows(InterpreterError.class,
            () -> Assert.assertNull(Primitive.VOID.getValue()));
        assertThat(e.getMessage(), containsString("attempt to unwrap void type"));
    }

    /**
     * Test primitive is not a number exception
     * @throws Exception in case of failure
     */
    @Test
    public void number_value_primitive_void() throws Exception {
        Throwable e = Assert.assertThrows(InterpreterError.class,
            () -> Assert.assertNull(Primitive.VOID.numberValue()));
        assertThat(e.getMessage(), containsString("Primitive not a number"));
    }

    /**
     * Test primitive unwrap null is null
     * @throws Exception in case of failure
     */
    @Test
    public void unwrap_primitive_null_big_decimal() throws Exception {
        Throwable e = Assert.assertThrows(InterpreterError.class,
            () -> Assert.assertNull(new Primitive((BigDecimal)null).getValue()));
        assertThat(e.getMessage(), containsString("Use Primitve.NULL instead of Primitive(null)"));
    }

    /**
     * Test if primitive to wrapper type is assignable
     * @throws Exception in case of failure
     */
    @Test
    public void is_asssignable_primitive_to_wrapper_type() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Integer.class, Integer.TYPE));
    }

    /**
     * Test cast wide integer to long type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_big_integer_to_long_type() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(BigInteger.valueOf(3L), BigInteger.class), Long.class, Types.CAST);

        Assert.assertEquals(Long.valueOf(3), casted);
    }

    /**
     * Test can cast wrapper type to wide integer
     * @throws Exception in case of failure
     */
    @Test
    public void can_cast_object_long_integer_to_long_type() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Long.class, BigInteger.class));
    }

    /**
     * Test cast primitive to object
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_to_object() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(1, Integer.TYPE), Object.class, Types.CAST);

        Assert.assertEquals((Object)Integer.valueOf(1), casted);
    }

    /**
     * Test can casted primitive to object is assignable
     * @throws Exception in case of failure
     */
    @Test
    public void is_assignable_object_primitive_to_object() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Object.class, Integer.TYPE));
    }

    /** Test {@link Types#prettyName(Class)} with primitive Class<?>, e.g.: <b>byte</b>, <b>int</b>, <b>char</b>, etc... */
    @Test
    public void pretty_name_of_primitive() {
        Assert.assertEquals("byte", Types.prettyName(byte.class));
    }

    /** Test {@link Types#prettyName(Class)} with an array Class<?>, e.g.: <b>java.lang.Object[]</b> */
    @Test
    public void pretty_name_of_array() {
        Assert.assertEquals("java.lang.Object[]", Types.prettyName(new Object[3].getClass()));
    }

    /** Test {@link Types#prettyName(Class)} with an matrix Class<?>, e.g.: <b>java.lang.Object[][][][][]</b> */
    @Test
    public void pretty_name_of_matrix() {
        Assert.assertEquals("int[][][][][][][]", Types.prettyName(new int[3][4][5][6][7][8][9].getClass()));
    }

    /** Test {@link Types#prettyName(Class)} with an null Class<?> */
    @Test
    public void pretty_name_of_null() {
        Assert.assertEquals("null", Types.prettyName((Class<?>) null));
    }

    /** Test {@link Types#prettyName(Type)} with some java.lang.reflect.Type */
    @Test
    public void pretty_name_of_types() {
        class MyClass<A, B extends A, C extends List<? super Integer>, D extends Map<B, ? super Object>, E extends Set<? extends Runnable> & Runnable, F extends List<?>> {}

        String[] prettyNames = Types.prettyNames(MyClass.class.getTypeParameters());

        Assert.assertEquals(6, prettyNames.length);
        Assert.assertEquals("A", prettyNames[0]);
        Assert.assertEquals("B extends A", prettyNames[1]);
        Assert.assertEquals("C extends java.util.List<? super java.lang.Integer>", prettyNames[2]);
        Assert.assertEquals("D extends java.util.Map<B, ? super java.lang.Object>", prettyNames[3]);
        Assert.assertEquals("E extends java.util.Set<? extends java.lang.Runnable> & java.lang.Runnable", prettyNames[4]);
        Assert.assertEquals("F extends java.util.List<?>", prettyNames[5]);

        Assert.assertEquals("java.lang.Boolean", Types.prettyName((Type) Boolean.class));
        Assert.assertEquals("null", Types.prettyName((Type) null));

        Type myType = new Type() {};
        RuntimeException error = Assert.assertThrows(RuntimeException.class, () -> Types.prettyName(myType));
        Assert.assertEquals("Can't return a pretty name because the type is unknown!", error.getMessage());
    }

    /** There shouldn't be a invalid round; this test is just to increase code coverage! */
    @Test
    public void is_assignable_invalid_round() throws Throwable {
        InterpreterError error = Assert.assertThrows(InterpreterError.class, () -> Types.isAssignable(null, null, -1));
        Assert.assertEquals("bad case", error.getMessage());
    }

    /** There is already a test for this in {@link BshLambdaTest}, this test is just to increase code coverage. */
    @Test
    public void lambda_check_cast() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> {}");
        Object result = Types.castObject(Runnable.class, bshLambda.dummyType, bshLambda, 0, true);
        Assert.assertEquals(Types.VALID_CAST, result);
    }

}
