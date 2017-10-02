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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class InterpreterConcurrencyTest {

    static final String script =
            /*  1 */ "call(param) {" +
            /*  3 */ "  class Echo {\n" +
            /*  4 */ "      \n" +
            /*  5 */ "      final Object _s;\n" +
            /*  6 */ "      \n" +
            /*  7 */ "      Echo(Object s) {\n" +
            /*  8 */ "          _s = s;\n" +
            /*  9 */ "      }\n" +
            /*  0 */ "      \n" +
            /* 11 */ "      Object echo() {\n" +
            /* 12 */ "          return param;\n" +
            /* 13 */ "      }\n" +
            /* 14 */ "      \n" +
            /* 15 */ "  }\n" +
            /* 16 */ "  \n" +
            /* 17 */ "  return new Echo(param).echo();\n" +
            /* 18 */ "}" +
            /* 19 */ "return this;";


    @Test
    public void single_threaded() throws Exception {
        final Interpreter interpreter = new Interpreter();
        final This callable = (This) interpreter.eval(script);
        Assert.assertEquals("foo", callable.invokeMethod("call", new Object[] { "foo" }));
        Assert.assertEquals(42, callable.invokeMethod("call", new Object[] { 42 }));
    }


    @Test
    public void multi_threaded_callable() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final String script =
                "call(v) {"+
                "   return v;" +
                "}" +
                "return this;";
        final Interpreter interpreter = new Interpreter();
        final This callable = (This) interpreter.eval(script);
        final Runnable runnable = new Runnable() {
            public void run() {
                final int value = counter.incrementAndGet();
                try {
                    Assert.assertEquals(value, callable.invokeMethod("call", new Object[] { value }  ));
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        measureConcurrentTime(runnable, 30, 30, 100);

    }

    @Test
    public void multi_threaded_class_generation() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        final Interpreter interpreter = new Interpreter();
        final This callable = (This) interpreter.eval(script);
        final Runnable runnable = new Runnable() {
            public void run() {
                try {
                    final int i = counter.incrementAndGet();
                    final Object o = callable.invokeMethod("call", new Object[]{i});
                    Assert.assertEquals(i, o); // todo - this needs some extra work and some more tests to ensure behaviour
                } catch (final EvalError evalError) {
                    throw new RuntimeException(evalError);
                }
            }
        };
        measureConcurrentTime(runnable, 30, 30, 100);
    }


    /**
     * helper methods / classes, could be extracted to a util class
     */


    private static void cleanUp() {
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
    }


    /**
     * Measure the time of concurrent executions of the provided runnable instance - the first error or runtime exceptions
     * during execution is populated to the caller. The provided runnable is executed {@code taskCount * iterationCount}
     * times.
     */
    public static long measureConcurrentTime(final Runnable runnable, final int threadCount, final int taskCount, final int iterationCount) throws InterruptedException {
        final long duration = _measureConcurrentTime(runnable, threadCount, taskCount, iterationCount);
        cleanUp();
        return duration;
    }


    private static long _measureConcurrentTime(final Runnable runnable, final int threadCount, final int taskCount, final int iterationCount) throws InterruptedException {
        if (threadCount < 1) {
            throw new IllegalArgumentException("thread count must be at least 1");
        }
        if (taskCount < threadCount) {
            throw new IllegalArgumentException("task count below thread count");
        }
        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
        final Exception callerStack = new Exception("called from");
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount + 1);
        final AtomicReference<Throwable> exceptionHolder = new AtomicReference<Throwable>();
        final MeasureRunnable toMeasure = new MeasureRunnable(countDownLatch, runnable, iterationCount, exceptionHolder);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < taskCount; i++) {
            executorService.submit(toMeasure);
        }
        cleanUp();
        final long startTime = System.nanoTime();
        countDownLatch.countDown(); // start all
        executorService.shutdown();
        executorService.awaitTermination(60 * 60, TimeUnit.SECONDS);
        final Throwable throwable = exceptionHolder.get();
        if (throwable instanceof RuntimeException) {
            throw combineTraces((RuntimeException) throwable, callerStack);
        }
        if (throwable instanceof Error) {
            throw combineTraces((Error) throwable, callerStack);
        }
        if (throwable != null) {
            //noinspection ThrowableInstanceNeverThrown
            throw combineTraces(new RuntimeException(throwable), callerStack);
        }
        return System.nanoTime() - startTime;
    }


    /**
     * Adds {@code cause} as root-cause to {@code throwable} and returns {@code throwable}.
     *
     * @param throwable exception which root-cause should be extended.
     * @param cause new root-cause, usually a caller-stack.
     * @param <T> type of given throwable.
     * @return {@code throwable} extended with the given {@code cause}.
     */
    private static <T extends Throwable> T combineTraces(final T throwable, final Exception cause) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        rootCause.initCause(cause);
        return throwable;
    }


    private static class MeasureRunnable implements Runnable {

        private final CountDownLatch _countDownLatch;
        private final Runnable _task;
        private final int _iterationCount;
        private final AtomicReference<Throwable> _exception;


        private MeasureRunnable(final CountDownLatch countDownLatch, final Runnable task, final int iterationCount, final AtomicReference<Throwable> exception) {
            _countDownLatch = countDownLatch;
            _task = task;
            _iterationCount = iterationCount;
            _exception = exception;
        }


        public void run() {
            try {
                _countDownLatch.countDown();
                for (int i = 0; i < _iterationCount; i++) {
                    _task.run();
                }
            } catch (RuntimeException e) {
                _exception.compareAndSet(null, e);
                throw e;
            } catch (Error e) {
                _exception.compareAndSet(null, e);
                throw e;
            }
        }

    }




}
