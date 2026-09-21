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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Static hooks called from the command scripts in gatedcmds/. */
public class GatedLoadSupport {
    private static final ConcurrentHashMap<String, AtomicInteger> loads = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CountDownLatch> entered = new ConcurrentHashMap<>();
    private static volatile Set<String> gated = new HashSet<>();
    private static volatile CountDownLatch gate = new CountDownLatch(1);

    /** Clear counters and choose which command keys block until release(). */
    static void reset(final String... gatedKeys) {
        loads.clear();
        entered.clear();
        gated = new HashSet<>(Arrays.asList(gatedKeys));
        gate = new CountDownLatch(1);
    }

    static void release() {
        gate.countDown();
    }

    static int loads(final String key) {
        final AtomicInteger n = loads.get(key);
        return n == null ? 0 : n.get();
    }

    static boolean awaitEntered(final String key, final long ms) throws InterruptedException {
        return entered.computeIfAbsent(key, k -> new CountDownLatch(1)).await(ms, TimeUnit.MILLISECONDS);
    }

    public static void enter(final String key) throws InterruptedException {
        loads.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        entered.computeIfAbsent(key, k -> new CountDownLatch(1)).countDown();
        if (gated.contains(key))
            gate.await(10, TimeUnit.SECONDS);
    }

    /** Separate entry point: bsh serializes calls to one static method, which would mask lock overlap. */
    public static void enterB(final String key) throws InterruptedException {
        enter(key);
    }
}
