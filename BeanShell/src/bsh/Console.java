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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;
import bsh.util.*;
import bsh.ConsoleInterface;


/**
	Console startup class.
*/
public class Console  {

	public static void main( String args[] ) {

		if ( !BshClassManager.classExists( "bsh.util.Util" ) )
			System.out.println("Can't find the BeanShell utilities...");

		if ( Capabilities.haveSwing() ) {
			bsh.util.Util.startSplashScreen();
			try {
				new Interpreter().eval("desktop()");
			} catch ( EvalError e ) {
				System.err.println("Couldn't start desktop: "+e);
			}
		} else {
			System.err.println(
			"Can't find javax.swing package: starting lame AWT Console...");
			AWTConsole.main( args );
		}
	}

}
