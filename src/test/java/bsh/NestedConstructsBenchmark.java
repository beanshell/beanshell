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

/** Real end-to-end measurement, through Interpreter.eval(), of the four
 * "block nested inside a loop" locations beyond the outer loop-body block
 * itself (already cached) and the if-branch (BSHIfStatement, separately
 * fixed and measured by LoopWithConditionalBenchmark): a bare nested {}
 * statement, a switch case body written as {}, a try body + catch body,
 * and a try body + finally body. Each script reaches its construct once
 * per loop iteration, so ITERATIONS reaches per run. Run once against the
 * unmodified BSHBlock/BSHSwitchStatement/BSHTryStatement (uncached, always
 * new BlockNameSpace) and again after extending each to opt into
 * BlockNameSpace.getInstance() the same way BSHIfStatement was. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NestedConstructsBenchmark {
    private static final int ITERATIONS = 1000;

    private static final String NESTED_BLOCK_SCRIPT =
        "int total = 0;\n" +
        "for (int i = 0; i < " + ITERATIONS + "; i++) {\n" +
        "    { int x = i; total = total + x; }\n" +
        "}\n" +
        "total;";

    private static final String SWITCH_SCRIPT =
        "int total = 0;\n" +
        "for (int i = 0; i < " + ITERATIONS + "; i++) {\n" +
        "    switch (i % 2) {\n" +
        "        case 0: { int x = i; total = total + x; break; }\n" +
        "        default: { int x = i; total = total + x; break; }\n" +
        "    }\n" +
        "}\n" +
        "total;";

    private static final String TRY_CATCH_SCRIPT =
        "int total = 0;\n" +
        "for (int i = 0; i < " + ITERATIONS + "; i++) {\n" +
        "    try {\n" +
        "        throw new RuntimeException(\"x\");\n" +
        "    } catch (RuntimeException e) {\n" +
        "        int x = i; total = total + x;\n" +
        "    }\n" +
        "}\n" +
        "total;";

    private static final String TRY_FINALLY_SCRIPT =
        "int total = 0;\n" +
        "for (int i = 0; i < " + ITERATIONS + "; i++) {\n" +
        "    try {\n" +
        "        total = total + i;\n" +
        "    } finally {\n" +
        "        int x = i;\n" +
        "    }\n" +
        "}\n" +
        "total;";

    private Interpreter nestedBlockInterpreter;
    private Interpreter switchInterpreter;
    private Interpreter tryCatchInterpreter;
    private Interpreter tryFinallyInterpreter;

    @Setup(Level.Trial)
    public void setup() {
        nestedBlockInterpreter = new Interpreter();
        switchInterpreter = new Interpreter();
        tryCatchInterpreter = new Interpreter();
        tryFinallyInterpreter = new Interpreter();
    }

    @Benchmark
    public Object nestedBlockInLoop() throws EvalError {
        return nestedBlockInterpreter.eval(NESTED_BLOCK_SCRIPT);
    }

    @Benchmark
    public Object switchCaseInLoop() throws EvalError {
        return switchInterpreter.eval(SWITCH_SCRIPT);
    }

    @Benchmark
    public Object tryCatchInLoop() throws EvalError {
        return tryCatchInterpreter.eval(TRY_CATCH_SCRIPT);
    }

    @Benchmark
    public Object tryFinallyInLoop() throws EvalError {
        return tryFinallyInterpreter.eval(TRY_FINALLY_SCRIPT);
    }
}
