/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

import java.io.*;
import java.net.*;

/**
	Remoe executor class.
*/
public class Remote {

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
		int ret = eval( url, text, null );
		System.exit( ret );
	}

	/**
		Evaluate text in the interpreter at url, capturing output into
		output and returning a possible integer return value.
	*/
	public static int eval( String url, String text, StringBuffer output )
		throws IOException
	{
		String returnValue = null;
		if ( url.startsWith( "http:" ) ) {
			returnValue = doHttp( url, text );
		} else if ( url.startsWith( "bsh:" ) ) {
			//doBsh( url, args );
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

	//static void doBsh( String url, String text ) { }

	static String doHttp( String postURL, String text ) 
	{
		String returnValue = null;
		StringBuffer sb = new StringBuffer();
		sb.append( "bsh.client=Remote" );
		sb.append( "&bsh.script=" );
		sb.append( URLEncoder.encode( text ) );
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

		  returnValue = urlcon.getHeaderField("bsh_return");

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
