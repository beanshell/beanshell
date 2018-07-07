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
import java.io.StringReader;
import java.lang.ref.WeakReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    public void prompt_displays_the_prompt() throws Exception {
        for (String P: new String[] { "abc> ", "cde# " }) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Interpreter bsh = new Interpreter(new InputStreamReader(System.in), new PrintStream(baos), System.err, false);

            bsh.prompt(P);
            assertTrue(baos.toString().contains(P));
        }
    }

     @Test
     public void set_prompt_by_interpreter() throws Exception {
         final StringReader in = new StringReader("\n");
         for (String P: new String[] { "abc> ", "cde# " }) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Interpreter bsh = new Interpreter(in, new PrintStream(baos), System.err, true);
            bsh.setExitOnEOF(false);
                        bsh.set("bsh.prompt", P);
            bsh.run();
            System.out.println(baos.toString());
            assertTrue(baos.toString().contains(P));
        }
     }

}
