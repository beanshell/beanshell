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
public class TargetError extends EvalError implements TargetErrorPrinter
{
	Throwable target;
	TargetErrorPrinter targetPrinter;

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
			getTargetErrorPrinter().printTargetError( target );
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

	/**
		This factory allows us to print InvocationTargetExceptions when
		the proxy mechanism is available.
	*/
	public TargetErrorPrinter getTargetErrorPrinter() {
		if ( targetPrinter == null )
			try {
				if ( Capabilities.haveProxyMechanism() )
					targetPrinter = 
						(TargetErrorPrinter)Reflect.constructObject( 
						"bsh.XTargetErrorPrinter", new Object [] { } );
				else
					targetPrinter = this;

			} catch ( Exception e ) {
				throw new InterpreterError("internal error 1 in TargetError: "+e);
			} 

		return targetPrinter;
	}

	public String printTargetError( Throwable t ) {
		return target.toString();
	}

}

