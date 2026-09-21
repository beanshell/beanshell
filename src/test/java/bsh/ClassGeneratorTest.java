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

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ClassGeneratorTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void create_class_with_default_constructor() throws Exception {
        eval("class X1 {}");
    }

    @Test
    public void creating_class_should_not_set_accessibility() throws Exception {
        boolean current = Capabilities.haveAccessibility();
        Capabilities.setAccessibility(false);
        assertFalse("pre: no accessibility should be set", Capabilities.haveAccessibility());
        TestUtil.eval("class X1 {}");
        assertFalse("post: no accessibility should be set", Capabilities.haveAccessibility());
        Capabilities.setAccessibility(current);
    }

    @Test
    public void create_instance() throws Exception {
        assertNotNull(
            eval(
                "class X2 {}",
                "return new X2();"
        ));
    }


    @Test
    public void constructor_args() throws Exception {
        final Object[] oa = (Object[]) eval(
            "class X3 implements IntSupplier {",
                "final Object _instanceVar;",
                "public X3(Object arg) { _instanceVar = arg; }",
                "public int getAsInt() { return _instanceVar; }",
            "}",
            "return new Object[] { new X3(0), new X3(1) } ");
        assertEquals(0, ( (IntSupplier) oa[0] ).getAsInt());
        assertEquals(1, ( (IntSupplier) oa[1] ).getAsInt());
    }

    @Test
    public void large_constructor_dispatch_preserves_constructors() throws Exception {
        StringBuilder script = new StringBuilder(
                "class LargeConstructorDispatch {"
                + "String result;"
                + "LargeConstructorDispatch() { this(\"chained\"); }");
        // Each overload adds a branch to every constructor's dispatch switch.
        // These signatures make the early jumps exceed a signed 16-bit offset.
        for (int count = 1; count <= 95; count++) {
            script.append("LargeConstructorDispatch(");
            for (int parameter = 0; parameter < count; parameter++) {
                if (parameter > 0) {
                    script.append(", ");
                }
                script.append("String p").append(parameter);
            }
            script.append(") { result = p0; }");
        }
        script.append("}");

        assertEquals("chained:direct", eval(script.toString(),
                "return new LargeConstructorDispatch().result + \":\""
                + " + new LargeConstructorDispatch(\"direct\").result;"));
    }

    @Test
    public void generated_members_have_valid_generic_parameter_metadata() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class SignatureProbe extends java.util.ArrayList {",
                    "SignatureProbe(int value) {}",
                    "void probe(String value) {}",
                    "public boolean add(Object value) { return super.add(value); }",
                "}",
                "return SignatureProbe.class;");

        assertArrayEquals(new Class<?>[] { int.class }, type
                .getDeclaredConstructor(int.class).getGenericParameterTypes());
        assertArrayEquals(new Class<?>[] { String.class }, type
                .getDeclaredMethod("probe", String.class).getGenericParameterTypes());
        assertArrayEquals(new Class<?>[] { Object.class }, type
                .getDeclaredMethod("_bshSuperArrayListadd", Object.class)
                .getGenericParameterTypes());
    }


    @Test
    public void call_protected_constructor_from_script() throws Exception {
        final Object[] oa = (Object[]) TestUtil.eval(
            "class X4 implements java.util.concurrent.Callable {",
                "final Object _instanceVar;",
                "X4(Object arg) { _instanceVar = arg; }",
                "public Object call() { return _instanceVar; }",
            "}",
            "return new Object[] { new X4(0), new X4(1) } ");
        assertEquals(0, ( (Callable<?>) oa[0] ).call());
        assertEquals(1, ( (Callable<?>) oa[1] ).call());
    }

    @Test
    public void field_typed_as_the_class_being_generated() throws Exception {
        assertEquals("tail", eval(
            "class Node { Node next; String val; }",
            "Node head = new Node();",
            "head.next = new Node();",
            "head.next.val = \"tail\";",
            "return head.next.val;"));
    }

    @Test
    public void self_typed_field_is_not_shadowed_by_an_imported_class() throws Exception {
        Class<?> cls = (Class<?>) eval(
            "class Node { Node next; }",
            "return Node.class;");
        assertEquals(cls, cls.getDeclaredField("next").getType());
    }

    public static class SuperArgs {
        public final String got;
        public SuperArgs(int a) { got = "int:" + a; }
        public SuperArgs(int a, String b) { got = "int,String:" + a + "," + b; }
        public SuperArgs(String a, String b) { got = "String,String:" + a + "," + b; }
        public SuperArgs(String a, int b, double c) { got = "String,int,double:" + a + "," + b + "," + c; }
    }

    @Test
    public void anonymous_subclass_super_args_of_different_types() throws Exception {
        assertEquals("int,String:1,a", eval(
            "import bsh.ClassGeneratorTest.SuperArgs;",
            "return new SuperArgs(1, \"a\") {}.got;"));
        assertEquals("String,int,double:a,2,3.0", eval(
            "import bsh.ClassGeneratorTest.SuperArgs;",
            "return new SuperArgs(\"a\", 2, 3.0) {}.got;"));
    }

    public static class RefArgs {
        public final String got;
        public RefArgs(String a, Object b) { got = "String,Object:" + a + "," + b; }
    }

    public static class VarArgs {
        public final String got;
        public VarArgs(int a, String... b) { got = "int,String...:" + a + "," + b.length; }
    }

    public static class NullableVarArgs {
        public final String got;
        public NullableVarArgs(int a, String... b) {
            got = "int,String...:" + a + "," + java.util.Arrays.toString(b);
        }
    }

    /** More parameters than the synthetic namer has letters, so the names it
     * makes run past 'z'. The tail is a different type from the rest: 30
     * parameters of one type never collided, so they prove nothing. */
    public static class ManyArgs {
        public final String got;
        public ManyArgs(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15, int a16, int a17, int a18, int a19, int a20, int a21, int a22, int a23, int a24, int a25, int a26, int a27, int a28, int a29, String a30) { got = a1 + ":" + a30; }
    }

    @Test
    public void anonymous_subclass_super_args_of_two_reference_types() throws Exception {
        assertEquals("String,Object:a,o", eval(
            "import bsh.ClassGeneratorTest.RefArgs;",
            "return new RefArgs(\"a\", \"o\") {}.got;"));
    }

    @Test
    public void anonymous_subclass_super_args_with_varargs_tail() throws Exception {
        assertEquals("int,String...:1,2", eval(
            "import bsh.ClassGeneratorTest.VarArgs;",
            "return new VarArgs(1, \"x\", \"y\") {}.got;"));
    }

    @Test
    public void anonymous_subclass_super_args_with_empty_varargs_tail() throws Exception {
        assertEquals("int,String...:1,0", eval(
            "import bsh.ClassGeneratorTest.VarArgs;",
            "return new VarArgs(1) {}.got;"));
    }

    // A scripted class's own constructor (not an anonymous subclass body) delegating
    // via super(...)/this(...) goes through a different code path (This.getConstructorArgs
    // -> the generated constructor's switch bytecode) than the anonymous-subclass tests
    // above (BSHAllocationExpression.superConstructorArgs). Both must pack a varargs tail.

    @Test
    public void scripted_subclass_super_delegation_with_varargs_tail() throws Exception {
        assertEquals("int,String...:1,2", eval(
            "import bsh.ClassGeneratorTest.VarArgs;",
            "class Sub extends VarArgs { Sub() { super(1, \"x\", \"y\"); } }",
            "return new Sub().got;"));
    }

    @Test
    public void scripted_subclass_super_delegation_with_empty_varargs_tail() throws Exception {
        assertEquals("int,String...:1,0", eval(
            "import bsh.ClassGeneratorTest.VarArgs;",
            "class Sub extends VarArgs { Sub() { super(1); } }",
            "return new Sub().got;"));
    }

    @Test
    public void scripted_this_delegation_to_a_scripted_varargs_constructor() throws Exception {
        assertEquals(6, eval(
            "class Runner { int total;",
            "  Runner(int... ns) { for (n : ns) total += n; }",
            "  Runner(boolean flag) { this(1, 2, 3); } }",
            "return new Runner(true).total;"));
    }

    @Test
    public void scripted_this_delegation_to_a_scripted_varargs_constructor_with_empty_tail() throws Exception {
        assertEquals(0, eval(
            "class Runner { int total;",
            "  Runner(int... ns) { for (n : ns) total += n; }",
            "  Runner(boolean flag) { this(); } }",
            "return new Runner(true).total;"));
    }

    @Test
    public void scripted_this_delegation_to_a_scripted_varargs_constructor_with_a_lone_array_argument() throws Exception {
        assertEquals(6, eval(
            "class Runner { int total;",
            "  Runner(int... ns) { for (n : ns) total += n; }",
            "  Runner(boolean flag) { this(new int[]{1, 2, 3}); } }",
            "return new Runner(true).total;"));
    }

    // A null anywhere in a multi-element varargs tail must convert like a cast, not an
    // assignment -- Types.castObject(null, type, ASSIGNMENT) throws, while CAST returns
    // Primitive.NULL. A lone null argument (matching the declared param count) takes a
    // different, already-tested branch and does not exercise this.
    @Test
    public void scripted_subclass_super_delegation_with_a_null_in_the_varargs_tail() throws Exception {
        assertEquals("int,String...:1,[x, null, y]", eval(
            "import bsh.ClassGeneratorTest.NullableVarArgs;",
            "class Sub extends NullableVarArgs { Sub() { super(1, \"x\", null, \"y\"); } }",
            "return new Sub().got;"));
    }

    @Test
    public void scripted_this_delegation_to_a_scripted_varargs_constructor_with_a_null_in_the_tail() throws Exception {
        assertEquals("[a, null, b]", eval(
            "class Runner { Object[] os;",
            "  Runner(Object... os) { this.os = os; }",
            "  Runner(boolean flag) { this(\"a\", null, \"b\"); } }",
            "return java.util.Arrays.toString(new Runner(true).os);"));
    }

    @Test
    public void anonymous_subclass_super_args_past_the_alphabet() throws Exception {
        assertEquals("1:end", eval(
            "import bsh.ClassGeneratorTest.ManyArgs;",
            "return new ManyArgs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, \"end\") {}.got;"));
    }

    @Test
    public void nested_anonymous_subclass_super_args() throws Exception {
        assertEquals("String,int,double:b,2,3.0", eval(
            "import bsh.ClassGeneratorTest.SuperArgs;",
            "outer = new SuperArgs(1, \"a\") {",
                "inner() { return new SuperArgs(\"b\", 2, 3.0) {}.got; }",
            "};",
            "return outer.inner();"));
    }

    @Test
    public void anonymous_subclass_super_args_of_one_type() throws Exception {
        assertEquals("int:1", eval(
            "import bsh.ClassGeneratorTest.SuperArgs;",
            "return new SuperArgs(1) {}.got;"));
        assertEquals("String,String:a,b", eval(
            "import bsh.ClassGeneratorTest.SuperArgs;",
            "return new SuperArgs(\"a\", \"b\") {}.got;"));
    }

    @Test
    public void class_with_abstract_method_must_be_abstract() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Test is not abstract and does not override abstract method x() in Test"));
        final Interpreter interpreter = new Interpreter();
        interpreter.setStrictJava(true);
        interpreter.eval("class Test { abstract void x(); }");
        interpreter.getNameSpace().clear();
    }

    @Test
    public void verify_public_accesible_modifiers() throws Exception {
        TestUtil.cleanUp();
        boolean current = Capabilities.haveAccessibility();
        Capabilities.setAccessibility(false);

        Class<?> cls = (Class<?>) TestUtil.eval(
            "abstract class X6 {",
                "public Object public_var;",
                "private Object private_var = null;",
                "protected Object protected_var = null;",
                "public final Object public_final_var = 0;",
                "final Object final_var = 0;",
                "static Object static_var;",
                "static final Object static_final_var = null;",
                "volatile Object volatile_var;",
                "transient Object transient_var;",
                "no_type_var = 0;",
                "Object no_modifier_var;",
                "X6() {}",
                "just_method() {}",
                "void void_method() {}",
                "Object type_method() {}",
                "synchronized sync_method() {}",
                "final final_method() {}",
                "static static_method() {}",
                "static final static_final_method() {}",
                "abstract abstract_method() {}",
                "public public_method() {}",
                "private private_method() {}",
                "protected protected_method() {}",
            "}",
            "return X6.class;");

        // public class
        assertTrue("class has public modifier", Reflect.getClassModifiers(cls).hasModifier("public"));
        assertTrue("class has abstract modifier", Reflect.getClassModifiers(cls).hasModifier("abstract"));

        // public static variables
        assertTrue("static_var has public modifier", var(cls, "static_var", "public"));
        assertTrue("static_var has static modifier", var(cls, "static_var", "static"));
        assertTrue("static_final_var has public modifier", var(cls, "static_final_var", "public"));
        assertTrue("static_final_var has static modifier", var(cls, "static_final_var", "static"));
        assertTrue("static_final_var has final modifier", var(cls, "static_final_var", "final"));

        // public static methods
        assertTrue("static_method has public modifier", meth(cls, "static_method", "public"));
        assertTrue("static_method has static modifier", meth(cls, "static_method", "static"));
        assertTrue("static_final_method has public modifier", meth(cls, "static_final_method", "public"));
        assertTrue("static_final_method has static modifier", meth(cls, "static_final_method", "static"));
        assertTrue("static_final_method has final modifier", meth(cls, "static_final_method", "final"));

        // public instance variables
        assertTrue("public_var has public modifier", var(cls, "public_var", "public"));
        assertFalse("private_var does not have public modifier", var(cls, "private_var", "public"));
        assertTrue("private_var has private modifier", var(cls, "private_var", "private"));
        assertFalse("protected_var does not have public modifier", var(cls, "protected_var", "public"));
        assertTrue("protected_var has protected modifier", var(cls, "protected_var", "protected"));
        assertTrue("public_final_var has public modifier", var(cls, "public_final_var", "public"));
        assertTrue("public_final_var has final modifier", var(cls, "public_final_var", "final"));
        assertTrue("final_var has public modifier", var(cls, "final_var", "public"));
        assertTrue("final_var has final modifier", var(cls, "final_var", "final"));
        assertTrue("transient_var has public modifier", var(cls, "transient_var", "public"));
        assertTrue("transient_var has transient modifier", var(cls, "transient_var", "transient"));
        assertTrue("volatile_var has public modifier", var(cls, "volatile_var", "public"));
        assertTrue("volatile_var has volatile modifier", var(cls, "volatile_var", "volatile"));
        assertTrue("no_modifier_var has public modifier", var(cls, "no_modifier_var", "public"));
        assertTrue("no_type_var has public modifier", var(cls, "no_type_var", "public"));

        // public instance methods
        assertTrue("constructor has public modifier", meth(cls, "X6", "public"));
        assertTrue("just_method has public modifier", meth(cls, "just_method", "public"));
        assertTrue("void_method has public modifier", meth(cls, "void_method", "public"));
        assertTrue("type_method has public modifier", meth(cls, "type_method", "public"));
        assertTrue("sync_method has public modifier", meth(cls, "sync_method", "public"));
        assertTrue("sync_method has synchronized modifier", meth(cls, "sync_method", "synchronized"));
        assertTrue("final_method has public modifier", meth(cls, "final_method", "public"));
        assertTrue("final_method has final modifier", meth(cls, "final_method", "final"));
        assertTrue("abstract_method has public modifier", meth(cls, "abstract_method", "public"));
        assertTrue("abstract_method has abstract modifier", meth(cls, "abstract_method", "abstract"));
        assertTrue("public_method has public modifier", meth(cls, "public_method", "public"));
        assertFalse("private_method does not have public modifier", meth(cls, "private_method", "public"));
        assertTrue("private_method has private modifier", meth(cls, "private_method", "private"));
        assertFalse("protected_method does not have public modifier", meth(cls, "protected_method", "public"));
        assertTrue("protected_method has protected modifier", meth(cls, "protected_method", "protected"));

        Capabilities.setAccessibility(current);
    }

    private boolean var(Class<?> type, String var, String mod) throws UtilEvalError {
        Variable v = Reflect.getDeclaredVariable(type, var);
        return null != v && v.hasModifier(mod);
    }

    private boolean meth(Class<?> type, String meth, String mod) throws UtilEvalError {
        BshMethod m = Reflect.getDeclaredMethod(type, meth, new Class<?>[0]);
        return null != m && m.hasModifier(mod);
    }

    @Test
    public void outer_namespace_visibility() throws Exception {
        final IntSupplier supplier = (IntSupplier) eval(
            "class X4 implements IntSupplier {",
                "public int getAsInt() { return var; }",
            "}",
            "var = 0;",
            "a = new X4();",
            "var = 1;",
            "return a;");
        assertEquals(1, supplier.getAsInt());
    }


    @Test
    public void static_fields_should_be_frozen() throws Exception {
        final IntSupplier supplier =  (IntSupplier)eval(
                "var = 0;",
                "class X5 implements IntSupplier {",
                    "static final Object VAR = var;",
                    "public int getAsInt() { return VAR; }",
                "}",
                "var = 1;", // class not initialized yet
                "a = new X5();", // lazy initialize
                "var = 2;", // constant X5.VAR unchanged
                "return a;"
        );
        assertEquals(1, supplier.getAsInt());
    }

   @Test
    public void primitive_data_types_class() throws Exception {
        Object object = eval("class Test { public static final int x = 4; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { public static int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { public final int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { static final int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { public int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { static int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { final int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { int x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
        object = eval("class Test { x = 1; }; new Test();");
        assertThat(Reflect.getVariable(object, "x").getValue(), instanceOf(Primitive.class));
    }

   @Test
    public void primitive_data_types_interface() throws Exception {
        Class<?> type = (Class<?>) eval("interface Test { public static final int x = 4; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { public static int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { public final int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { static final int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { public int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { static int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { final int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { int x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
        type = (Class<?>) eval("interface Test { x = 1; }; Test.class;");
        assertThat(Reflect.getVariable(type, "x").getValue(), instanceOf(Primitive.class));
    }

   @Test
    public void unwrapped_return_types_class() throws Exception {
        Object x = eval("class Test { public static final int x = 4; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { public static int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { public final int x = 1; }; new Test().x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { static final int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { public int x = 1; }; new Test().x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { static int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { final int x = 1; }; new Test().x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { int x = 1; }; new Test().x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("class Test { x = 1; }; new Test().x;");
        assertThat(x, instanceOf(Integer.class));
    }

   @Test
    public void unwrapped_return_types_interface() throws Exception {
        Object x = eval("interface Test { public static final int x = 4; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { public static int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { public final int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { static final int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { public int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { static int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { final int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { int x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
        x = eval("interface Test { x = 1; }; Test.x;");
        assertThat(x, instanceOf(Integer.class));
    }

    @Test
    public void interface_constant_fields() throws Exception {
        // In java interface constants are all public final string
        assertEquals(1, eval("interface Test { public static final int x = 1; }; Test.x;"));
        assertEquals(1, eval("interface Test { static final int x = 1; }; Test.x;"));
        assertEquals(1, eval("interface Test { final int x = 1; }; Test.x;"));
        assertEquals(1, eval("interface Test { public static int x = 1; }; Test.x;"));
        assertEquals(1, eval("interface Test { static int x = 1; }; Test.x;"));
        assertEquals(1, eval("interface Test { public static int x = 1; }; Test.x;"));
        assertEquals(2, eval("interface Test { int x = 2; }; Test.x;"));
        assertEquals(3, eval("interface Test { x = 3; }; Test.x;"));
    }

    @Test
    public void interface_constant_field_illegal_modifier() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Illegal modifier for interface field x. "
                + "Only public static & final are permitted."));

        eval("interface Test { protected int x = 2; };");
    }

    @Test
    public void class_static_fields() throws Exception {
        assertEquals(1, eval("class Test { public static final int x = 1; }; Test.x;"));
        assertEquals(1, eval("class Test { static final int x = 1; }; Test.x;"));
        assertEquals(1, eval("class Test { public static int x = 1; }; Test.x;"));
        assertEquals(1, eval("class Test { static int x = 1; }; Test.x;"));
        assertEquals(1, eval("class Test { public static int x = 1; }; Test.x;"));
        assertEquals("1", eval("class Test { public static String x = \"1\"; }; Test.x;"));
        assertEquals(0, eval("class Test { public static int x; }; Test.x;"));
        assertEquals(0, eval("class Test { public static final int x = 0; }; Test.x;"));
    }

    @Test
    public void class_instance_fields() throws Exception {
        assertEquals(1, eval("class Test { public final int x = 1; }; new Test().x;"));
        assertEquals(1, eval("class Test { final int x = 1; }; new Test().x;"));
        assertEquals(1, eval("class Test { public int x = 1; }; new Test().x;"));
        assertEquals(1, eval("class Test { int x = 1; }; new Test().x;"));
        assertEquals(1, eval("class Test { x = 1; }; new Test().x;"));
        assertEquals("1", eval("class Test { public String x = \"1\"; }; new Test().x;"));
        assertEquals(0, eval("class Test { int x; }; new Test().x;"));
        assertEquals(0, eval("class Test { final int x = 0; }; new Test().x;"));
        assertEquals(4, eval("class Test { int x = 4; }; new Test().x;"));
        assertEquals(5, eval("class Test { x = 5; }; new Test().x;"));
        assertEquals(6, eval("class Test { int x; Test() { x=6; } }; new Test().x;"));
    }

    @Test
    public void fields_edge_cases() throws Exception {
        assertEquals(7, eval("class Test { ITest in; int x; class ITest { out() { 7; } } Test() { in = new ITest(); x = in.out(); } }; new Test().x;"));
        assertEquals(8, eval("class Test { ITest in; class ITest { out() { 8; } } Test() { in = new ITest(); } } new Test().in.out();"));
    }

    @Test
    public void define_interface_with_constants() throws Exception {
        // all interface fields are public static final in java
        eval("interface Test { public static final int x = 1; }");
        eval("interface Test { static final int x = 1; }");
        eval("interface Test { final int x = 1; }");
        eval("interface Test { public static int x = 1; }");
        eval("interface Test { static int x = 1; }");
        eval("interface Test { int x = 1; }");
    }

    @Test
    public void scripted_overload_uses_declared_type_of_null_argument() throws Exception {
        assertEquals("Object", eval(
            "class NullOverloadA {",
                "public String test(Object o) { return \"Object\"; }",
                "public String test(Integer i) { return \"Integer\"; }",
            "}",
            "Object o = null;",
            "return new NullOverloadA().test(o);"));
    }

    @Test
    public void java_call_to_generated_overload_runs_that_overload() throws Exception {
        Object instance = eval(
            "class NullOverloadB {",
                "public String test(Object o) { return \"Object\"; }",
                "public String test(Integer i) { return \"Integer\"; }",
            "}",
            "return new NullOverloadB();");
        assertEquals("Object", instance.getClass()
            .getMethod("test", Object.class).invoke(instance, new Object[] {null}));
    }

    @Test
    public void enum_overload_uses_declared_type_of_null_argument() throws Exception {
        assertEquals("Object", eval(
            "enum NullOverloadE { A;",
                "public String test(Object o) { return \"Object\"; }",
                "public String test(Integer i) { return \"Integer\"; }",
            "}",
            "Object o = null;",
            "e = NullOverloadE.A;",
            "return e.test(o);"));
    }

    @Test
    public void scripted_overload_dispatches_non_null_by_runtime_type() throws Exception {
        assertEquals("Integer", eval(
            "class NullOverloadC {",
                "public String test(Object o) { return \"Object\"; }",
                "public String test(Integer i) { return \"Integer\"; }",
            "}",
            "Object o = Integer.valueOf(5);",
            "return new NullOverloadC().test(o);"));
    }

    @Test
    public void subclass_with_same_simple_name_calls_inherited_method() throws Exception {
        assertEquals("pkg383.A", eval(
            "package pkg383;",
            "public class A { public String name() { return \"pkg383.A\"; } }",
            "package pkg383.sub;",
            "public class A extends pkg383.A {}",
            "return new pkg383.sub.A().name();"
        ));
    }

    @Test
    public void class_method_does_not_hide_command_taking_other_arguments() throws Exception {
        Interpreter bsh = new Interpreter();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bsh.setOut(new java.io.PrintStream(out));
        bsh.eval("package pkg383p; public class Printer { public void print() { print(\"from command\"); } }");
        bsh.eval("new pkg383p.Printer().print();");
        assertThat(out.toString(), containsString("from command"));
    }

    @Test
    public void untyped_param_overrides_abstract_method_in_anonymous_subclass() throws Exception {
        assertEquals("got:x", eval(
            "abstract class Base812A { abstract Object run(String s); }",
            "Base812A b = new Base812A() { run(s) { return \"got:\" + s; } };",
            "return b.run(\"x\");"));
    }

    @Test
    public void untyped_param_overrides_abstract_method_in_named_class() throws Exception {
        assertEquals("got:x", eval(
            "abstract class Base812B { abstract Object run(String s); }",
            "class Impl812B extends Base812B { run(s) { return \"got:\" + s; } }",
            "return new Impl812B().run(\"x\");"));
    }

    @Test
    public void untyped_param_override_does_not_cross_talk_between_arities() throws Exception {
        assertEquals("one:x", eval(
            "abstract class Base812C {",
                "abstract Object run(String s);",
                "abstract Object run(String s, String t);",
            "}",
            "Base812C b = new Base812C() {",
                "run(s) { return \"one:\" + s; }",
                "run(s, t) { return \"two:\" + s + t; }",
            "};",
            "return b.run(\"x\");"));
        assertEquals("two:xy", eval(
            "abstract class Base812D {",
                "abstract Object run(String s);",
                "abstract Object run(String s, String t);",
            "}",
            "Base812D b = new Base812D() {",
                "run(s) { return \"one:\" + s; }",
                "run(s, t) { return \"two:\" + s + t; }",
            "};",
            "return b.run(\"x\", \"y\");"));
    }

    @Test
    public void untyped_param_override_stays_object_when_ambiguous() throws Exception {
        Class<?> cls = (Class<?>) eval(
            "abstract class Base812E {",
                "abstract Object run(String s);",
                "abstract Object run(Integer i);",
            "}",
            "class Sub812E extends Base812E { run(x) { return \"got:\" + x; } }",
            "return Sub812E.class;");
        assertNotNull(cls.getDeclaredMethod("run", Object.class));
    }

    @Test
    public void untyped_params_override_abstract_method_with_multiple_parameters() throws Exception {
        assertEquals("a-b", eval(
            "abstract class Base812F { abstract Object join(String a, String b); }",
            "Base812F j = new Base812F() { join(a, b) { return a + \"-\" + b; } };",
            "return j.join(\"a\", \"b\");"));
    }

    @Test
    public void mixed_typed_and_untyped_params_override_abstract_method() throws Exception {
        assertEquals("a-5", eval(
            "abstract class Base812G { abstract Object join(String a, Integer b); }",
            "Base812G j = new Base812G() { join(String a, b) { return a + \"-\" + b; } };",
            "return j.join(\"a\", 5);"));
    }

    @Test
    public void untyped_param_named_wait_does_not_override_final_object_wait() throws Exception {
        Class<?> cls = (Class<?>) eval(
            "class C812H { wait(x) { return x; } }",
            "return C812H.class;");
        assertNotNull(cls);
    }

    @Test
    public void redefining_a_superclass_rebinds_its_subclass() throws Exception {
        assertEquals(2, eval(
            "class P697 { int get() { return 1; } }",
            "class S697 extends P697 { }",
            "class P697 { int get() { return 2; } }",
            "return new S697().get();"));
        assertEquals(true, eval(
            "class P697b { }",
            "class S697b extends P697b { }",
            "class P697b { }",
            "return P697b.class == S697b.class.getSuperclass();"));
    }

    @Test
    public void cascade_failure_does_not_fail_the_triggering_statement() throws Exception {
        Interpreter bsh = new Interpreter();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        bsh.setOut(new java.io.PrintStream(err));
        bsh.eval("class P697c { } class S697c extends P697c { }");
        assertEquals("ok", bsh.eval("final class P697c { } return \"ok\";"));
        assertThat(err.toString(), containsString("Regeneration of class S697c after redefinition of P697c failed"));
        assertThat(err.toString(), containsString("Cannot inherit from final class"));
    }

    @Test
    public void enum_implementing_a_redefined_interface_is_not_regenerated() throws Exception {
        // known limitation: enums are excluded from the cascade
        assertEquals(false, eval(
            "interface I697e { int f(); }",
            "enum E697e implements I697e { X; public int f() { return 1; } }",
            "interface I697e { int f(); }",
            "return I697e.class.isAssignableFrom(E697e.class);"));
    }

    @Test
    public void cascade_does_not_disturb_an_inner_class_binding() throws Exception {
        Interpreter bsh = new Interpreter();
        assertEquals(7, bsh.eval(
            "class P697d { } "
            + "class S697d extends P697d { class In { int v() { return 7; } } int f() { return new In().v(); } } "
            + "class P697d { } "
            + "return new S697d().f();"));
        assertEquals(2, bsh.getClassManager().declarationCount());
    }

    @Test
    @Category(KnownIssue.class)
    public void cascade_pins_regenerated_uninstantiated_classes_in_context_store() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class P697e { } class L1 extends P697e { } class L2 extends P697e { } class L3 extends P697e { }");
        int before = This.contextStore.size();
        bsh.eval("class P697e { }");
        int growth = This.contextStore.size() - before;
        // Regenerated but never-instantiated subclasses each leave a
        // permanent This.contextStore entry (the #843 mechanism). Flip this
        // assertion once that is fixed.
        assertTrue("This.contextStore did not grow -- appears fixed; flip this assertion", growth >= 3);
    }
}
