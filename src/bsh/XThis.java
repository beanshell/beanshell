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

import java.lang.reflect.*;
import java.lang.reflect.InvocationHandler;
import java.io.*;
import java.util.Hashtable;

/**
	XThis is a dynamically loaded extension which extends This.java and adds 
	support for the generalized interface proxy mechanism introduced in 
	JDK1.3.  XThis allows bsh scripted objects to implement arbitrary 
	interfaces (be arbitrary event listener types).

	Note: This module relies on new features of JDK1.3 and will not compile
	with JDK1.2 or lower.  For those environments simply do not compile this
	class.

	Eventually XThis should become simply This, but for backward compatibility
	we will maintain This without requiring support for the proxy mechanism.

	XThis stands for "eXtended This" (I had to call it something).
	
	@see JThis	 See also JThis with explicit JFC support for compatibility.
	@see This	
*/
public class XThis extends This 
	{
	/**
		A cache of proxy interface handlers.
		Currently just one per interface.
	*/
	Hashtable interfaces;

	InvocationHandler invocationHandler = new Handler();

	public XThis( NameSpace namespace, Interpreter declaringInterp ) { 
		super( namespace, declaringInterp ); 
	}

	public String toString() {
		return "'this' reference (XThis) to Bsh object: " + namespace;
	}

	/**
		Get dynamic proxy for interface, caching those it creates.
	*/
	public Object getInterface( Class clas ) 
	{
		return getInterface( new Class[] { clas } );
	}

	/**
		Get dynamic proxy for interface, caching those it creates.
	*/
	public Object getInterface( Class [] ca ) 
	{
		if ( interfaces == null )
			interfaces = new Hashtable();

		// Make a hash of the interface hashcodes in order to cache them
		int hash = 21;
		for(int i=0; i<ca.length; i++)
			hash *= ca[i].hashCode() + 3;
		Object hashKey = new Integer(hash);

		Object interf = interfaces.get( hashKey );

		if ( interf == null ) 
		{
			ClassLoader classLoader = ca[0].getClassLoader(); // ?
			interf = Proxy.newProxyInstance( 
				classLoader, ca, invocationHandler );
			interfaces.put( hashKey, interf );
		}

		return interf;
	}

	/**
		This is the invocation handler for the dynamic proxy.
		<p>

		Notes:
		Inner class for the invocation handler seems to shield this unavailable
		interface from JDK1.2 VM...  
		
		I don't understand this.  JThis works just fine even if those
		classes aren't there (doesn't it?)  This class shouldn't be loaded
		if an XThis isn't instantiated in NameSpace.java, should it?
	*/
	class Handler implements InvocationHandler, java.io.Serializable 
	{
		public Object invoke( Object proxy, Method method, Object[] args ) 
			throws Throwable
		{
			try { 
				return invokeImpl( proxy, method, args );
			} catch ( TargetError te ) {
				// Unwrap target exception.  If the interface declares that 
				// it throws the ex it will be delivered.  If not it will be 
				// wrapped in an UndeclaredThrowable
				throw te.getTarget();
			} catch ( EvalError ee ) {
				// Ease debugging...
				// XThis.this refers to the enclosing class instance
				if ( Interpreter.DEBUG ) 
					Interpreter.debug( "EvalError in scripted interface: "
					+ XThis.this.toString() + ": "+ ee );
				throw ee;
			}
		}

		public Object invokeImpl( Object proxy, Method method, Object[] args ) 
			throws EvalError 
		{
			String methodName = method.getName();
			CallStack callstack = new CallStack( namespace );

			/*
				If equals() is not explicitly defined we must override the 
				default implemented by the This object protocol for scripted
				object.  To support XThis equals() must test for equality with 
				the generated proxy object, not the scripted bsh This object;
				otherwise callers from outside in Java will not see a the 
				proxy object as equal to itself.
			*/
			BshMethod equalsMethod = null;
			try {
				equalsMethod = namespace.getMethod( 
					"equals", new Class [] { Object.class } );
			} catch ( UtilEvalError e ) {/*leave null*/ }
			if ( methodName.equals("equals" ) && equalsMethod == null ) {
				Object obj = args[0];
				return proxy == obj ? Boolean.TRUE : Boolean.FALSE;
			}

			/*
				If toString() is not explicitly defined override the default 
				to show the proxy interfaces.
			*/
			BshMethod toStringMethod = null;
			try {
				toStringMethod = 
					namespace.getMethod( "toString", new Class [] { } );
			} catch ( UtilEvalError e ) {/*leave null*/ }

			if ( methodName.equals("toString" ) && toStringMethod == null)
			{
				Class [] ints = proxy.getClass().getInterfaces();
				// XThis.this refers to the enclosing class instance
				StringBuffer sb = new StringBuffer( 
					XThis.this.toString() + "\nimplements:" );
				for(int i=0; i<ints.length; i++)
					sb.append( " "+ ints[i].getName() 
						+ ((ints.length > 1)?",":"") );
				return sb.toString();
			}

			Class [] paramTypes = method.getParameterTypes();
			return Primitive.unwrap( 
				invokeMethod( methodName, Primitive.wrap(args, paramTypes) ) );
		}
	};
}



