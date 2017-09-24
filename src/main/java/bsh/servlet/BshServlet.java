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
package bsh.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import bsh.EvalError;
import bsh.Interpreter;

/**
 * This file is part of BeanShell - www.beanshell.org.
 *
 * @author Pat Niemeyer
 */
public class BshServlet extends HttpServlet {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The bsh version. */
    static String bshVersion;
    /** The example script. */
    static String exampleScript = "print(\"hello!\");";

    /**
     * Gets the bsh version.
     *
     * @return the bsh version
     */
    static String getBshVersion() {
        if (bshVersion != null)
            return bshVersion;
        /*
         * We have included a getVersion() command to detect the version
         * of bsh. If bsh is packaged in the WAR file it could access it
         * directly as a bsh command. But if bsh is in the app server's
         * classpath it won't see it here, so we will source it directly.
         * This command works around the lack of a coherent version number
         * in the early versions.
         */
        final Interpreter bsh = new Interpreter();
        try {
            bsh.eval(new InputStreamReader(BshServlet.class
                    .getResource("getVersion.bsh").openStream()));
            bshVersion = (String) bsh.eval("getVersion()");
        } catch (final Exception e) {
            bshVersion = "BeanShell: unknown version";
        }
        return bshVersion;
    }

    /** {@inheritDoc} */
    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response)
            throws ServletException, IOException {
        final String script = request.getParameter("bsh.script");
        final String client = request.getParameter("bsh.client");
        final String output = request.getParameter("bsh.servlet.output");
        final String captureOutErr = request
                .getParameter("bsh.servlet.captureOutErr");
        boolean capture = false;
        if (captureOutErr != null && captureOutErr.equalsIgnoreCase("true"))
            capture = true;
        Object scriptResult = null;
        Exception scriptError = null;
        final StringBuffer scriptOutput = new StringBuffer();
        if (script != null)
            try {
                scriptResult = this.evalScript(script, scriptOutput, capture,
                        request, response);
            } catch (final Exception e) {
                scriptError = e;
            }
        response.setHeader("Bsh-Return", String.valueOf(scriptResult));
        if (output != null && output.equalsIgnoreCase("raw")
                || client != null && client.equals("Remote"))
            this.sendRaw(request, response, scriptError, scriptResult,
                    scriptOutput);
        else
            this.sendHTML(request, response, script, scriptError, scriptResult,
                    scriptOutput, capture);
    }

    /**
     * Send HTML.
     *
     * @param request
     *            the request
     * @param response
     *            the response
     * @param script
     *            the script
     * @param scriptError
     *            the script error
     * @param scriptResult
     *            the script result
     * @param scriptOutput
     *            the script output
     * @param capture
     *            the capture
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void sendHTML(final HttpServletRequest request,
            final HttpServletResponse response, final String script,
            final Exception scriptError, final Object scriptResult,
            final StringBuffer scriptOutput, final boolean capture)
            throws IOException {
        // Format the output using a simple templating utility
        final SimpleTemplate st = new SimpleTemplate(
                BshServlet.class.getResource("page.template"));
        st.replace("version", getBshVersion());
        // String requestURI = HttpUtils.getRequestURL(request).toString()
        // I was told this should work
        final String requestURI = request.getRequestURI();
        st.replace("servletURL", requestURI);
        if (script != null)
            st.replace("script", script);
        else
            st.replace("script", exampleScript);
        if (capture)
            st.replace("captureOutErr", "CHECKED");
        else
            st.replace("captureOutErr", "");
        if (script != null)
            st.replace("scriptResult", this.formatScriptResultHTML(script,
                    scriptResult, scriptError, scriptOutput));
        response.setContentType("text/html");
        final PrintWriter out = response.getWriter();
        st.write(out);
        out.flush();
    }

    /**
     * Send raw.
     *
     * @param request
     *            the request
     * @param response
     *            the response
     * @param scriptError
     *            the script error
     * @param scriptResult
     *            the script result
     * @param scriptOutput
     *            the script output
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void sendRaw(final HttpServletRequest request,
            final HttpServletResponse response, final Exception scriptError,
            final Object scriptResult, final StringBuffer scriptOutput)
            throws IOException {
        response.setContentType("text/plain");
        final PrintWriter out = response.getWriter();
        if (scriptError != null)
            out.println("Script Error:\n" + scriptError);
        else
            out.println(scriptOutput.toString());
        out.flush();
    }

    /**
     * Format script result HTML.
     *
     * @param script
     *            the script
     * @param result
     *            the result
     * @param error
     *            the error
     * @param scriptOutput
     *            the script output
     * @return the string
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    String formatScriptResultHTML(final String script, final Object result,
            final Exception error, final StringBuffer scriptOutput)
            throws IOException {
        SimpleTemplate tmplt;
        if (error != null) {
            tmplt = new SimpleTemplate(
                    this.getClass().getResource("error.template"));
            String errString;
            if (error instanceof bsh.EvalError) {
                final int lineNo = ((EvalError) error).getErrorLineNumber();
                final String msg = error.getMessage();
                final int contextLines = 4;
                errString = escape(msg);
                if (lineNo > -1)
                    errString += "<hr>" + this.showScriptContextHTML(script,
                            lineNo, contextLines);
            } else
                errString = escape(error.toString());
            tmplt.replace("error", errString);
        } else {
            tmplt = new SimpleTemplate(
                    this.getClass().getResource("result.template"));
            tmplt.replace("value", escape(String.valueOf(result)));
            tmplt.replace("output", escape(scriptOutput.toString()));
        }
        return tmplt.toString();
    }

    /**
     * Show script context HTML.
     *
     * @param s
     *            the s
     * @param lineNo
     *            the line no
     * @param context
     *            the context
     * @return the string
     *
     * Show context number lines of string before and after target line.
     * Add HTML formatting to bold the target line.
     */
    String showScriptContextHTML(final String s, final int lineNo,
            final int context) {
        final StringBuffer sb = new StringBuffer();
        final BufferedReader br = new BufferedReader(new StringReader(s));
        final int beginLine = Math.max(1, lineNo - context);
        final int endLine = lineNo + context;
        for (int i = 1; i <= lineNo + context + 1; i++) {
            if (i < beginLine) {
                try {
                    br.readLine();
                } catch (final IOException e) {
                    throw new RuntimeException(e.toString());
                }
                continue;
            }
            if (i > endLine)
                break;
            String line;
            try {
                line = br.readLine();
            } catch (final IOException e) {
                throw new RuntimeException(e.toString());
            }
            if (line == null)
                break;
            if (i == lineNo)
                sb.append("<font color=\"red\">" + i + ": " + line
                        + "</font><br/>");
            else
                sb.append(i + ": " + line + "<br/>");
        }
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response)
            throws ServletException, IOException {
        this.doGet(request, response);
    }

    /**
     * Eval script.
     *
     * @param script
     *            the script
     * @param scriptOutput
     *            the script output
     * @param captureOutErr
     *            the capture out err
     * @param request
     *            the request
     * @param response
     *            the response
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    Object evalScript(final String script, final StringBuffer scriptOutput,
            final boolean captureOutErr, final HttpServletRequest request,
            final HttpServletResponse response) throws EvalError {
        // Create a PrintStream to capture output
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream pout = new PrintStream(baos);
        // Create an interpreter instance with a null inputstream,
        // the capture out/err stream, non-interactive
        final Interpreter bsh = new Interpreter(null, pout, pout, false);
        // set up interpreter
        bsh.set("bsh.httpServletRequest", request);
        bsh.set("bsh.httpServletResponse", response);
        // Eval the text, gathering the return value or any error.
        Object result = null;
        final PrintStream sout = System.out;
        final PrintStream serr = System.err;
        if (captureOutErr) {
            System.setOut(pout);
            System.setErr(pout);
        }
        try {
            // Eval the user text
            result = bsh.eval(script);
        } finally {
            if (captureOutErr) {
                System.setOut(sout);
                System.setErr(serr);
            }
        }
        pout.flush();
        scriptOutput.append(baos.toString());
        return result;
    }

    /**
     * Convert special characters to entities for XML output.
     *
     * @param value
     *            the value
     * @return the string
     */
    public static String escape(final String value) {
        final String search = "&<>";
        final String[] replace = {"&amp;", "&lt;", "&gt;"};
        final StringBuffer buf = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            final int pos = search.indexOf(c);
            if (pos < 0)
                buf.append(c);
            else
                buf.append(replace[pos]);
        }
        return buf.toString();
    }
}
