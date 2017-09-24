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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * The Class InterpreterConcurrencyTest.
 */
public class InterpreterConcurrencyTest {

    /** The Constant script. */
    static final String script = /* 1 */ "call(param) {"
    /* 3 */ + "  class Echo {\n"
    /* 4 */ + "      \n"
    /* 5 */ + "      final Object _s;\n"
    /* 6 */ + "      \n"
    /* 7 */ + "      Echo(Object s) {\n"
    /* 8 */ + "          _s = s;\n"
    /* 9 */ + "      }\n"
    /* 0 */ + "      \n"
    /* 11 */ + "      Object echo() {\n"
    /* 12 */ + "          return param;\n"
    /* 13 */ + "      }\n"
    /* 14 */ + "      \n"
    /* 15 */ + "  }\n"
    /* 16 */ + "  \n"
    /* 17 */ + "  return new Echo(param).echo();\n"
    /* 18 */ + "}"
    /* 19 */ + "return this;";

    /**
     * Single threaded.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    public void single_threaded() throws Exception {
        final This callable = this.createCallable();
        Assert.assertEquals("foo",
                callable.invokeMethod("call", new Object[] {"foo"}));
        Assert.assertEquals(42,
                callable.invokeMethod("call", new Object[] {42}));
    }

    /**
     * Multi threaded callable.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    public void multi_threaded_callable() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final String script = "call(v) {" + "   return v;" + "}"
                + "return this;";
        final Interpreter interpreter = new Interpreter();
        final This callable = (This) interpreter.eval(script);
        final Runnable runnable = new Runnable() {

            public void run() {
                final int value = counter.incrementAndGet();
                try {
                    Assert.assertEquals(value, callable.invokeMethod("call",
                            new Object[] {value}));
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        TestUtil.measureConcurrentTime(runnable, 30, 30, 100);
    }

    /**
     * Multi threaded class generation.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    public void multi_threaded_class_generation() throws Exception {
        final This callable = this.createCallable();
        final AtomicInteger counter = new AtomicInteger();
        final Runnable runnable = new Runnable() {

            public void run() {
                try {
                    final int i = counter.incrementAndGet();
                    final Object o = callable.invokeMethod("call",
                            new Object[] {i});
                    Assert.assertEquals(i, o);
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        TestUtil.measureConcurrentTime(runnable, 30, 30, 100);
    }

    /**
     * Creates the callable.
     *
     * @return the this
     * @throws EvalError
     *             the eval error
     */
    private This createCallable() throws EvalError {
        final Interpreter interpreter = new Interpreter();
        return (This) interpreter.eval(script);
    }
}
