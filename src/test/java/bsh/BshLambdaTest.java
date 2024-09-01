package bsh;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.lang.model.type.PrimitiveType;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@SuppressWarnings("unchecked")
public class BshLambdaTest {

    private void assertArrayEquals(Type[] expected, Type[] actuals) {
        Assert.assertArrayEquals(Types.prettyNames(expected), Types.prettyNames(actuals));
    }

    private void assertEquals(Type expected, Type actual) {
        Assert.assertEquals(Types.prettyName(expected), Types.prettyName(actual));
    }

    @FunctionalInterface
    public static interface CharSupplier { char getAsChar(); }

    public static interface CharSupplier2 extends CharSupplier {};

    @FunctionalInterface
    public static interface ByteSupplier { byte getAsByte(); }

    @FunctionalInterface
    public static interface ShortSupplier { short getAsShort(); }

    @FunctionalInterface
    public static interface FloatSupplier { float getAsFloat(); }

    @FunctionalInterface
    public static interface ListGenerator { List<?> getList(Object value); }

    @FunctionalInterface
    public static interface Executor { void exec() throws NullPointerException; }

    @FunctionalInterface
    public static interface Executor2 { void exec() throws IOException, NullPointerException; }

    @FunctionalInterface
    public static interface IntSupplierGenerator { IntSupplier generate(); }

    @FunctionalInterface
    public static interface LambdaAllArgs {
        boolean exec(
            char arg1,
            boolean arg2,
            byte arg3,
            short arg4,
            int arg5,
            long arg6,
            float arg7,
            double arg8,
            Object arg9
        );
    }

    public static class InvalidBshLambdaWrapper {}

    @FunctionalInterface
    public static interface GenericLambda1<T> { void exec(T value); }

    @FunctionalInterface
    public static interface GenericLambda2<T> { T exec(); }

    @FunctionalInterface
    public static interface GenericLambda3<T, R> { R exec(T value); }

    @FunctionalInterface
    public static interface GenericLambda4<T extends Map<String, Object>> { void exec(T value); }

    @FunctionalInterface
    public static interface GenericLambda5<T extends Thread> { T exec(); }

    @FunctionalInterface
    public static interface GenericLambda6<T extends Map<String, Object>, R extends Thread> { R exec(T value); }

    @FunctionalInterface
    public static interface GenericLambda7<T extends Throwable> { void exec() throws T; }

    @FunctionalInterface
    public static interface GenericLambda8<T extends IOException> { void exec() throws T; }

    @FunctionalInterface
    public static interface GenericLambda9<A extends List<? super Number> & Runnable, B extends Set<? extends String>, C extends NullPointerException> { A exec(B value) throws C; }

    @Test
    public void test_lambda_expression_empty_lambda() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("() -> {}");
        Assert.assertNull(lambda.invoke(new Object[0], new Class[0], void.class));
    }

    @Test
    public void test_lambda_expression_no_body_return_number() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("() -> 123");
        Assert.assertEquals(123, lambda.invoke(new Object[0], new Class[0], int.class).intValue());
    }

    @Test
    public void test_lambda_expression_no_body_return_new_object() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("() -> new ArrayList()");
        Assert.assertEquals(new ArrayList<>(), lambda.invoke(new Object[0], new Class[0], List.class));
    }

    @Test
    public void test_lambda_expression_no_body_return_static_member() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("() -> Collections.EMPTY_LIST");
        Assert.assertEquals(Collections.EMPTY_LIST, lambda.invoke(new Object[0], new Class[0], List.class));
    }

    @Test
    public void test_lambda_expression_no_body_return_invoke_static_member() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("() -> Collections.emptyMap()");
        Assert.assertEquals(Collections.emptyMap(), lambda.invoke(new Object[0], new Class[0], Map.class));
    }

    @Test
    public void test_lambda_expression_single_param_return_param() throws Throwable {
        Object obj = new Object();
        BshLambda lambda = (BshLambda) TestUtil.eval("p -> p");
        Assert.assertTrue(obj == lambda.invoke(new Object[] { obj }, new Class[0], Object.class));
    }

    @Test
    public void test_lambda_expression_wrong_args_length() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("(a, b, c, d) -> {}");
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> lambda.invoke(new Object[] { null, null, null }, new Class[0], void.class));
        Assert.assertTrue(error.getMessage().contains("Wrong number of arguments!"));
    }

    @Test
    public void test_lambda_expression_too_much_args() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("(a, b) -> {}");
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> lambda.invoke(new Object[] { null, null, null }, new Class[0], void.class));
        Assert.assertTrue(error.getMessage().contains("Wrong number of arguments!"));
    }

    @Test
    public void test_converted_lambda_expression_empty_lambda() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> {}");
        Runnable lambda = bshLambda.convertTo(Runnable.class);
        lambda.run();
    }

    @Test
    public void test_converted_lambda_expression_no_body_return_number() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> 123");
        Supplier<Integer> lambda = bshLambda.convertTo(Supplier.class);
        Assert.assertEquals(Integer.valueOf(123), lambda.get());
    }

    @Test
    public void test_converted_lambda_expression_no_body_return_new_object() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> new ArrayList()");
        Supplier<List<?>> lambda = bshLambda.convertTo(Supplier.class);
        Assert.assertEquals(new ArrayList<>(), lambda.get());
    }

    @Test
    public void test_converted_lambda_expression_no_body_return_static_member() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> Collections.EMPTY_LIST");
        Supplier<List<?>> lambda = bshLambda.convertTo(Supplier.class);
        Assert.assertEquals(Collections.EMPTY_LIST, lambda.get());
    }

    @Test
    public void test_converted_lambda_expression_no_body_return_invoke_static_member() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> Collections.emptyMap()");
        Supplier<Map<?, ?>> lambda = bshLambda.convertTo(Supplier.class);
        Assert.assertEquals(Collections.emptyMap(), lambda.get());
    }

    @Test
    public void test_converted_lambda_expression_single_param_return_param() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("num -> num * 2");
        Function<Integer, Integer> lambda = bshLambda.convertTo(Function.class);
        Assert.assertEquals(Integer.valueOf(10), lambda.apply(5));
    }

    @Test
    public void test_converted_invalid_functional_interface() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> {}");
        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class, () -> bshLambda.convertTo(Object.class));
        Assert.assertTrue(error.getMessage().contains("This class isn't a valid Functional Interface:"));
    }

    @Test
    public void test_casted_lambda_expression() throws Throwable {
        Object result = TestUtil.eval("(Function<Integer, Integer>) num -> num * 2");
        Assert.assertTrue(result instanceof Function);
        Function<Integer, Integer> lambda = (Function<Integer, Integer>) result;
        Assert.assertEquals(Integer.valueOf(8), lambda.apply(4));
    }

    @Test
    public void test_assigned_lambda_expression() throws Throwable {
        Object result = TestUtil.eval(
            "Function<Integer, Integer> lambda = num -> num * 2;",
            "return lambda;"
        );
        Assert.assertTrue(result instanceof Function);
        Function<Integer, Integer> lambda = (Function<Integer, Integer>) result;
        Assert.assertEquals(Integer.valueOf(16), lambda.apply(8));
    }

    @Test
    public void test_method_reference() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("new ArrayList<>()::equals");
        Assert.assertTrue(lambda.invoke(new Object[] { new ArrayList<>() }, new Class[0], boolean.class));
    }

    @Test
    public void test_method_reference_of_static_method() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("Collections::emptyList");
        Assert.assertEquals(Collections.emptyList(), lambda.invoke(new Object[0], new Class[0], List.class));
    }

    @Test
    public void test_method_reference_of_constructor() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("Integer::new");
        Assert.assertEquals(43, lambda.invoke(new Object[] { "43" }, new Class[0], Integer.class).intValue());
    }

    @Test
    public void test_method_reference_wrong_args_length() throws Throwable {
        BshLambda lambda = (BshLambda) TestUtil.eval("new ArrayList<>()::equals");
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> lambda.invoke(new Object[0], new Class[0], boolean.class));
        Assert.assertTrue(error.getMessage().contains("Can't invoke lambda"));
    }

    @Test
    public void test_converted_method_reference() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("new ArrayList<>()::equals");
        Predicate<Object> lambda = bshLambda.convertTo(Predicate.class);
        Assert.assertTrue(lambda.test(new ArrayList<>()));
    }

    @Test
    public void test_converted_method_reference_of_static_method() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Collections::emptyList");
        Supplier<List<?>> lambda = bshLambda.convertTo(Supplier.class);
        Assert.assertEquals(Collections.emptyList(), lambda.get());
    }

    @Test
    public void test_converted_method_reference_of_constructor() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Integer::new");
        Function<String, Integer> lambda = bshLambda.convertTo(Function.class);
        Assert.assertEquals((Integer) 32, lambda.apply("32"));
    }

    @Test
    public void test_converted_static_method_reference_to_non_static_method_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Object::toString");
        Function<Object, String> lambda = bshLambda.convertTo(Function.class);
        Assert.assertEquals("98", lambda.apply(98));
    }

    @Test
    public void test_converted_static_method_reference_to_non_static_method_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("List::toString");
        Function<Object, String> lambda = bshLambda.convertTo(Function.class);
        Assert.assertEquals("[10, 3, 456]", lambda.apply(Arrays.asList(10, 3, 456)));
    }

    @Test
    public void test_static_method_reference_to_non_static_method() throws Throwable {
        Object result = TestUtil.eval(
            "List<Object> list = Arrays.asList(new HashMap<>(), new ArrayList<>(), 1, \"Hello World\");",
            "return list.stream().map(Object::toString).collect(Collectors.toList());"
        );
        Assert.assertEquals(Arrays.asList("{}", "[]", "1", "Hello World"), result);
    }

    @Test
    public void test_static_method_reference_to_non_static_method_1() throws Throwable {
        Object result = TestUtil.eval(
            "List<List<String>> list = Arrays.asList(new ArrayList<>(), Arrays.asList(1, 2, 3, 4), Arrays.asList(\"Hello World\"));",
            "return list.stream().map(List::toString).collect(Collectors.toList());"
        );
        Assert.assertEquals(Arrays.asList("[]", "[1, 2, 3, 4],", "[Hello World]"), result);
    }

    @Test
    public void test_casted_method_reference() throws Throwable {
        Predicate<Object> lambda = (Predicate<Object>) TestUtil.eval("(java.util.function.Predicate) new ArrayList<>()::equals");
        Assert.assertTrue(lambda.test(new ArrayList<>()));
    }

    @Test
    public void test_casted_method_reference_of_static_method() throws Throwable {
        Supplier<List<?>> lambda = (Supplier<List<?>>) TestUtil.eval("(java.util.function.Supplier) Collections::emptyList");
        Assert.assertEquals(Collections.emptyList(), lambda.get());
    }

    @Test
    public void test_casted_method_reference_of_static_method_2() throws Throwable {
        try {
            Interpreter bsh = new Interpreter();
            bsh.eval(
                "import java.util.Comparator;\n" +
                "public class MyClass {\n" +
                "   public static int compare(MyClass a1, byte a2) { return 0; }\n" +
                "   public static int compare(MyClass a1, Map<String, Object> a2) { return 1; }\n" +
                "   public static int compare(MyClass a1, MyClass a2) { return 2; }\n" +
                "}\n" +
                "Comparator<MyClass> comp = MyClass::compare;\n" +
                "MyClass myObj = new MyClass();"
            );
            Comparator<Object> comp = (Comparator<Object>) bsh.getNameSpace().getVariable("comp");
            Object myObj = bsh.getNameSpace().getVariable("myObj");
            Assert.assertEquals(2, comp.compare(myObj, myObj));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void test_casted_method_reference_of_constructor() throws Throwable {
        Function<String, Integer> lambda = (Function<String, Integer>) TestUtil.eval("(java.util.function.Function) Integer::new");
        Assert.assertEquals((Integer) 32, lambda.apply("32"));
    }

    @Test
    public void test_casted_static_method_reference_to_non_static_method() throws Throwable {
        Function<Object, String> lambda = (Function<Object, String>) TestUtil.eval("(java.util.function.Function<Object, String>) Object::toString;");
        Assert.assertEquals("{}", lambda.apply(new HashMap<>()));
        Assert.assertEquals("[]", lambda.apply(new ArrayList<>()));
        Assert.assertEquals("1", lambda.apply(1));
        Assert.assertEquals("Hello World", lambda.apply("Hello World"));
    }

    @Test
    public void test_casted_static_method_reference_to_non_static_method_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        bsh.eval(
            "import java.util.Comparator;\n" +
            "public class MyClass {\n" +
            "   public int compare(byte obj) { return 0; }\n" +
            "   public int compare(Map<String, Object> obj) { return 1; }\n" +
            "   public int compare(MyClass obj) { return 2; }\n" +
            "}\n" +
            "Comparator<MyClass> comp = MyClass::compare;\n" +
            "MyClass myObj = new MyClass();"
        );
        Comparator<Object> comp = (Comparator<Object>) bsh.getNameSpace().getVariable("comp");
        Object myObj = bsh.getNameSpace().getVariable("myObj");
        Assert.assertEquals(2, comp.compare(myObj, myObj));
    }

    @Test
    public void test_casted_static_method_reference_to_non_static_method_3() throws Throwable {
        Interpreter bsh = new Interpreter();
        bsh.eval(
            "import java.util.Comparator;\n" +
            "public class MyClass {\n" +
            "   public int compare(byte obj) { return 0; }\n" +
            "   private int compare(Map<String, Object> obj) { return 1; }\n" +
            "   public int compare(MyClass obj) { return 2; }\n" +
            "}\n" +
            "Comparator<MyClass> comp = MyClass::compare;\n" +
            "MyClass myObj = new MyClass();"
        );
        Comparator<Object> comp = (Comparator<Object>) bsh.getNameSpace().getVariable("comp");
        Object myObj = bsh.getNameSpace().getVariable("myObj");
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> comp.compare(myObj, new HashMap<>()));
        Assert.assertTrue(error.getMessage().contains("Can't invoke lambda made from method reference!"));
    }

    @Test
    public void test_assigned_method_reference() throws Throwable {
        Predicate<Object> lambda = (Predicate<Object>) TestUtil.eval(
            "import java.util.function.Predicate;",
            "Predicate<Object> lambda = new ArrayList<>()::equals;",
            "return lambda;"
        );
        Assert.assertTrue(lambda.test(new ArrayList<>()));
    }

    @Test
    public void test_assigned_method_reference_of_static_method() throws Throwable {
        Supplier<List<?>> lambda = (Supplier<List<?>>) TestUtil.eval(
            "import java.util.function.Supplier;",
            "Supplier<List<?>> lambda = Collections::emptyList;",
            "return lambda;"
        );
        Assert.assertEquals(Collections.emptyList(), lambda.get());
    }

    @Test
    public void test_assigned_method_reference_of_constructor() throws Throwable {
        Function<String, Integer> lambda = (Function<String, Integer>) TestUtil.eval(
            "import java.util.function.Function;",
            "Function<String, Integer> lambda = Integer::new;",
            "return lambda;"
        );
        Assert.assertEquals((Integer) 32, lambda.apply("32"));
    }

    @Test
    public void test_assigned_static_method_reference_to_non_static_method() throws Throwable {
        Function<Object, String> lambda = (Function<Object, String>) TestUtil.eval(
            "import java.util.function.Function;",
            "Function<Object, String> lambda = Object::toString;",
            "return lambda;"
        );
        Assert.assertEquals("{}", lambda.apply(new HashMap<>()));
        Assert.assertEquals("[]", lambda.apply(new ArrayList<>()));
        Assert.assertEquals("1", lambda.apply(1));
        Assert.assertEquals("Hello World", lambda.apply("Hello World"));
    }

    @Test
    public void test_invoke_method_with_lambda_expression_arg() throws Throwable {
        List<Number> expected = Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        List<Number> result = (List<Number>) TestUtil.eval(
            "List<Number> nums1 = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);",
            "List<Number> nums2 = new ArrayList();",
            "nums1.forEach((num) -> nums2.add(num * 2));",
            "return nums2;"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_local_method_with_lambda_expression_arg_1() throws Throwable {
        List<Number> expected = Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        List<Number> result = (List<Number>) TestUtil.eval(
            "import java.util.function.Consumer;",
            "void forNum(Consumer<Number> consumer) {",
            "   List<Number> nums1 = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);",
            "   nums1.forEach(consumer);",
            "}",
            "List<Number> nums2 = new ArrayList();",
            "forNum((num) -> nums2.add(num * 2));",
            "return nums2;"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_local_method_with_lambda_expression_arg_2() throws Throwable {
        List<Number> expected = Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        List<Number> result = (List<Number>) TestUtil.eval(
            "import java.util.function.Consumer;",
            "void forNum(Consumer<Number> consumer) {",
            "   consumer.accept(1);",
            "   consumer.accept(2);",
            "   consumer.accept(3);",
            "   consumer.accept(4);",
            "   consumer.accept(5);",
            "   consumer.accept(6);",
            "   consumer.accept(7);",
            "   consumer.accept(8);",
            "   consumer.accept(9);",
            "   consumer.accept(10);",
            "}",
            "List<Number> nums2 = new ArrayList();",
            "forNum((num) -> nums2.add(num * 2));",
            "return nums2;"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_local_method_with_loose_type_lambda_expression_arg() throws Throwable {
        List<Number> expected = Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        List<Number> result = (List<Number>) TestUtil.eval(
            "import bsh.BshLambda;",
            "import org.junit.Assert;",
            "void forNum(consumer) {",
            "   Assert.assertTrue(consumer instanceof BshLambda);",
            "   consumer.invoke(new Object[] { 1 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 2 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 3 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 4 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 5 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 6 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 7 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 8 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 9 }, new Class[0], Object.class);",
            "   consumer.invoke(new Object[] { 10 }, new Class[0], Object.class);",
            "}",
            "List<Number> nums2 = new ArrayList();",
            "forNum((num) -> nums2.add(num * 2));",
            "return nums2;"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_static_method_with_lambda_expression_arg() throws Throwable {
        List<Number> expected = Arrays.asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        List<Number> result = (List<Number>) TestUtil.eval(
            "import java.util.function.Consumer;",
            "class MyClass {",
            "   public static void forNum(Consumer<Number> consumer) {",
            "       List<Number> nums1 = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);",
            "       nums1.forEach(consumer);",
            "   }",
            "}",
            "List<Number> nums2 = new ArrayList();",
            "MyClass.forNum((num) -> nums2.add(num * 2));",
            "return nums2;"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_method_with_method_reference_arg() throws Throwable {
        List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        List<Integer> result = (List<Integer>) TestUtil.eval(
            "import java.util.stream.Collectors;",
            "class Parser {",
            "   public Integer parseInt(String num) { return new Integer(num); }",
            "}",
            "Parser parser = new Parser();",
            "List<String> nums = Arrays.asList(\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\");",
            "return nums.stream().map(parser::parseInt).collect(Collectors.toList());"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_method_with_method_reference_of_static_method_arg() throws Throwable {
        List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        List<Integer> result = (List<Integer>) TestUtil.eval(
            "import java.util.stream.Collectors;",
            "List<String> nums = Arrays.asList(\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\");",
            "return nums.stream().map(Integer::parseInt).collect(Collectors.toList());"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_method_with_method_reference_of_constructor_arg() throws Throwable {
        List<Integer> expected = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        List<Integer> result = (List<Integer>) TestUtil.eval(
            "import java.util.stream.Collectors;",
            "List<String> nums = Arrays.asList(\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\");",
            "return nums.stream().map(Integer::new).collect(Collectors.toList());"
        );
        Assert.assertEquals(expected, result);
    }

    @Test
    public void test_invoke_primitive() throws Throwable {
        BshLambda bshBooleanLambda = (BshLambda) TestUtil.eval("() -> false");
        BshLambda bshCharLambda = (BshLambda) TestUtil.eval("() -> 'k'");
        BshLambda bshNumberLambda = (BshLambda) TestUtil.eval("() -> 2");

        Assert.assertEquals(false, bshBooleanLambda.convertTo(BooleanSupplier.class).getAsBoolean());
        Assert.assertEquals('k', bshCharLambda.convertTo(CharSupplier.class).getAsChar());
        Assert.assertEquals(2, bshNumberLambda.convertTo(ByteSupplier.class).getAsByte());
        Assert.assertEquals(2, bshNumberLambda.convertTo(ShortSupplier.class).getAsShort());
        Assert.assertEquals(2, bshNumberLambda.convertTo(IntSupplier.class).getAsInt());
        Assert.assertEquals(2, bshNumberLambda.convertTo(LongSupplier.class).getAsLong());
        Assert.assertEquals(2, bshNumberLambda.convertTo(FloatSupplier.class).getAsFloat(), 0);
        Assert.assertEquals(2, bshNumberLambda.convertTo(DoubleSupplier.class).getAsDouble(), 0);
    }

    @Test
    public void test_cant_invoke_primitive() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> null");

        RuntimeEvalError errorBoolean = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(BooleanSupplier.class).getAsBoolean());
        Assert.assertTrue(errorBoolean.getMessage().contains("Can't assign null to boolean"));

        RuntimeEvalError errorChar = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(CharSupplier.class).getAsChar());
        Assert.assertTrue(errorChar.getMessage().contains("Can't assign null to char"));

        RuntimeEvalError errorByte = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(ByteSupplier.class).getAsByte());
        Assert.assertTrue(errorByte.getMessage().contains("Can't assign null to byte"));

        RuntimeEvalError errorShort = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(ShortSupplier.class).getAsShort());
        Assert.assertTrue(errorShort.getMessage().contains("Can't assign null to short"));

        RuntimeEvalError errorInt = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(IntSupplier.class).getAsInt());
        Assert.assertTrue(errorInt.getMessage().contains("Can't assign null to int"));

        RuntimeEvalError errorLong = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(LongSupplier.class).getAsLong());
        Assert.assertTrue(errorLong.getMessage().contains("Can't assign null to long"));

        RuntimeEvalError errorFloat = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(FloatSupplier.class).getAsFloat());
        Assert.assertTrue(errorFloat.getMessage().contains("Can't assign null to float"));

        RuntimeEvalError errorDouble = Assert.assertThrows(RuntimeEvalError.class, () -> bshLambda.convertTo(DoubleSupplier.class).getAsDouble());
        Assert.assertTrue(errorDouble.getMessage().contains("Can't assign null to double"));
    }

    @Test
    public void test_method_reference_not_assignable_invalid_params() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class Parser {",
            "   public Integer parseInt(String num) { return new Integer(num); }",
            "}",
            "return new Parser()::parseInt"
        );
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, BiFunction.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_not_assignable_invalid_return_type() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class Parser {",
            "   public int parseInt(String num, String arg2) { return new Integer(num); }",
            "}",
            "return new Parser()::parseInt"
        );
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, BiFunction.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_not_assignable_invalid_static() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class Parser {",
            "   public int parseInt(String num, String arg2) { return new Integer(num); }",
            "}",
            "return Parser::parseInt"
        );
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, BiFunction.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_of_static_method_not_assignable() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Integer::parseInt");
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, BiFunction.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_of_constructor_not_assignable() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Integer::new");
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, BiFunction.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_of_constructor_not_assignable_invalid_return_type() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Integer::new");
        boolean assignable = BshLambda.isAssignable(bshLambda.dummyType, ListGenerator.class, Types.JAVA_BASE_ASSIGNABLE);
        Assert.assertFalse(assignable);
    }

    @Test
    public void test_method_reference_throw_target_error() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyInt {",
            "   public MyInt() {",
            "       throw new Exception(\"Error inside my constructor\");",
            "   }",
            "}",
            "return MyInt::new"
        );
        TargetError error = Assert.assertThrows(TargetError.class, () -> bshLambda.invokeImpl(new Object[0]));
        Assert.assertTrue(error.getMessage().contains("Error inside my constructor"));
    }

    @Test
    public void test_lambda_expression_not_assignable_invalid_params_length() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> {}");
        Assert.assertFalse(BshLambda.isAssignable(bshLambda.dummyType, Consumer.class, Types.JAVA_BASE_ASSIGNABLE));
    }

    @Test
    public void test_lambda_expression_typed_params() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "(",
            "   char arg1,",
            "   boolean arg2,",
            "   byte arg3,",
            "   short arg4,",
            "   int arg5,",
            "   long arg6,",
            "   float arg7,",
            "   double arg8,",
            "   Object arg9",
            ") -> {",
            "    return  arg1 == 'a' &&",
            "            arg2 == false &&",
            "            arg3 == 2 &&",
            "            arg4 == 37 &&",
            "            arg5 == 14 &&",
            "            arg6 == 108 &&",
            "            arg7 == 4.08f &&",
            "            arg8 == 921.123d &&",
            "            arg9 == Collections.EMPTY_SET",
            "   ;",
            "};"
        );
        LambdaAllArgs lambda = bshLambda.convertTo(LambdaAllArgs.class);
        Assert.assertTrue(lambda.exec('a', false, (byte) 2, (short) 37, 14, 108, 4.08f, 921.123d, Collections.EMPTY_SET));
        Assert.assertFalse(lambda.exec('b', false, (byte) 2, (short) 37, 14, 108, 4.08f, 921.123d, Collections.EMPTY_SET));
    }

    @Test
    public void test_lambda_expression_invalid_constructor() throws Throwable {
        Node node = null;
        NameSpace nameSpace = null;
        Node bodyNode = null;

        String expectedMsg = "The length of 'paramsModifiers', 'paramsTypes' and 'paramsNames' can't be different!";

        IllegalArgumentException error1 = Assert.assertThrows(IllegalArgumentException.class, () -> BshLambda.fromLambdaExpression(node, nameSpace, new Modifiers[0], new Class[] { Object.class }, new String[0], bodyNode));
        Assert.assertEquals(expectedMsg, error1.getMessage());

        IllegalArgumentException error2 = Assert.assertThrows(IllegalArgumentException.class, () -> BshLambda.fromLambdaExpression(node, nameSpace, new Modifiers[0], new Class[0], new String[] { "arg1" }, bodyNode));
        Assert.assertEquals(expectedMsg, error2.getMessage());
    }

    @Test
    public void test_lambda_expression_throw_exception() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> { throw new NullPointerException(\"Null error inside lambda\"); }");
        NullPointerException error = Assert.assertThrows(NullPointerException.class, () -> bshLambda.convertTo(Executor.class).exec());
        Assert.assertEquals("Null error inside lambda", error.getMessage());
    }

    @Test
    public void test_lambda_expression_throw_exception_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> { throw new NullPointerException(\"Null error inside lambda\"); }");
        NullPointerException error = Assert.assertThrows(NullPointerException.class, () -> bshLambda.convertTo(Executor2.class).exec());
        Assert.assertEquals("Null error inside lambda", error.getMessage());
    }

    @Test
    public void test_lambda_expression_unexpected_exception() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> { throw new NullPointerException(\"My Unexpected Error :P\"); }");
        Runnable lambda = bshLambda.convertTo(Runnable.class);
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> lambda.run());
        Assert.assertTrue(error.getMessage().contains("Can't invoke lambda: Unexpected Exception: My Unexpected Error :P"));
    }

    @Test
    public void test_lambda_expression_break() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> { break; }");
        Object result = bshLambda.invoke(new Object[0], new Class[0], void.class);
        Assert.assertNull(result);
    }

    @Test
    public void test_cant_convert() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("Integer::parseInt");
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(ByteSupplier.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to"));
    }

    @Test
    public void test_lambda_expression_lambda_generator() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> () -> 3");
        IntSupplierGenerator lambda1 = bshLambda.convertTo(IntSupplierGenerator.class);
        IntSupplier lambda2 = lambda1.generate();
        Assert.assertEquals(3, lambda2.getAsInt());
    }

    @Test
    public void test_lambda_expression_return_null() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> null");
        IntSupplierGenerator lambda1 = bshLambda.convertTo(IntSupplierGenerator.class);
        Assert.assertNull(lambda1.generate());
    }

    @Test
    public void test_lambda_expression_cant_assign_return() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> 3");
        IntSupplierGenerator lambda1 = bshLambda.convertTo(IntSupplierGenerator.class);
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> lambda1.generate());
        Assert.assertTrue(error.getMessage().contains("Can't assign int to java.util.function.IntSupplier"));
    }

    @Test // The error of this test must never occurr!! This tests is just for 100% code coverage
    public void test_cant_create_functional_interface_wrapper() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval("() -> {}");
        try (MockedStatic<BshLambda> mockedStatic = Mockito.mockStatic(BshLambda.class)) {
            mockedStatic.when(() -> BshLambda.getClassForFI(Runnable.class)).thenReturn(InvalidBshLambdaWrapper.class);
            mockedStatic.when(() -> BshLambda.isAssignable(bshLambda.dummyType, Runnable.class, Types.BSH_ASSIGNABLE)).thenReturn(true);

            UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(Runnable.class));
            Assert.assertTrue(error.getMessage().contains("Can't create a instance for the generate class for the BshLambda: "));
        }
    }

    @Test
    public void test_lambda_expression_closure() throws Throwable {
        IntSupplier supplier = (IntSupplier) TestUtil.eval(
            "int num = 10;",
            "IntSupplier supplier = () -> num;",
            "num = 20;",
            "return supplier;"
        );
        Assert.assertEquals(10, supplier.getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_closure_1() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++) {",
            "   int num = i * 3;",
            "   suppliers[i] = () -> num;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_closure_2() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final or effectivelly final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++) {",
            "   suppliers[i] = () -> i * 3;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_closure_3() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final or effectivelly final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++) suppliers[i] = () -> i * 3;",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_closure_4() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++) {",
            "   suppliers[i] = () -> i * 3;",
            "   i++;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertNull(suppliers[1]);
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertNull(suppliers[3]);
    }

    @Test
    public void test_for_statement_lambda_expression_closure_5() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++) {",
            "   i++;",
            "   suppliers[i] = () -> i * 3;",
            "}",
            "return suppliers;"
        );
        Assert.assertNull(suppliers[0]);
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertNull(suppliers[2]);
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_closure_6() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i = 0; i < suppliers.length; i++)",
            "   suppliers[i] = () -> i++ * 3;",
            "return suppliers;"
        );
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[0].getAsInt());
        Assert.assertTrue(error.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error2 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[1].getAsInt());
        Assert.assertTrue(error2.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error3 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[2].getAsInt());
        Assert.assertTrue(error3.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error4 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[3].getAsInt());
        Assert.assertTrue(error4.getMessage().contains("Cannot re-assign final variable"));
    }

    @Test
    public void test_for_statement_lambda_expression_closure_7() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        List<IntSupplier> suppliers = (List<IntSupplier>) TestUtil.eval(
            "List<IntSupplier> suppliers = new ArrayList<>();",
            "for (int i = 5; i < 10; i++) {",
            "   for (int i = 0; i < 4; i++) {",
            "       suppliers.add((IntSupplier) () -> i * 3);",
            "   }",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(20, suppliers.size());
        Assert.assertEquals(0, suppliers.get(0).getAsInt());
        Assert.assertEquals(3, suppliers.get(1).getAsInt());
        Assert.assertEquals(6, suppliers.get(2).getAsInt());
        Assert.assertEquals(9, suppliers.get(3).getAsInt());
        Assert.assertEquals(0, suppliers.get(4).getAsInt());
        Assert.assertEquals(3, suppliers.get(5).getAsInt());
        Assert.assertEquals(6, suppliers.get(6).getAsInt());
        Assert.assertEquals(9, suppliers.get(7).getAsInt());
        Assert.assertEquals(0, suppliers.get(8).getAsInt());
        Assert.assertEquals(3, suppliers.get(9).getAsInt());
        Assert.assertEquals(6, suppliers.get(10).getAsInt());
        Assert.assertEquals(9, suppliers.get(11).getAsInt());
        Assert.assertEquals(0, suppliers.get(12).getAsInt());
        Assert.assertEquals(3, suppliers.get(13).getAsInt());
        Assert.assertEquals(6, suppliers.get(14).getAsInt());
        Assert.assertEquals(9, suppliers.get(15).getAsInt());
        Assert.assertEquals(0, suppliers.get(16).getAsInt());
        Assert.assertEquals(3, suppliers.get(17).getAsInt());
        Assert.assertEquals(6, suppliers.get(18).getAsInt());
        Assert.assertEquals(9, suppliers.get(19).getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_1() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { 0, 1, 2, 3 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes) {",
            "   int num = i * 3;",
            "   suppliers[i] = () -> num;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_2() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final or effectivelly final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { 0, 1, 2, 3 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes) {",
            "   suppliers[i] = () -> i * 3;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_3() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final or effectivelly final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { 0, 1, 2, 3 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes) suppliers[i] = () -> i * 3;",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_4() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { 0, 1, 2, 3 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes) {",
            "   suppliers[i] = () -> i * 3;",
            "   i++;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_5() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { -1, 0, 1, 2 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes) {",
            "   i++;",
            "   suppliers[i] = () -> i * 3;",
            "}",
            "return suppliers;"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_enhanced_lambda_expression_closure_6() throws Throwable {
        // In Java we can't use the '() -> i' expression
        // but all scoped variables must be final/effectivelly-final for lambdas!
        // So this test must ensure it!
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "int[] indexes = { 0, 1, 2, 3 };",
            "IntSupplier[] suppliers = new IntSupplier[4];",
            "for (int i : indexes)",
            "   suppliers[i] = () -> i++ * 3;",
            "return suppliers;"
        );
        RuntimeEvalError error = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[0].getAsInt());
        Assert.assertTrue(error.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error2 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[1].getAsInt());
        Assert.assertTrue(error2.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error3 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[2].getAsInt());
        Assert.assertTrue(error3.getMessage().contains("Cannot re-assign final variable"));

        RuntimeEvalError error4 = Assert.assertThrows(RuntimeEvalError.class, () -> suppliers[3].getAsInt());
        Assert.assertTrue(error4.getMessage().contains("Cannot re-assign final variable"));
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_1() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    static int i = 0;",
            "    static IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) {",
            "           int num = i * 3;",
            "           suppliers[i] = () -> num;",
            "        }",
            "        return suppliers;",
            "    }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_2() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    static int i = 0;",
            "    static IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "        }",
            "        return suppliers;",
            "    }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_3() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    static int i = 0;",
            "    static IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) suppliers[i] = () -> i * 3;",
            "        return suppliers;",
            "    }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_4() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   static int i = 0;",
            "   static IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "           i++;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertNull(suppliers[1]);
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertNull(suppliers[3]);
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_5() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   static int i = 0;",
            "   static IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           i++;",
            "           suppliers[i] = () -> i * 3;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertNull(suppliers[0]);
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertNull(suppliers[2]);
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_static_class_members_closure_6() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   static int i = 0;",
            "   static IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++)",
            "          suppliers[i] = () -> i++ * 3;",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(15, suppliers[1].getAsInt());
        Assert.assertEquals(18, suppliers[2].getAsInt());
        Assert.assertEquals(21, suppliers[3].getAsInt());

        Assert.assertEquals(24, suppliers[0].getAsInt());
        Assert.assertEquals(27, suppliers[1].getAsInt());
        Assert.assertEquals(30, suppliers[2].getAsInt());
        Assert.assertEquals(33, suppliers[3].getAsInt());

        Assert.assertEquals(36, suppliers[0].getAsInt());
        Assert.assertEquals(39, suppliers[1].getAsInt());
        Assert.assertEquals(42, suppliers[2].getAsInt());
        Assert.assertEquals(45, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_1() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    int i = 0;",
            "    IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) {",
            "           int num = i * 3;",
            "           suppliers[i] = () -> num;",
            "        }",
            "        return suppliers;",
            "    }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_2() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    int i = 0;",
            "    IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "        }",
            "        return suppliers;",
            "    }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_3() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "    int i = 0;",
            "    IntSupplier[] getSuppliers() {",
            "        IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) suppliers[i] = () -> i * 3;",
            "        return suppliers;",
            "    }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_4() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "           i++;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertNull(suppliers[1]);
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertNull(suppliers[3]);
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_5() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           i++;",
            "           suppliers[i] = () -> i * 3;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertNull(suppliers[0]);
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertNull(suppliers[2]);
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_class_members_closure_6() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "class Foo {",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++)",
            "          suppliers[i] = () -> i++ * 3;",
            "       return suppliers;",
            "   }",
            "}",
            "return new Foo().getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(15, suppliers[1].getAsInt());
        Assert.assertEquals(18, suppliers[2].getAsInt());
        Assert.assertEquals(21, suppliers[3].getAsInt());

        Assert.assertEquals(24, suppliers[0].getAsInt());
        Assert.assertEquals(27, suppliers[1].getAsInt());
        Assert.assertEquals(30, suppliers[2].getAsInt());
        Assert.assertEquals(33, suppliers[3].getAsInt());

        Assert.assertEquals(36, suppliers[0].getAsInt());
        Assert.assertEquals(39, suppliers[1].getAsInt());
        Assert.assertEquals(42, suppliers[2].getAsInt());
        Assert.assertEquals(45, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_1() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           int num = i * 3;",
            "           suppliers[i] = () -> num;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertEquals(0, suppliers[0].getAsInt());
        Assert.assertEquals(3, suppliers[1].getAsInt());
        Assert.assertEquals(6, suppliers[2].getAsInt());
        Assert.assertEquals(9, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_2() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "        }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_3() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "        for (; i < suppliers.length; i++) suppliers[i] = () -> i * 3;",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_4() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           suppliers[i] = () -> i * 3;",
            "           i++;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertNull(suppliers[1]);
        Assert.assertEquals(12, suppliers[2].getAsInt());
        Assert.assertNull(suppliers[3]);
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_5() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++) {",
            "           i++;",
            "           suppliers[i] = () -> i * 3;",
            "       }",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertNull(suppliers[0]);
        Assert.assertEquals(12, suppliers[1].getAsInt());
        Assert.assertNull(suppliers[2]);
        Assert.assertEquals(12, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_lambda_expression_enum_members_closure_6() throws Throwable {
        IntSupplier[] suppliers = (IntSupplier[]) TestUtil.eval(
            "enum Foo {",
            "   FIRST(), SECOND();",
            "   Foo() {}",
            "   int i = 0;",
            "   IntSupplier[] getSuppliers() {",
            "       IntSupplier[] suppliers = new IntSupplier[4];",
            "       for (; i < suppliers.length; i++)",
            "          suppliers[i] = () -> i++ * 3;",
            "       return suppliers;",
            "   }",
            "}",
            "return Foo.FIRST.getSuppliers();"
        );
        Assert.assertEquals(12, suppliers[0].getAsInt());
        Assert.assertEquals(15, suppliers[1].getAsInt());
        Assert.assertEquals(18, suppliers[2].getAsInt());
        Assert.assertEquals(21, suppliers[3].getAsInt());

        Assert.assertEquals(24, suppliers[0].getAsInt());
        Assert.assertEquals(27, suppliers[1].getAsInt());
        Assert.assertEquals(30, suppliers[2].getAsInt());
        Assert.assertEquals(33, suppliers[3].getAsInt());

        Assert.assertEquals(36, suppliers[0].getAsInt());
        Assert.assertEquals(39, suppliers[1].getAsInt());
        Assert.assertEquals(42, suppliers[2].getAsInt());
        Assert.assertEquals(45, suppliers[3].getAsInt());
    }

    @Test
    public void test_for_statement_method_reference_1() throws Throwable {
        Predicate<Object>[] predicators = (Predicate<Object>[]) TestUtil.eval(
            "Predicate<Object>[] predicators = new Predicate<Object>[4];",
            "for (int i = 0; i < predicators.length; i++) {",
            "   predicators[i] = (i+\"\")::equals;",
            "}",
            "return predicators;"
        );
        Assert.assertTrue(predicators[0].test("0"));
        Assert.assertTrue(predicators[1].test("1"));
        Assert.assertTrue(predicators[2].test("2"));
        Assert.assertTrue(predicators[3].test("3"));
    }

    @Test
    public void test_for_statement_method_reference_2() throws Throwable {
        Predicate<Object>[] predicators = (Predicate<Object>[]) TestUtil.eval(
            "Predicate<Object>[] predicators = new Predicate<Object>[4];",
            "for (int i = 0; i < predicators.length; i++) {",
            "   predicators[i] = i.toString()::equals;",
            "}",
            "return predicators;"
        );
        Assert.assertTrue(predicators[0].test("0"));
        Assert.assertTrue(predicators[1].test("1"));
        Assert.assertTrue(predicators[2].test("2"));
        Assert.assertTrue(predicators[3].test("3"));
    }

    @Test
    public void test_for_statement_method_reference_3() throws Throwable {
        Predicate<Object>[] predicators = (Predicate<Object>[]) TestUtil.eval(
            "String[] items = { \"0\", \"1\", \"2\", \"3\" };",
            "Predicate<Object>[] predicators = new Predicate<Object>[4];",
            "for (String i : items) {",
            "   predicators[Integer.parseInt(i)] = i::equals;",
            "}",
            "return predicators;"
        );
        Assert.assertTrue(predicators[0].test("0"));
        Assert.assertTrue(predicators[1].test("1"));
        Assert.assertTrue(predicators[2].test("2"));
        Assert.assertTrue(predicators[3].test("3"));
    }

    @Test
    public void test_method_reference_namespace() throws Throwable {
        Predicate<Object> predicate = (Predicate<Object>) TestUtil.eval(
            "String num = \"10\";",
            "Predicate<Object> predicate = num::equals;",
            "num = \"20\";",
            "return predicate;"
        );
        Assert.assertTrue(predicate.test("10"));
    }

    @Test
    public void test_invalid_method_reference() throws Throwable {
        EvalError error = Assert.assertThrows(EvalError.class, () -> TestUtil.eval("new ArrayList()::equals.toString();"));
        Assert.assertTrue(error.getMessage().contains("Method Reference must be the last suffix!"));
    }

    @Test
    public void test_isAssignable_static_method_reference() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "public class MyClass {",
            "   public static Object cmp(MyClass a1) { return 1; }",
            "}",
            "return MyClass::cmp;"
        );
        Assert.assertFalse(bshLambda.isAssignable(BshLambda.methodFromFI(Predicate.class), Types.JAVA_BASE_ASSIGNABLE));
    }

    @Test
    public void test_isAssignable_static_method_reference_to_non_static_method() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "public class MyClass {",
            "   public Object cmp() { return 1; }",
            "}",
            "return MyClass::cmp;"
        );
        Assert.assertFalse(bshLambda.isAssignable(BshLambda.methodFromFI(Predicate.class), Types.JAVA_BASE_ASSIGNABLE));
    }

    @Test
    public void test_isAssignable_method_reference() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "public class MyClass {",
            "   public static Object cmp(Object a1) { return 1; }",
            "   public Object cmp() { return 2; }",
            "}",
            "return new MyClass()::cmp;"
        );
        Assert.assertFalse(bshLambda.isAssignable(BshLambda.methodFromFI(Predicate.class), Types.JAVA_BASE_ASSIGNABLE));
    }

    @Test
    public void test_signature_0() throws Throwable {
        Class<?> _class = Runnable.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedamF2YS5sYW5nLlJ1bm5hYmxl", wrapperClass.toGenericString());
        Assert.assertEquals("public void bsh.BshLambdaGeneratedamF2YS5sYW5nLlJ1bm5hYmxl.run()", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertEquals(Runnable.class, genericInterfaces[0]);
    }

    @Test
    public void test_signature_1() throws Throwable {
        Class<?> _class = GenericLambda1.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTE_<T>", wrapperClass.toGenericString());
        Assert.assertEquals("public void bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTE_.exec(T)", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

    @Test
    public void test_signature_2() throws Throwable {
        Class<?> _class = GenericLambda2.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTI_<T>", wrapperClass.toGenericString());
        Assert.assertEquals("public T bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTI_.exec()", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

    @Test
    public void test_signature_3() throws Throwable {
        Class<?> _class = GenericLambda3.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTM_<T,R>", wrapperClass.toGenericString());
        Assert.assertEquals("public R bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTM_.exec(T)", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

    @Test
    public void test_signature_4() throws Throwable {
        Class<?> _class = GenericLambda4.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTQ_<T extends java.util.Map<java.lang.String, java.lang.Object>>", wrapperClass.toGenericString());
        Assert.assertEquals("public void bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTQ_.exec(T)", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

    
	@Test
    public void test_signature_5() throws Throwable {
        Class<?> _class = GenericLambda5.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTU_<T extends java.lang.Thread>", wrapperClass.toGenericString());
        Assert.assertEquals("public T bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTU_.exec()", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

	@Test
    public void test_signature_6() throws Throwable {
        Class<?> _class = GenericLambda6.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTY_<T extends java.util.Map<java.lang.String, java.lang.Object>,R extends java.lang.Thread>", wrapperClass.toGenericString());
        Assert.assertEquals("public R bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTY_.exec(T)", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

	@Test
    public void test_signature_7() throws Throwable {
        Class<?> _class = GenericLambda7.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTc_<T extends java.lang.Throwable>", wrapperClass.toGenericString());
        Assert.assertEquals("public void bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTc_.exec() throws T", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

	@Test
    public void test_signature_8() throws Throwable {
        Class<?> _class = GenericLambda8.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTg_<T extends java.io.IOException>", wrapperClass.toGenericString());
        Assert.assertEquals("public void bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTg_.exec() throws T", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

	@Test
    public void test_signature_9() throws Throwable {
        Class<?> _class = GenericLambda9.class;
        Method method = BshLambda.methodFromFI(_class);

        Class<?> wrapperClass = BshLambda.getClassForFI(_class);
        Method wrapperMethod = wrapperClass.getMethod(method.getName(), method.getParameterTypes());

        Assert.assertEquals("public class bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTk_<A extends java.util.List<? super java.lang.Number> & java.lang.Runnable,B extends java.util.Set<? extends java.lang.String>,C extends java.lang.NullPointerException>", wrapperClass.toGenericString());
        Assert.assertEquals("public A bsh.BshLambdaGeneratedYnNoLkJzaExhbWJkYVRlc3QkR2VuZXJpY0xhbWJkYTk_.exec(B) throws C", wrapperMethod.toGenericString());

        Assert.assertEquals(wrapperClass, wrapperMethod.getDeclaringClass());
        assertArrayEquals(_class.getTypeParameters(), wrapperClass.getTypeParameters());
        assertArrayEquals(method.getGenericParameterTypes(), wrapperMethod.getGenericParameterTypes());
        assertEquals(method.getReturnType(), wrapperMethod.getReturnType());
        assertArrayEquals(method.getGenericExceptionTypes(), wrapperMethod.getGenericExceptionTypes());

        Type[] genericInterfaces = wrapperClass.getGenericInterfaces();
        Assert.assertEquals(1, genericInterfaces.length);
        Assert.assertTrue(genericInterfaces[0] instanceof ParameterizedType);
        ParameterizedType paramType = (ParameterizedType) genericInterfaces[0];
        Assert.assertEquals(_class, paramType.getRawType());
        assertArrayEquals(_class.getTypeParameters(), paramType.getActualTypeArguments());
    }

    @Test
    public void test_convertTo_generic_arg_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething(Integer num) {}",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda1<Integer> lambda = bshLambda.convertTo(GenericLambda1.class);
        lambda.exec(0);
    }

    @Test
    public void test_convertTo_generic_arg_valid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyClass {\n" +
            "   public void doSomething() {}\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        GenericLambda1 lambda = bshLambda.convertTo(GenericLambda1.class);
        lambda.exec(bsh.getNameSpace().getVariable("myObj"));
    }

    @Test
    public void test_convertTo_generic_arg_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public void doSomething(Integer num) {}",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda1.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda1"));
    }

    @Test
    public void test_convertTo_generic_arg_invalid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyClass {\n" +
            "   public void doSomething() {}\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        GenericLambda1 lambda = bshLambda.convertTo(GenericLambda1.class);
        Assert.assertThrows(RuntimeEvalError.class, () -> lambda.exec(new ArrayList<>()));
        Assert.assertThrows(RuntimeEvalError.class, () -> lambda.exec(null));
    }

    @Test
    public void test_convertTo_generic_return_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Map<?, ?> doSomething() { return Collections.EMPTY_MAP; }",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda2<Map<?, ?>> lambda = bshLambda.convertTo(GenericLambda2.class);
        Assert.assertEquals(Collections.EMPTY_MAP, lambda.exec());
    }

    @Test
    public void test_convertTo_generic_return_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public Integer doSomething() { return 0; }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda2.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda2"));
    }

    @Test
    public void test_convertTo_generic_return_invalid_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething() {}",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda2.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda2"));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Map<?, ?> doSomething(MyClass obj) { return Collections.EMPTY_MAP; }",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda3<?, Map<?, ?>> lambda = bshLambda.convertTo(GenericLambda3.class);
        Assert.assertEquals(Collections.EMPTY_MAP, lambda.exec(null));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_valid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyClass {\n" +
            "   public MyClass doSomething() { return this; }\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        Object myObj = bsh.getNameSpace().getVariable("myObj");
        GenericLambda3 lambda = bshLambda.convertTo(GenericLambda3.class);
        Assert.assertEquals(myObj, lambda.exec(myObj));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public void doSomething() {}",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda3.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda3"));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_invalid_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Map<?, ?> doSomething() { return Collections.EMPTY_MAP; }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda3.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda3"));
    }

    @Test
    public void test_convertTo_generic_arg_with_bound_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething(HashMap<String, Object> map) {}",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda4<HashMap<String, Object>> lambda = bshLambda.convertTo(GenericLambda4.class);
        lambda.exec(new HashMap<>());
    }

    @Test
    public void test_convertTo_generic_arg_with_bound_valid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyClass extends HashMap<String, Object> {\n" +
            "   public void doSomething() {}\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        GenericLambda4 lambda = bshLambda.convertTo(GenericLambda4.class);
        lambda.exec((Map<String, Object>) bsh.getNameSpace().getVariable("myObj"));
    }

    @Test
    public void test_convertTo_generic_arg_with_bound_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public void doSomething(Integer num) {}",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda4.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda4"));
    }

    @Test
    public void test_convertTo_generic_arg_with_bound_invalid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyClass extends HashMap<String, Object> {\n" +
            "   public void doSomething() {}\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        GenericLambda4 lambda = bshLambda.convertTo(GenericLambda4.class);
        Assert.assertThrows(RuntimeEvalError.class, () -> lambda.exec(new HashMap<>()));
        Assert.assertThrows(RuntimeEvalError.class, () -> lambda.exec(null));
    }

    @Test
    public void test_convertTo_generic_return_with_bound_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyThread extends Thread {}",
            "class MyClass {",
            "   public static MyThread doSomething() { return new MyThread(); }",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda5<?> lambda = bshLambda.convertTo(GenericLambda5.class);
        Assert.assertNotNull(lambda.exec());
    }

    @Test
    public void test_convertTo_generic_return_with_bound_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Integer doSomething() { return 0; }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda5.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda5"));
    }

    @Test
    public void test_convertTo_generic_return_with_bound_invalid_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething() { }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda5.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda5"));
    }

    @Test
    public void test_convertTo_generic_return_with_bound_invalid_3() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public Thread doSomething() { return new Thread(); }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda5.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda5"));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_with_bound_valid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Thread doSomething(Map<?, ?> obj) { return new Thread(); }",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda6 lambda = bshLambda.convertTo(GenericLambda6.class);
        Assert.assertNotNull(lambda.exec(null));
        Assert.assertNotNull(lambda.exec(new HashMap<>()));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_with_bound_valid_2() throws Throwable {
        Interpreter bsh = new Interpreter();
        BshLambda bshLambda = (BshLambda) bsh.eval(
            "class MyThread2 extends Thread {}\n" +
            "class MyClass extends HashMap<?, ?> {\n" +
            "   public MyThread2 doSomething() { return new MyThread2(); }\n" +
            "}\n" +
            "myObj = new MyClass();\n" +
            "return MyClass::doSomething;"
        );
        Map<?, ?> myObj = (Map<?, ?>) bsh.getNameSpace().getVariable("myObj");
        GenericLambda6 lambda = bshLambda.convertTo(GenericLambda6.class);
        Assert.assertNotNull(lambda.exec(myObj));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_with_bound_invalid_1() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public Thread doSomething() { return new Thread(); }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda6.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda6"));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_with_bound_invalid_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static Map<?, ?> doSomething(Map<?, ?> obj) { return obj; }",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda6.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda6"));
    }

    @Test
    public void test_convertTo_generic_arg_and_return_with_bound_invalid_3() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething(Map<?, ?> obj) {}",
            "}",
            "return MyClass::doSomething;"
        );
        UtilEvalError error = Assert.assertThrows(UtilEvalError.class, () -> bshLambda.convertTo(GenericLambda6.class));
        Assert.assertTrue(error.getMessage().contains("This BshLambda can't be converted to bsh.BshLambdaTest$GenericLambda6"));
    }

    // @FunctionalInterface // tests to do: 4
    // public static interface GenericLambda7<T extends Throwable> { void exec() throws T; }

    @Test
    public void test_convertTo_generic_throws_valid_1() throws Throwable {
        // BshLambda bshLambda = (BshLambda) TestUtil.eval(
        //     "class MyClass {",
        //     "   public static void doSomething() throws Throwable { throw new Throwable(); }",
        //     "}",
        //     "return MyClass::doSomething;"
        // );
        // GenericLambda7<?> lambda = bshLambda.convertTo(GenericLambda7.class);
        // Throwable error = Assert.assertThrows(Throwable.class, () -> lambda.exec());
        // error.printStackTrace();
        // Assert.assertEquals(Throwable.class, error.getClass());
        // Class<?> clazz = (Class<?>) TestUtil.eval(
        //     "class MyClass {",
        //     "   public static void doSomething() throws Throwable { throw new Throwable(); }",
        //     "}",
        //     "return MyClass.class;"
        // );
        // Class<?> clazz = List.class;
        // System.out.println("clazz: " + clazz);
        // for (Method method: clazz.getMethods()) {
        //     System.out.println(" - " + method.toGenericString());
        // }
    }

    @Test
    public void test_convertTo_generic_throws_valid_2() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething() throws NullPointerException { throw new NullPointerException(); }",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda7<?> lambda = bshLambda.convertTo(GenericLambda7.class);
        // NullPointerException error = Assert.assertThrows(NullPointerException.class, () -> lambda.exec());
        // Assert.assertEquals(NullPointerException.class, error.getClass());
    }

    @Test
    public void test_convertTo_generic_throws_valid_3() throws Throwable {
        BshLambda bshLambda = (BshLambda) TestUtil.eval(
            "class MyClass {",
            "   public static void doSomething() {}",
            "}",
            "return MyClass::doSomething;"
        );
        GenericLambda7<?> lambda = bshLambda.convertTo(GenericLambda7.class);
        lambda.exec(); // Just call the lambda
    }

    // @FunctionalInterface // tests to do: 4
    // public static interface GenericLambda8<T extends IOException> { void exec() throws T; }

    // @FunctionalInterface // tests to do: 6
    // public static interface GenericLambda9<A extends List<? super Number> & Runnable, B extends Set<? extends String>, C extends NullPointerException> { A exec(B value) throws C; }

}
