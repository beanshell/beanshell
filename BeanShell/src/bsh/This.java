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

import java.io.IOException;

/**
	'This' is the type of bsh scripted objects.
	A 'This' object is a bsh scripted object context.  It holds a namespace 
	reference and implements event listeners and various other interfaces.

	This holds a reference to the declaring interpreter for callbacks from
	outside of bsh.
*/
public class This implements java.io.Serializable, Runnable {
	/**
		This is the interpreter running when the This ref was created.
		It's used as a default interpreter for callback through the This
		where there is no current interpreter instance 
		e.g. interface proxy or event call backs from outside of bsh.
	*/
	public transient Interpreter declaringInterpreter;
	/**
		The namespace
	*/
	public NameSpace namespace;

	/**
		getThis() is a factory for bsh.This type references.  The capabilities
		of ".this" references in bsh are version dependent up until jdk1.3.
		The version dependence was to support different default interface
		implementations.  i.e. different sets of listener interfaces which
		scripted objects were capable of implementing.  In jdk1.3 the 
		reflection proxy mechanism was introduced which allowed us to 
		implement arbitrary interfaces.  This is fantastic.

		A This object is a thin layer over a namespace, comprising a bsh object
		context.  We create it here only if needed for the namespace.

		Note: this method is relatively slow because of the way it dynamically
		factories objects.  So This references are cached in NameSpace.
	*/
    static This getThis( 
		NameSpace namespace, Interpreter declaringInterpreter ) 
	{
		try {
			if ( Capabilities.haveProxyMechanism() )
				return (This)Reflect.constructObject( "bsh.XThis",
					new Object [] { namespace, declaringInterpreter } );
			else if ( Capabilities.haveSwing() )
				return (This)Reflect.constructObject( "bsh.JThis",
					new Object [] { namespace, declaringInterpreter } );
			else
				return new This( namespace, declaringInterpreter );

		} catch ( Exception e ) {
			throw new InterpreterError("internal error 1 in This: "+e);
		} 
    }

	/**
		Get dynamic proxy for interface.
	*/
	public Object getInterface( Class clas ) 
		throws EvalError
	{
		throw new EvalError( "Dynamic proxy mechanism not available");
	}

	/*
		I wish protected access were limited to children and not also 
		package scope... I want this to be a singleton implemented by various
		children.  
	*/
	protected This( NameSpace namespace, Interpreter declaringInterpreter ) { 
		this.namespace = namespace; 
		this.declaringInterpreter = declaringInterpreter;
	}


	public String toString() {
		return "'this' reference to Bsh object: " + namespace.name;
	}

	public void run() {
		try {
			invokeMethod( "run", new Object[0] );
		} catch( EvalError e ) {
			declaringInterpreter.error(
				"Exception in runnable:" + e );
		}
	}

	/**
		Invoke specified method using declaring interpreter.
	*/
	public Object invokeMethod( String name, Object [] args ) 
		throws EvalError
	{
		return invokeMethod( name, args, declaringInterpreter );
	}

	/**
		Invoke specified method with specified interpreter.
		This is simply a convenience method.
	*/
	public Object invokeMethod( 
		String name, Object [] args, Interpreter interpreter  ) 
		throws EvalError
	{
		return namespace.invokeMethod( name, args, interpreter );
	}


	/**
		Bind a This reference to a parent's namespace.
		This is a static utility method because it's used by a bsh command
		bind() and the interpreter doesn't currently allow access to direct 
		methods of This objects (small hack)
	*/
	public static void bind( 
		This ths, NameSpace namespace, Interpreter declaringInterpreter ) 
	{ 
		ths.namespace.setParent( namespace ); 
		ths.declaringInterpreter = declaringInterpreter;
	}


	/*
		For serialization.
	*/
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException {

		// Temporarily prune the namespace.

		NameSpace parent = namespace.getParent();
		// Bind would set the interpreter, but it's possible that the parent
		// is null (it's the root).  So save it...
		Interpreter interpreter = declaringInterpreter;
		namespace.prune();
		s.defaultWriteObject();
		// put it back
		namespace.setParent( parent );
		declaringInterpreter = interpreter;
	}

}

