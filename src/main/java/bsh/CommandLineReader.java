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

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * This is a quick hack to turn empty lines entered interactively on the
 * command line into ';\n' empty lines for the interpreter. It's just more
 * pleasant to be able to hit return on an empty line and see the prompt
 * reappear.
 *
 * This is *not* used when text is sourced from a file non-interactively.
 */
class CommandLineReader extends FilterReader {

    /**
     * Instantiates a new command line reader.
     *
     * @param in
     *            the in
     */
    public CommandLineReader(final Reader in) {
        super(in);
    }

    /** The Constant sentSemi. */
    static final int normal = 0, lastCharNL = 1, sentSemi = 2;
    /** The state. */
    int state = lastCharNL;

    /** {@inheritDoc} */
    @Override
    public int read() throws IOException {
        int b;
        if (this.state == sentSemi) {
            this.state = lastCharNL;
            return '\n';
        }
        // skip CR
        while ((b = this.in.read()) == '\r');
        if (b == '\n')
            if (this.state == lastCharNL) {
                b = ';';
                this.state = sentSemi;
            } else
                this.state = lastCharNL;
        else
            this.state = normal;
        return b;
    }

    /**
     * This is a degenerate implementation.
     * I don't know how to keep this from blocking if we try to read more
     * than one char... There is no available() for Readers ??
     *
     * @param buff
     *            the buff
     * @param off
     *            the off
     * @param len
     *            the len
     * @return the int
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    @Override
    public int read(final char buff[], final int off, final int len)
            throws IOException {
        final int b = this.read();
        if (b == -1)
            return -1; // EOF, not zero read apparently
        else {
            buff[off] = (char) b;
            return 1;
        }
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     * @throws Exception
     *             the exception
     */
    // Test it
    public static void main(final String[] args) throws Exception {
        final Reader in = new CommandLineReader(
                new InputStreamReader(System.in));
        while (true)
            System.out.println(in.read());
    }
}
