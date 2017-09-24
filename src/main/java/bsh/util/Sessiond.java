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
package bsh.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import bsh.Interpreter;
import bsh.NameSpace;

/**
 * BeanShell remote session server.
 * Starts instances of bsh for client connections.
 * Note: the sessiond effectively maps all connections to the same interpreter
 * (shared namespace).
 */
public class Sessiond extends Thread {

    /** The ss. */
    private final ServerSocket ss;
    /** The global name space. */
    NameSpace globalNameSpace;

    /*
     * public static void main(String argv[]) throws IOException
     * {
     * new Sessiond(Integer.parseInt(argv[0])).start();
     * }
     */

    /**
     * Instantiates a new sessiond.
     *
     * @param globalNameSpace
     *            the global name space
     * @param port
     *            the port
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public Sessiond(final NameSpace globalNameSpace, final int port)
            throws IOException {
        this.ss = new ServerSocket(port);
        this.globalNameSpace = globalNameSpace;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            while (true)
                new SessiondConnection(this.globalNameSpace, this.ss.accept())
                        .start();
        } catch (final IOException e) {
            System.out.println(e);
        }
    }
}

/**
 * The Class SessiondConnection.
 */
class SessiondConnection extends Thread {

    /** The global name space. */
    NameSpace globalNameSpace;

    /** The client. */
    Socket client;

    /**
     * Instantiates a new sessiond connection.
     *
     * @param globalNameSpace
     *            the global name space
     * @param client
     *            the client
     */
    SessiondConnection(final NameSpace globalNameSpace, final Socket client) {
        this.client = client;
        this.globalNameSpace = globalNameSpace;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            final InputStream in = this.client.getInputStream();
            final PrintStream out = new PrintStream(
                    this.client.getOutputStream());
            final Interpreter i = new Interpreter(new InputStreamReader(in),
                    out, out, true, this.globalNameSpace);
            i.setExitOnEOF(false); // don't exit interp
            i.run();
        } catch (final IOException e) {
            System.out.println(e);
        }
    }
}
