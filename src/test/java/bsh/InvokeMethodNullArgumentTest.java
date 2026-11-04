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
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/
package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Comparator;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import org.junit.Before;
import org.junit.Test;

import bsh.engine.BshScriptEngineFactory;

/** Java callers pass a plain null where a scripted method expects a value
 * (#925). */
public class InvokeMethodNullArgumentTest {

    private Interpreter interpreter;
    private This self;

    @Before
    public void setUp() throws Exception {
        interpreter = new Interpreter();
        interpreter.eval(
            "isNull(x) { return x == null; }"
            + "two(a, b) { return a + \",\" + b; }"
            + "three(a, b, c) { return a + \",\" + b + \",\" + c; }"
            + "none() { return \"none\"; }"
            + "nothing() { return null; }");
        self = (This) interpreter.eval("this");
    }

    private Object invoke(String name, Object... args) throws Exception {
        return Primitive.unwrap(self.invokeMethod(name, args));
    }

    @Test
    public void this_invoke_method_with_a_null_argument() throws Exception {
        assertEquals(true, invoke("isNull", new Object[] { null }));
    }

    @Test
    public void this_invoke_method_with_a_null_among_several_arguments() throws Exception {
        assertEquals("a,null", invoke("two", "a", null));
        assertEquals("null,b", invoke("two", null, "b"));
        assertEquals("a,null,c", invoke("three", "a", null, "c"));
        assertEquals("null,null,null", invoke("three", null, null, null));
    }

    @Test
    public void this_invoke_method_with_the_full_signature() throws Exception {
        assertEquals(true, Primitive.unwrap(self.invokeMethod("isNull",
            new Object[] { null }, interpreter, null, null, false)));
    }

    @Test
    public void name_space_invoke_method_with_a_null_argument() throws Exception {
        assertEquals(true, Primitive.unwrap(interpreter.getNameSpace()
            .invokeMethod("isNull", new Object[] { null }, interpreter)));
    }

    @Test
    public void the_callers_argument_array_is_not_modified() throws Exception {
        Object[] args = { "a", null };
        self.invokeMethod("two", args);
        assertEquals("a", args[0]);
        assertNull(args[1]);
    }

    @Test
    public void a_covariant_argument_array_with_a_null() throws Exception {
        assertEquals(true, Primitive.unwrap(
            self.invokeMethod("isNull", new String[] { null })));
        String[] args = { "a", null };
        assertEquals("a,null", Primitive.unwrap(self.invokeMethod("two", args)));
        assertNull(args[1]);
    }

    @Test
    public void a_method_returning_null_still_returns_null() throws Exception {
        assertNull(invoke("nothing"));
    }

    @Test
    public void primitive_null_and_a_null_array_still_work() throws Exception {
        assertEquals(true, invoke("isNull", Primitive.NULL));
        assertEquals("none", Primitive.unwrap(self.invokeMethod("none", null)));
        assertEquals("none", invoke("none"));
    }

    @Test
    public void script_engine_invoke_function_with_a_null_argument() throws Exception {
        ScriptEngine engine = new BshScriptEngineFactory().getScriptEngine();
        engine.eval("isNull(x) { return x == null; }"
            + "two(a, b) { return a + \",\" + b; }");
        Invocable invocable = (Invocable) engine;
        assertEquals(true, Primitive.unwrap(invocable.invokeFunction("isNull", (Object) null)));
        assertEquals("a,null", invocable.invokeFunction("two", "a", null));
    }

    @Test
    public void script_engine_invoke_method_with_a_null_argument() throws Exception {
        ScriptEngine engine = new BshScriptEngineFactory().getScriptEngine();
        engine.eval("isNull(x) { return x == null; }");
        Object scriptThis = engine.eval("this");
        assertEquals(true, Primitive.unwrap(((Invocable) engine).invokeMethod(
            scriptThis, "isNull", (Object) null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void interface_proxy_with_a_null_argument_still_works() throws Exception {
        interpreter.eval("compare(a, b) { return 0; }");
        Comparator<Object> comparator =
            (Comparator<Object>) interpreter.getInterface(Comparator.class);
        assertEquals(0, comparator.compare(null, "x"));
        assertEquals(0, comparator.compare("x", null));
    }
}
