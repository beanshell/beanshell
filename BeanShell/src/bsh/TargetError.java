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

import java.lang.reflect.InvocationTargetException;


/**
	TargetError is an EvalError that wraps an exception thrown by the script	
	(or by code called from the script).  TargetErrors indicate 'expected'
	or 'catchable' exceptions in the script, whereas a general EvalError 
	just indicates that the script cannot be evaluated for some reason.

	Note: this is used in the implementation of try/catch.
	The only types of exceptions which can be caught by bsh scripts are
	EvalError and its subclasses.  Normal invocation target exceptions are
	first wrapped in TargetError before being thrown up.
	We should do the same with arbitrary exceptions that we generate 
	(e.g. ClassCastException).

	Note: an important location to look at is BSHMethodInvocation.  There
	we catch eval errors and rethrow them to compound the location information
*/
public class TargetError extends EvalError 
{
	Throwable target;

	public TargetError(String msg, Throwable t, SimpleNode node )
	{
		super(msg, node);
		target = t;
	}

	public TargetError(Throwable t, SimpleNode node )
	{
		this("TargetError", t, node);
	}

	public Throwable getTarget()
	{
		// check for easy mistake
		if(target instanceof InvocationTargetException)
			return((InvocationTargetException)target).getTargetException();
		else
			return target;
	}

	public String toString() {

		return super.toString() 
			+ "\nTarget exception: " + 
			printTargetError( target );
	}

    public void printStackTrace() { 
		super.printStackTrace();
		System.out.println("--- Target Stack Trace ---");
		target.printStackTrace();
	}

	/**
		Re-throw the target error, prefixing msg to the message.
		Unfortunately at the moment java.lang.Exception's message isn't 
		mutable so we just make a new one... could do something about this 
		later.
	*/
	public void reThrow( String msg ) 
		throws TargetError 
	{
		throw new TargetError( msg+":"+getMessage(),  target, node );
	}

	public String printTargetError( Throwable t ) {
		String s = target.toString();

		if ( Capabilities.haveProxyMechanism() )
			s += "\n" + xPrintTargetError( t );

		return s;
	}

	/**
		This indirection is used to print InvocationTargetExceptions when
		the proxy mechanism is available.

		We are sheilded from compile problems by using a bsh script.
		This is acceptable here because we're not in a critical path.
	*/
	public String xPrintTargetError( Throwable t ) {
		String getTarget =
			"import java.lang.reflect.UndeclaredThrowableException;"+
			"if ( target instanceof UndeclaredThrowableException )"+
			"	return target.getUndeclaredThrowable().toString();" +
			"else return \"\"";

		Interpreter i = new Interpreter();
		try {
			i.set("target", t);
			return (String)i.eval( getTarget );
		} catch ( EvalError e ) {
			throw new InterpreterError("xprintarget: "+e.toString() );
		}
	}


}

