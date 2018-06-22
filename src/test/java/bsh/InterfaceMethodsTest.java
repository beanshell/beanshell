package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.script;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

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

        eval("import bsh.Reflect;",
            "interface AA {",
                "static int ab() { 2; }",
            "}",
            "class AAC implements AA {}",
            "AAC.ab();"
        );
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
}
