package bsh;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.toMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
public class TryStatementTest {

    @Test
    @Category(KnownIssue.class)
    public void try_with_resource_parsing() throws Exception {
        eval(
                "try (ByteArrayOutputStream x = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
        eval(
                "try (ByteArrayOutputStream x = new ByteArrayOutputStream(); ByteArrayOutputStream y = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
        eval(
                "try (x = new ByteArrayOutputStream(); y = new ByteArrayOutputStream()) {",
                "} catch (Exception e) {",
                "}\n"
        );
    }


    @Test
    @Category(KnownIssue.class)
    public void try_with_resource() throws Exception {
        final Interpreter interpreter = new Interpreter();
        final AtomicBoolean closed = new AtomicBoolean(false);
        final IOException fromWrite = new IOException("exception from write");
        final IOException fromClose = new IOException("exception from close");
        final OutputStream autoclosable = new OutputStream() {

            @Override
            public void write(final int b) throws IOException {
                throw fromWrite;
            }


            @Override
            public void close() throws IOException {
                closed.set(true);
                throw fromClose;
            }
        };
        try {
            interpreter.set("autoclosable", autoclosable);
            interpreter.eval(
                    "try (x = new BufferedOutputStream(autoclosable)) {\n" +
                    "   x.write(42);\n" +
                    "} catch (Exception e) {\n" +
                    "   thrownException = e;\n" +
                    "}\n"
            );
            fail("expected exception");
        } catch (final EvalError evalError) {
            if (evalError instanceof ParseException) {
                throw evalError;
            }
            final Throwable e = evalError.getCause();
            assertSame(fromWrite, e);
            interpreter.set("exception", e);
            final Object suppressed = interpreter.eval("return exception.getSuppressed();"); // avoid java 7 syntax in java code ;)
            assertSame(fromClose, suppressed);
        }
        assertTrue("stream should be closed", closed.get());
    }


    @Test
    public void try_catch_finally() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return after try..catch..finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "finally", "after"),
                calls);
    }

    @Test
    public void execute_finally_when_try_block_contains_return() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   return \"return from try\";",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from try", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_catch_block_throws_exception() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   throw new Exception(\"inside try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "   throw new Exception(\"inside catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "   return \"return from finally\";",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "catch", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_catch_block_contains_return_statement() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   throw new Exception(\"inside try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "   return \"return from catch\";",
                "} finally {",
                "   calls.add(\"finally\");",
                "   return \"return from finally\";",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "catch", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_try_block_contains_return_statement() throws Exception {
        final Object result = eval(
                "try {",
                "   return \"return from try\";",
                "} finally {",
                "   return \"return from finally\";",
                "}",
                "return \"return after try..finally\";"
        );
        assertEquals("return from finally", result);
    }

}
