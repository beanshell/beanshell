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
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class InterpreterConsoleTest {

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=50">Issue #50</a>
     */

    @Test
    public void read_from_provieded_reader() throws Exception {
        final StringReader r = new StringReader("var = \"hello\";");

        exit.expectSystemExit();

        Interpreter bsh = new Interpreter(givenConsole(r));
        bsh.run();

        assertEquals("hello", bsh.get("var"));
    }

    @Test
    public void write_to_provieded_printer() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final StringReader r = new StringReader("print(\"hello\");");
        final PrintStream p = new PrintStream(baos);

        exit.expectSystemExit();

        Interpreter bsh = new Interpreter(givenConsole(r, p));
        bsh.run();

        assertEquals("hello", baos.toString());
    }

    @Test
    public void write_to_provieded_error() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final StringReader r = new StringReader("error(\"hello\");");
        final PrintStream p = new PrintStream(baos);

        exit.expectSystemExit();

        Interpreter bsh = new Interpreter(givenConsole(r, p));
        bsh.run();

        assertEquals("// Error: hello", baos.toString());
    }

    @Test
    public void set_prompt_at_start() {
        final ByteArrayOutputStream w = new ByteArrayOutputStream();
        final StringReader r = new StringReader("");

        exit.expectSystemExit();

        ConsoleInterface console = givenConsole(r, new PrintStream(w));
        Interpreter bsh = new Interpreter(console);
        bsh.run();

        assertEquals("bsh", w.toString());
    }

    @Test
    public void set_prompt_when_needed() {
        final ByteArrayOutputStream w = new ByteArrayOutputStream();
        final StringReader r = new StringReader("\n");

        exit.expectSystemExit();

        ConsoleInterface console = givenConsole(r, new PrintStream(w));
        Interpreter bsh = new Interpreter(console);
        bsh.run();

        assertEquals("bsh", w.toString());
    }

    // --------------------------------------------------------- private methods

    private ConsoleInterface givenConsole(Reader in) {
        return givenConsole(in, null, null);
    }

    private ConsoleInterface givenConsole(Reader in, PrintStream out) {
        return givenConsole(in, out, null);
    }

    private ConsoleInterface givenConsole(final Reader in, final PrintStream out, final PrintStream err) {
        return new TestingConsole(in, out, err);
    }

    // ---------------------------------------------------------- TestingConsole

    private static class TestingConsole implements ConsoleInterface {
        private String prompt;
        private Reader in;
        private PrintStream out, err;

        public TestingConsole(Reader in, PrintStream out, PrintStream err) {
            this.in = in;
            this.out = out;
            this.err = err;
        }
        @Override
        public Reader getIn() {
            return (in == null) ? new InputStreamReader(System.in) : in;
        }

        @Override
        public PrintStream getOut() {
            return (out == null) ? System.out : out;
        }

        @Override
        public PrintStream getErr() {
            return (err == null) ? System.err : out;
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
            this.prompt = prompt;
        }
    }

}
