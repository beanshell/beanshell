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

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** Programmatic JMH entry point for this project's benchmark harness.
 * <p>
 * Preferred (real JVM forks, trustworthy numbers): build the test classpath
 * once, then invoke java directly so JMH's fork can find it via
 * java.class.path --
 * <pre>
 *   mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/bsh-cp.txt
 *   java -cp target/classes:target/test-classes:$(cat /tmp/bsh-cp.txt) bsh.BenchmarkRunner
 * </pre>
 * Convenience/debug only: `mvn test-compile exec:java -Dexec.mainClass=bsh.BenchmarkRunner`.
 * exec:java runs in-process through an isolated Maven classloader that doesn't
 * set java.class.path, so JMH's fork (which shells out to
 * `java -cp <classpath-file> ForkedMain`) can't find our classes there --
 * pass -Djmh.forks=0 to run in-process instead when using this path. That
 * loses cross-run JIT/GC isolation, so treat its numbers as directional only.
 * <p>
 * Optional first arg is a JMH include regex (default: every *Benchmark class). */
public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        String include = args.length > 0 ? args[0] : ".*Benchmark.*";
        int forks = Integer.getInteger("jmh.forks", 1);
        Options opt = new OptionsBuilder()
            .include(include)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .warmupIterations(5)
            .measurementIterations(5)
            .forks(forks)
            .build();
        new Runner(opt).run();
    }
}
