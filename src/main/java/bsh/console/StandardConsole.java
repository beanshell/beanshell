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
import java.io.Serializable;
import static java.lang.System.err;
import static java.lang.System.in;
import static java.lang.System.out;

/**
 * This is the simplest console possible: it just wraps System streams (that is
 * why the name StandardConsole).
 */
public class StandardConsole implements ConsoleInterface, Serializable {

    final protected static Reader IN = new InputStreamReader(in);

    @Override
    public Reader getIn() {
        return IN;
    }

    @Override
    public PrintStream getOut() {
        return System.out;
    }

    @Override
    public PrintStream getErr() {
        return System.err;
    }

    @Override
    public void println(Object o) {
        out.println(o); out.flush();
    }

    @Override
    public void print(Object o) {
        out.print(o); out.flush();
    }

    @Override
    public void error(Object o) {
        err.println(o); err.flush();
    }

    @Override
    public void prompt(String prompt) {
        print(prompt);
    }

    @Override
    public void setIn(Reader in) {
        throw new IllegalArgumentException("System.in can not be overriden");
    }

    @Override
    public void setOut(PrintStream out) {
        throw new IllegalArgumentException("System.out can not be overriden");
    }

    @Override
    public void setErr(PrintStream err) {
        throw new IllegalArgumentException("System.err can not be overriden");
    }

}
