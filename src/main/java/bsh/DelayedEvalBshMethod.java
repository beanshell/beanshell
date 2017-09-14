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

public class DelayedEvalBshMethod extends BshMethod
{
	String returnTypeDescriptor;
	BSHReturnType returnTypeNode;
	String [] paramTypeDescriptors;
	BSHFormalParameters paramTypesNode;

	// used for the delayed evaluation...
	transient CallStack callstack;
	transient Interpreter interpreter;

	/**
		This constructor is used in class generation.  It supplies String type
		descriptors for return and parameter class types and allows delay of
		the evaluation of those types until they are requested.  It does this
		by holding BSHType nodes, as well as an evaluation callstack, and
		interpreter which are called when the class types are requested.
	*/
	/*
		Note: technically I think we could get by passing in only the
		current namespace or perhaps BshClassManager here instead of
		CallStack and Interpreter.  However let's just play it safe in case
		of future changes - anywhere you eval a node you need these.
	*/
	DelayedEvalBshMethod(
		String name,
		String returnTypeDescriptor, BSHReturnType returnTypeNode,
		String [] paramNames,
		String [] paramTypeDescriptors, BSHFormalParameters paramTypesNode,
		BSHBlock methodBody,
		NameSpace declaringNameSpace, Modifiers modifiers,
		CallStack callstack, Interpreter interpreter
	) {
		super( name, null/*returnType*/, paramNames, null/*paramTypes*/,
			methodBody, declaringNameSpace, modifiers );

		this.returnTypeDescriptor = returnTypeDescriptor;
		this.returnTypeNode = returnTypeNode;
		this.paramTypeDescriptors = paramTypeDescriptors;
		this.paramTypesNode = paramTypesNode;
		this.callstack = callstack;
		this.interpreter = interpreter;
	}

	public String getReturnTypeDescriptor() { return returnTypeDescriptor; }

	public Class getReturnType()
	{
		if ( returnTypeNode == null )
			return null;

		// BSHType will cache the type for us
		try {
			return returnTypeNode.evalReturnType( callstack, interpreter );
		} catch ( EvalError e ) {
			throw new InterpreterError("can't eval return type: "+e);
		}
	}

	public String [] getParamTypeDescriptors() { return paramTypeDescriptors; }

	public Class [] getParameterTypes()
	{
		// BSHFormalParameters will cache the type for us
		try {
			return (Class [])paramTypesNode.eval( callstack, interpreter );
		} catch ( EvalError e ) {
			throw new InterpreterError("can't eval param types: "+e);
		}
	}
}
