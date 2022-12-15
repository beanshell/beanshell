package bsh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
public class TryStatementTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void multi_catch() throws Exception {
        Object resource = eval(
            "try {",
                "throw new NullPointerException('Looks like null');",
            "} catch (RuntimeException | Error e) {",
                "return e;",
            "}"
        );
        assertThat(resource, instanceOf(RuntimeException.class));
        assertThat(resource, not(instanceOf(Error.class)));
    }

    @Test
    public void multi_catch_2nd() throws Exception {
        thrown.expect(Exception.class);
        thrown.expectMessage(containsString("Looks like Uncaught"));

        eval(
            "try {",
                "throw new Exception('Looks like Uncaught');",
            "} catch (RuntimeException | Error e) {",
                "return e;",
            "}"
        );
    }

    @Test
    public void multi_catch_3rd() throws Exception {
        Object resource = eval(
            "try {",
                "throw new Error('Looks like Th');",
            "} catch (RuntimeException | Error e) {",
                "return e;",
            "}"
        );
        assertThat(resource, not(instanceOf(RuntimeException.class)));
        assertThat(resource, instanceOf(Error.class));
    }


    @Test
    public void try_with_resource_parsing() throws Exception {
        Object resource = eval(
            "try (ByteArrayOutputStream x = new ByteArrayOutputStream()) {",
                "return x;",
            "} catch (Exception e) {}"
        );
        assertThat(resource, instanceOf(AutoCloseable.class));
        assertThat(resource, instanceOf(ByteArrayOutputStream.class));
    }

    @Test
    public void try_with_resource_parsing_multi() throws Exception {
        Object resource = eval(
            "try (ByteArrayOutputStream x = new ByteArrayOutputStream(); ByteArrayOutputStream y = new ByteArrayOutputStream()) {",
                "return new AutoCloseable[] {x, y};",
            "} catch (Exception e) {}"
        );
        assertThat(resource, instanceOf(AutoCloseable[].class));
        assertThat((AutoCloseable[]) resource, arrayWithSize(2));
        assertThat(Array.get(resource, 0), instanceOf(ByteArrayOutputStream.class));
        assertThat(Array.get(resource, 1), instanceOf(ByteArrayOutputStream.class));
    }

    @Test
    public void try_with_resource_parsing_multi_loosetype() throws Exception {
        Object resource = eval(
            "try (x = new ByteArrayOutputStream(); y = new ByteArrayOutputStream()) {",
                "return new AutoCloseable[] {x, y};",
            "} catch (Exception e) {}"
        );
        assertThat(resource, instanceOf(AutoCloseable[].class));
        assertThat((AutoCloseable[]) resource, arrayWithSize(2));
        assertThat(Array.get(resource, 0), instanceOf(ByteArrayOutputStream.class));
        assertThat(Array.get(resource, 1), instanceOf(ByteArrayOutputStream.class));
    }

    @Test
    public void try_with_resource_non_autocloseable() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("The resource type java.lang.String does not implement java.lang.AutoCloseable."));

        eval(
            "try (x = new String()) {",
                "return x;",
            "} catch (Exception e) {}"
        );
    }

    @Test
    public void try_with_resource() throws Exception {
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
            eval(toMap("autoclosable", autoclosable),
                "try (x = new BufferedOutputStream(autoclosable)) {",
                    "x.write(42);",
                    "x.flush();",
                "} catch (e) {",
                    "throw e;",
                "}"
            );
            fail("expected exception");
        } catch (final Throwable evalError) {
            if (!(evalError.getCause() instanceof IOException))
                throw evalError;
            final Throwable e = evalError.getCause();
            assertSame("same fromWrite exception thrown", fromWrite, e);
            assertThat("1 suppressed exception collected", e.getSuppressed(), arrayWithSize(1));
            assertSame("same fromClose exception thrown", fromClose, e.getSuppressed()[0]);
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
