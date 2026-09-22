/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
/****************************************************************************/
package bsh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import bsh.org.objectweb.asm.ClassWriter;
import bsh.org.objectweb.asm.Opcodes;

/** Confirm/deny harness for four "not reproduced" concurrency issues around
 * scripted-class lifecycle: #843 and #827 (pinning), #827 (a definition
 * race), and #829 (a lock-order-inversion deadlock). #828 turned out, on
 * measurement, not to be a real bug -- see the regression guard below. */
@RunWith(FilteredTestRunner.class)
public class ClassLifecycleConcurrencyTest {

    private static final AtomicInteger PROBE_SEQ = new AtomicInteger();

    private static final int RACE_THREADS = 8;
    private static final int RACE_PER_THREAD = 150 * Integer.getInteger("bsh.stress.scale", 1);
    private static final int RACE_NAME_SLOTS = 4;
    private static final int RACE_WAIT_SECONDS = 120;

    // ---- (a) #843: a scripted class, never instantiated, pins its interpreter ----

    @Test
    @Category(KnownIssue.class)
    public void scripted_class_pins_its_declaring_interpreter() throws Exception {
        WeakReference<Interpreter> ref = defineUninstantiatedClassAndDrop();
        boolean collected = TestUtil.awaitCollected(ref);
        // #843: the interpreter should be reclaimable once every local
        // reference to it is gone. It isn't -- flip this assertion to
        // assertTrue once #843 is fixed.
        assertFalse("interpreter was collected -- #843 appears fixed; flip this assertion to assertTrue",
            collected);
    }

    private static WeakReference<Interpreter> defineUninstantiatedClassAndDrop() throws Exception {
        Interpreter interpreter = new Interpreter();
        Object result = interpreter.eval("class Foo" + PROBE_SEQ.incrementAndGet() + " { }");
        WeakReference<Interpreter> ref = new WeakReference<>(interpreter);
        interpreter = null;
        result = null;
        return ref;
    }

    // ---- (b) #827 pin: defineClass() alone pins the class manager ----

    @Test
    @Category(KnownIssue.class)
    public void defining_a_class_directly_pins_its_class_manager() throws Exception {
        WeakReference<BshClassManager> ref = defineClassDirectlyAndDrop();
        boolean collected = TestUtil.awaitCollected(ref);
        // #827: the class manager (and via it, the declaring interpreter)
        // should be reclaimable once every local reference is gone. It
        // isn't -- flip this assertion to assertTrue once #827 is fixed.
        assertFalse("class manager was collected -- #827 pin appears fixed; flip this assertion to assertTrue",
            collected);
    }

    private static WeakReference<BshClassManager> defineClassDirectlyAndDrop() throws Exception {
        String internalName = "bsh/ProbeX" + PROBE_SEQ.incrementAndGet();
        Interpreter interpreter = new Interpreter();
        BshClassManager manager = interpreter.getClassManager();
        manager.defineClass(internalName.replace('/', '.'), minimalClassBytes(internalName));
        WeakReference<BshClassManager> ref = new WeakReference<>(manager);
        interpreter = null;
        manager = null;
        return ref;
    }

    /** @return bytes for a minimal, valid, loadable public class with no
     * members -- enough to exercise defineClass() without needing to
     * instantiate or invoke anything on the result. */
    private static byte[] minimalClassBytes(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ---- (c) #827 race: DiscreteFilesClassLoader.instance is a plain,
    // non-volatile static written and read back across two separate steps
    // (ClassManagerImpl.java:460,465), so two threads defining same-named
    // scripted types can interleave and hand each other's classes the wrong
    // loader. ----

    @Test
    @Category(KnownIssue.class)
    public void concurrent_fresh_interpreters_race_defining_classes() throws Exception {
        raceScriptedTypeDefinition("class",
            "class K%1$d { int v() { return 23; } } new K%1$d().v();");
    }

    @Test
    @Category(KnownIssue.class)
    public void concurrent_fresh_interpreters_race_defining_interfaces() throws Exception {
        raceScriptedTypeDefinition("iface",
            "interface Op%1$d { int ap(int a); } Op%1$d o = a -> a * 2; o.ap(11) + 1;");
    }

    /** Runs {@code scriptTemplate} (evaluating to 23 on success) on
     * {@value #RACE_THREADS} threads against a real start barrier, each
     * thread using a fresh {@link Interpreter} per iteration and cycling
     * through only {@value #RACE_NAME_SLOTS} distinct type names so that
     * names collide across threads -- the collision is what makes #827's
     * race fire. */
    private static void raceScriptedTypeDefinition(String mode, String scriptTemplate) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(RACE_THREADS);
        final AtomicInteger raceFailures = new AtomicInteger();
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Throwable> unexpected = new AtomicReference<>();
        final Thread[] threads = new Thread[RACE_THREADS];
        for (int t = 0; t < RACE_THREADS; t++) {
            final int id = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < RACE_PER_THREAD && unexpected.get() == null; i++) {
                        int slot = i % RACE_NAME_SLOTS;
                        String script = String.format(scriptTemplate, slot);
                        try {
                            Object result = new Interpreter().eval(script);
                            Object unwrapped = Primitive.unwrap(result);
                            if (!Integer.valueOf(23).equals(unwrapped)) {
                                raceFailures.incrementAndGet();
                                messages.add(mode + " thread " + id + " iter " + i
                                    + ": wrong result " + unwrapped);
                            }
                        } catch (Throwable e) {
                            if (isRaceRelatedFailure(e)) {
                                raceFailures.incrementAndGet();
                                messages.add(mode + " thread " + id + " iter " + i + ": " + e);
                            } else {
                                unexpected.compareAndSet(null, e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    unexpected.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            }, mode + "-race-" + id);
            threads[t].setDaemon(true);
            threads[t].start();
        }
        start.countDown();
        boolean finished = done.await(RACE_WAIT_SECONDS, TimeUnit.SECONDS);
        if (unexpected.get() != null)
            throw new AssertionError(mode + " race threw an unexpected exception", unexpected.get());
        if (!finished) {
            StringBuilder sb = new StringBuilder(mode + " race: threads still running after "
                + RACE_WAIT_SECONDS + "s; stuck at:");
            for (Thread thread : threads)
                if (thread.isAlive() && thread.getStackTrace().length > 0)
                    sb.append("\n    ").append(thread.getName())
                        .append(" -> ").append(thread.getStackTrace()[0]);
            throw new AssertionError(sb.toString());
        }
        int total = RACE_THREADS * RACE_PER_THREAD;
        assertTrue(mode + ": expected at least one duplicate-definition failure over "
                + total + " scripted definitions (#827) but observed none"
                + (messages.isEmpty() ? "" : "; messages: " + messages),
            raceFailures.get() > 0);
    }

    /** The interleaved read/write of {@code DiscreteFilesClassLoader.instance}
     * (ClassManagerImpl.java:460,465) can hand one thread's class name a
     * different thread's loader. Depending on exactly how the two threads'
     * iterations interleave that has surfaced, in practice, as either a
     * duplicate-definition {@link LinkageError} or a class that "vanishes"
     * because the wrong loader's source map does not contain it -- both are
     * the same root cause, just different symptoms of it. */
    private static boolean isRaceRelatedFailure(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof LinkageError || cause instanceof ClassNotFoundException)
                return true;
            String message = cause.getMessage();
            if (message != null && (message.contains("not found in namespace")
                    || message.contains("Unknown class")
                    || message.contains("duplicate class")
                    || message.contains("duplicate interface")
                    || message.contains("duplicate definition")))
                return true;
        }
        return false;
    }

    // ---- (d) #828 regression guard: NOT a KnownIssue. The original "an
    // interface's static field carries no ConstantValue so it never
    // initializes, so its contextStore entry leaks" theory was refuted by
    // measurement -- ClassGeneratorUtil.java:222 generates a real static
    // field for interfaces too, and reading it does initialize. This guards
    // against that regressing: once initialization is forced, an interface
    // must be exactly as collectable as a class. ----

    @Test
    public void scripted_interfaces_are_collectable_like_classes_once_initialized() throws Exception {
        assertCollectableOnceInitialized("class",
            "class Klass%1$d { int v() { return 1; } }", "Klass");
        assertCollectableOnceInitialized("interface",
            "interface Iface%1$d { int apply(int x); }", "Iface");
    }

    private static void assertCollectableOnceInitialized(String label, String scriptTemplate, String namePrefix)
            throws Exception {
        int id = PROBE_SEQ.incrementAndGet();
        String simpleName = namePrefix + id;
        Interpreter interpreter = new Interpreter();
        interpreter.eval(String.format(scriptTemplate, id));
        Class<?> generated = interpreter.getNameSpace().getClass(simpleName);
        assertNotNull(label + " was not generated", generated);
        // Force <clinit>, which calls This.pullBshStatic (This.java:910-917)
        // and removes the contextStore entry that would otherwise pin the
        // interpreter -- an entry legitimately survives until the type is
        // initialized, so a probe that skips this measures laziness, not a
        // leak.
        Class.forName(generated.getName(), true, generated.getClassLoader());
        WeakReference<Interpreter> ref = new WeakReference<>(interpreter);
        interpreter = null;
        generated = null;

        // #827 (above) separately pins whichever loader most recently defined
        // a scripted type, via the non-volatile DiscreteFilesClassLoader
        // instance static. Displace it with an unrelated definition so this
        // probe measures only #828's question, not #827's.
        int displacerId = PROBE_SEQ.incrementAndGet();
        Interpreter displacer = new Interpreter();
        displacer.eval("class Displacer" + displacerId + " { } new Displacer" + displacerId + "();");
        displacer = null;

        assertTrue(label + " was not collected once initialized", TestUtil.awaitCollected(ref));
    }

    // ---- (e) #829 (fixed): FieldAccess.invoke (Invocable.java:601) used to
    // be synchronized on a single JVM-wide FieldAccess instance (cached in
    // BshClassManager.memberCache) and performed the class-initializing
    // GETSTATIC inside that monitor. This.initStatic (This.java:881) took
    // the same monitor from inside <clinit>. A thread that took the
    // FieldAccess monitor first, then itself tried to initialize the class,
    // deadlocked against a thread already running <clinit> for that class.
    // FieldAccess.invoke now resolves its MethodHandle under a private lock
    // and invokes it outside any lock, so external code holding an
    // Invocable's own monitor -- as this test deliberately does, to
    // reproduce the original cycle -- can no longer wedge bsh's own field
    // access at all. This test proves that: both threads now complete
    // cleanly, every time. ----

    @Test(timeout = 20000)
    public void static_field_access_and_class_init_deadlock_on_lock_order_inversion() throws Exception {
        int id = PROBE_SEQ.incrementAndGet();
        String simpleName = "Op" + id;
        Interpreter interpreter = new Interpreter();
        interpreter.eval("class " + simpleName + " { }");
        Class<?> generated = interpreter.getNameSpace().getClass(simpleName);
        assertNotNull("class was not generated", generated);

        Invocable fieldAccess = BshClassManager.memberCache.get(generated)
            .findField(This.Keys.BSHSTATIC.toString() + simpleName);
        assertNotNull("static field access not found", fieldAccess);

        CountDownLatch holderEntered = new CountDownLatch(1);

        // Thread B: waits for the holder to take the FieldAccess monitor,
        // then initializes the class -- acquiring the JVM's class-init lock
        // and, inside <clinit>, calling This.initStatic -> getClassStaticThis
        // -> FieldAccess.invoke. That call no longer needs the FieldAccess
        // monitor at all, so it no longer contends with the holder thread.
        Thread initializer = new Thread(() -> {
            try {
                holderEntered.await();
                Class.forName(generated.getName(), true, generated.getClassLoader());
            } catch (Throwable ignored) {
                // any failure here just means no deadlock occurred; the
                // assertions below are what determine pass/fail
            }
        }, "op-initializer-" + id);

        // Thread A: takes the FieldAccess object's own monitor first --
        // external to bsh, exactly what a caller with a reference to the
        // Invocable could do -- waits for B to either block on it or finish,
        // then itself tries to initialize the same class. Since bsh's own
        // field access no longer uses this monitor, B never blocks on it and
        // this wait just falls through once B completes.
        Thread holder = new Thread(() -> {
            synchronized (fieldAccess) {
                holderEntered.countDown();
                while (initializer.getState() != Thread.State.BLOCKED && initializer.isAlive())
                    Thread.yield();
                try {
                    Class.forName(generated.getName(), true, generated.getClassLoader());
                } catch (Throwable ignored) {
                    // see above
                }
            }
        }, "op-holder-" + id);

        initializer.setDaemon(true);
        holder.setDaemon(true);
        // Started in this order so that, by the time holder's busy-wait can
        // possibly run, initializer.isAlive() is already guaranteed true --
        // Thread.start() makes a thread alive before it returns, regardless
        // of when the OS actually schedules it. Starting holder first would
        // let its busy-wait read a not-yet-started initializer as "isAlive()
        // == false" -- indistinguishable from "already finished" -- and fall
        // through immediately, letting holder initialize the class itself
        // (reentrantly, no deadlock) before initializer ever contends.
        initializer.start();
        holder.start();

        holder.join(5000);
        initializer.join(5000);

        boolean deadlocked = holder.isAlive() && initializer.isAlive();
        String dump = deadlocked ? dumpAllThreads() : "";
        // #829 fixed: both threads join cleanly, every time. Both are
        // daemon so, if the deadlock somehow reappeared, they'd stay
        // blocked for the remainder of this fork's life by design -- this
        // test's fork exists to contain exactly that.
        assertFalse("holder thread did not join -- deadlock reappeared; thread dump:\n" + dump,
            holder.isAlive());
        assertFalse("initializer thread did not join -- deadlock reappeared; thread dump:\n" + dump,
            initializer.isAlive());
    }

    // ---- (f) #829 regression guard: an unforced, real-world repro. Rather
    // than manufacturing the deadlock by grabbing a FieldAccess's monitor
    // from test code (as (e) above does), this drives ordinary concurrent
    // cascade class regeneration against one shared Interpreter/
    // BshClassManager -- the scenario the original deadlock was actually
    // found in. It is a liveness check only: LinkageErrors and other eval
    // failures from racing redefinitions are expected and ignored; the only
    // thing this test cares about is that every thread finishes. ----

    private static final int CASCADE_THREADS = 8;
    private static final int CASCADE_ITERATIONS = 300;
    private static final int CASCADE_SLOTS = 4;
    private static final int CASCADE_WAIT_SECONDS = 15;

    @Test
    public void concurrent_cascade_class_regeneration_does_not_deadlock_on_field_access() throws Exception {
        int uid = PROBE_SEQ.incrementAndGet();
        final Interpreter interpreter = new Interpreter();

        // Single-threaded setup: 4 independent 3-level hierarchies.
        for (int slot = 0; slot < CASCADE_SLOTS; slot++) {
            interpreter.eval("class " + cascadeBaseName(uid, slot) + " { int v = 0; }");
            interpreter.eval("class " + cascadeLevelName(uid, slot, 1) + " extends "
                + cascadeBaseName(uid, slot) + " { int v = 0; }");
            interpreter.eval("class " + cascadeLevelName(uid, slot, 2) + " extends "
                + cascadeLevelName(uid, slot, 1) + " { int v = 0; }");
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CASCADE_THREADS);
        Thread[] threads = new Thread[CASCADE_THREADS];
        for (int t = 0; t < CASCADE_THREADS; t++) {
            final int id = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < CASCADE_ITERATIONS; i++) {
                        int slot = (id + i) % CASCADE_SLOTS;
                        int level = (id * 7 + i) % 3;
                        String script;
                        if (level == 0)
                            script = "class " + cascadeBaseName(uid, slot) + " { int v = " + i + "; }";
                        else
                            script = "class " + cascadeLevelName(uid, slot, level) + " extends "
                                + (level == 1 ? cascadeBaseName(uid, slot) : cascadeLevelName(uid, slot, level - 1))
                                + " { int v = " + i + "; }";
                        try {
                            interpreter.eval(script);
                        } catch (Throwable ignored) {
                            // concurrent redefinition legitimately throws
                            // LinkageError/eval errors sometimes -- that's
                            // expected and not what this test checks; it
                            // only checks for hangs
                        }
                    }
                } catch (InterruptedException ignored) {
                    // start latch interrupted -- fall through to done.countDown()
                } finally {
                    done.countDown();
                }
            }, "cascade-race-" + id);
            threads[t].setDaemon(true);
            threads[t].start();
        }
        start.countDown();
        boolean finished = done.await(CASCADE_WAIT_SECONDS, TimeUnit.SECONDS);
        if (!finished)
            fail("cascade class regeneration: threads still running after "
                + CASCADE_WAIT_SECONDS + "s; thread dump:\n" + dumpAllThreads());
    }

    private static String cascadeBaseName(int uid, int slot) {
        return "CascadeBase" + uid + "_" + slot;
    }

    private static String cascadeLevelName(int uid, int slot, int level) {
        return "CascadeL" + uid + "_" + slot + "_" + level;
    }

    private static String dumpAllThreads() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            sb.append(entry.getKey().getName()).append(" (").append(entry.getKey().getState()).append(")\n");
            for (StackTraceElement frame : entry.getValue())
                sb.append("    at ").append(frame).append('\n');
        }
        return sb.toString();
    }
}
