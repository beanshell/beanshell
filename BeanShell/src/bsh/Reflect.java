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

import java.lang.reflect.*;
import java.io.*;
import java.util.Vector;

// import Name.ClassIdentifier;  // does not work with 1.2.2?

/**
    All of the reflection API code lies here.  It is in the form
	of static utilities.  See the design note about object wrappers 
	in LHS.java for lamentations regarding this.
*/
class Reflect {

    /*
		Invoke method on object, may be static, dynamic, or This
		Note: we could probably remove This handling and prevent it
		from coming here...
	*/
    public static Object invokeObjectMethod(
		Interpreter interpreter, Object object, String methodName, 
		Object[] args) 
		throws ReflectError, InvocationTargetException, EvalError 
	{
        Interpreter.debug("invoke Method " + methodName + " on object " 
			+ object + " with args (");

		if ( object instanceof This )
			return ((This)object).invokeMethod( methodName, args, interpreter );
        else
			return invokeMethod( object.getClass(), object, methodName, args );
    }

    /** 
		Invoke static method
	*/
    public static Object invokeStaticMethod(
		Class clas, String methodName, Object [] args)
        throws ReflectError, InvocationTargetException
    {
        Interpreter.debug("invoke static Method");
        return invokeMethod(clas, null, methodName, args);
    }

    public static Object getIndex(Object array, int index)
        throws ReflectError
    {
        try {
            Object val = Array.get(array, index);
            return wrapPrimitive(val, array.getClass().getComponentType());
        }
        catch(Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    public static void setIndex(Object array, int index, Object val)
        throws ReflectError
    {
        try {
            val = unwrapPrimitive(val);
            Array.set(array, index, val);
        }
        catch(Exception e) {
            throw new ReflectError("Array access:" + e);
        }
    }

    public static Object getStaticField(Class clas, String fieldName)
        throws ReflectError
    {
        return getFieldValue(clas, null, fieldName);
    }

    public static Object getObjectField(Object object, String fieldName)
        throws ReflectError
    {
		if ( object instanceof This )
			return ((This)object).namespace.getVariable( fieldName );
		else
			return getFieldValue(object.getClass(), object, fieldName);
    }

    static LHS getLHSStaticField(Class clas, String fieldName)
        throws ReflectError
    {
        Field f = getField(clas, fieldName);
        return new LHS(f);
    }

    static LHS getLHSObjectField(Object object, String fieldName)
        throws ReflectError
    {
		if ( object instanceof This )
			return new LHS(((This)object).namespace, fieldName );

        Field f = getField(object.getClass(), fieldName);
        return new LHS(object, f);
    }

    private static Object getFieldValue(
		Class clas, Object object, String fieldName) throws ReflectError
    {
        try {
            Field f = getField(clas, fieldName);
// experiment
//f.setAccessible(true);
            if(f == null)
                throw new ReflectError("internal error 234423");

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

    private static Field getField(Class clas, String fieldName)
        throws ReflectError
    {
        try
        {
			// need to fix this for accessibility
			// this one only finds public 
            return clas.getField(fieldName);
			// this one doesn't find interfaces, etc.
            //return clas.getDeclaredField(fieldName);
        }
        catch(NoSuchFieldException e)
        {
            throw new ReflectError("No such field: " + fieldName );
        }
    }

    /*
        The full blown invoke method.
        Everybody should come here.

		Note: Method invocation could probably be speeded up if we eliminated
		the throwing of exceptions in the search for the proper method.
		We could probably cache our knowledge of method structure as well.
    */
    private static Object invokeMethod(
		Class clas, Object object, String name, Object[] args)
        throws ReflectError, InvocationTargetException
    {
        if(args == null)
            args = new Object[] { };

        // simple sanity check for voids
        // maybe this should have been caught further up?
        for(int i=0; i<args.length; i++)
            if(args[i] == Primitive.VOID)
                throw new ReflectError("Attempt to pass void argument " +
                    "(position " + i + ") to method: " + name);

        Class returnType = null;
        Object returnValue = null;

        Class[] types = getTypes(args);
        unwrapPrimitives(args);

        /*  This is structured poorly...  */
        try
        {
            try
            {
				Method m = findAccessibleMethod(clas, name, types);
                returnValue =  m.invoke(object, args);
                if(returnValue == null)
                    returnValue = Primitive.NULL;
                returnType = m.getReturnType();
            }
            catch(ReflectError e)
            {
                Interpreter.debug("Exact method " + methodString(name, types) +
                    " not found in '" + clas.getName() + "'");
            }

            if ( returnValue == null ) {
				if ( types.length == 0 )
					throw new ReflectError("No args method " + 
						methodString(name, types) + " not found in class'" + 
						clas.getName() + "'");

				// try to find an assignable method
				Method[] methods = clas.getMethods();
				Method m = findMostSpecificMethod(name, types, methods);

				if(m == null)
					m = findExtendedMethod(name, args, methods);

				if (m == null )
					throw new ReflectError("Method " + 
						methodString(name, types) + 
						" not found in class'" + clas.getName() + "'");

				// have the method
				m = findAccessibleMethod(
					clas, m.getName(), m.getParameterTypes());

				returnValue = m.invoke(object, args);
				returnType = m.getReturnType();
            }
        } catch(IllegalAccessException e) {
            throw new ReflectError( 
				"Cannot access method " + methodString(name, types) +
                " in '" + clas.getName() + "' :" + e);
        }

        return wrapPrimitive(returnValue, returnType);
    }

	/*
		Locate a version of the method that is accessible via a public 
		interface or through a public superclass.

		This solves the problem that arises when a package private class
		or private inner class implements a public interface or derives from
		a public type.
	*/
	static Method findAccessibleMethod( 
		Class clas, String name, Class [] types ) throws ReflectError 
	{
		Method meth = null;
		Vector classQ = new Vector();

		classQ.addElement( clas );
		while ( classQ.size() > 0 ) {
			Class c = (Class)classQ.firstElement();
			classQ.removeElementAt(0);

			// Is this it?
			if ( Modifier.isPublic( c.getModifiers() ) ) {
				try {
					meth = c.getDeclaredMethod( name, types );
					if ( /*meth != null &&*/
						Modifier.isPublic( meth.getModifiers() ) )
						return meth; // Yes, it is.
				} catch ( Exception e ) { 
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
		
		throw new ReflectError( 
			"Can't find publically accessible version of method: "+
			methodString(name, types) +
			" in interfaces or class hierarchy of class "+clas.getName() );
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

    public static String methodString(String name, Class[] types)
    {
        StringBuffer sb = new StringBuffer(name + "(");
        for(int i=0; i<(types.length - 1); i++)
        {
            Class c = types[i];
            sb.append(((c == null) ? "null" : c.getName()) + ", ");
        }
        if(types.length > 0)
        {
            Class c = types[types.length - 1];
            sb.append(((c == null) ? "null" : c.getName()));
        }
        sb.append(")");
        return sb.toString();
    }

    private static Class[] getTypes(Object[] args)
    {
        if(args == null)
            return new Class[0];

        Class[] types = new Class[args.length];

        for(int i=0; i<args.length; i++)
        {
            if(args[i] instanceof Primitive)
                types[i] = ((Primitive)args[i]).getType();
            else
                types[i] = args[i].getClass();
        }

        return types;
    }

    /*
        Replace Primitive wrappers with their java.lang wrapper values

        These barf if one of the args is void...  maybe these should throw
        an exception on void arg to force the rest of the code to clean up.
        There are places where we don't check right now... (constructors, index)
    */
    private static void unwrapPrimitives(Object[] args)
    {
        for(int i=0; i<args.length; i++)
            args[i] = unwrapPrimitive(args[i]);
    }

    private static Object unwrapPrimitive(Object arg)
    {
        if(arg instanceof Primitive)
            return((Primitive)arg).getValue();
        else
            return arg;
    }

    static Object constructObject(String clas, Object[] args)
        throws ReflectError, InvocationTargetException
    {
		Class c = NameSpace.classForName( clas );
		if ( c == null )
			throw new ReflectError("Class not found: "+clas); 

		return constructObject( c, args );
	}

    static Object constructObject(Class clas, Object[] args)
        throws ReflectError, InvocationTargetException
    {
        // simple sanity check for arguments
        for(int i=0; i<args.length; i++)
            if(args[i] == Primitive.VOID)
                throw new ReflectError("Attempt to pass void argument " +
                    "(position " + i + ") to constructor for: " + clas);

        Constructor con = null;
        Object obj = null;
        Class[] types = getTypes(args);
        unwrapPrimitives(args);

        if ( con == null ) 
		{
			/* 
				Find an appropriate constructor
				use declared here to see package and private as well
				(there are no inherited constructors to worry about) 
			*/
			Constructor[] constructors = clas.getDeclaredConstructors();
			con = findMostSpecificConstructor(types, constructors);

			if ( con == null )
				if ( types.length == 0 )
					throw new ReflectError(
						"Can't find default constructor for: "+clas);
				else
					con = findExtendedConstructor(args, constructors);

			if(con == null)
				throw new ReflectError("Can't find constructor: " 
					+ clas );
        }

        try
        {
            obj = con.newInstance(args);
        }
        catch(InstantiationException e)
        {
            throw new ReflectError("the class is abstract ");
        }
        catch(IllegalAccessException e)
        {
            throw new ReflectError("we don't have permission to create an instance");
        }
        catch(IllegalArgumentException e)
        {
            throw new ReflectError("the number of arguments was wrong");
        }
        if(obj == null)
            throw new ReflectError("couldn't construct the object");

        return obj;
    }

    /*
        Implement JLS 15.11.2
        This should be modified to throw NoSuchMethodException on failure

        Note: the method findMostSpecificConstructor should always parallel
        this method.  Combine the code someday...
    */
    static Method findMostSpecificMethod(String name, Class[] idealMatch, Method[] methods)
    {
        Class[] bestMatch = null;
        Method bestMatchMethod = null;

        Interpreter.debug("Find most specific method for " + methodString("args", idealMatch));

        for(int i=0; i<methods.length; i++)
        {
            Class[] targetMatch = methods[i].getParameterTypes();

            /*
                If name is right and idealMatch fits targetMatch and this
                is the first match or targetMatch is more specific than the
                best match, make it the new best match.
            */
            if (name.equals(methods[i].getName()) &&
                isAssignable(idealMatch, targetMatch ) &&
                ((bestMatch == null) ||
                    isAssignable( targetMatch, bestMatch )))
            {
                bestMatch = targetMatch;
                bestMatchMethod = methods[i];
            }
        }

        if(bestMatch != null)
        {
            Interpreter.debug("best match: " + bestMatchMethod);
            return bestMatchMethod;
        }
        else
        {
            Interpreter.debug("no match found");
            return null;
        }
    }

	/**
		This uses the NameSpace.checkAssignableFrom() method to determine
		compatability of args.  This allows special (non standard Java) bsh 
		widening operations...
	*/
    static Method findExtendedMethod(
		String name, Object[] args, Method[] methods)
    {
        Method bestMatch = null;
        Object[] tempArgs = new Object[args.length];

        for(int i = 0; i < methods.length; i++) {
            Method currentMethod = methods[i];
            if ( name.equals( currentMethod.getName() )) {
                Class[] parameters = currentMethod.getParameterTypes();
                try {
                    for(int j = 0; j < parameters.length; j++)
                        tempArgs[j] = NameSpace.checkAssignableFrom( 
							args[j], parameters[j]);

                    // if you get here, all the arguments were assignable
                    System.arraycopy(tempArgs, 0, args, 0, args.length);
                    return currentMethod;
                } catch(EvalError e) {
                    // do nothing (exception breaks you out of the for loop).
                }
            }
        }

        return null;
    }

    /*
        This method should exactly parallel findMostSpecificMethod()
    */
    static Constructor findMostSpecificConstructor(Class[] idealMatch,
        Constructor[] constructors)
    {
        Class[] bestMatch = null;
        Constructor bestMatchConstructor = null;

        Interpreter.debug("Find most specific constructor for "
            + methodString("args", idealMatch));

        for(int i=0; i<constructors.length; i++)
        {
            Class[] targetMatch = constructors[i].getParameterTypes();

            if(isAssignable(idealMatch, targetMatch) &&
                ((bestMatch == null) ||
                    isAssignable(targetMatch, bestMatch)))
            {
                bestMatch = targetMatch;
                bestMatchConstructor = constructors[i];
            }
        }

        if(bestMatch != null)
        {
            Interpreter.debug("best match: " + bestMatchConstructor);
            return bestMatchConstructor;
        }
        else
        {
            Interpreter.debug("no match found");
            return null;
        }
    }

	/*
		This uses the NameSpace.checkAssignableFrom() method to determine
		compatability of args.  This allows special (non standard Java) bsh 
		widening operations...
	*/
    static Constructor findExtendedConstructor(Object[] args, Constructor[] constructors)
    {
        Constructor bestMatch = null;
        Object[] tempArgs = new Object[args.length];

        for(int i = 0; i < constructors.length; i++)
        {
            Constructor currentConstructor = constructors[i];
            Class[] parameters = currentConstructor.getParameterTypes();
            try {
                for(int j = 0; j < parameters.length; j++)
                    tempArgs[j] = 
						NameSpace.checkAssignableFrom(args[j], parameters[j]);

                // if you get here, all the arguments were assignable
                System.arraycopy(tempArgs, 0, args, 0, args.length);
                return currentConstructor;
            }
            catch(EvalError e)
            {
                // do nothing (exception breaks you out of the for loop).
            }
        }

        return null;
    }

    static boolean isAssignable(Class[] from, Class[] to)
    {
        if(from.length != to.length)
            return false;

        for(int i=0; i<from.length; i++)
        {
            // Let null match any object type
            if(from[i] == null)
            {
                if(!(to[i].isPrimitive()))
                    continue;
                else
                    return false;
            }

            if(!isAssignableFrom(to[i], from[i]))
                return false;
        }

        return true;
    }

    /**
		This base method is meant to address a deficiency of 
		Class.isAssignableFrom() which does not take primitive widening 
		conversions into account.

		Note that the checkAssignable() method in NameSpace is the primary
		bsh method for checking assignability.  It adds extended bsh
		conversions, etc.
	*/
    static boolean isAssignableFrom(Class lhs, Class rhs)
    {
        if(lhs.isPrimitive() && rhs.isPrimitive())
        {
            if(lhs == rhs)
                return true;

            // handle primitive widening conversions - JLS 5.1.2
            if((rhs == Byte.TYPE) && (lhs == Short.TYPE || lhs == Integer.TYPE ||
                lhs == Long.TYPE || lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if((rhs == Short.TYPE) && (lhs == Integer.TYPE || lhs == Long.TYPE ||
                lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if((rhs == Character.TYPE) && (lhs == Integer.TYPE || lhs == Long.TYPE ||
                lhs == Float.TYPE || lhs == Double.TYPE))
                    return true;

            if((rhs == Integer.TYPE) && (lhs == Long.TYPE || lhs == Float.TYPE ||
                lhs == Double.TYPE))
                    return true;

            if((rhs == Long.TYPE) && (lhs == Float.TYPE || lhs == Double.TYPE))
                return true;

            if((rhs == Float.TYPE) && (lhs == Double.TYPE))
                return true;
        }
        else
            if(lhs.isAssignableFrom(rhs))
                return true;

        return false;
    }

    public static Object getObjectProperty(Object obj, String propName)
        throws EvalError, ReflectError
    {
        String accessorName = "get" + Character.toUpperCase(propName.charAt(0)) +
            propName.substring(1);

        Object[] args = new Object[] { };

        Interpreter.debug("property access: ");
        try
        {
            // null interpreter, accessor doesn't need to know
            return invokeObjectMethod(null, obj, accessorName, args);
        }
        catch(InvocationTargetException e)
        {
            throw new EvalError("Property accessor threw exception!");
        }
    }

    public static void setObjectProperty(Object obj, String propName, Object value)
        throws ReflectError, EvalError
    {
        String accessorName = "set" + Character.toUpperCase(propName.charAt(0)) +
            propName.substring(1);

        Object[] args = new Object[] { value };

        Interpreter.debug("property access: ");
        try
        {
            // null interpreter, accessor doesn't need to know
            invokeObjectMethod(null, obj, accessorName, args);
        }
        catch(InvocationTargetException e)
        {
            throw new EvalError("Property accessor threw exception!");
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

	/**[
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

}

