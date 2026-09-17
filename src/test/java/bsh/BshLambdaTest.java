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

    public interface GenericGetter<T> { T get(); }
    public interface StringGetter extends GenericGetter<String> {}
    public interface IntegerGetter extends GenericGetter<Integer> {}
    public static class GetterOverloads {
        public static String take(IntegerGetter g) { return "integer:" + g.get(); }
        public static String take(StringGetter g) { return "string:" + g.get(); }
    }

    // JLS 9.9: StringGetter's function type returns String.
    @Test
    public void a_specialized_generic_return_type_rejects_an_unfit_constant_at_conversion() throws Exception {
        try {
            new Interpreter().eval("import bsh.BshLambdaTest.StringGetter; StringGetter g = () -> 1;");
            fail("expected an EvalError: 1 is not a String");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("StringGetter"));
        }
        assertEquals("x", ((StringGetter) new Interpreter().eval(
            "import bsh.BshLambdaTest.StringGetter; (StringGetter) () -> \"x\";")).get());
    }

    // An unknown result that turns out wrong at run time fails inside invoke, not as a
    // ClassCastException at the Java call site.
    @Test
    public void a_specialized_generic_return_type_is_checked_when_the_body_runs() throws Exception {
        StringGetter g = (StringGetter) new Interpreter().eval(
            "import bsh.BshLambdaTest.StringGetter; foo() { return 1; } (StringGetter) () -> foo();");
        try {
            g.get();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("String"));
        }
    }

    public interface MultiLevelBase<T> { T get(); }
    public interface MultiLevelMid<U> extends MultiLevelBase<U> {}
    public interface MultiLevelStringGetter extends MultiLevelMid<String> {}

    // LAMBDA-REVIEW2.md F3: MultiLevelStringGetter is a plain LINEAR two-level
    // chain (no diamond) -- unlike the already-pinned
    // a_diamond_with_a_second_level_generic_ancestor_is_not_yet_supported case
    // in BshLambdaClassLoadingTest, which fails safely CLOSED (SAM discovery
    // itself refuses two branches that don't converge). Here there is only one
    // branch, so SAM discovery succeeds fine, but specializedReturnType's
    // single-level-only substitution can't resolve T two levels up and
    // silently falls back to the erasure Object -- so () -> 1 (an int,
    // nowhere near a String) was silently accepted instead of rejected.
    @Test
    public void a_multi_level_unresolved_return_type_rejects_a_known_incompatible_constant() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.MultiLevelStringGetter; MultiLevelStringGetter g = () -> 1;");
            fail("expected an EvalError: an int constant cannot be a MultiLevelStringGetter's result");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("MultiLevelStringGetter"));
        }
        // An unknown result is unaffected: still deferred to invocation time (existing, accepted behavior).
        // Because specializedReturnType can't resolve two levels, it falls back to Object.
        // When invoked with a narrowing assignment to String (as in the existing diamond test),
        // this exposes a pre-existing gap: it throws raw ClassCastException, not RuntimeEvalError.
        // That mismatch is a separate issue, not this task's to fix.
        Object g = new Interpreter().eval(
            "import bsh.BshLambdaTest.MultiLevelStringGetter;"
            + " foo() { return 1; } (MultiLevelStringGetter) () -> foo();");
        try {
            // Force a narrowing via typed-local, matching the pattern from
            // a_return_type_shared_by_two_generic_superinterfaces_does_not_depend_on_declaration_order
            String s = ((MultiLevelStringGetter) g).get();
            fail("MultiLevelStringGetter unknown result: expected ClassCastException when narrowed; got " + s);
        } catch (ClassCastException expected) {
            // Pre-existing behavior: raw checkcast failure, not a RuntimeEvalError at the wrapper's conversion.
            // This is the actual current behavior for unknown results with unresolved multi-level returns.
        }
    }

    @Test
    public void overloads_differing_only_in_a_specialized_return_type_resolve_as_javac_does() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("import bsh.BshLambdaTest.GetterOverloads;");
        assertEquals("string:s", interpreter.eval("GetterOverloads.take(() -> \"s\");"));
        assertEquals("integer:1", interpreter.eval("GetterOverloads.take(() -> 1);"));
    }

    // Two unrelated generic superinterfaces erasing the same SAM to Object, with
    // different (subtype-related) type arguments: javac resolves both declaration
    // orders to the narrower String, not whichever branch getMethods() happens
    // to visit first.
    public interface DiamondGetterA<T> { T get(); }
    public interface DiamondGetterB<T> { T get(); }
    public interface DiamondFirstA extends DiamondGetterA<String>, DiamondGetterB<CharSequence> {}
    public interface DiamondFirstB extends DiamondGetterB<CharSequence>, DiamondGetterA<String> {}

    // A diamond where one branch resolves in a single level and another branch
    // requires two levels (unresolvable by bsh's deliberately single-level-only substitution).
    // This tests that the ANY->ALL logic correctly accepts lambdas compatible with
    // the resolving branch (not over-rejecting due to another unresolvable branch).
    public interface PartialResolvingA<T> { T get(); }
    public interface PartialResolvingBase<U> { U get(); }
    public interface PartialResolvingMid<V> extends PartialResolvingBase<V> {}
    public interface PartialDiamondResolution extends PartialResolvingA<String>, PartialResolvingMid<String> {}

    // A known-String result works for both orders (this alone would also pass
    // under the order-dependent bug: whichever branch getMethods() visits
    // first, its erasure is Object, and a String result fits Object too).
    // The discriminating case is a runtime StringBuffer result: only the
    // buggy order ever names CharSequence in the wrapper, under which a
    // StringBuffer coerces successfully and reaches the caller's own,
    // real-Java, javac-inserted checkcast to String -- a raw
    // ClassCastException, not the RuntimeEvalError the wrapper's own
    // (correctly specialized) checkcast is supposed to raise instead.
    @Test
    public void a_return_type_shared_by_two_generic_superinterfaces_does_not_depend_on_declaration_order() throws Exception {
        assertEquals("x", ((DiamondFirstA) new Interpreter().eval(
            "import bsh.BshLambdaTest.DiamondFirstA; (DiamondFirstA) () -> \"x\";")).get());
        assertEquals("x", ((DiamondFirstB) new Interpreter().eval(
            "import bsh.BshLambdaTest.DiamondFirstB; (DiamondFirstB) () -> \"x\";")).get());

        DiamondFirstA a = (DiamondFirstA) new Interpreter().eval(
            "import bsh.BshLambdaTest.DiamondFirstA;"
            + " foo() { return new StringBuffer(\"x\"); } (DiamondFirstA) () -> foo();");
        try {
            String s = a.get();
            fail("DiamondFirstA: expected a RuntimeEvalError, StringBuffer is not a String; got " + s);
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("String"));
        }

        DiamondFirstB b = (DiamondFirstB) new Interpreter().eval(
            "import bsh.BshLambdaTest.DiamondFirstB;"
            + " foo() { return new StringBuffer(\"x\"); } (DiamondFirstB) () -> foo();");
        try {
            String s = b.get();
            fail("DiamondFirstB: expected a RuntimeEvalError, StringBuffer is not a String; got " + s);
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("String"));
        }
    }

    // A partial-resolution diamond: one branch (PartialResolvingA<String>) resolves
    // its return type in a single level, while another branch (PartialResolvingMid<String>)
    // would require two levels to resolve back to PartialResolvingBase. Tests that
    // the ANY->ALL gating logic correctly accepts lambdas with statically-known results
    // compatible with the resolving branch (not over-rejected due to the unresolvable branch).
    @Test
    public void a_partial_resolution_diamond_accepts_a_compatible_result() throws Exception {
        // Lambda result is String, compatible with PartialResolvingA<String> (one-level-resolvable branch)
        assertEquals("x", ((PartialDiamondResolution) new Interpreter().eval(
            "import bsh.BshLambdaTest.PartialDiamondResolution; (PartialDiamondResolution) () -> \"x\";")).get());
    }

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
    public interface IOAction { void run() throws java.io.IOException; }
    // A's branch declares IOException, B's declares nothing: javac's effective
    // throws clause for a call through AB is their intersection (nothing).
    public interface MixedThrowsAB extends IOAction, RunA {}

    public interface ExceptionAction { void run() throws Exception; }
    public interface ExceptionAndIO extends ExceptionAction, IOAction {}
    public interface FileNotFoundAction { void run() throws java.io.FileNotFoundException; }
    public interface IOAndFileNotFound extends IOAction, FileNotFoundAction {}
    public interface IOOrInterrupted { void run() throws java.io.IOException, InterruptedException; }
    public interface IOAndIOOrInterrupted extends IOAction, IOOrInterrupted {}
    // A single, non-diamond interface: no cross-branch conflict to resolve,
    // so javac copies this throws clause verbatim, subtype pair and all.
    public interface IOOrFileNotFoundAction { void run() throws java.io.IOException, java.io.FileNotFoundException; }

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

    public interface ListSink { void take(java.util.List<String> l); }

    // A parameter-type mismatch must be reported as such, not blamed on the
    // body's void/value shape or result type (which fit fine here).
    @Test
    public void a_parameter_type_mismatch_is_reported_distinctly_from_a_body_shape_mismatch() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.ListSink;"
                + " (ListSink) (java.util.ArrayList l) -> { };");
            fail("expected an EvalError: ArrayList does not match List<String>");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("parameter types don't match"));
            assertFalse(expected.getMessage(), expected.getMessage().contains("void/value shape"));
        }
    }

    public interface StringConsumer extends java.util.function.Consumer<String> {}

    // LAMBDA-REVIEW2.md F2: javac requires an explicit lambda parameter to
    // match the TARGET's resolved type (String, here), not the SAM's erased
    // declaration (Object). parametersFit's leniency for a type-variable-
    // mentioning parameter existed for the genuinely-unresolvable case (a raw
    // ancestor, a multi-level chain); StringConsumer's single-level
    // substitution is resolvable and was simply not being consulted.
    @Test
    public void an_explicit_parameter_must_match_the_targets_resolved_type_not_its_erasure() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.StringConsumer; StringConsumer c = (Integer x) -> {};");
            fail("expected an EvalError: StringConsumer resolves accept(T) to accept(String)");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("StringConsumer"));
        }
        // The matching case must keep working.
        StringConsumer c = (StringConsumer) new Interpreter().eval(
            "import bsh.BshLambdaTest.StringConsumer; (StringConsumer) (String x) -> {};");
        c.accept("ok"); // must not throw
    }

    public interface ObjSink extends java.util.function.Consumer<Object> {}

    // An unambiguous single-path specialized interface (no diamond): the parameter
    // is resolved to Object (from Consumer<Object>). Even though the resolved type
    // happens to equal the erasure, this is unambiguous, so javac (and now bsh)
    // requires exact match, rejecting Integer when Object is required. The diamond
    // case (an erasure-equal diamond with distinct declaring interfaces) stays lenient.
    @Test
    public void an_unambiguous_specialized_interface_requires_exact_match_even_if_resolved_equals_erasure() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.ObjSink; ObjSink c = (Integer x) -> {};");
            fail("expected an EvalError: ObjSink resolves accept(T) to accept(Object)");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ObjSink"));
        }
        // The matching case must keep working.
        ObjSink c = (ObjSink) new Interpreter().eval(
            "import bsh.BshLambdaTest.ObjSink; (ObjSink) (Object x) -> {};");
        c.accept("ok"); // must not throw
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

    // Documented, pre-existing divergence (CHANGES.md): bsh gives a loop no fresh
    // variable slot per iteration, so a lambda capturing it by reference sees
    // whatever the last iteration left there, not the value at its own iteration
    // the way Java does. Not lambda-specific -- a bsh.This proxy and an anonymous
    // inner class capturing the same loop variable reproduce this identical "last
    // value" result on this same tree (see .agent-notes bug log entry 29). This
    // pins the current, documented behavior; it is not the correct Java answer.
    @Test
    public void a_foreach_loops_variable_is_shared_by_every_lambda_that_captures_it() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("l = new java.util.ArrayList();"
            + " for (String s : new String[]{\"a\",\"b\",\"c\"})"
            + " l.add((java.util.function.Supplier) (() -> s));");
        @SuppressWarnings("unchecked")
        java.util.List<java.util.function.Supplier<String>> l =
            (java.util.List<java.util.function.Supplier<String>>) interpreter.get("l");
        assertEquals(3, l.size());
        for (java.util.function.Supplier<String> s : l)
            assertEquals("c", s.get());
    }

    // Same divergence, a C-style loop's block-local instead of a for-each variable.
    @Test
    public void a_c_style_loops_block_local_is_shared_by_every_lambda_that_captures_it() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("l2 = new java.util.ArrayList();"
            + " for (i = 0; i < 3; i++) { String t = \"x\" + i;"
            + " l2.add((java.util.function.Supplier) (() -> t)); }");
        @SuppressWarnings("unchecked")
        java.util.List<java.util.function.Supplier<String>> l2 =
            (java.util.List<java.util.function.Supplier<String>>) interpreter.get("l2");
        assertEquals(3, l2.size());
        for (java.util.function.Supplier<String> s : l2)
            assertEquals("x2", s.get());
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

    public interface ObjectReplacer { Object writeReplace(); }
    public interface StringReplacer { String writeReplace(); }
    public interface CovariantSerializableReplacer extends java.io.Serializable, ObjectReplacer, StringReplacer {}

    // Class.getDeclaredMethod("writeReplace") would return the String declaration,
    // hiding the surrogate hook: the whole abstract family must be checked.
    @Test
    public void a_covariant_write_replace_family_on_a_serializable_target_is_rejected() throws Exception {
        try {
            new Interpreter().eval("import bsh.BshLambdaTest.CovariantSerializableReplacer;"
                + " (CovariantSerializableReplacer) () -> \"w\";");
            fail("expected an EvalError naming writeReplace");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("writeReplace"));
        }
    }

    // readResolve's catch (BshLambda.convertTo failing during deserialization),
    // reached via a SecurityGuard installed between serialize and deserialize
    // rather than a SAM-arity change (see BshLambdaClassLoadingTest for that trigger).
    @Test
    public void a_security_guard_installed_before_deserialization_rejects_the_lambda() throws Exception {
        SerialTriple triple = (SerialTriple) new Interpreter().eval(
            "import bsh.BshLambdaTest.SerialTriple; (SerialTriple) x -> x;");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        new java.io.ObjectOutputStream(bytes).writeObject(triple);
        bsh.security.SecurityGuard noSerialTriple = new bsh.security.SecurityGuard() {
            public boolean canImplements(Class<?> iface) { return iface != SerialTriple.class; }
        };
        Interpreter.mainSecurityGuard.add(noSerialTriple);
        try {
            try {
                new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())).readObject();
                fail("expected an InvalidObjectException: the guard now vetoes SerialTriple");
            } catch (java.io.InvalidObjectException expected) {
                assertTrue(String.valueOf(expected.getCause()), expected.getCause() instanceof UtilEvalError);
            }
        } finally {
            Interpreter.mainSecurityGuard.remove(noSerialTriple);
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

    @Test
    public void a_declared_checked_exception_reaches_a_java_caller_as_itself() throws Exception {
        IOAction action = (IOAction) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOAction;"
            + " (IOAction) () -> { throw new java.io.IOException(\"failure\"); };");
        try {
            action.run();
            fail("expected an IOException");
        } catch (java.io.IOException expected) {
            assertEquals("failure", expected.getMessage());
        }
    }

    @Test
    public void an_unchecked_exception_from_the_body_reaches_a_java_caller_as_itself() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval(
            "(Runnable) () -> { throw new IllegalStateException(\"boom\"); };");
        try {
            r.run();
            fail("expected the IllegalStateException itself, not a RuntimeEvalError");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    @Test
    public void an_error_from_the_body_reaches_a_java_caller_as_itself() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval(
            "(Runnable) () -> { throw new LinkageError(\"link\"); };");
        try {
            r.run();
            fail("expected the LinkageError itself");
        } catch (LinkageError expected) {
            assertEquals("link", expected.getMessage());
        }
    }

    // The SAM declares IOException only; an unchecked exception is still exempt from that clause.
    @Test
    public void an_unchecked_exception_is_not_gated_by_the_declared_checked_clause() throws Exception {
        IOAction action = (IOAction) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOAction;"
            + " (IOAction) () -> { throw new IllegalArgumentException(\"arg\"); };");
        try {
            action.run();
            fail("expected the IllegalArgumentException itself");
        } catch (IllegalArgumentException expected) {
            assertEquals("arg", expected.getMessage());
        }
    }

    @Test
    public void a_script_can_catch_an_unchecked_exception_from_a_lambda_by_type() throws Exception {
        assertEquals("caught", new Interpreter().eval(
            "Runnable r = () -> { throw new IllegalStateException(\"ise\"); };"
            + " result = \"missed\";"
            + " try { r.run(); } catch (IllegalStateException e) { result = \"caught\"; }"
            + " result;"));
    }

    // An inner lambda's unchecked exception crosses a Java method and the outer wrapper unchanged.
    @Test
    public void an_unchecked_exception_from_a_nested_lambda_reaches_a_java_caller_as_itself() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval(
            "(Runnable) () -> { java.util.Arrays.asList(1).forEach(x -> { throw new IllegalStateException(\"inner\"); }); };");
        try {
            r.run();
            fail("expected the IllegalStateException itself");
        } catch (IllegalStateException expected) {
            assertEquals("inner", expected.getMessage());
        }
    }

    // Bug log 26 fixed on lambda-dev 2026-09-15: an out-of-bounds array store is now
    // a TargetError, so it is an unchecked exception like any other by the time
    // invoke sees it.
    @Test
    public void an_out_of_bounds_array_store_reaches_a_java_caller_as_itself() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval(
            "(Runnable) () -> { int[] a = new int[0]; a[1] = 2; };");
        try {
            r.run();
            fail("expected the ArrayIndexOutOfBoundsException itself");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void an_undeclared_checked_style_failure_is_still_wrapped() throws Exception {
        Runnable r = (Runnable) new Interpreter().eval(
            "(Runnable) () -> { throw new java.io.IOException(\"undeclared\"); };");
        try {
            r.run();
            fail("expected a RuntimeEvalError: Runnable declares no checked exception");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("failure") || expected.getMessage().contains("undeclared"));
        }
    }

    // The wrapper lives in package bsh and cannot name a non-public exception
    // type, so it must fall back to wrapping instead of an IllegalAccessError.
    @Test
    public void a_non_public_declared_exception_type_is_still_wrapped() throws Exception {
        mypackage.ThrowsHidden action = (mypackage.ThrowsHidden) new Interpreter().eval(
            "import mypackage.ThrowsHidden;"
            + " (ThrowsHidden) () -> { throw new mypackage.HiddenCheckedException(\"hidden\"); };");
        try {
            action.run();
            fail("expected a RuntimeEvalError: HiddenCheckedException is not public");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("hidden"));
        }
    }

    // AB's effective throws clause is the intersection of A's and B's (javac
    // rejects catch (IOException) around a call through AB), so a lambda body
    // that throws IOException must still be wrapped, not escape raw.
    @Test
    public void a_checked_exception_declared_by_only_one_inherited_branch_is_still_wrapped() throws Exception {
        MixedThrowsAB action = (MixedThrowsAB) new Interpreter().eval(
            "import bsh.BshLambdaTest.MixedThrowsAB;"
            + " (MixedThrowsAB) () -> { throw new java.io.IOException(\"leak\"); };");
        try {
            action.run();
            fail("expected a RuntimeEvalError: IOException is not in every inherited branch's throws clause");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("leak"));
        }
    }

    // The wrapper method really can throw IOAction's declared IOException, so
    // its own Exceptions attribute must say so, not report an empty list.
    @Test
    public void wrapper_method_reports_its_declared_checked_exception_via_reflection() throws Exception {
        IOAction action = (IOAction) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOAction;"
            + " (IOAction) () -> { throw new java.io.IOException(\"failure\"); };");
        Class<?>[] declared = action.getClass().getMethod("run").getExceptionTypes();
        assertEquals(1, declared.length);
        assertEquals(java.io.IOException.class, declared[0]);
    }

    // JLS 9.4.1.3: IOException is under Exception (E's branch) and is I's own clause.
    @Test
    public void effective_throws_clause_keeps_a_subtype_declared_by_every_branch() throws Exception {
        ExceptionAndIO action = (ExceptionAndIO) new Interpreter().eval(
            "import bsh.BshLambdaTest.ExceptionAndIO;"
            + " (ExceptionAndIO) () -> { throw new java.io.IOException(\"io\"); };");
        assertEquals(java.util.Arrays.asList(java.io.IOException.class),
            java.util.Arrays.asList(action.getClass().getMethod("run").getExceptionTypes()));
        try {
            action.run();
            fail("expected the IOException itself");
        } catch (java.io.IOException expected) {
            assertEquals("io", expected.getMessage());
        }
    }

    @Test
    public void effective_throws_clause_is_the_narrower_of_two_related_declarations() throws Exception {
        IOAndFileNotFound action = (IOAndFileNotFound) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOAndFileNotFound;"
            + " (IOAndFileNotFound) () -> { throw new java.io.FileNotFoundException(\"fnf\"); };");
        assertEquals(java.util.Arrays.asList(java.io.FileNotFoundException.class),
            java.util.Arrays.asList(action.getClass().getMethod("run").getExceptionTypes()));
        try {
            action.run();
            fail("expected the FileNotFoundException itself");
        } catch (java.io.FileNotFoundException expected) {
            assertEquals("fnf", expected.getMessage());
        }
    }

    // InterruptedException is declared by one branch only, so it is not in the clause.
    @Test
    public void effective_throws_clause_drops_a_type_missing_from_one_branch() throws Exception {
        IOAndIOOrInterrupted action = (IOAndIOOrInterrupted) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOAndIOOrInterrupted;"
            + " (IOAndIOOrInterrupted) () -> { throw new InterruptedException(\"int\"); };");
        assertEquals(java.util.Arrays.asList(java.io.IOException.class),
            java.util.Arrays.asList(action.getClass().getMethod("run").getExceptionTypes()));
        try {
            action.run();
            fail("expected a RuntimeEvalError");
        } catch (RuntimeEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("int"));
        }
    }

    // A lone interface (group.size() == 1) has no second branch to intersect
    // against, so both declared types must survive, not just one of the pair.
    @Test
    public void a_lone_branchs_own_throws_clause_keeps_both_related_types() throws Exception {
        IOOrFileNotFoundAction action = (IOOrFileNotFoundAction) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOOrFileNotFoundAction;"
            + " (IOOrFileNotFoundAction) () -> { throw new java.io.IOException(\"io\"); };");
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                java.io.IOException.class, java.io.FileNotFoundException.class)),
            new java.util.HashSet<>(java.util.Arrays.asList(
                action.getClass().getMethod("run").getExceptionTypes())));
        try {
            action.run();
            fail("expected the IOException itself");
        } catch (java.io.IOException expected) {
            assertEquals("io", expected.getMessage());
        }
    }

    @Test
    public void a_lone_branchs_declared_subtype_exception_reaches_the_caller_too() throws Exception {
        IOOrFileNotFoundAction action = (IOOrFileNotFoundAction) new Interpreter().eval(
            "import bsh.BshLambdaTest.IOOrFileNotFoundAction;"
            + " (IOOrFileNotFoundAction) () -> { throw new java.io.FileNotFoundException(\"fnf\"); };");
        try {
            action.run();
            fail("expected the FileNotFoundException itself");
        } catch (java.io.FileNotFoundException expected) {
            assertEquals("fnf", expected.getMessage());
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
    public void a_value_return_that_can_also_complete_normally_is_rejected_for_a_supplier() throws Exception {
        try {
            new Interpreter().eval(
                "flag = false; java.util.function.Supplier s = () -> { if (flag) return 1; };");
            fail("expected an EvalError: the block can complete normally, so it is not value-compatible");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Supplier"));
        }
    }

    @Test
    public void a_block_with_a_valued_return_that_can_fall_off_the_end_is_rejected_for_a_void_interface() throws Exception {
        try {
            new Interpreter().eval("flag = false; Runnable r = () -> { if (flag) return 1; };");
            fail("expected an EvalError: neither void- nor value-compatible");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("valued return"));
        }
    }

    @Test
    public void a_block_mixing_valued_and_bare_returns_is_rejected_by_cast_and_by_a_java_call() throws Exception {
        Interpreter interpreter = new Interpreter();
        for (String script : new String[] {
                "(Runnable) () -> { if (flag) return 1; return; };",
                "new Thread(() -> { if (flag) return 1; return; });" }) {
            try {
                interpreter.eval("flag = true; " + script);
                fail("expected an EvalError for: " + script);
            } catch (EvalError expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Runnable")
                    || expected.getMessage().contains("Thread"));
            }
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

    @Test
    public void getCause_and_getEvalError_agree_for_the_message_only_constructor() throws Exception {
        RuntimeEvalError e = new RuntimeEvalError("msg", null, null);
        assertEquals(e.getEvalError(), e.getCause());
    }

    @Test
    public void getCause_and_getEvalError_agree_for_the_message_and_cause_constructor() throws Exception {
        Exception cause = new Exception("boom");
        RuntimeEvalError e = new RuntimeEvalError("msg", null, null, cause);
        assertEquals(e.getEvalError(), e.getCause());
        assertEquals(cause, e.getEvalError().getCause());
    }

    @Test
    public void runtime_eval_error_declares_a_serial_version_uid() throws Exception {
        java.io.ObjectStreamClass descriptor = java.io.ObjectStreamClass.lookup(RuntimeEvalError.class);
        assertEquals(1L, descriptor.getSerialVersionUID());
    }

    public interface GenericMethodSam { <T> T id(T t); }

    // JLS 15.27.3: a lambda cannot implement a generic method.
    @Test
    public void a_generic_method_sam_is_rejected_with_a_message_that_says_so() throws Exception {
        try {
            new Interpreter().eval("import bsh.BshLambdaTest.GenericMethodSam; (GenericMethodSam) x -> x;");
            fail("expected an EvalError naming the generic method");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("generic"));
        }
    }

    @Test
    public void a_lambda_argument_to_super_in_a_scripted_constructor_is_converted() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "class Base { Runnable r; Base(Runnable r) { this.r = r; } }"
            + " class Sub extends Base { Sub() { super(() -> hit = true); } }"
            + " hit = false; new Sub().r.run(); hit;"));
    }

    @Test
    public void a_lambda_argument_to_a_java_super_constructor_is_converted() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "class T extends Thread { T() { super(() -> hit = true); } }"
            + " hit = false; t = new T(); t.run(); hit;"));
    }

    @Test
    public void a_lambda_argument_to_this_in_a_scripted_constructor_is_converted() throws Exception {
        assertEquals(7, Primitive.unwrap(new Interpreter().eval(
            "class C { java.util.concurrent.Callable c; C() { this(() -> 7); }"
            + " C(java.util.concurrent.Callable c) { this.c = c; } }"
            + " new C().c.call();")));
    }

    // Pre-existing: a `This` reference needs the same conversion where an interface is
    // expected. Thread(Runnable) would pass either way since Thread already implements
    // Runnable; FutureTask(Callable) does not, so it isolates the conversion.
    @Test
    public void a_this_argument_to_a_java_super_constructor_is_converted_to_the_interface() throws Exception {
        assertEquals(7, Primitive.unwrap(new Interpreter().eval(
            "Object call() { return 7; } outer = this;"
            + " class T extends java.util.concurrent.FutureTask { T() { super(outer); } }"
            + " t = new T(); t.run(); t.get();")));
    }

    // A `This` argument to an untyped ("loose") constructor parameter must stay opaque --
    // the generated constructor holds it as an untyped local, exactly as before lambdas
    // existed. paramTypes[k] is null for such a parameter.
    @Test
    public void a_this_argument_to_an_untyped_constructor_param_is_left_opaque() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "run() {} outer = this;"
            + " class Widget { Object handler; Widget(h) { this.handler = h; }"
            + " Widget() { this(outer); } }"
            + " new Widget().handler == outer;"));
    }

    // Same as above for a lambda argument -- this side already worked (Types.castObject
    // short-circuits a null target type before reaching BshLambda.castLambda), pinned here
    // alongside the This case above so both halves of the untyped-parameter path are covered.
    @Test
    public void a_lambda_argument_to_an_untyped_constructor_param_is_left_opaque() throws Exception {
        assertEquals(Boolean.TRUE, new Interpreter().eval(
            "class Holder { Object cb; Holder(c) { this.cb = c; }"
            + " Holder() { this(x -> x); } }"
            + " new Holder().cb != null;"));
    }

    @Test
    public void a_scripted_interface_constant_is_readable_through_a_lambda_wrapper() throws Exception {
        assertEquals("42,42,42", new Interpreter().eval(
            "interface K { int C = 42; int get(); } interface K2 extends K { }"
            + " K k = () -> 1; K2 k2 = () -> 2;"
            + " K.C + \",\" + k.C + \",\" + k2.C;"));
    }

    // A compiled interface's constant already works through the wrapper via getField.
    public interface JavaConstants { int X = 7; int get(); }

    @Test
    public void a_java_interface_constant_is_readable_through_a_lambda_wrapper() throws Exception {
        assertEquals(7, Primitive.unwrap(new Interpreter().eval(
            "import bsh.BshLambdaTest.JavaConstants; JavaConstants j = () -> 1; j.X;")));
    }

    @Test
    public void a_security_guard_can_veto_the_interface_a_lambda_implements() throws Exception {
        bsh.security.SecurityGuard noRunnables = new bsh.security.SecurityGuard() {
            public boolean canImplements(Class<?> iface) { return iface != Runnable.class; }
        };
        Interpreter.mainSecurityGuard.add(noRunnables);
        try {
            Interpreter interpreter = new Interpreter();
            assertEquals(1, Primitive.unwrap(interpreter.eval("java.util.concurrent.Callable c = () -> 1; c.call();")));
            try {
                interpreter.eval("Runnable r = () -> {};");
                fail("expected a SecurityError");
            } catch (EvalError expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Can't implement this interface"));
            }
            try {
                interpreter.eval("new Thread(() -> {});");
                fail("expected a SecurityError from the overload-resolved conversion");
            } catch (EvalError expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Can't implement this interface"));
            }
        } finally {
            Interpreter.mainSecurityGuard.remove(noRunnables);
        }
    }

    // Companion to the assignment/overload-resolved forms above: an explicit cast
    // must be vetoed the same way, with a different, allowed interface still
    // working under the same guard (the control case).
    @Test
    public void a_security_guard_veto_via_an_explicit_cast_matches_the_assignment_case() throws Exception {
        bsh.security.SecurityGuard noRunnables = new bsh.security.SecurityGuard() {
            public boolean canImplements(Class<?> iface) { return iface != Runnable.class; }
        };
        Interpreter.mainSecurityGuard.add(noRunnables);
        try {
            try {
                new Interpreter().eval("(Runnable) (() -> {});");
                fail("expected a SecurityError from the explicit cast");
            } catch (EvalError expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Can't implement this interface"));
            }
            assertEquals(1, Primitive.unwrap(
                new Interpreter().eval("((java.util.concurrent.Callable) (() -> 1)).call();")));
        } finally {
            Interpreter.mainSecurityGuard.remove(noRunnables);
        }
    }

    // Reflect.scriptedInterfaceConstant's recursive superinterface-constant lookup,
    // at a second level: a_scripted_interface_constant_is_readable_through_a_lambda_wrapper
    // above already covers one level (K2 extends K); this closes Task 14's remaining gap.
    @Test
    public void a_scripted_interface_constant_is_readable_through_two_levels_of_inheritance() throws Exception {
        assertEquals(41, Primitive.unwrap(new Interpreter().eval(
            "interface A1 { int K = 41; } interface B1 extends A1 { } interface C1 extends B1 { int get(); }"
            + " C1 c = () -> 1; c.K;")));
    }

    // markerTypeName must describe the lambda by its parameter types and shape,
    // never leak the internal synthetic marker class name, in an unmatched-overload error.
    @Test
    public void an_unmatched_overload_error_names_the_lambda_by_its_marker_type_name() throws Exception {
        try {
            new Interpreter().eval("m(Runnable r) { } m((String s) -> { });");
            fail("expected an EvalError: Runnable's SAM takes 0 parameters, not (String)");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("<lambda (String) returning void>"));
        }
    }

    public static class Base {
        public final SerializableReplaceWriter w;
        public Base(SerializableReplaceWriter w) { this.w = w; }
    }

    // This.convertOpaqueArgs's catch: a lambda argument to a Java super
    // constructor that fails BshLambda.convertTo (here, the SAM collides with
    // Serialization's writeReplace() hook) must be wrapped, naming the argument,
    // not escape as a bare UtilEvalError.
    @Test
    public void a_lambda_argument_to_a_java_super_constructor_that_fails_conversion_is_wrapped() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaTest.Base;"
                + " class A extends Base { A() { super(() -> null); } } new A();");
            fail("expected an error: SerializableReplaceWriter's SAM collides with writeReplace");
        } catch (EvalError | InterpreterError expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("argument 1"));
            assertTrue(message, message.contains("writeReplace"));
        }
    }

    // LAMBDA-REVIEW2.md F5: a scripted interface's default method body
    // evaluates through the interface's static namespace, expecting a
    // generated-class instance context; a lambda wrapper is deliberately
    // excluded from Reflect.isGeneratedClass (so other reflection paths don't
    // treat it as an ordinary scripted instance), so a default method calling
    // the interface's own abstract method on a lambda-implemented instance
    // resolves that call as if from a static context and fails. Conservative
    // fix (Jim's chosen scope, not a dispatch fix): reject the conversion
    // outright whenever the target is a scripted interface with ANY default
    // method, whether or not it actually calls the SAM.
    @Test
    public void a_scripted_interface_with_a_default_method_is_rejected_as_a_lambda_target() throws Exception {
        try {
            new Interpreter().eval(
                "interface D { int f(int x); default int twice(int x) { return f(x) * 2; } }"
                + " D d = x -> x + 1;");
            fail("expected an EvalError: a scripted default method cannot yet dispatch to a lambda's SAM");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("default"));
        }
        // Conservative: also rejected even when the default doesn't touch the SAM at all.
        try {
            new Interpreter().eval(
                "interface E { int f(int x); default int hello() { return 42; } }"
                + " E e = x -> x + 1;");
            fail("expected an EvalError: any scripted default rejects the target, not just a SAM-calling one");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("default"));
        }
        // A scripted interface with NO default method is unaffected.
        Object noDefault = new Interpreter().eval(
            "interface F { int f(int x); } F g = x -> x + 1; return g;");
        org.junit.Assert.assertNotNull(noDefault);
    }
}
