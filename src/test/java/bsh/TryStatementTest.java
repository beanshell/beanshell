package bsh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
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
            // 2 suppressed exceptions: the bsh-internal TargetError preserving
            // the original script trace (see BSHTryStatement), plus the real
            // exception from close() collected by try-with-resources
            assertThat("2 suppressed exceptions collected", e.getSuppressed(), arrayWithSize(2));
            assertThat("first suppressed is the preserved original bsh trace",
                e.getSuppressed()[0], instanceOf(EvalError.class));
            assertSame("same fromClose exception thrown", fromClose, e.getSuppressed()[1]);
            // the genuine (non-EvalError) suppressed exception must be
            // rendered plainly in the formatted message too -- otherwise
            // it stays invisible outside printStackTrace() (see
            // TargetError.printTargetError)
            assertThat("formatted message shows the genuine suppressed exception",
                evalError.getMessage(), containsString("Suppressed: "));
            assertThat("formatted message includes its own detail message",
                evalError.getMessage(), containsString("exception from close"));
        }
        assertTrue("stream should be closed", closed.get());
    }

    @Test
    public void suppressed_exception_native_stack_frames_are_rendered() throws Exception {
        // Unlike try_with_resource above, the suppressed exception here is
        // thrown by real JDK code (not constructed inline in this
        // bsh-package test class) so its own stack trace has a genuine
        // leading frame outside "bsh." for TargetError.nativeStackFrames
        // to render.
        final IOException fromWrite = new IOException("exception from write");
        final OutputStream autoclosable = new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                throw fromWrite;
            }

            @Override
            public void close() throws IOException {
                new RandomAccessFile("/definitely/does/not/exist/" + System.nanoTime(), "r").close();
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
            assertThat("suppressed exception's own real call site is shown",
                evalError.getMessage(), containsString("at java.base/java.io.RandomAccessFile"));
        }
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

    @Test
    public void uncaught_java_exception_in_try_finally_reports_failing_line() throws Exception {
        try {
            eval(
                "try {",
                "   x = 1;",
                "   Integer.parseInt(\"abc\");",
                "} finally {",
                "}"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            assertEquals(3, e.getErrorLineNumber());
        }
    }

    @Test
    public void java_exception_not_matching_catch_reports_failing_line() throws Exception {
        try {
            eval(
                "try {",
                "   x = 1;",
                "   Integer.parseInt(\"abc\");",
                "} catch (ArithmeticException e) {",
                "}"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            assertEquals(3, e.getErrorLineNumber());
        }
    }

    @Test
    public void uncaught_eval_error_in_try_finally_reports_failing_line() throws Exception {
        try {
            eval(
                "try {",
                "   x = 1;",
                "   undefinedMethodCall();",
                "} finally {",
                "}"
            );
            fail("Expected EvalError");
        } catch (EvalError e) {
            assertEquals(3, e.getErrorLineNumber());
        }
    }

    @Test
    public void nested_target_error_in_try_finally_reports_failing_line() throws Exception {
        try {
            eval(
                "class TryNestedThrower { void fail() { Integer.parseInt(\"abc\"); } }",
                "try {",
                "   new TryNestedThrower().fail();",
                "} finally {",
                "}"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            assertEquals(3, e.getErrorLineNumber());
        }
    }

    @Test
    public void uncaught_exception_message_has_no_duplicate_location() throws Exception {
        // The exception escapes untouched (no catch/finally wrapping), so
        // its own top-level location already is the failing line -- the
        // suppressed original-trace marker BSHTryStatement attaches while
        // unwinding would be entirely redundant here, and must be elided
        // (see TargetError.printTargetError).
        try {
            eval(
                "try {",
                "   Integer.parseInt(\"abc\");",
                "} finally {",
                "}"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            assertThat("no redundant duplicate of the already-correct top-level location",
                e.getMessage(), not(containsString("Originally thrown")));
        }
    }

    @Test
    public void nested_target_error_in_try_finally_preserves_original_throw_site() throws Exception {
        // A call crossing into a scripted class's method (dispatched via
        // reflection, see This.invokeMethod) wraps the exception again at
        // the call site, so the top-level location (line 3, the ".fail()"
        // call) is shallower than where it actually originated (line 1,
        // inside fail()'s body). The deeper original location must still
        // be recoverable from the message, and -- since the escaping
        // exception's own un-flattened cause chain runs through more than
        // one nested TargetError/EvalException layer here -- the message
        // must not embed each intermediate layer's own already-formatted
        // getMessage() recursively (that duplication is what this guards
        // against; see BSHTryStatement's rebuild-on-flatten logic).
        try {
            eval(
                "class TryNestedThrower2 { void fail() { Integer.parseInt(\"abc\"); } }",
                "try {",
                "   new TryNestedThrower2().fail();",
                "} finally {",
                "}"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            String msg = e.getMessage();
            assertThat("deepest original throw site is recoverable",
                msg, containsString("Originally thrown at line 1"));
            assertThat("intermediate call-chain frame is recoverable",
                msg, containsString("Called from top level at line 3"));
            int first = msg.indexOf("Caused by:");
            assertThat("has a Caused by section", first, not(-1));
            assertEquals("Caused by section is not duplicated",
                -1, msg.indexOf("Caused by:", first + 1));
        }
    }

    @Test
    public void call_chain_frame_from_within_catch_block_has_no_block_namespace_leak() throws Exception {
        // A call made from *within* a catch block runs with the catch
        // block's own BlockNameSpace on top of the callstack (it holds the
        // caught variable). That namespace's raw name carries a synthetic
        // "/BlockNameSpaceN" suffix (see BlockNameSpace's constructor) --
        // NameSpace.getDisplayName() must strip it before it reaches a
        // "Called from" frame.
        try {
            eval(
                "void innerCatchCall() { throw new RuntimeException(\"from-catch-call\"); }",
                "void catchCaller() {",
                "    try { throw new RuntimeException(\"first\"); }",
                "    catch (RuntimeException e) { innerCatchCall(); }",
                "}",
                "catchCaller();"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            assertThat("no BlockNameSpace implementation detail leaks into the trace",
                e.getMessage(), not(containsString("BlockNameSpace")));
            assertThat("the enclosing method's plain name is still shown",
                e.getMessage(), containsString("Called from method catchCaller"));
        }
    }

    @Test
    public void deeply_nested_rethrow_elides_shared_call_chain_frames() throws Exception {
        // Mirrors java.lang.Throwable's own common-frame elision for
        // chained exceptions (see TargetError.elideCommonFrames): the
        // "Originally thrown" trace and the enclosing exception's own
        // trace share their outermost frames here (both ultimately trace
        // back through the same deepOuter()/top-level call), so the
        // shared trailing run should collapse to a single "... N more"
        // line instead of repeating it -- while the two frames that
        // *aren't* shared (deepInnerWrap and deepMiddle, only present in
        // the deeper original trace) must still both be printed, joined
        // by a newline.
        try {
            eval(
                "void veryDeepInner() { throw new RuntimeException(\"very-deep-boom\"); }",
                "void deepInnerWrap() { veryDeepInner(); }",
                "void deepMiddle() {",
                "    try { deepInnerWrap(); }",
                "    catch (RuntimeException e) {",
                "        throw new RuntimeException(\"deep-wrapped\", e);",
                "    }",
                "}",
                "void deepOuter() { deepMiddle(); }",
                "deepOuter();"
            );
            fail("Expected TargetError");
        } catch (TargetError e) {
            String msg = e.getMessage();
            assertThat("shared trailing frames are elided",
                msg, containsString("... 2 more"));
            assertThat("first non-elided frame is present",
                msg, containsString("Called from method deepInnerWrap"));
            assertThat("second non-elided frame is present",
                msg, containsString("Called from method deepMiddle"));
        }
    }

    @Test
    public void try_with_resource_no_catch_no_finally() throws Exception {
        Object result = eval(
            "x = 0;",
            "try (java.io.StringReader r = new java.io.StringReader(\"\")) {",
                "x = 1;",
            "}",
            "return x;"
        );
        assertEquals(1, result);
    }

    @Test
    public void try_with_resource_no_catch_no_finally_in_method_body() throws Exception {
        Object result = eval(
            "m() {",
                "x = 0;",
                "try (java.io.StringReader r = new java.io.StringReader(\"\")) {",
                    "x = 1;",
                "}",
                "return x;",
            "}",
            "return m();"
        );
        assertEquals(1, result);
    }

    @Test
    public void try_with_resource_no_catch_no_finally_closes_resource() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean(false);
        final OutputStream autoclosable = new OutputStream() {
            @Override
            public void write(final int b) throws IOException {}

            @Override
            public void close() throws IOException {
                closed.set(true);
            }
        };
        eval(
            toMap("autoclosable", autoclosable),
            "try (x = new BufferedOutputStream(autoclosable)) {",
                "x.write(42);",
            "}"
        );
        assertTrue("stream should be closed", closed.get());
    }

    @Test
    public void bare_try_with_no_resources_catch_or_finally_still_fails_to_parse() throws Exception {
        thrown.expect(Exception.class);

        eval(
            "try {",
            "   x = 1;",
            "}"
        );
    }

}
