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

import java.lang.reflect.*;
import java.io.*;
import java.util.Vector;

/**
    All of the reflection API code lies here.  It is in the form
	of static utilities.  Maybe this belongs in LHS.java or a generic object 
	wrapper class.

	Note: More work to do in here to fix up the extended signature matching.
	need to work in a search along with findMostSpecificSignature...
	<p>

	Note: there are lots of cases here where the Java reflection API makes
	us catch exceptions (e.g. NoSuchFieldException) in order to do basic
	searching.  This has to be inefficient...  I wish they would add a more
	normal Java API for locating fields.
*/
class Reflect 
{
    /**
		Invoke method on arbitrary object.
		invocation may be static (through the object instance) or dynamic.
		Object may be a bsh scripted object (This type).
	*/
    public static Object invokeObjectMethod(
		Object object, String methodName, Object[] args, 
		Interpreter interpreter, CallStack callstack, SimpleNode callerInfo ) 
		throws ReflectError, EvalError, InvocationTargetException
	{
		// Bsh scripted object
		if ( object instanceof This && !passThisMethod( methodName) ) 
			return ((This)object).invokeMethod( 
				methodName, args, interpreter, callstack, callerInfo );
		else 
		// Java object
		{ 
			// find the java method
			try {
				BshClassManager bcm = callstack.top().getClassManager();
				Class clas = object.getClass();

				Method method = resolveJavaMethod( 
					bcm, clas, object, methodName, args, false );

				return invokeOnMethod( method, object, args );
			} catch ( UtilEvalError e ) {
				throw e.toEvalError( callerInfo, callstack );
			}
		}
    }

    /** 
		Invoke a method known to be static.
		No object instance is needed and there is no possibility of the 
		method being a bsh scripted method.
	*/
    public static Object invokeStaticMethod(
		BshClassManager bcm, Class clas, String methodName, Object [] args )
        throws ReflectError, UtilEvalError, InvocationTargetException
    {
        Interpreter.debug("invoke static Method");
        Method method = resolveJavaMethod( 
			bcm, clas, null, methodName, args, true );
		return invokeOnMethod( method, null, args );
    }

	/**
	*/
	private static Object invokeOnMethod( 
		Method method, Object object, Object[] args ) 
		throws ReflectError, InvocationTargetException
	{
		if ( Interpreter.DEBUG ) 
		{
			Interpreter.debug("Invoking method (entry): "
				+method+" with args:" );
			for(int i=0; i<args.length; i++)
				Interpreter.debug(
					"args["+i+"] = "+args[i]
					+" type = "+args[i].getClass() );
		}
		
		// Map types to assignable forms, need to keep this fast...
		Object [] tmpArgs = new Object [ args.length ];
		Class [] types = method.getParameterTypes();
		try {
			for (int i=0; i<args.length; i++)
				tmpArgs[i] = NameSpace.getAssignableForm( args[i], types[i] );
		} catch ( UtilEvalError e ) {
			throw new InterpreterError(
				"illegal argument type in method invocation: "+e );
		}

		// unwrap any primitives
		tmpArgs = unwrapPrimitives( tmpArgs );

		if ( Interpreter.DEBUG ) 
		{
			Interpreter.debug("Invoking method (after massaging values): "
				+method+" with tmpArgs:" );
			for(int i=0; i<tmpArgs.length; i++)
				Interpreter.debug(
					"tmpArgs["+i+"] = "+tmpArgs[i]
					+" type = "+tmpArgs[i].getClass() );
		}

		try 
		{
			Object returnValue = method.invoke( object, tmpArgs );
			if ( returnValue == null )
				returnValue = Primitive.NULL;
			Class returnType = method.getReturnType();

			return wrapPrimitive( returnValue, returnType );
		} catch( IllegalAccessException e ) {
			throw new ReflectError( "Cannot access method " 
				+ StringUtil.methodString(
					method.getName(), method.getParameterTypes() ) 
				+ " in '" + method.getDeclaringClass() + "' :" + e );
		}
	}

	/**
		Allow invocations of these method names on This type objects.
		Don't give bsh.This a chance to override their behavior.
		<p>

		If the method is passed here the invocation will actually happen on
		the bsh.This object via the regular reflective method invocation 
		mechanism.  If not, then the method is evaluated by bsh.This itself
		as a scripted method call.
	*/
	private static boolean passThisMethod( String name ) 
	{
		return 
			name.equals("getClass") 
			|| name.equals("invokeMethod")
			|| name.equals("getInterface")
			// These are necessary to let us test synchronization from scripts
			|| name.equals("wait") 
			|| name.equals("notify")
			|| name.equals("notifyAll");
	}

    public static Object getIndex(Object array, int index)
        throws ReflectError, UtilTargetError
    {
		if ( Interpreter.DEBUG ) 
			Interpreter.debug("getIndex: "+array+", index="+index);
        try {
            Object val = Array.get(array, index);
            return wrapPrimitive(val, array.getClass().getComponentType());
        }
        catch( ArrayIndexOutOfBoundsException  e1 ) {
			throw new UtilTargetError( e1 );
        } catch(Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    public static void setIndex(Object array, int index, Object val)
        throws ReflectError, UtilTargetError
    {
        try {
            val = Primitive.unwrap(val);
            Array.set(array, index, val);
        }
        catch( ArrayStoreException e2 ) {
			throw new UtilTargetError( e2 );
        } catch( IllegalArgumentException e1 ) {
			throw new UtilTargetError( 
				new ArrayStoreException( e1.toString() ) );
        } catch(Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    public static Object getStaticField(Class clas, String fieldName)
        throws UtilEvalError, ReflectError
    {
        return getFieldValue(clas, null, fieldName);
    }

    public static Object getObjectField( Object object, String fieldName )
        throws UtilEvalError, ReflectError
    {
		if ( object instanceof This )
			return ((This)object).namespace.getVariable( fieldName );
		else {
			try {
				return getFieldValue(object.getClass(), object, fieldName);
			} catch ( ReflectError e ) {
				// no field, try property acces

				if ( hasObjectPropertyGetter( object.getClass(), fieldName ) )
					return getObjectProperty( object, fieldName );
				else
					throw e;
			}
		}
    }

    static LHS getLHSStaticField(Class clas, String fieldName)
        throws UtilEvalError, ReflectError
    {
        Field f = getField(clas, fieldName);
        return new LHS(f);
    }

	/**
		Get an LHS reference to an object field.

		This method also deals with the field style property access.
		In the field does not exist we check for a property setter.
	*/
    static LHS getLHSObjectField( Object object, String fieldName )
        throws UtilEvalError, ReflectError
    {
		if ( object instanceof This )
		{
			// I guess this is when we pass it as an argument?
			// Setting locally
			boolean recurse = false; 
			return new LHS( ((This)object).namespace, fieldName, recurse );
		}

		try {
			Field f = getField(object.getClass(), fieldName);
			return new LHS(object, f);
		} catch ( ReflectError e ) {
			// not a field, try property access

			if ( hasObjectPropertySetter( object.getClass(), fieldName ) )
				return new LHS( object, fieldName );
			else
				throw e;
		}
    }

    private static Object getFieldValue(
		Class clas, Object object, String fieldName) 
		throws UtilEvalError, ReflectError
    {
        try {
            Field f = getField(clas, fieldName);

            if ( f == null )
                throw new ReflectError("internal: field not found:"+fieldName);

            Object value = f.get(object);
            Class returnType = f.getType();
            return wrapPrimitive(value, returnType);

        }
        catch(NullPointerException e) {
            throw new ReflectError(
				"???" + fieldName + " is not a static field.");
        }
        catch(IllegalAccessException e) {
            throw new ReflectError("Can't access field: " + fieldName);
        }
    }

	/**
		All field lookup should come through here.
		i.e. this method owns Class getField();
	*/
    private static Field getField(Class clas, String fieldName)
        throws UtilEvalError, ReflectError
    {
        try
        {
			if ( Capabilities.haveAccessibility() )
				return findAccessibleField( clas, fieldName );
			else
				// this one only finds public (and in interfaces, etc.)
				return clas.getField(fieldName);
        }
        catch( NoSuchFieldException e)
        {
			// try declaredField
            throw new ReflectError("No such field: " + fieldName );
        }
    }

	/**
		Used when accessibility capability is available to locate an occurrance
		of the field in the most derived class or superclass and set its 
		accessibility flag.
		Note that this method is not needed in the simple non accessible
		case because we don't have to hunt for fields.
		Note that classes may declare overlapping private fields, so the 
		distinction about the most derived is important.  Java doesn't normally
		allow this kind of access (super won't show private variables) so 
		there is no real syntax for specifying which class scope to use...
	*/
	private static Field findAccessibleField( Class clas, String fieldName ) 
		throws UtilEvalError, NoSuchFieldException
	{
		// Quick check catches public fields include those in interfaces
		try {
			return clas.getField(fieldName);
		} catch ( NoSuchFieldException e ) { }

		// Now, on with the hunt...
		while ( clas != null )
		{
			try {
				Field field = clas.getDeclaredField(fieldName);
				if ( ReflectManager.RMSetAccessible( field ) )
					return field;

			/*
				// Try interfaces of class for the field (has to be public)
				Class [] interfaces = clas.getInterfaces();
				for(int i=0; i<interfaces.length;i++) {
					try {
						return interfaces[i].getField( fieldName );
					} catch ( NoSuchFieldException e ) { }
				}
			*/
				// Not found, fall through to next class

			} catch(NoSuchFieldException e) { }

			clas = clas.getSuperclass();
		}
		throw new NoSuchFieldException( fieldName );
	}

    /**
        The full blown resolver method.  Everybody should come here.
		The method may be static or dynamic unless onlyStatic is set
		(in which case object may be null).

		@param onlyStatic 
			The method located must be static, the object param may be null.
		@throws ReflectError if method is not found
	*/
	/*
		Note: Method invocation could probably be speeded up if we eliminated
		the throwing of exceptions in the search for the proper method.
		After 1.3 we are caching method resolution anyway... shouldn't matter
		much.
    */
    static Method resolveJavaMethod (
		BshClassManager bcm, Class clas, Object object, 
		String name, Object[] args, boolean onlyStatic
	)
        throws ReflectError, UtilEvalError
    {
		Method method = null;

		if ( bcm == null ) 
			Interpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
		else
		{
			method = bcm.getResolvedMethod( clas, name, args, onlyStatic );
			if ( method != null )
				return method;
		}

		// This should probably be handled higher up
		if ( object == Primitive.NULL )
			throw new UtilTargetError( new NullPointerException(
				"Attempt to invoke method " +name+" on null value" ) );

        Class [] types = getTypes(args);

		// this was unecessary and counterproductive
        //args=unwrapPrimitives(args);

		// First try for an accessible version of the exact match.

		if ( Interpreter.DEBUG )
			Interpreter.debug( "Searching for method: "+
				StringUtil.methodString(name, types)
					+ " in '" + clas.getName() + "'" );

// Why do we do this?  Won't the overloaded resolution below find it
// just as well -- try to merge these next 

		try {
			method  = findAccessibleMethod(clas, name, types, onlyStatic);
		} catch ( SecurityException e ) { }

		if ( Interpreter.DEBUG && method != null )
			Interpreter.debug("findAccessibleMethod found: "+ method );

		// Look for an overloaded / standard Java assignable match
		// (First find the method, then find accessible version of it)
		if ( method == null ) 
		{
			// If no args stop here, can't do better than exact match above
			if ( types.length == 0 )
				throw new ReflectError(
					"No args "+ ( onlyStatic ? "static " : "" )
					+"method " + StringUtil.methodString(name, types) + 
					" not found in class'" + clas.getName() + "'");

			Method [] methods = clas.getMethods();
			if ( onlyStatic )
				methods = retainStaticMethods( methods );

			method = findMostSpecificMethod( name, types, methods );

			if ( Interpreter.DEBUG && method != null )
				Interpreter.debug("findMostSpecificMethod found: "+ method );

			// try to find an extended method
			if ( method == null )
			{
				method = findExtendedMethod( name, args, methods );

				if ( Interpreter.DEBUG && method != null )
					Interpreter.debug("findExtendedMethod found: "+ method );
			}

			// If we found an assignable or extended method, make sure we have 
			// an accessible version of it
			if ( method != null ) 
			{
				try {
					method = findAccessibleMethod( clas, method.getName(), 
						method.getParameterTypes(), onlyStatic);
				} catch ( SecurityException e ) { }
				if ( Interpreter.DEBUG && method == null )
					Interpreter.debug(
						"had a method, but it wasn't accessible");
			}
		}

		// If we didn't find anything throw error
		if ( method == null )
			throw new ReflectError(
				( onlyStatic ? "Static method " : "Method " )
				+ StringUtil.methodString(name, types) + 
				" not found in class'" + clas.getName() + "'");

		// Succeeded.  Cache the resolved method.
		if ( bcm != null )
			bcm.cacheResolvedMethod( clas, args, method );

		return method;
	}

	/**
		Return only the static methods
	*/
	private static Method [] retainStaticMethods( Method [] methods ) {
		Vector v = new Vector();
		for(int i=0; i<methods.length; i++)
			if ( Modifier.isStatic( methods[i].getModifiers() ) )
				v.addElement( methods[i] );

		Method [] ma = new Method [ v.size() ];
		v.copyInto( ma );
		return ma;
	}

	/**
		Locate a version of the method with the exact signature specified 
		that is accessible via a public interface or through a public 
		superclass or - if accessibility is on - through any interface or
		superclass.

		In the normal (non-accessible) case this still solves the problem that 
		arises when a package private class or private inner class implements a 
		public interface or derives from a public type.

		@param onlyStatic the method located must be static.
		@return null on not found
	*/
	static Method findAccessibleMethod( 
		Class clas, String name, Class [] types, boolean onlyStatic ) 
		throws UtilEvalError
	{
		Method meth = null;
		Method inaccessibleVersion = null;
		Vector classQ = new Vector();

		classQ.addElement( clas );
		Method found = null;
		while ( classQ.size() > 0 ) 
		{
			Class c = (Class)classQ.firstElement();
			classQ.removeElementAt(0);

			// Is this it?
			// Is the class public or can we use accessibility?
			if ( Modifier.isPublic( c.getModifiers() )
				|| ( Capabilities.haveAccessibility() ) )
			{
				try 
				{
					meth = c.getDeclaredMethod( name, types );

					// Is the method public or are we in accessibility mode?
					if ( ( Modifier.isPublic( meth.getModifiers() )
						&& Modifier.isPublic( c.getModifiers() ) )
						|| ( Capabilities.haveAccessibility() 
							&& ReflectManager.RMSetAccessible( meth ) ) )
					{
						found = meth; // Yes, it is.
						break;
					}
					else
					{
						// Found at least one matching method but couldn't use
						inaccessibleVersion = meth;
					}
				} catch ( NoSuchMethodException e ) { 
					// ignore and move on
				}
			}
			// No, it is not.
			
			// Is this a class?
			if ( !c.isInterface() ) {
				Class superclass = c.getSuperclass();
				if ( superclass != null )
					classQ.addElement((Object)superclass);
			}

			// search all of its interfaces breadth first
			Class [] intfs = c.getInterfaces();
			for( int i=0; i< intfs.length; i++ )
				classQ.addElement((Object)intfs[i]);
		}

		/* 
			If we found one and it satisfies onlyStatic return it
			
			Note: I don't believe it is necessary to check for the static
			condition in the above search because the Java compiler will not
			let dynamic and static methods hide/override one another.  So
			we simply check what is found, if any, at the end.
		*/
		if ( found != null &&
			( !onlyStatic || Modifier.isStatic( found.getModifiers() ) ) )
			return found;

		/*
			Not sure if this the best place to do this...
		*/
		if ( inaccessibleVersion != null )
			throw new UtilEvalError("Found non-public method: "
				+inaccessibleVersion
				+".  Use setAccessibility(true) to enable access to "
				+" private and protected members of classes." );
		
		return null;
	}

    private static Object wrapPrimitive(
		Object value, Class returnType) throws ReflectError
    {
        if(value == null)
            return Primitive.NULL;

        if(returnType == Void.TYPE)
            return Primitive.VOID;

        else
            if(returnType.isPrimitive())
            {
                if(value instanceof Number)
                    return new Primitive((Number)value);
                if(value instanceof Boolean)
                    return new Primitive((Boolean)value);
                if(value instanceof Character)
                    return new Primitive((Character)value);

                throw new ReflectError("Something bad happened");
            }
            else
                return value;
    }

    public static Class[] getTypes( Object[] args )
    {
        if ( args == null )
            return new Class[0];

        Class[] types = new Class[ args.length ];

        for( int i=0; i<args.length; i++ )
        {
			if ( args[i] == null )
				types[i] = null;
            else if ( args[i] instanceof Primitive )
                types[i] = ((Primitive)args[i]).getType();
            else
                types[i] = args[i].getClass();
        }

        return types;
    }

    /*
        Unwrap Primitive wrappers to their java.lang wrapper values.
		e.g. Primitive(42) becomes Integer(42)
    */
    private static Object [] unwrapPrimitives( Object[] args )
    {
		Object [] oa = new Object[ args.length ];
        for(int i=0; i<args.length; i++)
            oa[i] = Primitive.unwrap( args[i] );
		return oa;
    }

	/*
    private static Object unwrapPrimitive( Object arg )
    {
        if ( arg instanceof Primitive )
            return((Primitive)arg).getValue();
        else
            return arg;
    }
	*/

	/**
		Primary object constructor
		This method is simpler than those that must resolve general method
		invocation because constructors are not inherited.
	*/
    static Object constructObject( Class clas, Object[] args )
        throws ReflectError, InvocationTargetException
    {
		if ( clas.isInterface() )
			throw new ReflectError(
				"Can't create instance of an interface: "+clas);

        Object obj = null;
        Class[] types = getTypes(args);
		// this wasn't necessary until we invoke it, moved...
        //args=unwrapPrimitives(args);
        Constructor con = null;

		/* 
			Find an appropriate constructor
			use declared here to see package and private as well
			(there are no inherited constructors to worry about) 
		*/
		Constructor[] constructors = clas.getDeclaredConstructors();
		if ( Interpreter.DEBUG ) 
			Interpreter.debug("Looking for most specific constructor: "+clas);
		con = findMostSpecificConstructor(types, constructors);

		if ( con == null )
			if ( types.length == 0 )
				throw new ReflectError(
					"Can't find default constructor for: "+clas);
			else
				con = findExtendedConstructor(args, constructors);

		if ( con == null )
			throw new ReflectError(
				"Can't find constructor: " 
				+ StringUtil.methodString( clas.getName(), types )
				+" in class: "+ clas.getName() );;

        try {
        	args=unwrapPrimitives( args );
            obj = con.newInstance( args );
        } catch(InstantiationException e) {
            throw new ReflectError("the class is abstract ");
        } catch(IllegalAccessException e) {
            throw new ReflectError(
				"we don't have permission to create an instance");
        } catch(IllegalArgumentException e) {
            throw new ReflectError("the number of arguments was wrong");
        } 
		if (obj == null)
            throw new ReflectError("couldn't construct the object");

        return obj;
    }

    /**
        Implement JLS 15.11.2 for method resolution
		@return null on no match
    */
    static Method findMostSpecificMethod(
		String name, Class[] idealMatch, Method[] methods )
    {
		// Pull out the method signatures with matching names
		Vector sigs = new Vector();
		Vector meths = new Vector();
		for(int i=0; i<methods.length; i++)
		{
			// method matches name 
			if ( methods[i].getName().equals( name )  ) 
			{
				meths.addElement( methods[i] );
				sigs.addElement( methods[i].getParameterTypes() );
			}
		}

		Class [][] candidates = new Class [ sigs.size() ][];
		sigs.copyInto( candidates );

		if ( Interpreter.DEBUG ) 
			Interpreter.debug("Looking for most specific method: "+name);
		int match = findMostSpecificSignature( idealMatch, candidates );
		if ( match == -1 )
			return null;
		else
			return (Method)meths.elementAt( match );
    }

    /*
        This method should parallel findMostSpecificMethod()
    */
    static Constructor findMostSpecificConstructor(
		Class[] idealMatch, Constructor[] constructors)
    {
		// We don't have to worry about the name of our constructors

		Class [][] candidates = new Class [ constructors.length ] [];
		for(int i=0; i< candidates.length; i++ )
			candidates[i] = constructors[i].getParameterTypes();

		int match = findMostSpecificSignature( idealMatch, candidates );
		if ( match == -1 )
			return null;
		else
			return constructors[ match ];
    }

	/**
		findExtendedMethod uses the NameSpace.getAssignableForm() method to 
		determine compatability of arguments.  This allows special (non
		standard Java) bsh widening operations.  
		
		Note that this method examines the *arguments* themselves not the types.

		@param args the arguments
		@return null on not found
	*/
	/*
		Note: shouldn't we use something analagous to findMostSpecificSignature
		on a result set, rather than choosing the first one we find?
		(findMostSpecificSignature doesn't know about extended types).
	*/ 
    static Method findExtendedMethod(
		String name, Object[] args, Method[] methods )
    {
        for(int i = 0; i < methods.length; i++) 
		{
            Method currentMethod = methods[i];
			Class[] parameterTypes = currentMethod.getParameterTypes();
            if ( name.equals( currentMethod.getName() ) 
					&& argsAssignable( parameterTypes, args ) ) 
				return currentMethod;
        }

        return null;
    }

	/**
		This uses the NameSpace.getAssignableForm() method to determine
		compatability of args.  This allows special (non standard Java) bsh 
		widening operations...
	*/
    static Constructor findExtendedConstructor(
		Object[] args, Constructor[] constructors )
    {
        for(int i = 0; i < constructors.length; i++) 
		{
            Constructor currentConstructor = constructors[i];
            Class[] parameterTypes = currentConstructor.getParameterTypes();
            if ( argsAssignable( parameterTypes, args ) ) 
				return currentConstructor;
        }

        return null;
    }

	/**
		Arguments are assignable as defined by NameSpace.getAssignableForm()
		which takes into account special bsh conversions such as XThis and (ug)
		primitive wrapper promotion.
	*/
	private static boolean argsAssignable( Class [] parameters, Object [] args )
	{
		if ( parameters.length != args.length )
			return false;

		try {
			for(int j = 0; j < parameters.length; j++)
				NameSpace.getAssignableForm( args[j], parameters[j]);
		} catch ( UtilEvalError e ) {
			return false;
		}
		return true;
	}


	/**
        Implement JLS 15.11.2
		Return the index of the most specific arguments match or -1 if no	
		match is found.
	*/
	static int findMostSpecificSignature(
		Class [] idealMatch, Class [][] candidates )
	{
		Class [] bestMatch = null;
		int bestMatchIndex = -1;

		for (int i=0; i < candidates.length; i++) {
			Class[] targetMatch = candidates[i];

            /*
                If idealMatch fits targetMatch and this is the first match 
				or targetMatch is more specific than the best match, make it 
				the new best match.
            */
			if ( isSignatureAssignable(idealMatch, targetMatch ) &&
				((bestMatch == null) ||
					isSignatureAssignable( targetMatch, bestMatch )))
			{
				bestMatch = targetMatch;
				bestMatchIndex = i;
			}
		}

		if ( bestMatch != null ) {
			/*
			if ( Interpreter.DEBUG ) 
				Interpreter.debug("best match: " 
				+ StringUtil.methodString("args",bestMatch));
			*/
				
			return bestMatchIndex;
		}
		else {
			Interpreter.debug("no match found");
			return -1;
		}
	}

	/**
		Is the 'from' signature (argument types) assignable to the 'to' 
		signature (candidate method types) using isJavaAssignableFrom()?
		This method handles the special case of null values in 'to' types 
		indicating a loose type and matching anything.
	*/
    private static boolean isSignatureAssignable( Class[] from, Class[] to )
    {
        if ( from.length != to.length )
            return false;

        for(int i=0; i<from.length; i++)
        {
			// Null 'to' type indicates loose type.  Match anything.
			if ( to[i] == null )
				continue;

            if ( !isJavaAssignableFrom( to[i], from[i] ) )
                return false;
        }

        return true;
    }

    /**
		Is a standard Java assignment legal from the rhs type to the lhs type
		in a normal assignment?
		<p/>
		For Java primitive TYPE classes this method takes primitive promotion
		into account.  The ordinary Class.isAssignableFrom() does not take 
		primitive promotion conversions into account.  Note that Java allows
		additional assignments without a cast in combination with variable
		declarations.  Those are handled elsewhere (maybe should be here with a
		flag?)
		<p/>
		This class accepts a null rhs type indicating that the rhs was the
		value Primitive.NULL and allows it to be assigned to any object lhs
		type (non primitive)
		<p/>

		Note that the getAssignableForm() method in NameSpace is the primary
		bsh method for checking assignability.  It adds additional bsh
		conversions, etc. (need to clarify what)

		@param lhs assigning from rhs to lhs
		@param rhs assigning from rhs to lhs
	*/
    static boolean isJavaAssignableFrom( Class lhs, Class rhs )
    {
		// null 'from' type corresponds to type of Primitive.NULL
		// assign to any object type
		if ( rhs == null ) 
			return !lhs.isPrimitive();

		if ( lhs.isPrimitive() && rhs.isPrimitive() )
		{
			if ( lhs == rhs )
				return true;

			// handle primitive widening conversions - JLS 5.1.2
			if ( (rhs == Byte.TYPE) && 
				(lhs == Short.TYPE || lhs == Integer.TYPE ||
                lhs == Long.TYPE || lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if ( (rhs == Short.TYPE) && 
				(lhs == Integer.TYPE || lhs == Long.TYPE ||
                lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if ((rhs == Character.TYPE) && 
				(lhs == Integer.TYPE || lhs == Long.TYPE ||
                lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if ((rhs == Integer.TYPE) && 
				(lhs == Long.TYPE || lhs == Float.TYPE ||
                lhs == Double.TYPE))
                    return true;

            if ((rhs == Long.TYPE) && 
				(lhs == Float.TYPE || lhs == Double.TYPE))
                return true;

            if ((rhs == Float.TYPE) && (lhs == Double.TYPE))
                return true;
        }
        else
            if ( lhs.isAssignableFrom(rhs) )
                return true;

        return false;
    }

	private static String accessorName( String getorset, String propName ) {
        return getorset 
			+ String.valueOf(Character.toUpperCase(propName.charAt(0))) 
			+ propName.substring(1);
	}

    public static boolean hasObjectPropertyGetter( 
		Class clas, String propName ) 
	{
		String getterName = accessorName("get", propName );
		try {
			clas.getMethod( getterName, new Class [0] );
			return true;
		} catch ( NoSuchMethodException e ) { /* fall through */ }
		getterName = accessorName("is", propName );
		try {
			Method m = clas.getMethod( getterName, new Class [0] );
			return ( m.getReturnType() == Boolean.TYPE );
		} catch ( NoSuchMethodException e ) {
			return false;
		}
	}

    public static boolean hasObjectPropertySetter( 
		Class clas, String propName ) 
	{
		String setterName = accessorName("set", propName );
		Class [] sig = new Class [] { clas };
		Method [] methods = clas.getMethods();

		// we don't know the right hand side of the assignment yet.
		// has at least one setter of the right name?
		for(int i=0; i<methods.length; i++)
			if ( methods[i].getName().equals( setterName ) )
				return true;
		return false;
	}

    public static Object getObjectProperty(
		Object obj, String propName )
        throws UtilEvalError, ReflectError
    {
        Object[] args = new Object[] { };

        Interpreter.debug("property access: ");
		Method method = null;

		Exception e1=null, e2=null;
		try {
			String accessorName = accessorName( "get", propName );
			method = resolveJavaMethod( 
				null/*bcm*/, obj.getClass(), obj, accessorName, args, false );
		} catch ( Exception e ) { 
			e1 = e;
		}
		if ( method == null )
			try {
				String accessorName = accessorName( "is", propName );
				method = resolveJavaMethod( null/*bcm*/, obj.getClass(), obj, 
					accessorName, args, false );
				if ( method.getReturnType() != Boolean.TYPE )
					method = null;
			} catch ( Exception e ) { 
				e2 = e;
			}
		if ( method == null )
			throw new ReflectError("Error in property getter: "
				+e1 + (e2!=null?" : "+e2:"") );

        try {
			return invokeOnMethod( method, obj, args );
        }
        catch(InvocationTargetException e)
        {
            throw new UtilEvalError("Property accessor threw exception: "
				+e.getTargetException() );
        }
    }

    public static void setObjectProperty(
		Object obj, String propName, Object value)
        throws ReflectError, UtilEvalError
    {
        String accessorName = accessorName( "set", propName );
        Object[] args = new Object[] { value };

        Interpreter.debug("property access: ");
        try {
			Method method = resolveJavaMethod( 
				null/*bcm*/, obj.getClass(), obj, accessorName, args, false );
			invokeOnMethod( method, obj, args );
        }
        catch ( InvocationTargetException e )
        {
            throw new UtilEvalError("Property accessor threw exception: "
				+e.getTargetException() );
        }
    }

    /** 
		This method is meant to convert a JVM-array class name to the correct
    	'fully-qualified name' for the array class - JLS 6.7
	*/
    public static String normalizeClassName(Class type)
    {
        if(!type.isArray())
            return type.getName();

        StringBuffer className = new StringBuffer();
        try
        {
            className.append(getArrayBaseType(type).getName());
            for(int i = 0; i < getArrayDimensions(type); i++)
                className.append("[]");
        }
        catch(Exception e) { }

        return className.toString();
    }

	/**
		returns the dimensionality of the Class
		returns 0 if the Class is not an array class
	*/
    public static int getArrayDimensions(Class arrayClass)
    {
        if(!arrayClass.isArray())
            return 0;

        return arrayClass.getName().lastIndexOf('[') + 1;
    }

    /**

		Returns the base type of an array Class.
    	throws ReflectError if the Class is not an array class.
	*/
    public static Class getArrayBaseType(Class arrayClass) throws ReflectError
    {
        if(!arrayClass.isArray())
            throw new ReflectError("The class is not an array.");

		return arrayClass.getComponentType();

    }

	/**
		A command may be implemented as a compiled Java class containing one or
		more static invoke() methods of the correct signature.  The invoke()
		methods must accept two additional leading arguments of the interpreter
		and callstack, respectively. e.g. invoke(interpreter, callstack, ... )
		This method adds the arguments and invokes the static method, returning
		the result.
	*/
	public static Object invokeCompiledCommand( 
		Class commandClass, Object [] args, Interpreter interpreter, 
		CallStack callstack )
		throws UtilEvalError
	{
        // add interpereter and namespace to args list
        Object[] invokeArgs = new Object[args.length + 2];
        invokeArgs[0] = interpreter;
        invokeArgs[1] = callstack;
        System.arraycopy( args, 0, invokeArgs, 2, args.length );
		BshClassManager bcm = interpreter.getClassManager();
		try {
        	return Reflect.invokeStaticMethod( 
				bcm, commandClass, "invoke", invokeArgs );
		} catch ( InvocationTargetException e ) {
			throw new UtilEvalError(
				"Error in compiled command: "+e.getTargetException() );
		} catch ( ReflectError e ) {
			throw new UtilEvalError("Error invoking compiled command: "+e );
		}
	}
}

/*
Ok, I wrote this... should we use it in lieu of the pair of methods?
I guess not...

    private static Object findExtendedMethodOrConstructor(
		String name, Object[] args, Object [] methodsOrConstructors )
    {
        for(int i = 0; i < methodsOrConstructors.length; i++) 
		{
            Object currentMethodOrConstructor = methodsOrConstructors[i];
			Class[] parameterTypes = 
				getParameterTypes( currentMethodOrConstructor );

			if ( currentMethodOrConstructor instanceof Method
				&& !name.equals(
					((Method)currentMethodOrConstructor).getName() ) )
				continue;

            if ( argsAssignable( parameterTypes, args ) )
				return currentMethodOrConstructor;
        }

        return null;
    }

	private static Class [] getParameterTypes( Object methodOrConstructor )
	{
		if ( methodOrConstructor instanceof Method )
			return ((Method)methodOrConstructor).getParameterTypes();
		else
			return ((Constructor)methodOrConstructor).getParameterTypes();
	}
*/

