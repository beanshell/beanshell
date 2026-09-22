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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/** Regression-pinning harness for #881 -- concurrent {@code eval()} calls
 * against a single, shared {@link Interpreter} are not thread-safe. Three
 * distinct, independently confirmed mechanisms, each a check-then-act or
 * unsafe-publication race on state a fresh {@code Interpreter}'s global
 * {@link NameSpace} owns as ordinary (non-static, non-volatile) instance
 * fields -- {@code NameSpace.variables} and {@code NameSpace.names} are
 * plain {@code HashMap}s per {@code NameSpace} instance, and
 * {@code BSHTypedVariableDeclaration.bvda} is a plain field per shared AST
 * node -- not JVM-wide statics the way {@link SharedCacheConcurrencyTest}'s
 * caches are. Unlike that class, a race here also cannot spin forever: every
 * observed failure surfaces as a thrown {@code EvalError}/{@code
 * AssertionError} on the racing thread, which still reaches its {@code
 * finally} block and counts down normally, so a failure here cannot wedge a
 * thread for the rest of the fork's life. That means this class needs
 * neither its own Surefire execution/fork nor a bounded {@code
 * forkedProcessTimeoutInSeconds} -- a generous per-test {@code @Test(timeout
 * = ...)} is enough.
 *
 * <p>Each test below asserts the CORRECT, race-free outcome, so today -- with
 * #881 open -- each fails intermittently (narrow race windows: the exact
 * failure rate varies by mechanism and machine). Once the underlying races
 * are fixed, removing the {@code @Category(KnownIssue.class)} annotation
 * turns each into an ordinary regression guard. */
@RunWith(FilteredTestRunner.class)
public class SharedInterpreterEvalConcurrencyTest {

    private static final int THREADS = 16;

    // ---- Mechanism 1: BSHTypedVariableDeclaration.getDeclarators()
    // publishes its cached bvda field unsafely (BSHTypedVariableDeclaration
    // .java:48-60) -- a scripted method's body AST is parsed once and shared
    // across every invocation, so concurrent first invocations of the same
    // method can observe a partially-published declarator array and NPE. ----

    @Test(timeout = 300000)
    @Category(KnownIssue.class)
    public void concurrent_first_invocation_of_a_shared_scripted_method_does_not_npe() throws Exception {
        final int outerIterations = 500;
        for (int outer = 0; outer < outerIterations; outer++) {
            final Interpreter interpreter = new Interpreter();
            interpreter.eval("int m() { int v = 1; v = v + 1; return v; }");

            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(THREADS);
            final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            Thread[] threads = new Thread[THREADS];
            for (int t = 0; t < THREADS; t++) {
                threads[t] = new Thread(() -> {
                    try {
                        start.await();
                        Object v = interpreter.eval("m();");
                        // #881: once fixed, this should always evaluate to 2 --
                        // today, a narrow unsafe-publication window can instead
                        // NPE inside BSHTypedVariableDeclaration.eval().
                        assertEquals("m() should evaluate to 2 once #881 is fixed",
                            Integer.valueOf(2), v);
                    } catch (Throwable e) {
                        firstFailure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                }, "mech1-" + outer + "-" + t);
                threads[t].setDaemon(true);
                threads[t].start();
            }
            start.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            if (!finished)
                fail("mech1 outer iteration " + outer + ": threads still running after 30s; stuck at:\n"
                    + stuckThreadsReport(threads));
            Throwable f = firstFailure.get();
            if (f != null)
                throw new AssertionError(
                    "concurrent first invocation of a shared scripted method raced on outer iteration "
                        + outer + " of " + outerIterations
                        + " -- see #881 (BSHTypedVariableDeclaration unsafe publication)", f);
        }
    }

    // ---- Mechanism 2: NameSpace.getNameResolver() does a check-then-act
    // (containsKey/put/get) on the shared `names` HashMap (NameSpace.java:
    // 1421-1425) -- concurrent first resolutions of distinct names can lose
    // an entry, leaving BSHAmbiguousName.getName() to return null. ----

    @Test(timeout = 300000)
    @Category(KnownIssue.class)
    public void concurrent_first_resolution_of_many_names_does_not_npe() throws Exception {
        final int outerIterations = 300;
        final int namesPerIteration = 200;
        for (int outer = 0; outer < outerIterations; outer++) {
            final Interpreter interpreter = new Interpreter();
            for (int i = 0; i < namesPerIteration; i++)
                interpreter.set("n" + i, i);

            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(THREADS);
            final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            Thread[] threads = new Thread[THREADS];
            for (int t = 0; t < THREADS; t++) {
                threads[t] = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < namesPerIteration; i++) {
                            Object v = interpreter.eval("return n" + i + ";");
                            // #881: once fixed, every name should resolve every
                            // time -- today, the getNameResolver() cache race
                            // can leave a name unresolvable on its first lookup.
                            assertNotNull("n" + i + " should resolve to a non-null value once #881 is fixed",
                                v);
                        }
                    } catch (Throwable e) {
                        firstFailure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                }, "mech2-" + outer + "-" + t);
                threads[t].setDaemon(true);
                threads[t].start();
            }
            start.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            if (!finished)
                fail("mech2 outer iteration " + outer + ": threads still running after 30s; stuck at:\n"
                    + stuckThreadsReport(threads));
            Throwable f = firstFailure.get();
            if (f != null)
                throw new AssertionError(
                    "concurrent first resolution of many names raced on outer iteration "
                        + outer + " of " + outerIterations
                        + " -- see #881 (NameSpace.getNameResolver() HashMap race)", f);
        }
    }

    // ---- Mechanism 3: NameSpace.getVariableImpl()/setVariableImpl() do a
    // check-then-act on the shared `variables` HashMap (NameSpace.java:
    // 656-671) -- even when every thread declares and reads back only its
    // OWN distinct variables, the underlying HashMap structural mutation is
    // unsynchronized, so a thread's own just-declared variable can appear
    // "undefined" to its very next statement. This mechanism reproduced
    // overwhelmingly (299/300 in the original driver), so it needs far fewer
    // outer iterations to prove reliably -- but still several, not one shot,
    // so a single lucky pass can't make the test flaky under
    // -Dskip_known_issues=false. ----

    @Test(timeout = 120000)
    @Category(KnownIssue.class)
    public void concurrent_variable_declaration_in_shared_namespace_does_not_fail() throws Exception {
        final int outerIterations = 30;
        final int itersPerThread = 30;
        for (int outer = 0; outer < outerIterations; outer++) {
            final Interpreter interpreter = new Interpreter();
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(THREADS);
            final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            Thread[] threads = new Thread[THREADS];
            for (int t = 0; t < THREADS; t++) {
                final int id = t;
                threads[t] = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < itersPerThread; i++) {
                            String name = "v" + id + "_" + i;
                            Object v = interpreter.eval(
                                "int " + name + " = " + i + "; " + name + " + 1;");
                            // #881: once fixed, a thread declaring and
                            // immediately reading back its own distinct
                            // variable should always see it -- today, the
                            // unsynchronized `variables` HashMap can lose or
                            // hide the just-declared entry from the very next
                            // statement in the same thread.
                            assertEquals(name + " should read back correctly once #881 is fixed",
                                Integer.valueOf(i + 1), v);
                        }
                    } catch (Throwable e) {
                        firstFailure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                }, "mech3-" + outer + "-" + id);
                threads[t].setDaemon(true);
                threads[t].start();
            }
            start.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            if (!finished)
                fail("mech3 outer iteration " + outer + ": threads still running after 30s; stuck at:\n"
                    + stuckThreadsReport(threads));
            Throwable f = firstFailure.get();
            if (f != null)
                throw new AssertionError(
                    "concurrent declaration of distinct variables in a shared namespace raced on outer iteration "
                        + outer + " of " + outerIterations
                        + " -- see #881 (NameSpace.getVariableImpl()/setVariableImpl() HashMap race)", f);
        }
    }

    private static String stuckThreadsReport(Thread[] threads) {
        StringBuilder sb = new StringBuilder();
        for (Thread thread : threads)
            if (thread.isAlive() && thread.getStackTrace().length > 0)
                sb.append("    ").append(thread.getName())
                    .append(" -> ").append(thread.getStackTrace()[0]).append('\n');
        return sb.toString();
    }
}
