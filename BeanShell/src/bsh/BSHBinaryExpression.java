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
	This class needs logic to prevent the right hand side of boolean logical
	expressions from being naively evaluated...  e.g. for "foo && bar" bar 
	should not be evaluated in the case where foo is true.
*/
class BSHBinaryExpression extends SimpleNode 
	implements InterpreterConstants 
{
    public int kind;

    BSHBinaryExpression(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        Object lhs = ((SimpleNode)jjtGetChild(0)).eval(namespace, interpreter);

		/*
			Doing instanceof?  Next node is a type.
		*/
        if(kind == INSTANCEOF)
        {
            if(lhs instanceof Primitive)
            {
				// primitive can't be instance of anything, right?
				return new Primitive(false);

            }

            Class rhs = ((BSHType)jjtGetChild(1)).getType(namespace);
            boolean ret = (Reflect.isAssignableFrom(rhs, lhs.getClass()));
            return new Primitive(ret);
        }


		// The following two boolean checks were tacked on.
		// This could probably be smoothed out.

		/*
			Look ahead and short circuit evaluation of the rhs if:
				we're a boolean AND and the lhs is false.
		*/
		if ( kind == BOOL_AND || kind == BOOL_ANDX ) {
			Object obj = lhs;
			if ( isPrimitiveValue(lhs) )
				obj = ((Primitive)lhs).getValue();
			if ( obj instanceof Boolean && 
				( ((Boolean)obj).booleanValue() == false ) )
				return new Primitive(false);
		}
		/*
			Look ahead and short circuit evaluation of the rhs if:
				we're a boolean AND and the lhs is false.
		*/
		if ( kind == BOOL_OR || kind == BOOL_ORX ) {
			Object obj = lhs;
			if ( isPrimitiveValue(lhs) )
				obj = ((Primitive)lhs).getValue();
			if ( obj instanceof Boolean && 
				( ((Boolean)obj).booleanValue() == true ) )
				return new Primitive(true);
		}

		// end stuff that was tacked on.

		/*
			Are both the lhs and rhs either wrappers or primitive values?
		*/
		boolean isLhsWrapper = isWrapper( lhs );
        Object rhs = ((SimpleNode)jjtGetChild(1)).eval(namespace, interpreter);
		boolean isRhsWrapper = isWrapper( rhs );

		if ( ( isLhsWrapper || isPrimitiveValue( lhs ) ) &&
			 ( isRhsWrapper || isPrimitiveValue( rhs ) ) )
        {
			// Special case for EQ on two wrapper objects
			if ( isLhsWrapper && isRhsWrapper && kind == EQ ) {
				/*  
					Don't auto-unwrap wrappers (preserve identity semantics)
					FALL THROUGH TO OBJECT OPERATIONS BELOW.
				*/
			} else
				return primitiveBinaryOperation(lhs, rhs, kind);
        }

		/*
			Treat lhs and rhs as arbitrary objects
		*/
        switch(kind)
        {
            case EQ:
                return new Primitive((lhs == rhs));

            case NE:
                return new Primitive((lhs != rhs));

            case PLUS:
                if(lhs instanceof String || rhs instanceof String)
                    return lhs.toString() + rhs.toString();

            // FALL THROUGH TO DEFAULT CASE!!!

            default:
                if(lhs instanceof Primitive || rhs instanceof Primitive)
                    if(lhs == Primitive.VOID || rhs == Primitive.VOID)
                        throw new EvalError("illegal use of undefined object or 'void' literal", this);
                    else if(lhs == Primitive.NULL || rhs == Primitive.NULL)
                        throw new EvalError("illegal use of null object or 'null' literal", this);

                throw new EvalError("Operator: '" + tokenImage[kind] +
                    "' inappropriate for objects", this);
        }
    }

    public static Object primitiveBinaryOperation(Object obj1, Object obj2, int kind)
        throws EvalError
    {
        if(obj1 instanceof Primitive && obj2 instanceof Primitive)
            return Primitive.binaryOperation((Primitive)obj1, (Primitive)obj2, kind);

        // if one operand is a primitive wrapper and the other is a primitive
        // then wrap the primitive in a primitive wrapper.
        if(obj1 instanceof Primitive)
            obj1 = ((Primitive)obj1).getValue();
        if(obj2 instanceof Primitive)
            obj2 = ((Primitive)obj2).getValue();

        Class lhsType = obj1.getClass();
        Class rhsType = obj2.getClass();

        Object[] operands = Primitive.promotePrimitives(obj1, obj2);
        Object lhs = operands[0];
        Object rhs = operands[1];

        if(lhs.getClass() != rhs.getClass())
            throw new EvalError("type mismatch in operator.  " + lhsType +
                " cannot be matched with " + rhsType );

        if(lhs instanceof Boolean)
            return Primitive.booleanBinaryOperation((Boolean)lhs, (Boolean)rhs, kind);
        else if(lhs instanceof Integer)
        {
            Object result = Primitive.intBinaryOperation((Integer)lhs, (Integer)rhs, kind);
/*
            if(result instanceof Number && lhsType == rhsType)
            {
                Number number = (Number)result;
                if(lhsType == Byte.TYPE)
                    result = new Byte(number.byteValue());
                if(lhsType == Short.TYPE)
                    result = new Short(number.shortValue());
                if(lhsType == Character.TYPE)
                    result = new Character((char)number.intValue());
            }
*/
            return result;
        }
        else if(lhs instanceof Long)
            return Primitive.longBinaryOperation((Long)lhs, (Long)rhs, kind);
        else if(lhs instanceof Float)
            return Primitive.floatBinaryOperation((Float)lhs, (Float)rhs, kind);
        else if(lhs instanceof Double)
            return Primitive.doubleBinaryOperation((Double)lhs, (Double)rhs, kind);
        else
            throw new EvalError("Invalid types in binary operator" );
    }

	/*
		object is a non-null non-void Primitive type
	*/
	private boolean isPrimitiveValue( Object obj ) {
        return ( (obj instanceof Primitive) && (obj != Primitive.VOID && 
		obj != Primitive.NULL) );
	}

	/*
		object is a java.lang wrapper for boolean, char, or number type
	*/
	private boolean isWrapper( Object obj ) {
        return ( obj instanceof Boolean || 
			obj instanceof Character || obj instanceof Number );
	}
}
