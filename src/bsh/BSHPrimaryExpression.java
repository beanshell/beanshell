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

class BSHPrimaryExpression extends SimpleNode
{
	BSHPrimaryExpression(int id) { super(id); }

	/**
		Evaluate to a value object.
	*/
	public Object eval( CallStack callstack, Interpreter interpreter)  
		throws EvalError
	{
		return eval( false, callstack, interpreter );
	}

	/**
		Evaluate to a value object.
	*/
	public LHS toLHS( CallStack callstack, Interpreter interpreter)  
		throws EvalError
	{
		Object obj = eval( true, callstack, interpreter );

		if ( ! (obj instanceof LHS) )
			throw new EvalError("Can't assign to:", this, callstack );
		else
			return (LHS)obj;
	}

	/*
		Our children are a prefix expression and any number of suffixes.
		<p>

		We don't eval() any nodes until the suffixes have had an
		opportunity to work through them.  This lets the suffixes decide
		how to interpret an ambiguous name (e.g. for the .class operation).
	*/
	private Object eval( boolean toLHS, 
		CallStack callstack, Interpreter interpreter)  
		throws EvalError
	{
		Object obj = jjtGetChild(0);
		int numChildren = jjtGetNumChildren(); 

		for(int i=1; i<numChildren; i++)
			obj = ((BSHPrimarySuffix)jjtGetChild(i)).doSuffix(
				obj, toLHS, callstack, interpreter);

		/*
			If the result is a Node eval() it to an object or LHS
			(as determined by toLHS)
		*/
		if ( obj instanceof SimpleNode )
			if ( obj instanceof BSHAmbiguousName )
				if ( toLHS )
					obj = ((BSHAmbiguousName)obj).toLHS(
						callstack, interpreter);
				else
					obj = ((BSHAmbiguousName)obj).toObject(
						callstack, interpreter);
			else 
				// Some arbitrary kind of node
				if ( toLHS )
					// is this right?
					throw new EvalError("Can't assign to prefix.", 
						this, callstack );
				else
					obj = ((SimpleNode)obj).eval(callstack, interpreter);	

		// return LHS or value object as determined by toLHS
		if ( obj instanceof LHS )
			if ( toLHS )
				return obj;
			else
				try {
					return ((LHS)obj).getValue();
				} catch ( UtilEvalError e ) {
					throw e.toEvalError( this, callstack );
				}
		else
			return obj;
	}
}

