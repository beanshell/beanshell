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
import java.awt.Color;

/**
	The capabilities of a GUI console for BeanShell.
	Stream I/O and optimized print for output.
*/
public interface ConsoleInterface {
	public Reader getIn();
	public PrintStream getOut();
	public PrintStream getErr();
	public void println( String s );
	public void print( String s );
	public void print( String s, Color color );
	public void error( String s );
}
