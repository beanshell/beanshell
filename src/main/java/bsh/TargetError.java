/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

import java.lang.reflect.InvocationTargetException;

import java.io.PrintStream;

/**
	TargetError is an EvalError that wraps an exception thrown by the script	
	(or by code called from the script).  TargetErrors indicate exceptions 
	which can be caught within the script itself, whereas a general EvalError 
	indicates that the script cannot be evaluated further for some reason.
	
	If the exception is caught within the script it is automatically unwrapped,
	so the code looks like normal Java code.  If the TargetError is thrown
	from the eval() or interpreter.eval() method it may be caught and unwrapped
	to determine what exception was thrown.
*/
public class TargetError extends EvalError
{
	boolean inNativeCode;

	public TargetError(
		String msg, Throwable t, SimpleNode node, CallStack callstack, 
		boolean inNativeCode )
	{
		super( msg, node, callstack );
	    initCause(t);
		this.inNativeCode = inNativeCode;
	}

	public TargetError( Throwable t, SimpleNode node, CallStack callstack )
	{
		this("TargetError", t, node, callstack, false);
	}

	public Throwable getTarget()
	{
		// check for easy mistake
        final Throwable target = getCause();
		if(target instanceof InvocationTargetException)
			return((InvocationTargetException)target).getTargetException();
		else
			return target;
	}

	public String toString() 
	{
		return super.toString() 
			+ "\nTarget exception: " + 
			printTargetError( getCause() );
	}

    public void printStackTrace() { 
		printStackTrace( false, System.err );
	}

    public void printStackTrace( PrintStream out ) { 
		printStackTrace( false, out );
	}

    public void printStackTrace( boolean debug, PrintStream out ) {
		if ( debug ) {
			super.printStackTrace( out );
			out.println("--- Target Stack Trace ---");
		}
		getCause().printStackTrace(out);
	}

	/**
		Generate a printable string showing the wrapped target exception.
		If the proxy mechanism is available, allow the extended print to
		check for UndeclaredThrowableException and print that embedded error.
	*/
	public String printTargetError( Throwable t ) 
	{
		String s = getCause().toString();

		if ( Capabilities.canGenerateInterfaces() )
			s += "\n" + xPrintTargetError( t );

		return s;
	}

	/**
		Extended form of print target error.
		This indirection is used to print UndeclaredThrowableExceptions 
		which are possible when the proxy mechanism is available.

		We are shielded from compile problems by using a bsh script.
		This is acceptable here because we're not in a critical path...
		Otherwise we'd need yet another dynamically loaded module just for this.
	*/
	public String xPrintTargetError( Throwable t ) 
	{
		String getTarget =
			"import java.lang.reflect.UndeclaredThrowableException;"+
			"String result=\"\";"+
			"while ( target instanceof UndeclaredThrowableException ) {"+
			"	target=target.getUndeclaredThrowable(); " +
			"	result+=\"Nested: \"+target.toString();" +
			"}"+
			"return result;";
		Interpreter i = new Interpreter();
		try {
			i.set("target", t);
			return (String)i.eval( getTarget );
		} catch ( EvalError e ) {
			throw new InterpreterError("xprintarget: "+e.toString() );
		}
	}

	/**
		Return true if the TargetError was generated from native code.
		e.g. if the script called into a compiled java class which threw
		the excpetion.  We distinguish so that we can print the stack trace
		for the native code case... the stack trace would not be useful if
		the exception was generated by the script.  e.g. if the script
		explicitly threw an exception... (the stack trace would simply point
		to the bsh internals which generated the exception).
	*/
	public boolean inNativeCode() { 
		return inNativeCode; 
	}
}

