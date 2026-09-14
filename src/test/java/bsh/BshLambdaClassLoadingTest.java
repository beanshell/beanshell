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
