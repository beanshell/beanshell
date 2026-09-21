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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
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

    @Test
    public void cascade_reaches_a_subclass_of_a_nested_class() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class NBase1 { int v() { return 1; } } "
            + "class Outer1 { static class Inner extends NBase1 { } } "
            + "class Leaf1 extends Outer1.Inner { }");
        assertEquals(1, bsh.eval("return new Leaf1().v();"));
        bsh.eval("class NBase1 { int v() { return 2; } }");
        assertEquals(2, bsh.eval("return new Leaf1().v();"));
        assertEquals(2, bsh.eval("return new Outer1.Inner().v();"));
        assertSame(bsh.eval("return Outer1.Inner.class;"), bsh.eval("return Leaf1.class.getSuperclass();"));
    }

    @Test
    public void cascade_orders_a_nested_class_owner_before_its_subclass() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class Base2 { int v() { return 1; } } class Mid2 extends Base2 { } "
            + "class Outer2 { static class Inner extends Mid2 { } } "
            + "class Leaf2 extends Outer2.Inner { }");
        bsh.eval("class Base2 { int v() { return 2; } }");
        assertEquals(2, bsh.eval("return new Leaf2().v();"));
        assertSame(bsh.eval("return Outer2.Inner.class;"), bsh.eval("return Leaf2.class.getSuperclass();"));
        assertSame(bsh.eval("return Mid2.class;"), bsh.eval("return Outer2.Inner.class.getSuperclass();"));
    }

    @Test
    public void cascade_reaches_a_subclass_of_a_deeply_nested_class() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class Base3 { int v() { return 1; } } "
            + "class Outer3 { static class Mid { static class Inner extends Base3 { } } } "
            + "class Leaf3 extends Outer3.Mid.Inner { }");
        assertEquals(1, bsh.eval("return new Leaf3().v();"));
        bsh.eval("class Base3 { int v() { return 2; } }");
        assertEquals(2, bsh.eval("return new Leaf3().v();"));
        assertSame(bsh.eval("return Outer3.Mid.Inner.class;"), bsh.eval("return Leaf3.class.getSuperclass();"));
    }

    @Test
    public void cascade_reaches_a_subclass_of_a_nested_class_implementing_a_redefined_interface() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("interface I4 { int f(); } "
            + "class Outer4 { static class In implements I4 { public int f() { return 1; } } } "
            + "class Leaf4 extends Outer4.In { }");
        bsh.eval("interface I4 { int f(); }");
        assertEquals(true, bsh.eval("return I4.class.isAssignableFrom(Leaf4.class);"));
        assertSame(bsh.eval("return Outer4.In.class;"), bsh.eval("return Leaf4.class.getSuperclass();"));
    }

    @Test
    public void failed_nested_owner_regeneration_skips_the_subclass_of_its_nested_class() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Base5 { void m() { } } "
            + "class Outer5 { static class Inner extends Base5 { void m() { } } } "
            + "class Leaf5 extends Outer5.Inner { }");
        Object before = bsh.eval("return Leaf5.class;");
        bsh.eval("class Base5 { final void m() { } }");
        assertThat(err.toString(), containsString("Regeneration of class Outer5 after redefinition of Base5 failed"));
        assertSame(before, bsh.eval("return Leaf5.class;"));
    }

    @Test
    public void cascade_reaches_a_subclass_of_a_nested_class_in_a_package() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("package p6; class NBase6 { int v() { return 1; } } "
            + "class Outer6 { static class Inner extends NBase6 { } } "
            + "class Leaf6 extends Outer6.Inner { }");
        assertEquals(1, bsh.eval("return new p6.Leaf6().v();"));
        bsh.eval("package p6; class NBase6 { int v() { return 2; } }");
        assertEquals(2, bsh.eval("return new p6.Leaf6().v();"));
    }

    @Test
    public void nested_class_dependency_cycle_is_reported_not_regenerated() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Base7 { } "
            + "class Outer7 { static class Inner extends Base7 { } static class Other extends Base7 { } } "
            + "class Leaf7 extends Outer7.Inner { }");
        bsh.eval("class Outer7 { static class Inner extends Leaf7 { } static class Other extends Base7 { } }");
        err.reset();
        bsh.eval("class Base7 { }");
        assertThat(err.toString(), containsString("after redefinition of Base7: circular dependency"));
    }

    @Test
    public void redefining_the_owner_of_a_nested_class_still_rebinds_its_subclass() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class NBase8 { } class Outer8 { static class Inner extends NBase8 { } } class Leaf8 extends Outer8.Inner { }");
        bsh.eval("class Outer8 { static class Inner extends NBase8 { int v() { return 5; } } }");
        assertEquals(5, bsh.eval("return new Leaf8().v();"));
        bsh.eval("class NBase8 { }");
        assertEquals(5, bsh.eval("return new Leaf8().v();"));
        assertEquals(3, bsh.getClassManager().declarationCount());
    }

    @Test
    public void redefining_a_subclass_drops_its_old_nested_class_dependency() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.eval("class NBase9 { } class NBase9b { } class Outer9 { static class Inner extends NBase9 { } } "
            + "class Leaf9 extends Outer9.Inner { }");
        for (int i = 0; i < 3; i++)
            bsh.eval("class Leaf9 extends NBase9b { }");
        Object before = bsh.eval("return Leaf9.class;");
        bsh.eval("class NBase9 { }");
        assertSame(before, bsh.eval("return Leaf9.class;"));
        bsh.eval("class NBase9b { }");
        assertNotSame(before, bsh.eval("return Leaf9.class;"));
        assertEquals(4, bsh.getClassManager().declarationCount());
    }

    private static Object evalStatement(Interpreter bsh, String statement) throws Exception {
        Parser parser = new Parser(new java.io.StringReader(statement));
        parser.Line();
        return parser.popNode().eval(new CallStack(bsh.getNameSpace()), bsh);
    }

    @Test
    public void undeclared_supertype_leaves_the_class_pending_with_a_note() throws Exception {
        Interpreter bsh = new Interpreter();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        bsh.setOut(new java.io.PrintStream(err));
        assertSame(Primitive.VOID, evalStatement(bsh, "class Pd696 extends Pd696Base { }"));
        assertThat(err.toString(), containsString(
            "Class Pd696 is pending: unresolved supertype Pd696Base"));
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(true, bsh.eval("return Pd696 == void;"));
    }

    @Test
    public void misspelled_supertype_no_longer_throws_at_its_declaration() throws Exception {
        // accepted trade for late binding: the typo is reported by a note, not an exception
        Interpreter bsh = new Interpreter();
        bsh.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        bsh.eval("class Ms696 extends Objecct { }");
        bsh.eval("class Ms696i implements Runnabel { }");
        assertEquals(2, bsh.getClassManager().pendingCount());
    }

    @Test
    public void redeclaring_a_pending_class_does_not_double_register() throws Exception {
        Interpreter bsh = new Interpreter();
        bsh.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        bsh.eval("class Rd696 extends Rd696Base { }");
        bsh.eval("class Rd696 extends Rd696Base { }");
        bsh.eval("for (int i = 0; i < 3; i++) { class Rd696 extends Rd696Base { } }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(1, bsh.getClassManager().pendingEdgeCount());
        assertEquals(4, bsh.eval("class Rd696Base { public int get() { return 4; } } return new Rd696().get();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void enum_with_an_undeclared_interface_still_throws() throws Exception {
        Interpreter bsh = new Interpreter();
        try {
            bsh.eval("enum En696 implements En696I { A }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("En696I"));
        }
        assertEquals(0, bsh.getClassManager().pendingCount());
    }

    private static Interpreter quietInterpreter(java.io.ByteArrayOutputStream err) {
        Interpreter bsh = new Interpreter();
        bsh.setOut(new java.io.PrintStream(err));
        return bsh;
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1))
            count++;
        return count;
    }

    @Test
    public void declaring_a_pending_class_outright_drops_its_pending_entry() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Z696 extends Z696B { }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        bsh.eval("class Z696 { int v() { return 1; } }");
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
        bsh.eval("class Z696B { }");
        assertEquals(1, bsh.eval("return new Z696().v();"));
        assertEquals(Object.class, bsh.eval("return Z696.class.getSuperclass();"));
    }

    @Test
    public void promotion_in_another_package_drops_the_pending_entry() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pk1 extends Pk1B { }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        bsh.eval("package other696; class Pk1B { }");
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void pending_class_keeps_the_package_it_was_written_in() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pp1 extends Pp1B { int m() { return 7; } }");
        bsh.eval("package foo696; class Pp1B { }");
        assertEquals("Pp1", bsh.eval("return Pp1.class.getName();"));
        assertEquals(7, bsh.eval("return new Pp1().m();"));
        assertEquals("foo696.Pp1B", bsh.eval("return Pp1.class.getSuperclass().getName();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void pending_class_keeps_a_declared_package_when_the_package_changes() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("package p1x696; class Pp2 extends Pp2B { }");
        bsh.eval("package p2x696; class Pp2B { }");
        assertEquals("p1x696.Pp2", bsh.eval("return Pp2.class.getName();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
    }

    @Test
    public void nested_class_of_a_pending_class_keeps_the_package() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pp3 extends Pp3B { static class N { } }");
        bsh.eval("package foo696b; class Pp3B { }");
        assertEquals("Pp3$N", bsh.eval("return Pp3.N.class.getName();"));
        assertEquals("Pp3", bsh.eval("return Pp3.class.getName();"));
    }

    @Test
    public void class_declared_in_a_package_still_gets_it() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        assertEquals("foo696c.Pp4", bsh.eval("package foo696c; class Pp4 { } return Pp4.class.getName();"));
    }

    @Test
    public void default_package_class_still_has_no_package() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        assertEquals("Pp5", bsh.eval("class Pp5 { static class N { } } return Pp5.class.getName();"));
        assertEquals("Pp5$N", bsh.eval("return Pp5.N.class.getName();"));
    }

    @Test
    public void failed_promotion_of_a_pending_class_in_another_package_leaves_nothing_behind() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Pp6 extends Pp6B { void m() { } }");
        bsh.eval("package foo696e; class Pp6B { final void m() { } }");
        assertThat(err.toString(), containsString("Cannot override m()"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
        assertEquals(true, bsh.eval("return Pp6 == void;"));
    }

    @Test
    public void cascade_regeneration_keeps_the_package_the_class_was_declared_in() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("package pc696; class Cb { } class Cs extends Cb { }");
        bsh.eval("package pc696z;");
        Object before = bsh.eval("return pc696.Cs.class;");
        bsh.eval("package pc696; class Cb { int v() { return 5; } }", new NameSpace(bsh.getNameSpace(), "other"));
        assertNotSame(before, bsh.eval("return pc696.Cs.class;"));
        assertEquals("pc696.Cs", bsh.eval("return pc696.Cs.class.getName();"));
        assertEquals(5, bsh.eval("return new pc696.Cs().v();"));
    }

    @Test
    public void simple_supertype_name_matches_a_declaration_in_a_package() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        assertEquals("pk696.Q", bsh.eval(
            "package pk696; class Q extends R { } class R { } return Q.class.getName();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals("pk696.R", bsh.eval("return Q.class.getSuperclass().getName();"));
    }

    @Test
    public void nested_supertype_arrives_with_its_enclosing_class() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Nb696 extends Ob696.In { }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(9, bsh.eval(
            "class Ob696 { static class In { public int get() { return 9; } } } return new Nb696().get();"));
        assertEquals(true, bsh.eval("return Ob696.In.class == Nb696.class.getSuperclass();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
    }

    @Test
    public void reset_forgets_pending_declarations() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Rs696 extends Rs696Base { }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(1, bsh.getClassManager().pendingEdgeCount());
        bsh.reset();
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void failed_promotion_is_reported_once_and_forgotten() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Fp696 extends Fp696B { }");
        err.reset();
        assertEquals("ok", bsh.eval("final class Fp696B { } return \"ok\";"));
        assertEquals(1, occurrences(err.toString(), "could not be generated"));
        assertThat(err.toString(), containsString("Cannot inherit from final class"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void partial_promotion_keeps_only_the_still_missing_edges() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pp696 extends Pp696N implements Pp696J { public int f() { return 6; } }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(2, bsh.getClassManager().pendingEdgeCount());
        bsh.eval("class Pp696N { }");
        assertEquals(1, bsh.getClassManager().pendingCount());
        assertEquals(1, bsh.getClassManager().pendingEdgeCount());
        assertEquals(6, bsh.eval("interface Pp696J { int f(); } return new Pp696().f();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }

    @Test
    public void resolvable_but_invalid_supertype_still_throws_at_its_declaration() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("final class Fs696 { }");
        try {
            bsh.eval("class Gs696 extends Fs696 { }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cannot inherit from final class"));
        }
        try {
            bsh.eval("class Hs696 implements String { }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("is not an interface"));
        }
        assertEquals(0, bsh.getClassManager().pendingCount());
    }

    @Test
    public void promoted_class_brings_its_nested_classes_to_waiting_classes() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Cn696 extends Pn696.In { }");
        bsh.eval("class Pn696 extends Bn696 { static class In { public int get() { return 3; } } }");
        assertEquals(2, bsh.getClassManager().pendingCount());
        assertEquals(3, bsh.eval("class Bn696 { } return new Cn696().get();"));
        assertEquals(0, bsh.getClassManager().pendingCount());
        assertEquals(0, bsh.getClassManager().pendingEdgeCount());
    }
    private static void assertNotDeclared(Interpreter bsh, String name) {
        try {
            bsh.eval("return " + name + ".class;");
            org.junit.Assert.fail(name + " should not resolve");
        } catch (EvalError e) {
            // expected
        }
    }

    private static Interpreter strictInterpreter(java.io.ByteArrayOutputStream err) {
        Interpreter bsh = quietInterpreter(err);
        bsh.setStrictJava(true);
        return bsh;
    }

    @Test
    public void failed_cascade_on_final_override_keeps_the_previous_class_bound() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Pv1 { void m() { } } class Sv1 extends Pv1 { void m() { } }");
        Object before = bsh.eval("return Sv1.class;");
        bsh.eval("class Pv1 { final void m() { } }");
        assertThat(err.toString(), containsString("Regeneration of class Sv1 after redefinition of Pv1 failed"));
        assertSame(before, bsh.eval("return Sv1.class;"));
        assertSame(before, bsh.eval("return new Sv1().getClass();"));
    }

    @Test
    public void failed_strict_cascade_keeps_the_previous_class_bound() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = strictInterpreter(err);
        bsh.eval("interface Iv2 { int f(); } class Cv2 implements Iv2 { public int f() { return 1; } }");
        Object before = bsh.eval("return Cv2.class;");
        bsh.eval("interface Iv2 { int f(); int g(); }");
        assertThat(err.toString(), containsString("Regeneration of class Cv2 after redefinition of Iv2 failed"));
        assertSame(before, bsh.eval("return Cv2.class;"));
        assertEquals(1, bsh.eval("return new Cv2().f();"));
    }

    @Test
    public void failed_promotion_of_a_pending_class_keeps_the_previous_class_bound() throws Exception {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        Interpreter bsh = quietInterpreter(err);
        bsh.eval("class Av3 { void m() { } } class Bv3 extends Av3 { }");
        Object before = bsh.eval("return Bv3.class;");
        bsh.eval("class Bv3 extends Mv3 { void m() { } }");
        bsh.eval("class Mv3 { final void m() { } }");
        assertThat(err.toString(), containsString("Cannot override m()"));
        assertSame(before, bsh.eval("return Bv3.class;"));
        assertSame(before, bsh.eval("return new Bv3().getClass();"));
        assertEquals("Av3", ((Class<?>) before).getSuperclass().getSimpleName());
    }

    @Test
    public void first_definition_overriding_a_final_method_is_not_left_bound() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pv4 { final void m() { } }");
        try {
            bsh.eval("class Sv4 extends Pv4 { void m() { } }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cannot override m() in Pv4 overridden method is final"));
        }
        assertNotDeclared(bsh, "Sv4");
    }

    @Test
    public void first_definition_missing_an_abstract_method_is_not_left_bound_in_strict_mode() throws Exception {
        Interpreter bsh = strictInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("interface Iv4 { int f(); }");
        try {
            bsh.eval("class Cv4 implements Iv4 { }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cv4 is not abstract and does not override abstract method f() in Iv4"));
        }
        assertNotDeclared(bsh, "Cv4");
    }

    @Test
    public void final_override_check_matches_on_exact_parameter_types() throws Exception {
        Interpreter bsh = quietInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("class Pv5 { final void m(int x) { } private final void p() { } }");
        try {
            bsh.eval("class Ev5 extends Pv5 { void m(int x) { } }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cannot override m() in Pv5 overridden method is final"));
        }
        assertNotNull(bsh.eval("class Ov5 extends Pv5 { void m(String x) { } void m(long x) { } } return new Ov5();"));
        assertNotNull(bsh.eval("class Xv5 extends Pv5 { void p() { } } return new Xv5();"));
        assertNotNull(bsh.eval("class Nv5 extends Pv5 { void n() { } } return new Nv5();"));
    }

    @Test
    public void strict_abstract_check_accepts_inherited_and_abstract_class_implementations() throws Exception {
        Interpreter bsh = strictInterpreter(new java.io.ByteArrayOutputStream());
        assertEquals(3, bsh.eval(
            "class Bv6 { public int f() { return 3; } } interface Iv6 { int f(); } "
            + "class Cv6 extends Bv6 implements Iv6 { } return new Cv6().f();"));
        assertNotNull(bsh.eval(
            "interface Jv6 { int f(); int g(); } abstract class Av6 implements Jv6 { public int f() { return 1; } } return Av6.class;"));
    }

    @Test
    public void strict_abstract_check_rejects_reduced_visibility() throws Exception {
        Interpreter bsh = strictInterpreter(new java.io.ByteArrayOutputStream());
        bsh.eval("interface Iv7 { int f(); }");
        try {
            bsh.eval("class Cv7 implements Iv7 { protected int f() { return 1; } }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cannot reduce the visibility of the inherited method from Iv7"));
        }
        assertNotDeclared(bsh, "Cv7");
    }
    @Test
    public void strict_abstract_check_covers_enums_and_ignores_private_methods() throws Exception {
        Interpreter bsh = strictInterpreter(new java.io.ByteArrayOutputStream());
        assertEquals(1, bsh.eval(
            "interface Iv8 { int f(); } enum Ev8 implements Iv8 { A; public int f() { return 1; } } return Ev8.A.f();"));
        try {
            bsh.eval("class Cv8 implements Iv8 { private int f() { return 1; } }");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError e) {
            assertThat(e.getMessage(), containsString("Cv8 is not abstract and does not override abstract method f() in Iv8"));
        }
    }

    @Test
    public void strict_mode_accepts_an_unresolvable_signature_type_until_use() throws Exception {
        Interpreter bsh = strictInterpreter(new java.io.ByteArrayOutputStream());
        assertNotNull(bsh.eval("class Cv9 { int f(Undefined x) { return 1; } } return Cv9.class;"));
        try {
            bsh.eval("return new Cv9().f(null);");
            org.junit.Assert.fail("expected a NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            assertThat(e.getMessage(), containsString("Undefined"));
        }
    }
}
