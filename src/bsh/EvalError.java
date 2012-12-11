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

/**
	EvalError indicates that we cannot continue evaluating the script
	or the script has thrown an exception.

	EvalError may be thrown for a script syntax error, an evaluation 
	error such as referring to an undefined variable, an internal error.
	<p>
	
	@see TargetError
*/
public class EvalError extends Exception 
{
	SimpleNode node;

	// Note: no way to mutate the Throwable message, must maintain our own
	String message;

	CallStack callstack;

	public EvalError( String s, SimpleNode node, CallStack callstack ) {
		setMessage(s);
		this.node = node;
		// freeze the callstack for the stack trace.
		if ( callstack != null )
			this.callstack = callstack.copy();
	}

	/**
		Print the error with line number and stack trace.
	*/
	public String toString() 
	{
		String trace;
		if ( node != null )
			trace = " : at Line: "+ node.getLineNumber() 
				+ " : in file: "+ node.getSourceFile()
				+ " : "+node.getText();
		else
			// Users should not normally see this.
			trace = ": <at unknown location>";

		if ( callstack != null )
			trace = trace +"\n" + getScriptStackTrace();

		return getMessage() + trace;
	}

	/**
		Re-throw the error, prepending the specified message.
	*/
	public void reThrow( String msg ) 
		throws EvalError 
	{
		prependMessage( msg );
		throw this;
	}

	/**
		The error has trace info associated with it. 
		i.e. It has an AST node that can print its location and source text.
	*/
	SimpleNode getNode() {
		return node;
	}

	void setNode( SimpleNode node ) {
		this.node = node;
	}

	public String getErrorText() { 
		if ( node != null )
			return node.getText() ;
		else
			return "<unknown error>";
	}

	public int getErrorLineNumber() { 
		if ( node != null )
			return node.getLineNumber() ;
		else
			return -1;
	}

	public String getErrorSourceFile() {
		if ( node != null )
			return node.getSourceFile() ;
		else
			return "<unknown file>";
	}

	public String getScriptStackTrace() 
	{
		if ( callstack == null )
			return "<Unknown>";

		String trace = "";
		CallStack stack = callstack.copy();
		while ( stack.depth() > 0 ) 
		{
			NameSpace ns = stack.pop();
			SimpleNode node = ns.getNode();
			if ( ns.isMethod )
			{
				trace = trace + "\nCalled from method: " + ns.getName();
				if ( node != null )
					trace += " : at Line: "+ node.getLineNumber() 
						+ " : in file: "+ node.getSourceFile()
						+ " : "+node.getText();
			}
		}

		return trace;
	}

	/**
		@see #toString() for a full display of the information
	*/
	public String getMessage() { return message; }

	public void setMessage( String s ) { message = s; }

	/**
		Prepend the message if it is non-null.
	*/
	protected void prependMessage( String s ) 
	{ 
		if ( s == null )
			return;

		if ( message == null )
			message = s;
		else
			message = s + " : "+ message;
	}

}

