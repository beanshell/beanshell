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

import java.io.File;

/**
	A File that supports localized paths using the bsh current working 
	directory (bsh.cwd)

	Also adds basename/dirname functionality.

	Note: This kind of crosses the line between the core interpreter and
	the "util" package.  It's here so that we're consistent in the source()
	feature of the interpreter.
*/
class BshFile extends java.io.File {
	String dirName = "", baseName;

    BshFile( String fileName ) { 
		super( fileName );

		// init dirName, baseName
		int i = fileName.lastIndexOf( File.separator );
		if ( i != -1 ) {
			dirName = fileName.substring(0, i);
			baseName = fileName.substring(i+1);
		} else
			baseName = fileName;
	}

	public String dirName() { return dirName; }
	public String baseName() { return baseName; }

	public String toString() {
		return super.toString() +
			", dirName = "+dirName+", baseName = "+baseName ;
	}

}

