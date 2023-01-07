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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.lessThan;

import org.junit.Test;

public class InterpreterTest {
    ConsoleInterface getConsole(StringBuilder S, OutputStream oout, OutputStream oerr) {
        return new ConsoleInterface() {
            PrintStream err = new PrintStream(oerr);
            PrintStream out = new PrintStream(oout);
            @Override
            public Reader getIn() {
                return new StringReader("\n\n\n\n");
            }

            @Override
            public PrintStream getOut() {
                return this.out;
            }

            @Override
            public PrintStream getErr() {
                return this.err;
            }

            @Override
            public void println(Object o) {
                getOut().println(o);
            }

            @Override
            public void print(Object o) {
                getOut().print(o);
            }

            @Override
            public void error(Object o) {
                getErr().println(o);
            }

            @Override
            public void prompt(String prompt) {
                S.append('#').append(prompt).append('#');
            }
        };
    }

    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=50">Issue #50</a>
     */
    @Test(timeout = 10000)
    public void check_for_memory_leak() throws Exception {
        final WeakReference<Object> reference = new WeakReference<Object>(new Interpreter().eval("x = new byte[1024 * 1000]; return x;"));
        while (reference.get() != null) {
            System.gc();
            Thread.sleep(1);
        }
    }


    @Test
    public void test_constructors() throws Exception {
        Interpreter one = new Interpreter();
        assertTrue(one.evalOnly);
        assertNull(one.getParent());
        assertEquals("<unknown source>", one.getSourceFileInfo());
        assertEquals("global", one.globalNameSpace.getName());
        NameSpace ns = new NameSpace("twoSpace");
        Interpreter two = new Interpreter(ns);
        assertTrue(two.evalOnly);
        assertNull(two.getParent());
        assertEquals("<unknown source>", two.getSourceFileInfo());
        assertEquals("twoSpace", two.globalNameSpace.getName());
        Interpreter three = new Interpreter(two);
        assertTrue(three.evalOnly);
        assertNotNull(three.getParent());
        assertSame(two, three.getParent());
        assertEquals("<unknown source>", three.getSourceFileInfo());
        assertEquals("twoSpace", three.globalNameSpace.getName());
        Interpreter four = new Interpreter(ns, one);
        assertTrue(four.evalOnly);
        assertNotNull(four.getParent());
        assertSame(one, four.getParent());
        assertEquals("<unknown source>", four.getSourceFileInfo());
        assertEquals("twoSpace", four.globalNameSpace.getName());
        Interpreter five = new Interpreter(ns, "known source");
        assertTrue(five.evalOnly);
        assertNull(five.getParent());
        assertEquals("known source", five.getSourceFileInfo());
        assertEquals("twoSpace", five.globalNameSpace.getName());
        Interpreter six = new Interpreter(ns, one, "known source");
        assertTrue(six.evalOnly);
        assertNotNull(six.getParent());
        assertSame(one, six.getParent());
        assertEquals("known source", six.getSourceFileInfo());
        assertEquals("twoSpace", six.globalNameSpace.getName());
        one.getNameSpace().clear();
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
    public void displays_the_prompt_by_given_console() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);
        //
        // default prompt
        //
        Interpreter bsh = new Interpreter(C);
        bsh.setExitOnEOF(false);
        bsh.run();
        assertEquals("#bsh % #", S.toString());
        S.delete(0, S.length());

        //
        // custom prompt
        //
        Interpreter bshc = new Interpreter(C, bsh);
        bshc.setExitOnEOF(false);
        bshc.set("bsh.prompt", "abc> ");
        bshc.run();
        assertEquals("#abc> #", S.toString());
        S.delete(0, S.length());
    }

    @Test
    public void get_the_prompt_by_command() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        Interpreter bsh = new Interpreter(C, new NameSpace("global"));
        bsh.setExitOnEOF(false);
        bsh.setConsole(C);
        assertEquals("bsh % ", bsh.eval("getBshPrompt();"));
    }

    @Test
    public void get_print_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        Interpreter bsh = new Interpreter(C);
        bsh.setExitOnEOF(false);
        bsh.print("foo");
        assertEquals("foo", bout.toString());
    }

    @Test
    public void get_println_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        Interpreter bsh = new Interpreter(C);
        bsh.setExitOnEOF(false);
        bsh.println("bar");
        assertEquals("bar", bout.toString().trim());
    }

    @Test
    public void get_error_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        Interpreter bsh = new Interpreter(C);
        bsh.setExitOnEOF(false);
        bsh.error("baz");
        assertEquals("// Error: baz", berr.toString().trim());
    }

    @Test
    public void get_debug_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        Interpreter bsh = new Interpreter(C);
        bsh.setExitOnEOF(false);
        Interpreter.Console.debug.println("bug");
        assertEquals("bug", berr.toString().trim());
    }


    @Test
    public void get_output_after_close() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        Interpreter bsh = new Interpreter(new StringReader(""),
                new PrintStream(bout), new PrintStream(berr), false);
        bsh.setExitOnEOF(false);
        bsh.getErr().close();
        bsh.getOut().close();
        bsh.print("foo");
        bsh.println("bar");
        bsh.error("baz");
        assertThat(bout.toString(), isEmptyString());
        assertThat(berr.toString(), isEmptyString());
    }

    @Test
    public void get_default_output() throws Exception {
        PrintStream berr = new PrintStream(new ByteArrayOutputStream());
        PrintStream bout = new PrintStream(new ByteArrayOutputStream());

        Interpreter bsh = new Interpreter(new StringReader(""), bout, berr, false);
        bsh.setExitOnEOF(false);
        assertEquals(berr, bsh.getErr());
        assertEquals(bout, bsh.getOut());
        bsh.setErr(null);
        bsh.setOut(null);
        assertEquals(System.err, bsh.getErr());
        assertEquals(System.out, bsh.getOut());
    }

    @Test
    public void set_prompt_by_interpreter() throws Exception {
        final StringReader in = new StringReader("\n");
        for (String P: new String[] { "abc> ", "cde# " }) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream() ) {
                Interpreter bsh = new Interpreter(in, new PrintStream(baos),
                    new PrintStream(baos), true);
                bsh.setExitOnEOF(false);
                bsh.set("bsh.prompt", P);
                bsh.run();
                assertTrue(baos.toString().contains(P));
            }
        }
    }

    @Test
    public void overwrite_get_prompt_by_interpreter() throws Exception {
        final StringReader in = new StringReader("\n");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream() ) {
            Interpreter bsh = new Interpreter(in, new PrintStream(baos),
                new PrintStream(baos), true);

            bsh.setExitOnEOF(false);
            bsh.eval("getBshPrompt() { return 'abc>'; }");
            bsh.run();
            assertTrue(baos.toString().contains("abc>"));
        }
    }

    @Test
    public void no_debug_prompt_interpreter() throws Exception {
        final StringReader in = new StringReader("\nInterpreter.DEBUG.set(true);\n");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CommandLineReader repl = new CommandLineReader(in) ) {
            Interpreter bsh = new Interpreter(repl, new PrintStream(baos),
                new PrintStream(baos), true);

            bsh.setExitOnEOF(false);
            bsh.run();
            Interpreter.DEBUG.set(false);
            assertTrue(baos.toString().contains("bsh %"));
            assertEquals(2, baos.toString().split("Debug:").length);
        }
    }

    @Test
    public void overwrite_get_prompt_exception() throws Exception {
        final StringReader in = new StringReader("\n");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream() ) {
            Interpreter bsh = new Interpreter(in, new PrintStream(baos),
                new PrintStream(baos), true);

            bsh.setExitOnEOF(false);
            bsh.eval("getBshPrompt() { throw new Exception('prompt exception'); }");
            bsh.run();
            assertTrue(baos.toString().contains("bsh %"));
        }
    }

    @Test
    public void reset_interpreter() throws Exception {
        final Interpreter bsh = new Interpreter();
        assertEquals("test123", bsh.eval("'test' + (100 + 20 + 3)"));
        long b4 = Runtime.getRuntime().freeMemory();
        bsh.reset();
        TestUtil.cleanUp();
        assertThat(b4, lessThan(Runtime.getRuntime().freeMemory()));
    }

}
