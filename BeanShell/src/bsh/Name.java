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
//import bsh.Reflect.MethodInvoker;
//import bsh.Reflect.JavaMethod;

/**
	What's in a name?  I'll tell you...
	Name() is a somewhat ambiguous thing in the grammar and so is this.
	<p>
	
	This class is a name resolver.  It holds a possibly ambiguous dot 
	separated name and reference to a namespace in which it allegedly lives.  
	It provides methods that attempt to resolve the name to various types of 
	entities: e.g. an Object, a Class, a localy declared bsh method.
	<p>

	Name objects are not to be factoried by NameSpace.getNameResolver, 
	which caches them subject to a class namespace change.  This means that 
	we can cache information about various types of resolution here.
	Currently very little if any information is cached.  However with a future
	"optimize" setting that defeats certain dynamic behavior we might be able
	to cache quite a bit.
*/
/*
	<strong>Implementation notes</strong>
	<pre>
	Thread safety: all of the work methods in this class must be synchronized
	because they share the internal intermediate evaluation state.

	Note about invokeMethod():  We could simply use resolveMethod and return
	the MethodInvoker (BshMethod or JavaMethod) however there is no easy way
	for the AST (BSHMehodInvocation) to use this as it doesn't have type
	information about the target to resolve overloaded methods.
	(In Java, overloaded methods are resolved at complile time... here they
	are, of necessity, dynamic).  So it would have to do what we do here
	and cache by signature.  We now do that for the client in Reflect.java.

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

	// Begin Cached result structures

	Object asObject;
	LHS asLHS;

	// Note: it's ok to cache class resolution here because when the class
	// space changes the namespace will discard cached names.
	Class asClass;

	// End Cached result structures

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
		@see NameSpace getNameResolver().
	*/
	// I wish I could make this "friendly" to only NameSpace
	Name( NameSpace namespace, String s )
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
		throws UtilEvalError
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
		throws UtilEvalError
	{
		//if ( asObject != null )
			//return asObject;

		reset();

		Object obj = null;
		while( evalName != null )
			obj = consumeNextObjectField( callstack, interpreter, forceClass );

		if ( obj == null )
			throw new InterpreterError("null value in toObject()");

		asObject = obj;
		return asObject;
	}

	/**
		Get next prefixed object field component
	*/
	private Object consumeNextObjectField( 	
		CallStack callstack, Interpreter interpreter, boolean forceClass ) 
		throws UtilEvalError
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
			if ( Interpreter.DEBUG ) 
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
				if ( Interpreter.DEBUG ) 
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
			if ( Interpreter.DEBUG ) 
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
			if ( Interpreter.DEBUG ) 
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
				throw new UtilEvalError(
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

		if ( evalBaseObject == Primitive.NULL) // previous round produced null
			throw new UtilTargetError( new NullPointerException( 
				"Null Pointer while evaluating: " +value ) );

		if ( evalBaseObject == Primitive.VOID) // previous round produced void
			throw new UtilEvalError(
				"Undefined variable or class name while evaluating: "+value);

		if ( evalBaseObject instanceof Primitive)
			throw new UtilEvalError("Can't treat primitive like an object. "+
			"Error while evaluating: "+value);

		/* 
			Resolve relative to a class type
			static field, inner class, ?
		*/
		if ( evalBaseObject instanceof ClassIdentifier ) 
		{
			Class clas = ((ClassIdentifier)evalBaseObject).getTargetClass();
			String field = prefix(evalName, 1);

			Object obj = null;
			// static field?
			try {
				if ( Interpreter.DEBUG ) 
					Interpreter.debug("Name call to getStaticField, class: " 
						+clas+", field:"+field);
				obj = Reflect.getStaticField(clas, field);
			} catch( ReflectError e ) { 
				if ( Interpreter.DEBUG ) 
					Interpreter.debug("field reflect error: "+e);
			}

			// inner class?
			if ( obj == null ) {
				String iclass = clas.getName()+"$"+field;
				Class c = namespace.getClass( iclass );
				if ( c != null )
					obj = new ClassIdentifier(c);
			}

			if ( obj == null )
				throw new UtilEvalError(
					"No static field or inner class: " + field + " of " + clas);

			evalName = suffix(evalName);
			return (evalBaseObject = obj);
		}

		/*
			If we've fallen through here we are no longer resolving to
			a class type.
		*/
		if ( forceClass )
			throw new UtilEvalError( value +" does not resolve to a class name." );

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
		throw new UtilEvalError(
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
		throws UtilEvalError
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
				throw new UtilEvalError("Redundant to call .this on This type");
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
					throw new UtilEvalError(
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
				throw new UtilEvalError(
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
				throw new UtilEvalError(
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

		@throws ClassNotFoundException on class not found.
		@throws ClassPathException (type of EvalError) on special case of 
		ambiguous unqualified name after super import. 
	*/
	synchronized public Class toClass() 
		throws ClassNotFoundException, UtilEvalError
	{
		if ( asClass != null )
			return asClass;

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
			} catch ( UtilEvalError  e ) { }; // couldn't resolve it
		
			if ( obj instanceof ClassIdentifier )
				clas = ((ClassIdentifier)obj).getTargetClass();
		}

		if ( clas == null )
			throw new ClassNotFoundException(
				"Class: " + value+ " not found in namespace");

		asClass = clas;
		return asClass;
	}

	/*
	*/
	synchronized public LHS toLHS( 
		CallStack callstack, Interpreter interpreter )
		throws UtilEvalError
	{
		//if ( asLHS != null ) 
			//return asLHS;

	// Need to clean this up to a single return statement

		reset();

		/* if ( Interpreter.DEBUG ) 
			Interpreter.debug("Name toLHS: "+evalName+ " isCompound = "
			+ isCompound(evalName));
		*/

		// variable
		if(!isCompound(evalName)) {
			//Interpreter.debug("returning simple var LHS...");
			asLHS = new LHS(namespace,evalName);
			return asLHS;
		}

		// field
		Object obj = null;
		try
		{
			while(isCompound(evalName))
				obj = consumeNextObjectField( callstack, interpreter, false );
		}
		catch( UtilEvalError e )
		{
			throw new UtilEvalError("LHS evaluation: " + e);
		}

		if ( obj == null )
			throw new InterpreterError("internal error 2893749283");

		if(obj instanceof This)
		{
			Interpreter.debug("found This reference evaluating LHS");
			asLHS = new LHS(((This)obj).namespace, evalName);
			return asLHS;
		}

		if(evalName != null)
		{
			try
			{
				//System.err.println("Name getLHSObjectField call obj = "
				//	+obj+", name="+evalName);

				if ( obj instanceof ClassIdentifier ) 
				{
					Class clas = ((ClassIdentifier)obj).getTargetClass();
					asLHS = Reflect.getLHSStaticField(clas, evalName);
					return asLHS;
				} else {
					asLHS = Reflect.getLHSObjectField(obj, evalName);
					return asLHS;
				}
			} catch(ReflectError e)
			{
				throw new UtilEvalError("Field access: "+e);
			}
		}

		throw new InterpreterError("Internal error in lhs...");
	}
	
	private BshMethod toLocalMethod( Object [] args )
	{
		Class [] sig = Reflect.getTypes( args );
		return namespace.getMethod( value, sig );
	}


    /**
		Invoke the method identified by this name.
		Performs caching of method resolution using SignatureKey.
		<p>

        Name contains a wholely unqualfied messy name; resolve it to 
		( object | static prefix ) + method name and invoke.
		<p>

        The interpreter is necessary to support 'this.interpreter' references
		in the called code. (e.g. debug());
		<p>

		<pre>
        Some cases:

            // dynamic
            local();
            myVariable.foo();
            myVariable.bar.blah.foo();
            // static
            java.lang.Integer.getInteger("foo");
		</pre>
    */
    public Object invokeMethod(
		Interpreter interpreter, Object[] args, CallStack callstack,
		SimpleNode callerInfo
	)
        throws UtilEvalError, EvalError, ReflectError, InvocationTargetException
    {
		if ( !Name.isCompound(value) )
			return invokeLocalMethod( 
				interpreter, args, callstack, callerInfo );

        // Find target object or class identifier
        Name targetName = namespace.getNameResolver( Name.prefix(value));
        String methodName = Name.suffix(value, 1);

        Object obj = targetName.toObject( callstack, interpreter );

		if ( obj == Primitive.VOID ) 
			throw new UtilEvalError( "Attempt to resolve method: "+methodName
					+"() on undefined variable or class name: "+targetName);

        // if we've got an object, resolve the method
        if ( !(obj instanceof Name.ClassIdentifier) ) {

            if (obj instanceof Primitive) {

                if (obj == Primitive.NULL)
                    throw new UtilTargetError( new NullPointerException( 
						"Null Pointer in Method Invocation" ) );

                // some other primitive
                // should avoid calling methods on primitive, as we do
                // in Name (can't treat primitive like an object message)
                // but the hole is useful right now.
                interpreter.error("Attempt to access method on primitive..." +
                    " allowing bsh.Primitive to peek through for debugging");
            }

            // found an object and it's not an undefined variable
            return Reflect.invokeObjectMethod(
				obj, methodName, args, interpreter, callstack, callerInfo );
        }

		// It's a class

        // try static method
        if ( Interpreter.DEBUG ) 
        	Interpreter.debug("invokeMethod: trying static - " + targetName);

        Class clas = ((Name.ClassIdentifier)obj).getTargetClass();
		
        if ( clas != null )
			return Reflect.invokeStaticMethod( clas, methodName, args );

        // return null; ???
		throw new UtilEvalError("invokeMethod: unknown target: " + targetName);
    }

	/**
		Invoke a locally declared method or a bsh command.
		If the method is not already declared in the namespace then try
		to load it as a resource from the /bsh/commands path.
	
		Note: instead of invoking the method directly here we should probably
		call resolveObjectMethod passing a This reference.  That would have
		the side effect of allowing a locally defined invoke() method to
		handle undeclared method invocations just like in objects.  Not sure
		if this is desirable...  It seems that if you invoke a method directly
		in scope it should be there.

		Keeping this code separate allows us to differentiate between methods
		invoked directly in scope and those invoked through object references.
	*/
    private Object invokeLocalMethod( 
		Interpreter interpreter, Object[] args, CallStack callstack,
		SimpleNode callerInfo
	)
        throws EvalError, ReflectError, InvocationTargetException
    {
        if ( Interpreter.DEBUG ) 
        	Interpreter.debug("resolve local method: " + value);

        // Check for locally declared method
        BshMethod meth = toLocalMethod( args );
        if ( meth != null )
			return meth.invoke( args, interpreter, callstack, callerInfo );
        else
            if ( Interpreter.DEBUG ) 
				Interpreter.debug("no locally declared method: " + value);

	/*
		// Check for imported object method
		Method imeth = toImportedMethod( args );
        if ( imeth != null )
			return imeth.invoke( args, interpreter, callstack, callerInfo );
	*/

		// Look for scripted command as resource
        String commandName = "commands/" + value + ".bsh";
// Need to use class manager here...
        InputStream in = Interpreter.class.getResourceAsStream(commandName);
        if (in != null)
        {
            if ( Interpreter.DEBUG ) 
				Interpreter.debug("loading resource: " + commandName);

			if ( interpreter == null )
				throw new InterpreterError(
					"invokeLocalMethod: interpreter = null");

			try {
				interpreter.eval( 
					new InputStreamReader(in), namespace, commandName);
			/* 
				Strange case where we actually catch an EvalError 
				We are using the interpreter as
				a tool to load the command... not as part of the execution
				path.  The error points here... thrown exception includes the 
				command's error... (right?)
			*/
			} catch ( EvalError e ) {
				Interpreter.debug( e.toString() );
				throw new EvalError(
					"Error loading command: "+ e.getMessage(), 
					callerInfo, callstack );
			}

            // try again
            meth = toLocalMethod( args );
            if ( meth != null )
                return meth.invoke( args, interpreter, callstack, callerInfo );
            else
                throw new EvalError("Loaded resource: " + commandName +
                    "had an error or did not contain the correct method", 
					 callerInfo, callstack );
        }

        // check for compiled bsh command class

        commandName = "bsh.commands." + value;
        Class c = interpreter.getClassManager().classForName( commandName );
        if ( c == null )
            throw new EvalError("Command not found: " + value, 
			callerInfo, callstack );
		//System.out.println("found class: " +c);

        // add interpereter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = namespace;
        System.arraycopy(args, 0, invokeArgs, 2, args.length);
		try {
        	return Reflect.invokeStaticMethod( c, "invoke", invokeArgs );
		} catch ( ReflectError e ) {
			System.err.println("Invoke method not found");
		} catch ( UtilEvalError e ) {
			throw e.toEvalError( callerInfo, callstack );
		}

        // try to print help
        try {
            String s = (String)Reflect.invokeStaticMethod(
				c, "usage", null);
            interpreter.println(s);
            return Primitive.VOID;
        } catch(ReflectError e) {
            if ( Interpreter.DEBUG ) Interpreter.debug("usage threw: " + e);
            throw new EvalError(
				"Wrong number or type of args for command:", 
				callerInfo, callstack );
        } catch( UtilEvalError e) {
			throw e.toEvalError( callerInfo, callstack );
		}

		//throw new EvalError( "No local method or command: "+ value, 
			//callerInfo, callstack );
    }

	// Static methods that operate on compound ('.' separated) names

	public static boolean isCompound(String value)
	{
		return value.indexOf('.') != -1 ;
		//return countParts(value) > 1;
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
		if (parts < 1)
			return null;

		int count = 0;
		int index = value.length() + 1;

		while ( ((index = value.lastIndexOf('.', index - 1)) != -1) 
			&& (++count < parts) );

		return (index == -1) ? value : value.substring(index + 1);
	}

	// end compound name routines


	public String toString() { return value; }

	public static class ClassIdentifier {
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

