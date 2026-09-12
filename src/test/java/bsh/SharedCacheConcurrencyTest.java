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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The caches these exercise are static, so they are shared by every
 * interpreter in the JVM. Corrupting one of their bucket chains spins
 * forever in a tight loop that no interrupt or timeout can break, so each
 * test bounds its own wait and reports the threads that never came back. */
@RunWith(FilteredTestRunner.class)
public class SharedCacheConcurrencyTest {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 8000;
    private static final int WAIT_SECONDS = 60;

    /** A Parts holds its own key string, so its entry is never collected
     * however weak the map's keys are. Left alone, the distinct names below
     * would stay in the shared cache for the rest of the forked JVM. */
    @After
    public void emptyTheSharedCaches() {
        Name.clearParts();
        Reflect.clearInstanceCache();
    }

    @Test
    public void compound_name_parts_cache() throws Exception {
        hammer("Name.Parts", id -> {
            for (int i = 0; i < PER_THREAD; i++) {
                String name = "pkg" + (i % 977) + ".cls" + i + ".member" + id;
                assertEquals("cls" + i + ".member" + id, Name.suffix(name, 2));
                assertEquals("pkg" + (i % 977), Name.prefix(name, 1));
                assertEquals(3, Name.countParts(name));
            }
        });
    }

    /** accessorName() used to cache the capitalized name in a shared map,
     * for the sake of one char upcase. The map is gone; this guards against
     * a cache coming back, and against the result depending on the caller. */
    @Test
    public void property_accessor_name_holds_no_shared_state() throws Exception {
        hammer("Reflect.accessorName", id -> {
            for (int i = 0; i < PER_THREAD; i++) {
                String prop = "prop" + i + "x" + id;
                assertEquals("get" + "Prop" + i + "x" + id,
                        Reflect.accessorName("get", prop));
            }
        });
    }

    /** Unlike the other three, this one never reproduced the hang: its keys
     * are classes, and a class a caller still holds is never collected, so
     * the stale-entry path the others corrupt is not reached here. It covers
     * concurrent access against both outcomes the cache records; it is not
     * evidence the cache could corrupt. */
    @Test
    public void default_instance_cache() throws Exception {
        final Class<?>[] constructs = constructableTypes();
        final Class<?>[] cannot = arrayTypes();
        hammer("Reflect.instanceCache", id -> {
            for (int i = 0; i < 40; i++) {
                for (Class<?> type : constructs) {
                    Object instance = Reflect.getNewInstance(type);
                    assertNotNull(type + " constructs", instance);
                    assertTrue(type + " caches an instance of itself",
                            type.isInstance(instance));
                }
                for (Class<?> type : cannot)
                    assertNull(type + " cannot be constructed",
                            Reflect.getNewInstance(type));
                if (0 == i % 5)
                    Reflect.clearInstanceCache();
            }
        });
    }

    /** @return classes with a public no-arg constructor, so the cache stores
     * a real instance rather than only recording failures */
    private static Class<?>[] constructableTypes() {
        return new Class<?>[] { Object.class, String.class, StringBuilder.class,
            StringBuffer.class, java.util.ArrayList.class,
            java.util.LinkedList.class, java.util.HashMap.class,
            java.util.HashSet.class, java.util.TreeMap.class,
            java.util.TreeSet.class, java.util.Vector.class,
            java.util.Stack.class, java.util.Hashtable.class,
            java.util.LinkedHashMap.class, java.util.LinkedHashSet.class,
            java.util.ArrayDeque.class, java.util.PriorityQueue.class,
            java.util.Random.class, java.util.Date.class,
            java.util.BitSet.class, java.io.StringWriter.class,
            java.util.concurrent.ConcurrentHashMap.class,
            java.util.concurrent.CopyOnWriteArrayList.class };
    }

    /** @return many distinct classes, as nested array types of a few bases;
     * an array class has no constructor, so each records a failure */
    private static Class<?>[] arrayTypes() {
        Class<?>[] base = { String.class, Object.class, int.class, double.class,
                java.util.ArrayList.class, StringBuilder.class };
        Class<?>[] types = new Class<?>[base.length * 20];
        int n = 0;
        for (Class<?> b : base) {
            Class<?> type = b;
            for (int d = 0; d < 20; d++) {
                type = java.lang.reflect.Array.newInstance(type, 0).getClass();
                types[n++] = type;
            }
        }
        return types;
    }

    @Test
    public void capability_class_cache() throws Exception {
        hammer("Capabilities.classes", id -> {
            for (int i = 0; i < PER_THREAD / 10; i++) {
                assertTrue(Capabilities.classExists("java.lang.String"));
                Capabilities.classExists("no.such.Class" + i + "x" + id);
            }
        });
    }

    private interface Work {
        void run(int id) throws Exception;
    }

    /** Runs work on every thread at once and fails, rather than hanging the
     * suite, if any of them is still inside the cache when time runs out.
     * A thread that failed this way is still spinning when the test returns:
     * the loop it is in cannot be interrupted, so a failure here degrades
     * every test after it. Read a failure as "stop and fix", not "retry". */
    private static void hammer(String what, Work work) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        final AtomicLong completed = new AtomicLong();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            final int id = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    work.run(id);
                    completed.incrementAndGet();
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            }, what + "-" + t);
            threads[t].setDaemon(true);
            threads[t].start();
        }
        start.countDown();
        boolean finished = done.await(WAIT_SECONDS, TimeUnit.SECONDS);
        if (null != failure.get())
            throw new AssertionError(what + " threw", failure.get());
        if (!finished) {
            StringBuilder sb = new StringBuilder(what + ": "
                    + completed.get() + " of " + THREADS
                    + " threads finished in " + WAIT_SECONDS + "s; stuck at:");
            for (Thread thread : threads)
                if (thread.isAlive() && thread.getStackTrace().length > 0)
                    sb.append("\n    ").append(thread.getName())
                        .append(" -> ").append(thread.getStackTrace()[0]);
            throw new AssertionError(sb.toString());
        }
        assertEquals(what + " threads completed", THREADS, completed.get());
    }
}
