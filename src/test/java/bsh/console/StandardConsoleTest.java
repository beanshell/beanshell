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
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 */
public class StandardConsoleTest {

    @Test
    public void construct_and_hold() {
        ConsoleInterface C = new StandardConsole();
        assertSame(StandardConsole.IN, C.getIn());
        assertSame(System.out, C.getOut());
        assertSame(System.err, C.getErr());
    }

    @Test
    /**
     * Some beanshell code used to change output and error streams; we therefore
     * support this behaviour with setters
     */
    public void set_streams_not_allowed() {
        Reader      in  = new InputStreamReader(System.in);
        PrintStream out = System.out;
        PrintStream err = System.err;

        ConsoleInterface C = new StandardConsole();

        try {
            C.setIn (in);
            fail("missing error");
        } catch (IllegalArgumentException x) {
            //
            // OK
            //
        }
        try {
            C.setOut(out);
            fail("missing error");
        } catch (IllegalArgumentException x) {
            //
            // OK
            //
        }
        try {
            C.setErr(err);
            fail("missing error");
        } catch (IllegalArgumentException x) {
            //
            // OK
            //
        }
    }


}
