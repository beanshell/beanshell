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

    private Object operation(Object lhs, Object rhs, int kind) 
		throws EvalError
    {
		/*
			Implement String += value;
			According to the JLS, value may be anything.
			In BeanShell, we'll disallow VOID (undefined) values.
			(or should we map them to the empty string?)
		*/
		if ( lhs instanceof String && rhs != Primitive.VOID ) {
			if ( kind != PLUS )
				throw new EvalError(
					"Use of non + operator with String LHS", this);     

			return (String)lhs + rhs;
		}

        if ( lhs instanceof Primitive || rhs instanceof Primitive )
            if(lhs == Primitive.VOID || rhs == Primitive.VOID)
                throw new EvalError(
					"Illegal use of undefined object or 'void' literal", this);
            else if ( lhs == Primitive.NULL || rhs == Primitive.NULL )
                throw new EvalError(
					"Illegal use of null object or 'null' literal", this);


        if( (lhs instanceof Boolean || lhs instanceof Character ||
             lhs instanceof Number || lhs instanceof Primitive) &&
            (rhs instanceof Boolean || rhs instanceof Character ||
             rhs instanceof Number || rhs instanceof Primitive) )
        {
            return BSHBinaryExpression.primitiveBinaryOperation(lhs, rhs, kind);
        }

        throw new EvalError("Non primitive value in operator: " +
            lhs.getClass() + " " + tokenImage[kind] + " " + rhs.getClass(), this);
    }
}
