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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.Matchers.isEmptyString;

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
    public void displays_the_prompt_by_given_console() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);
        //
        // default prompt
        //
        try (Interpreter bsh = new Interpreter(C)) {
            bsh.setExitOnEOF(false);
            bsh.run();
            assertEquals("#bsh % #", S.toString());
            S.delete(0, S.length());

            //
            // custom prompt
            //
            try (Interpreter bshc = new Interpreter(C, bsh)) {
                bshc.setExitOnEOF(false);
                bshc.set("bsh.prompt", "abc> ");
                bshc.run();
                assertEquals("#abc> #", S.toString());
                S.delete(0, S.length());
            }
        }
    }

    @Test
    public void get_the_prompt_by_command() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        try (Interpreter bsh = new Interpreter(C, new NameSpace(null, "global"))) {
            bsh.setExitOnEOF(false);
            bsh.setConsole(C);
            assertEquals("bsh % ", bsh.eval("getBshPrompt();"));
        }
    }

    @Test
    public void get_print_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        try (Interpreter bsh = new Interpreter(C)) {
            bsh.setExitOnEOF(false);
            bsh.print("foo");
            assertEquals("foo", bout.toString());
        }
    }

    @Test
    public void get_println_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        try (Interpreter bsh = new Interpreter(C)) {
            bsh.setExitOnEOF(false);
            bsh.println("bar");
            assertEquals("bar", bout.toString().trim());
        }
    }

    @Test
    public void get_error_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        try (Interpreter bsh = new Interpreter(C)) {
            bsh.setExitOnEOF(false);
            bsh.error("baz");
            assertEquals("// Error: baz", berr.toString().trim());
        }
    }

    @Test
    public void get_debug_output() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final StringBuilder S = new StringBuilder();
        final ConsoleInterface C  = getConsole(S, bout, berr);

        try (Interpreter bsh = new Interpreter(C)) {
            bsh.setExitOnEOF(false);
            Interpreter.Console.debug.println("bug");
            assertEquals("bug", berr.toString().trim());
        }
    }


    @Test
    public void get_output_after_close() throws Exception {
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try (Interpreter bsh = new Interpreter(new StringReader(""),
                new PrintStream(bout), new PrintStream(berr), false)) {
            bsh.setExitOnEOF(false);
            bsh.close();
            bsh.print("foo");
            bsh.println("bar");
            bsh.error("baz");
            assertThat(bout.toString(), isEmptyString());
            assertThat(berr.toString(), isEmptyString());
       }
    }

    @Test
     public void set_prompt_by_interpreter() throws Exception {
         final StringReader in = new StringReader("\n");
         for (String P: new String[] { "abc> ", "cde# " }) {
             try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     Interpreter bsh = new Interpreter(in, new PrintStream(baos),
                             new PrintStream(baos), true)) {
                bsh.setExitOnEOF(false);
                bsh.set("bsh.prompt", P);
                bsh.run();
                assertTrue(baos.toString().contains(P));
            }
        }
     }

}
