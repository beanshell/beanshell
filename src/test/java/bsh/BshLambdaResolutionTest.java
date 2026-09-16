package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
    Phase 3 coverage for lambdas (issue #675): cast-free target typing, where
    the functional interface is chosen by overload resolution.
*/
@RunWith(FilteredTestRunner.class)
public class BshLambdaResolutionTest {

    public static final class Overloads {
        public static String take(Object o) { return "object"; }
        public static String take(Runnable r) { return "runnable"; }
        interface Hidden { int apply(int x); }
        public static int hidden(Hidden h) { return h.apply(21); }
        public interface Getter { Object get(); }
        public interface RunningGetter extends Getter { default Object get() { return null; } void run(); }
        public interface GettingRunner extends Runnable { default void run() {} Object get(); }
        public interface StrSupplier { String get(); }
        public interface IntegerSupplier { Integer get(); }
        public interface SC { String accept(String s); }
        public interface IC { String accept(Integer i); }
        public interface Eleven { Object apply(Object a, Object b, Object c, Object d, Object e, Object f,
            Object g, Object h, Object i, Object j, Object k); }
        public static String lone(java.util.concurrent.Callable<?> c) { return "lone"; }
        public interface W extends java.util.function.IntSupplier { default int getAsInt() { return 0; } Object get(); }
        public interface AGen { <T> T get(); }
    }

    public static class LoneCallableTaker {
        public static String take(java.util.concurrent.Callable c) { return "java"; }
    }

    public static class ObjectVsVarargsRunnable {
        public static String take(Object x) { return "object"; }
        public static String take(Runnable... x) { return "varargs"; }
    }

    /** Unrelated functional interfaces, deliberately at both ends of the
        alphabet: nothing may rank them by name. */
    public interface Aaa { void a(); }
    public interface Zzz { void z(); }

    public static class FixedAaaVarargsZzz {
        public static String q(Aaa x) { return "fixed"; }
        public static String q(Zzz... x) { return "varargs"; }
    }

    public static class FixedZzzVarargsAaa {
        public static String q(Zzz x) { return "fixed"; }
        public static String q(Aaa... x) { return "varargs"; }
    }

    public static class LoneObjectTaker {
        public static String q(Object x) { return "object"; }
    }

    public static class ObjectVsVarargsObject {
        public static String q(Object x) { return "fixed"; }
        public static String q(Object... x) { return "varargs"; }
    }

    /** long takes an Integer only by unbox-then-widen, which bsh models at
        BSH_ASSIGNABLE; javac picks the fixed-arity overload here. */
    public static class WideningVsVarargs {
        public static String q(long n, Runnable r) { return "fixed"; }
        public static String q(Number n, Runnable... r) { return "varargs"; }
    }

    private static final String IMPORT_AAA_ZZZ =
        "import bsh.BshLambdaResolutionTest.Aaa;\n"
        + "import bsh.BshLambdaResolutionTest.Zzz;\n";

    private static Object eval(String script) throws EvalError {
        return Primitive.unwrap(new Interpreter().eval(script));
    }

    // Pre-existing behaviour, pinned: a scripted object is only proxied to an
    // interface when no Object overload exists, because This.class matches
    // Object in the first assignability round. Lambdas deliberately do not
    // inherit this -- see object_overload_loses_to_a_matching_functional_interface.
    @Test
    public void scripted_object_prefers_an_object_overload_over_an_interface() throws Exception {
        assertEquals("object", eval(
            "f(Object o) { return \"object\"; }\n"
            + "f(Comparator c) { return \"comparator\"; }\n"
            + "compare(a, b) { return 0; }\n"
            + "f(this);"));
    }

    @Test
    public void bare_lambda_argument_to_a_typed_scripted_parameter() throws Exception {
        assertEquals(Boolean.TRUE, eval(
            "ran = false; accept(Runnable r) { r.run(); }\n"
            + "accept(() -> { ran = true; }); ran;"));
    }

    @Test
    public void java_method_taking_a_consumer() throws Exception {
        assertEquals(Integer.valueOf(6), eval(
            "sum = 0; Arrays.asList(1, 2, 3).forEach(x -> { sum += x; }); sum;"));
    }

    @Test
    public void java_constructor_taking_a_runnable() throws Exception {
        assertEquals(Boolean.TRUE, eval(
            "ran = false; t = new Thread(() -> { ran = true; }); t.run(); ran;"));
    }

    // Stream.map is the lone "map" candidate, so MemberCache.findBest returns
    // it without signature matching; this passed before Phase 3 and guards
    // that shortcut rather than the arity marker.
    @Test
    public void stream_map_with_an_expression_lambda() throws Exception {
        assertEquals(Arrays.asList(2, 4, 6), eval(
            "Arrays.asList(1, 2, 3).stream().map(x -> x * 2)"
            + ".collect(java.util.stream.Collectors.toList());"));
    }

    @Test
    public void list_sort_with_a_two_parameter_comparator() throws Exception {
        List<?> sorted = (List<?>) eval(
            "l = new ArrayList(Arrays.asList(1, 3, 2)); l.sort((a, b) -> b - a); l;");
        assertEquals(Arrays.asList(3, 2, 1), sorted);
    }

    @Test
    public void object_overload_loses_to_a_matching_functional_interface() throws Exception {
        String objectFirst = "f(Object o) { return \"object\"; }\n"
            + "f(Runnable r) { return \"runnable\"; }\n";
        String runnableFirst = "f(Runnable r) { return \"runnable\"; }\n"
            + "f(Object o) { return \"object\"; }\n";
        assertEquals("runnable", eval(objectFirst + "f(() -> {});"));
        assertEquals("runnable", eval(runnableFirst + "f(() -> {});"));
    }

    @Test
    public void java_object_overload_loses_to_a_matching_functional_interface() throws Exception {
        assertEquals("runnable", eval(
            "import bsh.BshLambdaResolutionTest.Overloads; Overloads.take(() -> {});"));
    }

    // The lambda must not push the whole call into BSH_ASSIGNABLE, where the
    // other arguments would be matched by looser rules than Java's. Declaring
    // Integer last puts it first in the candidate list, so a loose match wins.
    @Test
    public void other_arguments_keep_java_conversion_rules() throws Exception {
        String overloads = "w(long n, Runnable r) { return \"long\"; }\n"
            + "w(Integer n, Runnable r) { return \"Integer\"; }\n";
        assertEquals("long", eval(overloads + "w(1L, () -> {});"));
        assertEquals("long", eval(overloads + "w(1, () -> {});"));
    }

    @Test
    public void non_public_functional_interface_fails_clearly() throws Exception {
        try {
            eval("import bsh.BshLambdaResolutionTest.Overloads; Overloads.hidden(x -> x * 2);");
            fail("expected an EvalError");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not public"));
        }
    }

    @Test
    public void object_only_overload_still_receives_the_raw_lambda() throws Exception {
        assertTrue(eval("keep(Object o) { return o; } keep(x -> x);") instanceof BshLambda);
        assertTrue(eval("java.util.Objects.requireNonNull(x -> x);") instanceof BshLambda);
    }

    @Test
    public void arity_selects_between_functional_interfaces() throws Exception {
        String overloads = "g(Runnable r) { return \"runnable\"; }\n"
            + "g(java.util.function.Consumer c) { return \"consumer\"; }\n";
        assertEquals("runnable", eval(overloads + "g(() -> {});"));
        assertEquals("consumer", eval(overloads + "g(x -> {});"));
    }

    @Test
    public void functional_interface_beats_an_object_varargs_overload() throws Exception {
        assertEquals("runnable", eval(
            "v(Object... os) { return \"varargs\"; }\n"
            + "v(Runnable r) { return \"runnable\"; }\n"
            + "v(() -> {});"));
    }

    @Test
    public void lambda_does_not_match_a_boolean_parameter() throws Exception {
        String boolFirst = "b(boolean x) { return \"boolean\"; }\n"
            + "b(Runnable r) { return \"runnable\"; }\n";
        assertEquals("runnable", eval(boolFirst + "b(() -> {});"));
    }

    // Java picks Callable: 42 is not a statement, so the lambda cannot be a Runnable.
    @Test
    public void executor_submit_of_a_value_lambda_is_a_callable() throws Exception {
        assertEquals(Integer.valueOf(42), eval(
            "ex = java.util.concurrent.Executors.newSingleThreadExecutor(); r = null;\n"
            + "try { r = ex.submit(() -> 42).get(); } finally { ex.shutdown(); }\n"
            + "r;"));
    }

    /** Resolves f(body) with Runnable and Callable overloads declared in both orders. */
    private static void assertBothOrdersPick(String expected, String lambda) throws Exception {
        String runnable = "f(Runnable r) { return \"runnable\"; }\n";
        String callable = "f(java.util.concurrent.Callable c) { return \"callable\"; }\n";
        String call = "foo() { return 1; } x = 0; f(" + lambda + ");";
        assertEquals(lambda, expected, eval(runnable + callable + call));
        assertEquals(lambda, expected, eval(callable + runnable + call));
    }

    @Test
    public void value_expression_body_selects_the_value_returning_interface() throws Exception {
        assertBothOrdersPick("callable", "() -> 42");
        assertBothOrdersPick("callable", "() -> (foo())");
    }

    @Test
    public void block_returning_a_value_selects_the_value_returning_interface() throws Exception {
        assertBothOrdersPick("callable", "() -> { return 1; }");
    }

    @Test
    public void block_returning_nothing_selects_the_void_interface() throws Exception {
        assertBothOrdersPick("runnable", "() -> { x = 1; }");
        assertBothOrdersPick("runnable", "() -> { return; }");
        assertBothOrdersPick("runnable", "() -> { r = () -> { return 1; }; }");
        assertBothOrdersPick("runnable", "() -> { while (true) { break; } }");
        assertBothOrdersPick("runnable", "() -> { m() { return 1; } }");
        assertBothOrdersPick("runnable", "() -> { class K { int k() { return 1; } } }");
        assertBothOrdersPick("runnable", "() -> { o = new Object() { int k() { return 1; } }; }");
    }

    @Test
    public void body_shape_ranks_a_lambda_among_other_arguments() throws Exception {
        String runnable = "h(String s, Runnable r) { return \"runnable\"; }\n";
        String callable = "h(String s, java.util.concurrent.Callable c) { return \"callable\"; }\n";
        assertEquals("callable", eval(runnable + callable + "h(\"a\", () -> 42);"));
        assertEquals("callable", eval(callable + runnable + "h(\"a\", () -> 42);"));
        assertEquals("runnable", eval(runnable + callable + "h(\"a\", () -> { x = 1; });"));
        assertEquals("runnable", eval(callable + runnable + "h(\"a\", () -> { x = 1; });"));
        String objectRunnable = "k(Object o, Runnable r) { return \"object,runnable\"; }\n";
        String stringCallable = "k(String s, java.util.concurrent.Callable c) { return \"string,callable\"; }\n";
        assertEquals("string,callable", eval(objectRunnable + stringCallable + "k(\"a\", () -> 42);"));
        assertEquals("string,callable", eval(stringCallable + objectRunnable + "k(\"a\", () -> 42);"));
    }

    // A statement expression or a block that cannot complete normally fits
    // both; Java then prefers the interface that returns a value.
    @Test
    public void body_fitting_both_prefers_the_value_returning_interface() throws Exception {
        assertBothOrdersPick("callable", "() -> foo()");
        assertBothOrdersPick("callable", "() -> x = 1");
        assertBothOrdersPick("callable", "() -> x++");
        assertBothOrdersPick("callable", "() -> ++x");
        assertBothOrdersPick("callable", "() -> x += 1");
        assertBothOrdersPick("callable", "() -> new StringBuilder().reverse().setLength(0)");
        assertBothOrdersPick("callable", "() -> new Object()");
        assertBothOrdersPick("callable", "() -> { throw new RuntimeException(); }");
        assertBothOrdersPick("callable", "() -> { while (true) {} }");
        assertBothOrdersPick("callable", "() -> { for (;;) {} }");
        assertBothOrdersPick("callable", "() -> { for (; true;) {} }");
        assertBothOrdersPick("callable", "() -> { while (true) { if (x == 0) continue; } }");
        assertBothOrdersPick("callable", "() -> { do {} while (true); }");
        assertBothOrdersPick("callable",
            "() -> { if (x == 0) throw new RuntimeException(); else throw new Error(); }");
        assertBothOrdersPick("callable", "() -> { synchronized (this) { throw new RuntimeException(); } }");
        assertBothOrdersPick("callable", "() -> { { throw new RuntimeException(); } }");
        assertBothOrdersPick("callable", "() -> { try { throw new RuntimeException(); } finally {} }");
        assertBothOrdersPick("callable",
            "() -> { try { throw new RuntimeException(); } catch (Exception e) { throw new Error(); } }");
        assertBothOrdersPick("callable", "() -> { try {} finally { throw new Error(); } }");
        assertBothOrdersPick("callable", "() -> { out: while (true) {} }");
        assertBothOrdersPick("callable", "() -> { while ((true)) {} }");
        assertBothOrdersPick("callable", "() -> { for (int i = 0; true; i++) {} }");
        assertBothOrdersPick("callable",
            "() -> { try (java.io.StringReader r = new java.io.StringReader(\"\")) { x = 1; } finally { throw new Error(); } }");
        assertBothOrdersPick("callable", "() -> { switch (x) { case 1: default: throw new Error(); } }");
    }

    // javac: the outer loop cannot complete normally, so the block fits Callable and Runnable;
    // Callable is preferred (a value-compatible body that also fits void). Confirmed with javac 23.
    @Test
    public void a_nested_break_does_not_make_an_infinite_loop_body_void_shaped() throws Exception {
        assertBothOrdersPick("callable", "() -> { while (true) { while (true) { break; } } }");
        assertEquals(2, Primitive.unwrap(eval(
            "java.util.function.IntSupplier s = () -> { for (;;) { for (;;) { break; } return 2; } }; s.getAsInt();")));
    }

    @Test
    public void block_that_can_complete_normally_selects_the_void_interface() throws Exception {
        assertBothOrdersPick("runnable", "() -> { if (x == 0) throw new RuntimeException(); }");
        assertBothOrdersPick("runnable", "() -> { if (x == 0) return; throw new RuntimeException(); }");
        assertBothOrdersPick("runnable", "() -> { while (x == 0) {} }");
        assertBothOrdersPick("runnable", "() -> { while (!true) {} }");
        assertBothOrdersPick("runnable", "() -> { do {} while (false); }");
        assertBothOrdersPick("runnable", "() -> { out: ; }");
        assertBothOrdersPick("runnable", "() -> { out: inner: ; }");
        assertBothOrdersPick("runnable", "() -> { do ; while (false); }");
        assertBothOrdersPick("runnable", "() -> { try { x = 1; } finally { x = 2; } }");
        assertBothOrdersPick("runnable", "() -> { switch (x) { default: throw new Error(); case 1: x = 1; } }");
        assertBothOrdersPick("runnable", "() -> { if (x == 0) x = 1; else throw new Error(); }");
        assertBothOrdersPick("runnable", "() -> { if (x == 0) throw new Error(); else x = 1; }");
        assertBothOrdersPick("runnable", "() -> { for (int i = 0; i < 1; i++) {} }");
        assertBothOrdersPick("runnable", "() -> { out: { if (x == 0) break out; throw new Error(); } }");
        assertBothOrdersPick("runnable", "() -> { for (;;) { for (;;) { break; } break; } }");
        assertBothOrdersPick("runnable", "() -> { try { throw new RuntimeException(); } catch (Exception e) {} }");
        assertBothOrdersPick("runnable", "() -> { out: while (true) { break out; } }");
        assertBothOrdersPick("runnable", "() -> { switch (x) { case 1: throw new Error(); } }");
        assertBothOrdersPick("runnable", "() -> { switch (x) { default: throw new Error(); case 1: } }");
        assertBothOrdersPick("runnable", "() -> { switch (x) { case 1: break; default: throw new Error(); } }");
    }

    // javac picks Runnable for a void call; bsh cannot tell at resolution time,
    // so the Callable it picks must still behave like one.
    @Test
    public void void_method_call_body_as_a_callable_runs_and_yields_null() throws Exception {
        assertBothOrdersPick("callable", "() -> sb.setLength(0)");
        assertEquals("0,null,0,null", eval(
            "import java.util.concurrent.*; a = new StringBuilder(\"x\"); b = new StringBuilder(\"yz\");\n"
            + "Callable c = () -> a.setLength(0); r = c.call();\n"
            + "Callable d = () -> b.reverse().setLength(0); s = d.call();\n"
            + "a.length() + \",\" + r + \",\" + b.length() + \",\" + s;"));
    }

    @Test
    public void void_method_call_body_submitted_to_an_executor_runs() throws Exception {
        assertEquals("0,null,0,null", eval(
            "import java.util.concurrent.*;\n"
            + "ex = Executors.newSingleThreadScheduledExecutor(); a = new StringBuilder(\"x\");"
            + " b = new StringBuilder(\"y\"); r = null; s = null;\n"
            + "try { r = ex.submit(() -> a.setLength(0)).get();"
            + " s = ex.schedule(() -> b.setLength(0), 1, TimeUnit.MILLISECONDS).get(); }"
            + " finally { ex.shutdown(); }\n"
            + "a.length() + \",\" + r + \",\" + b.length() + \",\" + s;"));
    }

    // Only a void method call can stand in for null; an undefined name is an error.
    @Test
    public void void_result_fails_unless_a_method_call_returns_a_reference() throws Exception {
        // Block bodies with no return (statically VOID) are rejected at conversion time for value interfaces
        try {
            eval("sb = new StringBuilder(); java.util.concurrent.Callable f = () -> { sb.setLength(0); };");
            fail("expected an EvalError: VOID block body cannot fit Callable (non-void return)");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("its body does not fit"));
        }
        // Expression bodies with statement expressions (EITHER shape) have unknown result types
        // and are allowed at conversion time, failing only at invoke time if the result is incompatible
        for (String conversion : new String[] {
                "java.util.function.IntSupplier f = () -> sb.setLength(0); f.getAsInt();",
                "java.util.function.Supplier f = () -> undefinedNameQ; f.get();",
                "java.util.function.Supplier f = () -> sb.undefinedFieldQ; f.get();",
                "java.util.function.Supplier f = () -> sb.reverse().undefinedFieldQ; f.get();",
                "java.util.function.Function f = s -> s.lenght; f.apply(\"ab\");" }) {
            try {
                eval("sb = new StringBuilder(); " + conversion);
                fail("expected a TargetError for unknown-result body: " + conversion);
            } catch (TargetError expected) {
                String message = expected.getTarget().getMessage();
                assertTrue(conversion + ": " + message, message.contains("Cannot return void") || message.contains("undefined"));
            }
        }
    }

    private static final String GETTER = "bsh.BshLambdaResolutionTest.Overloads.Getter";
    private static final String RUNNING_GETTER = "bsh.BshLambdaResolutionTest.Overloads.RunningGetter";
    private static final String GETTING_RUNNER = "bsh.BshLambdaResolutionTest.Overloads.GettingRunner";

    /** Resolves call against the declarations in every order. */
    private static void assertEveryOrderPicks(String expected, String call, String... declarations)
            throws Exception {
        for (List<String> order : permutations(Arrays.asList(declarations)))
            assertEquals(order + " " + call, expected,
                eval(String.join("\n", order)
                    + "\nfoo() { return 1; } x = 0; arr = new int[1]; sb = new StringBuilder();\n" + call));
    }

    private static List<List<String>> permutations(List<String> items) {
        List<List<String>> all = new java.util.ArrayList<>();
        if (items.isEmpty())
            all.add(new java.util.ArrayList<>());
        for (String first : items) {
            List<String> rest = new java.util.ArrayList<>(items);
            rest.remove(first);
            for (List<String> tail : permutations(rest)) {
                tail.add(0, first);
                all.add(tail);
            }
        }
        return all;
    }

    // Where bsh cannot know the result, an Object result outranks a void one even
    // against a subinterface (javac, knowing the type, picks the subinterface).
    @Test
    public void object_result_beats_a_void_subinterface_for_an_unknown_result() throws Exception {
        assertEveryOrderPicks("getter", "q(() -> foo());",
            "q(" + GETTER + " g) { return \"getter\"; }", "q(" + RUNNING_GETTER + " g) { return \"running\"; }");
        for (String body : new String[] { "{ throw new Error(); }", "x = 1", "x += 1", "x++", "++x", "x--", "new Object()",
                "o.new Inner()", "new Object().new Inner()" })
            assertEveryOrderPicks("getter", "q(() -> " + body + ");",
                "q(" + GETTER + " g) { return \"getter\"; }", "q(" + RUNNING_GETTER + " g) { return \"running\"; }");
        assertEveryOrderPicks("gettingRunner", "w(() -> foo());",
            "w(Runnable r) { return \"runnable\"; }", "w(" + GETTING_RUNNER + " g) { return \"gettingRunner\"; }");
    }

    // An untyped parameter ties with itself, so the other argument decides; without a
    // lambda the same call follows declaration order (bug log 18).
    @Test
    public void untyped_parameters_compete_with_a_lambda_argument() throws Exception {
        assertEveryOrderPicks("collection", "each(new java.util.ArrayList(), v -> v);",
            "each(java.util.Collection c, f) { return \"collection\"; }", "each(Object o, f) { return \"object\"; }");
        for (String[] declarations : new String[][] {
                { "h(a, b) { return \"untyped\"; }", "h(Runnable r, b) { return \"runnable\"; }" },
                { "h(int i, a) { return \"untyped\"; }", "h(int i, Runnable r) { return \"runnable\"; }" } }) {
            for (String lambda : new String[] { "() -> {}", "() -> foo()", "() -> 1" }) {
                String call = declarations[0].startsWith("h(int") ? "h(null, " + lambda + ");" : "h(" + lambda + ", 1);";
                for (List<String> order : permutations(Arrays.asList(declarations)))
                    assertTrue(order + " " + call, Arrays.asList("untyped", "runnable").contains(
                        eval(String.join("\n", order) + "\nfoo() { return 1; }\n" + call)));
            }
        }
    }

    // A primitive result cannot carry a void call's null or an arbitrary object, so
    // void and reference results rank above it where javac would need static types.
    @Test
    public void body_fitting_both_prefers_void_over_a_primitive_result() throws Exception {
        String runnable = "f(Runnable r) { r.run(); return \"runnable\"; }";
        String intSupplier = "f(java.util.function.IntSupplier s) { s.getAsInt(); return \"intSupplier\"; }";
        for (String body : new String[] { "sb.setLength(0)", "new Object()", "foo()", "x++" })
            assertEveryOrderPicks("runnable", "f(() -> " + body + ");", runnable, intSupplier);
        assertEveryOrderPicks("intSupplier", "f(() -> 1);", runnable, intSupplier);
    }

    @Test
    public void reference_result_beats_a_primitive_result() throws Exception {
        String supplier = "g(java.util.function.Supplier s) { return \"supplier:\" + s.get(); }";
        for (String primitive : new String[] { "IntSupplier s) { return \"int:\" + s.getAsInt(); }",
                "BooleanSupplier s) { return \"boolean:\" + s.getAsBoolean(); }" })
            for (String body : new String[] { "\"s\"", "foo()", "new Object() { String toString() { return \"o\"; } }", "1" })
                assertEveryOrderPicks(body.equals("1") && primitive.startsWith("Int") ? "int:1"
                        : "supplier:" + (body.startsWith("new") ? "o" : body.replace("\"", "").replace("foo()", "1")),
                    "g(() -> " + body + ");", supplier, "g(java.util.function." + primitive);
        assertEveryOrderPicks("supplier:1", "g(() -> foo());", supplier,
            "g(Runnable r) { return \"runnable\"; }", "g(java.util.function.IntSupplier s) { return \"int\"; }");
    }

    // Object only matches a lambda in the loose round, where it must still rank below the interface.
    @Test
    public void functional_interface_beats_object_in_the_loose_round() throws Exception {
        assertEveryOrderPicks("runnable", "u(null, () -> {});",
            "u(int i, Object o) { return \"object\"; }", "u(int i, Runnable r) { return \"runnable\"; }");
        assertEveryOrderPicks("callable", "u(null, () -> 1);",
            "u(int i, Object o) { return \"object\"; }", "u(int i, java.util.concurrent.Callable c) { return \"callable\"; }");
    }

    // javac never applies a subinterface whose method the body's shape cannot fit.
    @Test
    public void interface_the_body_fits_beats_a_subinterface_it_cannot_fit() throws Exception {
        String getter = "q(" + GETTER + " g) { return \"getter\"; }";
        String running = "q(" + RUNNING_GETTER + " g) { return \"running\"; }";
        for (String body : new String[] { "1", "-x", "new int[1]", "(foo())", "x == 1 ? foo() : foo()",
                "new java.awt.Point().x", "arr[0]", "o.new int[2]", "o.new Inner[] { null }" })
            assertEveryOrderPicks("getter", "q(() -> " + body + ");", getter, running);
        assertEveryOrderPicks("getter", "q(() -> { return 1; });", getter, running);
        assertEveryOrderPicks("runnable", "w(() -> { x = 1; });",
            "w(Runnable r) { return \"runnable\"; }", "w(" + GETTING_RUNNER + " g) { return \"gettingRunner\"; }");
        assertEveryOrderPicks("getter,string", "m(() -> 1, \"s\");",
            "m(" + GETTER + " g, String s) { return \"getter,string\"; }",
            "m(" + RUNNING_GETTER + " g, Object o) { return \"running,object\"; }");
        assertEveryOrderPicks("getter", "t(() -> 1);", "t(Runnable r) { return \"runnable\"; }", getter.replace("q(", "t("),
            running.replace("q(", "t("));
    }

    @Test
    public void body_shape_breaks_ties_between_one_parameter_interfaces() throws Exception {
        String consumer = "g(java.util.function.Consumer c) { return \"consumer\"; }\n";
        String function = "g(java.util.function.Function f) { return \"function\"; }\n";
        assertEquals("function", eval(consumer + function + "g(v -> v + 1);"));
        assertEquals("function", eval(function + consumer + "g(v -> v + 1);"));
        assertEquals("consumer", eval(consumer + function + "g(v -> { v.hashCode(); });"));
        assertEquals("consumer", eval(function + consumer + "g(v -> { v.hashCode(); });"));
    }

    // Shape only ranks candidates; it never makes a lone candidate inapplicable.
    // bsh cannot tell these apart, so the name decides, in every declaration order.
    @Test
    public void indistinguishable_functional_interfaces_resolve_by_name() throws Exception {
        assertEveryOrderPicks("function", "s(x -> true);",
            "s(java.util.function.Function f) { return \"function\"; }",
            "s(java.util.function.IntFunction i) { return \"intFunction\"; }");
    }

    // As for any argument, a typed parameter matches in an earlier round than
    // an untyped one, so declaration order does not matter.
    @Test
    public void functional_interface_parameter_beats_an_untyped_one() throws Exception {
        String untypedFirst = "h(x) { return \"untyped\"; }\n"
            + "h(Runnable r) { return \"runnable\"; }\n";
        String runnableFirst = "h(Runnable r) { return \"runnable\"; }\n"
            + "h(x) { return \"untyped\"; }\n";
        assertEquals("runnable", eval(untypedFirst + "h(() -> {});"));
        assertEquals("runnable", eval(runnableFirst + "h(() -> {});"));
    }

    @Test
    public void lambda_of_another_arity_resolves_only_to_object() throws Exception {
        String eleven = "(a, b, c, d, e, f, g, h, i, j, k) -> a";
        assertEquals("object", eval(
            "m(Object o) { return \"object\"; }\n"
            + "m(Runnable r) { return \"runnable\"; }\n"
            + "m(" + eleven + ");"));
    }

    private static final String OVERLOADS = "bsh.BshLambdaResolutionTest.Overloads.";

    // Seventh review finding 1: javac applies only IntSupplier.
    @Test
    public void known_int_result_never_picks_a_string_result() throws Exception {
        assertEveryOrderPicks("int:1", "c(() -> 1);",
            "c(java.util.function.IntSupplier s) { return \"int:\" + s.getAsInt(); }",
            "c(" + OVERLOADS + "StrSupplier s) { return \"str:\" + s.get(); }");
        assertEveryOrderPicks("double:1.5", "c(() -> 1.5);",
            "c(java.util.function.DoubleSupplier s) { return \"double:\" + s.getAsDouble(); }",
            "c(" + OVERLOADS + "IntegerSupplier s) { return \"integer:\" + s.get(); }");
    }

    // Seventh review finding 3 and external F4.
    @Test
    public void known_primitive_result_picks_the_narrowest_fitting_primitive() throws Exception {
        assertEveryOrderPicks("int:1", "c(() -> 1);",
            "c(java.util.function.IntSupplier s) { return \"int:\" + s.getAsInt(); }",
            "c(java.util.function.DoubleSupplier s) { return \"double:\" + s.getAsDouble(); }",
            "c(java.util.function.BooleanSupplier s) { return \"bool:\" + s.getAsBoolean(); }");
    }

    // External F3: javac applies only SC.
    @Test
    public void explicit_parameter_type_selects_the_matching_interface() throws Exception {
        assertEveryOrderPicks("sc:ok", "pick((String value) -> \"ok\");",
            "pick(" + OVERLOADS + "SC v) { return \"sc:\" + v.accept(\"x\"); }",
            "pick(" + OVERLOADS + "IC v) { return \"ic:\" + v.accept(1); }");
    }

    // External F13: no marker ceiling.
    @Test
    public void lambda_with_eleven_parameters_resolves_to_a_functional_interface() throws Exception {
        assertEquals("a", eval("m(Object o) { return \"object\"; }\n"
            + "m(" + OVERLOADS + "Eleven e) { return e.apply(\"a\", 2, 3, 4, 5, 6, 7, 8, 9, 10, 11); }\n"
            + "m((a, b, c, d, e, f, g, h, i, j, k) -> a);"));
    }

    // The v4 review's regression case: javac and bsh both submit a Runnable.
    @Test
    public void unsure_void_block_submitted_to_an_executor_is_a_runnable() throws Exception {
        assertEquals("null", eval("flag = true; r = \"unset\";\n"
            + "ex = java.util.concurrent.Executors.newSingleThreadExecutor();\n"
            + "try { r = String.valueOf(ex.submit(() -> { while (flag) { flag = false; } }).get()); }"
            + " finally { ex.shutdown(); }\nr;"));
    }

    @Test
    public void lone_value_interface_rejects_a_certainly_void_block() throws Exception {
        try {
            eval("only(java.util.concurrent.Callable c) { return \"callable\"; }\n"
                + "only(() -> { x = 1; });");
            fail("a void block cannot be a Callable");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("only(<0-arg lambda returning void>)"));
        }
        assertEquals("callable", eval("only(java.util.concurrent.Callable c) { return \"callable\"; }\n"
            + "only(() -> { while (flag) { } });"));
    }

    // The untyped argument ties with itself, so the lambda's subtype rule decides.
    @Test
    public void untyped_argument_leaves_the_decision_to_the_lambda() throws Exception {
        assertEveryOrderPicks("w", "h(() -> 1, 1);",
            "h(java.util.function.IntSupplier s, b) { return \"int\"; }", "h(" + OVERLOADS + "W w, b) { return \"w\"; }");
    }

    // Scripted interfaces carry a malformed generic signature, which reflection rejects with an Error.
    @Test
    public void scripted_interface_is_a_lambda_target() throws Exception {
        assertEquals("S", eval("interface S { void run(); } m(S s) { return \"S\"; } m(() -> { });"));
        assertEquals("F:x", eval("interface F<T> { void accept(T t); }"
            + " seen = \"\"; m(F f) { f.accept(\"x\"); return \"F:\" + seen; } m((String s) -> { seen = s; });"));
        assertEquals("G:1", eval("interface G { Object get(); } m(G g) { return \"G:\" + g.get(); } m(() -> 1);"));
        assertEquals("P:3", eval("interface P { void take(int x); } seen = 0;"
            + " m(P p) { p.take(3); return \"P:\" + seen; } m((int x) -> { seen = x; });"));
    }

    // bsh types an unsuffixed 2147483648 as long; javac and bsh both run it as an int.
    @Test
    public void unsuffixed_long_literal_still_reaches_an_int_result() throws Exception {
        assertEveryOrderPicks("int:-2147483648", "m(() -> -2147483648);",
            "m(java.util.function.IntSupplier s) { return \"int:\" + s.getAsInt(); }",
            "m(Runnable r) { return \"runnable\"; }");
        assertEveryOrderPicks("int:-2147483648", "m(() -> -2147483648);",
            "m(java.util.function.IntSupplier s) { return \"int:\" + s.getAsInt(); }",
            "m(java.util.function.LongSupplier s) { return \"long:\" + s.getAsLong(); }",
            "m(java.util.function.Supplier s) { return \"supplier:\" + s.get(); }");
    }

    // JLS 15.27.3: a lambda cannot implement a generic method.
    @Test
    public void generic_method_interface_is_not_a_lambda_target() throws Exception {
        assertEveryOrderPicks("supplier", "m(() -> \"x\");",
            "m(" + OVERLOADS + "AGen g) { return \"agen\"; }",
            "m(java.util.function.Supplier s) { return \"supplier\"; }");
    }

    // Serializable is BshLambda's own interface, not a target a lambda can become.
    @Test
    public void lambda_does_not_match_a_serializable_parameter() throws Exception {
        assertEveryOrderPicks("object", "o(() -> 1);",
            "o(Object x) { return \"object\"; }", "o(java.io.Serializable s) { return \"serializable\"; }");
        try {
            eval("k(java.io.Serializable s) { return \"serializable\"; } k(() -> 1);");
            fail("a lambda is not Serializable");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("k(<0-arg lambda returning int>)"));
        }
    }

    // A void-body lambda is rejected when converted to a non-void interface,
    // even for a lone Java method. The conversion happens before method resolution.
    @Test
    public void lone_java_method_rejects_a_void_lambda_for_a_value_interface() throws Exception {
        try {
            eval("import bsh.BshLambdaResolutionTest.Overloads;"
                + " Overloads.lone(() -> { x = 1; });");
            fail("expected an error: void-body lambda cannot fit Callable");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("does not fit"));
        }
    }

    // MemberCache.findBest's lone-candidate shortcut (BshClassManager.java)
    // returns this candidate without checking lambda fit, but Invocable's
    // argument coercion (coerceToType -> castObject -> BshLambda.convertTo)
    // rejects the mismatch before the method handle is invoked, so no change
    // to findBest is needed. Pins that this stays true.
    @Test
    public void a_lone_compiled_java_method_still_rejects_a_certainly_void_lambda() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaResolutionTest.LoneCallableTaker;"
                + " LoneCallableTaker.take(() -> { x = 1; });");
            fail("expected an EvalError: a void block cannot fit Callable");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("take"));
        }
    }

    @Test
    public void unresolvable_call_does_not_name_the_internal_marker_type() throws Exception {
        try {
            eval("noSuchMethod(x -> x);");
            fail("expected an EvalError");
        } catch (EvalError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("noSuchMethod(<1-arg lambda>)"));
        }
    }

    @Test
    public void a_functional_varargs_candidate_beats_object_for_a_lambda_argument() throws Exception {
        assertEquals("varargs", new Interpreter().eval(
            "import bsh.BshLambdaResolutionTest.ObjectVsVarargsRunnable;"
            + " ObjectVsVarargsRunnable.take(() -> {});"));
    }

    @Test
    public void a_scripted_functional_varargs_candidate_beats_object_for_a_lambda_argument() throws Exception {
        assertEquals("varargs", eval(
            "f(Object x) { return \"object\"; }\n"
            + "f(Runnable... x) { return \"varargs\"; }\n"
            + "f(() -> {});"));
    }

    // javac reaches JLS 15.12.2 phase 3 only when no fixed-arity candidate
    // applies, so the fixed-arity one wins whichever way the names sort. The
    // two orderings are separate tests so neither can mask the other.
    @Test
    public void a_fixed_arity_functional_candidate_beats_a_later_named_varargs_one() throws Exception {
        assertEquals("fixed", eval(
            "import bsh.BshLambdaResolutionTest.FixedAaaVarargsZzz;"
            + " FixedAaaVarargsZzz.q(() -> {});"));
    }

    @Test
    public void a_fixed_arity_functional_candidate_beats_an_earlier_named_varargs_one() throws Exception {
        assertEquals("fixed", eval(
            "import bsh.BshLambdaResolutionTest.FixedZzzVarargsAaa;"
            + " FixedZzzVarargsAaa.q(() -> {});"));
    }

    @Test
    public void a_scripted_fixed_arity_candidate_beats_a_later_named_varargs_one() throws Exception {
        assertEquals("fixed", eval(IMPORT_AAA_ZZZ
            + "f(Aaa x) { return \"fixed\"; }\n"
            + "f(Zzz... x) { return \"varargs\"; }\n"
            + "f(() -> {});"));
    }

    @Test
    public void a_scripted_fixed_arity_candidate_beats_an_earlier_named_varargs_one() throws Exception {
        assertEquals("fixed", eval(IMPORT_AAA_ZZZ
            + "f(Zzz x) { return \"fixed\"; }\n"
            + "f(Aaa... x) { return \"varargs\"; }\n"
            + "f(() -> {});"));
    }

    // Holding the raw-lambda shortcut back must not disable it: Object is
    // still a lambda's target of last resort when nothing else will take it.
    @Test
    public void an_object_parameter_still_takes_a_lambda_raw_when_alone() throws Exception {
        assertEquals("object", eval(
            "import bsh.BshLambdaResolutionTest.LoneObjectTaker;"
            + " LoneObjectTaker.q(() -> {});"));
        assertEquals("object", eval(
            "f(Object x) { return \"object\"; }\n"
            + "f(() -> {});"));
    }

    // Both candidates are reachable only through that shortcut, so the choice
    // between them is settled by arity, not by which pass ran first.
    @Test
    public void fixed_arity_still_wins_when_only_the_raw_shortcut_applies() throws Exception {
        assertEquals("fixed", eval(
            "import bsh.BshLambdaResolutionTest.ObjectVsVarargsObject;"
            + " ObjectVsVarargsObject.q(() -> {});"));
        assertEquals("fixed", eval(
            "f(Object x) { return \"fixed\"; }\n"
            + "f(Object... x) { return \"varargs\"; }\n"
            + "f(() -> {});"));
    }

    // Round four is also bsh's only model for unbox-then-widen (JLS 5.3), which
    // an ordinary argument beside the lambda may be the only thing needing.
    @Test
    public void a_non_lambda_argument_keeps_its_round_four_conversion() throws Exception {
        assertEquals("fixed", eval(
            "import bsh.BshLambdaResolutionTest.WideningVsVarargs;"
            + " WideningVsVarargs.q(Integer.valueOf(1), () -> {});"));
    }

    /*
        An untyped parameter now outranks an Object one for a lambda, where it
        used to lose. Preferring a functional varargs candidate over Object --
        the whole point of holding the raw shortcut back -- closes a cycle:
        Object beat untyped, untyped beat Runnable..., and Runnable... now has
        to beat Object. One pair had to give, and Object over untyped is the
        only one of the three with nothing in Java behind it, since Java has
        neither untyped parameters nor any way for a lambda to target Object.
        What is left is a plain order: untyped, then Runnable..., then Object.
    */
    @Test
    public void an_untyped_scripted_parameter_beats_a_later_declared_object_one() throws Exception {
        assertEquals("untyped", eval(
            "f(x) { return \"untyped\"; }\n"
            + "f(Object o) { return \"object\"; }\n"
            + "f(() -> {});"));
    }

    @Test
    public void an_untyped_scripted_parameter_beats_an_earlier_declared_object_one() throws Exception {
        assertEquals("untyped", eval(
            "f(Object o) { return \"object\"; }\n"
            + "f(x) { return \"untyped\"; }\n"
            + "f(() -> {});"));
    }

    // The same overload set without a lambda is untouched, as every non-lambda
    // call is: there Object still wins, exactly as it did before.
    @Test
    public void an_object_parameter_still_beats_an_untyped_one_without_a_lambda() throws Exception {
        assertEquals("object", eval(
            "f(x) { return \"untyped\"; }\n"
            + "f(Object o) { return \"object\"; }\n"
            + "f(\"s\");"));
    }

    // A real functional interface still outranks an untyped parameter.
    @Test
    public void a_functional_interface_still_beats_an_untyped_scripted_parameter() throws Exception {
        assertEquals("runnable", eval(
            "f(x) { return \"untyped\"; }\n"
            + "f(Runnable r) { return \"runnable\"; }\n"
            + "f(() -> {});"));
    }

    // Accepted divergence: bsh's resolution has no runtime "ambiguous" outcome for
    // any call (lambda or not) -- ties among maximal candidates resolve
    // deterministically by interface name rather than raising an error, unlike
    // javac's static ambiguity.
    @Test
    public void tied_functional_interfaces_resolve_deterministically_by_name_not_ambiguity() throws Exception {
        String result = (String) new Interpreter().eval(
            "f(java.util.concurrent.Callable x) { return \"callable\"; }"
            + " f(java.util.function.Supplier x) { return \"supplier\"; }"
            + " f(() -> \"x\");");
        assertEquals("callable", result); // Callable's fully-qualified name sorts before Supplier's, so it wins the tie-break; see compareFully.
    }

    @Test
    public void a_check_only_cast_from_the_raw_lambda_class_reports_functional_targets_without_crashing() throws Exception {
        assertTrue(Types.isBshAssignable(Runnable.class, BshLambda.class));
        assertTrue(Types.isBshAssignable(java.util.function.Function.class, BshLambda.class));
        assertTrue(Types.isBshAssignable(Object.class, BshLambda.class));
        assertFalse(Types.isBshAssignable(java.util.List.class, BshLambda.class));
        assertFalse(Types.isBshAssignable(String.class, BshLambda.class));
    }

    @Test
    public void namespace_get_method_accepts_a_raw_lambda_class_in_the_signature() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("f(Runnable r) { return \"r\"; } lam = () -> {};");
        Object lam = interpreter.get("lam");
        BshMethod found = interpreter.getNameSpace().getMethod("f", new Class<?>[] { lam.getClass() });
        assertNotNull(found);
        assertEquals(Runnable.class, found.getParameterTypes()[0]);
    }
}
