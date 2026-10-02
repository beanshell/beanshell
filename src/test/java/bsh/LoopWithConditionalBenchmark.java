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

/** Real end-to-end measurement (through Interpreter.eval(), not a direct
 * BlockNameSpace micro-benchmark) of a for-loop whose body is a nested
 * {} block, itself containing an if whose branch is also a {} block --
 * i.e. exactly the "block nested inside a loop-body block" case that,
 * per BSHBlock.eval(CallStack,Interpreter) defaulting to the uncached
 * (false / new BlockNameSpace) path, currently pays full allocation cost
 * every reach, unlike the outer loop-body block which is cached. Run
 * before and after extending BSHIfStatement to opt into the cached path
 * to see the real per-iteration delta, not just the isolated
 * BlockNameSpace-acquisition delta BlockNameSpaceBenchmark measures. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class LoopWithConditionalBenchmark {
    private static final int ITERATIONS = 1000;
    private static final String SCRIPT =
        "int total = 0;\n" +
        "for (int i = 0; i < " + ITERATIONS + "; i++) {\n" +
        "    if (true) {\n" +
        "        int x = i;\n" +
        "        total = total + x;\n" +
        "    }\n" +
        "}\n" +
        "total;";

    private Interpreter interpreter;

    /** One Interpreter reused for the whole trial, matching the
     * long-running-script scenario (#655) this cache machinery exists
     * for -- and avoiding Interpreter-construction cost from swamping
     * the loop+if signal, which per-invocation setup would do. */
    @Setup(Level.Trial)
    public void setup() {
        interpreter = new Interpreter();
    }

    /** Cost of ITERATIONS loop passes each call -- divide by ITERATIONS
     * for a rough per-iteration figure comparable to
     * BlockNameSpaceBenchmark's pooled/fresh numbers. */
    @Benchmark
    public Object forLoopWithIfBlock() throws EvalError {
        return interpreter.eval(SCRIPT);
    }
}
