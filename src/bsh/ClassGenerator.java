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

import bsh.Capabilities.Unavailable;
import java.lang.reflect.InvocationTargetException;

public abstract class ClassGenerator
{
	private static ClassGenerator cg;

	public static ClassGenerator getClassGenerator() 
		throws UtilEvalError
	{
		if ( cg == null ) 
		{
			try {
				Class clas = Class.forName( "bsh.ClassGeneratorImpl" );
				cg = (ClassGenerator)clas.newInstance();
			} catch ( Exception e ) {
				throw new Unavailable("ClassGenerator unavailable: "+e);
			}
		}
	
		return cg;
	}

	/**
		Parse the BSHBlock for the class definition and generate the class.
	*/
	public abstract Class generateClass( 
		String name, Modifiers modifiers, 
		Class [] interfaces, Class superClass, BSHBlock block, 
		boolean isInterface, CallStack callstack, Interpreter interpreter 
	)
		throws EvalError;

	/**
		Invoke a super.method() style superclass method on an object instance.
		This is not a normal function of the Java reflection API and is
		provided by generated class accessor methods.
	*/
	public abstract Object invokeSuperclassMethod(
		BshClassManager bcm, Object instance, String methodName, Object [] args
	)
        throws UtilEvalError, ReflectError, InvocationTargetException;

	/**
		Change the parent of the class instance namespace.
		This is currently used for inner class support.
		Note: This method will likely be removed in the future.
	*/
	public abstract void setInstanceNameSpaceParent( 
		Object instance, String className, NameSpace parent );

}
