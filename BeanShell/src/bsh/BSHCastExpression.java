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

/**
	Implement casts.

	I think it should be possible to simplify some of the code here by
	using the NameSpace.checkAssignable() method, but I haven't looked into it.
*/
class BSHCastExpression extends SimpleNode {

    public BSHCastExpression(int id) { super(id); }

	public Object eval(
		NameSpace namespace, Interpreter interpreter ) throws EvalError
    {
        Class toType = ((BSHType)jjtGetChild(0)).getType(namespace);
		SimpleNode expression = (SimpleNode)jjtGetChild(1);

        // evaluate the expression
        Object result = expression.eval(namespace, interpreter);
        Class fromType = result.getClass();

        if ( toType.isPrimitive() ) {

            // cannot convert from object to primitive
            if(!(result instanceof Primitive))
                castError(result.getClass(), toType);

            Object primitive = ((Primitive)result).getValue();
            fromType = ((Primitive)result).getType();

            if(fromType == Boolean.TYPE)
            {
                // booleans only allow the reflexive case
                if(toType != Boolean.TYPE)
                    castError(fromType, toType);
            }
            else
            {
                // first promote char to int to avoid duplicating code
                if(primitive instanceof Character)
                    primitive = new Integer(((Character)primitive).charValue());

                if(primitive instanceof Number)
                {
                    Number number = (Number)primitive;

                    if(toType == Byte.TYPE)
                        result = new Primitive(number.byteValue());
                    else if(toType == Short.TYPE)
                        result = new Primitive(number.shortValue());
                    else if(toType == Character.TYPE)
                        result = new Primitive((char)number.intValue());
                    else if(toType == Integer.TYPE)
                        result = new Primitive(number.intValue());
                    else if(toType == Long.TYPE)
                        result = new Primitive(number.longValue());
                    else if(toType == Float.TYPE)
                        result = new Primitive(number.floatValue());
                    else if(toType == Double.TYPE)
                        result = new Primitive(number.doubleValue());
                    else
                        castError(fromType, toType);
                }
            }
        } else 

// Ack...  this probably shouldn't compile under 1.1 without XThis compiling
// but it seems to...  ug.  probably need yet another factory here...
// or a bsh script fragment or some other indirection

			// Can we use the proxy mechanism to cast a bsh.This to interface
			if ( Capabilities.haveProxyMechanism() &&
				(result instanceof bsh.This) && toType.isInterface() ) {
					result = ((XThis)result).getInterface( toType );
		} else 
			if ( !toType.isInstance(result) )
           		castError(fromType, toType);

		return result;
    }

	/*
		Wrap up the ClassCastException in a TargetError so that it can
		be caught...
	*/
    private void castError(Class from, Class to) throws EvalError {
		/*
        throw new EvalError("Illegal cast. Cannot cast " +
            Reflect.normalizeClassName(from) + " to " +
            Reflect.normalizeClassName(to), this );
		*/
		Exception cce = new ClassCastException("Illegal cast. Cannot cast " +
            Reflect.normalizeClassName(from) + " to " +
            Reflect.normalizeClassName(to) );

		throw new TargetError( cce, this );
    }
}
