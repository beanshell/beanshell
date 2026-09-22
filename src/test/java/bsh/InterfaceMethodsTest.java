package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import mypackage.IFoo;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.script;
import static bsh.matchers.StringUtilValue.valueString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class InterfaceMethodsTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void default_interface_method_from_static_context_fails() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot reach instance method: def(Object) from static context"));

        eval(
            "interface AA {",
                "default int def(a) { AB + a; }",
            "}",
            "class AAC implements AA { }",
            "AAC.def(1);"
        );
    }

    @Test
    public void static_interface_field_access_from_interface_static() throws Exception {
        Object ret = eval(
            "interface AA {",
                "AB=99;",
            "}",
            "class AAC implements AA {}",
            "AA.AB;"
        );
        assertEquals("constant is 99", 99, ret);
    }

    @Test
    public void static_interface_field_access_from_class_static() throws Exception {
        Object ret = eval(
            "interface AA {",
                "AB=99;",
            "}",
            "class AAC implements AA {}",
            "AAC.AB;"
        );
        assertEquals("constant is 99", 99, ret);
    }

    @Test
    public void static_interface_method_from_interface_static() throws Exception {
        Object ret = eval(
            "interface AA {",
                "static int ab(a,b,c) { a+b+c; }",
            "}",
            "AA.ab(1,2,3);"
        );
        assertEquals("method returns 1+2+3 = 6", 6, ret);
    }

    @Test
    public void static_interface_method_from_class_static_fails() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Static method ab() not found in class'AAC'"));
        Interpreter interpreter = new Interpreter();
        interpreter.setStrictJava(true);
        interpreter.eval(script(
            "import bsh.Reflect;",
            "interface AA {",
                "static int ab() { 2; }",
            "}",
            "class AAC implements AA {}",
            "AAC.ab();"
        ));
        interpreter.getNameSpace().clear();
    }

    @Test
    public void default_interface_method_inherited_by_class_instance() throws Exception {
        Object ret = eval(
            "interface AA {",
                "AB=99;",
                "default int def(a) { AB + a; }",
            "}",
            "class AAC implements AA {}",
            "new AAC().def(1)"
        );
        assertEquals("method returns 99+1 = 100", 100, ret);
    }

    @Test
    public void default_interface_method_calls_abstract_method_of_class_instance() throws Exception {
        Object ret = eval(
            "interface Foo {",
                "int bar();",
                "default int baz() { return bar() + 1; }",
            "}",
            "class Impl implements Foo { int bar() { return 1; } }",
            "Foo f = new Impl();",
            "f.baz();"
        );
        assertEquals("method returns 1+1 = 2", 2, ret);
    }

    @Test
    public void default_interface_method_dispatches_to_overriding_subclass() throws Exception {
        Object ret = eval(
            "interface Foo {",
                "int bar();",
                "default int baz() { return bar() + 1; }",
            "}",
            "class Impl implements Foo { int bar() { return 1; } }",
            "class Sub extends Impl { int bar() { return 5; } }",
            "new Sub().baz();"
        );
        assertEquals("method returns 5+1 = 6", 6, ret);
    }

    @Test
    public void default_interface_method_reaches_instance_through_this_and_other_defaults() throws Exception {
        Object ret = eval(
            "interface Foo {",
                "int bar(int x);",
                "default int twice(int x) { return this.bar(x) * 2; }",
                "default int baz(int x) { return twice(x) + 1; }",
            "}",
            "class Impl implements Foo { int bar(int x) { return x + 1; } }",
            "new Impl().baz(4);"
        );
        assertEquals("method returns (4+1)*2+1 = 11", 11, ret);
    }

    @Test
    public void class_instance_assignable_to_scripted_interface_type() throws Exception {
        Object ret = eval(
            "interface Foo { int bar(); }",
            "class Impl implements Foo { int bar() { return 1; } }",
            "int use(Foo foo) { return foo.bar(); }",
            "Foo f = new Impl();",
            "f.bar() + ((Foo) new Impl()).bar() + use(new Impl());"
        );
        assertEquals("methods return 1+1+1 = 3", 3, ret);
    }

    @Test
    public void default_interface_method_reads_interface_constant_before_instance_field() throws Exception {
        Object ret = eval(
            "interface A { int X = 10; default int getX() { return X; } }",
            "class B implements A { int X = 99; }",
            "new B().getX();"
        );
        assertEquals("interface constant is 10", 10, ret);
    }

    @Test
    public void default_interface_method_calls_interface_static_method_before_instance_method() throws Exception {
        Object ret = eval(
            "interface A { static int h() { return 1; } default int callH() { return h(); } }",
            "class B implements A { int h() { return 2; } }",
            "new B().callH();"
        );
        assertEquals("interface static method returns 1", 1, ret);
    }

    @Test
    public void default_interface_method_resolves_enclosing_scope_before_instance() throws Exception {
        Object ret = eval(
            "int limit = 7;",
            "int twice(int x) { return x * 2; }",
            "interface A { default int calc() { return twice(limit); } }",
            "class B implements A { int limit = 99; int twice(int x) { return 0; } }",
            "new B().calc();"
        );
        assertEquals("script's twice(limit) is 7*2 = 14", 14, ret);
    }

    @Test
    public void abstract_interface_method_not_implemented_fails() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("ZZC is not abstract and does not override abstract method ab() in ZZ"));

        final Interpreter interpreter = new Interpreter();
        interpreter.setStrictJava(true);
        interpreter.eval(script(
            "interface ZZ {",
                "int ab();",
            "}",
            "class ZZC implements ZZ { }"
        ));
        interpreter.getNameSpace().clear();
    }

    @Test
    public void abstract_interface_method_cannot_reduce_visibility() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot reduce the visibility of the inherited method from ZZ"));

        final Interpreter interpreter = new Interpreter();
        interpreter.setStrictJava(true);
        interpreter.eval(script(
            "interface ZZ {",
                "int ab();",
            "}",
            "class ZZC implements ZZ { protected int ab() { 1; } }"
        ));
        interpreter.getNameSpace().clear();
    }

    @Test
    public void static_interface_method_from_parent_interface_static() throws Exception {
        Object ret = eval(
            "interface BB {",
                "static int b() { return 1; }",
            "}",
            "interface BBB extends BB {",
                "static int b() { return 2; }",
            "}",
            "BBB.b();"
        );
        assertEquals("parent method returns 2", 2, ret);
    }

    @Test
    public void static_interface_method_from_child_interface_static() throws Exception {
        Object ret = eval(
            "interface BB {",
                "static int b() { 1; }",
            "}",
            "interface BBB extends BB {",
                "static int b() { 2; }",
            "}",
            "BB.b();"
        );
        assertEquals("child method returns 1", 1, ret);
    }

    @Test
    public void default_interface_method_overridden_by_parent_interface() throws Exception {
        Object ret = eval(
            "interface CC {",
                "default int b() { return 1; }",
            "}",
            "interface CCC extends CC {",
                "default int b() { 2; }",
            "}",
            "class CCCC implements CCC {}",
            "new CCCC().b();"
        );
        assertEquals("method returns 2", 2, ret);
    }

    @Test
    public void get_interface_test_primitive_methods() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
                "import mypackage.IFoo;",
                "boolean fieldBool = false;",
                "int fieldInt = 0;",
                "Boolean fieldBool2 = false;",
                "List run() {",
                    "fieldBool = ! fieldBool;",
                    "fieldBool2 = ! fieldBool2;",
                    "fieldInt++;",
                    "List list = new ArrayList();",
                    "list.add(fieldBool instanceof bsh.Primitive);",
                    "list.add(fieldBool);",
                    "list.add(fieldInt instanceof bsh.Primitive);",
                    "list.add(fieldInt);",
                    "list.add(fieldBool2 instanceof bsh.Primitive);",
                    "list.add(fieldBool2);",
                    "return list;",
                "}"
        ));
        IFoo foo = (IFoo) bsh.getInterface(IFoo.class);
        assertThat(foo.run(), valueString("[true, true, true, 1I, false, true]"));
        assertTrue("boolean field is Primitive", (Boolean)foo.run().get(0));
        assertTrue("boolean field value is true", (Boolean)foo.run().get(1));
        assertTrue("int field is Primitive", (Boolean)foo.run().get(2));
        assertEquals("int field value is 5 (called 5 times)", 5, (int)foo.run().get(3));
        assertFalse("Boolean wrapper type field is NOT Primitive", (Boolean)foo.run().get(4));
        assertTrue("Boolean wrapper type field value is true", (Boolean)foo.run().get(5));
        bsh.getNameSpace().clear();
    }

}
