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

import java.lang.reflect.Array;
import java.util.Hashtable;

/**
	What's in a name?  I'll tell you...
	Name() is a highly ambiguous thing in the grammar and so is this.
	
	This class holds a possibly ambiguous dot separated name and a
	namespace in which it lives.  It provides methods that attempt to resolve 
	the name to various types of entities: e.g. an Object, a Class, a
	localy declared bsh method.
*/
class Name implements java.io.Serializable
{
	public NameSpace namespace;
	String value = null;

	// For evaluation
	private String evalName;		// text left to eval
	private Object evalBaseObject;	// base object for current eval

	public Name(NameSpace namespace, String s)
	{
		this.namespace = namespace;
		value = s;
	}

	/**
		Resolve possibly complex name to an object value.

		Throws EvalError on various failures.
		A null object value is indicated by a Primitive.NULL.
		A return type of Primitive.VOID comes from attempting to access
		an undefined variable.

		Some cases:
			myVariable
			myVariable.foo
			myVariable.foo.bar
			java.awt.GridBagConstraints.BOTH
			my.package.stuff.MyClass.someField.someField...

		Interpreter reference is necessary to allow resolution of 
		".interpreter" magic field.
	*/
	synchronized public Object toObject( Interpreter interpreter ) 
		throws EvalError
	{
		evalName = value;
		evalBaseObject = null;

		Object obj = null;
		while( evalName != null )
			obj = consumeNextObjectField( interpreter );

		if ( obj == null )
			throw new InterpreterError("null value in toObject()");

		return obj;
	}

	/*  
		Get next prefixed object field component
	*/
	private Object consumeNextObjectField( Interpreter interpreter ) 
		throws EvalError
	{
		/*
			Is it a class name?
			If we're just starting eval of name (and it wasn't a variable
			reference per above) try to make it, else fail.
			Class names are attempted before long variable chains because
			we don't have any way to "back out" if we start down a chain
			and we're wrong.  In general this seems ok.
		*/
		if( evalBaseObject == null ) {
			Interpreter.debug( "trying class: " + evalName);
			
			/*
				Keep adding parts until we have a class 
			*/
			Class clas = null;
			int i = 1;
			for(; i <= countParts(evalName); i++)
				if ( (clas = namespace.getClass(prefix(evalName, i))) != null )
					break;
		
			if( clas != null )  {
				evalName = suffix(evalName, countParts(evalName) - i);
				return ( evalBaseObject = new ClassIdentifier(clas) );
			}
			// not a class (or variable per above)
			//throw new EvalError("Class or variable not found:" + evalName);
			Interpreter.debug( "not a class, trying var prefix "+evalName );
		}

		/*
			Is it a bsh script variable reference?
			If we're just starting the eval of name (no base object)
			or we're evaluating relative to a This reference check.
		*/
		if ( evalBaseObject == null || evalBaseObject instanceof This ) {

			String varName = prefix(evalName, 1);
			Interpreter.debug("trying to resolve variable: " + varName);
			Object obj;
			if ( evalBaseObject == null )
				obj = resolveThisFieldReference( 
					namespace, interpreter, varName, false );
			else
				obj = resolveThisFieldReference( 
					((This)evalBaseObject).namespace, 
					interpreter, varName, true );

			if ( obj == Primitive.VOID ) {

				if( suffix(evalName) == null ) {
					evalName = null; // finished
					return evalBaseObject = obj;  // convention
				} else
					throw new EvalError(
						"Class or variable not found:" + evalName);
			} else {
				// Resolved the variable
				Interpreter.debug( "resolved variable: " + varName + 
					" in namespace: "+namespace);
				evalName = suffix(evalName);
				return evalBaseObject = obj;
			}
		}


		/*
			--------------------------------------------------------
			After this point we're definitely evaluating relative to
			a base object.
			--------------------------------------------------------
		*/

		/*
			Do some basic validity checks.
		*/

		if(evalBaseObject == Primitive.NULL) // previous round produced null
			throw new EvalError("Null pointer error...");

		if(evalBaseObject == Primitive.VOID) // previous round produced void
			throw new EvalError("Void pointer error...");

		if(evalBaseObject instanceof Primitive)
			throw new EvalError("Can't treat primitive like an object.");

		/* 
			Resolve relative to a class type
			static field, inner class, ?
		*/
		if ( evalBaseObject instanceof ClassIdentifier ) {
			Class clas = ((ClassIdentifier)evalBaseObject).getTargetClass();
			String field = prefix(evalName, 1);

			Object obj = null;
			// static field?
			try {
				obj = Reflect.getStaticField(clas, field);
			} catch(ReflectError e) { }

			// inner class?
			if ( obj == null ) {
				String iclass = clas.getName()+"$"+field;
				Class c = namespace.getClass( iclass );
				if ( c != null )
					obj = new ClassIdentifier(c);
			}

			if ( obj == null )
				throw new EvalError(
					"No static field or inner class: " + field + " of " + clas);

			evalName = suffix(evalName);
			return (evalBaseObject = obj);
		}

		/* 
			Some kind of field access?
			or inner class?
		*/

		String field = prefix(evalName, 1);

		/* length access on array? */
		if(field.equals("length") && evalBaseObject.getClass().isArray())
		{
			Object obj = new Primitive(Array.getLength(evalBaseObject));
			evalName = suffix(evalName);
			return (evalBaseObject = obj);
		}

		/* check for field */
		try
		{
			Object obj = Reflect.getObjectField(evalBaseObject, field);
			evalName = suffix(evalName);
			return (evalBaseObject = obj);
		}
		catch(ReflectError e) { /* not a field */ }
	
		// if we get here we have failed
		throw new EvalError(
			"Cannot access field: " + field + ", on object: " + evalBaseObject);
	}

	/*
		Resolve a variable relative to a This reference.
		Optionally interpret special "magic" field names: e.g. interpreter.
	*/
	Object resolveThisFieldReference( 
		NameSpace thisNamespace, Interpreter interpreter, 
		String varName, boolean specialFieldsVisible ) 
	{
		Object obj = null;

		if ( varName.equals("this") )
			obj = thisNamespace.getThis( interpreter );
		else if ( varName.equals("super") )
			obj = thisNamespace.getSuper().getThis( interpreter );
		else if ( varName.equals("global") )
			obj = thisNamespace.getGlobal().getThis( interpreter );
		else
			if ( specialFieldsVisible ) 
				if (varName.equals("namespace"))
					obj = thisNamespace;
				else if (varName.equals("interpreter"))
					obj = interpreter;
				else if (varName.equals("variables"))
					obj = thisNamespace.getVariableNames();
				else if (varName.equals("methods"))
					obj = thisNamespace.getMethodNames();

		if ( obj == null )
			obj = thisNamespace.getVariable(varName);

		return obj;
	}

	synchronized public Class toClass() throws EvalError 
	{
		evalName = value;
		evalBaseObject = null;

		/* Try straightforward class name first */
		Class clas = namespace.getClass(evalName);

		if ( clas == null ) {
			/* 
				Try toObject() which knows how to work through inner classes
				and see what we end up with 
			*/
			Object obj = null;
			try {
				obj = toObject( null ); // null we don't care about interp ref
			} catch ( EvalError  e ) { }; // couldn't resolve it
		
			if ( obj instanceof ClassIdentifier )
				clas = ((ClassIdentifier)obj).getTargetClass();
			else
				if ( obj != null )
					if ( obj == Primitive.VOID )
						throw new EvalError( "\""+value+"\"" +
							" does not resolve to a "+ 
							"class name.  It is undefined." );
					else
						throw new EvalError( "\""+value+"\"" +
							" does not resolve to a "+ 
							"class name.  It resolves to an object of type: "+ 
							obj.getClass().getName() );
		}

		if( clas == null )
			throw new EvalError(
				"Class: " + value+ " not found in namespace");

		return clas;
	}

	/*
	*/
	synchronized public LHS toLHS( Interpreter interpreter )
	{
		evalName = value;
		evalBaseObject = null;

		// variable
		if(!isCompound(evalName))
			return new LHS(namespace,evalName);

		// field
		Object obj = null;
		try
		{
			while(isCompound(evalName))
				obj = consumeNextObjectField( interpreter );
		}
		catch(EvalError e)
		{
			Interpreter.debug("LHS evaluation: " + e);
			return null;
		}

		if(obj == null)
			throw new InterpreterError("internal error 2893749283");

		if(obj instanceof This)
		{
			Interpreter.debug("found This reference evaluating LHS");
			return new LHS(((This)obj).namespace, evalName);
		}

		if(evalName != null)
		{
			try
			{
				return Reflect.getLHSObjectField(obj, evalName);
			}
			catch(ReflectError e)
			{
				Interpreter.debug("reflect error:" + e);
				return null;
			}
		}

		// We bit off our field in the very first bite
		// have to back off and make a class out of the prefix
		Interpreter.debug("very first field was it...");

		Class clas = namespace.getClass(prefix(value));
		if(clas == null)
			throw new InterpreterError("internal error 238974983");

		String field = suffix(value, 1);

		try
		{
			return Reflect.getLHSStaticField(clas, field);
		}
		catch(ReflectError e)
		{
			Interpreter.debug("reflect error:" + e);
			return null;
		}
	}
	
	public BshMethod toLocalMethod()
	{
		return namespace.getMethod(value);
	}

	static boolean isCompound(String value)
	{
		return countParts(value) > 1;
	}

	static int countParts(String value)
	{
		if(value == null)
			return 0;

		int count = 0;
		int index = -1;
		while((index = value.indexOf('.', index + 1)) != -1)
			count++;
		return count + 1;
	}

	static String prefix(String value)
	{
		if(!isCompound(value))
			return null;

		return prefix(value, countParts(value) - 1);
	}

	static String prefix(String value, int parts)
	{
		if(parts < 1)
			return null;

		int count = 0;
		int index = -1;

		while(((index = value.indexOf('.', index + 1)) != -1) && (++count < parts))
		{ ; }

		return (index == -1) ? value : value.substring(0, index);
	}

	static String suffix(String name)
	{
		if(!isCompound(name))
			return null;

		return suffix(name, countParts(name) - 1);
	}

	public static String suffix(String value, int parts)
	{
		if(parts < 1)
			return null;

		int count = 0;
		int index = value.length() + 1;

		while(((index = value.lastIndexOf('.', index - 1)) != -1) && (++count < parts))
		{ ; }

		return (index == -1) ? value : value.substring(index + 1);
	}

	public String toString() { return value; }

	static class ClassIdentifier {
		Class clas;

		public ClassIdentifier( Class clas ) {
			this.clas = clas;
		}

		public Class getTargetClass() {
			return clas;
		}

		public String toString() {
			return "Class Identifier: "+clas.getName();
		}
	}
}

