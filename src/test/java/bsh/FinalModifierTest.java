package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class FinalModifierTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void assignment_to_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _initVar."));

        eval(
            "class X3 {",
                "final Object _initVar = null;",
                "public X3() { _initVar = 0; }",
            "}",
            "new X3();"
        );
    }

    @Test
    public void assignment_to_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _assVar."));

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
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Static final field _staticVar is not set."));

        eval(
            "class X7 {",
                "static final Object _staticVar;",
            "}"
        );
    }

    @Test
    public void assignment_to_static_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _initStaticVar."));

        eval(
            "class X7 {",
                "static final Object _initStaticVar = null;",
                "public X7() { _initStaticVar = 0; }",
            "}",
            "new X7();"
        );
    }

    @Test
    public void assignment_to_static_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _assStaticVar."));

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
            "}",
            "x3 = new X3();",
            "x3._unAssVar = 0;",
            "return x3._unAssVar;"
        );
        assertEquals("Un-assigned field _unAssVal equals 0.", 0, unAssVar);
    }

    @Test
    public void final_in_method_parameter() throws Exception {
        final Object result = eval(
                "String test(final String text){",
                    "return text;",
                "}",
                "return test(\"abc\");"
        );
        assertEquals("abc", result);
    }

    @Test
    public void final_in_method_parameter_should_not_allow_modify() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field text."));

        eval(
                "Object test(final String text, int digit){",
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
        thrown.expectMessage(containsString("Cannot re-assign final field text."));

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
        thrown.expectMessage(containsString("Cannot re-assign final field text."));

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
        thrown.expectMessage(containsString("Cannot re-assign final field tot."));

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
        thrown.expectMessage(containsString("Cannot re-assign final field i."));

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
        thrown.expectMessage(containsString("Cannot re-assign final field e."));

        eval(
            "try {",
                "throw new Exception(\"abc\");",
            "} catch (final Exception e) {",
                "e = new Exception(\"def\");",
                "return e.getMessage();",
            "}"
        );
    }
}
