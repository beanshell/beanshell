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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Q2: is caching a class's reflected members (BshClassManager.memberCache, a
 * soft-value ValueReferenceMap keyed by Class) worth it, and if so does the
 * ValueReferenceMap plumbing (weak/soft refs, synchronized, reference-queue
 * polling on every get()) earn its keep over a trivial ConcurrentHashMap?
 * <p>
 * Three variants, same lookup (Reflect.java's real call shape: cache.get(cls)
 * .findMethod(name, argTypes)):
 * - cachedHit: the real memberCache (steady-state hit, cache warmed in setup)
 * - plainMapHit: identical lookup through a bare ConcurrentHashMap instead
 *   of ValueReferenceMap -- isolates the reference-cache plumbing's own cost,
 *   but note it drops weak/soft-key semantics entirely (not a fair
 *   production candidate, just a lower bound on achievable overhead)
 * - noCacheRebuildEveryCall: rebuilds MemberCache (i.e. re-scans the class
 *   via reflection) on every call -- the cost caching exists to avoid */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MemberCacheBenchmark {
    private static final Class<?> TARGET = ArrayList.class;
    private static final Class<?>[] ARG_TYPES = { Object.class };

    private ConcurrentHashMap<Class<?>, BshClassManager.MemberCache> plainMap;

    @Setup(Level.Trial)
    public void setup() {
        BshClassManager.memberCache.get(TARGET);
        plainMap = new ConcurrentHashMap<>();
        plainMap.computeIfAbsent(TARGET, BshClassManager.MemberCache::new);
    }

    @Benchmark
    public Invocable cachedHit() {
        return BshClassManager.memberCache.get(TARGET).findMethod("add", ARG_TYPES);
    }

    @Benchmark
    public Invocable plainMapHit() {
        return plainMap.computeIfAbsent(TARGET, BshClassManager.MemberCache::new)
            .findMethod("add", ARG_TYPES);
    }

    @Benchmark
    public Invocable noCacheRebuildEveryCall() {
        return new BshClassManager.MemberCache(TARGET).findMethod("add", ARG_TYPES);
    }
}
