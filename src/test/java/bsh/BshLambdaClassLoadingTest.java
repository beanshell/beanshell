package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.Opcodes;

/**
    Lambda wrappers must link against the interface's own class loader, and
    must not keep bsh's loader or an interpreter's class manager alive.
*/
@RunWith(FilteredTestRunner.class)
public class BshLambdaClassLoadingTest {

    /** A loader bsh's own loader cannot see into. */
    private static final class ChildLoader extends ClassLoader {
        ChildLoader() {
            super(BshLambdaClassLoadingTest.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    private static byte[] interfaceBytes(String internalName, String method, String descriptor) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            internalName, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, method, descriptor, null, null)
            .visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] classBytes(String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        bsh.org.objectweb.asm.MethodVisitor mv =
            cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Object materialize(Interpreter interpreter, String lambda, Class<?> iface)
            throws Exception {
        return Types.castObject(interpreter.eval(lambda), iface, Types.CAST);
    }

    @Test
    public void interface_invisible_to_bsh_is_implemented() throws Exception {
        Class<?> iface = new ChildLoader().define(
            interfaceBytes("hidden/Doubler", "apply", "(I)I"));
        Object doubler = materialize(new Interpreter(), "x -> x * 2;", iface);
        Method apply = iface.getMethod("apply", int.class);
        assertEquals(42, apply.invoke(doubler, 21));
    }

    @Test
    public void same_named_interfaces_in_sibling_loaders_each_get_their_own_wrapper()
            throws Exception {
        byte[] bytes = interfaceBytes("sibling/Same", "get", "()I");
        Class<?> first = new ChildLoader().define(bytes);
        Class<?> second = new ChildLoader().define(bytes);
        Interpreter interpreter = new Interpreter();
        assertTrue(first.isInstance(materialize(interpreter, "() -> 1;", first)));
        assertTrue(second.isInstance(materialize(interpreter, "() -> 2;", second)));
    }

    @Test
    public void sam_return_type_visible_only_to_the_interface_loader() throws Exception {
        ChildLoader loader = new ChildLoader();
        Class<?> product = loader.define(classBytes("only/Product"));
        Class<?> maker = loader.define(interfaceBytes("only/Maker", "make", "()Lonly/Product;"));
        Interpreter interpreter = new Interpreter();
        Object made = product.getConstructor().newInstance();
        interpreter.set("made", made);
        Object m = materialize(interpreter, "() -> made;", maker);
        assertEquals(made, maker.getMethod("make").invoke(m));
    }

    @Test
    public void scripted_interface_wrapper_links_against_the_interface_loader() throws Exception {
        Interpreter interpreter = new Interpreter();
        Object d = interpreter.eval("interface Doubler { int apply(int x); } (Doubler) x -> x * 2;");
        Class<?> doubler = interpreter.eval("Doubler.class") instanceof Class
            ? (Class<?>) interpreter.eval("Doubler.class") : null;
        assertEquals(doubler.getClassLoader(), d.getClass().getClassLoader().getParent());
    }

    public interface CharSequenceMaker { CharSequence make(String s); }
    public interface StringMaker { String make(String s); }
    public interface CovariantMaker extends CharSequenceMaker, StringMaker {}

    // javac accepts this lambda; the wrapper must answer both descriptors.
    @Test
    public void covariant_diamond_implements_every_inherited_descriptor() throws Exception {
        Object maker = new Interpreter().eval(
            "import bsh.BshLambdaClassLoadingTest.CovariantMaker; (CovariantMaker) s -> s + \"!\";");
        assertEquals("x!", ((StringMaker) maker).make("x"));
        assertEquals("y!", ((CharSequenceMaker) maker).make("y"));
    }

    public interface SerializableMaker { java.io.Serializable make(String s); }
    public interface ComparableMaker { Comparable<?> make(String s); }
    public interface UnrelatedFirstMaker extends SerializableMaker, ComparableMaker, StringMaker {}
    public interface SubstitutableFirstMaker extends StringMaker, ComparableMaker, SerializableMaker {}

    // Serializable and Comparable are unrelated, but String substitutes for both.
    @Test
    public void diamond_with_unrelated_returns_is_functional_in_any_order() throws Exception {
        for (Class<?> iface : new Class<?>[] { UnrelatedFirstMaker.class, SubstitutableFirstMaker.class }) {
            Object maker = new Interpreter().eval(
                "import " + iface.getName().replace('$', '.') + "; (" + iface.getSimpleName() + ") s -> s + \"!\";");
            assertEquals("a!", ((StringMaker) maker).make("a"));
            assertEquals("b!", ((ComparableMaker) maker).make("b"));
            assertEquals("c!", ((SerializableMaker) maker).make("c"));
        }
    }

    public interface TwoRunners { void first(); void second(); }
    public interface TwoOverloads { void run(String s); void run(Integer i); }

    @Test
    public void several_abstract_methods_with_one_return_type_are_not_functional() throws Exception {
        for (Class<?> iface : new Class<?>[] { TwoRunners.class, TwoOverloads.class }) {
            try {
                materialize(new Interpreter(), iface == TwoRunners.class ? "() -> {};" : "x -> {};", iface);
                org.junit.Assert.fail("expected a UtilEvalError for " + iface);
            } catch (UtilEvalError expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
            }
        }
    }

    // javac rejects this interface, but bytecode can define it.
    @Test
    public void diamond_without_a_substitutable_return_is_not_functional() throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            "unrelated/Both", null, "java/lang/Object", new String[] {
                SerializableMaker.class.getName().replace('.', '/'),
                ComparableMaker.class.getName().replace('.', '/') });
        cw.visitEnd();
        Class<?> both = new ChildLoader().define(cw.toByteArray());
        try {
            materialize(new Interpreter(), "s -> s;", both);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface GenericSam<T> { void m(T x); }
    public interface StringSam { void m(String x); }
    public interface GenericDiamond extends GenericSam<String>, StringSam {}

    // GenericSam<String>'s erased m(Object) and StringSam's m(String) are the
    // same method once T is substituted with String as GenericDiamond sees it.
    @Test
    public void a_generic_sam_diamond_with_a_consistent_substitution_is_functional() throws Exception {
        assertEquals("x", new Interpreter().eval(
            "import bsh.BshLambdaClassLoadingTest.GenericDiamond;"
            + " r = null; ((GenericDiamond) (String s) -> { r = s; }).m(\"x\"); r;"));
    }

    // The wrapper must answer both inherited descriptors, exactly like the
    // non-generic covariant diamond case above -- discovery picking one
    // method as "the" SAM must not affect what the wrapper implements.
    @Test
    @SuppressWarnings("rawtypes")
    public void a_generic_sam_diamond_wrapper_answers_both_inherited_descriptors() throws Exception {
        Interpreter interpreter = new Interpreter();
        Object obj = interpreter.eval(
            "import bsh.BshLambdaClassLoadingTest.GenericDiamond;"
            + " r = null; (GenericDiamond) (String s) -> { r = s; };");
        ((StringSam) obj).m("a");
        assertEquals("a", interpreter.get("r"));
        ((GenericSam) obj).m("b");
        assertEquals("b", interpreter.get("r"));
    }

    public interface StringTaker { void take(String s); }
    public interface IntTaker { void take(int i); }
    public interface UnrelatedTakerDiamond extends StringTaker, IntTaker {}

    // Two genuinely unrelated (non-generic) interfaces must still be rejected:
    // substitutedParameterTypes returns plain erasure for non-generic methods,
    // so the equality check still fails on its own.
    @Test
    public void two_unrelated_interfaces_with_same_arity_different_types_are_still_rejected() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", UnrelatedTakerDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface Mid<T> extends GenericSam<T> {}
    public interface DeepDiamond extends Mid<String>, StringSam {}

    // Mid<String> is DeepDiamond's direct ancestor, not GenericSam: a
    // single-level lookup for GenericSam's declaring class finds nothing,
    // so substitution is unresolvable and this correctly falls back to rejection.
    @Test
    public void a_diamond_with_a_second_level_generic_ancestor_falls_back_to_rejection() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", DeepDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface PartialDiamond<T> extends GenericSam<T>, StringSam {}

    // PartialDiamond's own type variable T is not a concrete Class, so
    // directSubstitution's actual-type-argument check correctly bails out.
    @Test
    public void a_diamond_whose_type_variable_is_not_directly_substituted_is_not_functional() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", PartialDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    @SuppressWarnings("rawtypes")
    public interface RawDiamond extends GenericSam, StringSam {}

    // A raw ancestor reports as a plain Class, not a ParameterizedType, so
    // directSubstitution correctly finds no substitution to apply.
    @Test
    public void a_diamond_with_a_raw_generic_ancestor_is_not_functional() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", RawDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface BoundedSam<T extends CharSequence> { void m(T x); }
    public interface BoundedDiamond extends BoundedSam<String>, StringSam {}

    // A bound on the type variable doesn't block single-level substitution:
    // the actual type argument is still a concrete Class either way.
    @Test
    public void a_bounded_type_variable_still_resolves_via_direct_substitution() throws Exception {
        assertEquals("x", new Interpreter().eval(
            "import bsh.BshLambdaClassLoadingTest.BoundedDiamond;"
            + " r = null; ((BoundedDiamond) (String s) -> { r = s; }).m(\"x\"); r;"));
    }

    public interface IntSam { void m(int x); }
    public interface BoxedDiamond extends GenericSam<Integer>, IntSam {}

    // T substituted with Integer must not be conflated with a primitive int
    // parameter: Integer.class != int.class, so this is correctly rejected.
    @Test
    public void a_boxed_type_variable_does_not_match_a_primitive_parameter() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", BoxedDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface GenericArraySam<T> { void m(T[] x); }
    public interface StringArraySam { void m(String[] x); }
    public interface ArrayDiamond extends GenericArraySam<String>, StringArraySam {}

    // T[] reflects as a GenericArrayType, not a TypeVariable, so it is never
    // recognized as needing substitution and falls back to plain erasure
    // comparison (Object[] vs String[]) -- out of scope, correctly rejected.
    @Test
    public void a_diamond_with_a_generic_array_substitution_falls_back_to_rejection() throws Exception {
        try {
            materialize(new Interpreter(), "s -> {};", ArrayDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not a functional interface"));
        }
    }

    public interface ObjectReturner { Object get(); }
    public interface CharSequenceReturner { CharSequence get(); }
    public interface ObjectFirstDiamond extends ObjectReturner, CharSequenceReturner {}
    public interface CharSequenceFirstDiamond extends CharSequenceReturner, ObjectReturner {}

    // mostConcrete must not let getMethods()'s declaration-order-dependent
    // iteration override the already-correct, order-independent return-type
    // candidate for an ordinary (non-generic, zero-parameter) diamond.
    @Test
    public void most_concrete_return_type_selection_is_order_independent() throws Exception {
        for (Class<?> iface : new Class<?>[] { ObjectFirstDiamond.class, CharSequenceFirstDiamond.class })
            assertEquals("wrong sam picked for " + iface,
                CharSequence.class, BshLambda.singleAbstractMethod(iface).getReturnType());
    }

    // discoverSingleAbstractMethod must not crash (methods.get(0) on an
    // empty list) for an interface with zero abstract methods.
    @Test
    public void a_marker_interface_with_no_abstract_methods_is_not_functional() throws Exception {
        assertNull(BshLambda.singleAbstractMethod(java.io.Serializable.class));
    }

    public interface CsFromGeneric<T> { CharSequence m(T x); }
    public interface ObjFromString { Object m(String x); }
    public interface WeakeningDiamond extends CsFromGeneric<String>, ObjFromString {}

    // javac resolves this diamond's functional descriptor to the more
    // specific CharSequence-returning branch and rejects a body that
    // doesn't fit it at compile time; mostConcrete's swap search must never
    // demote that already-correct return type by preferring a "concrete"
    // sibling with a less specific one, or bsh would wrongly accept an
    // int-returning body here and only fail later, confusingly, at call time.
    @Test
    @SuppressWarnings("unchecked")
    public void a_diamond_swap_never_demotes_the_optimal_return_type() throws Exception {
        assertEquals(CharSequence.class,
            BshLambda.singleAbstractMethod(WeakeningDiamond.class).getReturnType());
        Object obj = materialize(new Interpreter(), "s -> \"ok\";", WeakeningDiamond.class);
        assertEquals("ok", ((CsFromGeneric<String>) obj).m("x"));
        try {
            materialize(new Interpreter(), "s -> 1;", WeakeningDiamond.class);
            org.junit.Assert.fail("expected a UtilEvalError: int is not a CharSequence");
        } catch (UtilEvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("does not fit"));
        }
    }

    public interface ObjectSam { void m(Object x); }
    public interface ErasureEqualDiamond extends GenericSam<Object>, ObjectSam {}

    // GenericSam<Object>'s m and ObjectSam's m already share one erasure, so
    // mostConcrete has nothing useful to resolve here and must leave the
    // generic branch in place: LambdaDescriptor.fits's protected lenient-match
    // rule (a SAM parameter that still mentions a type variable accepts any
    // compatible declared lambda parameter type, not just an exact one) keys
    // off of which method stays "the" SAM, and this task must not narrow it.
    @Test
    public void an_erasure_equal_diamond_keeps_its_lenient_generic_match() throws Exception {
        assertEquals("x", new Interpreter().eval(
            "import bsh.BshLambdaClassLoadingTest.ErasureEqualDiamond;"
            + " r = null; ((ErasureEqualDiamond) (String s) -> { r = s; }).m(\"x\"); r;"));
    }

    // A generic Signature attribute naming a type absent from the classpath
    // (an optional dependency missing at runtime) throws TypeNotPresentException
    // from Method.getGenericParameterTypes() -- the same family
    // LambdaDescriptor.fits already guards its identical call against.
    // discoverSingleAbstractMethod must not crash on it either, for a
    // perfectly ordinary, non-diamond interface.
    @Test
    public void a_generic_signature_naming_a_missing_type_does_not_crash_discovery() throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            "missing/Taker", null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "take", "(Ljava/util/List;)V",
            "(Ljava/util/List<Lmissing/NoSuchType;>;)V", null).visitEnd();
        cw.visitEnd();
        Class<?> iface = new ChildLoader().define(cw.toByteArray());
        Object taker = materialize(new Interpreter(), "x -> {};", iface);
        assertTrue(iface.isInstance(taker));
    }

    static final class PackagePrivateProduct {}
    public interface MakesPackagePrivate { PackagePrivateProduct make(); }

    // The wrapper lives in another runtime package, so it cannot name this type.
    @Test
    public void non_public_sam_return_type_fails_at_conversion() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaClassLoadingTest.MakesPackagePrivate; (MakesPackagePrivate) () -> null;");
            org.junit.Assert.fail("expected an EvalError");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not public"));
        }
    }

    interface PackagePrivateParam {}
    public interface TakesPackagePrivateParam { void accept(PackagePrivateParam p); }

    // The wrapper lives in another runtime package, so it cannot name a parameter type.
    @Test
    public void a_public_interface_with_a_non_public_parameter_type_is_rejected() throws Exception {
        try {
            new Interpreter().eval(
                "import bsh.BshLambdaClassLoadingTest.TakesPackagePrivateParam;"
                + " (TakesPackagePrivateParam) p -> {};");
            org.junit.Assert.fail("expected an EvalError: the parameter type is not public");
        } catch (EvalError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("PackagePrivateParam"));
        }
    }

    // A wrapper must run with bsh's permissions, not an empty ProtectionDomain.
    @Test
    public void wrapper_shares_bsh_protection_domain() throws Exception {
        Object r = new Interpreter().eval("(Runnable) () -> {};");
        assertEquals(BshLambda.class.getProtectionDomain(), r.getClass().getProtectionDomain());
    }

    /** Not parallel capable: loadClass locks the loader itself. */
    private static final class LockingLoader extends ClassLoader {
        LockingLoader() {
            super(BshLambdaClassLoadingTest.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }

    // Thread A holds an interface loader's lock while converting to Runnable;
    // thread B's conversion needs that loader. Unrelated conversions must not
    // wait on each other, or the two threads deadlock.
    @Test(timeout = 30000)
    public void conversions_for_different_interfaces_do_not_share_a_lock() throws Exception {
        LockingLoader loader = new LockingLoader();
        Class<?> iface = loader.define(interfaceBytes("locked/Op", "get", "()I"));
        Interpreter interpreter = new Interpreter();
        Object lambdaForA = interpreter.eval("() -> {};");
        Object lambdaForB = interpreter.eval("() -> 1;");
        BshLambda.singleAbstractMethod(iface);
        BshLambda.singleAbstractMethod(Runnable.class);

        Thread b = new Thread(() -> {
            try {
                Types.castObject(lambdaForB, iface, Types.CAST);
            } catch (UtilEvalError e) {
                throw new RuntimeException(e);
            }
        });
        Thread a = new Thread(() -> {
            synchronized (loader) {
                b.start();
                while (b.getState() != Thread.State.BLOCKED && b.isAlive())
                    Thread.yield();
                try {
                    Types.castObject(lambdaForA, Runnable.class, Types.CAST);
                } catch (UtilEvalError e) {
                    throw new RuntimeException(e);
                }
            }
        });
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        a.join(10000);
        b.join(10000);
        long[] deadlocked = java.lang.management.ManagementFactory.getThreadMXBean()
            .findDeadlockedThreads();
        assertNull("deadlocked threads", deadlocked);
        assertTrue(!a.isAlive() && !b.isAlive());
    }

    // bsh loaded by a loader its host later drops (app servers, plugin hosts)
    // must be collectable after a script used a lambda for a JDK interface.
    @Test
    public void throwaway_bsh_loader_is_collectable_after_a_lambda() throws Exception {
        WeakReference<ClassLoader> ref = runInThrowawayLoader(
            "Runnable r = () -> {}; r.run(); java.util.function.Function f = x -> x; f.apply(3);");
        for (int n = 0; n < 50 && ref.get() != null; n++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull("bsh's class loader still reachable", ref.get());
    }

    @Test
    public void throwaway_bsh_loader_is_collectable_after_resolving_a_lambda() throws Exception {
        WeakReference<ClassLoader> ref = runInThrowawayLoader(
            "m(java.util.function.IntSupplier s) { return s.getAsInt(); }"
            + " m(java.util.function.Supplier s) { return s.get(); } foo() { return 1; } m(() -> 1); m(() -> foo());");
        for (int n = 0; n < 50 && ref.get() != null; n++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull("bsh's class loader still reachable", ref.get());
    }

    @Test
    public void markers_do_not_accumulate_when_a_lambda_is_resolved_in_a_loop() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("m(Runnable r) { } m(java.util.concurrent.Callable c) { }"
            + " n(java.util.function.Consumer d) { } n(Runnable e) { }");
        long before = BshLambda.markersDefined();
        interpreter.eval("for (i = 0; i < 10000; i++) { m(() -> 7654321); n((String s) -> { }); }");
        assertTrue("markers defined: " + (BshLambda.markersDefined() - before),
            BshLambda.markersDefined() - before <= 2);
    }

    @Test
    public void marker_is_collectable_once_its_lambda_is_unreachable() throws Exception {
        WeakReference<Class<?>> ref = new WeakReference<>(
            ((BshLambda) new Interpreter().eval("() -> 918273645L;")).marker());
        for (int n = 0; n < 50 && ref.get() != null; n++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull("marker still reachable", ref.get());
    }

    @Test
    public void equal_descriptors_share_one_marker_across_racing_threads() throws Exception {
        for (int round = 0; round < 20; round++) {
            String lambda = "() -> " + (5000000000L + round) + "L;";
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Class<?>>> markers = new java.util.ArrayList<>();
            java.util.List<Object> keep = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
            try {
                for (int t = 0; t < 16; t++)
                    markers.add(pool.submit(() -> {
                        BshLambda each = (BshLambda) new Interpreter().eval(lambda);
                        keep.add(each);
                        start.await();
                        return each.marker();
                    }));
                start.countDown();
                java.util.Set<Class<?>> distinct = new java.util.HashSet<>();
                for (java.util.concurrent.Future<Class<?>> marker : markers)
                    distinct.add(marker.get());
                assertEquals(lambda, 1, distinct.size());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static WeakReference<ClassLoader> runInThrowawayLoader(String script) throws Exception {
        URL classes = Interpreter.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader loader = new URLClassLoader(new URL[] { classes },
            ClassLoader.getSystemClassLoader().getParent());
        Class<?> interpreterClass = loader.loadClass("bsh.Interpreter");
        Object interpreter = interpreterClass.getConstructor().newInstance();
        interpreterClass.getMethod("eval", String.class).invoke(interpreter, script);
        loader.close();
        return new WeakReference<>(loader);
    }
}
