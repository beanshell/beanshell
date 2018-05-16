/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

class BSHUnaryExpression extends SimpleNode implements ParserConstants
{
    public int kind;
    public boolean postfix = false;

    BSHUnaryExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        SimpleNode node = (SimpleNode)jjtGetChild(0);

        // If this is a unary increment of decrement (either pre or postfix)
        // then we need an LHS to which to assign the result.  Otherwise
        // just do the unary operation for the value.
        try {
            if ( kind == INCR || kind == DECR ) {
                LHS lhs = ((BSHPrimaryExpression)node).toLHS(
                    callstack, interpreter );
                return lhsUnaryOperation( lhs, interpreter.getStrictJava() );
            } else
                return
                    unaryOperation( node.eval(callstack, interpreter), kind );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    private Object lhsUnaryOperation( LHS lhs, boolean strictJava )
        throws UtilEvalError
    {
        if ( Interpreter.DEBUG ) Interpreter.debug("lhsUnaryOperation");
        Object prevalue, postvalue;
        prevalue = lhs.getValue();
        postvalue = unaryOperation(prevalue, kind);

        Object retVal;
        if ( postfix )
            retVal = prevalue;
        else
            retVal = postvalue;

        lhs.assign( postvalue, strictJava );
        return retVal;
    }

    private Object unaryOperation( Object op, int kind ) throws UtilEvalError
    {
        if (op instanceof Boolean || op instanceof Character
            || op instanceof Number)
            return primitiveWrapperUnaryOperation( op, kind );

        if ( !(op instanceof Primitive) )
            throw new UtilEvalError( "Unary operation " + tokenImage[kind]
                + " inappropriate for object" );


        return Primitive.unaryOperation((Primitive)op, kind);
    }

    private Object primitiveWrapperUnaryOperation(Object val, int kind)
        throws UtilEvalError
    {
        Class operandType = val.getClass();
        Object operand = Primitive.promoteToInteger(val);

        if ( operand instanceof Boolean )
            return Primitive.booleanUnaryOperation((Boolean)operand, kind)
                    ? Boolean.TRUE : Boolean.FALSE;
        else
        if ( operand instanceof Integer )
        {
            int result = Primitive.intUnaryOperation((Integer)operand, kind);

            // ++ and -- must be cast back the original type
            if(kind == INCR || kind == DECR)
            {
                if(operandType == Byte.TYPE)
                    return Byte.valueOf((byte)result);
                if(operandType == Short.TYPE)
                    return Short.valueOf((short)result);
                if(operandType == Character.TYPE)
                    return Character.valueOf((char)result);
            }

            return Integer.valueOf(result);
        }
        else if(operand instanceof Long)
            return Long.valueOf(Primitive.longUnaryOperation((Long)operand, kind));
        else if(operand instanceof Float)
            return Float.valueOf(Primitive.floatUnaryOperation((Float)operand, kind));
        else if(operand instanceof Double)
            return Double.valueOf(Primitive.doubleUnaryOperation((Double)operand, kind));
        else
            throw new InterpreterError("An error occurred.  Please call technical support.");
    }
}
