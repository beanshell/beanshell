package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.ref.WeakReference;
import java.util.function.BiFunction;
import java.util.concurrent.Callable;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
    Phase 2 coverage for lambda evaluation (issue #675): direct cast/assignment
    to a functional interface only. Cast-free overload resolution is Phase 3.
*/
@RunWith(FilteredTestRunner.class)
public class BshLambdaTest {

    public interface SerialTriple extends java.io.Serializable { int apply(int x); }
    public interface ReplaceWriter { Object writeReplace(); }
    public interface SerializableReplaceWriter extends java.io.Serializable { Object writeReplace(); }

    // The wrapper generates its own writeReplace, which must not clash with the SAM's.
    @Test
    public void interface_whose_method_is_named_write_replace_is_implementable() throws Exception {
        assertEquals("w", new Interpreter().eval(
            "import bsh.BshLambdaTest.ReplaceWriter; ((ReplaceWriter) () -> \"w\").writeReplace();"));
    }
    public interface RunA { void run(); }
    public interface RunB { void run(); }
    public interface RunAB extends RunA, RunB {}

    // Two inherited abstract methods with one signature are a single SAM.
    @Test
    public void interface_inheriting_the_same_method_twice_is_functional() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "import bsh.BshLambdaTest.RunAB; hit = false;"
            + " ((RunAB) () -> { hit = true; }).run(); hit;"));
    }

    @Test
    public void lambda_expression_evaluates_to_a_bsh_lambda_value() throws Exception {
        Interpreter interpreter = new Interpreter();
        Object result = interpreter.eval("x -> x + 1;");
        assertTrue(result instanceof BshLambda);
        assertEquals("<lambda: (x)>", result.toString());
    }

    @Test
    public void cast_to_runnable_and_invoke_executes_the_body() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("ran = false;");
        Runnable r = (Runnable) interpreter.eval("(Runnable) () -> { ran = true; };");
        r.run();
        assertEquals(Boolean.TRUE, interpreter.get("ran"));
    }

    @Test
    public void typed_variable_declaration_coerces_without_a_cast() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("ran = false;");
        Runnable r = (Runnable) interpreter.eval(
            "Runnable r = () -> { ran = true; }; r;");
        r.run();
        assertEquals(Boolean.TRUE, interpreter.get("ran"));
    }

    @Test
    public void typed_field_declaration_coerces_without_a_cast() throws Exception {
        Interpreter interpreter = new Interpreter();
        Runnable r = (Runnable) interpreter.eval(
            "class Holder { Runnable r; }\n"
            + "h = new Holder(); h.r = () -> {}; h.r;");
        r.run(); // must not throw
    }

    /**
        Path 1 only: the argument is already a real Runnable (materialized
        via the explicit cast) by the time NameSpace.getMethod resolves
        which "accept" overload to call, via Reflect.findMostSpecificBshMethod
        -> Types.findMostSpecificSignature -- the same machinery Java
        overload resolution uses. A *bare* lambda argument routes through
        that same resolution before Invocable.coerceToType ever runs, so
        cast-free scripted-method-parameter passing needs Phase 3's arity
        marker (Types.getType/isJavaBaseAssignable are untouched in Phase 2
        by design); confirmed by reproducing "Command not found" for the
        cast-free form before writing this test.
    */
    @Test
    public void cast_scripted_method_argument_resolves_and_coerces() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("ran = false; accept(Runnable r) { r.run(); }");
        interpreter.eval("accept((Runnable) (() -> { ran = true; }));");
        assertEquals(Boolean.TRUE, interpreter.get("ran"));
    }

    @Test
    public void return_value_is_coerced_and_primitives_are_unwrapped() throws Exception {
        Interpreter interpreter = new Interpreter();
        @SuppressWarnings("unchecked")
        Callable<Integer> c = (Callable<Integer>) interpreter.eval(
            "(java.util.concurrent.Callable) () -> 42;");
        Object result = c.call();
        assertTrue("expected a boxed Integer, not a bsh.Primitive", result instanceof Integer);
        assertEquals(Integer.valueOf(42), result);
    }

    @Test
    public void multi_parameter_lambda_binds_each_argument() throws Exception {
        @SuppressWarnings("unchecked")
        BiFunction<Integer, Integer, Integer> add =
            (BiFunction<Integer, Integer, Integer>) new Interpreter().eval(
                "(BiFunction) (a, b) -> a + b;");
        assertEquals(Integer.valueOf(7), add.apply(3, 4));
    }

    @Test
    public void casting_to_a_non_functional_interface_fails_clearly() throws Exception {
        Interpreter interpreter = new Interpreter();
        try {
            interpreter.eval("(java.util.List) x -> x;");
            fail("expected an EvalError: List is not a functional interface");
        } catch (EvalError expected) {
            // expected -- List declares more than one abstract method
        }
    }

    @Test
    public void arity_mismatch_against_the_target_interface_fails_clearly() throws Exception {
        Interpreter interpreter = new Interpreter();
        try {
            interpreter.eval("(Runnable) (a, b) -> a + b;");
            fail("expected an EvalError: Runnable's SAM takes 0 parameters, not 2");
        } catch (EvalError expected) {
            // expected
        }
    }

    @Test
    public void typed_lambda_parameters_are_bound_with_their_types() throws Exception {
        assertEquals("xy", new Interpreter().eval(
            "((BiFunction) (String a, String b) -> a + b).apply(\"x\", \"y\");"));
    }

    @Test
    public void typed_lambda_parameter_rejects_an_incompatible_argument() throws Exception {
        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, Object> f = (java.util.function.Function<Object, Object>)
            new Interpreter().eval("(Function) (java.util.Date d) -> d;");
        try {
            f.apply("not a date");
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(String.valueOf(expected.getCause()), expected.getCause() instanceof EvalError);
        }
    }

    @Test
    public void untyped_parameter_shadows_an_outer_variable_of_the_same_name() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals(Integer.valueOf(3), interpreter.eval(
            "x = 100; y = 200; java.util.function.BiFunction f = (x, y) -> x + y; f.apply(1, 2);"));
        assertEquals(Integer.valueOf(100), interpreter.get("x"));
        assertEquals(Integer.valueOf(200), interpreter.get("y"));
    }

    @Test
    public void assigning_an_untyped_parameter_does_not_write_the_outer_variable() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals(Integer.valueOf(5), interpreter.eval(
            "x = 7; java.util.function.Function f = x -> { x = 5; return x; }; f.apply(1);"));
        assertEquals(Integer.valueOf(7), interpreter.get("x"));
    }

    @Test
    public void this_in_a_lambda_is_the_enclosing_this() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "m() { java.util.function.Supplier s = () -> this; return s.get() == this; } m();"));
    }

    @Test
    public void this_in_a_lambda_in_a_scripted_class_is_the_instance() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval("class C {"
            + " Object viaLambda() { java.util.function.Supplier s = () -> this; return s.get(); } }"
            + " c = new C(); c.viaLambda() == c;"));
    }

    @Test
    public void field_assigned_through_this_in_a_lambda_reaches_the_instance() throws Exception {
        assertEquals(Integer.valueOf(9), Primitive.unwrap(new Interpreter().eval("class C { int f = 1;"
            + " int setF() { Runnable r = () -> { this.f = 9; }; r.run(); return f; } }"
            + " new C().setF();")));
    }

    @Test
    public void super_in_a_lambda_is_the_enclosing_super() throws Exception {
        assertEquals("3,3", new Interpreter().eval(
            "m() { q = 3; n() { int q = 4; java.util.function.Supplier s = () -> super.q;"
            + " return s.get() + \",\" + super.q; } return n(); } m();"));
    }

    @Test
    public void super_method_call_in_a_lambda_reaches_the_superclass() throws Exception {
        assertEquals("A", new Interpreter().eval(
            "class A { String hi() { return \"A\"; } }"
            + " class B extends A { String hi() { return \"B\"; }"
            + " String viaLambda() { java.util.function.Supplier s = () -> super.hi(); return s.get(); } }"
            + " new B().viaLambda();"));
    }

    // Like a This proxy, a deserialized lambda must not run: a stream that could
    // run script code while being read is a deserialization gadget (CVE-2016-2510).
    private static void assertRefusesToRun(Runnable call) {
        try {
            call.run();
            fail("a deserialized lambda must not run");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("deserialized"));
        }
    }

    @Test
    public void interpreter_holding_a_raw_lambda_serializes_but_the_lambda_does_not_run() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("inc = x -> x + 1;");
        Interpreter copy = TestUtil.serDeser(interpreter);
        java.util.function.Function<?, ?> inc = (java.util.function.Function<?, ?>)
            copy.eval("(java.util.function.Function) inc;");
        assertRefusesToRun(() -> inc.apply(null));
    }

    @Test
    public void interpreter_holding_a_converted_lambda_serializes_but_the_lambda_does_not_run() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("import bsh.BshLambdaTest.SerialTriple; n = 0; "
            + "SerialTriple triple = (SerialTriple) x -> { n++; return x; };");
        Interpreter copy = TestUtil.serDeser(interpreter);
        SerialTriple triple = (SerialTriple) copy.get("triple");
        assertRefusesToRun(() -> triple.apply(1));
        assertEquals(Integer.valueOf(0), Primitive.unwrap(copy.eval("n;")));
    }

    @Test
    public void lambda_for_a_serializable_interface_serializes_but_does_not_run() throws Exception {
        SerialTriple triple = TestUtil.serDeser((SerialTriple) new Interpreter().eval(
            "import bsh.BshLambdaTest.SerialTriple; (SerialTriple) x -> x * 3;"));
        assertRefusesToRun(() -> triple.apply(2));
    }

    @Test
    public void plain_runnable_wrapper_is_not_serializable() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval("(Runnable) () -> {};");
        assertFalse(r instanceof java.io.Serializable);
    }

    @Test
    public void serializable_interface_whose_sam_is_write_replace_is_rejected() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.SerializableReplaceWriter;"
                + " (SerializableReplaceWriter) () -> \"x\";");
            fail("expected an EvalError: no safe writeReplace hook can be generated");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("writeReplace"));
        }
    }

    @Test
    public void deserializing_a_lambda_comparator_does_not_run_its_body() throws Exception {
        // A system property, not a script variable: the copy would write its own namespace.
        String ran = "bsh.BshLambdaTest.comparatorRan";
        @SuppressWarnings("unchecked")
        java.util.Comparator<Object> comparator = (java.util.Comparator<Object>) new Interpreter().eval(
            "(java.util.Comparator) (a, b) -> { System.setProperty(\"" + ran + "\", \"yes\"); return 0; };");
        java.util.PriorityQueue<Object> queue = new java.util.PriorityQueue<>(2, comparator);
        queue.add("a");
        queue.add("b");
        System.clearProperty(ran);
        try {
            TestUtil.serDeser(queue);
            fail("reading the queue must fail, as it does for a This proxy");
        } catch (RuntimeException expected) {
            assertNull(System.getProperty(ran));
        } finally {
            System.clearProperty(ran);
        }
    }

    @Test
    public void break_escaping_a_block_body_fails() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval("(Runnable) () -> { break; };");
        try {
            r.run();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("'continue' or 'break'"));
        }
    }

    @Test
    public void continue_escaping_a_block_body_fails() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval("(Runnable) () -> { continue; };");
        try {
            r.run();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("'continue' or 'break'"));
        }
    }

    @Test
    public void break_inside_a_loop_in_a_block_body_is_not_an_escape() throws Exception {
        assertEquals(Integer.valueOf(3), Primitive.unwrap(new Interpreter().eval(
            "n = 0; ((Runnable) () -> { while (true) { if (++n == 3) break; } }).run(); n;")));
    }

    @Test
    public void casting_to_a_class_fails_clearly() throws Exception {
        try {
            new Interpreter().eval("(String) x -> x;");
            fail("expected an EvalError");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("can only be assigned to a functional interface"));
        }
    }

    @Test
    public void block_body_return_value_is_the_result() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<Integer> c = (Callable<Integer>) new Interpreter().eval(
            "(java.util.concurrent.Callable) () -> { x = 3; return x + 4; };");
        assertEquals(Integer.valueOf(7), c.call());
    }

    // Every primitive parameter type in one SAM: long and double take two
    // local-variable slots, so a slot miscount shifts every later argument.
    @Test
    public void primitive_parameters_of_every_type_reach_the_body() throws Exception {
        assertEquals("truex1234" + "5.5" + "6.5", new Interpreter().eval(
            "interface AllPrims { String all(boolean z, char c, byte b, short s, int i,"
            + " long j, float f, double d); }\n"
            + "p = (AllPrims) (z, c, b, s, i, j, f, d) -> \"\" + z + c + b + s + i + j + f + d;\n"
            + "p.all(true, 'x', (byte) 1, (short) 2, 3, 4L, 5.5f, 6.5d);"));
    }

    @Test
    public void primitive_return_types_of_every_type_are_unboxed() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval(
            "interface RZ { boolean f(); } interface RC { char f(); }"
            + " interface RB { byte f(); } interface RS { short f(); }"
            + " interface RJ { long f(); } interface RF { float f(); }"
            + " interface RD { double f(); }");
        assertEquals(Boolean.TRUE, Primitive.unwrap(interpreter.eval("((RZ) () -> true).f();")));
        assertEquals(Character.valueOf('q'), Primitive.unwrap(interpreter.eval("((RC) () -> 'q').f();")));
        assertEquals(Byte.valueOf((byte) 7), Primitive.unwrap(interpreter.eval("((RB) () -> (byte) 7).f();")));
        assertEquals(Short.valueOf((short) 8), Primitive.unwrap(interpreter.eval("((RS) () -> (short) 8).f();")));
        assertEquals(Long.valueOf(9L), Primitive.unwrap(interpreter.eval("((RJ) () -> 9L).f();")));
        assertEquals(Float.valueOf(1.5f), Primitive.unwrap(interpreter.eval("((RF) () -> 1.5f).f();")));
        assertEquals(Double.valueOf(2.5d), Primitive.unwrap(interpreter.eval("((RD) () -> 2.5d).f();")));
    }

    @Test
    public void script_error_in_the_body_reaches_a_java_caller_unchecked() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval("(Runnable) () -> { noSuchMethod(); };");
        try {
            r.run();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("noSuchMethod"));
        }
    }

    @Test
    public void runtime_eval_error_exposes_the_script_error() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval("(Runnable) () -> {\n noSuchMethod(); };");
        try {
            r.run();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            EvalError error = expected.getEvalError();
            assertEquals(expected.getMessage(), error.getMessage());
            assertTrue(String.valueOf(error.getErrorLineNumber()), error.getErrorLineNumber() > 0);
        }
    }

    @Test
    public void uncoercible_return_value_reaches_a_java_caller_unchecked() throws Exception {
        java.util.function.IntSupplier s = (java.util.function.IntSupplier)
            new Interpreter().eval("(java.util.function.IntSupplier) () -> new Object();");
        try {
            s.getAsInt();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("int"));
        }
    }

    // A wrapper implementing a scripted interface is GeneratedClass-assignable
    // but is not itself a script-generated class, so it has no namespace fields.
    @Test
    public void lambda_for_a_scripted_interface_is_invocable() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("interface Doubler { int apply(int x); }");
        assertEquals(Integer.valueOf(42),
            Primitive.unwrap(interpreter.eval("d = (Doubler) x -> x * 2; d.apply(21);")));
        assertEquals(Integer.valueOf(42),
            Primitive.unwrap(interpreter.eval("Doubler t = x -> x * 2; t.apply(21);")));
        assertEquals(Integer.valueOf(42),
            Primitive.unwrap(interpreter.eval(
                "use(Doubler d) { return d.apply(21); } use((Doubler) x -> x * 2);")));
    }

    // Defining a class through a class manager races other interpreters doing
    // the same; lambdas for JDK interfaces must not need to.
    @Test
    public void lambdas_in_concurrent_interpreters_all_materialize() throws Exception {
        int threads = 8, perThread = 100;
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Throwable> failures =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        new Interpreter().eval("Runnable r = () -> {}; r.run();"
                            + " java.util.function.Function f = x -> x; f.apply(1);");
                        ok.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            workers[t].start();
        }
        start.countDown();
        for (Thread w : workers)
            w.join();
        assertTrue("first failure: " + (failures.isEmpty() ? "" : failures.get(0)), failures.isEmpty());
        assertEquals(threads * perThread, ok.get());
    }

    // An interpreter's class manager is collectable once dropped, with or
    // without scripted classes; materializing a lambda must not change that.
    @Test
    public void materializing_a_lambda_does_not_pin_the_class_manager() throws Exception {
        assertCollected(dropInterpreterAfter("((Runnable) () -> {}).run();"));
    }

    private static WeakReference<BshClassManager> dropInterpreterAfter(String script)
            throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval(script);
        return new WeakReference<>(interpreter.getClassManager());
    }

    // ClassManagerImpl.reloadClasses parks the last class loader it made in a
    // static (DiscreteFilesClassLoader.instance), pinning whichever manager
    // defined a class most recently -- pre-existing, lambda-independent.
    // Defining a class elsewhere moves that static on before we check.
    private static void assertCollected(WeakReference<?> ref) throws Exception {
        new Interpreter().eval("class DisplaceLastClassLoader {} new DisplaceLastClassLoader();");
        for (int n = 0; n < 50 && ref.get() != null; n++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull("class manager still reachable after its interpreter was dropped", ref.get());
    }

    @Test
    public void direct_cast_rejects_a_value_body_for_a_void_interface() throws Exception {
        try {
            new Interpreter().eval("Runnable r = () -> 1; r.run();");
            fail("expected an EvalError: Runnable's SAM returns void, the body returns a value");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Runnable"));
        }
    }

    @Test
    public void direct_cast_rejects_a_void_body_for_a_value_interface() throws Exception {
        try {
            new Interpreter().eval(
                "(java.util.concurrent.Callable) () -> { x = 1; };");
            fail("expected an EvalError: a block that completes normally is void-shaped");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Callable"));
        }
    }

    @Test
    public void direct_cast_rejects_a_known_result_that_cannot_fit_the_return_type() throws Exception {
        try {
            new Interpreter().eval("java.util.function.IntSupplier s = () -> \"x\";");
            fail("expected an EvalError: String cannot become int");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("IntSupplier"));
        }
    }

    @Test
    public void a_null_result_from_an_unknown_body_reaches_a_java_caller_as_runtime_eval_error() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("nullGiver() { return null; }");
        java.util.function.IntSupplier s = (java.util.function.IntSupplier)
            interpreter.eval("(java.util.function.IntSupplier) () -> nullGiver();");
        try {
            s.getAsInt();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("int"));
        }
    }

    @Test
    public void an_out_of_range_result_from_an_unknown_body_reaches_a_java_caller_as_runtime_eval_error() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("bigGiver() { return 5000000000L; }");
        java.util.function.IntSupplier s = (java.util.function.IntSupplier)
            interpreter.eval("(java.util.function.IntSupplier) () -> bigGiver();");
        try {
            s.getAsInt();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("int"));
        }
    }
}
