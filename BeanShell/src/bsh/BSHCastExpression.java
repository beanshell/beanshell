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
	Implement casts.

	I think it should be possible to simplify some of the code here by
	using the NameSpace.getAssignableForm() method, but I haven't looked 
	into it.
*/
class BSHCastExpression extends SimpleNode {

    public BSHCastExpression(int id) { super(id); }

	/**
		@return the result of the cast.
	*/
	public Object eval(
		CallStack callstack, Interpreter interpreter ) throws EvalError
    {
		NameSpace namespace = callstack.top();
        Class toType = ((BSHType)jjtGetChild(0)).getType(namespace);
		SimpleNode expression = (SimpleNode)jjtGetChild(1);

        // evaluate the expression
        Object fromValue = expression.eval(callstack, interpreter);
        Class fromType = fromValue.getClass();

		try {
			return castObject( fromValue, toType );
		} catch ( EvalError e ) {
			e.reThrow( this );
			throw new InterpreterError("can't happen"); // help the compiler
		}
    }

	/**
		Cast an object to a new type.
		This method can handle bsh.Primitive types (representing primitive 
		casts) as well as arbitrary object casts.
		@param fromValue an Object or bsh.Primitive primitive value 
		@param toType the class type of the cast result, which may include
		primitive types, e.g. Byte.TYPE
	*/
	public static Object castObject( Object fromValue, Class toType )
		throws EvalError
	{
        Class fromType = fromValue.getClass();

		// The compiler isn't smart enough to allow me to leave this unassigned
		// even though it is clearly assigned in all cases below.
        Object result = null;

		// Going to a primitive type
        if ( toType.isPrimitive() ) 
			if ( fromValue instanceof Primitive )
				result = castPrimitive( (Primitive)fromValue, toType );
			else
				// cannot convert from object to primitive
                castError(fromValue.getClass(), toType);
        else 
			// Going to an object type
			if ( fromValue instanceof Primitive )
				// let castPrimitive handle trivial but legit case of NULL
				result = castPrimitive( (Primitive)fromValue, toType );
			else
				// Can we use the proxy mechanism to cast a bsh.This to 
				// the correct interface?
				if ( Capabilities.canGenerateInterfaces() &&
					(fromValue instanceof bsh.This) && toType.isInterface() ) 
						result = ((bsh.This)fromValue).getInterface( toType );
				else 
					// Could probably add getAssignableForm here to allow 
					// special bsh widening converions... wrappers to wrappers
					if ( toType.isInstance(fromValue ) )
						result = fromValue;
					else
						castError(fromType, toType);

		if ( result == null )
			throw new InternalError("bad construct somewhere...");

		return result;
	}

	/**
		Wrap up the ClassCastException in a TargetError so that it can
		be caught...
		Node user should catch and add the node
	*/
    public static void castError(Class from, Class to) throws EvalError {
		castError( 
			Reflect.normalizeClassName(from), Reflect.normalizeClassName(to) );
    }

    public static void castError(String from, String to) throws EvalError 
	{
		Exception cce = new ClassCastException("Illegal cast. Cannot cast " +
            from + " to " + to );
		throw new TargetError( "Cast", cce );
    }

	/**
		Cast the bsh.Primitive value to a new bsh.Primitive value
		This is usually a numeric type cast.  Other cases include:
			boolean can be cast to boolen
			null can be cast to any object type
			void cannot be cast to anything
	*/
	public static Primitive castPrimitive( Primitive primValue, Class toType ) 
		throws EvalError
	{
		// can't cast void to anything
		if ( primValue == Primitive.VOID )
			castError( "void value", Reflect.normalizeClassName(toType) );

		// unwrap, etc.
		Object value = primValue.getValue();
		Class fromType = primValue.getType();

		// Trying to cast primitive to an object type?
		// only works for Primitive.NULL
		if ( !toType.isPrimitive() )
			if ( primValue != Primitive.NULL )
				castError("primitive value", "object type:" + toType);
			else
				return primValue;

		// can only cast boolean to boolean
		if ( fromType == Boolean.TYPE )
		{
			if ( toType != Boolean.TYPE )
				castError(fromType, toType);
			else 
				return primValue;
		}

		// trying to do numeric promotion

		// first promote char to Number type to avoid duplicating code
		if (value instanceof Character)
			value = new Integer(((Character)value).charValue());

		if (value instanceof Number)
		{
			Number number = (Number)value;

			if (toType == Byte.TYPE)
				value = new Primitive(number.byteValue());
			else if(toType == Short.TYPE)
				value = new Primitive(number.shortValue());
			else if(toType == Character.TYPE)
				value = new Primitive((char)number.intValue());
			else if(toType == Integer.TYPE)
				value = new Primitive(number.intValue());
			else if(toType == Long.TYPE)
				value = new Primitive(number.longValue());
			else if(toType == Float.TYPE)
				value = new Primitive(number.floatValue());
			else if(toType == Double.TYPE)
				value = new Primitive(number.doubleValue());
			else
				castError(fromType, toType);

			return (Primitive)value;
		} 

		throw new EvalError("unknown type in cast");
	}
}
