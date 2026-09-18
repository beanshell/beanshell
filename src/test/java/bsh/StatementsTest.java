package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class StatementsTest {

    @Test
    public void switch_on_strings() throws Exception {
        final Object result = eval(
            "switch('hurz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurz matches hurz", 3, result);
    }

    @Test
    public void switch_to_default() throws Throwable {

        final Object result = eval(
            "switch('hurzzzz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurzzzzz doesn't match any case", 5, result);
    }

    @Test
    public void annotations_are_accepted_and_discarded() throws Exception {
        assertEquals("hi jim", eval(
            "@Deprecated class C {",
                "@SuppressWarnings({\"a\",\"b\"}) String greet(@Deprecated String who) { return \"hi \" + who; }",
            "}",
            "return new C().greet(\"jim\");"));
    }

    @Test
    public void annotation_leaves_no_children_on_the_declaration() throws Exception {
        Class<?> cls = (Class<?>) eval(
            "class C { @Deprecated int n = 5; }",
            "return C.class;");
        assertEquals(int.class, cls.getDeclaredField("n").getType());
    }

    @Test
    public void annotation_name_may_be_qualified() throws Exception {
        assertEquals("ok", eval(
            "class C { @org.junit.Test @java.lang.Deprecated String m() { return \"ok\"; } }",
            "return new C().m();"));
    }

    @Test
    public void annotation_takes_named_pairs_nesting_and_empty_parens() throws Exception {
        assertEquals("ok", eval(
            "class C {",
                "@Foo() @Bar(x=1, y=\"s\") @Outer(@Inner(\"v\")) String m(@Deprecated who) { return \"ok\"; }",
            "}",
            "return new C().m(null);"));
    }

    @Test
    public void method_declarator_may_carry_the_array_brackets() throws Exception {
        assertEquals(3, eval(
            "int f()[] { return new int[]{1,2,3}; }",
            "return f()[2];"));
    }

    @Test
    public void method_declarator_brackets_add_to_the_declared_dimensions() throws Exception {
        assertEquals(int[][].class, eval(
            "int f()[][] { return new int[1][1]; }",
            "return f().getClass();"));
        assertEquals(String[][].class, eval(
            "String[] g()[] { return new String[1][1]; }",
            "return g().getClass();"));
    }

    @Test
    public void method_declarator_brackets_precede_the_throws_clause() throws Exception {
        assertEquals(9, eval(
            "int f()[] throws Exception { return new int[]{9}; }",
            "return f()[0];"));
    }

    @Test
    public void array_class_literal_may_be_parenthesized() throws Exception {
        assertEquals(String[].class, eval("return (String[].class);"));
        assertEquals(String[][].class, eval("return (String[][].class);"));
        assertEquals(String[].class, eval("return (java.lang.String[].class);"));
    }

    @Test
    public void primitive_class_literal_may_be_parenthesized() throws Exception {
        assertEquals(int.class, eval("return (int.class);"));
        assertEquals(int[].class, eval("return (int[].class);"));
        assertEquals(double[][].class, eval("return (double[][].class);"));
    }

    @Test
    public void parenthesized_class_literal_does_not_disturb_casts() throws Exception {
        assertEquals(3, eval("return (int) 3.7;"));
        assertEquals("s", eval("return (String) \"s\";"));
        assertEquals(1, eval("return ((int[]) new int[]{1})[0];"));
    }

    @Test
    public void operator_words_still_lex_as_operators() throws Exception {
        assertEquals(true, eval("return true @or false;"));
        assertEquals(false, eval("return true @and false;"));
        assertEquals(8, eval("return 2 @pow 3;"));
    }

    @Test
    public void operator_words_lex_as_operators_without_surrounding_space()
            throws Exception {
        assertEquals(8, eval("return 2@pow3;"));
        assertEquals(1, eval("a=7; b=3; return a@modb;"));
        assertEquals(true, eval("t=true; f=false; return t@orf;"));
        assertEquals(2, eval("q=12; return q@mod5;"));
    }

    @Test
    public void an_annotation_name_may_begin_with_an_operator_word()
            throws Exception {
        assertEquals("ok", eval(
            "class C { @org.junit.Test @android.Foo String m() { return \"ok\"; } }",
            "return new C().m();"));
    }

    @Test
    public void an_annotation_may_follow_one_that_has_arguments()
            throws Exception {
        assertEquals("ok", eval(
            "class C {",
                "@Foo(a=1) @Outer(@Inner(\"v\")) @org.junit.Test",
                "String m() { return \"ok\"; }",
            "}",
            "return new C().m();"));
    }

    @Test
    public void an_operator_word_still_splits_after_an_annotated_declaration()
            throws Exception {
        assertEquals(8, eval(
            "class C { @Foo String s = \"x\"; int v = 2@pow3; }",
            "return new C().v;"));
    }

    @Test
    public void switch_on_enum() throws Exception {
        Object ret = eval(
            "enum Test { KEY1, KEY2 }",
            "val = Test.KEY1;",
            "switch (val) {",
                "case KEY1:",
                "case KEY2:",
                    "return 'not default';",
                    "break;",
                "default:",
                    "return 'default';",
            "}"
        );
        assertThat("not default branch", ret, equalTo("not default"));
    }

    @Test
    public void do_while_with_empty_statement_body() throws Exception {
        // body is a no-op each time; x++ < 2 is checked (and increments x) for
        // x=0, x=1, x=2 -- true, true, false -- leaving x at 3.
        Object ret = eval(
            "x = 0;",
            "do ; while (x++ < 2);",
            "return x;");
        assertEquals(3, ret);
    }

    @Test
    public void do_while_with_normal_body_still_works() throws Exception {
        Object ret = eval(
            "x = 0;",
            "do { x++; } while (x < 3);",
            "return x;");
        assertEquals(3, ret);
    }

    @Test
    public void while_with_empty_statement_body_still_works() throws Exception {
        Object ret = eval(
            "x = 0;",
            "while (x++ < 2) ;",
            "return x;");
        assertEquals(3, ret);
    }

    @Test
    public void do_while_with_empty_block_body_still_works() throws Exception {
        Object ret = eval(
            "x = 0;",
            "do {} while (x++ < 2);",
            "return x;");
        assertEquals(3, ret);
    }

}
