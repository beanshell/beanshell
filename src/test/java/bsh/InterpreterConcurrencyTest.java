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
import static bsh.TestUtil.script;
import static bsh.TestUtil.measureConcurrentTime;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class InterpreterConcurrencyTest {

    static final String script = script(
        /*  1 */ "call(param) {",
        /*  2 */ "",
        /*  3 */ "  class Echo {",
        /*  4 */ "",
        /*  5 */ "      final Object _s;",
        /*  6 */ "",
        /*  7 */ "      Echo(Object s) {",
        /*  8 */ "          _s = s;",
        /*  9 */ "      }",
        /* 10 */ "",
        /* 11 */ "      Object echo() {",
        /* 12 */ "          return _s;",
        /* 13 */ "      }",
        /* 14 */ "",
        /* 15 */ "  }",
        /* 16 */ "",
        /* 17 */ "  return new Echo(param).echo();",
        /* 18 */ "}",
        /* 19 */ "return this;"
    );


    @Test
    public void single_threaded() throws Exception {
        final This callable = createCallable();
        assertEquals("foo", callable.invokeMethod("call", new Object[] { "foo" }));
        assertEquals(42, callable.invokeMethod("call", new Object[] { 42 }));
    }


    @Test
    public void multi_threaded_callable() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final String script = script(
                "call(v) {",
                "   return v;",
                "}",
                "return this;"
            );
        final Interpreter interpreter = new Interpreter();
        final This callable = (This) interpreter.eval(script);
        final Runnable runnable = new Runnable() {
            public void run() {
                final int value = counter.incrementAndGet();
                try {
                    assertEquals(value, callable.invokeMethod("call", new Object[] { value }  ));
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        measureConcurrentTime(runnable, 30, 30, 100);
        interpreter.close();
    }


    @Test
    public void multi_threaded_class_generation() throws Exception {
        final This callable = createCallable();
        final AtomicInteger counter = new AtomicInteger();
        final Runnable runnable = new Runnable() {
            public void run() {
                try {
                    final int i = counter.incrementAndGet();
                    final Object o = callable.invokeMethod("call", new Object[]{i});
                    assertEquals(i, o);
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        measureConcurrentTime(runnable, 30, 30, 100);
    }


    private This createCallable() throws Exception {
        try (final Interpreter interpreter = new Interpreter()) {
            return (This) interpreter.eval(script);
        }
    }

}
