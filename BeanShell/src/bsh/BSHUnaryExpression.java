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

class BSHUnaryExpression extends SimpleNode implements InterpreterConstants
{
    public int kind;
	public boolean postfix = false;

    BSHUnaryExpression(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        SimpleNode node = (SimpleNode)jjtGetChild(0);

        if(node instanceof BSHLHSPrimaryExpression)
            return lhsUnaryOperation(((BSHLHSPrimaryExpression)node).toLHS(namespace, interpreter));
        else
            return unaryOperation(node.eval(namespace, interpreter), kind);
    }

    private Object lhsUnaryOperation(LHS lhs) throws EvalError
    {
        Interpreter.debug("lhsUnaryOperation");
        Object prevalue, postvalue;
        prevalue = lhs.getValue();
        postvalue = unaryOperation(prevalue, kind);

		Object retVal;
		if ( postfix )
			retVal = prevalue;
		else
			retVal = postvalue;

		lhs.assign(postvalue);
		return retVal;
    }

    private Object unaryOperation(Object op, int kind) throws EvalError
    {
        if(op instanceof Boolean || op instanceof Character || op instanceof Number)
            return primitiveWrapperUnaryOperation(op, kind);

        if(!(op instanceof Primitive))
            throw new EvalError("Unary operation " + tokenImage[kind]
                + " inappropriate for object", this);

        return Primitive.unaryOperation((Primitive)op, kind);
    }

    private Object primitiveWrapperUnaryOperation(Object val, int kind)
        throws EvalError
    {
        Class operandType = val.getClass();
        Object operand = Primitive.promotePrimitive(val);

        if(operand instanceof Boolean)
            return new Boolean(Primitive.booleanUnaryOperation((Boolean)operand, kind));
        else if(operand instanceof Integer)
        {
            int result = Primitive.intUnaryOperation((Integer)operand, kind);

            // ++ and -- must be cast back the original type
            if(kind == INCR || kind == DECR)
            {
                if(operandType == Byte.TYPE)
                    return new Byte((byte)result);
                if(operandType == Short.TYPE)
                    return new Short((short)result);
                if(operandType == Character.TYPE)
                    return new Character((char)result);
            }

            return new Integer(result);
        }
        else if(operand instanceof Long)
            return new Long(Primitive.longUnaryOperation((Long)operand, kind));
        else if(operand instanceof Float)
            return new Float(Primitive.floatUnaryOperation((Float)operand, kind));
        else if(operand instanceof Double)
            return new Double(Primitive.doubleUnaryOperation((Double)operand, kind));
        else
            throw new InterpreterError("An error occurred.  Please call technical support.");
    }
}
