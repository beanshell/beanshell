package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class FinalModifierTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void assignment_to_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable _initVar."));

        eval(
            "class X3 {",
                "final Object _initVar = null;",
                "X3() { _initVar = 0; }",
            "}",
            "new X3();"
        );
    }

    @Test
    public void assignment_to_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable _assVar."));

        eval(
            "class X3 {",
                "final Object _assVar = null;",
            "}",
            "x3 = new X3();",
            "x3._assVar = 0;"
        );
    }

    @Test
    public void non_assignment_to_static_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(ExceptionInInitializerError.class);
        thrown.expectCause(allOf(
            instanceOf(UtilEvalError.class),
            hasProperty("message",
                containsString("Static final variable _staticVar is not initialized."))));

        eval(
            "class X7 {",
                "static final Object _staticVar;",
            "}",
            "X7._staticVar;" // lazy initialize
        );
    }

    @Test
    public void non_assignment_to_static_final_field_late_init_should_not_be_allowed() throws Exception {
        thrown.expect(ExceptionInInitializerError.class);
        thrown.expectCause(allOf(
            instanceOf(UtilEvalError.class),
            hasProperty("message",
                containsString("Static final variable _staticVar is not initialized."))));

        Class<?> clas = (Class<?>) eval(
            "class X7 {",
                "static final Object _staticVar;",
                "static final int _value = 6;",
            "}",
            "X7.class;" // X7 not initialized
        );
        clas.getField("_value").get(null); // lazy initialize
    }

    @Test
    public void non_assignment_to_static_final_field_no_initialize_should_be_allowed() throws Exception {
        Class<?> clas = (Class<?>) eval(
            "class X8 {",
                "static final Object _staticVar;",
            "}",
            "X8.class;" // X8 not initialized
        );
        assertThat("Class name is X8 not initialized", "X8", equalTo(clas.getName()));
    }

    @Test
    public void non_assignment_to_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Final variable _finVar1 is not initialized."));

        eval(
            "class X77 {",
                "final Object _finVar1;",
            "}",
            "new X77();"
        );
    }

    @Test
    public void assignment_to_static_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable _initStaticVar."));

        eval(
            "class X7 {",
                "static final Object _initStaticVar = null;",
                "X7() { _initStaticVar = 0; }",
            "}",
            "new X7();"
        );
    }

    @Test
    public void assignment_to_static_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable _assStaticVar."));

        eval(
            "class X7 {",
                "static final Object _assStaticVar = null;",
            "}",
            "X7._assStaticVar = 0;"
        );
    }

    @Test
    public void assignment_to_unassigned_final_field_is_allowed() throws Exception {
        Object unAssVar = eval(
            "class X3 {",
                "final Object _unAssVar;",
                "X3 () {",
                    "this._unAssVar = 0;",
                "}",
            "}",
            "x3 = new X3();",
            "return x3._unAssVar;"
        );
        assertEquals("Un-assigned field _unAssVal equals 0.", 0, unAssVar);
    }

    @Test
    public void assignment_to_unassigned_static_final_field_in_static_block_is_allowed() throws Exception {
        Object unAssVar = eval(
            "class X3 {",
                "static final Object _staticAssVal;",
                "static { ",
                    "_staticAssVal = 3; ",
                "}",
            "}",
            "return X3._staticAssVal;"
        );
        assertEquals("Un-assigned field _staticAssVal equals 3.", 3, unAssVar);
    }

    @Test
    public void assignment_to_static_final_primitive_field_no_default_value() throws Exception {
        Object unAssVar = eval(
            "class X3 {",
                "static final int _staticAssVal = 4;",
            "}",
            "return X3._staticAssVal;"
        );
        assertEquals("Assigned primitive field _staticAssVal equals 4.", 4, unAssVar);
    }

    @Test
    public void assignment_to_interface_primitive_field_no_default_value() throws Exception {
        Object unAssVar = eval(
            "interface X3 {",
                "int _staticAssVal = 4;",
            "}",
            "return X3._staticAssVal;"
        );
        assertEquals("Assigned primitive field _staticAssVal equals 4.", 4, unAssVar);
    }

    @Test
    public void assignment_to_interface_primitive_field_default_value() throws Exception {
        thrown.expect(ExceptionInInitializerError.class);
        thrown.expectCause(allOf(
            instanceOf(UtilEvalError.class),
            hasProperty("message",
                containsString("Static final variable _staticAssVal is not initialized."))));

        eval(
            "interface X3 {",
                "int _staticAssVal;",
            "}",
            "return X3._staticAssVal;"
        );
    }

    @Test
    public void assignment_to_static_final_primitive_field_default_value() throws Exception {
        thrown.expect(ExceptionInInitializerError.class);
        thrown.expectCause(allOf(
            instanceOf(UtilEvalError.class),
            hasProperty("message",
                containsString("Static final variable _staticAssVal is not initialized."))));

        eval(
            "class X3 {",
                "static final int _staticAssVal;",
            "}",
            "return X3._staticAssVal;"
        );
    }

    @Test
    public void assignment_to_final_primitive_field_default_value() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Final variable _instVal is not initialized."));

        eval(
            "class X3 {",
                "final int _instVal;",
            "}",
            "return new X3()._instVal;"
        );
    }

    @Test
    public void final_in_method_parameter() throws Exception {
        final Object result = eval(
                "String test(final String text) {",
                    "return text;",
                "}",
                "return test(\"abc\");"
        );
        assertEquals("abc", result);
    }

    @Test
    public void final_in_method_parameter_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable text."));

        eval(
                "Object test(final String text, int digit) {",
                    "digit = 2;",
                    "text = \"def\";",
                "}",
                "test(\"abc\", 1);"
        );
    }

    @Test
    public void final_variable_in_method_block() throws Exception {
        final Object result = eval(
                "String test() {",
                    "final String text = \"abc\";",
                    "return text;",
                "}",
                "return test();"
        );
        assertEquals("abc", result);
    }

    @Test
    public void final_variable_in_method_block_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable text."));

        eval(
                "String test() {",
                    "final String text = \"abc\";",
                    "text = \"def\";",
                    "return text;",
                "}",
                "return test();"
        );
    }

    @Test
    public void final_variable_global_scope() throws Exception {
        final Object result = eval(
            "final String text = \"abc\";",
            "return text;"
        );
        assertEquals("abc", result);
    }

    @Test
    public void final_variable_global_scope_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable text."));

        eval(
            "final String text = \"abc\";",
            "text = \"def\";",
            "return text;"
        );
    }

    @Test
    public void final_variable_in_while_loop() throws Exception {
        final Object result = eval(
            "lst = new int[] {1,2,3,4,5};",
            "int p = 0, i = 0;",
            "while(i < lst.length) {",
                "final int tot = p + lst[i++];",
                "p = tot;",
            "}",
            "return p;"
        );
        assertEquals(15, result);
    }

    @Test
    public void final_variable_in_while_loop_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable tot."));

        eval(
            "lst = new int[] {1,2,3,4,5};",
            "int p = 0, i = 0;",
            "while(i < lst.length) {",
                "final int tot = p + lst[i++];",
                "p = tot;",
                "tot = 0;",
            "}"
        );
    }

    @Test
    public void final_variable_in_for_loop() throws Exception {
        final Object result = eval(
            "lst = new int[] {1,2,3,4,5};",
            "tot = 0;",
            "for (final int i : lst)",
                "tot += i;",
            "return tot;"
        );
        assertEquals(15, result);
    }

    @Test
    public void final_variable_in_for_loop_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable i."));

        eval(
            "lst = new int[] {1,2,3,4,5};",
            "tot = 0;",
            "for (final int i : lst)",
                "i += i;"
        );
    }

    @Test
    public void final_variable_in_catch() throws Exception {
        final Object result = eval(
            "try {",
                "throw new Exception(\"abc\");",
            "} catch (final Exception e) {",
                "return e.getMessage();",
            "}"
        );
        assertEquals("abc", result);
    }

    @Test
    public void final_variable_in_catch_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final variable e."));

        eval(
            "try {",
                "throw new Exception(\"abc\");",
            "} catch (final Exception e) {",
                "e = new Exception(\"def\");",
            "}"
        );
    }

    @Test
    public void final_method_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final lastM() {}",
            "}",
            "class C extends P1 {",
                "lastM() {}",
            "}"
        );
    }

    @Test
    public void final_method_with_return_type_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final String lastM() {}",
            "}",
            "class C extends P1 {",
                "lastM() {}",
            "}"
        );
    }

    @Test
    public void final_void_method_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final void lastM() {}",
            "}",
            "class C extends P1 {",
                "lastM() {}",
            "}"
        );
    }

    @Test
    public void final_void_method_should_not_allow_override_return_type() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final void lastM() {}",
            "}",
            "class C extends P1 {",
                "int lastM() {}",
            "}"
        );
    }

    @Test
    public void final_method_with_args_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final lastM(a, e) {}",
            "}",
            "class C extends P1 {",
                "lastM(a, e) {}",
            "}"
        );
    }

    @Test
    public void final_method_with_assignable_args_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final lastM(a, e) {}",
            "}",
            "class C extends P1 {",
                "lastM(String a, Integer e) {}",
            "}"
        );
    }

    @Test
    public void final_method_with_mixed_args_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final lastM(int a, String b, c, Object d) {}",
            "}",
            "class C extends P1 {",
                "lastM(int a, String b, c, Object d) {}",
            "}"
        );
    }

    @Test
    public void final_static_method_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override firstM() in P6 overridden method is final"));

        eval(
            "class P6 {",
                "static final firstM() {}",
            "}",
            "class C extends P6 {",
                "firstM() {}",
            "}"
        );
    }

    @Test
    public void final_static_method_should_not_allow_static_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override firstM() in P6 overridden method is final"));

        eval(
            "class P6 {",
                "static final firstM() {}",
            "}",
            "class C extends P6 {",
                "static firstM() {}",
            "}"
        );
    }

    @Test
    public void final_method_should_not_allow_static_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override firstM() in P6 overridden method is final"));

        eval(
            "class P6 {",
                "final firstM() {}",
            "}",
            "class C extends P6 {",
                "static firstM() {}",
            "}"
        );
    }

    @Test
    public void final_protected_method_should_not_allow_override() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override protM() in P6 overridden method is final"));

        eval(
            "class P6 {",
                "protected final protM() {}",
            "}",
            "class C extends P6 {",
                "protM() {}",
            "}"
        );
    }

    @Test
    public void final_pivate_method_should_allow_override() throws Exception {
        final Object res = eval(
            "class P6 {",
                "private final privM() { return 2; }",
                "public pubP() { return privM(); }",
                "public inhP() { return statC(); }",
                "protected ovrM() { return pubC(); }",
            "}",
            "class C extends P6 {",
                "privM() { return 1 + ovrM(); }",
                "pubC() { return privM() + pubP(); }",
                "public ovrM() { return 0; }",
                "statC() { return super.ovrM(); }",

            "}",
            "return new C().inhP();"
        );
        assertEquals(3, res);
    }

    @Test
    public void final_method_should_not_allow_override_private() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot override lastM() in P1 overridden method is final"));

        eval(
            "class P1 {",
                "final lastM() {}",
            "}",
            "class C extends P1 {",
                "private lastM() {}",
            "}"
        );
    }

    @Test
    public void final_class_should_not_allow_extends() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot inherit from final class P2"));

        eval(
            "final class P2 { }",
            "class C extends P2 { }"
        );
    }

    @Test
    public void final_static_class_should_not_allow_extends() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot inherit from final class P2"));

        eval(
            "static final class P2 { }",
            "class C extends P2 { }"
        );
    }
}
