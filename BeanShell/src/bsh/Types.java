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

/**
	Static routines supporing type comparison and conversion in BeanShell.
*/
class Types 
{
	static final int CAST=0, ASSIGNMENT=1;
	/**
		Special value that indicates by identity that the result of a cast
		operation was a valid cast.  This is used by castObject() and
		castPrimitive() in the checkOnly mode of operation.  This value is a 
		Primitive type so that it can be returned by castPrimitive.
	*/
	static Primitive VALID_CAST = new Primitive(1);
	static Primitive INVALID_CAST = new Primitive(-1);
	/**
		Get the Java types of the arguments.
	*/
    public static Class[] getTypes( Object[] args )
    {
        if ( args == null )
            return new Class[0];

        Class[] types = new Class[ args.length ];

        for( int i=0; i<args.length; i++ )
        {
			if ( args[i] == null )
				types[i] = null;
            else 
			if ( args[i] instanceof Primitive )
                types[i] = ((Primitive)args[i]).getType();
            else
                types[i] = args[i].getClass();
        }

        return types;
    }

	/**
		Arguments are assignable as defined by Types.getAssignableForm()
		which takes into account special bsh conversions such as XThis and
		primitive wrapper promotion.
		@deprecated fix this! need to stop catching exception
	*/
	static boolean argsAssignable( Class [] parameters, Object [] args )
	{
		Class [] argTypes = getTypes( args );
		return isSignatureAssignable( argTypes, parameters );
	}

	/**
		Is the 'from' signature (argument types) assignable to the 'to' 
		signature (candidate method types) using isJavaAssignable()?
		This method handles the special case of null values in 'to' types 
		indicating a loose type and matching anything.
	*/
	/*
		Should check for strict java here and use isJavaAssignable() instead
	*/
    static boolean isSignatureAssignable( Class[] from, Class[] to )
    {
        if ( from.length != to.length )
            return false;

        for(int i=0; i<from.length; i++)
            if ( !isBshAssignable( to[i], from[i] ) )
                return false;

        return true;
    }

    /**
		Is a standard Java assignment legal from the rhs type to the lhs type
		in a normal assignment (i.e. without any cast)?
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

		Note that the getAssignableForm() method is the primary bsh method for 
		checking assignability.  It adds additional bsh conversions, etc. 

		@see #isBshAssignable( Class, Class )
		@param lhs assigning from rhs to lhs
		@param rhs assigning from rhs to lhs
	*/
    static boolean isJavaAssignable( Class lhs, Class rhs )
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

	/**
		@param rhs may be Primitive
		@param lhsType is java type or TYPE
		@deprecated Getting rid of this...
		Use isBshAssignable(...) and castObject(...)
	*/
    public static Object getAssignableForm( Object rhs, Class lhsType )
		throws UtilEvalError
    {
		return castObject( rhs, lhsType, ASSIGNMENT );
    }

	/**
		Attempt to cast an object instance to a new type.
		This method can handle fromValue Primitive types (representing 
		primitive casts) as well as fromValue object casts requiring interface 
		generation, etc.

		@param toType the class type of the cast result, which may include
		primitive types, e.g. Byte.TYPE

		@param fromValue an Object or bsh.Primitive primitive value (including
			Primitive.NULL or Primitive.VOID )

		@see #isBshAssignable( Class, Class )
	*/
	public static Object castObject( 
		Object fromValue, Class toType, int operation ) 
		throws UtilEvalError
	{
		if ( fromValue == null )
			throw new InterpreterError("null fromValue");

		Class fromType = 
			fromValue instanceof Primitive ? 
				((Primitive)fromValue).getType() 
				: fromValue.getClass();

		return castObject( 
			toType, fromType, fromValue, operation, false/*checkonly*/ );
	}

	static boolean isBshAssignable( Class toType, Class fromType )
	{
		try {
			return castObject( 
				toType, fromType, null/*fromValue*/, 
				ASSIGNMENT, true/*checkOnly*/ 
			) == VALID_CAST;
		} catch ( UtilEvalError e ) {
			// This should not happen with checkOnly true
			throw new InterpreterError("err in cast check: "+e);
		}
	}

	/*
		Perform a cast, cast check, or assignability check.

		@param toType the class type of the cast result, which may include
			primitive types, e.g. Byte.TYPE.  toType may be null to indicate a
			loose type assignment (which matches any fromType).

		@param fromType is the class type of the value to be cast including
			java primitive TYPE classes for primitives.
			fromType should be null to indicate that the fromValue is or would
			be Primitive.NULL

		@param fromValue an Object or bsh.Primitive primitive value (including
			Primitive.NULL or Primitive.VOID )

		@param checkOnly If checkOnly is true then fromValue must be null.
			FromType is checked for the cast to toType...
			If checkOnly is false then fromValue must be non-null
			(Primitive.NULL is ok) and the actual cast is performed.

		@throws UtilEvalError on invalid assignment (when operation is
			assignment ).

		@throws UtilTargetError wrapping ClassCastException on cast error 
			(when operation is cast)

		@param operation is Types.CAST or Types.ASSIGNMENT

		@see Primitive.getType()
	*/
	static Object castObject( 
		Class toType, Class fromType, Object fromValue, 
		int operation, boolean checkOnly )
		throws UtilEvalError
	{
		/*
			Lots of preconditions checked here...
			Once things are running smoothly we might comment these out
			(That's what assertions are for).
		*/
		if ( checkOnly && fromValue != null )
			throw new InterpreterError("bad cast params 1");
		if ( !checkOnly && fromValue == null )
			throw new InterpreterError("bad cast params 2");
		if ( fromType == Primitive.class )
			throw new InterpreterError("bad from Type, need to unwrap");
		if ( fromValue == Primitive.NULL && fromType != null )
			throw new InterpreterError("inconsistent args 1");
		if ( fromValue == Primitive.VOID && fromType != Void.TYPE )
			throw new InterpreterError("inconsistent args 2");
		if ( toType == Void.TYPE )
			throw new InterpreterError("loose toType should be null");
		
		// assignment to loose type, void type, or exactly same type
		if ( toType == null || toType == fromType )
			return checkOnly ? VALID_CAST :
				fromValue;

        if ( toType.isPrimitive() ) 
		{
			if ( fromType == Void.TYPE || fromType == null 
				|| fromType.isPrimitive() )
			{
				// Both primitives, do primitive cast
				return Primitive.castPrimitive( 
					toType, fromType, (Primitive)fromValue, 
					checkOnly, operation );
			} else
			{
				if ( Primitive.isWrapperType( fromType ) )
				{
					// wrapper to primitive
					// Convert value to Primitive and check/cast it.

					//Object r = checkOnly ? VALID_CAST :
					Class unboxedFromType = Primitive.unboxType( fromType );
					Primitive primFromValue;
					if ( checkOnly ) 
						primFromValue = null; // must be null in checkOnly
					else
						primFromValue = (Primitive)Primitive.wrap( 
							fromValue, unboxedFromType );

					return Primitive.castPrimitive( 
						toType, unboxedFromType, primFromValue, 
						checkOnly, operation );
				} else
				{
					// Cannot cast from arbitrary object to primitive
					if ( checkOnly )
						return INVALID_CAST;
					else
						throw castError( toType, fromType, operation );
				}
			}
        }

		// Casting to reference type
		if ( fromType == Void.TYPE || fromType == null
			|| fromType.isPrimitive() )
		{
			if ( Primitive.isWrapperType( toType ) 
				&& fromType != Void.TYPE && fromType != null )
			{
				// primitive to wrapper type
				return checkOnly ? VALID_CAST :
					Primitive.castWrapper( 
						Primitive.unboxType(toType), 
						((Primitive)fromValue).getValue() );
			}

			// Primitive (not null or void) to Object.class type
			if ( toType == Object.class 
				&& fromType != Void.TYPE && fromType != null )
			{
				// box it
				return checkOnly ? VALID_CAST :
					((Primitive)fromValue).getValue();
			}

			// Primitive to arbitrary object type. 
			// Allow Primitive.castToType() to handle it as well as cases of 
			// Primitive.NULL and Primitive.VOID
			return Primitive.castPrimitive( 
				toType, fromType, (Primitive)fromValue, checkOnly, operation );
		}

		// If type already assignable no cast necessary
		// We do this last to allow various errors above to be caught.
		// e.g cast Primitive.Void to Object would pass this
		if ( toType.isAssignableFrom( fromType ) )
			return checkOnly ? VALID_CAST : 
				fromValue;

		// Can we use the proxy mechanism to cast a bsh.This to 
		// the correct interface?
		if ( toType.isInterface() 
			&& bsh.This.class.isAssignableFrom( fromType ) 
			&& Capabilities.canGenerateInterfaces() 
		)
			return checkOnly ? VALID_CAST : 
				((bsh.This)fromValue).getInterface( toType );

		// Both numeric wrapper types? 
		// Try numeric style promotion wrapper cast
		if ( Primitive.isWrapperType( toType ) 
			&& Primitive.isWrapperType( fromType ) 
		)
			return checkOnly ? VALID_CAST :
				Primitive.castWrapper( toType, fromValue );
		
		if ( checkOnly )
			return INVALID_CAST;
		else
			throw castError( toType, fromType , operation  );
	}

	/**
		Return a UtilEvalError or UtilTargetError wrapping a ClassCastException
		describing an illegal assignment or illegal cast, respectively.	
	*/
    static UtilEvalError castError( 
		Class lhsType, Class rhsType, int operation   ) 
    {
		return castError( 
			Reflect.normalizeClassName(lhsType),
			Reflect.normalizeClassName(rhsType), operation  );
    }

    static UtilEvalError castError( 
		String lhs, String rhs, int operation   ) 
    {
		if ( operation == ASSIGNMENT )
			return new UtilEvalError (
				"Can't assign " + rhs + " to "+ lhs );

		Exception cce = new ClassCastException(
			"Cannot cast " + rhs + " to " + lhs );
		return new UtilTargetError( cce );
    }

	// Currently unused, but we'll probably need it later.
	/**
		Determine if a cast would be legitimate in order to handle the 
		special cases where a numeric declared var is assigned a type larger 
		than it can handle. (JLS cite??)

			byte b = 5;
			byte b1 = 5*10;

		Normally the above would be int types.

	boolean canCastToDeclaredType( Object value, Class toType ) {
		if ( !(value instanceof Primitive) )
			return false;
		Class fromType = ((Primitive)value).getType();
		
		if ( (toType==Byte.TYPE || toType==Short.TYPE || toType==Character.TYPE)
			&& fromType == Integer.TYPE 
		)
			return true;
		else
			return false;
	}
	*/

	/**
	// Need to confirm that this works under 1.1
	static Class typeDescriptorToType( String desc ) 
	{
		if ( desc.startsWith("[") )
			// wouldn't normally use Class.forName, but for primitive ok...
			return Class.forName(
	}
	*/
}
