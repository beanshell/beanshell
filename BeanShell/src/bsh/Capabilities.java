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

/**
	The map of extended features supported by the runtime in which we live.
*/
public class Capabilities {
	static boolean checkedForSwing, haveSwing;
	public static boolean haveSwing() {

		if ( checkedForSwing )
			return haveSwing;

		haveSwing = BshClassManager.classExists( "javax.swing.JButton" );
		checkedForSwing = true;
		return ( haveSwing );
	}

	static boolean checkedForProxyMech, haveProxyMech;
	public static boolean haveProxyMechanism() {
		if ( checkedForProxyMech )
			return haveProxyMech;

		haveProxyMech = BshClassManager.classExists( 
			"java.lang.reflect.Proxy" );
		checkedForProxyMech = true;
		return haveProxyMech;
	}

	/**
		An attempt was made to use an unavailable capability
		This exception is used in core facilities where integration is
		necessarily tight. 
	public static class Unavailable extends Exception {
		public Unavailable(String s ){ super(s); }
	}
	*/
}


