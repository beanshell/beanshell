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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
/**
    Remote executor class. Posts a script from the command line to a BshServlet
    or embedded  interpreter using (respectively) HTTP or the bsh telnet
    service. Output is printed to stdout and a numeric return value is scraped
    from the result.
*/
public class Remote
{
    public static void main( String args[] )
        throws Exception
    {
        if ( args.length < 2 ) {
            System.out.println(
                "usage: Remote URL(http|bsh) file [ file ] ... ");
            System.exit(1);
        }
        String url = args[0];
        String text = getFile(args[1]);
        int ret = eval( url, text );
        System.exit( ret );
        }

    /**
        Evaluate text in the interpreter at url, returning a possible integer
        return value.
    */
    public static int eval( String url, String text )
        throws IOException
    {
        String returnValue = null;
        if ( url.startsWith( "http:" ) ) {
            returnValue = doHttp( url, text );
        } else if ( url.startsWith( "bsh:" ) ) {
            returnValue = doBsh( url, text );
        } else
            throw new IOException( "Unrecognized URL type."
                +"Scheme must be http:// or bsh://");

        try {
            return Integer.parseInt( returnValue );
        } catch ( Exception e ) {
            // this convention may change...
            return 0;
        }
    }

    static String doBsh( String url, String text )
    {
        OutputStream out;
        InputStream in;
        String host = "";
        String port = "";
        String returnValue = "-1";
        String orgURL = url;

        // Need some format checking here
        try {
            url = url.substring(6); // remove the bsh://
            // get the index of the : between the host and the port is located
            int index = url.indexOf(":");
            host = url.substring(0,index);
            port = url.substring(index+1,url.length());
        } catch ( Exception ex ) {
            System.err.println("Bad URL: "+orgURL+": "+ex  );
            return returnValue;
        }

        try {
            System.out.println("Connecting to host : "
                + host + " at port : " + port);
            Socket s = new Socket(host, Integer.parseInt(port) + 1);

            out = s.getOutputStream();
            in = s.getInputStream();

            sendLine( text, out );

            BufferedReader bin = new BufferedReader(
                new InputStreamReader(in));
              String line;
              while ( (line=bin.readLine()) != null )
                System.out.println( line );

            // Need to scrape a value from the last line?
            returnValue="1";
            return returnValue;
        } catch(Exception ex) {
            System.err.println("Error communicating with server: "+ex);
            return returnValue;
        }
    }

    private static void sendLine( String line, OutputStream outPipe )
        throws IOException
    {
        outPipe.write( line.getBytes() );
        outPipe.flush();
    }


    /*
        TODO: this is not unicode friendly, nor is getFile()
        The output is urlencoded 8859_1 text.
        should probably be urlencoded UTF-8... how does the servlet determine
        the encoded charset?  I guess we're supposed to add a ";charset" clause
        to the content type?
    */
    static String doHttp( String postURL, String text )
    {
        String returnValue = null;
        StringBuffer sb = new StringBuffer();
        sb.append( "bsh.client=Remote" );
        sb.append( "&bsh.script=" );
        // This requires Java 1.3
        try {
            sb.append( URLEncoder.encode( text, "UTF-8" ) );
        } catch ( UnsupportedEncodingException e ) {
            e.printStackTrace();
        }
        String formData = sb.toString(  );

        try {
          URL url = new URL( postURL );
          HttpURLConnection urlcon =
              (HttpURLConnection) url.openConnection(  );
          urlcon.setRequestMethod("POST");
          urlcon.setRequestProperty("Content-type",
              "application/x-www-form-urlencoded");
          urlcon.setDoOutput(true);
          urlcon.setDoInput(true);
          PrintWriter pout = new PrintWriter( new OutputStreamWriter(
              urlcon.getOutputStream(), "8859_1"), true );
          pout.print( formData );
          pout.flush();

          // read results...
          int rc = urlcon.getResponseCode();
          if ( rc != HttpURLConnection.HTTP_OK )
            System.out.println("Error, HTTP response: "+rc );

          returnValue = urlcon.getHeaderField("Bsh-Return");

          BufferedReader bin = new BufferedReader(
            new InputStreamReader( urlcon.getInputStream() ) );
          String line;
          while ( (line=bin.readLine()) != null )
            System.out.println( line );

          System.out.println( "Return Value: "+returnValue );

        } catch (MalformedURLException e) {
          System.out.println(e);     // bad postURL
        } catch (IOException e2) {
          System.out.println(e2);    // I/O error
        }

        return returnValue;
    }

    /*
        Note: assumes default character encoding
    */
    static String getFile( String name )
        throws FileNotFoundException, IOException
    {
        StringBuffer sb = new StringBuffer();
        BufferedReader bin = new BufferedReader( new FileReader( name ) );
        String line;
        while ( (line=bin.readLine()) != null )
            sb.append( line ).append( "\n" );
        return sb.toString();
    }

}
