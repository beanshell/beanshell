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

import java.lang.reflect.Array;
import java.util.Hashtable;
import java.io.*;
import java.lang.reflect.InvocationTargetException;

/**
	What's in a name?  I'll tell you...
	Name() is a somewhat ambiguous thing in the grammar and so is this.
	<p>
	
	This class is a name resolver.  It holds a possibly ambiguous dot 
	separated name and reference to a namespace in which it allegedly lives.  
	It provides methods that attempt to resolve the name to various types of 
	entities: e.g. an Object, a Class, a localy declared bsh method.
	<p>

	*** implementing ***
	Name objects are not to be constructed arbitrarily, but are to be 
	factoried by NameSpace.getNameResolver, which caches them subject to
	a namespace change.  This means that we can cache information about various
	types of resolution here.
*/
/*
	<strong>Implementation notes</strong>
	<pre>

	*** implementing ***
	Name objects are not to be constructed arbitrarily, but are to be 
	factoried by NameSpace.getNameResolver, which caches them subject to
	a namespace change.  This means that we can cache information about various
	types of resolution here.
	Note that we cannot simply cache any result, we must be smart about it.
	For example, the value of a variable may change between calls.  So we could	
	cache the knowledge that it is a variable, but must re-fetch the value
	each time.  We could even do cool optimizations such as knowing about
	'final' variables ;)
	
	Questions: how much will this actually buy us?  The simple cases are not
	expensive here, are they?  How often are long chains of names evaluated?
	*** implementing ***

	Threads:
	Thread safety: all of the work methods in this class must be synchronized
	because they share the internal intermediate evaluation state.

	Random note:
	In some ways Name wants to be a private inner class of NameSpace... 
	However it is used elsewhere as an absraction for objects which haven't
	been pinned down yet.  So it is exposed.

	Note on this.caller resolution:
	Although references like these do work:

		this.caller.caller.caller...   // works

	the equivalent using successive calls:

		// does *not* work
		for( caller=this.caller; caller != null; caller = caller.caller );

	is prohibited by the restriction that you can only call .caller on a 
	literal	this or caller reference.
	The effect is that magic caller reference only works through the current 
	'this' reference.
	The real explanation is that This referernces do not really know anything
	about their depth on the call stack.  It might even be hard to define
	such a thing...

	For those purposes we provide :

		this.callstack

	</pre>
*/
class Name implements java.io.Serializable
{
	// These do not change during evaluation
	public NameSpace namespace;
	String value = null;
	
	// ---------------------------------------------------------
	// The following instance variables mutate during evaluation and should
	// be reset by the reset() method where necessary

	// For evaluation
	private String evalName;		// text left to eval
	private Object evalBaseObject;	// base object for current eval

	private int callstackDepth;		// number of times eval hit 'this.caller'
	/** 
		The last round consume the literal 'this' reference (not super, 
		global, or another This type var).  We use this flag to support magic
		variables that can only be referenced through 'this.xxx', e.g.
		this.interpreter and this.caller;
	*/
	private boolean literalThisReference;
	/** 
		The last round consume the literal 'caller' reference (not super, 
		global, or another This type var).  This is used to limit references
		to .caller to only after a literal 'this' or compound '.caller'.
	*/
	private boolean literalCallerReference;

	//  
	//  End mutable instance variables.
	// ---------------------------------------------------------


	private void reset() {
		evalName = value;
		evalBaseObject = null;
		callstackDepth = 0;
		literalThisReference=false;
		literalCallerReference=false;
	}

	/**
		This constructor should *not* be used in general. 
		Use NameSpace getNameResolver() which supports caching.
		I wish I could make this "friendly" to just that class.
		@see NameSpace getNameResolver().
	*/
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
		"this.interpreter" magic field.
		CallStack reference is necessary to allow resolution of 
		"this.caller" magic field.
		"this.callstack" magic field.
	*/
	public Object toObject( CallStack callstack, Interpreter interpreter ) 
		throws EvalError
	{
		return toObject( callstack, interpreter, false );
	}

	/**
		@see toObject()
		@param forceClass if true then resolution will only produce a class.
		This is necessary to disambiguate in cases where the grammar knows
		that we want a class; where in general the var path may be taken.
	*/
	synchronized public Object toObject( 
		CallStack callstack, Interpreter interpreter, boolean forceClass ) 
		throws EvalError
	{
		reset();

		Object obj = null;
		while( evalName != null )
			obj = consumeNextObjectField( callstack, interpreter, forceClass );

		if ( obj == null )
			throw new InterpreterError("null value in toObject()");

		return obj;
	}

	/**
		Get next prefixed object field component
	*/
	private Object consumeNextObjectField( 	
		CallStack callstack, Interpreter interpreter, boolean forceClass ) 
		throws EvalError
	{
		/*
			Is it a simple variable name?
			Doing this first gives the correct Java precedence for vars 
			vs. imported class names (at least in the simple case - see
			tests/precedence1.bsh).  It should also speed things up a bit.
		*/
		if ( (evalBaseObject == null && !isCompound(evalName) )
			&& !forceClass ) 
		{
			Object obj = resolveThisFieldReference( 
				callstack, namespace, interpreter, evalName, false );

			if ( obj != Primitive.VOID ) {
				evalName = null; // finished
				return evalBaseObject = obj;  // convention
			}
		}

		/*
			Is it a bsh script variable reference?
			If we're just starting the eval of name (no base object)
			or we're evaluating relative to a This reference check.
		*/
		if ( ( evalBaseObject == null || evalBaseObject instanceof This  )
			&& !forceClass ) 
		{
			String varName = prefix(evalName, 1);
			Interpreter.debug("trying to resolve variable: " + varName);
			Object obj;
			if ( evalBaseObject == null ) {
				obj = resolveThisFieldReference( 
					callstack, namespace, interpreter, varName, false );
			} else {
				// null callstack, cannot be caller reference
				obj = resolveThisFieldReference( 
					callstack, ((This)evalBaseObject).namespace, 
					interpreter, varName, true );
			}

			if ( obj != Primitive.VOID ) 
			{
				// Resolved the variable
				Interpreter.debug( "resolved variable: " + varName + 
					" in namespace: "+namespace);
				evalName = suffix(evalName);
				return evalBaseObject = obj;
			}
		}

		/*
			Is it a class name?
			If we're just starting eval of name try to make it, else fail.
		*/
		if ( evalBaseObject == null ) {
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
			Interpreter.debug( "not a class, trying var prefix "+evalName );
		}


		/*
			If we didn't find a class or variable name (or prefix) above
			there are two possibilities:

			- If we are a simple name then we can pass as a void variable 
			reference.
			- If we are compound then we must fail at this point.
		*/
		if ( evalBaseObject == null ) {
			if( !isCompound(evalName) ) {
				evalName = null; // finished
				return evalBaseObject = Primitive.VOID;  // convention
			} else
				throw new EvalError(
					"Class or variable not found:" + evalName);
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
			throw new TargetError( "Null Pointer while evaluating: "
				+value, new NullPointerException() );

		if(evalBaseObject == Primitive.VOID) // previous round produced void
			throw new EvalError(
				"Undefined variable or class name while evaluating: "+value);

		if(evalBaseObject instanceof Primitive)
			throw new EvalError("Can't treat primitive like an object. "+
			"Error while evaluating: "+value);

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
			If we've fallen through here we are no longer resolving to
			a class type.
		*/
		if ( forceClass )
			throw new EvalError( value +" does not resolve to a class name." );

		/* 
			Some kind of field access?
		*/

		String field = prefix(evalName, 1);

		/* length access on array? */
		if(field.equals("length") && evalBaseObject.getClass().isArray())
		{
			Object obj = new Primitive(Array.getLength(evalBaseObject));
			evalName = suffix(evalName);
			return (evalBaseObject = obj);
		}

		/* check for field on object */
		// Note: could eliminate throwing the exception somehow
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

	/**
		Resolve a variable relative to a This reference.

		This is the general variable resolution method, accomodating special
		fields from the This context.  Together the namespace and interpreter
		comprise the This context.  The callstack, if available allows for the
		this.caller construct.  
		Optionally interpret special "magic" field names: e.g. interpreter.

		@param callstack may be null, but this is only legitimate in special
		cases where we are sure resolution will not involve this.caller.

		@param namespace the namespace of the this reference (should be the
		same as the top of the stack?
	*/
	Object resolveThisFieldReference( 
		CallStack callstack, NameSpace thisNamespace, Interpreter interpreter, 
		String varName, boolean specialFieldsVisible ) 
		throws EvalError
	{
		Object obj = null;
		// preserve the state of the last round flags until the end
		boolean 
			wasThis = false,		
			wasCaller = false;

		if ( varName.equals("this") ) {
			// Hack! If the special fields are visible turn of further .this
			// prevent user from skipping to things like super.this.caller
			if ( specialFieldsVisible )
				throw new EvalError("Redundant to call .this on This type");
			obj = thisNamespace.getThis( interpreter );
			wasThis = true;
		} 

		if ( obj == null ) {
			if ( varName.equals("super") )
				obj = thisNamespace.getSuper().getThis( interpreter );
			else if ( varName.equals("global") )
				obj = thisNamespace.getGlobal().getThis( interpreter );
		}

		if ( obj == null && specialFieldsVisible ) {
			if (varName.equals("namespace"))
				obj = thisNamespace;
			else if (varName.equals("variables"))
				obj = thisNamespace.getVariableNames();
			else if (varName.equals("methods"))
				obj = thisNamespace.getMethodNames();
			else if ( varName.equals("interpreter") )
				if ( literalThisReference )
					obj = interpreter;
				else
					throw new EvalError(
						"Can only call .interpreter on literal 'this'");
		}

		if ( obj == null && specialFieldsVisible && varName.equals("caller") )
		{
			if ( literalThisReference || literalCallerReference ) 
			{
				// get the previous context (see notes for this class)
				if ( callstack == null )
					throw new InterpreterError("no callstack");
				obj = callstack.get( ++callstackDepth ).getThis( 
					interpreter ); 
			}
			else
				throw new EvalError(
				"Can only call .caller on literal 'this' or literal '.caller'");

			wasCaller = true;
		}

		if ( obj == null && specialFieldsVisible 
			&& varName.equals("callstack") )
		{
			if ( literalThisReference ) 
			{
				// get the previous context (see notes for this class)
				if ( callstack == null )
					throw new InterpreterError("no callstack");
				obj = callstack;
			}
			else
				throw new EvalError(
				"Can only call .callstack on literal 'this'");
		}


		if ( obj == null )
			obj = thisNamespace.getVariable(varName);

		literalThisReference = wasThis;
		literalCallerReference = wasCaller;
		return obj;
	}

	/**
		Check the cache, else use toObject() to try to resolve to a class
		identifier.  

		Throws EvalError on class not found...
	*/
	synchronized public Class toClass() throws EvalError 
	{
		reset();

		/* Try straightforward class name first */
		Class clas = namespace.getClass(evalName);

		if ( clas == null ) {
			/* 
				Try toObject() which knows how to work through inner classes
				and see what we end up with 
			*/
			Object obj = null;
			try {
				// Null interpreter and callstack references.
				// class only resolution should not require them.
				obj = toObject( null, null, true );  
			} catch ( EvalError  e ) { }; // couldn't resolve it
		
			if ( obj instanceof ClassIdentifier )
				clas = ((ClassIdentifier)obj).getTargetClass();
		}

		if( clas == null )
			throw new EvalError(
				"Class: " + value+ " not found in namespace");

		return clas;
	}

	/*
	*/
	synchronized public LHS toLHS( 
		CallStack callstack, Interpreter interpreter )
		throws EvalError
	{
		reset();

		//Interpreter.debug("Name toLHS: "+evalName+ " isCompound = "
			//+isCompound(evalName));

		// variable
		if(!isCompound(evalName)) {
			//Interpreter.debug("returning simple var LHS...");
			return new LHS(namespace,evalName);
		}

		// field
		Object obj = null;
		try
		{
			while(isCompound(evalName))
				obj = consumeNextObjectField( callstack, interpreter, false );
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
	
	private BshMethod toLocalMethod( Object [] args )
	{
		Class [] sig = Reflect.getTypes( args );
		return namespace.getMethod( value, sig );
	}


    /**
		Invoke the method identified by name.

        Name contains a wholely unqualfied messy name; resolve it to 
		( object | static prefix ) + method name and invoke.

        The interpreter is necessary to support 'this.interpreter' references
		in the called code. (e.g. debug());

        Some cases:

            // dynamic
            local();
            myVariable.foo();
            myVariable.bar.blah.foo();
            // static
            java.lang.Integer.getInteger("foo");

    */
    public Object invokeMethod(
		Interpreter interpreter, Object[] args, CallStack callstack,
		SimpleNode callerInfo
	)
        throws EvalError, ReflectError, InvocationTargetException
    {
        if ( !Name.isCompound(value) )
            return invokeLocalMethod(interpreter, args, callstack, callerInfo);

        // find target object
        Name targetName = namespace.getNameResolver( Name.prefix(value));
        String methodName = Name.suffix(value, 1);

        Object obj = targetName.toObject( callstack, interpreter );

		if ( obj == Primitive.VOID ) 
			throw new EvalError( "Attempt to invoke method: "+methodName
					+"() on undefined variable or class name: "+targetName);

        // if we've got an object, invoke the method
        if ( !(obj instanceof Name.ClassIdentifier) ) {

            if (obj instanceof Primitive) {

                if (obj == Primitive.NULL)
                    throw new TargetError( "Null Pointer in Method Invocation",
					new NullPointerException() );

                // some other primitive
                // should avoid calling methods on primitive, as we do
                // in Name (can't treat primitive like an object message)
                // but the hole is useful right now.
                interpreter.error("Attempt to access method on primitive..." +
                    " allowing bsh.Primitive to peek through for debugging");
            }

            // found an object and it's not an undefined variable
            return Reflect.invokeObjectMethod(
				interpreter, obj, methodName, args, callerInfo);
        }

        // try static method
        Interpreter.debug("invokeMethod: trying static - " + targetName);

        Class clas = ((Name.ClassIdentifier)obj).getTargetClass();
        if (clas != null)
            return Reflect.invokeStaticMethod(clas, methodName, args);

        // return null; ???
		throw new EvalError("unknown target: " + targetName);
    }

	/**
		Invoke a locally declared method or a bsh command.
		If the method is not already declared in the namespace then try
		to load it as a resource from the /bsh/commands path.
	
		Note: instead of invoking the method directly here we should probably
		call invokeObjectMethod passing a This reference.  That would have
		the side effect of allowing a locally defined invoke() method to
		handle undeclared method invocations just like in objects.  Not sure
		if this is desirable...  It seems that if you invoke a method directly
		in scope it should be there.

		Keeping this code separate allows us to differentiate between methods
		invoked directly in scope and those invoked through object references.
	*/
    public Object invokeLocalMethod( 
		Interpreter interpreter, Object[] args, CallStack callstack,
		SimpleNode callerInfo
	)
        throws EvalError, ReflectError, InvocationTargetException
    {
        Interpreter.debug("invoke local method: " + value);

        // Check for locally declared method
        BshMethod meth = toLocalMethod( args );
        if ( meth != null )
            return meth.invokeDeclaredMethod( args, interpreter, callstack, callerInfo );
        else
            Interpreter.debug("no locally declared method: " + value);

        /*
			Look for scripted command as resource
		*/
		// Why not /bsh/commands here?  Why relative to Interpreter?
        String commandName = "commands/" + value + ".bsh";
        InputStream in = Interpreter.class.getResourceAsStream(commandName);
        if (in != null)
        {
            Interpreter.debug("loading resource: " + commandName);

			if ( interpreter == null )
				throw new InterpreterError("2234432 interpreter = null");

            interpreter.eval( 
				new InputStreamReader(in), namespace, commandName);

            // try again
            meth = toLocalMethod( args );
            if(meth != null)
                return meth.invokeDeclaredMethod( 
					args, interpreter, callstack, callerInfo );
            else
                throw new EvalError("Loaded resource: " + commandName +
                    "had an error or did not contain the correct method");
        }

        // check for compiled bsh command class
        commandName = "bsh.commands." + value;
        // create class outside of any namespace
        Class c = BshClassManager.classForName( commandName );
        if(c == null)
            throw new EvalError("Command not found: " + value);

        // add interpereter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = namespace;
        System.arraycopy(args, 0, invokeArgs, 2, args.length);
        try
        {
            return Reflect.invokeStaticMethod(c, "invoke", invokeArgs);
        }
        catch(ReflectError e)
        {
            Interpreter.debug("invoke command args error:" + e);
            // bad args
        }
        // try to print help
        try
        {
            String s = (String)Reflect.invokeStaticMethod(c, "usage", null);
            interpreter.println(s);
            return Primitive.VOID;
        }
        catch(ReflectError e)
        {
            Interpreter.debug("usage threw: " + e);
            throw new EvalError("Wrong number or type of args for command");
        }
    }

	// Static methods that operate on compound ('.' separated) names

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

	// end compound name routines


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

