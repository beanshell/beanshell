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
*/
/*
	Note: This class is messy.  The method and field resolution need to be
	rewritten.  Various methods in here catch NoSuchMethod or NoSuchField
	exceptions during their searches.  These should be rewritten to avoid
	having to catch the exceptions.  Method lookups are now cached at a high 
	level so they are less important, however the logic is messy.
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
		if ( object instanceof This && !This.isExposedThisMethod( methodName) ) 
			return ((This)object).invokeMethod( 
				methodName, args, interpreter, callstack, callerInfo,
				false/*delcaredOnly*/ );
		else 
		// Java object
		{ 
			// find the java method
			try {
				BshClassManager bcm = 
					interpreter == null ? null : interpreter.getClassManager();
				Class clas = object.getClass();

				Method method = resolveExpectedJavaMethod( 
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
        Method method = resolveExpectedJavaMethod( 
			bcm, clas, null, methodName, args, true );
		return invokeOnMethod( method, null, args );
    }

	/**
		Invoke the Java method on the specified object.
		@param args may be null
	*/
	static Object invokeOnMethod( 
		Method method, Object object, Object[] args ) 
		throws ReflectError, InvocationTargetException
	{
		if ( args == null )
			args = new Object[0];

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
				tmpArgs[i] = Types.getAssignableForm( args[i], types[i] );
		} catch ( UtilEvalError e ) {
			throw new InterpreterError(
				"illegal argument type in method invocation: "+e );
		}

		// unwrap any primitives
		tmpArgs = Primitive.unwrap( tmpArgs );

		if ( Interpreter.DEBUG ) 
		{
			Interpreter.debug("Invoking method (after massaging values): "
				+method+" with tmpArgs:" );
			for(int i=0; i<tmpArgs.length; i++)
				Interpreter.debug(
					"tmpArgs["+i+"] = "+tmpArgs[i]
					+" type = "+tmpArgs[i].getClass() );
		}

		try {
			Object returnValue = method.invoke( object, tmpArgs );
			if ( returnValue == null )
				returnValue = Primitive.NULL;
			Class returnType = method.getReturnType();

			return Primitive.wrap( returnValue, returnType );
		} catch( IllegalAccessException e ) {
			throw new ReflectError( "Cannot access method " 
				+ StringUtil.methodString(
					method.getName(), method.getParameterTypes() ) 
				+ " in '" + method.getDeclaringClass() + "' :" + e );
		}
	}

    public static Object getIndex(Object array, int index)
        throws ReflectError, UtilTargetError
    {
		if ( Interpreter.DEBUG ) 
			Interpreter.debug("getIndex: "+array+", index="+index);
        try {
            Object val = Array.get(array, index);
            return Primitive.wrap( val, array.getClass().getComponentType() );
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
        return getFieldValue( clas, null, fieldName, true/*onlystatic*/);
    }

	/**
	*/
    public static Object getObjectField( Object object, String fieldName )
        throws UtilEvalError, ReflectError
    {
		if ( object instanceof This )
			return ((This)object).namespace.getVariable( fieldName );
		else {
			try {
				return getFieldValue(
					object.getClass(), object, fieldName, false/*onlystatic*/);
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
        Field f = resolveExpectedJavaField( 
			clas, fieldName, true/*onlystatic*/);
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
			Field f = resolveExpectedJavaField( 
				object.getClass(), fieldName, false/*onlyStatic*/ );
			return new LHS(object, f);
		} catch ( ReflectError e ) 
		{
			// not a field, try property access
			if ( hasObjectPropertySetter( object.getClass(), fieldName ) )
				return new LHS( object, fieldName );
			else
				throw e;
		}
    }

    private static Object getFieldValue(
		Class clas, Object object, String fieldName, boolean onlyStatic ) 
		throws UtilEvalError, ReflectError
    {
        try {
            Field f = resolveExpectedJavaField( clas, fieldName, onlyStatic );

            Object value = f.get(object);
            Class returnType = f.getType();
            return Primitive.wrap( value, returnType );

        } catch( NullPointerException e ) { // shouldn't happen
            throw new ReflectError(
				"???" + fieldName + " is not a static field.");
        } catch(IllegalAccessException e) {
            throw new ReflectError("Can't access field: " + fieldName);
        }
    }

	/**
	*/
	/*
		Note: this method and resolveExpectedJavaField should be rewritten
		to invert this logic so that no exceptions need to be caught
		unecessarily.  This is just a temporary impl.
		@return the field or null if not found
	*/
    protected static Field resolveJavaField( 
		Class clas, String fieldName, boolean onlyStatic )
        throws UtilEvalError
    {
		try {
			return resolveExpectedJavaField( clas, fieldName, onlyStatic );
		} catch ( ReflectError e ) { 
			return null;
		}
	}

	/**
		@throws ReflectError if the field is not found.
	*/
	/*
		Note: this should really just throw NoSuchFieldException... need
		to change related signatures and code.
	*/
    protected static Field resolveExpectedJavaField( 
		Class clas, String fieldName, boolean onlyStatic
	)
        throws UtilEvalError, ReflectError
    {
		Field f;
        try {
			if ( Capabilities.haveAccessibility() )
				f = findAccessibleField( clas, fieldName );
			else
				// this one only finds public (and in interfaces, etc.)
				f = clas.getField(fieldName);
        }
        catch( NoSuchFieldException e)
        {
			// try declaredField
            throw new ReflectError("No such field: " + fieldName );
        }

		if ( onlyStatic && !Modifier.isStatic( f.getModifiers() ) )
			throw new UtilEvalError(
				"Can't reach instance field: "+fieldName
				+" from static context: "+clas.getName() );

		return f;
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

		@return the Field or throws NoSuchFieldException
		@throws NoSuchFieldException if the field is not found
	*/
	/*
		This method should be rewritten to use getFields() and avoid catching
		exceptions during the search.
	*/
	private static Field findAccessibleField( Class clas, String fieldName ) 
		throws UtilEvalError, NoSuchFieldException
	{
		Field field;

		// Quick check catches public fields include those in interfaces
		try {
			field = clas.getField(fieldName);
			ReflectManager.RMSetAccessible( field );
			return field;
		} catch ( NoSuchFieldException e ) { }

		// Now, on with the hunt...
		while ( clas != null )
		{
			try {
				field = clas.getDeclaredField(fieldName);
				ReflectManager.RMSetAccessible( field );
				return field;

				// Not found, fall through to next class

			} catch(NoSuchFieldException e) { }

			clas = clas.getSuperclass();
		}
		throw new NoSuchFieldException( fieldName );
	}

	/**
		This method expects a non-null method from resolveJavaMethod().
		If the method is not found it throws a descriptive ReflectError.
	*/
    protected static Method resolveExpectedJavaMethod(
		BshClassManager bcm, Class clas, Object object, 
		String name, Object[] args, boolean onlyStatic )
        throws ReflectError, UtilEvalError
    {
		Method method = resolveJavaMethod( 
			bcm, clas, object, name, args, onlyStatic );

		if ( method != null )
			return method;

		Class [] types = Types.getTypes(args);
		throw new ReflectError(
			( onlyStatic ? "Static method " : "Method " )
			+ StringUtil.methodString(name, types) + 
			" not found in class'" + clas.getName() + "'");
	}

    /**
        The full blown resolver method.  All other method invocation methods
		delegate to this.  The method may be static or dynamic unless
		onlyStatic is set (in which case object may be null).
		If onlyStatic is set the only static methods will be located.
		<p/>
		This method performs Java method caching internally.

		@param onlyStatic 
			The method located must be static, the object param may be null.
		@return the method or null if no matching method was found.
		@deprecated
	*/
	/*
		Note: object is only used here for precondition... get rid of it?
	*/
    protected static Method resolveJavaMethod(
		BshClassManager bcm, Class clas, Object object, 
		String name, Object[] args, boolean onlyStatic )
        throws UtilEvalError
    {
		// Why is object in the args?
		if ( object == Primitive.NULL )
			throw new UtilTargetError( new NullPointerException(
				"Attempt to invoke method " +name+" on null value" ) );

        Class [] types = Types.getTypes(args);
		return resolveJavaMethod( bcm, clas, name, types, onlyStatic );
	}

	/*
		Notes:

		This is broken.  It finds public but less specific methods over
		non-public but more specific ones.

		findMostSpecficMethod() needs to be rewritten to eliminate 
		findAccessibleMethod.  We should implement the findMostSpecificMethod 
		that uses the publicOnly flag.  FindMostSpecificMethod should also
		operate in two passes to give standard Java assignable matches priority
		over extended bsh type matches.
	*/
    protected static Method resolveJavaMethod(
		BshClassManager bcm, Class clas, String name, 
		Class [] types, boolean onlyStatic )
        throws UtilEvalError
    {
		if ( clas == null )
			throw new InterpreterError("null class");

		Method method = null;
		if ( bcm == null ) 
			Interpreter.debug("resolveJavaMethod UNOPTIMIZED lookup");
		else {
			method = bcm.getResolvedMethod( clas, name, types, onlyStatic );
			if ( method != null )
				return method;
		}

		if ( Interpreter.DEBUG )
			Interpreter.debug( "Searching for method: "+
				StringUtil.methodString(name, types)
					+ " in '" + clas.getName() + "'" );

		/*
			First try for an accessible version of the exact match.
			This first lookup seems redundant with below, but is apparently
			needed.  This whole thing is messy.
		*/
		try {
			method  = findAccessibleMethod( clas, name, types );
		} catch ( SecurityException e ) { }

		// If not found and there are arguments to match -
		// Look for an overloaded assignable match
		// (First find the method, then find accessible version of it)
		if ( method == null && types.length > 0 ) 
		{
			// Gather all of the methods of class and parents
			Vector mv = new Vector();
			Class c = clas;
			while( c != null )
			{
				Method [] m = c.getDeclaredMethods();
				for(int i=0; i<m.length; i++)
					mv.add( m[i] );
				c = c.getSuperclass();
			}
			Method [] methods = new Method [mv.size()];
			mv.copyInto( methods );

			boolean publicOnly = !Capabilities.haveAccessibility();
			method = findMostSpecificMethod( name, types, methods, publicOnly );

			if ( method != null && !Modifier.isPublic( method.getModifiers() ) )
			{
				try {
					ReflectManager.RMSetAccessible( method );
				} catch ( UtilEvalError e ) { /*ignore*/ }
			}
/*
			// If found a method, make sure we have accessible version of it
			if ( method != null ) 
			{
				try {
					method = findAccessibleMethod( 
						clas, method.getName(), method.getParameterTypes() );
				} catch ( SecurityException e ) { /leave null/ }
				if ( Interpreter.DEBUG && method == null )
					Interpreter.debug(
						"had a method, but it wasn't accessible");
			}
	*/

		}

		if ( method != null 
			&& onlyStatic && !Modifier.isStatic( method.getModifiers() ) 
		)
			throw new UtilEvalError(
				"Cannot reach instance method: "
				+ StringUtil.methodString(
					method.getName(), method.getParameterTypes() )
				+ " from static context: "+ clas.getName() );

		// Succeeded.  Cache the resolved method.
		if ( method != null && bcm != null )
			bcm.cacheResolvedMethod( clas, types, method );

		return method;
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
	/*
		Notes: See notes on findMostSpecificMethod.
		This method should be rolled into findMostSpecificMethod.
	*/
	static Method findAccessibleMethod( 
		Class clas, String name, Class [] types ) 
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

		if ( found != null )
			return found;

		if ( inaccessibleVersion != null )
			throw new UtilEvalError("Found non-public method: "
				+inaccessibleVersion
				+".  Use setAccessibility(true) to enable access to "
				+" private and protected members of classes." );
		
		return null; 
	}

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
        Class[] types = Types.getTypes(args);
        Constructor con = null;

		/* 
			Find an appropriate constructor.
			use declared here to see package and private as well
			(there are no inherited constructors to worry about) 
		*/
		Constructor[] constructors = clas.getDeclaredConstructors();
		if ( Interpreter.DEBUG ) 
			Interpreter.debug("Looking for most specific constructor: "+clas);
		con = findMostSpecificConstructor(types, constructors);

		if ( con == null )
		{
			if ( types.length == 0 )
				throw new ReflectError(
					"Can't find default constructor for: "+clas);
			else
				throw new ReflectError(
					"Can't find constructor: " 
					+ StringUtil.methodString( clas.getName(), types )
					+" in class: "+ clas.getName() );
		}

		if ( !Modifier.isPublic( con.getModifiers() )
			&& Capabilities.haveAccessibility() )
			try {
				ReflectManager.RMSetAccessible( con );
			} catch ( UtilEvalError e ) { /*ignore*/ }

        args=Primitive.unwrap( args );
        try {
            obj = con.newInstance( args );
        } catch(InstantiationException e) {
            throw new ReflectError("the class is abstract ");
        } catch(IllegalAccessException e) {
            throw new ReflectError(
				"We don't have permission to create an instance."
				+"Use setAccessibility(true) to enable access." );
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
	/*
		Notes:

		This is broken.  It finds public but less specific methods over
		non-public but more specific ones.

		This method needs to be rewritten to eliminate findAccessibleMethod.
		We should implement the findMostSpecificMethod that uses the publicOnly
		flag.  FindMostSpecificMethod should also operate in two passes to give
		standard Java assignable matches priority over extended bsh type
		matches.
	*/
    static Method findMostSpecificMethod(
		String name, Class[] idealMatch, Method[] methods,
		boolean publicOnly )
    {
		// Pull out the method signatures with matching names
		Vector sigs = new Vector();
		Vector meths = new Vector();
		for(int i=0; i<methods.length; i++)
		{
			if ( publicOnly && !Modifier.isPublic( methods[i].getModifiers() ) )
				continue;

			// method matches name 
			if ( methods[i].getName().equals( name ) ) 
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
		int match = 
			findMostSpecificConstructorIndex( idealMatch, constructors );
		if ( match == -1 )
			return null;
		else
			return constructors[ match ];
    }

    static int findMostSpecificConstructorIndex(
		Class[] idealMatch, Constructor[] constructors)
    {
		Class [][] candidates = new Class [ constructors.length ] [];
		for(int i=0; i< candidates.length; i++ )
			candidates[i] = constructors[i].getParameterTypes();

		return findMostSpecificSignature( idealMatch, candidates );
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
			if ( Types.isSignatureAssignable(idealMatch, targetMatch ) &&
				((bestMatch == null) ||
					Types.isSignatureAssignable( targetMatch, bestMatch )))
			{
				bestMatch = targetMatch;
				bestMatchIndex = i;
			}
		}

		if ( bestMatch != null )
			return bestMatchIndex;
		else
			return -1;
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
			method = resolveExpectedJavaMethod( 
				null/*bcm*/, obj.getClass(), obj, accessorName, args, false );
		} catch ( Exception e ) { 
			e1 = e;
		}
		if ( method == null )
			try {
				String accessorName = accessorName( "is", propName );
				method = resolveExpectedJavaMethod( 
					null/*bcm*/, obj.getClass(), obj, 
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
			Method method = resolveExpectedJavaMethod( 
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
		Return a more human readable version of the type name.
		Specifically, array types are returned with postfix "[]" dimensions.
		e.g. return "int []" for integer array instead of "class [I" as
		would be returned by Class getName() in that case.
	*/
    public static String normalizeClassName(Class type)
    {
        if ( !type.isArray() )
            return type.getName();

        StringBuffer className = new StringBuffer();
        try {
            className.append( getArrayBaseType(type).getName() +" ");
            for(int i = 0; i < getArrayDimensions(type); i++)
                className.append("[]");
        } catch( ReflectError e ) { /*shouldn't happen*/ }

        return className.toString();
    }

	/**
		returns the dimensionality of the Class
		returns 0 if the Class is not an array class
	*/
    public static int getArrayDimensions(Class arrayClass)
    {
        if ( !arrayClass.isArray() )
            return 0;

        return arrayClass.getName().lastIndexOf('[') + 1;  // why so cute?
    }

    /**

		Returns the base type of an array Class.
    	throws ReflectError if the Class is not an array class.
	*/
    public static Class getArrayBaseType(Class arrayClass) throws ReflectError
    {
        if ( !arrayClass.isArray() )
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

