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

/**
	The script cannot be evaluated because of an error.
	e.g. a syntax error, an evaluation error such as referring to an undefined
	variable, an internal error.
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
	*/
	public void reThrow( String msg ) 
		throws EvalError 
	{
		throw new EvalError( msg +":" + getMessage(), node );
	}
}

