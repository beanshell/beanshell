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
	EvalError indicates that we cannot continue evaluating the script
	or the script has thrown an exception.

	EvalError may be thrown for a script syntax error, an evaluation 
	error such as referring to an undefined variable, an internal error.
	
	If the script has thrown an exception the exception will be wrapped
	in a TargetError.  
	@see TargetError
*/
public class EvalError extends Exception {
	SimpleNode node;

	public EvalError(String s) {
		super(s);
	}

	public EvalError(String s, SimpleNode node) {
		super(s);
		this.node = node;
	}

	public String getLocation() {
		String loc;
		if ( node == null )
			loc = "<Unknown location>";
		else
			loc = "Line "+node.firstToken.beginLine+" : ";

		return loc;
	}

	/*
		bug fix by: Andreas.Rasmusson@sics.se
	*/
	public String toString() {
		String err = "";
		if ( node != null ) {
			Token t = node.firstToken;
			while ( t!=null ) {
				err += t.image;
				if ( !t.image.equals(".") )
					err += " ";
				if ( t==node.lastToken ||
					t.image.equals("{") || t.image.equals(";") )
					break;
				t=t.next;
			}
		}
			
		return super.toString() + " : " + err;
	}

	/**
		Re-throw the eval error, prefixing msg to the message.
		Unfortunately at the moment java.lang.Exception's message isn't 
		mutable so we just make a new one... could do something about this 
		later.

NOTE: should we add value of toString() here so as to produce a sort of
bsh stack trace? 
	*/
	public void reThrow( String msg ) 
		throws EvalError 
	{
		throw new EvalError( msg +":" + getMessage(), node );
	}

	public void reThrow( SimpleNode node ) 
		throws EvalError 
	{
		throw new EvalError( getMessage(), node );
	}
}

