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

import java.io.IOException;

/**
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
			if ( NameSpace.haveProxyMechanism() )
				return (This)Reflect.constructObject( "bsh.XThis",
					new Object [] { namespace, declaringInterpreter } );
			else if ( NameSpace.haveSwing() )
				return (This)Reflect.constructObject( "bsh.JThis",
					new Object [] { namespace, declaringInterpreter } );
			else
				return new This( namespace, declaringInterpreter );

		} catch ( Exception e ) {
			throw new InterpreterError("internal error 1 in This: "+e);
		} 
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

