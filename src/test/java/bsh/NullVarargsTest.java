/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
/****************************************************************************/

package bsh;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

/** Java reference calls for issue #778. */
public class NullVarargsTest {
    /** A typed varargs constructor: the tail must be a String[], not Object[]. */
    public static class TypedVarargs {
        public final String kind;
        public TypedVarargs(String... parts) { kind = "String..." + parts.length; }
    }

    public static class Target {
        public Object[] array;
        public Object object;
        public static Object[] staticArray;
        public int calls;
        public final Object[] received;
        public Target(Object... args) { received = args; }
        public String result() { return objects(received); }
        public static String objects(Object... args) {
            return args == null ? "null-array" : Arrays.toString(args);
        }
        public String instance(Object... args) { return objects(args); }
        public static String prefix(String first, Object... args) { return objects(args); }
        public static String strings(String... args) { return objects((Object[]) args); }
        public static String ints(int... args) {
            return args == null ? "null-array" : Arrays.toString(args);
        }
        public Object[] arrayResult() { calls++; return null; }
        public Object objectResult() { calls++; return null; }
        public Target receiver() { calls++; return this; }
        public int index() { calls++; return 0; }
        public static String overloaded(Object arg) { return "Object"; }
        public static String overloaded(Object... args) { return "Object[]:" + objects(args); }
        public static String ambiguous(String arg) { return "String"; }
        public static String ambiguous(char[] arg) { return "char[]"; }
        public static boolean same(Object expected, Object... args) { return expected == args; }
    }

    public static class SuperTarget extends Target {
        public SuperTarget() { super(); }
    }
    public static class ArraySource {
        public Object[] value() { return null; }
    }
    public static class ObjectSource {
        public Object value() { return null; }
    }
    public static class Outer {
        public class Inner extends Target {
            public Inner(Object... args) { super(args); }
        }
    }
    public static class Constructors {
        public final String selected;
        public Constructors(Object arg) { selected = "Object"; }
        public Constructors(Object... args) { selected = "Object[]:" + Target.objects(args); }
    }

    private void compare(Object expected, String script) throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Interpreter interpreter = new Interpreter();
            interpreter.eval("import bsh.NullVarargsTest.Target;");
            interpreter.setStrictJava(strict);
            interpreter.eval("Target target = new Target(); Object[] array = null; Object object = null;");
            assertEquals("strict=" + strict + ": " + script, expected, interpreter.eval(script));
        }
    }

    @Test public void constructors() throws Exception {
        compare(new Target((Object[]) null).result(), "new Target(null).result()");
        compare(new Target((Object[]) null).result(), "new Target((Object[])null).result()");
        compare(new Target((Object) null).result(), "new Target((Object)null).result()");
        compare(new Target().result(), "new Target().result()");
        compare(new Target(null, null).result(), "new Target(null, null).result()");
    }

    @Test public void methods() throws Exception {
        compare(Target.objects((Object[]) null), "Target.objects(null)");
        compare(Target.objects((Object[]) null), "Target.objects((Object[])null)");
        compare(Target.objects((Object) null), "Target.objects((Object)null)");
        compare(new Target().instance((Object[]) null), "target.instance(null)");
        compare(Target.prefix("x", (Object[]) null), "Target.prefix(\"x\", null)");
        compare(Target.strings((String[]) null), "Target.strings(null)");
        compare(Target.strings((String) null), "Target.strings((String)null)");
        compare(Target.ints((int[]) null), "Target.ints(null)");
        compare(Target.ints((int[]) null), "Target.ints((int[])null)");
    }

    @Test public void declared_null_types() throws Exception {
        compare("null-array", "Target.objects(array)");
        compare("[null]", "Target.objects(object)");
        compare("null-array", "Target.objects(((array)))");
        compare("null-array", "Target.objects(target.array)");
        compare("[null]", "Target.objects(target.object)");
        compare("null-array", "Target.objects(Target.staticArray)");
        compare("null-array", "Object[][] arrays = {null}; Target.objects(arrays[0])");
        compare("[null]", "Object[] objects = {null}; Target.objects(objects[0])");
        compare("[null]", "Object[] values = new Object[1][]; Target.objects(values[0])");
        compare("[null]", "Object[] values = new Object[1][]; Target.objects(((Object[])values)[0])");
        compare("null-array", "Object[][] values = new Object[1][]; Target.objects(values[0])");
    }

    @Test public void return_types_and_imports() throws Exception {
        compare("null-array", "Target.objects(target.arrayResult())");
        compare("[null]", "Target.objects(target.objectResult())");
        compare("null-array", "Object[] getArray() { return null; } Target.objects(getArray())");
        compare("[null]", "Object getObject() { return null; } Target.objects(getObject())");
        compare("null-array", "import static bsh.NullVarargsTest.Target.objects; objects(array)");
        compare("[null]", "import static bsh.NullVarargsTest.Target.objects; objects(object)");
        compare("null-array", "target.receiver().instance(array)");
    }

    @Test public void overloads() throws Exception {
        compare(Target.overloaded((Object[]) null), "Target.overloaded(null)");
        compare(Target.overloaded((Object[]) null), "Target.overloaded((Object[])null)");
        compare(Target.overloaded((Object) null), "Target.overloaded((Object)null)");
        compare(Target.overloaded((Object[]) null), "Target.overloaded(array)");
        compare(Target.overloaded((Object) null), "Target.overloaded(object)");
        compare("String", "Target.ambiguous(null)");
    }

    @Test public void evaluation_once_and_order() throws Exception {
        compare("null-array:2", "String value = Target.objects(target.receiver().arrayResult()); value + \":\" + target.calls");
        compare("null-array:1", "String value = Target.objects(target.receiver().array); value + \":\" + target.calls");
        compare("null-array:1", "Object[][] values = {null}; String value = Target.objects(values[target.index()]); value + \":\" + target.calls");
        compare("[1, null, 3]:3", "Target.objects(++target.calls, target.arrayResult(), ++target.calls) + \":\" + target.calls");
    }

    @Test public void repeated_calls_and_existing_arrays() throws Exception {
        compare("null-array[null][][null, null]", "String call(Object[] array, Object object) { return Target.objects(array) + Target.objects(object) + Target.objects() + Target.objects(null, null); } call(array, object)");
        compare(true, "Object[] values = {1, 2}; Target.same(values, values)");
        compare("[null]", "Target.objects(new Object[]{null})");
        compare("[1, 2]", "Target.objects(1, 2)");
        compare("[]", "Target.ints()");
        compare("Object[]:[a]", "Object value = new Object[]{\"a\"}; Target.overloaded(value)");
    }

    @Test public void constructor_overloads_and_inner_classes() throws Exception {
        String imports = "import bsh.NullVarargsTest.Constructors; import bsh.NullVarargsTest.Outer; ";
        compare(new Constructors((Object[]) null).selected, imports + "new Constructors(null).selected");
        compare(new Constructors((Object) null).selected, imports + "new Constructors((Object)null).selected");
        compare("null-array", imports + "Outer outer = new Outer(); outer.new Inner(array).result()");
        compare("[null]", imports + "Outer outer = new Outer(); outer.new Inner(object).result()");
    }

    @Test public void cached_calls_with_different_return_types() throws Exception {
        compare("null-array[null]null-array[null]", "import bsh.NullVarargsTest.ArraySource; import bsh.NullVarargsTest.ObjectSource; "
                + "String call(Object value) { return Target.objects(value.value()); } "
                + "call(new ArraySource()) + call(new ObjectSource()) + call(new ArraySource()) + call(new ObjectSource())");
    }

    @Test public void qualified_script_methods_and_super_calls() throws Exception {
        compare("[null]", "Object getObject() { return null; } Target.objects(this.getObject())");
        compare("null-array", "Object[] getArray() { return null; } Target.objects(this.getArray())");
        compare("[null]", "import bsh.NullVarargsTest.SuperTarget; class Child extends SuperTarget { String call() { return super.instance((Object)null); } } new Child().call()");
        compare("null-array", "import bsh.NullVarargsTest.SuperTarget; class Child extends SuperTarget { String call() { return super.instance((Object[])null); } } new Child().call()");
    }

    @Test public void safe_navigation_and_non_null_dispatch() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals("null-array", interpreter.eval("import bsh.NullVarargsTest.Target; Target target = null; Target.objects(target?.array)"));
        compare("Object[]:[a]", "Target.overloaded((Object)new Object[]{\"a\"})");
        compare("Object", "Target.overloaded(\"a\")");
    }

    @Test public void direct_invocable_null_argument_list() throws Exception {
        Invocable method = Invocable.get(Target.class.getMethod("objects", Object[].class));
        assertEquals("[]", Primitive.unwrap(method.invoke(null, (Object[]) null)));
        assertEquals("null-array", Primitive.unwrap(method.invoke(null, new Object[]{null})));
        assertEquals("null-array", Primitive.unwrap(method.invoke(null, Primitive.NULL)));
    }

    @Test public void assignment_expression_declared_null_types() throws Exception {
        compare("null-array", "Target.objects(array = null)");
        compare("[null]", "Target.objects(object = null)");
    }

    @Test public void property_getter_declared_null_types() throws Exception {
        String bean = "class Bean { Object getFoo() { return null; } Object[] getBars() { return null; } } Bean bean = new Bean(); ";
        compare("[null]", bean + "Target.objects(bean.foo)");
        compare("null-array", bean + "Target.objects(bean.bars)");
        compare("[null]", bean + "Target.objects(bean{\"foo\"})");
        compare("null-array", bean + "Target.objects(bean{\"bars\"})");
    }

    @Test public void ternary_expression_declared_null_types() throws Exception {
        compare("[null]", "Target.objects(true ? object : object)");
        compare("null-array", "Target.objects(true ? array : array)");
    }

    @Test public void anonymous_class_construction_declared_null_types() throws Exception {
        compare(new Target((Object) null).result(), "new Target(object) {}.result()");
        compare(new Target((Object[]) null).result(), "new Target(array) {}.result()");
    }

    @Test public void null_array_is_more_specific_than_object() {
        Class<?>[] nullType = {null};
        assertEquals(1, Reflect.findMostSpecificSignature(nullType,
                new Class<?>[][] {{Object.class}, {Object[].class}}));
        assertEquals(0, Reflect.findMostSpecificSignature(nullType,
                new Class<?>[][] {{Object[].class}, {Object.class}}));
    }

    @Test
    public void anonymous_subclass_wraps_a_typed_varargs_tail() throws Exception {
        assertEquals("String...2", TestUtil.eval(
            "import bsh.NullVarargsTest.TypedVarargs;",
            "return new TypedVarargs(\"a\", \"b\"){}.kind;"));
    }

    @Test
    public void anonymous_subclass_wraps_an_empty_varargs_tail() throws Exception {
        assertEquals("String...0", TestUtil.eval(
            "import bsh.NullVarargsTest.TypedVarargs;",
            "return new TypedVarargs(){}.kind;"));
    }

    @Test
    public void anonymous_subclass_converts_an_object_array_varargs_tail() throws Exception {
        assertEquals("String...2", TestUtil.eval(
            "import bsh.NullVarargsTest.TypedVarargs;",
            "return new TypedVarargs((Object[]){\"a\", \"b\"}){}.kind;"));
        assertEquals("String...0", TestUtil.eval(
            "import bsh.NullVarargsTest.TypedVarargs;",
            "return new TypedVarargs((Object[]){}){}.kind;"));
    }
}
