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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * A very simple httpd that supports the remote server mode.
 * Files are loaded relative to the classpath (as resources).
 *
 * Warning: this is not secure! This server can probably be duped into
 * serving any file on your system! Beware!
 *
 * Note: at some point this should be recast as a beanshell script.
 */
public class Httpd extends Thread {

    /** The ss. */
    ServerSocket ss;

    /**
     * The main method.
     *
     * @param argv
     *            the arguments
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static void main(final String argv[]) throws IOException {
        new Httpd(Integer.parseInt(argv[0])).start();
    }

    /**
     * Instantiates a new httpd.
     *
     * @param port
     *            the port
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public Httpd(final int port) throws IOException {
        this.ss = new ServerSocket(port);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        // System.out.println("starting httpd...");
        try {
            while (true)
                new HttpdConnection(this.ss.accept()).start();
        } catch (final IOException e) {
            System.out.println(e);
        }
    }
}

/**
 * The Class HttpdConnectionData.
 */
class HttpdConnection extends Thread {

    /** The client. */
    public Socket client;

    /** The in. */
    public BufferedReader in;

    /** The out. */
    public OutputStream out;

    /** The pout. */
    public PrintStream pout;

    /** The is http 1. */
    public boolean isHttp1;

    /**
     * Instantiates a new httpd connection data.
     *
     * @param client
     *            the client
     */
    HttpdConnection(final Socket client) {
        this.client = client;
        this.setPriority(NORM_PRIORITY - 1);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            this.in = new BufferedReader(
                    new InputStreamReader(this.client.getInputStream()));
            this.out = this.client.getOutputStream();
            this.pout = new PrintStream(this.out);
            final String request = this.in.readLine();
            if (request == null)
                this.error(400, "Empty Request");
            if (request.toLowerCase().indexOf("http/1.") != -1) {
                String s;
                while (!(s = this.in.readLine()).equals("") && s != null);
                this.isHttp1 = true;
            }
            final StringTokenizer st = new StringTokenizer(request);
            if (st.countTokens() < 2)
                this.error(400, "Bad Request");
            else {
                final String command = st.nextToken();
                if (command.equals("GET"))
                    this.serveFile(st.nextToken());
                else
                    this.error(400, "Bad Request");
            }
            this.client.close();
        } catch (final IOException e) {
            System.out.println("I/O error " + e);
            try {
                this.client.close();
            } catch (final Exception e2) {}
        }
    }

    /**
     * Serve file.
     *
     * @param file
     *            the file
     * @throws FileNotFoundException
     *             the file not found exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void serveFile(String file)
            throws FileNotFoundException, IOException {
        // Do some mappings
        if (file.equals("/"))
            file = "/remote/remote.html";
        if (file.startsWith("/remote/"))
            file = "/bsh/util/lib/" + file.substring(8);
        /*
         * if(file.startsWith("/"))
         * file = file.substring(1);
         * if(file.endsWith("/") || file.equals(""))
         * file = file + "index.html";
         * if(!fileAccessOK(file))
         * {
         * error(403, "Forbidden");
         * return;
         * }
         */
        // don't send java packages over... (e.g. swing)
        if (file.startsWith("/java"))
            this.error(404, "Object Not Found");
        else
            try {
                System.out.println("sending file: " + file);
                this.sendFileData(file);
            } catch (final FileNotFoundException e) {
                this.error(404, "Object Not Found");
            }
    }

    /**
     * Send file data.
     *
     * @param file
     *            the file
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws FileNotFoundException
     *             the file not found exception
     */
    private void sendFileData(final String file)
            throws IOException, FileNotFoundException {
        /*
         * Why aren't resources being found when this runs on Win95?
         */
        final InputStream fis = this.getClass().getResourceAsStream(file);
        if (fis == null)
            throw new FileNotFoundException(file);
        final byte[] data = new byte[fis.available()];
        if (this.isHttp1) {
            this.pout.println("HTTP/1.0 200 Document follows");
            this.pout.println("Content-length: " + data.length);
            if (file.endsWith(".gif"))
                this.pout.println("Content-type: image/gif");
            else if (file.endsWith(".html") || file.endsWith(".htm"))
                this.pout.println("Content-Type: text/html");
            else
                this.pout.println("Content-Type: application/octet-stream");
            this.pout.println();
        }
        int bytesread = 0;
        // Never, ever trust available()
        do {
            bytesread = fis.read(data);
            if (bytesread > 0)
                this.pout.write(data, 0, bytesread);
        }
        while (bytesread != -1);
        this.pout.flush();
    }

    /**
     * Error.
     *
     * @param num
     *            the num
     * @param s
     *            the s
     */
    private void error(final int num, String s) {
        s = "<html><h1>" + s + "</h1></html>";
        if (this.isHttp1) {
            this.pout.println("HTTP/1.0 " + num + " " + s);
            this.pout.println("Content-type: text/html");
            this.pout.println("Content-length: " + s.length() + "\n");
        }
        this.pout.println(s);
    }
}
