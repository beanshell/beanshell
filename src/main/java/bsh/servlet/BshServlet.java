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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import bsh.EvalError;
import bsh.FileReader;
import bsh.Interpreter;

/**
    This file is part of BeanShell - www.beanshell.org

    @author Pat Niemeyer
*/
public class BshServlet extends HttpServlet
{
    private static final String bshVersion;
    static String exampleScript = "print(\"hello!\");";

    static {
            /*
            We have included a getVersion() command to detect the version
            of bsh.  If bsh is packaged in the WAR file it could access it
            directly as a bsh command.  But if bsh is in the app server's
            classpath it won't see it here, so we will source it directly.

            This command works around the lack of a coherent version number
            in the early versions.
        */
        String tmp = "BeanShell: unknown version";
        try ( FileReader reader = new FileReader(
                BshServlet.class.getResource("getVersion.bsh").openStream());
                Interpreter bsh = new Interpreter() ) {
            bsh.eval( reader );
            tmp = (String)bsh.eval( "getVersion()" );
        } catch ( Exception e ) { /* ignore fall back on original value */ }
        bshVersion = tmp;
    }
    static String getBshVersion()
    {
        return bshVersion;
    }

    public void doGet(
        HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String script = request.getParameter("bsh.script");
        String client = request.getParameter("bsh.client");
        String output = request.getParameter("bsh.servlet.output");
        String captureOutErr =
            request.getParameter("bsh.servlet.captureOutErr");
        boolean capture = false;
        if ( captureOutErr != null && captureOutErr.equalsIgnoreCase("true") )
            capture = true;

        Object scriptResult = null;
        Exception scriptError = null;
        StringBuilder scriptOutput = new StringBuilder();
        if ( script != null ) {
            try {
                scriptResult = evalScript(
                    script, scriptOutput, capture, request, response );
            } catch ( Exception e ) {
                scriptError = e;
            }
        }

        response.setHeader( "Bsh-Return", String.valueOf(scriptResult) );

        if ( ( output != null && output.equalsIgnoreCase("raw") )
                || ( client != null && client.equals("Remote") ) )
            sendRaw(
                request, response, scriptError, scriptResult, scriptOutput );
        else
            sendHTML( request, response, script, scriptError,
                scriptResult, scriptOutput, capture );
    }

    void sendHTML(
        HttpServletRequest request, HttpServletResponse response,
        String script, Exception scriptError, Object scriptResult,
        StringBuilder scriptOutput, boolean capture )
        throws IOException
    {
        // Format the output using a simple templating utility
        SimpleTemplate st = new SimpleTemplate(
            BshServlet.class.getResource("page.template") );
        st.replace( "version", getBshVersion() );

        //String requestURI = HttpUtils.getRequestURL( request ).toString()
        // I was told this should work
        String requestURI = request.getRequestURI();

        st.replace( "servletURL", requestURI );
        if ( script != null )
            st.replace( "script", script );
        else
            st.replace( "script", exampleScript );
        if ( capture )
            st.replace( "captureOutErr", "CHECKED" );
        else
            st.replace( "captureOutErr", "" );
        if ( script != null )
            st.replace( "scriptResult",
                formatScriptResultHTML(
                    script, scriptResult, scriptError, scriptOutput ) );

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        st.write(out);
        out.flush();
        out.close();
    }

    void sendRaw(
        HttpServletRequest request, HttpServletResponse response,
        Exception scriptError, Object scriptResult, StringBuilder scriptOutput )
        throws IOException
    {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        if ( scriptError != null )
            out.println( "Script Error:\n"+scriptError );
        else
            out.println( scriptOutput.toString() );
        out.flush();
        out.close();
    }

    /**
    */
    String formatScriptResultHTML(
        String script, Object result, Exception error,
        StringBuilder scriptOutput )
        throws IOException
    {
        SimpleTemplate tmplt;

        if ( error != null )
        {
            tmplt = new SimpleTemplate(
                getClass().getResource("error.template") );

            String errString;

            if ( error instanceof EvalError )
            {
                EvalError evalError = (EvalError)error;
                int lineNo = evalError.getErrorLineNumber();
                String msg = evalError.getRawMessage();
                int contextLines = 4;
                errString = escape(msg);
                if ( lineNo > -1 )
                    errString += "<hr>"
                        + showScriptContextHTML( script, lineNo, contextLines );
            } else
                errString = escape( error.toString() );

            tmplt.replace("error", errString );
        } else {
            tmplt = new SimpleTemplate(
                getClass().getResource("result.template") );
            tmplt.replace( "value", escape(String.valueOf(result)) );
            tmplt.replace( "output", escape(scriptOutput.toString()) );
        }

        return tmplt.toString();
    }

    /*
        Show context number lines of string before and after target line.
        Add HTML formatting to bold the target line.
    */
    String showScriptContextHTML( String s, int lineNo, int context )
    {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader( new StringReader(s) );

        int beginLine = Math.max( 1, lineNo-context );
        int endLine = lineNo + context;
        for( int i=1; i<=lineNo+context+1; i++ )
        {
            if ( i < beginLine )
            {
                try {
                    br.readLine();
                } catch ( IOException e ) {
                    throw new RuntimeException( e.toString() );
                }
                continue;
            }
            if ( i > endLine )
                break;

            String line;
            try {
                line = br.readLine();
            } catch ( IOException e ) {
                throw new RuntimeException( e.toString() );
            }

            if ( line == null )
                break;
            if ( i == lineNo )
                sb.append( "<font color=\"red\">"+i+": "+line +"</font><br/>" );
            else
                sb.append( i+": " +line +"<br/>" );
        }

        return sb.toString();
    }
    public void doPost(
        HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        doGet( request, response );
    }

    Object evalScript(
        String script, StringBuilder scriptOutput, boolean captureOutErr,
        HttpServletRequest request, HttpServletResponse response )
        throws EvalError
    {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintStream pout = new PrintStream( baos, true, "UTF-8" );
             // Create an interpreter instance with a null inputstream,
             // the capture out/err stream, non-interactive
             Interpreter bsh = new Interpreter( null, pout, pout, false )) {

            // set up interpreter
            bsh.set( "bsh.httpServletRequest", request );
            bsh.set( "bsh.httpServletResponse", response );

            // Eval the text, gathering the return value or any error.
            Object result = null;
            PrintStream sout = System.out;
            PrintStream serr = System.err;
            if ( captureOutErr ) {
                System.setOut( pout );
                System.setErr( pout );
            }
            try {
                // Eval the user text
                result = bsh.eval( script );
            } finally {
                if ( captureOutErr ) {
                    System.setOut( sout );
                    System.setErr( serr );
                }
            }
            scriptOutput.append( baos.toString("UTF-8") );
            return result;
        } catch (IOException e) { /* ignore */ }
        throw new EvalError("Script evaluation failed", null, null);
    }

    /**
    * Convert special characters to entities for XML output
    */
    public static String escape(String value)
    {
        String search = "&<>";
        String[] replace = {"&amp;", "&lt;", "&gt;"};

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            int pos = search.indexOf(c);
            if (pos < 0)
                buf.append(c);
            else
                buf.append(replace[pos]);
        }

        return buf.toString();
    }

}


