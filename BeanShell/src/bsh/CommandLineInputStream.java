/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

import java.io.*;

/**
	This is a quick hack to turn empty lines entered on the command line
	into ';\n' empty lines for the interpreter.  It's just more pleasant to
	be able to hit return on an empty line and see the prompt reappear.
		
	This is *not* used when text is sourced from a file non-interactively.
*/
class CommandLineInputStream extends FilterInputStream {
    public CommandLineInputStream(InputStream in) {
		super(in);
    }

	static final int 
		normal = 0,
		lastCharNL = 1,
		sentSemi = 2;

	int state = lastCharNL;

    public int read() throws IOException {
		int b;

		if ( state == sentSemi ) {
			state = lastCharNL;
			return '\n';
		}

		// skip CR
        while ( (b = in.read()) == '\r' );

		if ( b == '\n' )
			if ( state == lastCharNL ) {
				b = ';';
				state = sentSemi;
			} else
				state = lastCharNL;
		else
			state = normal;

		return b;
    }

	// degenerate implementation
    public int read(byte buff[], int off, int len) throws IOException {
		int b = read();
		if ( b == -1 )
			return 0;
		else {
			buff[off]=(byte)b;
			return 1;
		}
    }

	// Test it
	public static void main( String [] args ) throws Exception {
		InputStream in = new CommandLineInputStream( System.in );
		while ( true )
			System.out.println( in.read() );
		
	}
}

