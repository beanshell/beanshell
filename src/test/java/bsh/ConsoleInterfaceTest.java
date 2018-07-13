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

import java.io.PrintStream;
import java.io.Reader;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 */
public class ConsoleInterfaceTest {
    @Test
    public void default_prompt() {
        final StringBuilder S = new StringBuilder();

        ConsoleInterface c = new ConsoleInterface() {
            @Override
            public Reader getIn() {
                return null;
            }

            @Override
            public PrintStream getOut() {
                return null;
            }

            @Override
            public PrintStream getErr() {
                return null;
            }

            @Override
            public void println(Object o) {
            }

            @Override
            public void print(Object o) {
                S.append(String.valueOf(o));
            }

            @Override
            public void error(Object o) {
            }
        };

        c.prompt("hello "); assertEquals("hello ", S.toString());
        c.prompt("world"); assertEquals("hello world", S.toString());
    }

    @Test
    public void given_prompt() {
        final StringBuilder S = new StringBuilder();

        ConsoleInterface c = new ConsoleInterface() {
            @Override
            public Reader getIn() {
                return null;
            }

            @Override
            public PrintStream getOut() {
                return null;
            }

            @Override
            public PrintStream getErr() {
                return null;
            }

            @Override
            public void println(Object o) {
            }

            @Override
            public void print(Object o) {
            }

            @Override
            public void error(Object o) {
            }

            @Override
            public void prompt(String prompt) {
                S.append('#').append(prompt).append('#');
            }
        };

        c.prompt("hello"); assertEquals("#hello#", S.toString());
        c.prompt("world"); assertEquals("#hello##world#", S.toString());
    }
}
