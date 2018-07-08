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
package bsh.console;

import bsh.ConsoleInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 */
public class SimpleConsoleTest {

    @Test
    public void construct_and_hold() {
        try {
            new SimpleConsole(null, System.out, System.err);
            fail("missing argument sanity check");
        } catch (IllegalArgumentException x) {
            assertTrue(x.getMessage().equals("in can not be null"));
        }

        try {
            new SimpleConsole(new InputStreamReader(System.in), null, System.err);
            fail("missing argument sanity check");
        } catch (IllegalArgumentException x) {
            assertTrue(x.getMessage().equals("out can not be null"));
        }

        try {
            new SimpleConsole(new InputStreamReader(System.in), System.out, null);
            fail("missing argument sanity check");
        } catch (IllegalArgumentException x) {
            assertTrue(x.getMessage().equals("err can not be null"));
        }

        Reader      in  = new InputStreamReader(System.in);
        PrintStream out = System.out;
        PrintStream err = System.err;

        ConsoleInterface C = new SimpleConsole(in, out, err);
        assertTrue("unexpected input stream", C.getIn() == in);
        assertTrue("unexpected output stream", C.getOut() == out);
        assertTrue("unexpected error stream", C.getErr() == err);

        in = new StringReader("");
        out = new PrintStream(new ByteArrayOutputStream());
        err = new PrintStream(new ByteArrayOutputStream());

        C = new SimpleConsole(in, out, err);
        assertTrue("unexpected input stream", C.getIn() == in);
        assertTrue("unexpected output stream", C.getOut() == out);
        assertTrue("unexpected error stream", C.getErr() == err);
    }

    @Test
    public void print_and_println() {
        Reader in = new StringReader("");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleInterface C = new SimpleConsole(in, new PrintStream(out), System.err);
        C.print("hello"); assertEquals("hello", out.toString());
        C.print(" ");
        C.print("world"); assertEquals("hello world", out.toString());

        out = new ByteArrayOutputStream();

        C = new SimpleConsole(in, new PrintStream(out), System.err);
        C.println("hello"); assertEquals("hello\n", out.toString());
        C.println("world"); assertEquals("hello\nworld\n", out.toString());
    }

    @Test
    public void error() {
        Reader in = new StringReader("");
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        ConsoleInterface C = new SimpleConsole(in, System.out, new PrintStream(err));
        C.error("hello"); assertEquals("hello\n", err.toString());
        C.error("world"); assertEquals("hello\nworld\n", err.toString());
    }

    @Test
    public void prompt() {
        Reader in = new StringReader("");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleInterface C = new SimpleConsole(in, new PrintStream(out), System.err);
        C.prompt("bsh> "); assertEquals("bsh> ", out.toString());
        C.prompt("> "); assertEquals("bsh> > ", out.toString());
    }

    @Test
    /**
     * Some beanshell code used to change output and error streams; we therefore
     * support this behaviour with setters
     */
    public void set_streams() {
        Reader      in  = new InputStreamReader(System.in);
        PrintStream out = System.out;
        PrintStream err = System.err;

        ConsoleInterface C = new SimpleConsole(in, out, err);

        in  = new InputStreamReader(System.in);
        out = new PrintStream(System.out);
        err = new PrintStream(System.err);

        C.setIn ( in); assertSame(in,  C.getIn() );
        C.setOut(out); assertSame(out, C.getOut());
        C.setErr(err); assertSame(err, C.getErr());

    }


}
