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

