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

	public Object eval(
		CallStack callstack, Interpreter interpreter ) throws EvalError
    {
		NameSpace namespace = callstack.top();
        Class toType = ((BSHType)jjtGetChild(0)).getType(namespace);
		SimpleNode expression = (SimpleNode)jjtGetChild(1);

        // evaluate the expression
        Object result = expression.eval(callstack, interpreter);
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
			// Can we use the proxy mechanism to cast a bsh.This to interface?
			if ( Capabilities.haveProxyMechanism() &&
				(result instanceof bsh.This) && toType.isInterface() ) {
					result = ((bsh.This)result).getInterface( toType );
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
		Exception cce = new ClassCastException("Illegal cast. Cannot cast " +
            Reflect.normalizeClassName(from) + " to " +
            Reflect.normalizeClassName(to) );

		throw new TargetError( cce, this );
    }
}
