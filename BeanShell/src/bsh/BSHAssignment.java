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

class BSHAssignment extends SimpleNode implements InterpreterConstants
{
    public int operator;

    BSHAssignment(int id) { super(id); }

    public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
    {
        LHS lhs = ((BSHLHSPrimaryExpression)jjtGetChild(0)).toLHS(namespace, interpreter);
        Object rhs = ((SimpleNode)jjtGetChild(1)).eval(namespace, interpreter);

        if(rhs == Primitive.VOID)
            throw new EvalError("Void assignment.", this);
        if(lhs == null)
            throw new EvalError("Error, can't assign to null...??", this);

        switch(operator)
        {
            case ASSIGN:
                return lhs.assign(rhs);

            case PLUSASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, PLUS));

            case MINUSASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, MINUS));

            case STARASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, STAR));

            case SLASHASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, SLASH));

            case ANDASSIGN:
            case ANDASSIGNX:
                return lhs.assign(operation(lhs.getValue(), rhs, BIT_AND));

            case ORASSIGN:
            case ORASSIGNX:
                return lhs.assign(operation(lhs.getValue(), rhs, BIT_OR));

            case XORASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, XOR));

            case MODASSIGN:
                return lhs.assign(operation(lhs.getValue(), rhs, MOD));

            case LSHIFTASSIGN:
            case LSHIFTASSIGNX:
                return lhs.assign(operation(lhs.getValue(), rhs, LSHIFT));

            case RSIGNEDSHIFTASSIGN:
            case RSIGNEDSHIFTASSIGNX:
                return lhs.assign(operation(lhs.getValue(), rhs, RSIGNEDSHIFT));

            case RUNSIGNEDSHIFTASSIGN:
            case RUNSIGNEDSHIFTASSIGNX:
                return lhs.assign(operation(lhs.getValue(), rhs, RUNSIGNEDSHIFT));

            default:
                throw new InterpreterError("unimplemented operator in assignment BSH");
        }
    }

    private Object operation(Object lhs, Object rhs, int kind) throws EvalError
    {
        if(lhs instanceof Primitive || rhs instanceof Primitive)
            if(lhs == Primitive.VOID || rhs == Primitive.VOID)
                throw new EvalError("Illegal use of undefined object or 'void' literal", this);
            else if(lhs == Primitive.NULL || rhs == Primitive.NULL)
                throw new EvalError("Illegal use of null object or 'null' literal", this);

        if( (lhs instanceof Boolean || lhs instanceof Character ||
             lhs instanceof Number || lhs instanceof Primitive) &&
            (rhs instanceof Boolean || rhs instanceof Character ||
             rhs instanceof Number || rhs instanceof Primitive) )
        {
            return BSHBinaryExpression.primitiveBinaryOperation(lhs, rhs, kind);
        }

		// Implement String += String;
		if ( lhs instanceof String && rhs instanceof String )
			return (String)lhs + (String)rhs;

        throw new EvalError("Non primitive value in operator: " +
            lhs.getClass() + " " + tokenImage[kind] + " " + rhs.getClass(), this);
    }
}
