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

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Q1: is pooling/reusing BlockNameSpace instances (BlockNameSpace.blockspaces,
 * a weak-value ValueReferenceMap keyed by parent+blockId) worth its cost over
 * simply allocating a fresh BlockNameSpace on every block/loop-body entry?
 * <p>
 * "pooled*" benchmarks go through BlockNameSpace.getInstance(), the real
 * cache used by BSHBlock/BSHEnhancedForLoop etc. "fresh*" benchmarks bypass
 * it and construct + clear() directly, which is what every call would do
 * if the cache were removed. Same parent/blockId across invocations in
 * both cases, matching a real loop body which reuses one block id every
 * iteration -- i.e. this is the cache's intended steady-state hit path,
 * not a worst case.
 * <p>
 * "*Bare" variants isolate the cache/allocation cost alone. "*WithVariable"
 * variants add one typed variable declaration per call, since a namespace
 * that never has anything declared in it doesn't exercise clear()'s real
 * cost and isn't representative of an actual loop body. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BlockNameSpaceBenchmark {
    private NameSpace parent;
    private int blockId;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        final Interpreter interp = new Interpreter();
        parent = interp.getNameSpace();
        blockId = BlockNameSpace.blockCount.incrementAndGet();
    }

    @Benchmark
    public NameSpace pooledBare() {
        return BlockNameSpace.getInstance(parent, blockId);
    }

    @Benchmark
    public BlockNameSpace freshBare() {
        final BlockNameSpace ns = new BlockNameSpace(parent, blockId);
        ns.clear();
        return ns;
    }

    @Benchmark
    public NameSpace pooledWithVariable() throws UtilEvalError {
        final NameSpace ns = BlockNameSpace.getInstance(parent, blockId);
        ns.setTypedVariable("i", Integer.TYPE, new Primitive(counter++), false);
        return ns;
    }

    @Benchmark
    public BlockNameSpace freshWithVariable() throws UtilEvalError {
        final BlockNameSpace ns = new BlockNameSpace(parent, blockId);
        ns.clear();
        ns.setTypedVariable("i", Integer.TYPE, new Primitive(counter++), false);
        return ns;
    }
}
