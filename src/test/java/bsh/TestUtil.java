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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TestUtil {

    /**
     * Serializes and then deserializes the given instance - should be not null.
     *
     * @param orgig this instance is serialized and then deserialized
     * @return the instance after serialization and deserialization
     */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T serDeser(final T orgig) {
        try {
            final ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
            new ObjectOutputStream(byteOS).writeObject(orgig);
            return (T) new ObjectInputStream(new ByteArrayInputStream(byteOS.toByteArray())).readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    static void cleanUp() {
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
    }

    static {
        Capabilities.setAccessibility(Boolean.valueOf(System.getProperty("accessibility")));
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


    static long _measureConcurrentTime(final Runnable runnable, final int threadCount, final int taskCount, final int iterationCount) throws InterruptedException {
        if (threadCount < 1) {
            throw new IllegalArgumentException("thread count must be at least 1");
        }
        if (taskCount < threadCount) {
            throw new IllegalArgumentException("task count below thread count");
        }

        final Exception callerStack = new Exception("called from");
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount + 1);
        final AtomicReference<Throwable> exceptionHolder = new AtomicReference<Throwable>();
        final MeasureRunnable toMeasure = new MeasureRunnable(countDownLatch, runnable, iterationCount, exceptionHolder);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < taskCount; i++)
            executorService.submit(toMeasure);
        cleanUp();
        final long startTime = System.nanoTime();
        countDownLatch.countDown(); // start all
        executorService.shutdown();
        executorService.awaitTermination(60 * 60, TimeUnit.SECONDS);
        final Throwable throwable = exceptionHolder.get();
        if (throwable instanceof RuntimeException)
            throw combineTraces((RuntimeException) throwable, callerStack);
        if (throwable instanceof Error)
            throw combineTraces((Error) throwable, callerStack);
        if (throwable != null)
            throw combineTraces(new RuntimeException(throwable), callerStack);
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
    static <T extends Throwable> T combineTraces(final T throwable, final Exception cause) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null)
            rootCause = rootCause.getCause();
        rootCause.initCause(cause);
        return throwable;
    }

    public static String script(final String ... code) {
        final StringBuffer buffer = new StringBuffer();
        for (final String s : code)
            buffer.append(s).append('\n');
        return buffer.toString();
    }

    public static Object eval(final String ... code) throws Exception {
        return eval(Collections.<String, Integer>emptyMap(), code);
    }

    public static Object eval(final Map<String, ?> params, final String ... code) throws Exception {
        final Interpreter interpreter = new Interpreter();
        for (final Map.Entry<String, ?> entry : params.entrySet())
            interpreter.set(entry.getKey(), entry.getValue());
        return interpreter.eval(script(code));
    }

    public static <K,E> Map<K,E> toMap(K key, E value) {
        return Collections.singletonMap(key, value);
    }

    static class MeasureRunnable implements Runnable {

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
                for (int i = 0; i < _iterationCount; i++)
                    _task.run();
            } catch (Error | RuntimeException e) {
                _exception.compareAndSet(null, e);
                throw e;
            }
        }

    }
}
