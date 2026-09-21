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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

/** Lazy loading of scripted commands into a namespace shared by several threads. */
public class CommandLoadConcurrencyTest {
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        final Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    @After
    public void tearDown() {
        GatedLoadSupport.release();
        pool.shutdownNow();
    }

    private static Interpreter shared() throws Exception {
        final Interpreter i = new Interpreter();
        i.eval("importCommands(\"/gatedcmds\");");
        return i;
    }

    private static This caller(final Interpreter i) throws Exception {
        return (This) i.eval("callA() { return gatedA(); } callB() { return gatedB(); } "
                + "callOuter() { return outerCmd(); } return this;");
    }

    private Future<Object> call(final This t, final String method) {
        return pool.submit((Callable<Object>) () -> t.invokeMethod(method, new Object[0]));
    }

    @Test
    public void same_command_loaded_once_by_racing_threads() throws Exception {
        GatedLoadSupport.reset("A");
        final This t = caller(shared());
        final Future<Object> a = call(t, "callA");
        assertTrue(GatedLoadSupport.awaitEntered("A", 5000));
        final Future<Object> b = call(t, "callA");
        Thread.sleep(500);
        assertEquals("second thread must wait for the first load", 1, GatedLoadSupport.loads("A"));
        GatedLoadSupport.release();
        assertEquals("A", a.get(10, TimeUnit.SECONDS));
        assertEquals("A", b.get(10, TimeUnit.SECONDS));
        assertEquals(1, GatedLoadSupport.loads("A"));
    }

    @Test
    public void different_commands_load_one_at_a_time() throws Exception {
        GatedLoadSupport.reset("A", "B");
        final This t = caller(shared());
        final Future<Object> a = call(t, "callA");
        assertTrue(GatedLoadSupport.awaitEntered("A", 5000));
        final Future<Object> b = call(t, "callB");
        assertFalse("B loaded while A was still loading", GatedLoadSupport.awaitEntered("B", 500));
        GatedLoadSupport.release();
        assertEquals("A", a.get(10, TimeUnit.SECONDS));
        assertEquals("B", b.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void command_loading_another_command_does_not_deadlock() throws Exception {
        GatedLoadSupport.reset();
        final This t = caller(shared());
        assertEquals("outer:inner", call(t, "callOuter").get(10, TimeUnit.SECONDS));
        assertEquals(1, GatedLoadSupport.loads("outer"));
        assertEquals(1, GatedLoadSupport.loads("inner"));
    }

    @Test
    public void declared_only_call_loads_command_despite_parent_method() throws Exception {
        final Interpreter i = new Interpreter();
        i.eval("declaredFoo() { return \"parent\"; }");
        final NameSpace child = new NameSpace(i.getNameSpace(), "child");
        child.importCommands("/gatedcmds");
        assertEquals("cmd", child.getThis(i).invokeMethod(
                "declaredFoo", new Object[0], i, new CallStack(child), null, true));
    }

    @Test
    public void parent_and_child_loads_do_not_deadlock() throws Exception {
        GatedLoadSupport.reset("C", "P");
        final Interpreter i = new Interpreter();
        i.eval("importCommands(\"/gatedparent\");");
        final NameSpace parent = i.getNameSpace();
        final NameSpace child = new NameSpace(parent, "child");
        child.importCommands("/gatedchild");
        i.eval("cl() { return childCmd2(); }", child);
        i.set("childRef", child.getThis(i));
        final Future<Object> a = pool.submit(() -> i.eval("childCmd()", child));
        assertTrue(GatedLoadSupport.awaitEntered("C", 5000));
        final Future<Object> b = pool.submit(() -> i.eval("parentCmd()", parent));
        Thread.sleep(300);
        GatedLoadSupport.release();
        assertEquals("c", a.get(10, TimeUnit.SECONDS));
        assertEquals("p", b.get(10, TimeUnit.SECONDS));
    }
}
