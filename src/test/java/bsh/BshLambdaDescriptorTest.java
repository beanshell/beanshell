package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;

/** What overload resolution can know about a lambda before it runs: body shape and result type. */
@RunWith(FilteredTestRunner.class)
public class BshLambdaDescriptorTest {

    private static Node body(String lambda) throws Exception {
        Parser parser = new Parser(new StringReader(lambda + ";"));
        parser.Line();
        Node expression = parser.popNode().jjtGetChild(0);
        return expression.jjtGetChild(expression.jjtGetNumChildren() - 1);
    }

    private static int shape(String lambda) throws Exception {
        return BSHLambdaExpression.bodyShape(body(lambda));
    }

    private static LambdaDescriptor.Result result(String lambda) throws Exception {
        return BSHLambdaExpression.result(body(lambda));
    }

    private static void assertResult(Class<?> type, LambdaDescriptor.Result result, Object... constants) {
        assertEquals(type, result.type);
        assertEquals(new HashSet<>(Arrays.asList(constants)), result.constants);
    }

    @Test
    public void block_that_certainly_completes_is_void() throws Exception {
        assertEquals(BshLambda.VOID, shape("() -> { x = 1; }"));
        assertEquals(BshLambda.VOID, shape("() -> { return; }"));
        assertEquals(BshLambda.VOID, shape("() -> { if (c) throw new Error(); }"));
        assertEquals(BshLambda.VOID, shape("() -> { for (v : list) { } }"));
        assertEquals(BshLambda.VOID, shape("() -> { switch (x) { case 1: foo(); } }"));
    }

    @Test
    public void block_that_cannot_complete_fits_either() throws Exception {
        assertEquals(BshLambda.EITHER, shape("() -> { throw new Error(); }"));
        assertEquals(BshLambda.EITHER, shape("() -> { while (true) { } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { for (;;) { } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { if (c) throw new Error(); else throw new Error(); }"));
        assertEquals(BshLambda.EITHER, shape("() -> { try { throw new Error(); } catch (Error e) { throw e; } }"));
    }

    // JLS 15.28: a condition built from literals and operators is a constant.
    @Test
    public void loop_on_a_folded_constant_condition_cannot_complete() throws Exception {
        assertEquals(BshLambda.EITHER, shape("() -> { while (1 == 1) { } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { do { } while (1 < 2); }"));
        assertEquals(BshLambda.EITHER, shape("() -> { for (; !false; ) { } }"));
        assertEquals(BshLambda.VOID, shape("() -> { while (1 == 2) { } }"));
    }

    // JLS 14.13: a do statement completes only if its body does (or a continue or break reaches out).
    @Test
    public void do_statement_completes_only_through_its_body() throws Exception {
        assertEquals(BshLambda.EITHER, shape("() -> { do { throw new Error(); } while (false); }"));
        assertEquals(BshLambda.VOID, shape("() -> { do { x++; } while (false); }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { do { if (c) continue; throw new Error(); } while (false); }"));
        assertEquals(BshLambda.EITHER, shape("() -> { do { throw new Error(); } while (flag); }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { do { x++; } while (flag); }"));
    }

    // A name may be a constant variable, and a break's target is not traced.
    @Test
    public void void_resting_on_an_unfoldable_condition_or_a_break_is_unsure() throws Exception {
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { while (flag) { flag = false; } }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { for (int i = 0; i < 3; i++) { } }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { while (true) { if (c) break; } }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { l: while (true) { break l; } }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { switch (x) { case 1: break; default: throw new Error(); } }"));
        assertEquals(BshLambda.VOID_UNSURE,
            shape("() -> { while (true) { try { break; } finally { throw new Error(); } } }"));
    }

    // JLS 14.15: an unlabeled break ends only the innermost loop or switch.
    @Test
    public void a_break_inside_a_nested_loop_or_switch_does_not_end_the_outer_loop() throws Exception {
        assertEquals(BshLambda.EITHER, shape("() -> { while (true) { while (true) { break; } } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { for (;;) { switch (x) { case 1: break; } } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { do { for (v : list) { break; } } while (true); }"));
        assertEquals(BshLambda.VALUE, shape("() -> { for (;;) { for (;;) { break; } return 2; } }"));
        assertEquals(BshLambda.VALUE, shape("() -> { while (true) { switch (1) { case 1: break; } return 1; } }"));
        assertEquals(BshLambda.VALUE, shape("() -> { for (;;) { inner: { break inner; } return 3; } }"));
    }

    // A labeled break ends the statement carrying that label and nothing else.
    @Test
    public void a_labeled_break_ends_only_its_own_statement() throws Exception {
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { outer: while (true) { while (true) { break outer; } } }"));
        assertEquals(BshLambda.EITHER, shape("() -> { outer: while (true) { inner: while (true) { break inner; } } }"));
        assertEquals(BshLambda.VOID_UNSURE, shape("() -> { l: { foo(); break l; } }"));
    }

    @Test
    public void value_return_and_expression_shapes_are_unchanged() throws Exception {
        assertEquals(BshLambda.VALUE, shape("() -> { return 1; }"));
        assertEquals(BshLambda.VALUE, shape("() -> 1"));
        assertEquals(BshLambda.EITHER, shape("() -> foo()"));
    }

    @Test
    public void a_block_with_a_valued_return_that_can_also_complete_normally_is_not_value_shaped() throws Exception {
        assertNotEquals(BshLambda.VALUE, shape("() -> { if (flag) return 1; }"));
    }

    @Test
    public void a_block_mixing_a_valued_and_a_bare_return_is_not_value_shaped() throws Exception {
        assertNotEquals(BshLambda.VALUE, shape("() -> { if (flag) return 1; return; }"));
    }

    // JLS 15.27.2: a valued return rules out void-compatibility; a bare return or a
    // reachable end rules out value-compatibility. Both together fit nothing.
    @Test
    public void a_block_mixing_valued_and_bare_returns_or_falling_off_the_end_is_invalid() throws Exception {
        assertEquals(BshLambda.INVALID, shape("() -> { if (flag) return 1; }"));
        assertEquals(BshLambda.INVALID, shape("() -> { if (flag) return 1; return; }"));
        assertEquals(BshLambda.INVALID, shape("() -> { if (flag) return foo(); }"));
        assertEquals(BshLambda.INVALID, shape("() -> { switch (x) { case 1: return x; case 2: return x + 1; } }"));
        assertEquals(BshLambda.INVALID, shape("() -> { for (v : list) { return v; } }"));
    }

    // bsh cannot tell whether `flag` is a constant variable, so the valued return decides the shape.
    @Test
    public void a_valued_return_under_an_unfoldable_condition_is_value_shaped() throws Exception {
        assertEquals(BshLambda.VALUE, shape("() -> { while (flag) { return 1; } }"));
        assertEquals(BshLambda.VALUE, shape("() -> { while (true) { if (c) break; return 1; } }"));
    }

    // Exhaustive branches that all return a value cannot complete normally: VALUE, not INVALID.
    @Test
    public void an_if_else_returning_a_value_on_both_branches_is_value_shaped() throws Exception {
        assertEquals(BshLambda.VALUE, shape("() -> { if (flag) return 1; else return 2; }"));
        assertTrue(descriptor("() -> { if (flag) return 1; else return 2; }").fits(Callable.class));
        assertFalse(descriptor("() -> { if (flag) return 1; else return 2; }").fits(Runnable.class));
    }

    @Test
    public void an_invalid_block_fits_no_interface() throws Exception {
        LambdaDescriptor invalid = descriptor("() -> { if (flag) return 1; }");
        assertFalse(invalid.fits(Runnable.class));
        assertFalse(invalid.fits(Supplier.class));
        assertFalse(invalid.fits(IntSupplier.class));
        LambdaDescriptor unknown = descriptor("() -> { if (flag) return foo(); }");
        assertFalse(unknown.fits(Runnable.class));
        assertFalse(unknown.fits(Callable.class));
    }

    @Test
    public void literal_result_has_the_type_of_its_bsh_value() throws Exception {
        assertResult(int.class, result("() -> 1"), 1);
        assertResult(char.class, result("() -> 'a'"), 'a');
        assertResult(String.class, result("() -> 'ab'"), "ab");
        assertResult(long.class, result("() -> 3000000000L"), 3000000000L);
        assertResult(String.class, result("() -> \"s\""), "s");
        assertEquals(LambdaDescriptor.NullType.class, result("() -> null").type);
    }

    @Test
    public void unary_operators_fold_as_bsh_evaluates_them() throws Exception {
        assertResult(long.class, result("() -> -2147483648L"), -2147483648L);
        assertResult(int.class, result("() -> -'a'"), -97);
        assertResult(int.class, result("() -> ~1"), -2);
    }

    // bsh widens an unsuffixed literal too large for int to long; javac would reject or keep int.
    @Test
    public void unsuffixed_literal_widened_to_long_is_unknown() throws Exception {
        assertNull(result("() -> 3000000000"));
        assertNull(result("() -> 0x80000000"));
        assertNull(result("() -> -0x80000000"));
    }

    // JLS 3.10.1: 2147483648 is legal only as the operand of unary minus, giving the int minimum.
    @Test
    public void negated_int_minimum_literal_is_an_int() throws Exception {
        assertResult(int.class, result("() -> -2147483648"), Integer.MIN_VALUE);
        assertResult(int.class, result("() -> (-2147483648)"), Integer.MIN_VALUE);
        assertResult(int.class, result("() -> -2_147_483_648"), Integer.MIN_VALUE);
        assertNull(result("() -> -(2147483648)"));
    }

    @Test
    public void primitive_array_cast_is_unknown() throws Exception {
        assertNull(result("() -> (int[]) x"));
    }

    @Test
    public void primitive_cast_fixes_the_type() throws Exception {
        assertResult(short.class, result("() -> (short) 1"), (short) 1);
        assertEquals(int.class, result("() -> (int) foo()").type);
        assertNull(result("() -> (int) foo()").constants);
    }

    @Test
    public void anything_else_or_a_folding_failure_is_unknown() throws Exception {
        for (String lambda : new String[] { "() -> foo()", "() -> x", "() -> ~1.5",
                "() -> -true", "() -> (String) x", "() -> new Object()", "() -> { throw new Error(); }",
                "() -> { if (c) return 1; return 1L; }", "() -> { if (c) return 1; return foo(); }" })
            assertNull(lambda, result(lambda));
    }

    // Deep-review Finding 11: expressionResult() must recurse into binary and
    // ternary expressions the same way fold() already does for loop completion.
    @Test
    public void a_foldable_binary_expression_is_a_known_constant_result() throws Exception {
        assertResult(int.class, result("() -> 1 + 1"), 2);
    }

    @Test
    public void a_foldable_ternary_expression_is_a_known_constant_result() throws Exception {
        assertResult(int.class, result("() -> true ? 1 : 2"), 1);
    }

    @Test
    public void block_result_joins_every_return() throws Exception {
        assertResult(int.class, result("() -> { if (c) return 1; return 2; }"), 1, 2);
        assertResult(int.class, result("() -> { Runnable r = () -> { return; }; return 1; }"), 1);
        LambdaDescriptor.Result mixed = result("() -> { if (c) return 1; return (int) foo(); }");
        assertEquals(int.class, mixed.type);
        assertNull(mixed.constants);
    }

    @Test
    public void constants_of_different_types_are_different() throws Exception {
        assertNotEquals(result("() -> 1"), result("() -> 1L"));
        assertEquals(result("() -> 1"), result("() -> 1"));
    }

    public interface StrSupplier { String get(); }
    public interface ObjSupplier { Object get(); }
    public interface LongGetter { long get(); }
    public interface ShortS { short get(); }
    public interface ByteS { byte get(); }
    public interface BoxedByteS { Byte get(); }
    public interface NumberS { Number get(); }
    public interface IntegerS { Integer get(); }
    public interface LongS { Long get(); }
    public interface SC { void accept(String s); }
    public interface IC { void accept(Integer i); }
    public interface NC { void accept(Number n); }
    public interface AH<T> { void h(T[] a); }
    public interface NumberTaker<T extends Number> { void take(T t); }
    public interface ListTaker<T> { void take(java.util.List<T> list); }
    public interface FixedListTaker { void take(java.util.List<String> values); }
    public interface MapTaker { void take(java.util.Map<String, Integer> m); }
    public interface WildcardTaker { void take(java.util.List<?> values); }
    public interface BoundedWildcardTaker<T> { void take(java.util.List<? extends T> values); }
    public interface NestedTaker<T> { void take(java.util.Map<String, java.util.List<T>> m); }
    public interface IntTask extends Supplier<Object> { default Object get() { return null; } int getAsInt(); }
    public interface W extends IntSupplier { default int getAsInt() { return 0; } Object get(); }
    public interface CycB { int getAsInt(); }
    public interface CycA extends CycB { default int getAsInt() { return 0; } boolean getAsBoolean(); }
    public interface CycC extends CycA { default boolean getAsBoolean() { return false; } long getAsLong(); }
    public interface A0 { void run(); }
    public interface A1 { void go(); }
    interface Hidden { void run(); }

    private static LambdaDescriptor descriptor(String lambda, Class<?>... paramTypes) throws Exception {
        Node body = body(lambda);
        return new LambdaDescriptor(BSHLambdaExpression.bodyShape(body), paramTypes,
            BSHLambdaExpression.result(body));
    }

    @Test
    public void known_result_fits_by_java_assignment_rules() throws Exception {
        LambdaDescriptor one = descriptor("() -> 1");
        for (Class<?> fits : new Class<?>[] { IntSupplier.class, DoubleSupplier.class, LongGetter.class,
                ShortS.class, ByteS.class, BoxedByteS.class, Supplier.class, NumberS.class, IntegerS.class })
            assertTrue(fits.getSimpleName(), one.fits(fits));
        for (Class<?> unfit : new Class<?>[] { StrSupplier.class, BooleanSupplier.class, LongS.class,
                Runnable.class })
            assertFalse(unfit.getSimpleName(), one.fits(unfit));
        assertTrue(descriptor("() -> 'a'").fits(ShortS.class));
        assertTrue(descriptor("() -> -128").fits(ByteS.class));
        assertFalse(descriptor("() -> 128").fits(ByteS.class));
        assertFalse(descriptor("() -> 1.5").fits(IntSupplier.class));
        assertTrue(descriptor("() -> { if (c) return 1; return 2; }").fits(ByteS.class));
        assertFalse(descriptor("() -> { if (c) return 1; return 200; }").fits(ByteS.class));
        assertTrue(descriptor("() -> null").fits(StrSupplier.class));
        assertFalse(descriptor("() -> null").fits(IntSupplier.class));
        assertFalse(descriptor("() -> -129").fits(ByteS.class));
        assertFalse(descriptor("() -> (char) foo()").fits(ShortS.class));
        assertFalse(descriptor("() -> \"s\"").fits(IntTask.class));
    }

    @Test
    public void shape_decides_void_and_value_targets() throws Exception {
        assertTrue(descriptor("() -> { x = 1; }").fits(Runnable.class));
        assertFalse(descriptor("() -> { x = 1; }").fits(Callable.class));
        assertFalse(descriptor("() -> x").fits(Runnable.class));
        assertTrue(descriptor("() -> foo()").fits(Runnable.class));
        assertTrue(descriptor("() -> foo()").fits(Callable.class));
        assertTrue(descriptor("() -> { while (flag) { } }").fits(Runnable.class));
        assertTrue(descriptor("() -> { while (flag) { } }").fits(Callable.class));
    }

    @Test
    public void explicit_parameter_types_must_match_unless_the_parameter_is_generic() throws Exception {
        assertTrue(descriptor("(s) -> { }", String.class).fits(SC.class));
        assertFalse(descriptor("(s) -> { }", String.class).fits(IC.class));
        assertTrue(descriptor("(s) -> { }", String.class).fits(Consumer.class));
        assertTrue(descriptor("(x) -> { }", int.class).fits(Consumer.class));
        assertTrue(descriptor("(x) -> { }", int.class).fits(IntConsumer.class));
        assertFalse(descriptor("(x) -> { }", Integer.class).fits(IntConsumer.class));
        assertFalse(descriptor("(x) -> { }", Integer.class).fits(NC.class));
        assertTrue(descriptor("(a) -> { }", String[].class).fits(AH.class));
        assertFalse(descriptor("(s) -> { }", String.class).fits(NumberTaker.class));
        assertTrue(descriptor("(l) -> { }", java.util.ArrayList.class).fits(ListTaker.class));
        assertTrue(descriptor("(s) -> { }", (Class<?>) null).fits(IC.class));
    }

    // JLS 15.27.3: a concrete parameterized signature is available through reflection,
    // so the erasure must match exactly; only an unresolved type variable earns leniency.
    @Test
    public void a_concrete_parameterized_parameter_requires_an_exact_erasure() throws Exception {
        assertFalse(descriptor("(x) -> { }", java.util.ArrayList.class).fits(FixedListTaker.class));
        assertTrue(descriptor("(x) -> { }", java.util.List.class).fits(FixedListTaker.class));
        assertFalse(descriptor("(x) -> { }", java.util.Collection.class).fits(FixedListTaker.class));
        assertFalse(descriptor("(x) -> { }", java.util.HashMap.class).fits(MapTaker.class));
        assertFalse(descriptor("(x) -> { }", java.util.ArrayList.class).fits(WildcardTaker.class));
        assertTrue(descriptor("(x) -> { }", java.util.ArrayList.class).fits(BoundedWildcardTaker.class));
        assertTrue(descriptor("(x) -> { }", java.util.HashMap.class).fits(NestedTaker.class));
    }

    @Test
    public void arity_and_access_decide_before_anything_else() throws Exception {
        assertFalse(descriptor("(a) -> { }", (Class<?>) null).fits(Runnable.class));
        assertFalse(descriptor("() -> { }").fits(Consumer.class));
        assertFalse(descriptor("() -> { }").fits(Hidden.class));
        assertFalse(descriptor("() -> { }").fits(Object.class));
        Class<?>[] eleven = new Class<?>[11];
        assertEquals(11, new LambdaDescriptor(BshLambda.VALUE, eleven, null).arity);
    }

    /** The candidate picked for a one-argument call, which must be the same in every declaration order. */
    private static Class<?> pick(LambdaDescriptor lambda, Class<?>... candidates) {
        Set<Class<?>> picked = new HashSet<>();
        for (List<Class<?>> order : permutations(Arrays.asList(candidates))) {
            Class<?>[][] signatures = new Class<?>[order.size()][];
            List<Integer> applicable = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) {
                signatures[i] = new Class<?>[] { order.get(i) };
                if (order.get(i) == null || lambda.fits(order.get(i)) || order.get(i) == Object.class)
                    applicable.add(i);
            }
            int index = LambdaDescriptor.select(new LambdaDescriptor[] { lambda }, signatures, applicable);
            picked.add(index < 0 ? null : order.get(index));
        }
        assertEquals("one pick in every order: " + picked, 1, picked.size());
        return picked.iterator().next();
    }

    private static <T> List<List<T>> permutations(List<T> items) {
        List<List<T>> all = new ArrayList<>();
        if (items.isEmpty())
            all.add(new ArrayList<>());
        for (int i = 0; i < items.size(); i++) {
            List<T> rest = new ArrayList<>(items);
            T first = rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                tail.add(0, first);
                all.add(tail);
            }
        }
        return all;
    }

    // Seventh review findings 1-3 and external F4: javac's answers.
    @Test
    public void known_results_pick_what_javac_picks() throws Exception {
        assertEquals(IntSupplier.class, pick(descriptor("() -> 1"), IntSupplier.class, StrSupplier.class));
        assertEquals(DoubleSupplier.class, pick(descriptor("() -> 1.5"), IntSupplier.class, DoubleSupplier.class));
        assertEquals(IntSupplier.class, pick(descriptor("() -> 1"), IntSupplier.class, DoubleSupplier.class));
        assertEquals(IntSupplier.class, pick(descriptor("() -> 1"), IntSupplier.class, BooleanSupplier.class));
        assertEquals(Supplier.class, pick(descriptor("() -> 1"), StrSupplier.class, Supplier.class));
        assertEquals(Supplier.class, pick(descriptor("() -> \"s\""), Supplier.class, IntTask.class));
        assertEquals(StrSupplier.class, pick(descriptor("() -> \"x\""), StrSupplier.class, ObjSupplier.class));
        assertEquals(IntSupplier.class, pick(descriptor("() -> 1"), IntSupplier.class, LongGetter.class));
        assertEquals(W.class, pick(descriptor("() -> 1"), IntSupplier.class, W.class));
        assertEquals(CycC.class, pick(descriptor("() -> 1"), CycA.class, CycB.class, CycC.class));
        assertEquals(SC.class, pick(descriptor("(s) -> { }", String.class), SC.class, IC.class));
    }

    // javac itself follows declaration order here (each beats one other); bsh
    // must still pick one, and for a primitive constant the narrowest primitive.
    @Test
    public void jls_cycle_falls_back_to_the_ranking_key() throws Exception {
        assertEquals(IntSupplier.class, pick(descriptor("() -> 1"), IntSupplier.class, DoubleSupplier.class, W.class));
    }

    public interface Getter { Object get(); }
    public interface SubGetter extends Getter { }

    @Test
    public void subinterface_outranks_its_parent_for_an_unknown_result() throws Exception {
        assertEquals(SubGetter.class, pick(descriptor("() -> foo()"), Getter.class, SubGetter.class));
    }

    @Test
    public void unknown_results_are_ranked_safety_first() throws Exception {
        assertEquals(Callable.class, pick(descriptor("() -> foo()"), Runnable.class, Callable.class));
        assertEquals(Runnable.class, pick(descriptor("() -> foo()"), Runnable.class, IntSupplier.class));
        assertEquals(Runnable.class, pick(descriptor("() -> { while (flag) { flag = false; } }"),
            Runnable.class, Callable.class));
        assertEquals(Callable.class, pick(descriptor("() -> { while (true) { } }"), Runnable.class, Callable.class));
    }

    @Test
    public void functional_interfaces_beat_object_and_untyped_parameters() throws Exception {
        assertEquals(Runnable.class, pick(descriptor("() -> { }"), Object.class, Runnable.class));
        assertEquals(Runnable.class, pick(descriptor("() -> { }"), null, Runnable.class));
        assertEquals(Object.class, pick(descriptor("() -> { }"), null, Object.class));
    }

    // Equal in every respect bsh can see: the interface name decides, whatever the declaration order.
    @Test
    public void remaining_ties_go_by_interface_name() throws Exception {
        assertEquals(Function.class, pick(descriptor("x -> true", (Class<?>) null), Function.class, IntFunction.class));
        assertEquals(A0.class, pick(descriptor("() -> foo()"), A0.class, A1.class));
    }

    @Test
    public void other_arguments_decide_before_a_name_tie_break() throws Exception {
        LambdaDescriptor lambda = descriptor("() -> foo()");
        Class<?>[][] signatures = { { A0.class, CharSequence.class }, { A1.class, String.class } };
        List<Integer> both = Arrays.asList(0, 1);
        assertEquals(1, LambdaDescriptor.select(new LambdaDescriptor[] { lambda, null }, signatures, both));
        Class<?>[][] reversed = { signatures[1], signatures[0] };
        assertEquals(0, LambdaDescriptor.select(new LambdaDescriptor[] { lambda, null }, reversed, both));
    }

    // Accepted divergence: bsh has no representation of a target interface's
    // generic type arguments anywhere (Consumer<String> c = ...; resolves c's type
    // to the plain raw Consumer.class) -- so an explicit lambda parameter narrower
    // than a generic SAM's erasure is accepted by assignability, not rejected by
    // exact equality, since there is no substituted type anywhere to compare
    // against.
    @Test
    public void an_explicit_parameter_narrower_than_a_generic_sams_erasure_still_fits() throws Exception {
        Node body = body("(String s) -> {}");
        LambdaDescriptor descriptor = new LambdaDescriptor(
            BSHLambdaExpression.bodyShape(body), new Class<?>[] { String.class }, null);
        assertTrue(descriptor.fits(java.util.function.Consumer.class));
    }

    public interface GenericGetter<T> { T get(); }
    public interface StringGetter extends GenericGetter<String> {}
    public interface IntegerGetter extends GenericGetter<Integer> {}

    @Test
    public void a_known_result_is_checked_against_the_specialized_return_type() throws Exception {
        assertFalse(descriptor("() -> 1").fits(StringGetter.class));
        assertTrue(descriptor("() -> 1").fits(IntegerGetter.class));
        assertTrue(descriptor("() -> \"s\"").fits(StringGetter.class));
        assertTrue(descriptor("() -> \"s\"").fits(GenericGetter.class));
    }

    @Test
    public void ranking_sees_the_specialized_return_type() throws Exception {
        assertEquals(StringGetter.class, pick(descriptor("() -> \"s\""), IntegerGetter.class, StringGetter.class));
        assertEquals(IntegerGetter.class, pick(descriptor("() -> 1"), IntegerGetter.class, StringGetter.class));
    }

    // Two unrelated generic superinterfaces erasing get() to Object: javac
    // resolves both declaration orders to the narrower String (confirmed by
    // compiling the Java equivalent), so fits must not depend on which
    // getMethods() happens to visit first.
    public interface DiamondGetterA<T> { T get(); }
    public interface DiamondGetterB<T> { T get(); }
    public interface DiamondFirstA extends DiamondGetterA<String>, DiamondGetterB<CharSequence> {}
    public interface DiamondFirstB extends DiamondGetterB<CharSequence>, DiamondGetterA<String> {}

    @Test
    public void a_return_type_shared_by_two_generic_superinterfaces_does_not_depend_on_declaration_order() throws Exception {
        assertEquals(String.class, BshLambda.functionReturnType(DiamondFirstA.class));
        assertEquals(String.class, BshLambda.functionReturnType(DiamondFirstB.class));
    }

    // Round-1 regression: an array actual type's own getPackage() is always
    // null (arrays have none), so the module-export accessibility check added
    // for Finding 1 must consult the component type's package/module, not the
    // array class's -- otherwise every array actual type falls back to the
    // erasure, even one whose component (e.g. java.lang.String, in the named,
    // exported java.base module) is perfectly accessible.
    public interface StringArrayGetter extends GenericGetter<String[]> {}
    public interface IntArrayGetter extends GenericGetter<int[]> {}
    public interface StringArrayArrayGetter extends GenericGetter<String[][]> {}

    @Test
    public void an_array_actual_type_specializes_by_its_component_types_accessibility() throws Exception {
        assertEquals(String[].class, BshLambda.functionReturnType(StringArrayGetter.class));
        assertEquals(int[].class, BshLambda.functionReturnType(IntArrayGetter.class));
        assertEquals(String[][].class, BshLambda.functionReturnType(StringArrayArrayGetter.class));
    }
}
