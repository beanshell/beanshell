import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import bsh.*;

/**
	This file is part of BeanShell - www.beanshell.org

	A simple test harness for bsh in a servlet.
	Evaluate the text supplied int the parameter "text", showing the 
	return value and captured output.
	
	@author Pat Niemeyer
*/
public class TestBshServlet extends HttpServlet 
{
    public void doGet(
		HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
	{
		/*
			We have included a getVersion() command to detect the version
			of bsh.  If bsh is packaged in the WAR file it could access it
			directly as a bsh command.  But if bsh is in the app server's
			classpath it won't see it here, so we will source it directly.

			This command works around the lack of a coherent version number
			in the early versions.
		*/
		String getVersionCommand = SimpleTemplate.getStringFromStream (
			getClass().getResource(
				"/bsh/commands/getVersion.bsh").openStream() );
		
		// Create a PrintStream to capture output
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream pout = new PrintStream( baos );

		// Create an interpreter instance with a null inputstream,
		// the capture out/err stream, non-interactive 
		Interpreter bsh = new Interpreter( null, pout, pout, false );

		String text = request.getParameter("text");
		
		// Eval the text, gathering the return value or any error.
		String version = "<unknown>";
		Object result = null;
		String error = null;
		// Temporarily swap System.out to capture output there as well.
		PrintStream sout = System.out;
		System.setOut( pout );
		try { 
			// Get the bsh version
			bsh.eval( getVersionCommand );	// source the command
			version = (String)bsh.eval( "getVersion()" );  // execute it

			// Eval the user text
			result = bsh.eval(text);
		} catch ( EvalError e ) {
			error = e.toString();
		} finally {
			System.setOut( sout );
		}

		// Format the output using a simple templating utility
		SimpleTemplate st = new SimpleTemplate( 
			getServletContext().getResource("/page.template") );
		st.replace( "version", version);
		st.replace( "text", text );
		st.replace( "return", ((error==null)?String.valueOf(result):error) );
		pout.flush();
		st.replace( "output", baos.toString() );

		// Write the page
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
		st.write(out);
    }

    public void doPost(
		HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
	{	
		doGet( request, response );
	}

}

