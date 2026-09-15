package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
    Phase 1 (grammar-only) coverage for lambda expressions, issue #675.
    Asserts parse-tree shape; no evaluation semantics exist yet.
*/
@RunWith(FilteredTestRunner.class)
public class LambdaExpressionParseTest {

    private static SimpleNode parse(String source) throws ParseException {
        Parser parser = new Parser(new StringReader(source));
        SimpleNode node = null;
        while (!parser.Line())
            node = parser.popNode();
        return node;
    }

    /** The statement-level Expression() wrapper every top-level expression gets. */
    private static SimpleNode unwrapAssignment(SimpleNode statement) {
        assertTrue("expected the statement-expression wrapper",
            statement instanceof BSHAssignment);
        return (SimpleNode) statement.jjtGetChild(0);
    }

    @Test
    public void single_untyped_identifier_parameter() throws Exception {
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("x -> x + 1;"));
        assertEquals("x", lambda.paramName);
        assertEquals(1, lambda.jjtGetNumChildren());
        assertTrue(lambda.jjtGetChild(0) instanceof BSHAssignment);
    }

    @Test
    public void zero_arg_parenthesized_form() throws Exception {
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("() -> 5;"));
        assertNull(lambda.paramName);
        assertEquals(2, lambda.jjtGetNumChildren());
        BSHFormalParameters params = (BSHFormalParameters) lambda.jjtGetChild(0);
        assertEquals(0, params.jjtGetNumChildren());
        assertTrue(lambda.jjtGetChild(1) instanceof BSHAssignment);
    }

    @Test
    public void multiple_untyped_parameters() throws Exception {
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("(a, b) -> a + b;"));
        assertNull(lambda.paramName);
        BSHFormalParameters params = (BSHFormalParameters) lambda.jjtGetChild(0);
        assertEquals(2, params.jjtGetNumChildren());
    }

    @Test
    public void typed_parameter() throws Exception {
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("(String s) -> s.length();"));
        BSHFormalParameters params = (BSHFormalParameters) lambda.jjtGetChild(0);
        assertEquals(1, params.jjtGetNumChildren());
        BSHFormalParameter param = (BSHFormalParameter) params.jjtGetChild(0);
        assertEquals("s", param.name);
        assertEquals(1, param.jjtGetNumChildren());
        assertTrue(param.jjtGetChild(0) instanceof BSHType);
    }

    @Test
    public void block_body() throws Exception {
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("() -> { return 5; };"));
        assertEquals(2, lambda.jjtGetNumChildren());
        assertTrue(lambda.jjtGetChild(1) instanceof BSHBlock);
    }

    @Test
    public void cast_to_functional_interface_still_parses() throws Exception {
        SimpleNode body = unwrapAssignment(parse("(Runnable) () -> {};"));
        assertTrue(body instanceof BSHCastExpression);
        assertTrue(body.jjtGetChild(1) instanceof BSHLambdaExpression);
    }

    @Test
    public void lambda_in_both_ternary_branches() throws Exception {
        // a = b ? x -> x : y -> y;  -- exercises Expression()'s own recursion
        // through the lambda hook, not just UnaryExpression()'s.
        parse("a = b ? x -> x : y -> y;");
    }

    @Test
    public void nested_lambda() throws Exception {
        BSHLambdaExpression outer =
            (BSHLambdaExpression) unwrapAssignment(parse("x -> y -> x + y;"));
        assertEquals("x", outer.paramName);
        assertTrue(outer.jjtGetChild(0) instanceof BSHAssignment);
        BSHLambdaExpression inner =
            (BSHLambdaExpression) outer.jjtGetChild(0).jjtGetChild(0);
        assertEquals("y", inner.paramName);
    }

    @Test
    public void lambda_body_binds_tighter_than_enclosing_argument_comma() throws Exception {
        // The lambda body's Expression() must not swallow the "," and stray
        // into the next argument -- this is exactly what the LOOKAHEAD(1)
        // annotations on the precedence chain need to keep working.
        SimpleNode call = unwrapAssignment(parse("dummyFn(x -> x + 1, y);"));
        SimpleNode methodInvocation = (SimpleNode) call.jjtGetChild(0);
        BSHArguments arguments = null;
        for (int i = 0; i < methodInvocation.jjtGetNumChildren(); i++)
            if (methodInvocation.jjtGetChild(i) instanceof BSHArguments)
                arguments = (BSHArguments) methodInvocation.jjtGetChild(i);
        assertEquals(2, arguments.jjtGetNumChildren());
        SimpleNode firstArg = unwrapAssignment((SimpleNode) arguments.jjtGetChild(0));
        assertTrue(firstArg instanceof BSHLambdaExpression);
    }

    @Test
    public void bare_parenthesized_identifier_without_arrow_is_not_a_lambda() throws Exception {
        // (x) + 1 must still parse as a parenthesized expression, not misfire
        // as an attempted lambda parameter list.
        SimpleNode root = unwrapAssignment(parse("(x) + 1;"));
        assertTrue(root instanceof BSHBinaryExpression);
    }

    @Test
    public void cast_expression_without_lambda_still_parses() throws Exception {
        SimpleNode root = unwrapAssignment(parse("(int) 5.0;"));
        assertTrue(root instanceof BSHCastExpression);
    }

    @Test
    public void array_initializer_lambda_body_needs_the_parenthesized_escape_hatch()
            throws Exception {
        // {1,2,3} is a valid BeanShell *expression* (unlike Java), so it
        // collides with the lambda block-body form; the bare form must fail
        // and the parenthesized form must be the documented escape hatch.
        try {
            parse("x -> {1,2,3};");
            fail("expected a ParseException: block-body form must win over "
                + "the array-initializer expression");
        } catch (ParseException expected) {
            // expected
        }
        BSHLambdaExpression lambda =
            (BSHLambdaExpression) unwrapAssignment(parse("x -> ({1,2,3});"));
        assertEquals(1, lambda.jjtGetNumChildren());
        assertTrue(lambda.jjtGetChild(0) instanceof BSHAssignment);
    }

    @Test
    public void lambda_as_an_operator_operand_fails_to_parse() throws Exception {
        String[] sources = { "1 + x -> x;", "x || y -> z;", "x && y -> z;", "x == y -> z;",
            "x * y -> y;", "x ** y -> y;", "x ?? y -> y;", "x | y -> y;", "x ^ y -> y;",
            "x & y -> y;", "x <= y -> y;", "x >> y -> y;", "-x -> x;", "!x -> x;", "~x -> x;",
            "+x -> x;", "a = 1 + () -> 1;", "x -> {} + 1;", "() -> {} instanceof Runnable;",
            "r = () -> {} ? 1 : 2;", "() -> {} = 5;" };
        for (String source : sources) {
            try {
                parse(source);
                fail("expected a ParseException for " + source);
            } catch (ParseException expected) {
                assertEquals(source, 1, expected.getErrorLineNumber());
                assertTrue(source + ": " + expected.getMessage(), expected.getMessage().contains("->"));
            }
        }
    }

    @Test
    public void operator_operand_error_reaches_the_interpreter_before_evaluation() throws Exception {
        Interpreter interpreter = new Interpreter();
        try {
            interpreter.eval("x = false; y = true; z = false; r = x || y -> z;");
            fail("expected an EvalError");
        } catch (EvalError expected) {
            assertNull(interpreter.get("r"));
        }
    }

    @Test
    public void lambdas_outside_operator_operands_still_parse() throws Exception {
        String[] sources = { "r = x -> x;", "r = s = x -> x;", "m() { return x -> x || y; }",
            "x -> y -> x + y;", "(x -> x) + 1;", "f(a, x -> -x, !b);", "r = b ? x -> x : y -> y;",
            "(Runnable) () -> {};", "r += x -> x;", "b ? x : y -> y;" };
        for (String source : sources)
            parse(source);
    }

    @Test
    public void lambda_with_duplicate_parameter_names_fails() throws Exception {
        try {
            new Interpreter().eval("(x, x) -> x;");
            fail("expected an EvalError: duplicate parameter name");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("x"));
        }
    }

    @Test
    public void lambda_mixing_typed_and_untyped_parameters_fails() throws Exception {
        String[] sources = { "(String a, b) -> a;", "(a, String b) -> b;" };
        for (String source : sources) {
            try {
                new Interpreter().eval(source);
                fail("expected an EvalError for " + source);
            } catch (EvalError expected) {
                assertTrue(source, expected.getMessage().length() > 0);
            }
        }
    }
}
