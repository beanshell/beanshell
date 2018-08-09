package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import mypackage.IFoo;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.script;
import static bsh.matchers.StringUtilValue.valueString;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
        try ( Interpreter interpreter = new Interpreter() ) {
            interpreter.setStrictJava(true);
            interpreter.eval(script(
                "import bsh.Reflect;",
                "interface AA {",
                    "static int ab() { 2; }",
                "}",
                "class AAC implements AA {}",
                "AAC.ab();"
            ));
        }
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
    public void abstract_interface_method_not_implemented_fails() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("ZZC is not abstract and does not override abstract method ab() in ZZ"));

        try (final Interpreter interpreter = new Interpreter()) {
            interpreter.setStrictJava(true);
            interpreter.eval(script(
                "interface ZZ {",
                    "int ab();",
                "}",
                "class ZZC implements ZZ { }"
            ));
        }
    }

    @Test
    public void abstract_interface_method_cannot_reduce_visibility() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot reduce the visibility of the inherited method from ZZ"));

        try (final Interpreter interpreter = new Interpreter()) {
            interpreter.setStrictJava(true);
            interpreter.eval(script(
                "interface ZZ {",
                    "int ab();",
                "}",
                "class ZZC implements ZZ { protected int ab() { 1; } }"
            ));
        }
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
        try (Interpreter bsh = new Interpreter()) {
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
        }
    }

}
