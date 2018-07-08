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
import java.io.PrintStream;
import java.io.Reader;

/**
 *
 */
public class SimpleConsole implements ConsoleInterface {

    private Reader in;
    private PrintStream out;
    private PrintStream err;

    public SimpleConsole(Reader in, PrintStream out, PrintStream err) {
        if (in == null) {
            throw new IllegalArgumentException("in can not be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out can not be null");
        }
        if (err == null) {
            throw new IllegalArgumentException("err can not be null");
        }
        this.in = in; this.out = out; this.err = err;
    }

    @Override
    public Reader getIn() {
        return in;
    }

    @Override
    public PrintStream getOut() {
        return out;
    }

    @Override
    public PrintStream getErr() {
        return err;
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
        this.in = in;
    }

    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public void setErr(PrintStream err) {
        this.err = err;
    }

}
