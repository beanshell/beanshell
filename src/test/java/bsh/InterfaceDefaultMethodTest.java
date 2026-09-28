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

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import bsh.defaultpkg.HiddenAccess;

import static bsh.TestUtil.eval;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
public class InterfaceDefaultMethodTest {

    public interface Greeter {
        String name();

        default String greet() {
            return "hello " + name();
        }
    }

    public interface SubGreeter extends Greeter { }

    public interface SubSubGreeter extends SubGreeter { }

    public interface ReAbstracted extends Greeter {
        @Override
        String greet();
    }

    public interface Numbers {
        int base();

        default int twice() {
            return base() * 2;
        }

        default int add(int a) {
            return base() + a;
        }
    }

    public interface Joiner {
        default String join(String... xs) {
            return String.join(",", xs);
        }
    }

    public interface Chained {
        default String outer() {
            return "[" + inner() + "]";
        }

        default String inner() {
            return "in";
        }
    }

    public interface IoThrower {
        default void io() throws IOException {
            throw new IOException("io");
        }
    }

    public interface DiamondA {
        default String m() {
            return "A";
        }
    }

    public interface DiamondB {
        default String m() {
            return "B";
        }
    }

    private static This scripted(String... code) throws Exception {
        String[] all = Arrays.copyOf(code, code.length + 1);
        all[code.length] = "return this;";
        return (This) eval(all);
    }

    @Test
    public void java_default_runs_on_proxy() throws Exception {
        assertEquals("hello bob", eval(
            "name() { return \"bob\"; }",
            "return ((bsh.InterfaceDefaultMethodTest.Greeter) this).greet();"
        ));
    }

    @Test
    public void inherited_default_two_levels_up() throws Exception {
        assertEquals("hello bob", eval(
            "name() { return \"bob\"; }",
            "return ((bsh.InterfaceDefaultMethodTest.SubGreeter) this).greet();"
        ));
        assertEquals("hello bob", eval(
            "name() { return \"bob\"; }",
            "return ((bsh.InterfaceDefaultMethodTest.SubSubGreeter) this).greet();"
        ));
    }

    @Test
    public void primitive_return_and_parameters() throws Exception {
        assertEquals(42, eval(
            "base() { return 21; }",
            "return ((bsh.InterfaceDefaultMethodTest.Numbers) this).twice();"
        ));
        assertEquals(22, eval(
            "base() { return 21; }",
            "return ((bsh.InterfaceDefaultMethodTest.Numbers) this).add(1);"
        ));
    }

    @Test
    public void varargs_default() throws Exception {
        assertEquals("a,b", eval(
            "return ((bsh.InterfaceDefaultMethodTest.Joiner) this).join(\"a\", \"b\");"
        ));
    }

    @Test
    public void default_calling_another_default() throws Exception {
        assertEquals("[in]", eval(
            "return ((bsh.InterfaceDefaultMethodTest.Chained) this).outer();"
        ));
    }

    @Test
    public void checked_exception_from_default_keeps_its_type() throws Exception {
        assertEquals("io", eval(
            "t = (bsh.InterfaceDefaultMethodTest.IoThrower) this;",
            "try {",
                "t.io();",
            "} catch (java.io.IOException e) {",
                "return e.getMessage();",
            "}",
            "return \"not thrown\";"
        ));
        IoThrower proxy = (IoThrower) scripted().getInterface(IoThrower.class);
        try {
            proxy.io();
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("io", e.getMessage());
        }
    }

    @Test
    public void package_private_interface_default() throws Exception {
        This t = scripted("name() { return \"bob\"; }");
        assertEquals("hidden bob", HiddenAccess.greet(t.getInterface(HiddenAccess.type())));
    }

    @Test
    public void script_engine_interface_default() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("beanshell");
        engine.eval("compare(a, b) { return a.compareTo(b); }");
        @SuppressWarnings("unchecked")
        Comparator<String> c = ((Invocable) engine).getInterface(Comparator.class);
        List<String> l = new ArrayList<>(Arrays.asList("b", "a", "c"));
        l.sort(c.reversed());
        assertEquals(Arrays.asList("c", "b", "a"), l);
    }

    @Test
    public void scripted_override_wins_over_default() throws Exception {
        assertEquals("mine", eval(
            "name() { return \"bob\"; }",
            "greet() { return \"mine\"; }",
            "return ((bsh.InterfaceDefaultMethodTest.Greeter) this).greet();"
        ));
    }

    @Test
    public void invoke_catch_all_wins_over_default() throws Exception {
        assertEquals("caught greet", eval(
            "invoke(m, a) { return \"caught \" + m; }",
            "return ((bsh.InterfaceDefaultMethodTest.Greeter) this).greet();"
        ));
    }

    @Test
    public void reabstracted_default_is_not_run() throws Exception {
        ReAbstracted proxy = (ReAbstracted) scripted("name() { return \"bob\"; }")
            .getInterface(ReAbstracted.class);
        try {
            proxy.greet();
            fail("expected the missing method to fail");
        } catch (UndeclaredThrowableException e) {
            assertThat(e.getCause(), instanceOf(EvalError.class));
            assertThat(e.getCause().getMessage(), containsString("greet()"));
        }
    }

    @Test
    public void diamond_defaults_use_first_interface() throws Exception {
        Object proxy = scripted().getInterface(new Class<?>[] { DiamondA.class, DiamondB.class });
        assertEquals("A", ((DiamondA) proxy).m());
        assertEquals("A", ((DiamondB) proxy).m());
    }
}
