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

import bsh.console.SimpleConsole;
import bsh.console.StandardConsole;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class InterpreterTest {


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=50">Issue #50</a>
     */
    @Test(timeout = 10000)
    public void check_for_memory_leak() throws Exception {
        @SuppressWarnings("resource")
        final WeakReference<Object> reference = new WeakReference<Object>(new Interpreter().eval("x = new byte[1024 * 2024]; return x;"));
        while (reference.get() != null) {
            System.gc();
            Thread.sleep(100);
        }
    }

    @Test
    public void check_system_object() throws Exception {
        TestUtil.eval("bsh.system.foo = \"test\";");
        final Object result = TestUtil.eval("return bsh.system.foo;");
        assertEquals("test", result);
        assertNull(TestUtil.eval("return bsh.system.shutdownOnExit;"));
        Interpreter.setShutdownOnExit(false);
        assertEquals(Boolean.FALSE, TestUtil.eval("return bsh.system.shutdownOnExit;"));
    }

    @Test
    public void SimpleConsole_if_streams_in_constructor() throws Exception {
        Reader      in  = new InputStreamReader(System.in);
        PrintStream out = System.out;
        PrintStream err = System.err;

        Interpreter bsh = new Interpreter(in, out, err, true);
        ConsoleInterface c = bsh.getConsole();

        assertTrue("not a SimpleConsole", c instanceof SimpleConsole);
        assertSame(in, c.getIn());
        assertSame(out, c.getOut());
        assertSame(err, c.getErr());

        bsh = new Interpreter(in, out, err, true, null);
        c = bsh.getConsole();

        assertTrue("not a SimpleConsole", c instanceof SimpleConsole);
        assertSame(in, c.getIn());
        assertSame(out, c.getOut());
        assertSame(err, c.getErr());

        bsh = new Interpreter(in, out, err, true, null, null, null);
        c = bsh.getConsole();

        assertTrue("not a SimpleConsole", c instanceof SimpleConsole);
        assertSame(in, c.getIn());
        assertSame(out, c.getOut());
        assertSame(err, c.getErr());
    }

    @Test
    public void console_in_constructor() {
        SimpleConsole simple = new SimpleConsole(new StringReader(""), System.out, System.err);
        StandardConsole standard = new StandardConsole();

        assertSame(new Interpreter(simple).getConsole(), simple);
        assertSame(new Interpreter(simple, null).getConsole(), simple);
        assertSame(new Interpreter(standard).getConsole(), standard);

    }

    // --------------------------------------------------------- private methods
}
